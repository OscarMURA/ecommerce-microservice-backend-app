pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    string(name: 'DOCKER_IMAGE_TAG', defaultValue: 'latest', description: 'Tag de la imagen en Docker Hub a desplegar')
    string(name: 'GKE_CLUSTER_NAME', defaultValue: 'ecommerce-dev-gke-v2', description: 'Nombre del cluster GKE')
    string(name: 'GKE_LOCATION', defaultValue: 'us-central1-a', description: 'Zona o región del cluster GKE')
    string(name: 'K8S_NAMESPACE', defaultValue: 'staging', description: 'Namespace de Kubernetes donde se desplegará')
    string(name: 'REPLICA_COUNT', defaultValue: '1', description: 'Número de réplicas del servicio')
  }

  environment {
    SERVICE_NAME = "favourite-service"
    SERVICE_PORT = "8086"
    GITHUB_TOKEN = credentials('github-token')
  }

  stages {

    stage('Validate Branch') {
      steps {
        script {
          def branch = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').replaceFirst('^origin/', '')
          if (!branch) {
            echo "Rama no disponible (posible indexado). Se omite validación."
            return
          }
          if (branch != 'staging') {
            error "favourite-service-stage solo se ejecuta en rama staging (rama actual: '${branch}')."
          }
          echo "Branch validada: ${branch}"
          env.PIPELINE_BRANCH = branch
        }
      }
    }

    stage('Checkout') {
      steps {
        checkout scm
        script { echo "Workspace: ${env.WORKSPACE}" }
      }
    }

    stage('Check for Service Changes') {
      steps {
        script {
          def serviceDir = "${env.SERVICE_NAME}/"
          def changedFiles = []

          echo "🔍 Verificando cambios en ${env.SERVICE_NAME}..."

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

          def hasServiceChanges = changedFiles.any { file -> file.startsWith(serviceDir) }
          def hasSharedChanges = changedFiles.any { file ->
            file == 'pom.xml' || file.startsWith('jenkins/') || file.startsWith('.github/')
          }

          if (!hasServiceChanges && !hasSharedChanges && changedFiles.size() > 0) {
            echo "ℹ️ No se detectaron cambios en ${env.SERVICE_NAME}"
            echo "✅ Pipeline se omite porque no hay cambios relevantes"
            currentBuild.result = 'SUCCESS'
            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
              error("Pipeline omitido exitosamente - no hay cambios en ${env.SERVICE_NAME}")
            }
            return
          } else {
            echo "✅ Cambios detectados relevantes para ${env.SERVICE_NAME}"
            echo "🚀 Continuando con el despliegue a staging..."
          }
        }
      }
    }

    stage('Deploy to GKE Staging') {
      steps {
        withCredentials([
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID'),
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'docker-user', variable: 'DOCKER_USER')
        ]) {
          script {
            def imageTag = params.DOCKER_IMAGE_TAG?.trim() ?: 'latest'
            def dockerHubImage = "${DOCKER_USER}/${env.SERVICE_NAME}:${imageTag}"
            
            echo "🚀 Desplegando ${env.SERVICE_NAME} a GKE Staging"
            echo "📦 Imagen: ${dockerHubImage}"
            echo "🏷️  Namespace: ${params.K8S_NAMESPACE}"
            echo "🔢 Réplicas: ${params.REPLICA_COUNT}"

            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
              "GKE_LOCATION=${params.GKE_LOCATION}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "SERVICE_NAME=${env.SERVICE_NAME}",
              "SERVICE_PORT=${env.SERVICE_PORT}",
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
if ! command -v kubectl &> /dev/null; then
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

echo "🚀 Desplegando ${SERVICE_NAME} en namespace ${K8S_NAMESPACE}..."

kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${SERVICE_NAME}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ${SERVICE_NAME}
    environment: staging
spec:
  replicas: ${REPLICA_COUNT}
  selector:
    matchLabels:
      app: ${SERVICE_NAME}
  template:
    metadata:
      labels:
        app: ${SERVICE_NAME}
        environment: staging
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
          value: "staging"
        - name: SPRING_CLOUD_CONFIG_ENABLED
          value: "false"
        resources:
          requests:
            cpu: 200m
            memory: 512Mi
          limits:
            cpu: 500m
            memory: 1Gi
        livenessProbe:
          httpGet:
            path: /${SERVICE_NAME}/actuator/health
            port: ${SERVICE_PORT}
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /${SERVICE_NAME}/actuator/health
            port: ${SERVICE_PORT}
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: ${SERVICE_NAME}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ${SERVICE_NAME}
    environment: staging
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

echo "⏳ Esperando que el deployment esté listo..."
kubectl wait --for=condition=available --timeout=300s \
  deployment/${SERVICE_NAME} -n ${K8S_NAMESPACE} || {
  echo "❌ Timeout esperando deployment. Verificando estado..."
  kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME}
  kubectl describe deployment ${SERVICE_NAME} -n ${K8S_NAMESPACE}
  exit 1
}

echo "✅ ${SERVICE_NAME} desplegado exitosamente en staging"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Estado del deployment:"
kubectl get deployment ${SERVICE_NAME} -n ${K8S_NAMESPACE}
echo ""
echo "📦 Pods:"
kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME}
echo ""
echo "🌐 Service:"
kubectl get service ${SERVICE_NAME} -n ${K8S_NAMESPACE}
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
'''
            }
          }
        }
      }
    }

    stage('Health Check') {
      steps {
        withCredentials([
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID')
        ]) {
          script {
            echo "🏥 Verificando salud del servicio en staging..."
            
            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${params.GKE_CLUSTER_NAME}",
              "GKE_LOCATION=${params.GKE_LOCATION}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "SERVICE_NAME=${env.SERVICE_NAME}",
              "SERVICE_PORT=${env.SERVICE_PORT}",
              "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
            ]) {
              sh '''
set -e

# Configure PATH for gcloud and kubectl
export PATH="/usr/local/bin:/usr/bin:/bin:/opt/google-cloud-sdk/google-cloud-sdk/bin:/opt/google-cloud-sdk/bin:$PATH"

# Verify gcloud is available
if ! command -v gcloud &> /dev/null; then
  echo "❌ Error: gcloud no encontrado"
  exit 1
fi

# Verify kubectl is available
if ! command -v kubectl &> /dev/null; then
  echo "❌ Error: kubectl no encontrado"
  exit 1
fi

gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" \
  --zone "${GKE_LOCATION}" \
  --project "${GCP_PROJECT_ID}"

echo "🏥 Verificando health endpoint..."
MAX_RETRIES=12
RETRY_COUNT=0
HEALTH_STATUS=""

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  HEALTH_RESPONSE=$(kubectl exec -n "${K8S_NAMESPACE}" deployment/"${SERVICE_NAME}" -- \
    curl -s http://localhost:"${SERVICE_PORT}"/${SERVICE_NAME}/actuator/health 2>/dev/null || echo "ERROR")
  
  if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"' || echo "$HEALTH_RESPONSE" | grep -q '"status" : "UP"'; then
    HEALTH_STATUS="UP"
    echo "✅ Servicio ${SERVICE_NAME} está UP en staging"
    echo "$HEALTH_RESPONSE"
    break
  else
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "⚠️  Intento $RETRY_COUNT/$MAX_RETRIES - Servicio no está listo aún..."
    if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
      sleep 10
    fi
  fi
done

if [ "$HEALTH_STATUS" != "UP" ]; then
  echo "❌ Health check falló después de $MAX_RETRIES intentos"
  echo "📋 Logs del servicio:"
  kubectl logs -n "${K8S_NAMESPACE}" deployment/"${SERVICE_NAME}" --tail=100
  exit 1
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Health check exitoso para ${SERVICE_NAME} en staging"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
'''
            }
          }
        }
      }
    }

  }

  post {
    success {
      echo "✅ ${env.SERVICE_NAME}-stage completado exitosamente"
      script {
        try {
          sh("""curl -X POST "https://api.github.com/repos/OscarMURA/ecommerce-microservice-backend-app/statuses/${env.GIT_COMMIT}" \\
            -H "Authorization: token \${GITHUB_TOKEN}" \\
            -H "Content-Type: application/json" \\
            -d '{"state":"success","description":"Jenkins: Staging deployment passed","context":"ci/jenkins/${env.SERVICE_NAME}-stage"}'""")
        } catch (Exception e) {
          echo "⚠️ No se pudo actualizar estado en GitHub: ${e.message}"
        }
      }
    }
    failure {
      echo "❌ ${env.SERVICE_NAME}-stage falló"
      script {
        try {
          sh("""curl -X POST "https://api.github.com/repos/OscarMURA/ecommerce-microservice-backend-app/statuses/${env.GIT_COMMIT}" \\
            -H "Authorization: token \${GITHUB_TOKEN}" \\
            -H "Content-Type: application/json" \\
            -d '{"state":"failure","description":"Jenkins: Staging deployment failed","context":"ci/jenkins/${env.SERVICE_NAME}-stage"}'""")
        } catch (Exception e) {
          echo "⚠️ No se pudo actualizar estado en GitHub: ${e.message}"
        }
      }
    }
    always {
      cleanWs()
    }
  }
}

