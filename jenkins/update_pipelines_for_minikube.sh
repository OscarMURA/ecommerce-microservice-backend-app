#!/bin/bash
# Script para actualizar todos los pipelines -dev.groovy para soportar deploy automático a Minikube

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICES=(
  "order-service:8081"
  "payment-service:8082"
  "shipping-service:8084"
  "favourite-service:8086"
  "service-discovery:8761"
)

echo "🚀 Actualizando pipelines para soportar deploy automático a Minikube..."

for service_port in "${SERVICES[@]}"; do
  IFS=':' read -r service port <<< "$service_port"
  PIPELINE_FILE="${BASE_DIR}/${service}/jenkins/${service}-dev.groovy"
  
  if [ ! -f "$PIPELINE_FILE" ]; then
    echo "⚠️  Pipeline no encontrado: $PIPELINE_FILE"
    continue
  fi
  
  echo "📝 Procesando: $service (puerto $port)"
  
  # Ya actualizados manualmente: user-service, product-service
  if [ "$service" == "user-service" ] || [ "$service" == "product-service" ]; then
    echo "  ✅ Ya actualizado (salteado)"
    continue
  fi
  
  echo "  ⚠️  Este servicio requiere actualización manual siguiendo el patrón de user-service"
done

echo "✅ Revisión completada"
echo ""
echo "📋 Servicios pendientes de actualización:"
echo "  - order-service"
echo "  - payment-service"
echo "  - shipping-service"
echo "  - favourite-service"
echo "  - service-discovery"
echo ""
echo "💡 Usa user-service-dev.groovy como plantilla para los cambios"


