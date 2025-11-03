pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    string(name: 'DOCKER_IMAGE_TAG', defaultValue: 'latest', description: 'Tag de la imagen en Docker Hub a desplegar')
    string(name: 'GKE_CLUSTER_NAME', defaultValue: 'ecommerce-prod-gke', description: 'Nombre del cluster GKE de producción')
    string(name: 'GKE_LOCATION', defaultValue: 'us-central1-a', description: 'Zona o región del cluster GKE')
    string(name: 'K8S_NAMESPACE', defaultValue: 'prod', description: 'Namespace de Kubernetes donde se desplegará (PRODUCCIÓN)')
    string(name: 'REPLICA_COUNT', defaultValue: '2', description: 'Número de réplicas por servicio (recomendado mínimo 2 en prod)')
    booleanParam(name: 'DEPLOY_SERVICE_DISCOVERY', defaultValue: true, description: 'Desplegar service-discovery')
    booleanParam(name: 'DEPLOY_USER_SERVICE', defaultValue: true, description: 'Desplegar user-service')
    booleanParam(name: 'DEPLOY_PRODUCT_SERVICE', defaultValue: true, description: 'Desplegar product-service')
    booleanParam(name: 'DEPLOY_ORDER_SERVICE', defaultValue: true, description: 'Desplegar order-service')
    booleanParam(name: 'DEPLOY_SHIPPING_SERVICE', defaultValue: true, description: 'Desplegar shipping-service')
    booleanParam(name: 'DEPLOY_PAYMENT_SERVICE', defaultValue: true, description: 'Desplegar payment-service')
    booleanParam(name: 'DEPLOY_FAVOURITE_SERVICE', defaultValue: true, description: 'Desplegar favourite-service')
    booleanParam(name: 'FORCE_DEPLOY_ALL', defaultValue: false, description: 'Forzar despliegue de todos los servicios seleccionados')
    string(name: 'PERF_TEST_USERS', defaultValue: '50', description: 'Número de usuarios concurrentes para pruebas de rendimiento')
    string(name: 'PERF_TEST_SPAWN_RATE', defaultValue: '5', description: 'Usuarios creados por segundo (spawn rate)')
    string(name: 'PERF_TEST_DURATION', defaultValue: '3m', description: 'Duración de las pruebas de rendimiento (ej: 1m30s, 5m)')
  }

  environment {
    GITHUB_TOKEN = credentials('github-token')
  }

  stages {

    stage('Validate Branch') {
      steps {
        script {
          def branch = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').replaceFirst('^origin/', '')
          if (!branch) {
            echo "⚠️ Rama no disponible (posible indexado). Se omite validación."
            return
          }
          if (branch != 'master' && branch != 'main') {
            error "❌ All-Services-Prod solo se ejecuta en rama master o main (rama actual: '${branch}')."
          }
          echo "✅ Branch validada: ${branch}"
          env.PIPELINE_BRANCH = branch
        }
      }
    }

    stage('Checkout') {
      steps {
        checkout scm
        script { 
          echo "📁 Workspace: ${env.WORKSPACE}" 
        }
      }
    }

    stage('Generate Release Notes') {
      steps {
        script {
          echo ""
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          echo "📝 GENERANDO RELEASE NOTES"
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          echo ""
          
          withEnv([
            "BUILD_NUMBER=${env.BUILD_NUMBER}",
            "BRANCH_NAME=${env.PIPELINE_BRANCH ?: 'master'}",
            "GIT_COMMIT=${env.GIT_COMMIT}",
            "ENVIRONMENT=prod"
          ]) {
            sh '''
set -e

# Dar permisos de ejecución al script
chmod +x jenkins/scripts/generate-release-notes.sh

# Generar las release notes
./jenkins/scripts/generate-release-notes.sh "RELEASE_NOTES_${BUILD_NUMBER}.md"

echo ""
echo "✅ Release Notes generadas exitosamente"
'''
          }
          
          // Archivar las release notes como artefacto
          archiveArtifacts artifacts: "RELEASE_NOTES_${env.BUILD_NUMBER}.md", allowEmptyArchive: false
          
          // Leer y mostrar en consola
          def releaseNotesContent = readFile("RELEASE_NOTES_${env.BUILD_NUMBER}.md")
          echo ""
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          echo "📋 CONTENIDO DE LAS RELEASE NOTES:"
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          echo releaseNotesContent
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        }
      }
    }

    stage('Detect Service Changes') {
      steps {
        script {
          // Mapa de servicios con sus puertos (service-discovery PRIMERO)
          def servicesData = [
            'service-discovery': '8761',
            'user-service': '8085',
            'product-service': '8083',
            'order-service': '8081',
            'shipping-service': '8084',
            'payment-service': '8082',
            'favourite-service': '8086'
          ]

          // Configuración de qué servicios desplegar
          def deployFlags = [
            'service-discovery': params.DEPLOY_SERVICE_DISCOVERY,
            'user-service': params.DEPLOY_USER_SERVICE,
            'product-service': params.DEPLOY_PRODUCT_SERVICE,
            'order-service': params.DEPLOY_ORDER_SERVICE,
            'shipping-service': params.DEPLOY_SHIPPING_SERVICE,
            'payment-service': params.DEPLOY_PAYMENT_SERVICE,
            'favourite-service': params.DEPLOY_FAVOURITE_SERVICE
          ]

          // Rastrear qué servicios cambiaron
          def servicesChanged = [:]
          servicesData.each { name, port ->
            servicesChanged[name] = false
          }

          def changedFiles = []

          echo "🔍 Detectando cambios en servicios..."

          try {
            if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT && env.GIT_PREVIOUS_SUCCESSFUL_COMMIT != env.GIT_COMMIT) {
              changedFiles = sh(
                script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}",
                returnStdout: true
              ).trim().split('\n').findAll { it?.trim() }
            } else {
              def commitCount = sh(
                script: "git rev-list --count HEAD",
                returnStdout: true
              ).trim().toInteger()

              if (commitCount > 1) {
                changedFiles = sh(
                  script: "git diff --name-only HEAD~1 HEAD",
                  returnStdout: true
                ).trim().split('\n').findAll { it?.trim() }
              } else {
                changedFiles = sh(
                  script: "git ls-tree -r --name-only HEAD",
                  returnStdout: true
                ).trim().split('\n').findAll { it?.trim() }
              }
            }
          } catch (Exception e) {
            echo "⚠️ No se pudo comparar con commit anterior: ${e.message}"
            changedFiles = sh(
              script: "git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || git ls-tree -r --name-only HEAD",
              returnStdout: true
            ).trim().split('\n').findAll { it?.trim() }
          }

          // Detectar cambios en cada servicio
          servicesData.each { serviceName, port ->
            def serviceDir = "${serviceName}/"
            def hasServiceChanges = changedFiles.any { file -> file.startsWith(serviceDir) }
            
            if (hasServiceChanges) {
              servicesChanged[serviceName] = true
              echo "✅ Cambios detectados en ${serviceName}"
            }
          }

          // Detectar cambios compartidos que afectan a todos
          def hasSharedChanges = changedFiles.any { file ->
            file == 'pom.xml' || file.startsWith('jenkins/') || file.startsWith('.github/')
          }

          if (hasSharedChanges) {
            echo "⚠️ Cambios detectados en archivos compartidos - todos los servicios seleccionados se desplegarán"
            deployFlags.each { serviceName, flag ->
              if (flag) {
                servicesChanged[serviceName] = true
              }
            }
          }

          // Determinar qué servicios se van a desplegar
          def servicesToDeploy = []
          servicesData.each { serviceName, port ->
            if (deployFlags[serviceName] && (servicesChanged[serviceName] || params.FORCE_DEPLOY_ALL)) {
              servicesToDeploy << serviceName
            }
          }

          if (servicesToDeploy.isEmpty()) {
            echo "ℹ️ No hay servicios para desplegar"
            echo "✅ Pipeline se omite porque no hay cambios relevantes en los servicios seleccionados"
            currentBuild.result = 'SUCCESS'
            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
              error("Pipeline omitido exitosamente - no hay servicios para desplegar")
            }
            return
          }

          env.SERVICES_TO_DEPLOY = servicesToDeploy.join(',')
          echo "🚀 Servicios a desplegar en PRODUCCIÓN: ${env.SERVICES_TO_DEPLOY}"
        }
      }
    }

    stage('Deploy Services to GKE Production') {
      when {
        expression { env.SERVICES_TO_DEPLOY != null && env.SERVICES_TO_DEPLOY != '' }
      }
      steps {
        withCredentials([
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID'),
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'docker-user', variable: 'DOCKER_USER')
        ]) {
          script {
            def imageTag = params.DOCKER_IMAGE_TAG?.trim() ?: 'latest'
            def servicesToDeploy = env.SERVICES_TO_DEPLOY.split(',')

            // Mapa de puertos por servicio (service-discovery PRIMERO)
            def servicePorts = [
              'service-discovery': '8761',
              'user-service': '8085',
              'product-service': '8083',
              'order-service': '8081',
              'shipping-service': '8084',
              'payment-service': '8082',
              'favourite-service': '8086'
            ]
            
            // Mapa de health check paths por servicio
            def serviceHealthPaths = [
              'service-discovery': '/actuator/health',
              'user-service': '/user-service/actuator/health',
              'product-service': '/product-service/actuator/health',
              'order-service': '/order-service/actuator/health',
              'shipping-service': '/shipping-service/actuator/health',
              'payment-service': '/payment-service/actuator/health',
              'favourite-service': '/favourite-service/actuator/health'
            ]
            
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            echo "🚀 Iniciando despliegue masivo a GKE PRODUCCIÓN"
            echo "📦 Imagen tag: ${imageTag}"
            echo "🏷️  Namespace: ${params.K8S_NAMESPACE}"
            echo "🔢 Réplicas: ${params.REPLICA_COUNT}"
            echo "📋 Servicios: ${servicesToDeploy.join(', ')}"
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

            // Desplegar cada servicio
            servicesToDeploy.each { serviceName ->
              def servicePort = servicePorts[serviceName]
              def healthPath = serviceHealthPaths[serviceName]
              def dockerHubImage = "${DOCKER_USER}/${serviceName}:${imageTag}"
              
              echo ""
              echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
              echo "🚀 Desplegando ${serviceName} en PRODUCCIÓN"
              echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

              withEnv([
                "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
                "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
                "GKE_LOCATION=${params.GKE_LOCATION}",
                "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
                "SERVICE_NAME=${serviceName}",
                "SERVICE_PORT=${servicePort}",
                "SERVICE_HEALTH_PATH=${healthPath}",
                "DOCKER_HUB_IMAGE=${dockerHubImage}",
                "REPLICA_COUNT=${params.REPLICA_COUNT}",
                "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
              ]) {
                sh '''
set -e

# Configure PATH for gcloud and kubectl
export PATH="/usr/local/bin:/usr/bin:/bin:/opt/google-cloud-sdk/google-cloud-sdk/bin:/opt/google-cloud-sdk/bin:$PATH"

# Find and set gcloud path
GCLOUD_PATH=$(which gcloud 2>/dev/null || find /usr -name gcloud 2>/dev/null | head -1 || find /opt -name gcloud 2>/dev/null | head -1 || echo "")
if [ -z "$GCLOUD_PATH" ]; then
  echo "❌ Error: gcloud no encontrado. Instálalo con:"
  echo "   sudo apt-get update && sudo apt-get install -y google-cloud-sdk google-cloud-sdk-gke-gcloud-auth-plugin"
  exit 1
fi
echo "✅ gcloud encontrado en: $GCLOUD_PATH"

# Verify kubectl is available
if ! command -v kubectl > /dev/null 2>&1; then
  echo "❌ Error: kubectl no encontrado"
  exit 1
fi
echo "✅ kubectl encontrado"

echo "🔐 Autenticando con Google Cloud..."
gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
gcloud config set project "${GCP_PROJECT_ID}"

echo "☸️ Configurando kubectl para GKE..."
gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" \
  --zone "${GKE_LOCATION}" \
  --project "${GCP_PROJECT_ID}"

echo "📦 Creando namespace si no existe..."
kubectl create namespace "${K8S_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "🚀 Desplegando ${SERVICE_NAME} en namespace ${K8S_NAMESPACE} (PRODUCCIÓN)..."

kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${SERVICE_NAME}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ${SERVICE_NAME}
    environment: production
    deployed-by: all-services-prod-pipeline
spec:
  replicas: ${REPLICA_COUNT}
  selector:
    matchLabels:
      app: ${SERVICE_NAME}
  template:
    metadata:
      labels:
        app: ${SERVICE_NAME}
        environment: production
    spec:
      containers:
      - name: ${SERVICE_NAME}
        image: ${DOCKER_HUB_IMAGE}
        imagePullPolicy: Always
        ports:
        - containerPort: ${SERVICE_PORT}
        env:
        - name: SERVER_PORT
          value: "${SERVICE_PORT}"
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_CLOUD_CONFIG_ENABLED
          value: "false"
        - name: EUREKA_CLIENT_ENABLED
          value: "false"
        resources:
          requests:
            cpu: 250m
            memory: 768Mi
          limits:
            cpu: 1000m
            memory: 2Gi
        livenessProbe:
          httpGet:
            path: ${SERVICE_HEALTH_PATH}
            port: ${SERVICE_PORT}
          initialDelaySeconds: 90
          periodSeconds: 15
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: ${SERVICE_HEALTH_PATH}
            port: ${SERVICE_PORT}
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: ${SERVICE_NAME}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ${SERVICE_NAME}
    environment: production
spec:
  type: ClusterIP
  selector:
    app: ${SERVICE_NAME}
  ports:
  - port: ${SERVICE_PORT}
    targetPort: ${SERVICE_PORT}
    protocol: TCP
    name: http
EOF

echo "⏳ Esperando que el deployment esté listo (timeout 5 min)..."
kubectl wait --for=condition=available --timeout=300s \
  deployment/${SERVICE_NAME} -n ${K8S_NAMESPACE} || {
  echo "❌ Timeout esperando deployment. Verificando estado..."
  kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME}
  kubectl describe deployment ${SERVICE_NAME} -n ${K8S_NAMESPACE}
  exit 1
}

echo "✅ ${SERVICE_NAME} desplegado exitosamente en PRODUCCIÓN"
echo ""
echo "📊 Estado del deployment:"
kubectl get deployment ${SERVICE_NAME} -n ${K8S_NAMESPACE}
echo ""
echo "📦 Pods:"
kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME}
echo ""
echo "🌐 Service:"
kubectl get service ${SERVICE_NAME} -n ${K8S_NAMESPACE}
'''
              }
            }
          }
        }
      }
    }

    stage('Health Check All Services') {
      when {
        expression { env.SERVICES_TO_DEPLOY != null && env.SERVICES_TO_DEPLOY != '' }
      }
      steps {
        withCredentials([
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID')
        ]) {
          script {
            def servicesToDeploy = env.SERVICES_TO_DEPLOY.split(',')

            // Mapa de puertos por servicio
            def servicePorts = [
              'service-discovery': '8761',
              'user-service': '8085',
              'product-service': '8083',
              'order-service': '8081',
              'shipping-service': '8084',
              'payment-service': '8082',
              'favourite-service': '8086'
            ]

            // Mapa de health check paths por servicio
            def serviceHealthPaths = [
              'service-discovery': '/actuator/health',
              'user-service': '/user-service/actuator/health',
              'product-service': '/product-service/actuator/health',
              'order-service': '/order-service/actuator/health',
              'shipping-service': '/shipping-service/actuator/health',
              'payment-service': '/payment-service/actuator/health',
              'favourite-service': '/favourite-service/actuator/health'
            ]
            
            echo ""
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            echo "🏥 Verificando salud de todos los servicios en PRODUCCIÓN"
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            
            def healthResults = [:]
            
            servicesToDeploy.each { serviceName ->
              def servicePort = servicePorts[serviceName]
              def healthPath = serviceHealthPaths[serviceName]
              
              echo ""
              echo "🏥 Verificando ${serviceName}..."
              
              withEnv([
                "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
                "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
                "GKE_LOCATION=${params.GKE_LOCATION}",
                "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
                "SERVICE_NAME=${serviceName}",
                "SERVICE_PORT=${servicePort}",
                "SERVICE_HEALTH_PATH=${healthPath}",
                "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
              ]) {
                try {
                  sh '''
set -e

# Configure PATH for gcloud and kubectl
export PATH="/usr/local/bin:/usr/bin:/bin:/opt/google-cloud-sdk/google-cloud-sdk/bin:/opt/google-cloud-sdk/bin:$PATH"

# Verify gcloud is available
if ! command -v gcloud > /dev/null 2>&1; then
  echo "❌ Error: gcloud no encontrado"
  exit 1
fi

# Verify kubectl is available
if ! command -v kubectl > /dev/null 2>&1; then
  echo "❌ Error: kubectl no encontrado"
  exit 1
fi

gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" \
  --zone "${GKE_LOCATION}" \
  --project "${GCP_PROJECT_ID}"

echo "🏥 Verificando health endpoint de ${SERVICE_NAME} en PRODUCCIÓN..."
MAX_RETRIES=15
RETRY_COUNT=0
HEALTH_STATUS=""

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  HEALTH_RESPONSE=$(kubectl exec -n "${K8S_NAMESPACE}" deployment/"${SERVICE_NAME}" -- \
    curl -s http://localhost:"${SERVICE_PORT}"${SERVICE_HEALTH_PATH} 2>/dev/null || echo "ERROR")
  
  if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"' || echo "$HEALTH_RESPONSE" | grep -q '"status" : "UP"'; then
    HEALTH_STATUS="UP"
    echo "✅ ${SERVICE_NAME} está UP en producción"
    echo "$HEALTH_RESPONSE"
    break
  else
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "⚠️  Intento $RETRY_COUNT/$MAX_RETRIES - ${SERVICE_NAME} no está listo aún..."
    if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
      sleep 15
    fi
  fi
done

if [ "$HEALTH_STATUS" != "UP" ]; then
  echo "❌ Health check falló para ${SERVICE_NAME} después de $MAX_RETRIES intentos"
  echo "📋 Logs del servicio:"
  kubectl logs -n "${K8S_NAMESPACE}" deployment/"${SERVICE_NAME}" --tail=100
  exit 1
fi
'''
                  healthResults[serviceName] = 'SUCCESS'
                  echo "✅ Health check exitoso para ${serviceName}"
                } catch (Exception e) {
                  healthResults[serviceName] = 'FAILED'
                  echo "❌ Health check falló para ${serviceName}: ${e.message}"
                  throw e
                }
              }
            }
            
            echo ""
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            echo "📊 RESUMEN DE HEALTH CHECKS EN PRODUCCIÓN:"
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            healthResults.each { service, status ->
              def icon = status == 'SUCCESS' ? '✅' : '❌'
              echo "${icon} ${service}: ${status}"
            }
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          }
        }
      }
    }

    

    stage('Run E2E Tests') {
      when {
        expression { env.SERVICES_TO_DEPLOY != null && env.SERVICES_TO_DEPLOY != '' }
      }
      steps {
        withCredentials([
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID')
        ]) {
          script {
            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
              "GKE_LOCATION=${params.GKE_LOCATION}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
            ]) {
              sh '''
set -e

# Configure PATH for gcloud and kubectl
export PATH="/usr/local/bin:/usr/bin:/bin:/opt/google-cloud-sdk/google-cloud-sdk/bin:/opt/google-cloud-sdk/bin:$PATH"

# Verify gcloud is available
if ! command -v gcloud > /dev/null 2>&1; then
  echo "❌ Error: gcloud no encontrado"
  exit 1
fi

# Verify kubectl is available
if ! command -v kubectl > /dev/null 2>&1; then
  echo "❌ Error: kubectl no encontrado"
  exit 1
fi

# Authenticate with GCP and configure kubectl
gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
gcloud config set project "${GCP_PROJECT_ID}"
gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" \
  --zone "${GKE_LOCATION}" \
  --project "${GCP_PROJECT_ID}"

echo "🧪 Ejecutando pruebas E2E en ambiente de PRODUCCIÓN..."
# Execute E2E tests script
./jenkins/scripts/run-e2e-gke.sh "${K8S_NAMESPACE}" "${GCP_PROJECT_ID}" "${GKE_CLUSTER_NAME}" "${GKE_LOCATION}"
'''
            }
          }
        }
      }
    }

    stage('Run Performance Tests') {
      when {
        expression { env.SERVICES_TO_DEPLOY != null && env.SERVICES_TO_DEPLOY != '' }
      }
      steps {
        withCredentials([
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID')
        ]) {
          script {
            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
              "GKE_LOCATION=${params.GKE_LOCATION}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}",
              "PERF_TEST_USERS=${params.PERF_TEST_USERS ?: '50'}",
              "PERF_TEST_SPAWN_RATE=${params.PERF_TEST_SPAWN_RATE ?: '5'}",
              "PERF_TEST_DURATION=${params.PERF_TEST_DURATION ?: '3m'}",
              "SERVICES_TO_DEPLOY=${env.SERVICES_TO_DEPLOY}"
            ]) {
              sh """
echo "⚡ Ejecutando pruebas de rendimiento en PRODUCCIÓN..."
./jenkins/scripts/run-performance-gke.sh \
  "${params.K8S_NAMESPACE}" \
  "${GCP_PROJECT_ID}" \
  "${params.GKE_CLUSTER_NAME}" \
  "${params.GKE_LOCATION}" \
  "${env.SERVICES_TO_DEPLOY}" \
  "${params.PERF_TEST_USERS ?: '50'}" \
  "${params.PERF_TEST_SPAWN_RATE ?: '5'}" \
  "${params.PERF_TEST_DURATION ?: '3m'}"
"""
            }
            
            // Archive performance test results as artifacts
            archiveArtifacts artifacts: 'performance-results/**/*', allowEmptyArchive: true
          }
        }
      }
    }





    stage('Deployment Summary') {
      when {
        expression { env.SERVICES_TO_DEPLOY != null && env.SERVICES_TO_DEPLOY != '' }
      }
      steps {
        withCredentials([
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID')
        ]) {
          script {
            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
              "GKE_LOCATION=${params.GKE_LOCATION}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
            ]) {
              sh '''
set -e

# Configure PATH for gcloud and kubectl
export PATH="/usr/local/bin:/usr/bin:/bin:/opt/google-cloud-sdk/google-cloud-sdk/bin:/opt/google-cloud-sdk/bin:$PATH"

gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" \
  --zone "${GKE_LOCATION}" \
  --project "${GCP_PROJECT_ID}"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 RESUMEN FINAL DEL DESPLIEGUE EN PRODUCCIÓN"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📦 Todos los Deployments en ${K8S_NAMESPACE}:"
kubectl get deployments -n "${K8S_NAMESPACE}" -l deployed-by=all-services-prod-pipeline
echo ""
echo "🌐 Todos los Services en ${K8S_NAMESPACE}:"
kubectl get services -n "${K8S_NAMESPACE}" -l deployed-by=all-services-prod-pipeline
echo ""
echo "📦 Todos los Pods en ${K8S_NAMESPACE}:"
kubectl get pods -n "${K8S_NAMESPACE}" -l deployed-by=all-services-prod-pipeline
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Despliegue en PRODUCCIÓN completado exitosamente"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
'''
            }
          }
        }
      }
    }

  }

  post {
    success {
      echo "✅ All-Services-Prod completado exitosamente"
      script {
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "🎉 DESPLIEGUE A PRODUCCIÓN EXITOSO"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "📋 Build: #${env.BUILD_NUMBER}"
        echo "📝 Release Notes: Revisar artefactos del build"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        try {
          sh("""curl -X POST "https://api.github.com/repos/OscarMURA/ecommerce-microservice-backend-app/statuses/${env.GIT_COMMIT}" \\
            -H "Authorization: token \${GITHUB_TOKEN}" \\
            -H "Content-Type: application/json" \\
            -d '{"state":"success","description":"Jenkins: Production deployment successful","context":"ci/jenkins/all-services-prod"}'""")
        } catch (Exception e) {
          echo "⚠️ No se pudo actualizar estado en GitHub: ${e.message}"
        }
      }
    }
    failure {
      echo "❌ All-Services-Prod falló"
      script {
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "🚨 DESPLIEGUE A PRODUCCIÓN FALLÓ"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "⚠️ ACCIÓN REQUERIDA: Revisar logs inmediatamente"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        try {
          sh("""curl -X POST "https://api.github.com/repos/OscarMURA/ecommerce-microservice-backend-app/statuses/${env.GIT_COMMIT}" \\
            -H "Authorization: token \${GITHUB_TOKEN}" \\
            -H "Content-Type: application/json" \\
            -d '{"state":"failure","description":"Jenkins: Production deployment failed","context":"ci/jenkins/all-services-prod"}'""")
        } catch (Exception e) {
          echo "⚠️ No se pudo actualizar estado en GitHub: ${e.message}"
        }
      }
    }
    always {
      script {
        if (env.SERVICES_TO_DEPLOY) {
          echo ""
          echo "📋 Servicios procesados: ${env.SERVICES_TO_DEPLOY}"
          echo "📝 Release Notes disponibles como artefacto del build"
        }
      }
      cleanWs()
    }
  }
}

