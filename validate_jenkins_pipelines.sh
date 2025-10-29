#!/bin/bash

# Script para validar que todos los pipelines de Jenkins estén correctamente configurados

set -euo pipefail

echo "🔍 Validando pipelines de Jenkins..."

# Directorio base del proyecto
PROJECT_ROOT="/home/oscar/Documents/Taller 2 Ingesoft/ecommerce-microservice-backend-app"
cd "$PROJECT_ROOT"

# Servicios esperados
EXPECTED_SERVICES=(
  "api-gateway"
  "cloud-config"
  "favourite-service"
  "order-service"
  "payment-service"
  "product-service"
  "service-discovery"
  "shipping-service"
  "user-service"
)

# Función para validar un pipeline
validate_pipeline() {
  local service="$1"
  local pipeline_file="$service/jenkins/$service-dev.groovy"
  
  echo "📋 Validando $service..."
  
  # Verificar que el archivo existe
  if [ ! -f "$pipeline_file" ]; then
    echo "❌ Error: Pipeline no encontrado: $pipeline_file"
    return 1
  fi
  
  # Verificar que contiene el nombre del servicio correcto
  if ! grep -q "SERVICE_NAME = \"$service\"" "$pipeline_file"; then
    echo "❌ Error: SERVICE_NAME no coincide en $pipeline_file"
    return 1
  fi
  
  # Verificar que contiene las etapas principales
  local required_stages=(
    "Validate Branch"
    "Checkout Pipeline Repo"
    "Ensure VM Available"
    "Sync Repository on VM"
    "Unit Tests"
    "Integration Tests"
    "Recolectar Reportes"
    "Build and Push Docker Image"
    "Deploy to Kubernetes"
  )
  
  for stage in "${required_stages[@]}"; do
    if ! grep -q "stage('$stage')" "$pipeline_file"; then
      echo "❌ Error: Etapa '$stage' no encontrada en $pipeline_file"
      return 1
    fi
  done
  
  # Verificar que usa el script de despliegue correcto
  if ! grep -q "deploy-single-service-to-gke.sh" "$pipeline_file"; then
    echo "❌ Error: Script de despliegue incorrecto en $pipeline_file"
    return 1
  fi
  
  # Verificar que el nombre del servicio aparece en los mensajes de error
  if ! grep -q "$service-dev" "$pipeline_file"; then
    echo "❌ Error: Nombre del servicio no aparece en mensajes de error en $pipeline_file"
    return 1
  fi
  
  echo "✅ $service: Pipeline válido"
  return 0
}

# Función para validar estructura de directorios
validate_structure() {
  echo "📁 Validando estructura de directorios..."
  
  for service in "${EXPECTED_SERVICES[@]}"; do
    if [ ! -d "$service" ]; then
      echo "❌ Error: Directorio del servicio no encontrado: $service"
      return 1
    fi
    
    if [ ! -d "$service/jenkins" ]; then
      echo "❌ Error: Directorio jenkins no encontrado en: $service"
      return 1
    fi
  done
  
  echo "✅ Estructura de directorios válida"
  return 0
}

# Función para validar scripts de despliegue
validate_deployment_scripts() {
  echo "🚀 Validando scripts de despliegue..."
  
  local deploy_script="jenkins/scripts/deploy-single-service-to-gke.sh"
  
  if [ ! -f "$deploy_script" ]; then
    echo "❌ Error: Script de despliegue no encontrado: $deploy_script"
    return 1
  fi
  
  if [ ! -x "$deploy_script" ]; then
    echo "❌ Error: Script de despliegue no es ejecutable: $deploy_script"
    return 1
  fi
  
  # Verificar que el script contiene las variables requeridas
  local required_vars=(
    "GCP_PROJECT_ID"
    "GKE_CLUSTER_NAME"
    "K8S_SERVICE_NAME"
    "K8S_IMAGE_REGISTRY"
  )
  
  for var in "${required_vars[@]}"; do
    if ! grep -q "$var" "$deploy_script"; then
      echo "❌ Error: Variable $var no encontrada en script de despliegue"
      return 1
    fi
  done
  
  echo "✅ Scripts de despliegue válidos"
  return 0
}

# Función para mostrar resumen
show_summary() {
  echo ""
  echo "📊 Resumen de validación:"
  echo "=========================="
  echo "✅ Pipelines creados: ${#EXPECTED_SERVICES[@]}"
  echo "✅ Servicios configurados:"
  for service in "${EXPECTED_SERVICES[@]}"; do
    echo "   - $service"
  done
  echo ""
  echo "📋 Próximos pasos:"
  echo "1. Crear pipelines multibranch en Jenkins para cada servicio"
  echo "2. Configurar credenciales requeridas"
  echo "3. Configurar la VM de integración"
  echo "4. Probar los pipelines con un commit a develop"
  echo ""
  echo "📚 Documentación: jenkins/README-INDIVIDUAL-PIPELINES.md"
}

# Ejecutar validaciones
main() {
  local errors=0
  
  # Validar estructura
  if ! validate_structure; then
    ((errors++))
  fi
  
  # Validar scripts de despliegue
  if ! validate_deployment_scripts; then
    ((errors++))
  fi
  
  # Validar cada pipeline
  for service in "${EXPECTED_SERVICES[@]}"; do
    if ! validate_pipeline "$service"; then
      ((errors++))
    fi
  done
  
  # Mostrar resumen
  show_summary
  
  if [ $errors -eq 0 ]; then
    echo "🎉 ¡Todos los pipelines están correctamente configurados!"
    exit 0
  else
    echo "❌ Se encontraron $errors errores. Revisar los mensajes anteriores."
    exit 1
  fi
}

# Ejecutar validación
main "$@"
