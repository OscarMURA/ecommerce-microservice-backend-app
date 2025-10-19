#!/bin/bash

# Script para levantar los servicios en el orden correcto
# Uso: ./start-services.sh

set -e  # Exit if any command fails

echo "🚀 Iniciando servicios del ecommerce..."
echo ""

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 1. Levantar core.yml (Eureka, Config, Zipkin)
echo -e "${BLUE}[1/3]${NC} Levantando servicios core (Eureka, Config, Zipkin)..."
docker compose -f docker-compose/core.yml up -d

echo -e "${YELLOW}⏳ Esperando 50 segundos a que se inicialicen...${NC}"
sleep 50

# 2. Verificar que core está arriba
echo -e "${BLUE}[2/3]${NC} Verificando servicios core..."
if curl -s http://localhost:8761/eureka/apps > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Eureka está disponible${NC}"
else
    echo -e "${YELLOW}⚠️  Eureka aún no responde (puede tardar más)${NC}"
fi

# 3. Levantar los 6 servicios principales
echo -e "${BLUE}[3/3]${NC} Levantando los 6 servicios principales..."
docker compose -f docker-compose/compose.yml up -d \
    api-gateway-container \
    user-service-container \
    product-service-container \
    order-service-container

echo -e "${YELLOW}⏳ Esperando 60 segundos a que los servicios se registren...${NC}"
sleep 60

echo ""
echo -e "${GREEN}✅ Todos los servicios han sido levantados${NC}"
echo ""
echo "📊 Verificando estado de los servicios registrados en Eureka..."
echo ""

# Mostrar servicios registrados
SERVICES=$(curl -s http://localhost:8761/eureka/apps | grep -oP '(?<=<app>)[^<]+' | sort)
echo -e "${BLUE}Servicios registrados:${NC}"
echo "$SERVICES" | while read service; do
    echo -e "  ${GREEN}✓${NC} $service"
done

echo ""
echo "🌐 URLs de acceso:"
echo -e "  ${BLUE}API Gateway:${NC}        http://localhost:8080/app/actuator/health"
echo -e "  ${BLUE}Eureka:${NC}              http://localhost:8761"
echo -e "  ${BLUE}Config Server:${NC}       http://localhost:9296"
echo -e "  ${BLUE}Zipkin:${NC}              http://localhost:9411"
echo ""
echo "🧪 Pruebas básicas:"
echo "  curl http://localhost:8080/app/api/users"
echo "  curl http://localhost:8080/app/api/products"
echo "  curl http://localhost:8080/app/api/orders"
echo ""
echo -e "${GREEN}¡Listo para usar!${NC}"
