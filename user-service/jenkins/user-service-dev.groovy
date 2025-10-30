pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    string(name: 'VM_NAME', defaultValue: 'ecommerce-integration-runner', description: 'Nombre de la VM de BUILD/TEST (runner)')
    string(name: 'MINIKUBE_VM_NAME', defaultValue: 'ecommerce-minikube-dev', description: 'Nombre de la VM que tiene Minikube')
    string(name: 'VM_REGION', defaultValue: 'nyc3', description: 'Región del droplet (usado si hay que crearlo)')
    string(name: 'VM_SIZE', defaultValue: 's-1vcpu-2gb', description: 'Tamaño del droplet (usado si hay que crearlo)')
    string(name: 'VM_IMAGE', defaultValue: 'ubuntu-22-04-x64', description: 'Imagen del droplet (usado si hay que crearlo)')
    string(name: 'JENKINS_CREATE_VM_JOB', defaultValue: 'Jenkins_Create_VM', description: 'Nombre del pipeline que aprovisiona la VM en DigitalOcean')
    string(name: 'VM_JOB_BRANCH_HINTS', defaultValue: 'main,master,infra/main,infra/master', description: 'Sufijos (coma separada) para intentar en jobs multibranch si la VM no existe')
    string(name: 'VM_JOB_EXTRA_PATHS', defaultValue: '', description: 'Rutas completas adicionales (coma separada) a intentar antes de los sufijos')
    string(name: 'REPO_URL', defaultValue: 'https://github.com/OscarMURA/ecommerce-microservice-backend-app.git', description: 'Repositorio a clonar en la VM')
    string(name: 'APP_BRANCH', defaultValue: '', description: 'Branch del repo a usar (vacío = rama actual del pipeline)')
    booleanParam(name: 'DEPLOY_TO_MINIKUBE', defaultValue: true, description: 'Desplegar servicio en Minikube (VM de desarrollo) al finalizar las pruebas')
    booleanParam(name: 'DEPLOY_TO_K8S', defaultValue: false, description: 'Desplegar servicios en GKE (Kubernetes en GCP) al finalizar las pruebas')
    choice(name: 'K8S_ENVIRONMENT', choices: ['dev', 'staging', 'prod'], description: 'Etiqueta de ambiente para los recursos de Kubernetes (solo para GKE)')
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
    SERVICE_NAME = "user-service"
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
          if (!(branch == 'develop' || branch.startsWith('feat/'))) {
            error "user-service-dev solo se ejecuta en ramas develop o feat/** (rama actual: '${branch}')."
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
          
          // Obtener la lista de archivos cambiados comparando con el commit anterior
          try {
            // Para multibranch pipelines, usar GIT_PREVIOUS_SUCCESSFUL_COMMIT si está disponible
            if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT && env.GIT_PREVIOUS_SUCCESSFUL_COMMIT != env.GIT_COMMIT) {
              echo "📊 Comparando con commit previo exitoso: ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
              changedFiles = sh(
                script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}",
                returnStdout: true
              ).trim().split('\n').findAll { it?.trim() }
            } else {
              // Comparar con el commit anterior (HEAD~1)
              echo "📊 Comparando con commit anterior (HEAD~1)"
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
                // Si es el primer commit, todos los archivos son "nuevos"
                changedFiles = sh(
                  script: "git ls-tree -r --name-only HEAD",
                  returnStdout: true
                ).trim().split('\n').findAll { it?.trim() }
              }
            }
          } catch (Exception e) {
            echo "⚠️ No se pudo comparar con commit anterior: ${e.message}"
            echo "🔄 Usando todos los archivos del commit actual..."
            // Si falla, asumir que hay cambios (mejor ejecutar que omitir)
            changedFiles = sh(
              script: "git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || git ls-tree -r --name-only HEAD",
              returnStdout: true
            ).trim().split('\n').findAll { it?.trim() }
          }
          
          echo "📋 Archivos modificados en este commit (${changedFiles.size()} archivos):"
          changedFiles.take(10).each { file ->
            echo "   - ${file}"
          }
          if (changedFiles.size() > 10) {
            echo "   ... y ${changedFiles.size() - 10} archivos más"
          }
          
          // Verificar si hay cambios en el directorio del servicio o en archivos compartidos
          def hasServiceChanges = changedFiles.any { file -> 
            file.startsWith(serviceDir)
          }
          
          // También considerar cambios en archivos compartidos (pom.xml padre, etc.)
          def hasSharedChanges = changedFiles.any { file ->
            file == 'pom.xml' || // POM padre afecta a todos
            file.startsWith('jenkins/') || // Cambios en pipelines compartidos
            file.startsWith('.github/') // Cambios en workflows de GitHub
          }
          
          if (!hasServiceChanges && !hasSharedChanges && changedFiles.size() > 0) {
            echo "ℹ️ No se detectaron cambios en ${env.SERVICE_NAME}"
            echo "📋 Archivos modificados pertenecen a otros servicios:"
            changedFiles.findAll { !it.startsWith(serviceDir) && it != 'pom.xml' && !it.startsWith('jenkins/') }.each { file ->
              echo "   - ${file}"
            }
            echo "✅ Pipeline se omite porque no hay cambios relevantes en ${env.SERVICE_NAME}"
            currentBuild.result = 'SUCCESS'
            // Usar catchError para marcar como éxito pero detener la ejecución
            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
              error("Pipeline omitido exitosamente - no hay cambios en ${env.SERVICE_NAME}")
            }
            return
          } else if (changedFiles.size() == 0) {
            echo "ℹ️ No se detectaron cambios en el repositorio"
            echo "✅ Pipeline se omite porque no hay cambios"
            currentBuild.result = 'SUCCESS'
            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
              error("Pipeline omitido exitosamente - no hay cambios en el repositorio")
            }
            return
          } else {
            echo "✅ Cambios detectados relevantes para ${env.SERVICE_NAME}:"
            if (hasServiceChanges) {
              changedFiles.findAll { it.startsWith(serviceDir) }.each { file ->
                echo "   ✓ ${file}"
              }
            }
            if (hasSharedChanges) {
              changedFiles.findAll { it == 'pom.xml' || it.startsWith('jenkins/') }.each { file ->
                echo "   ✓ ${file} (archivo compartido)"
              }
            }
            echo "🚀 Continuando con el pipeline..."
          }
        }
      }
    }

    stage('Ensure VM Available') {
      steps {
        withCredentials([string(credentialsId: 'digitalocean-token', variable: 'DO_TOKEN')]) {
          script {
            def fetchIp = { vmName ->
              sh(script: """
set -e
curl -sS -H "Authorization: Bearer ${DO_TOKEN}" "https://api.digitalocean.com/v2/droplets?per_page=200" \
  | jq -r --arg NAME \"${vmName}\" '.droplets[] | select(.name==\$NAME) | .networks.v4[] | select(.type==\"public\") | .ip_address' \
  | head -n1
""", returnStdout: true).trim()
            }

            def buildIp = fetchIp(params.VM_NAME)
            def minikubeIp = fetchIp(params.MINIKUBE_VM_NAME)
            def currentIp = buildIp
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
              buildIp = fetchIp(params.VM_NAME)
              currentIp = buildIp
            }

            if (!currentIp) {
              error "No se pudo obtener la IP pública de ${params.VM_NAME} después de intentar crearla."
            }

            if (!minikubeIp) {
              echo "No se encontró la VM ${params.MINIKUBE_VM_NAME}. Solicitando creación..."
              // Intentar crear la VM de Minikube usando el mismo mecanismo
              def candidate = params.JENKINS_CREATE_VM_JOB?.trim() ?: ''
              if (!candidate) { error 'Debe configurar JENKINS_CREATE_VM_JOB para crear VMs automáticamente' }
              try {
                build job: candidate, wait: true, propagate: true, parameters: [
                  string(name: 'ACTION', value: 'create'),
                  string(name: 'VM_CONFIG', value: 'ecommerce_minikube'),
                  booleanParam(name: 'ARCHIVE_METADATA', value: true)
                ]
              } catch (Exception ex) {
                echo "No se pudo crear VM minikube: ${ex.message}"
              }
              sleep(time: 30, unit: 'SECONDS')
              minikubeIp = fetchIp(params.MINIKUBE_VM_NAME)
            }

            env.DROPLET_IP = buildIp
            env.BUILD_VM_IP = buildIp
            env.MINIKUBE_VM_IP = minikubeIp
            echo "BUILD VM IP: ${env.BUILD_VM_IP}"
            echo "MINIKUBE VM IP: ${env.MINIKUBE_VM_IP}"
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
./mvnw -B -pl "$SERVICE_NAME" test -Dtest='*ServiceImplTest' -DfailIfNoTests=false
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
        expression { 
          return params.DEPLOY_TO_MINIKUBE?.toString()?.toBoolean() || 
                 params.DEPLOY_TO_K8S?.toString()?.toBoolean() 
        }
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

            if (params.DEPLOY_TO_MINIKUBE?.toString()?.toBoolean()) {
              // Para Minikube: construir imagen local en VM y cargar a Minikube
              echo "🔨 Construyendo imagen Docker para Minikube: ${env.SERVICE_NAME}"
              
              withEnv([
                "BUILD_IP=${env.BUILD_VM_IP}",
                "MINIKUBE_IP=${env.MINIKUBE_VM_IP}",
                "REMOTE_DIR=${env.REMOTE_DIR}",
                "SERVICE_NAME=${env.SERVICE_NAME}"
              ]) {
                sh '''
set -e
export SSHPASS="$VM_PASSWORD"

# --- CONSTRUCCIÓN Y EMPAQUETADO EN VM BUILD ---
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$BUILD_IP" "REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'EOFBUILD'
set -euo pipefail
cd "$REMOTE_DIR"
SERVICE_DIR="$REMOTE_DIR/$SERVICE_NAME"
DOCKERFILE_PATH="$SERVICE_DIR/Dockerfile"
if [ ! -d "$SERVICE_DIR" ]; then
  echo "❌ Error: Directorio $SERVICE_DIR no existe"
  exit 1
fi
if [ ! -f "$DOCKERFILE_PATH" ]; then
  echo "❌ Error: Dockerfile no encontrado en $DOCKERFILE_PATH"
  exit 1
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔨 Construyendo: $SERVICE_NAME para Minikube"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
docker build -t "${SERVICE_NAME}:minikube" "$REMOTE_DIR" \
  -f "$DOCKERFILE_PATH" \
  --build-arg SERVICE_NAME="$SERVICE_NAME" \
  --build-arg BUILD_DATE="$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  || { echo "❌ Error construyendo $SERVICE_NAME"; exit 1; }
echo "📦 Empaquetando imagen..."
docker save "${SERVICE_NAME}:minikube" | gzip > "/tmp/${SERVICE_NAME}-minikube.tar.gz"
EOFBUILD

# --- TRANSFIERE: DE VM BUILD A JENKINS ---
sshpass -e scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$BUILD_IP":"/tmp/${SERVICE_NAME}-minikube.tar.gz" \
  ./

# --- TRANSFIERE: DE JENKINS A VM MINIKUBE ---
sshpass -e scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  "./${SERVICE_NAME}-minikube.tar.gz" \
  jenkins@"$MINIKUBE_IP":"/tmp/${SERVICE_NAME}-minikube.tar.gz"

# --- CARGA IMAGEN EN MINIKUBE ---
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$MINIKUBE_IP" "SERVICE_NAME='$SERVICE_NAME' bash -s" <<'EOFLOAD'
set -euo pipefail
export PATH="/usr/local/bin:$PATH"
docker load -i "/tmp/${SERVICE_NAME}-minikube.tar.gz"
/usr/local/bin/minikube image load "${SERVICE_NAME}:minikube"
rm -f "/tmp/${SERVICE_NAME}-minikube.tar.gz"
echo "✅ Imagen disponible en Minikube"
EOFLOAD
'''
              }
            }
            
            if (params.DEPLOY_TO_K8S?.toString()?.toBoolean()) {
              // Para GKE: construir y subir a GCR (código original)
              echo "🔨 Construyendo imagen Docker para GKE: ${env.SERVICE_NAME}"
              echo "📦 Registro: ${imageRegistry}"
              echo "🏷️  Tag: ${imageTag}"

              withEnv([
                "GCP_PROJECT_ID=${GCP_PROJECT_ID}",
                "IMAGE_REGISTRY=${imageRegistry}",
                "IMAGE_TAG=${imageTag}",
                "TARGET_IP=${env.BUILD_VM_IP}",
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

echo "🔨 Construyendo y subiendo imagen Docker a GCR..."
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "GCP_PROJECT_ID='$GCP_PROJECT_ID' IMAGE_REGISTRY='$IMAGE_REGISTRY' IMAGE_TAG='$IMAGE_TAG' REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' CREDS_TEMP_FILE='$CREDS_TEMP_FILE' bash -s" <<'EOFBUILD'
set -euo pipefail

GCP_CREDS_FILE="$CREDS_TEMP_FILE"

echo "🔐 Autenticando con Google Cloud..."
gcloud auth activate-service-account --key-file="$GCP_CREDS_FILE"
gcloud config set project "$GCP_PROJECT_ID"
gcloud auth configure-docker gcr.io --quiet

cd "$REMOTE_DIR"

SERVICE_DIR="$REMOTE_DIR/$SERVICE_NAME"
DOCKERFILE_PATH="$SERVICE_DIR/Dockerfile"

if [ ! -d "$SERVICE_DIR" ]; then
  echo "❌ Error: Directorio $SERVICE_DIR no existe"
  exit 1
fi

if [ ! -f "$DOCKERFILE_PATH" ]; then
  echo "❌ Error: Dockerfile no encontrado en $DOCKERFILE_PATH"
  exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔨 Construyendo: $SERVICE_NAME"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

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

echo "🧹 Limpiando credenciales temporales..."
rm -f "$GCP_CREDS_FILE"

echo "✅ Imagen construida y subida exitosamente a GCR"
EOFBUILD
'''
              }
            }
          }
        }
      }
    }

    stage('Deploy to Minikube') {
      when {
        expression { return params.DEPLOY_TO_MINIKUBE?.toString()?.toBoolean() }
      }
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            // Mapeo de servicios a puertos
            def servicePorts = [
              'user-service': '8085',
              'product-service': '8083',
              'order-service': '8081',
              'payment-service': '8082',
              'shipping-service': '8084',
              'favourite-service': '8086',
              'service-discovery': '8761'
            ]
            
            def servicePort = servicePorts[env.SERVICE_NAME] ?: '8080'
            
            echo "🚀 Desplegando ${env.SERVICE_NAME} a Minikube en VM ${env.DROPLET_IP}..."
            
            withEnv([
              "TARGET_IP=${env.MINIKUBE_VM_IP}",
              "SERVICE_NAME=${env.SERVICE_NAME}",
              "SERVICE_PORT=${servicePort}",
              "NAMESPACE=ecommerce",
              "REMOTE_DIR=${env.REMOTE_DIR}"
            ]) {
              sh '''
set -e
export SSHPASS="$VM_PASSWORD"

echo "🚀 Desplegando $SERVICE_NAME a Minikube..."
sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  jenkins@"$TARGET_IP" "SERVICE_NAME='$SERVICE_NAME' SERVICE_PORT='$SERVICE_PORT' NAMESPACE='$NAMESPACE' REMOTE_DIR='$REMOTE_DIR' bash -s" <<'EOFDEPLOY'
set -euo pipefail

export PATH="/usr/local/bin:$PATH"

# Asegurar PATH en VM Minikube
export PATH="/usr/local/bin:$PATH"

# Configurar contexto de Minikube (solo si no está ya activo)
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "")
if [ "$CURRENT_CONTEXT" != "minikube" ]; then
  echo "🔄 Cambiando contexto a minikube..."
  kubectl config use-context minikube
else
  echo "✅ Contexto minikube ya está activo"
fi

# Crear namespace si no existe
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Verificar que ConfigMap y Secrets existen
if ! kubectl get configmap ecommerce-config -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "⚠️ ConfigMap ecommerce-config no existe. Aplicando desde repositorio..."
  if [ -f "$REMOTE_DIR/minikube-deployment/minikube-configmap.yaml" ]; then
    kubectl apply -f "$REMOTE_DIR/minikube-deployment/minikube-configmap.yaml"
  else
    echo "❌ No se encontró minikube-configmap.yaml. Asegúrate de que el repositorio esté sincronizado."
  fi
fi

if ! kubectl get secret ecommerce-secrets -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "⚠️ Secret ecommerce-secrets no existe. Aplicando desde repositorio..."
  if [ -f "$REMOTE_DIR/minikube-deployment/minikube-secrets.yaml" ]; then
    kubectl apply -f "$REMOTE_DIR/minikube-deployment/minikube-secrets.yaml"
  else
    echo "❌ No se encontró minikube-secrets.yaml. Asegúrate de que el repositorio esté sincronizado."
  fi
fi

echo "📦 Desplegando $SERVICE_NAME en Minikube..."

# Aplicar deployment
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $SERVICE_NAME
  namespace: $NAMESPACE
spec:
  replicas: 1
  selector:
    matchLabels:
      app: $SERVICE_NAME
  template:
    metadata:
      labels:
        app: $SERVICE_NAME
    spec:
      containers:
      - name: $SERVICE_NAME
        image: $SERVICE_NAME:minikube
        ports:
        - containerPort: $SERVICE_PORT
        env:
        - name: SERVER_PORT
          value: "$SERVICE_PORT"
        - name: SPRING_CLOUD_CONFIG_ENABLED
          value: "false"
        envFrom:
        - configMapRef:
            name: ecommerce-config
        - secretRef:
            name: ecommerce-secrets
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 400m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: $SERVICE_NAME
  namespace: $NAMESPACE
spec:
  selector:
    app: $SERVICE_NAME
  ports:
  - port: $SERVICE_PORT
    targetPort: $SERVICE_PORT
EOF

echo "⏳ Esperando que $SERVICE_NAME esté listo..."
kubectl wait --for=condition=available --timeout=300s deployment/$SERVICE_NAME -n "$NAMESPACE" || {
  echo "⚠️ Timeout esperando deployment. Verificando estado..."
  kubectl get pods -n "$NAMESPACE" -l app="$SERVICE_NAME"
  exit 1
}

echo "✅ $SERVICE_NAME desplegado exitosamente"

# Verificar estado
echo "📊 Estado del deployment:"
kubectl get deployment $SERVICE_NAME -n "$NAMESPACE"
kubectl get pods -n "$NAMESPACE" -l app="$SERVICE_NAME"
EOFDEPLOY
'''
            }
          }
        }
      }
    }

    stage('Deploy to Kubernetes (GKE)') {
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
    stage('Wait for Service Discovery') {
      when {
        expression { env.SERVICE_NAME != 'service-discovery' && (params.DEPLOY_TO_MINIKUBE?.toString()?.toBoolean()) }
      }
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            withEnv([
              "TARGET_IP=${env.MINIKUBE_VM_IP}",
              "NAMESPACE=ecommerce"
            ]) {
              sh '''
set -e
export SSHPASS="$VM_PASSWORD"

echo "⏳ Esperando que Service Discovery esté UP..."
for i in $(seq 1 30); do
  # ¿Existe pod corriendo?
  if sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    jenkins@"$TARGET_IP" "kubectl get pods -n $NAMESPACE -l app=service-discovery --field-selector=status.phase=Running" | grep -q service-discovery; then

    # Health check dentro del deployment
    if sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
      jenkins@"$TARGET_IP" "kubectl exec -n $NAMESPACE deployment/service-discovery -- curl -s --max-time 5 http://localhost:8761/actuator/health" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      echo "✅ Service Discovery saludable"
      exit 0
    fi
  fi
  echo "⌛ Intento $i/30: esperando Service Discovery..."
  sleep 10
done

echo "❌ Service Discovery no disponible tras 5 minutos"
exit 1
'''
            }
          }
        }
      }
    }

  post {
    success {
      echo "✅ user-service-dev completado. Resultados almacenados en reports/test-reports-user-service.tar.gz (si aplica)."
      script {
        try {
          sh("""curl -X POST "https://api.github.com/repos/OscarMURA/ecommerce-microservice-backend-app/statuses/${env.GIT_COMMIT}" \\
            -H "Authorization: token \${GITHUB_TOKEN}" \\
            -H "Content-Type: application/json" \\
            -d '{"state":"success","description":"Jenkins: Build passed","context":"ci/jenkins/user-service"}'""")
        } catch (Exception e) {
          echo "⚠️ No se pudo actualizar estado en GitHub: ${e.message}"
        }
      }
    }
    failure {
      echo "❌ user-service-dev falló. Revisa los logs para detalles."
      script {
        try {
          sh("""curl -X POST "https://api.github.com/repos/OscarMURA/ecommerce-microservice-backend-app/statuses/${env.GIT_COMMIT}" \\
            -H "Authorization: token \${GITHUB_TOKEN}" \\
            -H "Content-Type: application/json" \\
            -d '{"state":"failure","description":"Jenkins: Build failed","context":"ci/jenkins/user-service"}'""")
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
// End of pipeline
