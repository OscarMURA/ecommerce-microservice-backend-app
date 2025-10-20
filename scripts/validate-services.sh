#!/bin/bash

# Script para validar que los servicios están funcionando correctamente
# Uso: ./validate-services.sh

set -e

echo "🔍 Validando los servicios del ecommerce..."
echo ""

# Colores
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASSED=0
FAILED=0

# Función para hacer una prueba
test_service() {
    local name=$1
    local url=$2
    local expected_code=$3
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    
    if [ "$HTTP_CODE" = "$expected_code" ]; then
        echo -e "${GREEN}✅${NC} $name (HTTP $HTTP_CODE)"
        ((PASSED++))
    else
        echo -e "${RED}❌${NC} $name (HTTP $HTTP_CODE, esperado $expected_code)"
        ((FAILED++))
    fi
}

echo -e "${BLUE}=== Servicios Base (Core) ===${NC}"
test_service "Eureka" "http://localhost:8761/eureka/apps" "200"
test_service "Config Server" "http://localhost:9296/actuator/health" "200"
test_service "Zipkin" "http://localhost:9411" "200"

echo ""
echo -e "${BLUE}=== Servicios Principales ===${NC}"
test_service "API Gateway" "http://localhost:8080/actuator/health" "200"
test_service "User Service" "http://localhost:8700/actuator/health" "200"
test_service "Product Service" "http://localhost:8500/actuator/health" "200"
test_service "Order Service" "http://localhost:8300/actuator/health" "200"
test_service "Payment Service" "http://localhost:8400/actuator/health" "200"
test_service "Favourite Service" "http://localhost:8800/actuator/health" "200"
test_service "Shipping Service" "http://localhost:8600/actuator/health" "200"
test_service "Proxy Client" "http://localhost:8900/actuator/health" "200"

echo ""
echo -e "${BLUE}=== Servicios Registrados en Eureka ===${NC}"
SERVICES=$(curl -s http://localhost:8761/eureka/apps | grep -oP '(?<=<app>)[^<]+' | sort)
SERVICE_COUNT=$(echo "$SERVICES" | grep -c . || echo 0)

echo -e "Servicios registrados: ${YELLOW}$SERVICE_COUNT${NC}"
echo "$SERVICES" | while read service; do
    echo -e "  ${GREEN}•${NC} $service"
done

echo ""
echo -e "${BLUE}=== Resumen ===${NC}"
TOTAL=$((PASSED + FAILED))
echo -e "Pruebas pasadas: ${GREEN}$PASSED/$TOTAL${NC}"

if [ $FAILED -gt 0 ]; then
    echo -e "${RED}⚠️  Hay $FAILED pruebas fallidas${NC}"
    echo ""
    echo "📋 Pasos para debuggear:"
    echo "  1. Verifica logs: docker compose logs -f api-gateway-container"
    echo "  2. Verifica redes: docker network ls"
    echo "  3. Verifica contenedores: docker ps"
    echo "  4. Verifica si los servicios están registrados en Eureka"
    exit 1
else
    echo -e "${GREEN}✅ ¡Todos los servicios están funcionando!${NC}"
    exit 0
fi
