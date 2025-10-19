#!/bin/bash

# Script para detener y limpiar todos los servicios
# Uso: ./stop-services.sh

echo "🛑 Deteniendo servicios..."

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Detener compose.yml
echo -e "${YELLOW}Deteniendo servicios principales...${NC}"
docker compose -f docker-compose/compose.yml down

# Detener core.yml
echo -e "${YELLOW}Deteniendo servicios core...${NC}"
docker compose -f docker-compose/core.yml down

echo ""
echo -e "${GREEN}✅ Todos los servicios han sido detenidos${NC}"
echo ""
echo "Contenedores activos:"
docker ps --format "table {{.Names}}\t{{.Status}}" | head -1
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "api-gateway|user-service|product-service|order-service|service-discovery|cloud-config|zipkin" || echo "  (ninguno)"

echo ""
echo "Para eliminar volúmenes también: docker compose -f docker-compose/core.yml down -v && docker compose -f docker-compose/compose.yml down -v"
