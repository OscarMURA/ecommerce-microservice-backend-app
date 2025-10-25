#!/usr/bin/env bash
# Script para probar el despliegue localmente con Minikube
# Uso: ./test-local-minikube.sh

set -euo pipefail

YELLOW="\033[1;33m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
RED="\033[0;31m"
NC="\033[0m"

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Verificar que Minikube esté instalado
if ! command -v minikube &> /dev/null; then
    log_error "Minikube no está instalado. Instálalo con:"
    echo "  curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64"
    echo "  sudo install minikube-linux-amd64 /usr/local/bin/minikube"
    exit 1
fi

# Verificar que kubectl esté instalado
if ! command -v kubectl &> /dev/null; then
    log_error "kubectl no está instalado"
    exit 1
fi

log_info "🚀 Iniciando Minikube con perfil 'ecommerce-test'..."

# Detectar memoria disponible en Docker
DOCKER_MEM=$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)
DOCKER_MEM_MB=$((DOCKER_MEM / 1024 / 1024))

# Configurar memoria basado en disponibilidad
if [[ ${DOCKER_MEM_MB} -lt 4096 ]]; then
  # Docker tiene menos de 4GB, usar configuración mínima
  MINIKUBE_MEM="3000"
  MINIKUBE_CPUS="2"
  log_warn "⚠️  Docker tiene solo ${DOCKER_MEM_MB}MB. Usando configuración mínima."
  log_info "Para mejor rendimiento, aumenta la memoria de Docker en: Docker Desktop → Settings → Resources"
elif [[ ${DOCKER_MEM_MB} -lt 8192 ]]; then
  # Docker tiene 4-8GB, usar configuración media
  MINIKUBE_MEM="4096"
  MINIKUBE_CPUS="2"
  log_info "ℹ️  Usando configuración media (${DOCKER_MEM_MB}MB disponibles)"
else
  # Docker tiene >8GB, usar configuración completa
  MINIKUBE_MEM="8192"
  MINIKUBE_CPUS="4"
  log_info "ℹ️  Usando configuración completa (${DOCKER_MEM_MB}MB disponibles)"
fi

minikube start --profile=ecommerce-test \
  --cpus="${MINIKUBE_CPUS}" \
  --memory="${MINIKUBE_MEM}" \
  --disk-size=20g \
  --driver=docker \
  --kubernetes-version=v1.28.0 || {
    log_warn "Minikube ya está corriendo o falló el start"
  }

# Cambiar al contexto de Minikube
kubectl config use-context ecommerce-test

log_info "📦 Creando namespace..."
kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -

log_info "🔧 Aplicando ConfigMaps y Secrets..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)/infra-ecommerce-microservice-backend-app"

if [[ -d "${INFRA_DIR}/kubernetes/manifests" ]]; then
  kubectl apply -f "${INFRA_DIR}/kubernetes/manifests/configmap.yaml"
  kubectl apply -f "${INFRA_DIR}/kubernetes/manifests/secret.yaml"
else
  log_warn "No se encontraron manifiestos base, continuando..."
fi

log_info "🏗️  Opción A: Usar imágenes locales"
log_info "  Para construir imágenes localmente:"
echo '  eval $(minikube docker-env --profile=ecommerce-test)'
echo '  cd ../../'
echo '  docker build -t local/service-discovery:test -f service-discovery/Dockerfile .'
echo '  docker build -t local/cloud-config:test -f cloud-config/Dockerfile .'
echo ""
log_info "🏗️  Opción B: Usar imágenes de GCR (requiere pull secret)"
echo '  Necesitarás crear un docker-registry-secret con credenciales de GCP'
echo ""

log_info "📋 Para desplegar un servicio de prueba:"
cat <<'EOF'

# 1. Generar manifiesto de prueba
cat > /tmp/service-discovery-test.yaml <<YAML
apiVersion: apps/v1
kind: Deployment
metadata:
  name: service-discovery
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: service-discovery
  template:
    metadata:
      labels:
        app: service-discovery
    spec:
      containers:
      - name: service-discovery
        image: local/service-discovery:test  # O usar imagen de GCR
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: 8761
        env:
        - name: SERVER_PORT
          value: "8761"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: 180  # VALORES CORREGIDOS
          periodSeconds: 5
          failureThreshold: 80
          timeoutSeconds: 3
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: service-discovery
  namespace: ecommerce
spec:
  selector:
    app: service-discovery
  type: NodePort
  ports:
  - name: http
    port: 8761
    targetPort: http
YAML

# 2. Aplicar
kubectl apply -f /tmp/service-discovery-test.yaml

# 3. Monitorear
kubectl -n ecommerce get pods -w

# 4. Ver logs en tiempo real
kubectl -n ecommerce logs -f deployment/service-discovery

# 5. Acceder al servicio (cuando esté Ready)
minikube service service-discovery -n ecommerce --profile=ecommerce-test

EOF

log_success "✅ Minikube configurado y listo para pruebas"
log_info "Comandos útiles:"
echo "  kubectl -n ecommerce get all                    # Ver recursos"
echo "  kubectl -n ecommerce describe pod <POD>         # Debug de pod"
echo "  kubectl -n ecommerce logs -f <POD>              # Ver logs"
echo "  minikube dashboard --profile=ecommerce-test     # Dashboard visual"
echo "  minikube stop --profile=ecommerce-test          # Detener cluster"
echo "  minikube delete --profile=ecommerce-test        # Eliminar cluster"

