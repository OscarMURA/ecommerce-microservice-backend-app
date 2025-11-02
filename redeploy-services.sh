#!/usr/bin/env bash
# Script para reconstruir y redesplegar servicios modificados en Minikube

set -euo pipefail

YELLOW="\033[1;33m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
RED="\033[0;31m"
NC="\033[0m"

log_info() {
  echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
  echo -e "${GREEN}[OK]${NC} $*"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $*"
}

# Verificar que estamos en el directorio correcto
if [ ! -f "pom.xml" ]; then
  log_error "Este script debe ejecutarse desde el directorio raíz del proyecto"
  exit 1
fi

# Verificar que Minikube está corriendo
if ! minikube status >/dev/null 2>&1; then
  log_error "Minikube no está ejecutándose. Por favor inicia Minikube primero."
  exit 1
fi

# Configurar contexto de Minikube
kubectl config use-context minikube

# Servicios a reconstruir (los que modificamos)
services=("user-service" "product-service" "order-service")

log_info "🔨 Reconstruyendo y redesplegando servicios modificados..."
echo ""

for service in "${services[@]}"; do
  log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  log_info "📦 Procesando: ${service}"
  log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  # 1. Construir imagen Docker
  log_info "🔨 Construyendo imagen Docker para ${service}..."
  if docker build -t "${service}:minikube" -f "${service}/Dockerfile" .; then
    log_success "✅ Imagen construida exitosamente"
  else
    log_error "❌ Error al construir la imagen"
    exit 1
  fi
  
  # 2. Cargar imagen a Minikube
  log_info "⬆️  Cargando imagen a Minikube..."
  if minikube image load "${service}:minikube"; then
    log_success "✅ Imagen cargada exitosamente"
  else
    log_error "❌ Error al cargar la imagen"
    exit 1
  fi
  
  # 3. Reiniciar deployment
  log_info "🔄 Reiniciando deployment de ${service}..."
  if kubectl rollout restart deployment/${service} -n ecommerce; then
    log_success "✅ Deployment reiniciado"
  else
    log_error "❌ Error al reiniciar el deployment"
    exit 1
  fi
  
  # 4. Esperar a que el deployment esté listo
  log_info "⏳ Esperando a que ${service} esté listo..."
  if kubectl rollout status deployment/${service} -n ecommerce --timeout=180s; then
    log_success "✅ ${service} está listo y funcionando"
  else
    log_error "❌ Timeout esperando a que ${service} esté listo"
    log_warn "Verifica los logs con: kubectl logs -n ecommerce deployment/${service}"
    exit 1
  fi
  
  echo ""
done

log_success "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "✅ Todos los servicios han sido reconstruidos y redesplegados"
log_success "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
log_info "📋 Servicios redesplegados:"
for service in "${services[@]}"; do
  echo "   ✅ ${service}"
done
echo ""
log_info "🧪 Ahora puedes ejecutar las pruebas E2E:"
log_info "   cd e2e-tests && ./run-e2e-cluster.sh port-forward"


