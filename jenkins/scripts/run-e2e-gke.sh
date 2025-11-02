#!/bin/bash

# Script para ejecutar pruebas E2E contra un cluster GKE
# Uso: ./run-e2e-gke.sh <namespace> <gcp-project-id> <cluster-name> <zone>

set -e

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuración
NAMESPACE=${1:-staging}
GCP_PROJECT_ID=${2}
CLUSTER_NAME=${3}
ZONE=${4}

# Configuración de servicios
declare -a SERVICES=("user-service" "product-service" "order-service" "payment-service" "shipping-service" "favourite-service")
declare -a SERVICE_PORTS=(8085 8083 8081 8082 8084 8086)
declare -a CONTEXT_PATHS=("/user-service" "/product-service" "/order-service" "/payment-service" "/shipping-service" "/favourite-service")

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🧪 Ejecutando pruebas E2E contra GKE${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Verificar que kubectl está disponible
if ! command -v kubectl > /dev/null 2>&1; then
    echo -e "${RED}❌ kubectl no está instalado${NC}"
    exit 1
fi

# Verificar que el namespace existe
if ! kubectl get namespace "${NAMESPACE}" &> /dev/null; then
    echo -e "${RED}❌ Namespace '${NAMESPACE}' no existe${NC}"
    echo -e "${YELLOW}💡 Asegúrate de que el cluster está desplegado${NC}"
    exit 1
fi

# Verificar que los servicios están disponibles
echo -e "${BLUE}🔍 Verificando que los servicios estén disponibles...${NC}"
ALL_READY=true
for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    
    if ! kubectl get deployment "${SERVICE}" -n "${NAMESPACE}" &> /dev/null; then
        echo -e "${RED}❌ Deployment '${SERVICE}' no encontrado en namespace '${NAMESPACE}'${NC}"
        ALL_READY=false
        continue
    fi
    
    READY=$(kubectl get deployment "${SERVICE}" -n "${NAMESPACE}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
    if [ "${READY}" != "1" ]; then
        echo -e "${YELLOW}⚠️  ${SERVICE} no está ready (ready: ${READY:-0}/1)${NC}"
        ALL_READY=false
    else
        echo -e "${GREEN}✅ ${SERVICE} está ready${NC}"
    fi
done

if [ "$ALL_READY" = false ]; then
    echo -e "${YELLOW}❌ Algunos servicios no están ready. Esperando 30 segundos adicionales...${NC}"
    sleep 30
fi

# Configurar port-forwards
echo ""
echo -e "${BLUE}📡 Configurando port-forwards para las pruebas E2E...${NC}"

# Iniciar port-forwards en background
PIDS=()
SERVICE_MAP=("user-service:8085:/user-service" "product-service:8083:/product-service" \
             "order-service:8081:/order-service" "payment-service:8082:/payment-service" \
             "shipping-service:8084:/shipping-service" "favourite-service:8086:/favourite-service")

for SERVICE_INFO in "${SERVICE_MAP[@]}"; do
    IFS=':' read -r SERVICE PORT CONTEXT <<< "$SERVICE_INFO"
    
    echo -e "${BLUE}  → Port-forward ${SERVICE}:${PORT}...${NC}"
    kubectl port-forward -n "${NAMESPACE}" "deployment/${SERVICE}" "${PORT}:${PORT}" > /dev/null 2>&1 &
    PIDS+=($!)
    sleep 2
done

# Función de limpieza
cleanup() {
    echo ""
    echo -e "${YELLOW}🧹 Limpiando port-forwards...${NC}"
    for pid in "${PIDS[@]}"; do
        kill $pid 2>/dev/null || true
    done
}
trap cleanup EXIT

# Esperar que los port-forwards estén listos
echo ""
echo -e "${BLUE}⏳ Esperando que los port-forwards estén listos...${NC}"
sleep 10

# Verificar conectividad
echo ""
echo -e "${BLUE}🔍 Verificando conectividad a los servicios...${NC}"
for SERVICE_INFO in "${SERVICE_MAP[@]}"; do
    IFS=':' read -r SERVICE PORT CONTEXT <<< "$SERVICE_INFO"
    
    if curl -s -f "http://localhost:${PORT}${CONTEXT}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ ${SERVICE} accesible${NC}"
    else
        echo -e "${YELLOW}⚠️  ${SERVICE} no responde aún${NC}"
    fi
done

# Navegar al directorio de e2e-tests
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}/e2e-tests"

# Configurar variables de entorno para Spring Boot
export SPRING_PROFILES_ACTIVE=test,cluster
export CLUSTER_BASE_URL="http://localhost"
export USER_SERVICE_URL="http://localhost:8085"
export USER_SERVICE_CONTEXT="/user-service"
export PRODUCT_SERVICE_URL="http://localhost:8083"
export PRODUCT_SERVICE_CONTEXT="/product-service"
export ORDER_SERVICE_URL="http://localhost:8081"
export ORDER_SERVICE_CONTEXT="/order-service"
export PAYMENT_SERVICE_URL="http://localhost:8082"
export PAYMENT_SERVICE_CONTEXT="/payment-service"
export SHIPPING_SERVICE_URL="http://localhost:8084"
export SHIPPING_SERVICE_CONTEXT="/shipping-service"
export FAVOURITE_SERVICE_URL="http://localhost:8086"
export FAVOURITE_SERVICE_CONTEXT="/favourite-service"

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🚀 Iniciando suite de pruebas E2E...${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Ejecutar las pruebas E2E
./mvnw clean test \
  -Dspring.profiles.active=test,cluster \
  -Dtest=*E2E*Test \
  -DCLUSTER_BASE_URL="${CLUSTER_BASE_URL}" \
  -DUSER_SERVICE_URL="${USER_SERVICE_URL}" \
  -DUSER_SERVICE_CONTEXT="${USER_SERVICE_CONTEXT}" \
  -DPRODUCT_SERVICE_URL="${PRODUCT_SERVICE_URL}" \
  -DPRODUCT_SERVICE_CONTEXT="${PRODUCT_SERVICE_CONTEXT}" \
  -DORDER_SERVICE_URL="${ORDER_SERVICE_URL}" \
  -DORDER_SERVICE_CONTEXT="${ORDER_SERVICE_CONTEXT}" \
  -DPAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL}" \
  -DPAYMENT_SERVICE_CONTEXT="${PAYMENT_SERVICE_CONTEXT}" \
  -DSHIPPING_SERVICE_URL="${SHIPPING_SERVICE_URL}" \
  -DSHIPPING_SERVICE_CONTEXT="${SHIPPING_SERVICE_CONTEXT}" \
  -DFAVOURITE_SERVICE_URL="${FAVOURITE_SERVICE_URL}" \
  -DFAVOURITE_SERVICE_CONTEXT="${FAVOURITE_SERVICE_CONTEXT}" || {
    echo ""
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${RED}❌ Pruebas E2E fallaron${NC}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    exit 1
}

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Todas las pruebas E2E pasaron exitosamente${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

