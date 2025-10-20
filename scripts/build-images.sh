#!/bin/bash

# Build Images Script for Ecommerce Microservices
# This script builds Docker images for all microservices with custom tags

set -e  # Exit on any error

echo "🐳 Building Docker Images for Ecommerce Microservices"
echo "========================================================"

PROJECT_VERSION="0.1.0"
ENVIRONMENT="dev"
REGISTRY="ecommerce"

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Array of services to build
declare -a SERVICES=(
    "api-gateway:8080"
    "user-service:8700"
    "product-service:8500"
    "order-service:8300"
    "payment-service:8400"
    "favourite-service:8800"
    "shipping-service:8600"
    "proxy-client:8900"
    "service-discovery:8761"
    "cloud-config:9296"
)

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo -e "${YELLOW}Project Root: ${PROJECT_ROOT}${NC}\n"

# Function to build a service
build_service() {
    local service_name="$1"
    local service_port="$2"
    local image_tag="${REGISTRY}-${service_name}:latest"
    
    echo -e "${BLUE}Building ${service_name}...${NC}"
    echo "  Image: ${image_tag}"
    echo "  Port: ${service_port}"
    
    docker build \
        -t "${image_tag}" \
        -t "${REGISTRY}-${service_name}:${PROJECT_VERSION}" \
        --build-arg PROJECT_VERSION="${PROJECT_VERSION}" \
        --build-arg ENVIRONMENT="${ENVIRONMENT}" \
        -f "${service_name}/Dockerfile" \
        . 2>&1 | tail -20
    
    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully built ${image_tag}${NC}\n"
    else
        echo -e "${RED}✗ Failed to build ${image_tag}${NC}\n"
        exit 1
    fi
}

# Build all services
echo -e "${YELLOW}Step 1: Building Microservice Images${NC}\n"

for service in "${SERVICES[@]}"; do
    IFS=':' read -r service_name service_port <<< "$service"
    build_service "$service_name" "$service_port"
done

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ All images built successfully!${NC}"
echo -e "${GREEN}========================================${NC}\n"

# Display summary
echo -e "${YELLOW}Built Images Summary:${NC}"
echo "====================="
for service in "${SERVICES[@]}"; do
    IFS=':' read -r service_name service_port <<< "$service"
    echo -e "  ${GREEN}${REGISTRY}-${service_name}:${PROJECT_VERSION}${NC}"
done

echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Create network (if needed): docker network create microservices_network"
echo "2. Start services: docker compose -f docker-compose/core.yml up -d"
echo "3. Wait 50 seconds, then: docker compose -f docker-compose/compose.yml up -d"
echo "4. Verify: curl http://localhost:8761/eureka/apps"
echo "5. Check Docker images: docker images | grep ecommerce"
