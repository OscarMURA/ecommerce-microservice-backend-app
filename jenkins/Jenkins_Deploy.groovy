pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    string(name: 'REPO_URL', defaultValue: 'https://github.com/OscarMURA/ecommerce-microservice-backend-app.git', description: 'Repositorio del backend')
    string(name: 'APP_BRANCH', defaultValue: 'develop', description: 'Branch del repositorio a desplegar')
    choice(name: 'K8S_ENVIRONMENT', choices: ['dev', 'staging', 'prod'], description: 'Ambiente de Kubernetes')
    string(name: 'K8S_NAMESPACE', defaultValue: 'ecommerce', description: 'Namespace de Kubernetes')
    string(name: 'K8S_SERVICES', defaultValue: 'service-discovery user-service product-service favourite-service order-service shipping-service payment-service', description: 'Servicios a desplegar')
    string(name: 'GKE_CLUSTER_NAME', defaultValue: 'ecommerce-dev-gke-v2', description: 'Nombre del cluster GKE')
    string(name: 'GKE_LOCATION', defaultValue: 'us-central1-a', description: 'Zona del cluster GKE')
    string(name: 'K8S_IMAGE_REGISTRY', defaultValue: 'us-docker.pkg.dev/devops-activity/app-images', description: 'Registro de contenedores (p. ej. us-docker.pkg.dev/PROJECT/REPO)')
    string(name: 'K8S_IMAGE_TAG', defaultValue: '', description: 'Tag de las imágenes (vacío = commit actual)')
    string(name: 'INFRA_REPO_URL', defaultValue: 'https://github.com/OscarMURA/infra-ecommerce-microservice-backend-app.git', description: 'Repositorio de manifiestos K8s')
    string(name: 'INFRA_REPO_BRANCH', defaultValue: 'infra/master', description: 'Rama del repo de infraestructura')
    booleanParam(name: 'BUILD_IMAGES', defaultValue: true, description: 'Construir y subir imágenes Docker antes de desplegar')
    string(name: 'VM_NAME', defaultValue: 'ecommerce-integration-runner', description: 'VM de DigitalOcean para construir imágenes')
  }

  environment {
    WORKSPACE_DIR = "${env.WORKSPACE}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          echo "🚀 Pipeline de Despliegue Rápido"
          echo "📦 Branch: ${params.APP_BRANCH}"
          echo "🎯 Ambiente: ${params.K8S_ENVIRONMENT}"
          echo "☸️  Cluster: ${params.GKE_CLUSTER_NAME}"
        }
      }
    }

    stage('Get VM IP') {
      when {
        expression { params.BUILD_IMAGES }
      }
      steps {
        withCredentials([string(credentialsId: 'digitalocean-token', variable: 'DO_TOKEN')]) {
          script {
            def dropletIp = sh(script: """
set -e
curl -sS -H "Authorization: Bearer ${DO_TOKEN}" "https://api.digitalocean.com/v2/droplets?per_page=200" \
  | jq -r --arg NAME "${params.VM_NAME}" '.droplets[] | select(.name==\$NAME) | .networks.v4[] | select(.type=="public") | .ip_address' \
  | head -n1
""", returnStdout: true).trim()

            if (!dropletIp) {
              error "❌ No se encontró la VM ${params.VM_NAME}. Por favor créala primero con Jenkins_Create_VM."
            }

            env.DROPLET_IP = dropletIp
            echo "✅ VM encontrada: ${env.DROPLET_IP}"
          }
        }
      }
    }

    stage('Build and Push Images') {
      when {
        expression { params.BUILD_IMAGES }
      }
      steps {
        withCredentials([
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID'),
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            def imageTag = params.K8S_IMAGE_TAG?.trim()
            if (!imageTag) {
              imageTag = sh(script: "git ls-remote ${params.REPO_URL} ${params.APP_BRANCH} | cut -f1 | cut -c1-7", returnStdout: true).trim()
              if (!imageTag) {
                imageTag = 'latest'
              }
            }
            
            env.IMAGE_TAG = imageTag
            
            echo "🔨 Construyendo imágenes Docker"
            echo "📦 Registro: ${params.K8S_IMAGE_REGISTRY}"
            echo "🏷️  Tag: ${imageTag}"

            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "IMAGE_REGISTRY=${params.K8S_IMAGE_REGISTRY}",
              "IMAGE_TAG=${imageTag}",
              "TARGET_IP=${env.DROPLET_IP}",
              "REPO_URL=${params.REPO_URL}",
              "APP_BRANCH=${params.APP_BRANCH}",
              "REMOTE_GCP_CRED_PATH=/home/jenkins/.config/gcloud/service-account.json"
            ]) {
              sh '''
set -e
export SSHPASS="$VM_PASSWORD"

echo "📥 Verificando código en la VM..."
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "REPO_URL='$REPO_URL' APP_BRANCH='$APP_BRANCH' bash -s" <<'EOFSYNC'
set -euo pipefail

REMOTE_DIR="/opt/ecommerce-app/backend"

if [ ! -d "$REMOTE_DIR/.git" ]; then
  echo "📦 Clonando repositorio..."
  mkdir -p /opt/ecommerce-app
  cd /opt/ecommerce-app
  git clone "$REPO_URL" backend
fi

cd "$REMOTE_DIR"
echo "🔄 Actualizando código..."
git fetch origin "$APP_BRANCH"
git checkout -B "$APP_BRANCH" "origin/$APP_BRANCH" 2>/dev/null || git checkout "$APP_BRANCH"
git pull origin "$APP_BRANCH" || true
chmod +x mvnw || true

echo "✅ Código actualizado en $REMOTE_DIR"
EOFSYNC

echo "🔐 Usando credenciales de GCP ya configuradas en la VM..."
REMOTE_GCP_CRED_PATH="/home/jenkins/.config/gcloud/service-account.json"

echo "🔨 Construyendo y subiendo imágenes..."
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "GCP_PROJECT_ID='$GCP_PROJECT_ID' IMAGE_REGISTRY='$IMAGE_REGISTRY' IMAGE_TAG='$IMAGE_TAG' REMOTE_GCP_CRED_PATH='$REMOTE_GCP_CRED_PATH' bash -s" <<'EOFBUILD'
set -euo pipefail

REMOTE_DIR="/opt/ecommerce-app/backend"

# Usar la credencial ya provisionada por Jenkins_Create_VM (o la ruta personalizada)
GCP_CREDS_FILE="${REMOTE_GCP_CRED_PATH:-/home/jenkins/.config/gcloud/service-account.json}"

if [ ! -f "$GCP_CREDS_FILE" ]; then
  echo "❌ No se encontró la credencial en $GCP_CREDS_FILE. Ejecuta Jenkins_Create_VM con CONFIGURE_GCP_ACCESS=true o ajusta REMOTE_GCP_CRED_PATH."
  exit 1
fi

# Verificar que gcloud esté instalado
if ! command -v gcloud &> /dev/null; then
    echo "⚠️  gcloud no está instalado. Instalando..."
    
    # Instalar gcloud SDK
    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -
    sudo apt-get update && sudo apt-get install -y google-cloud-sdk google-cloud-sdk-gke-gcloud-auth-plugin
    
    echo "✅ gcloud instalado"
fi

echo "🔐 Autenticando con GCP..."
gcloud auth activate-service-account --key-file="$GCP_CREDS_FILE"
gcloud config set project "$GCP_PROJECT_ID"

# Detectar host del registro (gcr.io, us-docker.pkg.dev, etc.) y configurar docker
REGISTRY_HOST="$(echo "$IMAGE_REGISTRY" | cut -d/ -f1)"
if [ -z "$REGISTRY_HOST" ]; then
  echo "❌ No se pudo determinar el host del registro a partir de '$IMAGE_REGISTRY'"
  exit 1
fi
gcloud auth configure-docker "$REGISTRY_HOST" --quiet

services="service-discovery user-service product-service favourite-service order-service shipping-service payment-service"

# Función para detectar si un servicio necesita rebuild
needs_rebuild() {
  local service="$1"
  local service_dir="$REMOTE_DIR/$service"
  
  # Verificar si existe la imagen en el registry
  local image_name="${IMAGE_REGISTRY}/${service}:${IMAGE_TAG}"
  
  # Intentar hacer pull de la imagen para verificar si existe
  if docker pull "$image_name" >/dev/null 2>&1; then
    echo "✅ $service: Imagen existente encontrada en registry, usando imagen existente"
    return 1  # No necesita rebuild
  else
    echo "🔄 $service: Imagen no encontrada en registry, necesita rebuild"
    return 0  # Necesita rebuild
  fi
}

# Detectar servicios que necesitan rebuild
services_to_build=()
for service in $services; do
  if needs_rebuild "$service"; then
    services_to_build+=("$service")
  fi
done

echo "📋 Servicios que necesitan rebuild: ${services_to_build[*]:-ninguno}"

# Solo construir servicios que cambiaron
for service in "${services_to_build[@]}"; do
  SERVICE_DIR="$REMOTE_DIR/$service"
  DOCKERFILE_PATH="$SERVICE_DIR/Dockerfile"
  
  # Verificar si existe el directorio del servicio
  if [ ! -d "$SERVICE_DIR" ]; then
    echo "⚠️  Omitiendo $service (directorio no existe)"
    continue
  fi
  
  # Verificar si existe Dockerfile en el directorio del servicio
  if [ ! -f "$DOCKERFILE_PATH" ]; then
    echo "⚠️  Omitiendo $service (no tiene Dockerfile)"
    continue
  fi
  
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "🔨 Construyendo: $service"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  # Construir desde la raíz del repositorio, usando -f para especificar el Dockerfile
  # Esto permite que los COPY en el Dockerfile funcionen correctamente
  docker build -t "${IMAGE_REGISTRY}/${service}:${IMAGE_TAG}" "$REMOTE_DIR" \
    -f "$DOCKERFILE_PATH" \
    --build-arg SERVICE_NAME="$service" \
    --build-arg BUILD_DATE="$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    || {
      echo "❌ Error construyendo $service"
      continue
    }
  
  echo "📤 Subiendo: ${IMAGE_REGISTRY}/${service}:${IMAGE_TAG}"
  docker push "${IMAGE_REGISTRY}/${service}:${IMAGE_TAG}" || {
    echo "❌ Error subiendo $service"
    continue
  }
  
  echo "✅ Completado: $service"
done

# Si no hay servicios que construir, usar imágenes existentes
if [ ${#services_to_build[@]} -eq 0 ]; then
  echo "🎯 No hay servicios que reconstruir, usando imágenes existentes"
fi

echo ""
echo "✅ Todas las imágenes fueron construidas y subidas"
EOFBUILD
'''
            }
          }
        }
      }
    }

    stage('Deploy to Kubernetes') {
      steps {
        withCredentials([
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID'),
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS')
        ]) {
          script {
            def imageTag = params.K8S_IMAGE_TAG?.trim()
            if (!imageTag && env.IMAGE_TAG) {
              imageTag = env.IMAGE_TAG
            }
            if (!imageTag) {
              imageTag = sh(script: "git ls-remote ${params.REPO_URL} ${params.APP_BRANCH} | cut -f1 | cut -c1-7", returnStdout: true).trim()
              if (!imageTag) {
                imageTag = 'latest'
              }
            }

echo "📦 Desplegando servicios con tag: ${imageTag}"

            def workspaceRoot = pwd()
            def infraDir = "${workspaceRoot}/infra-k8s-config"

            dir('infra-k8s-config') {
              deleteDir()
              git branch: params.INFRA_REPO_BRANCH, credentialsId: 'github-token', url: params.INFRA_REPO_URL
            }

            def rawServices = (params.K8S_SERVICES ?: '')
              .split(/[,\s]+/)
              .collect { it?.trim()?.toLowerCase() }
              .findAll { it }
            def serviceList = rawServices.join(' ')

            def defaultReplicas = params.K8S_ENVIRONMENT == 'prod' ? '2' : '1'
            def rolloutTimeout = params.K8S_ENVIRONMENT == 'prod' ? '420' : '240'

            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
              "GKE_CLUSTER_LOCATION=${params.GKE_LOCATION}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "K8S_SERVICE_LIST=${serviceList}",
              "K8S_IMAGE_REGISTRY=${params.K8S_IMAGE_REGISTRY}",
              "K8S_IMAGE_TAG=${imageTag}",
              "INFRA_REPO_DIR=${infraDir}",
              "K8S_ENVIRONMENT=${params.K8S_ENVIRONMENT}",
              "K8S_DEFAULT_REPLICAS=${defaultReplicas}",
              "K8S_ROLLOUT_TIMEOUT=${rolloutTimeout}",
              "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
            ]) {
              sh '''
set -e
chmod +x jenkins/scripts/deploy-to-gke.sh
jenkins/scripts/deploy-to-gke.sh
'''
            }
          }
        }
      }
    }
  }

  post {
    success {
      echo "✅ Despliegue completado exitosamente"
      echo "🎯 Ambiente: ${params.K8S_ENVIRONMENT}"
      echo "☸️  Cluster: ${params.GKE_CLUSTER_NAME}"
      echo "📦 Namespace: ${params.K8S_NAMESPACE}"
    }
    failure {
      echo "❌ El despliegue falló. Revisa los logs para más detalles."
    }
    always {
      cleanWs()
    }
  }
}
