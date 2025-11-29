#!/bin/bash

# Script para pushear cada servicio a su repositorio individual en GitHub
# Uso: ./push-to-individual-repos.sh

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuración
GITHUB_ORG="Ecommerce-Microservice-Lab"
GITHUB_USER="OscarMURA"  # Cambia esto por tu usuario de GitHub

# Directorio base
BASE_DIR="$(pwd)"
TEMP_DIR="/tmp/ecommerce-push"

# Lista de servicios a pushear
SERVICES=(
    "service-discovery"
    "api-gateway"
    "cloud-config"
    "favourite-service"
    "order-service"
    "payment-service"
    "product-service"
    "shipping-service"
    "user-service"
    "e2e-tests"
    "performance-tests"
)

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}Push de servicios a repositorios individuales${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""

# Función para pushear un servicio
push_service() {
    local service=$1
    local service_dir="${BASE_DIR}/${service}"
    local temp_service_dir="${TEMP_DIR}/${service}"
    local repo_url="https://github.com/${GITHUB_ORG}/${service}.git"
    
    echo -e "${YELLOW}Procesando: ${service}${NC}"
    
    # Verificar si existe la carpeta del servicio
    if [ ! -d "$service_dir" ]; then
        echo -e "${RED}  ✗ La carpeta ${service} no existe. Saltando...${NC}"
        return 1
    fi
    
    # Crear directorio temporal
    mkdir -p "$temp_service_dir"
    
    # Copiar contenido del servicio
    echo "  → Copiando archivos..."
    cp -r "${service_dir}/." "$temp_service_dir/"
    
    # Ir al directorio temporal
    cd "$temp_service_dir" || return 1
    
    # Remover .git si existe
    rm -rf .git
    
    # Inicializar repositorio
    echo "  → Inicializando repositorio..."
    git init -b master
    
    # Configurar git (usa tu configuración global si ya está configurada)
    git config user.name "$(git config --global user.name)"
    git config user.email "$(git config --global user.email)"
    
    # Agregar todos los archivos
    git add .
    
    # Hacer commit
    echo "  → Creando commit..."
    git commit -m "Update ${service} from main repository"
    
    # Agregar remote
    git remote add origin "$repo_url"
    
    # Push con force
    echo "  → Pusheando a GitHub..."
    if git push -f origin master; then
        echo -e "${GREEN}  ✓ ${service} pusheado exitosamente${NC}"
    else
        echo -e "${RED}  ✗ Error al pushear ${service}${NC}"
        echo -e "${YELLOW}  Verifica que el repositorio exista: ${repo_url}${NC}"
        return 1
    fi
    
    # Volver al directorio base
    cd "$BASE_DIR" || return 1
    
    echo ""
}

# Crear directorio temporal
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

# Verificar que estamos en el directorio correcto
if [ ! -d "service-discovery" ]; then
    echo -e "${RED}Error: No estás en el directorio raíz del proyecto${NC}"
    echo "Por favor ejecuta este script desde: ecommerce-microservice-backend-app/"
    exit 1
fi

# Contador de éxitos y fallos
success_count=0
fail_count=0

# Procesar cada servicio
for service in "${SERVICES[@]}"; do
    if push_service "$service"; then
        ((success_count++))
    else
        ((fail_count++))
    fi
done

# Limpiar directorio temporal
echo -e "${YELLOW}Limpiando archivos temporales...${NC}"
rm -rf "$TEMP_DIR"

# Resumen
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}Resumen:${NC}"
echo -e "${GREEN}  ✓ Exitosos: ${success_count}${NC}"
if [ $fail_count -gt 0 ]; then
    echo -e "${RED}  ✗ Fallidos: ${fail_count}${NC}"
fi
echo -e "${GREEN}======================================${NC}"

exit 0
