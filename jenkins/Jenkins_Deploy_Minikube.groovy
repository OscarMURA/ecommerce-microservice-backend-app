pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }

  parameters {
    choice(name: 'VM_CONFIG', choices: ['ecommerce_minikube'], description: 'Configuración de VM para Minikube (debe coincidir con Jenkins_Create_VM)')
    booleanParam(name: 'CLEAN_DEPLOYMENT', defaultValue: false, description: 'Limpiar despliegue anterior antes de desplegar (elimina namespace ecommerce)')
    booleanParam(name: 'RUN_HEALTH_CHECKS', defaultValue: true, description: 'Ejecutar health checks al final del despliegue')
    booleanParam(name: 'ARCHIVE_LOGS', defaultValue: true, description: 'Archivar logs de despliegue como artefactos')
  }

  environment {
    REPO_URL = "https://github.com/OscarMURA/ecommerce-microservice-backend-app.git"
    BRANCH = "develop"
    VM_NAME = "ecommerce-minikube-dev"
    NAMESPACE = "ecommerce"
    DEPLOYMENT_TIMEOUT = "600" // 10 minutos
  }

  stages {
    stage('Validate Prerequisites') {
      steps {
        script {
          echo "🔍 Validando prerrequisitos para despliegue en Minikube..."
          
          // Verificar que la VM existe
          withCredentials([string(credentialsId: 'digitalocean-token', variable: 'DO_TOKEN')]) {
            def vmExists = sh(
              script: """
                curl -sS -H "Authorization: Bearer ${DO_TOKEN}" "https://api.digitalocean.com/v2/droplets?per_page=200" \\
                  | jq -r --arg NAME "${env.VM_NAME}" '.droplets[] | select(.name==\$NAME) | .name' \\
                  | head -n1
              """,
              returnStdout: true
            ).trim()
            
            if (!vmExists) {
              error "❌ VM '${env.VM_NAME}' no encontrada. Ejecuta primero Jenkins_Create_VM con VM_CONFIG='ecommerce_minikube'"
            }
            
            echo "✅ VM '${env.VM_NAME}' encontrada"
          }
          
          // Obtener IP de la VM
          withCredentials([string(credentialsId: 'digitalocean-token', variable: 'DO_TOKEN')]) {
            env.VM_IP = sh(
              script: """
                curl -sS -H "Authorization: Bearer ${DO_TOKEN}" "https://api.digitalocean.com/v2/droplets?per_page=200" \\
                  | jq -r --arg NAME "${env.VM_NAME}" '.droplets[] | select(.name==\$NAME) | .networks.v4[] | select(.type=="public") | .ip_address' \\
                  | head -n1
              """,
              returnStdout: true
            ).trim()
            
            if (!env.VM_IP) {
              error "❌ No se pudo obtener IP de la VM '${env.VM_NAME}'"
            }
            
            echo "🌐 VM IP: ${env.VM_IP}"
          }
        }
      }
    }

    stage('Connect to VM and Prepare Environment') {
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "🔗 Conectando a VM ${env.VM_IP} y preparando entorno..."
            
            // Esperar a que SSH esté disponible
            sh """
              set -e
              echo "⏳ Esperando que SSH esté disponible en ${env.VM_IP}..."
              for i in \$(seq 1 30); do
                if sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "echo SSH ready" >/dev/null 2>&1; then
                  echo "✅ SSH disponible en ${env.VM_IP}"
                  break
                fi
                echo "   reintentando (\$i/30)..."
                sleep 10
              done
            """
            
            // Verificar que Minikube esté instalado
            sh """
              set -e
              echo "🔍 Verificando instalación de Minikube..."
              sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                if command -v minikube >/dev/null 2>&1; then
                  echo '✅ Minikube está instalado'
                  minikube version
                else
                  echo '❌ Minikube no está instalado'
                  exit 1
                fi
                
                if command -v kubectl >/dev/null 2>&1; then
                  echo '✅ kubectl está instalado'
                  kubectl version --client
                else
                  echo '❌ kubectl no está instalado'
                  exit 1
                fi
                
                if command -v docker >/dev/null 2>&1; then
                  echo '✅ Docker está instalado'
                  docker --version
                else
                  echo '❌ Docker no está instalado'
                  exit 1
                fi
              "
            """
          }
        }
      }
    }

    stage('Clone Repository') {
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "📦 Clonando repositorio desde rama ${env.BRANCH}..."
            
            sh """
              set -e
              sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                set -e
                echo '📁 Preparando directorio de trabajo...'
                rm -rf ecommerce-microservice-backend-app
                mkdir -p ecommerce-microservice-backend-app
                cd ecommerce-microservice-backend-app
                
                echo '📥 Clonando repositorio...'
                git clone -b ${env.BRANCH} ${env.REPO_URL} .
                
                echo '📋 Información del repositorio:'
                git log --oneline -5
                echo '🌿 Rama actual:'
                git branch --show-current
                echo '📊 Estado del repositorio:'
                git status
              "
            """
          }
        }
      }
    }

    stage('Clean Previous Deployment') {
      when {
        expression { params.CLEAN_DEPLOYMENT }
      }
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "🧹 Limpiando despliegue anterior..."
            
            sh """
              set -e
              sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                set -e
                echo '🛑 Deteniendo Minikube...'
                minikube stop || true
                
                echo '🗑️ Eliminando Minikube completamente...'
                minikube delete || true
                
                echo '✅ Limpieza completada'
              "
            """
          }
        }
      }
    }

    stage('Deploy to Minikube') {
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "🚀 Iniciando despliegue en Minikube..."
            
            // Ejecutar el script de despliegue
            sh """
              set -e
              sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                set -e
                cd ecommerce-microservice-backend-app
                
                echo '🚀 Ejecutando script de despliegue en Minikube...'
                chmod +x minikube-deployment/test-minikube.sh
                ./minikube-deployment/test-minikube.sh
                
                echo '✅ Script de despliegue completado'
              "
            """
          }
        }
      }
    }

    stage('Verify Deployment') {
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "🔍 Verificando estado del despliegue..."
            
            sh """
              set -e
              sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                set -e
                echo '📊 Estado de los deployments:'
                kubectl get deployments -n ${env.NAMESPACE}
                
                echo '📊 Estado de los pods:'
                kubectl get pods -n ${env.NAMESPACE}
                
                echo '📊 Estado de los services:'
                kubectl get services -n ${env.NAMESPACE}
                
                echo '⏳ Esperando que todos los pods estén listos...'
                kubectl wait --for=condition=ready pod --all -n ${env.NAMESPACE} --timeout=${env.DEPLOYMENT_TIMEOUT}s || true
              "
            """
          }
        }
      }
    }

    stage('Health Checks') {
      when {
        expression { params.RUN_HEALTH_CHECKS }
      }
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "🏥 Ejecutando health checks..."
            
            // Esperar a que los servicios estén completamente listos
            echo "⏳ Esperando 60 segundos para que los servicios se inicialicen completamente..."
            sleep(time: 60, unit: 'SECONDS')
            
            def healthCheckResults = [:]
            def services = [
              'service-discovery': ['8761', ''],
              'order-service': ['8081', '/order-service'],
              'payment-service': ['8082', '/payment-service'],
              'product-service': ['8083', '/product-service'],
              'shipping-service': ['8084', '/shipping-service'],
              'user-service': ['8085', '/user-service'],
              'favourite-service': ['8086', '/favourite-service']
            ]
            
            services.each { service, config ->
              def port = config[0]
              def path = config[1]
              try {
                // Intentar health check con timeout
                def healthResult = sh(
                  script: """
                    sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                      timeout 30 kubectl exec -n ${env.NAMESPACE} deployment/${service} -- curl -s --max-time 10 http://localhost:${port}${path}/actuator/health 2>/dev/null || echo 'HEALTH_CHECK_FAILED'
                    "
                  """,
                  returnStdout: true
                ).trim()
                
                if (healthResult.contains('"status":"UP"')) {
                  healthCheckResults[service] = 'UP'
                  echo "✅ ${service}: UP"
                } else if (healthResult.contains('HEALTH_CHECK_FAILED')) {
                  healthCheckResults[service] = 'DOWN'
                  echo "❌ ${service}: DOWN - Health check failed"
                } else {
                  healthCheckResults[service] = 'DOWN'
                  echo "❌ ${service}: DOWN - ${healthResult}"
                }
              } catch (Exception e) {
                healthCheckResults[service] = 'ERROR'
                echo "❌ ${service}: ERROR - ${e.getMessage()}"
              }
            }
            
            // Guardar resultados de health checks
            def jsonString = "{"
            healthCheckResults.each { service, status ->
              jsonString += "\"${service}\": \"${status}\","
            }
            jsonString = jsonString.substring(0, jsonString.length() - 1) + "}"
            writeFile file: 'health-check-results.json', text: jsonString
            
            // Verificar si todos los servicios están UP
            def allServicesUp = healthCheckResults.values().every { it == 'UP' }
            if (!allServicesUp) {
              echo "⚠️ Algunos servicios no están funcionando correctamente"
              echo "Resultados: ${healthCheckResults}"
            } else {
              echo "✅ Todos los servicios están funcionando correctamente"
            }
          }
        }
      }
    }

    stage('Generate Access URLs') {
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "🌐 Generando URLs de acceso a los servicios..."
            
            def accessUrls = sh(
              script: """
                sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                  echo '🔗 URLs de acceso a los servicios:'
                  echo 'Service Discovery (Eureka):'
                  minikube service service-discovery -n ${env.NAMESPACE} --url || echo 'No disponible'
                  echo 'Zipkin (Tracing):'
                  minikube service zipkin -n ${env.NAMESPACE} --url || echo 'No disponible'
                "
              """,
              returnStdout: true
            ).trim()
            
            echo accessUrls
            
            // Guardar URLs de acceso
            writeFile file: 'access-urls.txt', text: accessUrls
          }
        }
      }
    }

    stage('Archive Deployment Logs') {
      when {
        expression { params.ARCHIVE_LOGS }
      }
      steps {
        withCredentials([
          string(credentialsId: 'integration-vm-password', variable: 'VM_PASSWORD')
        ]) {
          script {
            echo "📦 Archivando logs de despliegue..."
            
            // Obtener logs de todos los servicios
            sh """
              set -e
              sshpass -p "${VM_PASSWORD}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null jenkins@${env.VM_IP} "
                set -e
                cd ecommerce-microservice-backend-app
                mkdir -p deployment-logs
                
                echo '📋 Obteniendo logs de todos los servicios...'
                kubectl logs -n ${env.NAMESPACE} deployment/service-discovery --tail=50 > deployment-logs/service-discovery.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/order-service --tail=50 > deployment-logs/order-service.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/payment-service --tail=50 > deployment-logs/payment-service.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/product-service --tail=50 > deployment-logs/product-service.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/shipping-service --tail=50 > deployment-logs/shipping-service.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/user-service --tail=50 > deployment-logs/user-service.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/favourite-service --tail=50 > deployment-logs/favourite-service.log || true
                kubectl logs -n ${env.NAMESPACE} deployment/zipkin --tail=50 > deployment-logs/zipkin.log || true
                
                echo '📊 Estado final del cluster:'
                kubectl get all -n ${env.NAMESPACE} > deployment-logs/cluster-status.txt
                
                echo '✅ Logs archivados'
              "
            """
            
            // Descargar logs desde la VM
            sh """
              set -e
              sshpass -p "${VM_PASSWORD}" scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -r jenkins@${env.VM_IP}:ecommerce-microservice-backend-app/deployment-logs . || true
            """
            
            // Archivar logs como artefactos
            archiveArtifacts artifacts: 'deployment-logs/**/*', fingerprint: true
            archiveArtifacts artifacts: 'health-check-results.json', fingerprint: true
            archiveArtifacts artifacts: 'access-urls.txt', fingerprint: true
          }
        }
      }
    }

    stage('Summary') {
      steps {
        script {
          def ip = env.VM_IP ?: 'N/A'
          echo """
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 Jenkins_Deploy_Minikube Summary
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Repositorio      : ${env.REPO_URL}
• Rama             : ${env.BRANCH}
• VM               : ${env.VM_NAME}
• VM IP            : ${ip}
• Namespace        : ${env.NAMESPACE}
• Limpieza previa  : ${params.CLEAN_DEPLOYMENT}
• Health checks    : ${params.RUN_HEALTH_CHECKS}
• Logs archivados  : ${params.ARCHIVE_LOGS}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
          """
        }
      }
    }
  }

  post {
    success {
      echo "✅ Pipeline Jenkins_Deploy_Minikube completado exitosamente."
      echo "🌐 Los microservicios están desplegados en Minikube en la VM ${env.VM_IP}"
      echo "📊 Revisa los artefactos para logs y URLs de acceso"
    }
    failure {
      echo "❌ Pipeline Jenkins_Deploy_Minikube falló."
      echo "🔍 Revisa los logs para más detalles"
      echo "💡 Sugerencias:"
      echo "   - Verifica que la VM esté funcionando"
      echo "   - Revisa que Minikube esté instalado correctamente"
      echo "   - Considera usar CLEAN_DEPLOYMENT=true para limpiar despliegues anteriores"
    }
    always {
      cleanWs(patterns: [[pattern: 'deployment-logs/**', type: 'EXCLUDE']])
    }
  }
}
