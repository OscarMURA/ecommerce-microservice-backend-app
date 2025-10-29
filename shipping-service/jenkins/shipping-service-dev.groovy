pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    string(name: 'VM_NAME', defaultValue: 'ecommerce-integration-runner', description: 'Nombre de la VM creada por Jenkins_Create_VM')
    string(name: 'VM_REGION', defaultValue: 'nyc3', description: 'Región del droplet (usado si hay que crearlo)')
    string(name: 'VM_SIZE', defaultValue: 's-1vcpu-2gb', description: 'Tamaño del droplet (usado si hay que crearlo)')
    string(name: 'VM_IMAGE', defaultValue: 'ubuntu-22-04-x64', description: 'Imagen del droplet (usado si hay que crearlo)')
    string(name: 'JENKINS_CREATE_VM_JOB', defaultValue: 'Jenkins_Create_VM', description: 'Nombre del pipeline que aprovisiona la VM en DigitalOcean')
    string(name: 'VM_JOB_BRANCH_HINTS', defaultValue: 'main,master,infra/main,infra/master', description: 'Sufijos (coma separada) para intentar en jobs multibranch si la VM no existe')
    string(name: 'VM_JOB_EXTRA_PATHS', defaultValue: '', description: 'Rutas completas adicionales (coma separada) a intentar antes de los sufijos')
    string(name: 'REPO_URL', defaultValue: 'https://github.com/OscarMURA/ecommerce-microservice-backend-app.git', description: 'Repositorio a clonar en la VM')
    string(name: 'APP_BRANCH', defaultValue: '', description: 'Branch del repo a usar (vacío = rama actual del pipeline)')
    booleanParam(name: 'DEPLOY_TO_K8S', defaultValue: false, description: 'Desplegar servicios en Kubernetes al finalizar las pruebas')
    choice(name: 'K8S_ENVIRONMENT', choices: ['dev', 'staging', 'prod'], description: 'Etiqueta de ambiente para los recursos de Kubernetes')
    string(name: 'K8S_NAMESPACE', defaultValue: 'ecommerce', description: 'Namespace de Kubernetes donde se desplegarán los servicios')
    string(name: 'GKE_CLUSTER_NAME', defaultValue: 'ecommerce-dev-gke-v2', description: 'Nombre del cluster GKE')
    string(name: 'GKE_LOCATION', defaultValue: 'us-central1-a', description: 'Zona o región del cluster GKE')
    string(name: 'K8S_IMAGE_REGISTRY', defaultValue: 'gcr.io/devops-activity', description: 'Registro de contenedores (p. ej. gcr.io/proyecto)')
    string(name: 'K8S_IMAGE_TAG', defaultValue: '', description: 'Tag de las imágenes a desplegar (vacío usa el commit actual)')
    string(name: 'INFRA_REPO_URL', defaultValue: 'https://github.com/OscarMURA/infra-ecommerce-microservice-backend-app.git', description: 'Repositorio con manifiestos de infraestructura')
    string(name: 'INFRA_REPO_BRANCH', defaultValue: 'infra/master', description: 'Rama del repositorio de infraestructura a usar')
  }

  environment {
    REMOTE_BASE = "/opt/ecommerce-app"
    REMOTE_DIR = "/opt/ecommerce-app/backend"
    SERVICE_NAME = "shipping-service"
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
          if (!(branch == 'develop' || branch.startsWith('feat/'))) {
            error "shipping-service-dev solo se ejecuta en ramas develop o feat/** (rama actual: '${branch}')."
          }
          echo "Branch validada: ${branch}"
          env.PIPELINE_BRANCH = branch
        }
      }
    }

    stage('Checkout Pipeline Repo') {
      steps {
        checkout scm
        script {
          echo "Workspace: ${env.WORKSPACE}"
        }
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
              echo "📊 Comparando con commit previo exitoso: ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
              changedFiles = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}", returnStdout: true).trim().split('\n').findAll { it?.trim() }
            } else {
              echo "📊 Comparando con commit anterior (HEAD~1)"
              def commitCount = sh(script: "git rev-list --count HEAD", returnStdout: true).trim().toInteger()
              if (commitCount > 1) {
                changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim().split('\n').findAll { it?.trim() }
              } else {
                changedFiles = sh(script: "git ls-tree -r --name-only HEAD", returnStdout: true).trim().split('\n').findAll { it?.trim() }
              }
            }
          } catch (Exception e) {
            echo "⚠️ No se pudo comparar con commit anterior: ${e.message}"
            echo "🔄 Usando todos los archivos del commit actual..."
            changedFiles = sh(script: "git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || git ls-tree -r --name-only HEAD", returnStdout: true).trim().split('\n').findAll { it?.trim() }
          }
          echo "📋 Archivos modificados en este commit (${changedFiles.size()} archivos):"
          changedFiles.take(10).each { file -> echo "   - ${file}" }
          if (changedFiles.size() > 10) { echo "   ... y ${changedFiles.size() - 10} archivos más" }
          def hasServiceChanges = changedFiles.any { file -> file.startsWith(serviceDir) }
          def hasSharedChanges = changedFiles.any { file -> file == 'pom.xml' || file.startsWith('jenkins/') || file.startsWith('.github/') }
          if (!hasServiceChanges && !hasSharedChanges && changedFiles.size() > 0) {
            echo "ℹ️ No se detectaron cambios en ${env.SERVICE_NAME}"
            echo "✅ Pipeline se omite porque no hay cambios relevantes en ${env.SERVICE_NAME}"
            currentBuild.result = 'SUCCESS'
            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') { error("Pipeline omitido exitosamente - no hay cambios en ${env.SERVICE_NAME}") }
            return
          } else if (changedFiles.size() == 0) {
            echo "ℹ️ No se detectaron cambios en el repositorio"
            echo "✅ Pipeline se omite porque no hay cambios"
            currentBuild.result = 'SUCCESS'
            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') { error("Pipeline omitido exitosamente - no hay cambios en el repositorio") }
            return
          } else {
            echo "✅ Cambios detectados relevantes para ${env.SERVICE_NAME}:"
            if (hasServiceChanges) { changedFiles.findAll { it.startsWith(serviceDir) }.each { file -> echo "   ✓ ${file}" } }
            if (hasSharedChanges) { changedFiles.findAll { it == 'pom.xml' || it.startsWith('jenkins/') }.each { file -> echo "   ✓ ${file} (archivo compartido)" } }
            echo "🚀 Continuando con el pipeline..."
          }
        }
      }
    }

    stage('Ensure VM Available') {
      steps {
        withCredentials([string(credentialsId: 'digitalocean-token', variable: 'DO_TOKEN')]) {
          script {
            def fetchIp = {
              sh(script: """
set -e
curl -sS -H \"Authorization: Bearer ${DO_TOKEN}\" \"https://api.digitalocean.com/v2/droplets?per_page=200\" \
  | jq -r --arg NAME \"${params.VM_NAME}\" '.droplets[] | select(.name==\$NAME) | .networks.v4[] | select(.type==\"public\") | .ip_address' \
  | head -n1
""", returnStdout: true).trim()
            }

            def currentIp = fetchIp()
            if (!currentIp) {
              echo "No se encontró la VM ${params.VM_NAME}. Solicitando creación..."
              def baseJob = params.JENKINS_CREATE_VM_JOB?.trim() ?: ''
              def hints = (params.VM_JOB_BRANCH_HINTS ?: '')
                .split(',')
                .collect { it.trim() }
                .findAll { it }
              if (env.PIPELINE_BRANCH && !hints.contains(env.PIPELINE_BRANCH)) {
                hints << env.PIPELINE_BRANCH
              }
              def extra = (params.VM_JOB_EXTRA_PATHS ?: '')
                .split(',')
                .collect { it.trim() }
                .findAll { it }
              def jobCandidates = []
              jobCandidates.addAll(extra)
              if (baseJob) {
                jobCandidates << baseJob
                if (!baseJob.contains('/')) {
                  hints.each { suffix ->
                    jobCandidates << "${baseJob}/${suffix}"
                  }
                }
              }
              jobCandidates = jobCandidates.collect { it.trim() }.findAll { it }.unique()

              def triggered = false
              def lastError = null
              for (candidate in jobCandidates) {
                try {
                  echo "Intentando disparar job '${candidate}'..."
                  build job: candidate, wait: true, propagate: true, parameters: [
                    string(name: 'ACTION', value: 'create'),
                    string(name: 'VM_NAME', value: params.VM_NAME),
                    string(name: 'VM_REGION', value: params.VM_REGION),
                    string(name: 'VM_SIZE', value: params.VM_SIZE),
                    string(name: 'VM_IMAGE', value: params.VM_IMAGE),
                    booleanParam(name: 'ARCHIVE_METADATA', value: true)
                  ]
                  triggered = true
                  env.JOB_USED_FOR_VM = candidate
                  break
                } catch (Exception ex) {
                  lastError = ex
                  echo "No se pudo ejecutar '${candidate}': ${ex.message}"
                }
              }
              if (!triggered) {
                def suggestion = baseJob.contains('/') ? baseJob : "${baseJob}/${hints ? hints[0] : 'main'}"
                error "No se pudo invocar el pipeline Jenkins_Create_VM. Revisa el parámetro 'JENKINS_CREATE_VM_JOB' o proporciona un sufijo válido (p. ej. '${suggestion}'). Último error: ${lastError?.message}"
              }
              sleep(time: 30, unit: 'SECONDS')
              currentIp = fetchIp()
            }

            if (!currentIp) {
              error "No se pudo obtener la IP pública de ${params.VM_NAME} después de intentar crearla."
            }

            env.DROPLET_IP = currentIp
            echo "VM disponible en IP ${env.DROPLET_IP}"
          }
        }
      }
    }

    stage('Sync Repository on VM') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          script {
            def branchToUse = params.APP_BRANCH?.trim()
            if (!branchToUse) {
              branchToUse = env.PIPELINE_BRANCH ?: 'develop'
            }
            def branchExistsStatus = sh(
              script: """
set -e
git ls-remote --heads "${params.REPO_URL}" "${branchToUse}" | grep -q "${branchToUse}"
""",
              returnStatus: true
            )
            if (branchExistsStatus != 0 && params.APP_BRANCH?.trim()) {
              echo "La rama '${branchToUse}' no existe en remoto. Usando rama del pipeline '${env.PIPELINE_BRANCH ?: 'develop'}'."
              branchToUse = env.PIPELINE_BRANCH ?: 'develop'
            }
            echo "Sincronizando repositorio ${params.REPO_URL} con rama ${branchToUse}"
            withEnv([
              "TARGET_IP=${env.DROPLET_IP}",
              "REMOTE_BASE=${env.REMOTE_BASE}",
              "REMOTE_DIR=${env.REMOTE_DIR}",
              "REPO_URL=${params.REPO_URL}",
              "APP_BRANCH=${branchToUse}"
            ]) {
            sh(label: 'Esperar VM lista', script: '''
set -e
export SSHPASS="$VM_PASSWORD"
for i in $(seq 1 30); do
  if sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"$TARGET_IP" "echo VM ready" 2>/dev/null; then
    exit 0
  fi
  echo "Esperando acceso SSH a $TARGET_IP... ($i/30)"
  sleep 10
done
echo "Timeout esperando SSH en $TARGET_IP"
exit 1
''')

            sh(label: 'Sincronizar repositorio', script: '''
set -e
export SSHPASS="$VM_PASSWORD"
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "REMOTE_BASE='$REMOTE_BASE' REMOTE_DIR='$REMOTE_DIR' REPO_URL='$REPO_URL' APP_BRANCH='$APP_BRANCH' VM_PASSWORD='$VM_PASSWORD' bash -s" <<'EOF'
set -euo pipefail
ensure_remote_base() {
  if mkdir -p "$REMOTE_BASE"; then
    return 0
  fi

  echo "mkdir -p $REMOTE_BASE falló sin sudo, reintentando..." >&2
  if command -v sudo >/dev/null 2>&1; then
    if sudo -n true 2>/dev/null; then
      sudo mkdir -p "$REMOTE_BASE"
      sudo chown -R "$USER":"$USER" "$REMOTE_BASE"
      return 0
    fi

    if [ -n "${VM_PASSWORD:-}" ]; then
      printf '%s\n' "$VM_PASSWORD" | sudo -S mkdir -p "$REMOTE_BASE"
      printf '%s\n' "$VM_PASSWORD" | sudo -S chown -R "$USER":"$USER" "$REMOTE_BASE"
      return 0
    fi
  fi

  echo "No se pudo crear ${REMOTE_BASE}. Verifica permisos o usa otra ruta." >&2
  exit 1
}

ensure_remote_base
cd "$REMOTE_BASE"
if [ ! -d backend/.git ]; then
  rm -rf backend || true
  git clone "$REPO_URL" backend
fi
cd backend
git fetch origin "$APP_BRANCH" || git fetch origin
if git rev-parse --verify "origin/$APP_BRANCH" >/dev/null 2>&1; then
  git checkout -B "$APP_BRANCH" "origin/$APP_BRANCH"
else
  git checkout "$APP_BRANCH"
fi
git reset --hard "origin/$APP_BRANCH" || true
git clean -fd
chmod +x mvnw || true
git config --global --add safe.directory "$REMOTE_DIR" || true
EOF
''')
          }
          }
        }
      }
    }

    stage('Unit Tests') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          withEnv([
            "TARGET_IP=${env.DROPLET_IP}",
            "REMOTE_DIR=${env.REMOTE_DIR}",
            "SERVICE_NAME=${env.SERVICE_NAME}"
          ]) {
            sh(label: 'Pruebas unitarias', script: '''
set -e
export SSHPASS="$VM_PASSWORD"
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'EOF'
set -euo pipefail
cd "$REMOTE_DIR"

summarize_reports() {
  local module="$1"
  local report_dir="$module/target/surefire-reports"
  if [ ! -d "$report_dir" ]; then
    echo "ℹ️ Sin reportes en ${report_dir}"
    return
  fi

  local summary=""
  if compgen -G "$report_dir"/*.txt >/dev/null 2>&1; then
    summary=$(grep -h 'Tests run:' "$report_dir"/*.txt 2>/dev/null | tail -n1 || true)
  fi
  if [ -z "$summary" ] && compgen -G "$report_dir"/TEST-*.xml >/dev/null 2>&1; then
    summary=$(REPORT_DIR="$report_dir" python3 <<'PY' 2>/dev/null
import glob
import os
import xml.etree.ElementTree as ET

report_dir = os.environ["REPORT_DIR"]
total = fails = errors = skipped = 0
found = False

for path in glob.glob(os.path.join(report_dir, "TEST-*.xml")):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    total += int(root.attrib.get("tests", 0))
    fails += int(root.attrib.get("failures", 0))
    errors += int(root.attrib.get("errors", 0))
    skipped += int(root.attrib.get("skipped", 0))
    found = True

if found:
    print(f"Tests run: {total}, Failures: {fails}, Errors: {errors}, Skipped: {skipped}")
PY
)
  fi

  if [ -n "$summary" ]; then
    echo "📊 ${module} -> ${summary}"
  else
    echo "⚠️ No se pudo extraer resumen para ${module}"
  fi
}

echo "➡️ Ejecutando pruebas unitarias para $SERVICE_NAME"
./mvnw -B -pl "$SERVICE_NAME" test -Dtest='*ApplicationTests' -DfailIfNoTests=false
summarize_reports "$SERVICE_NAME"
EOF
''')
          }
        }
      }
    }

    stage('Integration Tests') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          withEnv([
            "TARGET_IP=${env.DROPLET_IP}",
            "REMOTE_DIR=${env.REMOTE_DIR}",
            "SERVICE_NAME=${env.SERVICE_NAME}"
          ]) {
            sh(label: 'Pruebas de integración', script: '''
set -e
export SSHPASS="$VM_PASSWORD"
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'EOF'
set -euo pipefail
cd "$REMOTE_DIR"

summarize_reports() {
  local module="$1"
  local report_dir="$module/target/surefire-reports"
  if [ ! -d "$report_dir" ]; then
    echo "ℹ️ Sin reportes en ${report_dir}"
    return
  fi

  local summary=""
  if compgen -G "$report_dir"/*.txt >/dev/null 2>&1; then
    summary=$(grep -h 'Tests run:' "$report_dir"/*.txt 2>/dev/null | tail -n1 || true)
  fi
  if [ -z "$summary" ] && compgen -G "$report_dir"/TEST-*.xml >/dev/null 2>&1; then
    summary=$(REPORT_DIR="$report_dir" python3 <<'PY' 2>/dev/null
import glob
import os
import xml.etree.ElementTree as ET

report_dir = os.environ["REPORT_DIR"]
total = fails = errors = skipped = 0
found = False

for path in glob.glob(os.path.join(report_dir, "TEST-*.xml")):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    total += int(root.attrib.get("tests", 0))
    fails += int(root.attrib.get("failures", 0))
    errors += int(root.attrib.get("errors", 0))
    skipped += int(root.attrib.get("skipped", 0))
    found = True

if found:
    print(f"Tests run: {total}, Failures: {fails}, Errors: {errors}, Skipped: {skipped}")
PY
)
  fi

  if [ -n "$summary" ]; then
    echo "📊 ${module} -> ${summary}"
  else
    echo "⚠️ No se pudo extraer resumen para ${module}"
  fi
}

echo "➡️ Ejecutando pruebas de integración para $SERVICE_NAME"
./mvnw -B -pl "$SERVICE_NAME" test -Dtest='*IntegrationTest' -DfailIfNoTests=false
summarize_reports "$SERVICE_NAME"
EOF
''')
          }
        }
      }
    }

    stage('Recolectar Reportes') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          withEnv([
            "TARGET_IP=${env.DROPLET_IP}",
            "REMOTE_DIR=${env.REMOTE_DIR}",
            "SERVICE_NAME=${env.SERVICE_NAME}"
          ]) {
            sh(label: 'Empaquetar reportes', script: '''
set -e
export SSHPASS="$VM_PASSWORD"
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'EOF'
set -euo pipefail
cd "$REMOTE_DIR"
tar -czf /tmp/test-reports-$SERVICE_NAME.tar.gz \
  $SERVICE_NAME/target/surefire-reports 2>/dev/null || true
EOF

mkdir -p reports
sshpass -e scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP":/tmp/test-reports-$SERVICE_NAME.tar.gz reports/test-reports-$SERVICE_NAME.tar.gz || true
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"$TARGET_IP" "rm -f /tmp/test-reports-$SERVICE_NAME.tar.gz" || true
''')
            archiveArtifacts artifacts: "reports/test-reports-${env.SERVICE_NAME}.tar.gz", fingerprint: true, allowEmptyArchive: true
          }
        }
      }
    }

    stage('Build and Push Docker Image') {
      when {
        expression { return params.DEPLOY_TO_K8S?.toString()?.toBoolean() }
      }
      steps {
        withCredentials([
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID'),
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            def imageTag = params.K8S_IMAGE_TAG?.trim()
            if (!imageTag) {
              imageTag = env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'latest'
            }
            
            def imageRegistry = params.K8S_IMAGE_REGISTRY?.trim()
            if (!imageRegistry) {
              error "El parámetro K8S_IMAGE_REGISTRY no puede ser vacío."
            }

            echo "🔨 Construyendo imagen Docker para: ${env.SERVICE_NAME}"
            echo "📦 Registro: ${imageRegistry}"
            echo "🏷️  Tag: ${imageTag}"

            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "IMAGE_REGISTRY=${imageRegistry}",
              "IMAGE_TAG=${imageTag}",
              "TARGET_IP=${env.DROPLET_IP}",
              "REMOTE_DIR=${env.REMOTE_DIR}",
              "SERVICE_NAME=${env.SERVICE_NAME}"
            ]) {
              sh '''
set -e
export SSHPASS="$VM_PASSWORD"

echo "🔐 Copiando credenciales de GCP a la VM..."
CREDS_TEMP_FILE="/tmp/gcp-creds-$RANDOM.json"
sshpass -e scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  "${GOOGLE_APPLICATION_CREDENTIALS}" jenkins@"$TARGET_IP":"$CREDS_TEMP_FILE"

echo "🔨 Construyendo y subiendo imagen Docker..."
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "GCP_PROJECT_ID='$GCP_PROJECT_ID' IMAGE_REGISTRY='$IMAGE_REGISTRY' IMAGE_TAG='$IMAGE_TAG' REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' CREDS_TEMP_FILE='$CREDS_TEMP_FILE' bash -s" <<'EOFBUILD'
set -euo pipefail

# Usar la ruta temporal que pasamos
GCP_CREDS_FILE="$CREDS_TEMP_FILE"

echo "🔐 Autenticando con Google Cloud..."
gcloud auth activate-service-account --key-file="$GCP_CREDS_FILE"
gcloud config set project "$GCP_PROJECT_ID"
gcloud auth configure-docker gcr.io --quiet

cd "$REMOTE_DIR"

SERVICE_DIR="$REMOTE_DIR/$SERVICE_NAME"
DOCKERFILE_PATH="$SERVICE_DIR/Dockerfile"

# Verificar si existe el directorio del servicio
if [ ! -d "$SERVICE_DIR" ]; then
  echo "❌ Error: Directorio $SERVICE_DIR no existe"
  exit 1
fi

# Verificar si existe Dockerfile en el directorio del servicio
if [ ! -f "$DOCKERFILE_PATH" ]; then
  echo "❌ Error: Dockerfile no encontrado en $DOCKERFILE_PATH"
  exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔨 Construyendo: $SERVICE_NAME"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Construir desde la raíz del repositorio, usando -f para especificar el Dockerfile
docker build -t "${IMAGE_REGISTRY}/${SERVICE_NAME}:${IMAGE_TAG}" "$REMOTE_DIR" \
  -f "$DOCKERFILE_PATH" \
  --build-arg SERVICE_NAME="$SERVICE_NAME" \
  --build-arg BUILD_DATE="$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  || {
    echo "❌ Error construyendo $SERVICE_NAME"
    exit 1
  }

echo "📤 Subiendo: ${IMAGE_REGISTRY}/${SERVICE_NAME}:${IMAGE_TAG}"
docker push "${IMAGE_REGISTRY}/${SERVICE_NAME}:${IMAGE_TAG}" || {
  echo "❌ Error subiendo $SERVICE_NAME"
  exit 1
}

echo "✅ Completado: $SERVICE_NAME"

echo "🧹 Limpiando credenciales temporales..."
rm -f "$GCP_CREDS_FILE"

echo "✅ Imagen construida y subida exitosamente"
EOFBUILD
'''
            }
          }
        }
      }
    }

    stage('Deploy to Kubernetes') {
      when {
        expression { return params.DEPLOY_TO_K8S?.toString()?.toBoolean() }
      }
      steps {
        withCredentials([
          string(credentialsId: 'gcp-project-id', variable: 'GCP_PROJECT_ID'),
          file(credentialsId: 'gcp-service-account', variable: 'GOOGLE_APPLICATION_CREDENTIALS')
        ]) {
          script {
            def imageTag = params.K8S_IMAGE_TAG?.trim()
            if (!imageTag) {
              imageTag = env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'latest'
              echo "Usando tag de imagen '${imageTag}'."
            }

            def imageRegistry = params.K8S_IMAGE_REGISTRY?.trim()
            if (!imageRegistry) {
              error "El parámetro K8S_IMAGE_REGISTRY no puede ser vacío."
            }

            def clusterName = params.GKE_CLUSTER_NAME?.trim()
            def clusterLocation = params.GKE_LOCATION?.trim()
            if (!clusterName || !clusterLocation) {
              error "Debe especificar GKE_CLUSTER_NAME y GKE_LOCATION."
            }

            def infraRepoUrl = params.INFRA_REPO_URL?.trim()
            if (!infraRepoUrl) {
              error "El parámetro INFRA_REPO_URL es requerido para clonar los manifiestos."
            }
            def infraRepoBranch = params.INFRA_REPO_BRANCH?.trim() ?: 'infra/master'

            def workspaceRoot = pwd()
            def infraDir = "${workspaceRoot}/infra-k8s-config"

            dir('infra-k8s-config') {
              deleteDir()
              git branch: infraRepoBranch, credentialsId: 'github-token', url: infraRepoUrl
            }

            echo "Servicio objetivo: ${env.SERVICE_NAME}"

            def defaultReplicas = params.K8S_ENVIRONMENT == 'prod' ? '2' : '1'
            def rolloutTimeout = params.K8S_ENVIRONMENT == 'prod' ? '420' : '240'

            withEnv([
              "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
              "GKE_CLUSTER_NAME=${clusterName}",
              "GKE_CLUSTER_LOCATION=${clusterLocation}",
              "K8S_NAMESPACE=${params.K8S_NAMESPACE}",
              "K8S_SERVICE_NAME=${env.SERVICE_NAME}",
              "K8S_IMAGE_REGISTRY=${imageRegistry}",
              "K8S_IMAGE_TAG=${imageTag}",
              "INFRA_REPO_DIR=${infraDir}",
              "K8S_ENVIRONMENT=${params.K8S_ENVIRONMENT}",
              "K8S_DEFAULT_REPLICAS=${defaultReplicas}",
              "K8S_ROLLOUT_TIMEOUT=${rolloutTimeout}",
              "GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS}"
            ]) {
              sh '''
set -e
chmod +x jenkins/scripts/deploy-single-service-to-gke.sh
jenkins/scripts/deploy-single-service-to-gke.sh
'''
            }
          }
        }
      }
    }
  }

  post {
    success {
      echo "✅ shipping-service-dev completado. Resultados almacenados en reports/test-reports-shipping-service.tar.gz (si aplica)."
    }
    failure {
      echo "❌ shipping-service-dev falló. Revisa los logs para detalles."
    }
    always {
      cleanWs()
    }
  }
}
