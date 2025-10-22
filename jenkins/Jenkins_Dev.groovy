pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    string(name: 'VM_NAME', defaultValue: 'ecommerce-integration-runner', description: 'Nombre de la VM creada por Jenkins_Create_VM')
    string(name: 'VM_REGION', defaultValue: 'nyc3', description: 'Región del droplet (usado si hay que crearlo)')
    string(name: 'VM_SIZE', defaultValue: 's-1vcpu-2gb', description: 'Tamaño del droplet (usado si hay que crearlo)')
    string(name: 'VM_IMAGE', defaultValue: 'ubuntu-22-04-x64', description: 'Imagen del droplet (usado si hay que crearlo)')
    string(name: 'JENKINS_CREATE_VM_JOB', defaultValue: 'Jenkins_Create_VM', description: 'Nombre del pipeline que aprovisiona la VM en DigitalOcean')
    string(name: 'REPO_URL', defaultValue: 'https://github.com/OscarMURA/ecommerce-microservice-backend-app.git', description: 'Repositorio a clonar en la VM')
    string(name: 'APP_BRANCH', defaultValue: 'main', description: 'Branch del repositorio a usar')
  }

  environment {
    REMOTE_BASE = "/opt/ecommerce-app"
    REMOTE_DIR = "${env.REMOTE_BASE}/backend"
    UNIT_SERVICES = "user-service product-service order-service favourite-service shipping-service payment-service"
  }

  stages {
    stage('Validate Branch') {
      steps {
        script {
          def branch = env.BRANCH_NAME ?: env.GIT_BRANCH ?: ''
          branch = branch.replaceFirst('^origin/', '')

          if (!(branch == 'develop' || branch.startsWith('feat/'))) {
            error "Jenkins_Dev solo se ejecuta en ramas develop o feat/** (rama actual: '${branch ?: 'desconocida'}')."
          }

          echo "Branch validada: ${branch}"
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

    stage('Ensure VM Available') {
      steps {
        withCredentials([string(credentialsId: 'digitalocean-token', variable: 'DO_TOKEN')]) {
          script {
            def fetchIp = {
              return sh(script: """
                set -e
                curl -sS -H "Authorization: Bearer ${DO_TOKEN}" "https://api.digitalocean.com/v2/droplets?per_page=200" \\
                  | jq -r --arg NAME "${params.VM_NAME}" '.droplets[] | select(.name==\\$NAME) | .networks.v4[] | select(.type=="public") | .ip_address' \\
                  | head -n1
              """, returnStdout: true).trim()
            }

            def currentIp = fetchIp()
            if (!currentIp) {
              echo "No se encontró la VM ${params.VM_NAME}. Solicitando creación..."
              build job: params.JENKINS_CREATE_VM_JOB, wait: true, propagate: true, parameters: [
                string(name: 'ACTION', value: 'create'),
                string(name: 'VM_NAME', value: params.VM_NAME),
                string(name: 'VM_REGION', value: params.VM_REGION),
                string(name: 'VM_SIZE', value: params.VM_SIZE),
                string(name: 'VM_IMAGE', value: params.VM_IMAGE),
                booleanParam(name: 'ARCHIVE_METADATA', value: true)
              ]
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
            sh '''
              set -e
              export SSHPASS="${VM_PASSWORD}"
              for i in $(seq 1 30); do
                if sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" "echo VM ready"; then
                  break
                fi
                echo "Esperando acceso SSH a ${DROPLET_IP}... ($i/30)"
                sleep 10
              done
            '''

            sh """
              set -e
              export SSHPASS="${VM_PASSWORD}"
              sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" <<'EOF'
set -euo pipefail
mkdir -p "${REMOTE_BASE}"
cd "${REMOTE_BASE}"
if [ ! -d backend/.git ]; then
  rm -rf backend || true
  git clone "${REPO_URL}" backend
fi
cd backend
git fetch --all
git checkout "${APP_BRANCH}"
git reset --hard "origin/${APP_BRANCH}"
git clean -fd
chmod +x mvnw || true
git config --global --add safe.directory "${REMOTE_DIR}" || true
EOF
            """
          }
        }
      }
    }

    stage('Unit Tests') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          sh """
            set -e
            export SSHPASS="\${VM_PASSWORD}"
            sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" <<'EOF'
set -euo pipefail
cd "${REMOTE_DIR}"
for svc in ${UNIT_SERVICES}; do
  echo "➡️ Ejecutando pruebas unitarias para \$svc"
  ./mvnw -B -pl \$svc test -Dtest='*ApplicationTests' -DfailIfNoTests=false
done
EOF
          """
        }
      }
    }

    stage('Integration Tests') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          sh """
            set -e
            export SSHPASS="\${VM_PASSWORD}"
            sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" <<'EOF'
set -euo pipefail
cd "${REMOTE_DIR}"
for svc in ${UNIT_SERVICES}; do
  echo "➡️ Ejecutando pruebas de integración para \$svc"
  ./mvnw -B -pl \$svc test -Dtest='*IntegrationTest' -DfailIfNoTests=false
done
EOF
          """
        }
      }
    }

    stage('E2E Tests') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          sh """
            set -e
            export SSHPASS="\${VM_PASSWORD}"
            sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" <<'EOF'
set -euo pipefail
cd "${REMOTE_DIR}"
echo "➡️ Ejecutando pruebas E2E"
./mvnw -B -pl e2e-tests test -Dtest='*E2E*Test' -DfailIfNoTests=false
EOF
          """
        }
      }
    }

    stage('Recolectar Reportes') {
      steps {
        withCredentials([string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')]) {
          sh """
            set -e
            export SSHPASS="\${VM_PASSWORD}"
            sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" <<'EOF'
set -euo pipefail
cd "${REMOTE_DIR}"
tar -czf /tmp/test-reports.tar.gz \\
  user-service/target/surefire-reports \\
  product-service/target/surefire-reports \\
  favourite-service/target/surefire-reports \\
  order-service/target/surefire-reports \\
  shipping-service/target/surefire-reports \\
  payment-service/target/surefire-reports \\
  e2e-tests/target/surefire-reports 2>/dev/null || true
EOF

            mkdir -p reports
            sshpass -e scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \\
              jenkins@"${DROPLET_IP}":/tmp/test-reports.tar.gz reports/test-reports.tar.gz || true
            sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@"${DROPLET_IP}" "rm -f /tmp/test-reports.tar.gz" || true
          """
          archiveArtifacts artifacts: 'reports/test-reports.tar.gz', fingerprint: true, allowEmptyArchive: true
        }
      }
    }
  }

  post {
    success {
      echo "✅ Jenkins_Dev completado. Resultados almacenados en reports/test-reports.tar.gz (si aplica)."
    }
    failure {
      echo "❌ Jenkins_Dev falló. Revisa los logs para detalles."
    }
    always {
      cleanWs()
    }
  }
}
