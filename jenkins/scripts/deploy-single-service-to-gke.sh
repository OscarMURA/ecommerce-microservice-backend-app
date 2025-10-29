#!/bin/bash

# Script para desplegar un solo servicio a GKE
# Uso: ./deploy-single-service-to-gke.sh

set -euo pipefail

# Variables requeridas del entorno
required_vars=(
  "GCP_PROJECT_ID"
  "GKE_CLUSTER_NAME"
  "GKE_CLUSTER_LOCATION"
  "K8S_NAMESPACE"
  "K8S_SERVICE_NAME"
  "K8S_IMAGE_REGISTRY"
  "K8S_IMAGE_TAG"
  "INFRA_REPO_DIR"
  "K8S_ENVIRONMENT"
  "K8S_DEFAULT_REPLICAS"
  "K8S_ROLLOUT_TIMEOUT"
  "GOOGLE_APPLICATION_CREDENTIALS"
)

# Verificar variables requeridas
for var in "${required_vars[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "❌ Error: Variable de entorno $var no está definida"
    exit 1
  fi
done

echo "🚀 Iniciando despliegue de ${K8S_SERVICE_NAME} a GKE"
echo "📦 Proyecto: ${GCP_PROJECT_ID}"
echo "🏗️  Cluster: ${GKE_CLUSTER_NAME} (${GKE_CLUSTER_LOCATION})"
echo "📁 Namespace: ${K8S_NAMESPACE}"
echo "🐳 Imagen: ${K8S_IMAGE_REGISTRY}/${K8S_SERVICE_NAME}:${K8S_IMAGE_TAG}"
echo "🌍 Ambiente: ${K8S_ENVIRONMENT}"

# Autenticar con Google Cloud
echo "🔐 Autenticando con Google Cloud..."
gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
gcloud config set project "${GCP_PROJECT_ID}"

# Obtener credenciales del cluster
echo "🔗 Obteniendo credenciales del cluster GKE..."
gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" \
  --location="${GKE_CLUSTER_LOCATION}" \
  --project="${GCP_PROJECT_ID}"

# Verificar que el namespace existe, si no crearlo
echo "📁 Verificando namespace ${K8S_NAMESPACE}..."
if ! kubectl get namespace "${K8S_NAMESPACE}" >/dev/null 2>&1; then
  echo "📁 Creando namespace ${K8S_NAMESPACE}..."
  kubectl create namespace "${K8S_NAMESPACE}"
fi

# Aplicar etiquetas al namespace
kubectl label namespace "${K8S_NAMESPACE}" \
  environment="${K8S_ENVIRONMENT}" \
  --overwrite

# Buscar manifiestos del servicio en el repositorio de infraestructura
echo "🔍 Buscando manifiestos para ${K8S_SERVICE_NAME}..."
SERVICE_MANIFESTS=""

# Buscar en diferentes ubicaciones posibles
for manifest_dir in \
  "${INFRA_REPO_DIR}/kubernetes/manifests" \
  "${INFRA_REPO_DIR}/k8s" \
  "${INFRA_REPO_DIR}/manifests" \
  "${INFRA_REPO_DIR}"; do
  
  if [ -d "${manifest_dir}" ]; then
    # Buscar archivos YAML que contengan el nombre del servicio
    for manifest_file in "${manifest_dir}"/*.yaml "${manifest_dir}"/*.yml; do
      if [ -f "${manifest_file}" ] && grep -q "${K8S_SERVICE_NAME}" "${manifest_file}" 2>/dev/null; then
        SERVICE_MANIFESTS="${SERVICE_MANIFESTS} ${manifest_file}"
      fi
    done
  fi
done

if [ -z "${SERVICE_MANIFESTS}" ]; then
  echo "⚠️  No se encontraron manifiestos específicos para ${K8S_SERVICE_NAME}"
  echo "🔧 Creando manifiestos básicos..."

  # Crear deployment básico
  cat > "/tmp/${K8S_SERVICE_NAME}-deployment.yaml" << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${K8S_SERVICE_NAME}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ${K8S_SERVICE_NAME}
    environment: ${K8S_ENVIRONMENT}
spec:
  replicas: ${K8S_DEFAULT_REPLICAS}
  selector:
    matchLabels:
      app: ${K8S_SERVICE_NAME}
  template:
    metadata:
      labels:
        app: ${K8S_SERVICE_NAME}
        environment: ${K8S_ENVIRONMENT}
    spec:
      containers:
      - name: ${K8S_SERVICE_NAME}
        image: ${K8S_IMAGE_REGISTRY}/${K8S_SERVICE_NAME}:${K8S_IMAGE_TAG}
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "${K8S_ENVIRONMENT}"
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
EOF

  # Crear service básico
  cat > "/tmp/${K8S_SERVICE_NAME}-service.yaml" << EOF
apiVersion: v1
kind: Service
metadata:
  name: ${K8S_SERVICE_NAME}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ${K8S_SERVICE_NAME}
    environment: ${K8S_ENVIRONMENT}
spec:
  selector:
    app: ${K8S_SERVICE_NAME}
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
  type: ClusterIP
EOF

  SERVICE_MANIFESTS="/tmp/${K8S_SERVICE_NAME}-deployment.yaml /tmp/${K8S_SERVICE_NAME}-service.yaml"
fi

# Aplicar manifiestos
echo "📋 Aplicando manifiestos para ${K8S_SERVICE_NAME}..."
for manifest in ${SERVICE_MANIFESTS}; do
  if [ -f "${manifest}" ]; then
    echo "📄 Aplicando: $(basename "${manifest}")"
    
    # Reemplazar variables en el manifiesto
    sed -i "s/\${K8S_SERVICE_NAME}/${K8S_SERVICE_NAME}/g" "${manifest}"
    sed -i "s/\${K8S_NAMESPACE}/${K8S_NAMESPACE}/g" "${manifest}"
    sed -i "s/\${K8S_IMAGE_REGISTRY}/${K8S_IMAGE_REGISTRY}/g" "${manifest}"
    sed -i "s/\${K8S_IMAGE_TAG}/${K8S_IMAGE_TAG}/g" "${manifest}"
    sed -i "s/\${K8S_ENVIRONMENT}/${K8S_ENVIRONMENT}/g" "${manifest}"
    sed -i "s/\${K8S_DEFAULT_REPLICAS}/${K8S_DEFAULT_REPLICAS}/g" "${manifest}"
    
    kubectl apply -f "${manifest}"
  fi
done

# Esperar a que el deployment esté listo
echo "⏳ Esperando a que el deployment esté listo..."
kubectl rollout status deployment/${K8S_SERVICE_NAME} \
  -n "${K8S_NAMESPACE}" \
  --timeout="${K8S_ROLLOUT_TIMEOUT}s"

# Verificar el estado del servicio
echo "🔍 Verificando estado del servicio..."
kubectl get pods -n "${K8S_NAMESPACE}" -l app="${K8S_SERVICE_NAME}"
kubectl get services -n "${K8S_NAMESPACE}" -l app="${K8S_SERVICE_NAME}"

# Mostrar logs del servicio
echo "📋 Mostrando logs del servicio (últimas 20 líneas)..."
kubectl logs -n "${K8S_NAMESPACE}" -l app="${K8S_SERVICE_NAME}" --tail=20 || true

echo "✅ Despliegue de ${K8S_SERVICE_NAME} completado exitosamente!"

# Limpiar archivos temporales
rm -f "/tmp/${K8S_SERVICE_NAME}-deployment.yaml" "/tmp/${K8S_SERVICE_NAME}-service.yaml"
