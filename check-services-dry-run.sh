#!/bin/bash

# Script DRY-RUN para ver qué se pusheará sin hacer cambios reales
# Útil para verificar antes de ejecutar el push real

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuración
GITHUB_ORG="Ecommerce-Microservice-Lab"

# Directorio base
BASE_DIR="$(pwd)"

# Lista de servicios
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

echo -e "${CYAN}======================================${NC}"
echo -e "${CYAN}DRY-RUN: Verificación de servicios${NC}"
echo -e "${CYAN}======================================${NC}"
echo ""
echo -e "${YELLOW}Este es un DRY-RUN - NO se harán cambios reales${NC}"
echo ""

# Función para analizar un servicio
analyze_service() {
    local service=$1
    local service_dir="${BASE_DIR}/${service}"
    local repo_url="https://github.com/${GITHUB_ORG}/${service}.git"
    
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Servicio: ${service}${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    # Verificar si existe la carpeta
    if [ ! -d "$service_dir" ]; then
        echo -e "${RED}  ✗ CARPETA NO EXISTE${NC}"
        echo ""
        return 1
    fi
    
    echo -e "${GREEN}  ✓ Carpeta existe${NC}"
    echo -e "  📁 Ruta: ${service_dir}"
    echo -e "  🔗 Repositorio destino: ${repo_url}"
    echo ""
    
    # Contar archivos
    local file_count=$(find "$service_dir" -type f | wc -l)
    local dir_count=$(find "$service_dir" -type d | wc -l)
    
    echo -e "  📊 Estadísticas:"
    echo -e "     • Archivos: ${file_count}"
    echo -e "     • Directorios: ${dir_count}"
    
    # Calcular tamaño
    local size=$(du -sh "$service_dir" 2>/dev/null | cut -f1)
    echo -e "     • Tamaño total: ${size}"
    echo ""
    
    # Listar archivos principales
    echo -e "  📄 Archivos principales:"
    ls -lh "$service_dir" | grep "^-" | head -15 | awk '{printf "     • %-30s %6s\n", $9, $5}'
    echo ""
    
    # Listar directorios
    echo -e "  📂 Directorios:"
    ls -lh "$service_dir" | grep "^d" | awk '{printf "     • %s\n", $9}' | grep -v "^\.$"
    echo ""
    
    # Verificar si tiene pom.xml (Maven project)
    if [ -f "$service_dir/pom.xml" ]; then
        echo -e "  ${GREEN}✓ Proyecto Maven detectado${NC}"
        # Extraer información del pom.xml si existe
        if command -v xmllint &> /dev/null; then
            local artifactId=$(xmllint --xpath "//project/artifactId/text()" "$service_dir/pom.xml" 2>/dev/null)
            local version=$(xmllint --xpath "//project/version/text()" "$service_dir/pom.xml" 2>/dev/null)
            if [ ! -z "$artifactId" ]; then
                echo -e "     • ArtifactId: ${artifactId}"
            fi
            if [ ! -z "$version" ]; then
                echo -e "     • Version: ${version}"
            fi
        fi
    fi
    
    # Verificar si tiene Dockerfile
    if [ -f "$service_dir/Dockerfile" ]; then
        echo -e "  ${GREEN}✓ Dockerfile encontrado${NC}"
    fi
    
    # Verificar si tiene README
    if [ -f "$service_dir/README.md" ]; then
        echo -e "  ${GREEN}✓ README.md encontrado${NC}"
    fi
    
    # Verificar Jenkins
    if [ -d "$service_dir/jenkins" ]; then
        echo -e "  ${GREEN}✓ Configuración Jenkins encontrada${NC}"
        local jenkins_files=$(find "$service_dir/jenkins" -type f | wc -l)
        echo -e "     • Archivos Jenkins: ${jenkins_files}"
    fi
    
    echo ""
    
    return 0
}

# Verificar directorio
if [ ! -d "service-discovery" ]; then
    echo -e "${RED}Error: No estás en el directorio raíz del proyecto${NC}"
    echo "Por favor ejecuta este script desde: ecommerce-microservice-backend-app/"
    exit 1
fi

# Contadores
exist_count=0
missing_count=0

# Analizar cada servicio
for service in "${SERVICES[@]}"; do
    if analyze_service "$service"; then
        ((exist_count++))
    else
        ((missing_count++))
    fi
done

# Resumen final
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}RESUMEN DRY-RUN:${NC}"
echo -e "${GREEN}  ✓ Servicios encontrados: ${exist_count}${NC}"
if [ $missing_count -gt 0 ]; then
    echo -e "${RED}  ✗ Servicios no encontrados: ${missing_count}${NC}"
fi
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${YELLOW}Para ejecutar el push real, usa:${NC}"
echo -e "  ${GREEN}./push-to-individual-repos-interactive.sh${NC}  (recomendado)"
echo -e "  ${GREEN}./push-to-individual-repos.sh${NC}             (automático)"
echo ""

exit 0
