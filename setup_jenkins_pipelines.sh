#!/bin/bash

# Script para configurar automáticamente los pipelines de Jenkins
# Requiere Jenkins CLI configurado

set -euo pipefail

echo "🚀 Configurando pipelines de Jenkins para microservicios..."

# Configuración
JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
JENKINS_USER="${JENKINS_USER:-admin}"
JENKINS_PASSWORD="${JENKINS_PASSWORD:-}"
JENKINS_CLI="${JENKINS_CLI:-jenkins-cli.jar}"

# Servicios
SERVICES=(
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

# Función para crear un pipeline multibranch
create_multibranch_pipeline() {
  local service="$1"
  local job_name="${service}-dev"
  
  echo "📋 Creando pipeline multibranch para $service..."
  
  # Crear XML de configuración del job
  cat > "/tmp/${job_name}.xml" << EOF
<?xml version='1.1' encoding='UTF-8'?>
<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject plugin="workflow-multibranch@2.26">
  <actions/>
  <description>Pipeline de desarrollo para ${service}</description>
  <properties>
    <org.jenkinsci.plugins.pipeline.modeldefinition.config.FolderLibraries plugin="pipeline-model-definition@2.2118.v31fd5b_9944b_5">
      <libraries/>
    </org.jenkinsci.plugins.pipeline.modeldefinition.config.FolderLibraries>
  </properties>
  <folderViews class="jenkins.branch.MultiBranchProjectViewHolder" plugin="branch-api@2.8.0">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </folderViews>
  <healthMetrics>
    <com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric plugin="cloudbees-folder@6.18">
      <nonRecursive>false</nonRecursive>
    </com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric>
  </healthMetrics>
  <icon class="jenkins.branch.MetadataActionFolderIcon" plugin="branch-api@2.8.0">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </icon>
  <orphanedItemStrategy class="com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy" plugin="cloudbees-folder@6.18">
    <pruneDeadBranches>true</pruneDeadBranches>
    <daysToKeep>-1</daysToKeep>
    <numToKeep>-1</numToKeep>
  </orphanedItemStrategy>
  <triggers>
    <com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger plugin="cloudbees-folder@6.18">
      <spec>H/15 * * * *</spec>
      <interval>900000</interval>
    </com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger>
  </triggers>
  <disabled>false</disabled>
  <sources class="jenkins.branch.MultiBranchProject\$BranchSourceList" plugin="branch-api@2.8.0">
    <data>
      <jenkins.branch.BranchSource>
        <source class="jenkins.plugins.git.GitSCMSource" plugin="git@4.11.3">
          <id>github-source</id>
          <remote>https://github.com/OscarMURA/ecommerce-microservice-backend-app.git</remote>
          <credentialsId>github-token</credentialsId>
          <traits>
            <jenkins.plugins.git.traits.BranchDiscoveryTrait>
              <strategyId>1</strategyId>
            </jenkins.plugins.git.traits.BranchDiscoveryTrait>
            <jenkins.plugins.git.traits.OriginPullRequestDiscoveryTrait>
              <strategyId>1</strategyId>
            </jenkins.plugins.git.traits.OriginPullRequestDiscoveryTrait>
            <jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait plugin="scm-api@2.6.4">
              <includes>develop,feat/*</includes>
              <excludes></excludes>
            </jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait>
          </traits>
        </source>
        <strategy class="jenkins.branch.DefaultBranchPropertyStrategy" plugin="branch-api@2.8.0">
          <props class="empty-list"/>
        </strategy>
      </jenkins.branch.BranchSource>
    </data>
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </sources>
  <factory class="org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory" plugin="workflow-multibranch@2.26">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
    <scriptPath>${service}/jenkins/${service}-dev.groovy</scriptPath>
  </factory>
</org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
EOF

  # Crear el job usando Jenkins CLI
  if [ -f "$JENKINS_CLI" ]; then
    java -jar "$JENKINS_CLI" -s "$JENKINS_URL" -auth "$JENKINS_USER:$JENKINS_PASSWORD" create-job "$job_name" < "/tmp/${job_name}.xml"
    echo "✅ Pipeline creado: $job_name"
  else
    echo "⚠️  Jenkins CLI no encontrado. Crear manualmente el pipeline: $job_name"
    echo "   Script path: ${service}/jenkins/${service}-dev.groovy"
  fi
  
  # Limpiar archivo temporal
  rm -f "/tmp/${job_name}.xml"
}

# Función para verificar conectividad con Jenkins
check_jenkins_connection() {
  echo "🔍 Verificando conexión con Jenkins..."
  
  if [ -f "$JENKINS_CLI" ]; then
    if java -jar "$JENKINS_CLI" -s "$JENKINS_URL" -auth "$JENKINS_USER:$JENKINS_PASSWORD" who-am-i >/dev/null 2>&1; then
      echo "✅ Conexión con Jenkins exitosa"
      return 0
    else
      echo "❌ Error: No se pudo conectar con Jenkins"
      echo "   URL: $JENKINS_URL"
      echo "   Usuario: $JENKINS_USER"
      return 1
    fi
  else
    echo "⚠️  Jenkins CLI no encontrado en: $JENKINS_CLI"
    echo "   Descargar desde: $JENKINS_URL/jnlpJars/jenkins-cli.jar"
    return 1
  fi
}

# Función para mostrar instrucciones de configuración manual
show_manual_instructions() {
  echo ""
  echo "📋 Instrucciones para configuración manual:"
  echo "=========================================="
  echo ""
  echo "1. Ir a Jenkins → New Item → Multibranch Pipeline"
  echo "2. Para cada servicio, crear un pipeline con:"
  echo "   - Nombre: {servicio}-dev"
  echo "   - Branch Sources: Git"
  echo "   - Repository URL: https://github.com/OscarMURA/ecommerce-microservice-backend-app.git"
  echo "   - Credentials: github-token"
  echo "   - Behaviors:"
  echo "     * Add: Filter by name (with wildcards)"
  echo "     * Include: develop, feat/*"
  echo "   - Build Configuration:"
  echo "     * Mode: by Jenkinsfile"
  echo "     * Script Path: {servicio}/jenkins/{servicio}-dev.groovy"
  echo ""
  echo "3. Servicios a configurar:"
  for service in "${SERVICES[@]}"; do
    echo "   - $service-dev"
  done
  echo ""
  echo "4. Credenciales requeridas:"
  echo "   - digitalocean-token (Secret text)"
  echo "   - integration-vm-password (Secret text)"
  echo "   - gcp-project-id (Secret text)"
  echo "   - gcp-service-account (Secret file)"
  echo "   - github-token (Secret text)"
  echo ""
  echo "5. Documentación completa: jenkins/README-INDIVIDUAL-PIPELINES.md"
}

# Función principal
main() {
  echo "🔧 Configurando pipelines de Jenkins..."
  echo "Jenkins URL: $JENKINS_URL"
  echo "Jenkins User: $JENKINS_USER"
  echo ""
  
  # Verificar conexión con Jenkins
  if check_jenkins_connection; then
    echo ""
    echo "🚀 Creando pipelines automáticamente..."
    
    # Crear cada pipeline
    for service in "${SERVICES[@]}"; do
      create_multibranch_pipeline "$service"
    done
    
    echo ""
    echo "✅ Todos los pipelines han sido creados exitosamente!"
    echo ""
    echo "📋 Próximos pasos:"
    echo "1. Verificar que las credenciales estén configuradas"
    echo "2. Configurar la VM de integración"
    echo "3. Hacer un commit a develop para probar los pipelines"
    
  else
    echo ""
    show_manual_instructions
  fi
}

# Verificar argumentos
if [ $# -gt 0 ]; then
  case "$1" in
    --help|-h)
      echo "Uso: $0 [opciones]"
      echo ""
      echo "Variables de entorno:"
      echo "  JENKINS_URL      - URL de Jenkins (default: http://localhost:8080)"
      echo "  JENKINS_USER     - Usuario de Jenkins (default: admin)"
      echo "  JENKINS_PASSWORD - Contraseña de Jenkins"
      echo "  JENKINS_CLI      - Ruta al jenkins-cli.jar (default: jenkins-cli.jar)"
      echo ""
      echo "Ejemplo:"
      echo "  JENKINS_URL=http://jenkins.example.com JENKINS_PASSWORD=mypass $0"
      exit 0
      ;;
    *)
      echo "Opción desconocida: $1"
      echo "Usar --help para ver la ayuda"
      exit 1
      ;;
  esac
fi

# Ejecutar función principal
main "$@"
