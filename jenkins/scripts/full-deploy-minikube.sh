#!/usr/bin/env bash
# Full deployment test in Minikube - simulates deploy-to-gke.sh
set -euo pipefail

BLUE="\033[0;34m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m"

INFO_TAG="[INFO]"
WARN_TAG="[WARN]"
OK_TAG="[OK]"
ERR_TAG="[ERROR]"

log_info() {
  echo -e "${BLUE}${INFO_TAG}${NC} $*"
}

log_warn() {
  echo -e "${YELLOW}${WARN_TAG}${NC} $*"
}

log_success() {
  echo -e "${GREEN}${OK_TAG}${NC} $*"
}

log_error() {
  echo -e "${RED}${ERR_TAG}${NC} $*"
}

# Detectar memoria de Docker y configurar Minikube
DOCKER_MEM_MB=0
if command -v docker &> /dev/null; then
  DOCKER_MEM_RAW=$(docker info 2>/dev/null | grep "Total Memory" | awk '{print $3}')
  if [[ -n "${DOCKER_MEM_RAW}" ]]; then
    # Convertir GiB a MB (aproximado)
    DOCKER_MEM_MB=$(echo "${DOCKER_MEM_RAW}" | awk '{printf "%.0f", $1 * 1024}')
  fi
fi

# Default si no se pudo detectar
if [[ ${DOCKER_MEM_MB} -eq 0 || ${DOCKER_MEM_MB} -lt 1000 ]]; then
  DOCKER_MEM_MB=4096
  log_info "No se pudo detectar memoria de Docker, usando default: ${DOCKER_MEM_MB}MB"
fi

if [[ ${DOCKER_MEM_MB} -lt 4096 ]]; then
  MINIKUBE_MEM="3000"
  MINIKUBE_CPUS="2"
  log_warn "⚠️  Docker tiene solo ${DOCKER_MEM_MB}MB. Usando configuración mínima."
elif [[ ${DOCKER_MEM_MB} -lt 8192 ]]; then
  MINIKUBE_MEM="4096"
  MINIKUBE_CPUS="2"
  log_info "ℹ️  Usando configuración media (${DOCKER_MEM_MB}MB disponibles)"
else
  MINIKUBE_MEM="8192"
  MINIKUBE_CPUS="4"
  log_info "ℹ️  Usando configuración completa (${DOCKER_MEM_MB}MB disponibles)"
fi

log_info "🚀 Iniciando Minikube para despliegue completo..."
minikube start \
  --profile=ecommerce-deploy \
  --driver=docker \
  --memory="${MINIKUBE_MEM}" \
  --cpus="${MINIKUBE_CPUS}" \
  --kubernetes-version=v1.28.0 \
  --disk-size=20g

kubectl config use-context ecommerce-deploy

log_info "📦 Configurando namespace y recursos base..."
kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -

# Aplicar ConfigMap y Secrets desde el repo de infra
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
INFRA_DIR="${INFRA_DIR:-${REPO_ROOT}/../infra-ecommerce-microservice-backend-app}"

if [[ -d "${INFRA_DIR}/kubernetes/manifests" ]]; then
  log_info "Aplicando manifiestos base desde ${INFRA_DIR}..."
  kubectl apply -f "${INFRA_DIR}/kubernetes/manifests/namespace.yaml" || true
  kubectl apply -f "${INFRA_DIR}/kubernetes/manifests/configmap.yaml" || true
  kubectl apply -f "${INFRA_DIR}/kubernetes/manifests/secret.yaml" || true
else
  log_warn "No se encontró ${INFRA_DIR}, usando configuración mínima..."
  # ConfigMap mínimo
  cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: ecommerce-config
  namespace: ecommerce
data:
  SPRING_PROFILES_ACTIVE: "dev"
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: "http://service-discovery:8761/eureka/"
  SPRING_CONFIG_IMPORT: "optional:configserver:http://cloud-config:9296/"
EOF

  # Secret mínimo
  cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: ecommerce-secrets
  namespace: ecommerce
type: Opaque
stringData:
  DB_PASSWORD: "postgres"
  JWT_SECRET: "test-secret-key-for-minikube"
EOF
fi

log_info "🔧 Configurando Docker para usar Minikube..."
eval $(minikube docker-env --profile=ecommerce-deploy)

log_info "🔨 Construyendo imágenes Docker..."
cd "${REPO_ROOT}"

# Lista de servicios a desplegar (en orden)
SERVICES=(
  "service-discovery"
  "cloud-config"
  "api-gateway"
  "proxy-client"
  "user-service"
  "product-service"
  "favourite-service"
  "order-service"
  "shipping-service"
  "payment-service"
)

# Construir imágenes
for service in "${SERVICES[@]}"; do
  if [[ ! -d "${service}" ]]; then
    log_warn "Omitiendo ${service} (directorio no existe)"
    continue
  fi
  
  log_info "📦 Construyendo ${service}..."
  docker build -t "local/${service}:latest" \
    -f "${service}/Dockerfile" \
    --build-arg SERVICE_NAME="${service}" \
    . --quiet || log_warn "Error construyendo ${service}"
done

log_success "✅ Todas las imágenes construidas"

log_info "📋 Generando manifiestos de Kubernetes..."

# Función para generar manifest (adaptada de deploy-to-gke.sh)
generate_manifest() {
  local svc="$1"
  local port="$2"
  local service_type="${3:-ClusterIP}"
  local replicas="${4:-1}"
  
  # Configurar probes según el servicio
  local readiness_initial=60
  local readiness_failures=40
  local liveness_initial=120
  local liveness_failures=5
  
  if [[ "${svc}" == "service-discovery" ]]; then
    readiness_initial=80
    readiness_failures=50
  elif [[ "${svc}" == "cloud-config" ]]; then
    readiness_initial=80
    readiness_failures=50
  elif [[ "${svc}" == "api-gateway" ]]; then
    readiness_initial=100
    readiness_failures=60
    liveness_initial=180
  fi
  
  # Variables de entorno extra
  local extra_env=""
  if [[ "${svc}" == "cloud-config" ]]; then
    extra_env="        - name: SPRING_PROFILES_ACTIVE
          value: \"native,dev\"
        - name: SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS
          value: \"classpath:/configs\""
  fi
  
  cat <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${svc}
  namespace: ecommerce
  labels:
    app: ${svc}
spec:
  replicas: ${replicas}
  selector:
    matchLabels:
      app: ${svc}
  template:
    metadata:
      labels:
        app: ${svc}
    spec:
      containers:
      - name: ${svc}
        image: local/${svc}:latest
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: ${port}
        env:
        - name: SERVER_PORT
          value: "${port}"
${extra_env}
        envFrom:
        - configMapRef:
            name: ecommerce-config
        - secretRef:
            name: ecommerce-secrets
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: ${readiness_initial}
          periodSeconds: 5
          failureThreshold: ${readiness_failures}
          timeoutSeconds: 3
          successThreshold: 1
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: ${liveness_initial}
          periodSeconds: 30
          failureThreshold: ${liveness_failures}
          timeoutSeconds: 3
        resources:
          requests:
            cpu: 50m
            memory: 128Mi
          limits:
            cpu: 500m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: ${svc}
  namespace: ecommerce
  labels:
    app: ${svc}
spec:
  selector:
    app: ${svc}
  type: ${service_type}
  ports:
  - name: http
    port: ${port}
    targetPort: http
EOF
}

# Puertos de servicios
declare -A SERVICE_PORTS=(
  [service-discovery]=8761
  [cloud-config]=9296
  [api-gateway]=8080
  [proxy-client]=8900
  [user-service]=8700
  [product-service]=8500
  [favourite-service]=8800
  [order-service]=8300
  [shipping-service]=8600
  [payment-service]=8400
)

# Generar todos los manifests
MANIFEST_DIR=$(mktemp -d)
log_info "Manifiestos en: ${MANIFEST_DIR}"

for svc in "${SERVICES[@]}"; do
  port="${SERVICE_PORTS[${svc}]}"
  if [[ -z "${port}" ]]; then
    continue
  fi
  
  service_type="ClusterIP"
  replicas=1
  
  if [[ "${svc}" == "api-gateway" ]]; then
    service_type="NodePort"
    replicas=1  # Solo 1 réplica en Minikube
  fi
  
  generate_manifest "${svc}" "${port}" "${service_type}" "${replicas}" > "${MANIFEST_DIR}/${svc}.yaml"
done

log_success "✅ Manifiestos generados"

# FASE 1: Desplegar service-discovery (PRIMERO)
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "🚀 FASE 1: Desplegando service-discovery (crítico)"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

kubectl apply -f "${MANIFEST_DIR}/service-discovery.yaml"

log_info "⏳ Esperando a que service-discovery esté Ready..."
kubectl -n ecommerce wait --for=condition=ready pod -l app=service-discovery --timeout=400s || {
  log_error "service-discovery no alcanzó Ready"
  kubectl -n ecommerce get pods -l app=service-discovery
  kubectl -n ecommerce describe pod -l app=service-discovery | tail -20
  exit 1
}

log_success "✅ service-discovery está Ready"

# FASE 2: Desplegar cloud-config (SEGUNDO)
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "🚀 FASE 2: Desplegando cloud-config (crítico)"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

kubectl apply -f "${MANIFEST_DIR}/cloud-config.yaml"

log_info "⏳ Esperando a que cloud-config esté Ready..."
kubectl -n ecommerce wait --for=condition=ready pod -l app=cloud-config --timeout=400s || {
  log_error "cloud-config no alcanzó Ready"
  kubectl -n ecommerce get pods -l app=cloud-config
  kubectl -n ecommerce describe pod -l app=cloud-config | tail -20
  exit 1
}

log_success "✅ cloud-config está Ready"

# Verificación ACTIVA del ConfigServer
log_info "🔍 Verificando que ConfigServer esté respondiendo..."
CLOUD_CONFIG_POD=$(kubectl -n ecommerce get pod -l app=cloud-config -o jsonpath='{.items[0].metadata.name}')

ELAPSED=0
MAX_WAIT=180
VERIFIED=false

while [[ ${ELAPSED} -lt ${MAX_WAIT} ]]; do
  if kubectl -n ecommerce exec "${CLOUD_CONFIG_POD}" -- curl -sf http://localhost:9296/service-discovery/dev > /dev/null 2>&1; then
    log_success "✅ ConfigServer verificado y respondiendo"
    VERIFIED=true
    break
  fi
  
  if [[ $((ELAPSED % 30)) -eq 0 ]]; then
    log_info "⏳ Esperando ConfigServer... (${ELAPSED}s / ${MAX_WAIT}s)"
  fi
  
  sleep 5
  ELAPSED=$((ELAPSED + 5))
done

if [[ "${VERIFIED}" != "true" ]]; then
  log_warn "⚠️  ConfigServer no verificado, pero continuando..."
fi

log_info "⏳ Esperando 60s para propagación de Service DNS..."
sleep 60

# Verificación DNS inter-pod (como en deploy-to-gke.sh)
log_info "🔍 Verificando Service DNS inter-pod..."
DISCOVERY_POD=$(kubectl -n ecommerce get pod -l app=service-discovery -o jsonpath='{.items[0].metadata.name}')

for i in {1..10}; do
  if kubectl -n ecommerce exec "${DISCOVERY_POD}" -- curl -sf -m 5 http://cloud-config:9296/actuator/health > /dev/null 2>&1; then
    log_success "✅ Service de cloud-config accesible vía DNS desde otros pods"
    break
  else
    if [[ $i -eq 10 ]]; then
      log_warn "⚠️  DNS verification falló 10 veces (normal en algunos clusters)"
    else
      log_info "Intento $i/10..."
      sleep 5
    fi
  fi
done

log_info "⏳ Esperando 30s adicionales antes de desplegar api-gateway..."
sleep 30

# FASE 3: Desplegar api-gateway
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "🚀 FASE 3: Desplegando api-gateway"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

kubectl apply -f "${MANIFEST_DIR}/api-gateway.yaml"

log_info "⏳ Esperando a que api-gateway esté Ready (timeout: 8 minutos)..."
kubectl -n ecommerce wait --for=condition=ready pod -l app=api-gateway --timeout=480s || {
  log_error "❌ api-gateway no alcanzó Ready"
  log_info "Estado de pods:"
  kubectl -n ecommerce get pods -l app=api-gateway -o wide
  log_info "Logs:"
  kubectl -n ecommerce logs -l app=api-gateway --tail=100
  exit 1
}

log_success "✅ api-gateway está Ready!"

# FASE 4: Desplegar servicios restantes (opcional, solo si hay recursos)
if [[ ${DOCKER_MEM_MB} -gt 6000 ]]; then
  log_info ""
  log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  log_info "🚀 FASE 4: Desplegando servicios de negocio"
  log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  for svc in proxy-client user-service product-service; do
    if [[ -f "${MANIFEST_DIR}/${svc}.yaml" ]]; then
      log_info "Aplicando ${svc}..."
      kubectl apply -f "${MANIFEST_DIR}/${svc}.yaml"
    fi
  done
  
  log_info "⏳ Esperando rollout de servicios..."
  sleep 30
else
  log_warn "⚠️  Memoria limitada (${DOCKER_MEM_MB}MB), omitiendo servicios adicionales"
  log_info "Para desplegar más servicios, aumenta la memoria de Docker a 8GB+"
fi

# RESUMEN FINAL
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "✅ DESPLIEGUE COMPLETADO EN MINIKUBE"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo ""
log_info "📊 Estado de todos los pods:"
kubectl -n ecommerce get pods -o wide

echo ""
log_info "📊 Estado de todos los servicios:"
kubectl -n ecommerce get svc

echo ""
log_success "🎉 Servicios críticos desplegados exitosamente:"
echo "  ✅ service-discovery (Eureka)"
echo "  ✅ cloud-config (Config Server)"
echo "  ✅ api-gateway (Gateway)"

echo ""
log_info "🌐 Para acceder a los servicios:"
echo "  minikube service api-gateway -n ecommerce --profile=ecommerce-deploy"
echo "  minikube service service-discovery -n ecommerce --profile=ecommerce-deploy"

echo ""
log_info "📋 Comandos útiles:"
echo "  kubectl -n ecommerce get pods -w"
echo "  kubectl -n ecommerce logs -f -l app=api-gateway"
echo "  kubectl -n ecommerce exec -it <pod> -- curl localhost:8080/actuator/health"
echo "  minikube dashboard --profile=ecommerce-deploy"

echo ""
log_info "🛑 Para detener:"
echo "  minikube stop --profile=ecommerce-deploy"
echo "  minikube delete --profile=ecommerce-deploy"

