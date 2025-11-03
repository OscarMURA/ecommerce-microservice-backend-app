#!/usr/bin/env bash
# Script definitivo para limpiar Minikube
# Uso: ./cleanup-minikube-final.sh

set -euo pipefail

BLUE="\033[0;34m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m"

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║${NC}    ${GREEN}Limpieza Definitiva de Minikube${NC}                        ${BLUE}║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

log_info "🧹 Iniciando limpieza completa de Minikube..."

# Servicios a limpiar
SERVICES=(
  "service-discovery"
  "order-service"
  "payment-service"
  "product-service"
  "shipping-service"
  "user-service"
  "favourite-service"
  "zipkin"
)

# 1. Eliminar namespace (esto elimina todos los recursos)
log_info "🗑️  Eliminando namespace ecommerce..."
kubectl delete namespace ecommerce --ignore-not-found=true --grace-period=30 --force 2>/dev/null || true

# 2. Eliminar imágenes de Minikube
log_info "🗑️  Eliminando imágenes de Minikube..."
for service in "${SERVICES[@]}"; do
  minikube image rm "${service}:minikube" 2>/dev/null || true
done

# 3. Limpiar imágenes Docker
log_info "🗑️  Limpiando imágenes Docker..."
for service in "${SERVICES[@]}"; do
  docker rmi "${service}:minikube" --force 2>/dev/null || true
done

# 4. Limpiar imágenes huérfanas
log_info "🗑️  Limpiando imágenes huérfanas..."
docker image prune -f 2>/dev/null || true

# 5. Detener Minikube
log_info "🛑 Deteniendo Minikube..."
minikube stop 2>/dev/null || true

# 6. Eliminar Minikube completamente
log_info "🗑️  Eliminando Minikube completamente..."
minikube delete 2>/dev/null || true

# 7. Limpiar configuración de kubectl
log_info "🗑️  Limpiando configuración de kubectl..."
kubectl config delete-context minikube 2>/dev/null || true
kubectl config delete-cluster minikube 2>/dev/null || true

log_success "✅ Limpieza completada exitosamente!"
echo ""
log_info "📋 Para verificar que todo está limpio:"
echo "  docker images | grep minikube    # No debería mostrar nada"
echo "  kubectl config get-contexts      # No debería mostrar minikube"
echo "  minikube status                  # Debería mostrar 'not found'"
echo ""
log_info "🚀 Para desplegar de nuevo:"
echo "  ./deploy-minikube-final.sh clean"
echo ""
