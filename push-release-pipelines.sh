#!/bin/bash

# =============================================================================
# Script para hacer push de los pipelines de release y crear el primer tag
# =============================================================================

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Lista de servicios
SERVICES=(
    "service-discovery"
    "payment-service"
    "product-service"
    "user-service"
    "order-service"
    "shipping-service"
    "favourite-service"
)

# Organización de GitHub
GITHUB_ORG="Ecommerce-Microservice-Lab"

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}  Push Release Pipelines & Create v1.0.0    ${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

# Función para procesar cada servicio
process_service() {
    local SERVICE=$1
    local SERVICE_DIR="${SERVICE}"
    
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}📦 Procesando: ${SERVICE}${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    if [ ! -d "$SERVICE_DIR" ]; then
        echo -e "${RED}❌ Directorio $SERVICE_DIR no encontrado${NC}"
        return 1
    fi
    
    cd "$SERVICE_DIR"
    
    # Verificar que es un repo git
    if [ ! -d ".git" ]; then
        echo -e "${RED}❌ No es un repositorio git${NC}"
        cd ..
        return 1
    fi
    
    # Verificar remote
    REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
    if [ -z "$REMOTE_URL" ]; then
        echo -e "${YELLOW}⚠️  Configurando remote origin...${NC}"
        git remote add origin "https://github.com/${GITHUB_ORG}/${SERVICE}.git"
    fi
    
    echo -e "${GREEN}📁 Archivos a agregar:${NC}"
    echo "   - .github/workflows/release.yml"
    echo "   - CHANGELOG.md"
    echo "   - ROLLBACK.md"
    
    # Agregar archivos
    git add .github/workflows/release.yml CHANGELOG.md ROLLBACK.md 2>/dev/null || true
    
    # Verificar si hay cambios
    if git diff --cached --quiet; then
        echo -e "${YELLOW}⚠️  No hay cambios nuevos que commitear${NC}"
    else
        # Commit
        git commit -m "feat: add release pipeline with Change Management

- Add GitHub Actions workflow for automated releases
- Add CHANGELOG.md with Keep a Changelog format
- Add ROLLBACK.md with rollback procedures
- Implement semantic versioning
- Add automatic release notes generation

Part of: Change Management & Release Notes implementation"
        
        echo -e "${GREEN}✅ Commit creado${NC}"
    fi
    
    # Push
    echo -e "${BLUE}📤 Pushing to origin/master...${NC}"
    git push origin master || git push origin main || echo -e "${YELLOW}⚠️  Push failed, may need manual push${NC}"
    
    # Crear tag v1.0.0 si no existe
    if git rev-parse "v1.0.0" >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Tag v1.0.0 ya existe${NC}"
    else
        echo -e "${BLUE}🏷️  Creando tag v1.0.0...${NC}"
        git tag -a "v1.0.0" -m "Release v1.0.0 - Initial Release

## 🎉 First Official Release

This is the initial release of ${SERVICE}.

### Features
- Full microservice functionality
- Docker support
- Kubernetes deployment ready
- Health check endpoints
- Comprehensive API documentation

### Change Management
- Automated release pipeline
- Semantic versioning
- Automatic release notes generation
- Documented rollback procedures"
        
        git push origin "v1.0.0"
        echo -e "${GREEN}✅ Tag v1.0.0 creado y pusheado${NC}"
    fi
    
    cd ..
    echo -e "${GREEN}✅ ${SERVICE} completado${NC}"
    echo ""
}

# Menú principal
echo "Selecciona una opción:"
echo "1) Procesar TODOS los servicios"
echo "2) Procesar un servicio específico"
echo "3) Solo crear tags v1.0.0 (sin push de archivos)"
echo "4) Ver estado de los servicios"
echo ""
read -p "Opción [1-4]: " OPTION

case $OPTION in
    1)
        echo ""
        echo -e "${BLUE}Procesando todos los servicios...${NC}"
        for SERVICE in "${SERVICES[@]}"; do
            process_service "$SERVICE"
        done
        ;;
    2)
        echo ""
        echo "Servicios disponibles:"
        for i in "${!SERVICES[@]}"; do
            echo "  $((i+1))) ${SERVICES[$i]}"
        done
        echo ""
        read -p "Número del servicio: " SERVICE_NUM
        INDEX=$((SERVICE_NUM-1))
        if [ $INDEX -ge 0 ] && [ $INDEX -lt ${#SERVICES[@]} ]; then
            process_service "${SERVICES[$INDEX]}"
        else
            echo -e "${RED}Opción inválida${NC}"
        fi
        ;;
    3)
        echo ""
        echo -e "${BLUE}Creando tags v1.0.0 en todos los servicios...${NC}"
        for SERVICE in "${SERVICES[@]}"; do
            if [ -d "$SERVICE/.git" ]; then
                cd "$SERVICE"
                if ! git rev-parse "v1.0.0" >/dev/null 2>&1; then
                    git tag -a "v1.0.0" -m "Release v1.0.0 - Initial Release"
                    git push origin "v1.0.0" 2>/dev/null || echo "Push tag failed for $SERVICE"
                    echo -e "${GREEN}✅ Tag v1.0.0 creado en $SERVICE${NC}"
                else
                    echo -e "${YELLOW}⚠️  Tag ya existe en $SERVICE${NC}"
                fi
                cd ..
            fi
        done
        ;;
    4)
        echo ""
        echo -e "${BLUE}Estado de los servicios:${NC}"
        echo ""
        for SERVICE in "${SERVICES[@]}"; do
            if [ -d "$SERVICE" ]; then
                cd "$SERVICE"
                echo -e "${YELLOW}📦 ${SERVICE}${NC}"
                
                # Verificar archivos
                [ -f ".github/workflows/release.yml" ] && echo "   ✅ release.yml" || echo "   ❌ release.yml"
                [ -f "CHANGELOG.md" ] && echo "   ✅ CHANGELOG.md" || echo "   ❌ CHANGELOG.md"
                [ -f "ROLLBACK.md" ] && echo "   ✅ ROLLBACK.md" || echo "   ❌ ROLLBACK.md"
                
                # Verificar tag
                if [ -d ".git" ]; then
                    if git rev-parse "v1.0.0" >/dev/null 2>&1; then
                        echo "   ✅ Tag v1.0.0 existe"
                    else
                        echo "   ❌ Tag v1.0.0 no existe"
                    fi
                fi
                
                cd ..
                echo ""
            fi
        done
        ;;
    *)
        echo -e "${RED}Opción inválida${NC}"
        ;;
esac

echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}  ¡Proceso completado!                       ${NC}"
echo -e "${GREEN}=============================================${NC}"
echo ""
echo "📝 Próximos pasos:"
echo "   1. Verifica los releases en GitHub: https://github.com/${GITHUB_ORG}"
echo "   2. Cada servicio debería mostrar 'v1.0.0 Latest' en Releases"
echo "   3. El workflow se ejecutará automáticamente con nuevos tags"
echo ""
echo "🏷️  Para crear un nuevo release:"
echo "   git tag -a v1.1.0 -m 'Release v1.1.0'"
echo "   git push origin v1.1.0"
echo ""
