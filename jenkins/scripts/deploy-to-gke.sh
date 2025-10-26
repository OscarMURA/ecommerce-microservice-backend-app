#!/usr/bin/env bash
# Deploy selected microservices to a GKE cluster and report their health.

set -euo pipefail

YELLOW="\033[1;33m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
RED="\033[0;31m"
NC="\033[0m"

INFO_TAG="[INFO]"
WARN_TAG="[WARN]"
OK_TAG="[OK]"
ERR_TAG="[ERROR]"

log_info() {
  echo -e "${BLUE}${INFO_TAG}${NC} $*"
}

log_warn() {
  echo -e "${YELLOW}${WARN_TAG}${NC} $*"
}

log_success() {
  echo -e "${GREEN}${OK_TAG}${NC} $*"
}

log_error() {
  echo -e "${RED}${ERR_TAG}${NC} $*"
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    log_error "La variable de entorno ${name} es requerida."
    exit 1
  fi
}

require_cmd() {
  local binary="$1"
  if ! command -v "${binary}" >/dev/null 2>&1; then
    log_error "El comando '${binary}' no está disponible en el agente Jenkins."
    exit 1
  fi
}

# Required environment variables supplied by Jenkins stage.
require_env "GCP_PROJECT_ID"
require_env "GKE_CLUSTER_NAME"
require_env "GKE_CLUSTER_LOCATION"
require_env "K8S_NAMESPACE"
require_env "K8S_SERVICE_LIST"
require_env "K8S_IMAGE_REGISTRY"
require_env "K8S_IMAGE_TAG"
require_env "INFRA_REPO_DIR"
require_env "GOOGLE_APPLICATION_CREDENTIALS"

# Optional overrides.
K8S_ENVIRONMENT="${K8S_ENVIRONMENT:-dev}"
K8S_DEFAULT_REPLICAS="${K8S_DEFAULT_REPLICAS:-1}"
K8S_ROLLOUT_TIMEOUT="${K8S_ROLLOUT_TIMEOUT:-240}"

export CLOUDSDK_CORE_DISABLE_PROMPTS=1
export USE_GKE_GCLOUD_AUTH_PLUGIN=True
export PATH="/opt/google-cloud-sdk/google-cloud-sdk/bin:${PATH}"

require_cmd "gcloud"
require_cmd "kubectl"

if [[ ! -d "${INFRA_REPO_DIR}" ]]; then
  log_error "El repositorio de infraestructura no existe en ${INFRA_REPO_DIR}"
  exit 1
fi

log_info "Autenticando con Google Cloud..."
gcloud auth activate-service-account --key-file "${GOOGLE_APPLICATION_CREDENTIALS}" >/dev/null 2>&1 || {
  log_error "No se pudo autenticar la service account de GCP."
  exit 1
}
gcloud config set project "${GCP_PROJECT_ID}" >/dev/null

log_info "Obteniendo credenciales del cluster ${GKE_CLUSTER_NAME} (${GKE_CLUSTER_LOCATION})..."
if ! gcloud container clusters get-credentials "${GKE_CLUSTER_NAME}" --zone "${GKE_CLUSTER_LOCATION}" --project "${GCP_PROJECT_ID}" >/dev/null 2>&1; then
  log_error "No fue posible obtener credenciales para el cluster."
  exit 1
fi
log_success "Credenciales configuradas."

declare -A SERVICE_PORTS=(
  [service-discovery]=8761
  [user-service]=8085
  [product-service]=8083
  [favourite-service]=8086
  [order-service]=8081
  [shipping-service]=8084
  [payment-service]=8082
)

# Configuración de tipos de servicio
declare -A SERVICE_TYPES
# Sin LoadBalancer por defecto - todos los servicios usan ClusterIP

# Rutas de health check para cada servicio (CRÍTICO: usadas por readiness probes)
declare -A SERVICE_HEALTH_PATH=(
  [service-discovery]="/actuator/health"
  [user-service]="/user-service/actuator/health"
  [product-service]="/product-service/actuator/health"
  [favourite-service]="/favourite-service/actuator/health"
  [order-service]="/order-service/actuator/health"
  [shipping-service]="/shipping-service/actuator/health"
  [payment-service]="/payment-service/actuator/health"
)

declare -A SERVICE_REPLICAS=(
  # Todos los servicios usan 1 réplica por defecto
)

readarray -t RAW_SERVICES < <(printf '%s\n' "${K8S_SERVICE_LIST}" | tr ',;' '\n' | tr ' ' '\n')

SERVICES=()
declare -A SEEN
for svc in "${RAW_SERVICES[@]}"; do
  svc="$(echo "${svc}" | tr '[:upper:]' '[:lower:]' | xargs)"
  [[ -z "${svc}" ]] && continue
  if [[ -n "${SERVICE_PORTS[${svc}]+_}" && -z "${SEEN[${svc}]:-}" ]]; then
    SERVICES+=("${svc}")
    SEEN["${svc}"]=1
  fi
done

for dep in service-discovery; do
  if [[ -z "${SEEN[${dep}]:-}" ]]; then
    log_warn "Agregando servicio dependiente requerido '${dep}'."
    SERVICES=("${dep}" "${SERVICES[@]}")
    SEEN["${dep}"]=1
  fi
done

if [[ "${#SERVICES[@]}" -eq 0 ]]; then
  log_error "No se proporcionaron servicios válidos para desplegar."
  exit 1
fi

log_info "Servicios a desplegar: ${SERVICES[*]}"

# Función para detectar si un servicio necesita ser reconstruido
needs_rebuild() {
  local svc="$1"
  local current_commit="$2"
  
  # Verificar si el servicio tiene cambios desde el último commit
  if git diff --quiet HEAD~1 HEAD -- "${svc}/"; then
    log_info "✅ ${svc}: Sin cambios detectados, usando imagen existente"
    return 1  # No necesita rebuild
  else
    log_info "🔄 ${svc}: Cambios detectados, necesita rebuild"
    return 0  # Necesita rebuild
  fi
}

# Función para verificar si un servicio está funcionando correctamente
is_service_healthy() {
  local svc="$1"
  
  # Verificar si el deployment existe
  if kubectl --namespace "${K8S_NAMESPACE}" get deployment "${svc}" &>/dev/null 2>&1; then
    # Verificar si está listo (Ready/1) - MÁS ESTRICTO
    local ready_replicas=$(kubectl --namespace "${K8S_NAMESPACE}" get deployment "${svc}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
    local desired_replicas=$(kubectl --namespace "${K8S_NAMESPACE}" get deployment "${svc}" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
    local available_replicas=$(kubectl --namespace "${K8S_NAMESPACE}" get deployment "${svc}" -o jsonpath='{.status.availableReplicas}' 2>/dev/null || echo "0")
    
    # Verificar que TODOS los pods estén Ready y Available
    if [[ "${ready_replicas}" -eq "${desired_replicas}" && "${available_replicas}" -eq "${desired_replicas}" && "${ready_replicas}" -gt 0 ]]; then
      # Verificación ADICIONAL: comprobar que el pod esté realmente funcionando
      local pod_name=$(kubectl --namespace "${K8S_NAMESPACE}" get pod -l app="${svc}" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
      if [[ -n "${pod_name}" ]]; then
        local pod_status=$(kubectl --namespace "${K8S_NAMESPACE}" get pod "${pod_name}" -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
        local pod_ready=$(kubectl --namespace "${K8S_NAMESPACE}" get pod "${pod_name}" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
        
        if [[ "${pod_status}" == "Running" && "${pod_ready}" == "True" ]]; then
          log_info "✅ ${svc}: Ya está funcionando correctamente (${ready_replicas}/${desired_replicas} ready, ${available_replicas} available)"
          return 0  # Está funcionando
        else
          log_info "❌ ${svc}: Pod no está funcionando correctamente (Status: ${pod_status}, Ready: ${pod_ready})"
          return 1  # Necesita redeploy
        fi
      else
        log_info "❌ ${svc}: No se encontró pod activo"
        return 1  # Necesita redeploy
      fi
    else
      log_info "❌ ${svc}: No está funcionando correctamente (Ready: ${ready_replicas}/${desired_replicas}, Available: ${available_replicas}/${desired_replicas})"
      return 1  # Necesita redeploy
    fi
  else
    log_info "🆕 ${svc}: No existe, necesita deploy inicial"
    return 1  # Necesita deploy
  fi
}

# Función para detectar si un servicio crítico necesita rebuild forzado
critical_service_needs_rebuild() {
  local svc="$1"
  
  # Verificar si hay cambios en el directorio del servicio
  if ! git diff --quiet HEAD~1 HEAD -- "${svc}/"; then
    log_info "🔄 ${svc}: Cambios detectados en servicio crítico, forzando rebuild"
    return 0  # Necesita rebuild
  fi
  
  # Verificar si el commit message indica rebuild forzado
  local commit_message=$(git log -1 --pretty=%B)
  if echo "${commit_message}" | grep -qi "rebuild.*${svc}\|${svc}.*rebuild\|force.*${svc}\|${svc}.*force"; then
    log_info "🔄 ${svc}: Commit message indica rebuild forzado"
    return 0  # Necesita rebuild
  fi
  
  # Verificación ESPECIAL para servicios críticos: detectar problemas comunes
  if [[ "${svc}" == "service-discovery" ]]; then
    local pod_name=$(kubectl --namespace "${K8S_NAMESPACE}" get pod -l app="service-discovery" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [[ -n "${pod_name}" ]]; then
      # Verificar logs para detectar problemas de Eureka
      local logs=$(kubectl --namespace "${K8S_NAMESPACE}" logs "${pod_name}" --tail=20 2>/dev/null || echo "")
      if echo "${logs}" | grep -q "Unable to start web server"; then
        log_warn "🔄 ${svc}: Detectado problema de inicio, forzando rebuild"
        return 0  # Necesita rebuild
      fi
    fi
  fi
  
  log_info "✅ ${svc}: Sin cambios en servicio crítico, preservando"
  return 1  # No necesita rebuild
}

# Servicios críticos que NO se deben tocar si están funcionando
CRITICAL_SERVICES=("service-discovery")

# Detectar servicios que necesitan rebuild
SERVICES_TO_BUILD=()
SERVICES_TO_DEPLOY=()
SERVICES_TO_CLEAN=()

# Verificar servicios críticos antes de limpiar
log_info "🔍 Verificando estado de servicios críticos..."
for svc in "${CRITICAL_SERVICES[@]}"; do
  if [[ " ${SERVICES[*]} " =~ " ${svc} " ]]; then
    if is_service_healthy "${svc}"; then
      log_success "✅ ${svc}: Funcionando correctamente, preservando..."
      # Solo agregar a rebuild si hay cambios o commit message lo indica
      if critical_service_needs_rebuild "${svc}"; then
        SERVICES_TO_BUILD+=("${svc}")
        SERVICES_TO_CLEAN+=("${svc}")
        log_warn "🔄 ${svc}: Cambios detectados, forzando rebuild"
      else
        log_info "🛡️  ${svc}: Sin cambios, preservando deployment existente"
      fi
    else
      log_warn "⚠️  ${svc}: Necesita redeploy"
      SERVICES_TO_BUILD+=("${svc}")
      SERVICES_TO_CLEAN+=("${svc}")
    fi
  fi
done

# Procesar servicios no críticos
for svc in "${SERVICES[@]}"; do
  # Si es un servicio crítico, ya fue procesado arriba
  if [[ " ${CRITICAL_SERVICES[*]} " =~ " ${svc} " ]]; then
    continue
  fi
  
  # Servicios no críticos: siempre verificar si necesitan rebuild
  if needs_rebuild "${svc}" "${K8S_IMAGE_TAG}"; then
    SERVICES_TO_BUILD+=("${svc}")
  fi
  
  # Agregar a deploy y clean
  SERVICES_TO_DEPLOY+=("${svc}")
  SERVICES_TO_CLEAN+=("${svc}")
done

log_info "📋 Servicios que necesitan rebuild: ${SERVICES_TO_BUILD[*]:-ninguno}"
log_info "📋 Servicios a desplegar: ${SERVICES_TO_DEPLOY[*]}"
log_info "📋 Servicios a limpiar: ${SERVICES_TO_CLEAN[*]:-ninguno}"

# Limpieza selectiva: solo limpiar servicios que necesitan redeploy
log_info "🧹 Limpiando deployments selectivamente..."
for svc in "${SERVICES_TO_CLEAN[@]}"; do
  # Si es un servicio crítico y está funcionando, NO limpiar
  if [[ " ${CRITICAL_SERVICES[*]} " =~ " ${svc} " ]] && is_service_healthy "${svc}" && ! critical_service_needs_rebuild "${svc}"; then
    log_info "🛡️  Preservando ${svc} (servicio crítico funcionando sin cambios)"
    continue
  fi
  
  # Limpiar solo si necesita redeploy
  log_info "🧹 Limpiando ${svc} (necesita redeploy)..."
  if kubectl --namespace "${K8S_NAMESPACE}" get deployment "${svc}" &>/dev/null 2>&1; then
    kubectl --namespace "${K8S_NAMESPACE}" delete deployment "${svc}" --cascade=background --ignore-not-found=true 2>/dev/null &
  fi
done

# Esperar a que todos los deletes terminen en paralelo
wait

# Espera rápida para que los pods se terminen
log_info "⏳ Esperando a que los pods antiguos se terminen..."
for svc in "${SERVICES_TO_CLEAN[@]}"; do
  # Solo limpiar pods si el servicio fue limpiado
  if [[ " ${CRITICAL_SERVICES[*]} " =~ " ${svc} " ]] && is_service_healthy "${svc}" && ! critical_service_needs_rebuild "${svc}"; then
    continue  # No limpiar pods de servicios críticos preservados
  fi
  
  kubectl --namespace "${K8S_NAMESPACE}" delete pods -l app="${svc}" --grace-period=5 --ignore-not-found=true 2>/dev/null || true &
done
wait

log_success "✅ Limpieza selectiva de deployments completada."

BASE_MANIFEST_DIR="${INFRA_REPO_DIR}/kubernetes/manifests"
if [[ -d "${BASE_MANIFEST_DIR}" ]]; then
  for base in namespace.yaml configmap.yaml; do
    if [[ -f "${BASE_MANIFEST_DIR}/${base}" ]]; then
      log_info "Aplicando manifiesto base ${base}..."
      kubectl apply -f "${BASE_MANIFEST_DIR}/${base}"
    fi
  done
  
  # Aplicar el secret base (sin docker-registry-secret, lo crearemos después)
  if [[ -f "${BASE_MANIFEST_DIR}/secret.yaml" ]]; then
    log_info "Aplicando secrets base (sin docker-registry-secret)..."
    kubectl apply -f "${BASE_MANIFEST_DIR}/secret.yaml" --namespace "${K8S_NAMESPACE}" || true
  fi
else
  log_warn "No se encontró ${BASE_MANIFEST_DIR}; se continuará sin manifiestos base."
fi

kubectl get namespace "${K8S_NAMESPACE}" >/dev/null 2>&1 || {
  log_info "Creando namespace ${K8S_NAMESPACE}..."
  kubectl create namespace "${K8S_NAMESPACE}"
}

log_info "Configurando secreto de Docker Registry para GCR..."
REGISTRY_HOST="$(echo "${K8S_IMAGE_REGISTRY}" | cut -d/ -f1)"
if [[ -z "${REGISTRY_HOST}" ]]; then
  log_warn "No se pudo derivar el host del registro desde '${K8S_IMAGE_REGISTRY}'. Usando gcr.io."
  REGISTRY_HOST="gcr.io"
fi
kubectl --namespace "${K8S_NAMESPACE}" create secret docker-registry docker-registry-secret \
  --docker-server="${REGISTRY_HOST}" \
  --docker-username=_json_key \
  --docker-password="$(cat ${GOOGLE_APPLICATION_CREDENTIALS})" \
  --docker-email=jenkins@ecommerce.local \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl --namespace "${K8S_NAMESPACE}" patch configmap ecommerce-config --type merge \
  --patch "{\"data\":{\"SPRING_PROFILES_ACTIVE\":\"${K8S_ENVIRONMENT}\"}}" >/dev/null 2>&1 || true

RENDER_DIR="$(mktemp -d)"
log_info "Manifiestos renderizados en ${RENDER_DIR}"

render_manifest() {
  local svc="$1"
  local port="${SERVICE_PORTS[${svc}]}"
  local service_type="${SERVICE_TYPES[${svc}]:-ClusterIP}"
  
  # CRÍTICO: Obtener health_path del array global con fallback seguro
  # Nota: En bash, el array se accede como SERVICE_HEALTH_PATH[$svc]
  # Si no existe, usar el fallback /actuator/health
  local health_path="/actuator/health"
  if [[ -n "${SERVICE_HEALTH_PATH[${svc}]:-}" ]]; then
    health_path="${SERVICE_HEALTH_PATH[${svc}]}"
  fi
  
  local replicas="${SERVICE_REPLICAS[${svc}]:-${K8S_DEFAULT_REPLICAS}}"
  local manifest="${RENDER_DIR}/${svc}.yaml"
  local extra_env_block=""
  # Recursos optimizados para GKE con nodos más potentes
  local cpu_request="200m"
  local cpu_limit="800m"
  local mem_request="512Mi"
  local mem_limit="1Gi"

  # Asignar recursos específicos según las necesidades de cada servicio
  if [[ "${svc}" == "service-discovery" ]]; then
    # service-discovery es crítico pero más ligero
    cpu_request="100m"
    cpu_limit="500m"
    mem_request="512Mi"
    mem_limit="1Gi"
  fi

  # Probes optimizadas basadas en la configuración exitosa de Minikube
  # Usar configuración más simple y rápida que funciona en Minikube
  if [[ "${svc}" == "service-discovery" ]]; then
    # Service discovery: configuración que funciona en Minikube
    READINESS_INITIAL_DELAY="60"
    READINESS_FAILURE_THRESHOLD="10"
    LIVENESS_INITIAL_DELAY="120"
    LIVENESS_FAILURE_THRESHOLD="5"
  else
    # Microservicios de negocio: tiempo realista basado en logs (89s + margen)
    READINESS_INITIAL_DELAY="150"
    READINESS_FAILURE_THRESHOLD="15"
    LIVENESS_INITIAL_DELAY="200"
    LIVENESS_FAILURE_THRESHOLD="5"
  fi

  # Configuración embebida para todos los servicios (sin cloud-config)
  extra_env_block="            - name: SPRING_CLOUD_CONFIG_ENABLED
              value: \"false\"
            - name: SPRING_PROFILES_ACTIVE
              value: \"${K8S_ENVIRONMENT:-dev}\""

  cat > "${manifest}" <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${svc}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: "${svc}"
    environment: "${K8S_ENVIRONMENT}"
    app.kubernetes.io/name: "${svc}"
    app.kubernetes.io/part-of: "ecommerce-platform"
    app.kubernetes.io/managed-by: "jenkins"
    app.kubernetes.io/version: "${K8S_IMAGE_TAG}"
    app.kubernetes.io/instance: "${svc}-${K8S_ENVIRONMENT}"
  annotations:
    jenkins.io/job: "${JOB_NAME:-unknown}"
    jenkins.io/build-number: "${BUILD_NUMBER:-0}"
    jenkins.io/git-commit: "${GIT_COMMIT:-unknown}"
spec:
  replicas: ${replicas}
  progressDeadlineSeconds: 900
  selector:
    matchLabels:
      app: "${svc}"
  template:
    metadata:
      labels:
        app: "${svc}"
        environment: "${K8S_ENVIRONMENT}"
        app.kubernetes.io/name: "${svc}"
        app.kubernetes.io/instance: "${svc}-${K8S_ENVIRONMENT}"
    spec:
      imagePullSecrets:
        - name: docker-registry-secret
      containers:
        - name: ${svc}
          image: ${K8S_IMAGE_REGISTRY}/${svc}:${K8S_IMAGE_TAG}
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: ${port}
              protocol: TCP
          env:
            - name: SERVER_PORT
              value: "${port}"
${extra_env_block}
          envFrom:
            - configMapRef:
                name: ecommerce-config
            - secretRef:
                name: ecommerce-secrets
          # Readiness and Liveness probes para verificar que el servicio está listo
          readinessProbe:
            httpGet:
              path: ${health_path}
              port: ${port}
            initialDelaySeconds: ${READINESS_INITIAL_DELAY:-90}
            periodSeconds: 5
            failureThreshold: ${READINESS_FAILURE_THRESHOLD:-50}
            timeoutSeconds: 3
            successThreshold: 1
          livenessProbe:
            httpGet:
              path: ${health_path}
              port: ${port}
            initialDelaySeconds: ${LIVENESS_INITIAL_DELAY:-300}
            periodSeconds: 30
            failureThreshold: ${LIVENESS_FAILURE_THRESHOLD:-10}
            timeoutSeconds: 3
          resources:
            requests:
              cpu: ${cpu_request}
              memory: ${mem_request}
            limits:
              cpu: ${cpu_limit}
              memory: ${mem_limit}
---
apiVersion: v1
kind: Service
metadata:
  name: ${svc}
  namespace: ${K8S_NAMESPACE}
  labels:
    app: "${svc}"
    environment: "${K8S_ENVIRONMENT}"
spec:
  selector:
    app: "${svc}"
  type: ${service_type}
  ports:
    - name: http
      port: ${port}
      targetPort: http
EOF
}

LB_SERVICES=()

# Verificar que hay nodos disponibles antes de desplegar
log_info "Verificando disponibilidad de nodos del cluster..."
AVAILABLE_NODES=$(kubectl get nodes --no-headers 2>/dev/null | wc -l)
if [[ "${AVAILABLE_NODES}" -eq 0 ]]; then
  log_error "No hay nodos disponibles en el cluster ${GKE_CLUSTER_NAME}"
  exit 1
fi
log_info "Nodos disponibles: ${AVAILABLE_NODES}"

# Primera fase: desplegar service-discovery PRIMERO
if [[ -n "${SEEN[service-discovery]:-}" ]]; then
  if [[ -z "${SERVICE_PORTS[service-discovery]+_}" ]]; then
    log_error "Servicio 'service-discovery' no está soportado por el pipeline."
    exit 1
  fi
  render_manifest "service-discovery"
  log_info "Aplicando servicio crítico: service-discovery..."
  kubectl --namespace "${K8S_NAMESPACE}" apply -f "${RENDER_DIR}/service-discovery.yaml"
  
  log_info "Esperando rollout de service-discovery..."
  if ! kubectl --namespace "${K8S_NAMESPACE}" rollout status "deployment/service-discovery" --timeout="300s"; then
    log_error "El servicio service-discovery no alcanzó el estado Ready dentro del tiempo esperado."
    log_info "Estado de pods para service-discovery:"
    kubectl --namespace "${K8S_NAMESPACE}" get pods -l app="service-discovery" -o wide
    log_info "Eventos del deployment service-discovery:"
    kubectl --namespace "${K8S_NAMESPACE}" describe deployment "service-discovery" | grep -A 20 "Events:" || true
    log_info "Logs del contenedor:"
    kubectl --namespace "${K8S_NAMESPACE}" logs -l app="service-discovery" --tail=50 2>/dev/null || true
    log_error "Disponibilidad de recursos en cluster:"
    kubectl top nodes 2>/dev/null || log_warn "No hay métrica de recursos disponible"
    exit 1
  fi
  log_success "service-discovery está listo."
fi

log_info "Service Discovery listo. Desplegando servicios restantes..."

# Desplegar el resto de los servicios
for svc in "${SERVICES[@]}"; do
  if [[ "${svc}" == "service-discovery" ]]; then
    continue  # Ya fue desplegado
  fi
  if [[ -z "${SERVICE_PORTS[${svc}]+_}" ]]; then
    log_error "Servicio '${svc}' no está soportado por el pipeline."
    exit 1
  fi
  render_manifest "${svc}"
  log_info "Aplicando ${svc}..."
  kubectl --namespace "${K8S_NAMESPACE}" apply -f "${RENDER_DIR}/${svc}.yaml"
  if [[ "${SERVICE_TYPES[${svc}]:-ClusterIP}" == "LoadBalancer" ]]; then
    LB_SERVICES+=("${svc}")
  fi
done

for svc in "${SERVICES[@]}"; do
  # Saltar service-discovery que ya fue esperado
  if [[ "${svc}" == "service-discovery" ]]; then
    continue
  fi
  
  log_info "Esperando rollout de ${svc}..."
  # Timeout optimizado basado en la configuración exitosa de Minikube
  TIMEOUT="600s"
  
  # Todos los microservicios usan la misma configuración optimizada
  log_info "⏳ ${svc}: Usando timeout optimizado (${TIMEOUT})"
  
  if ! kubectl --namespace "${K8S_NAMESPACE}" rollout status "deployment/${svc}" --timeout="${TIMEOUT}"; then
    log_error "El despliegue de ${svc} no alcanzó el estado Ready dentro del tiempo esperado."
    log_info "Estado actual de pods:"
    kubectl --namespace "${K8S_NAMESPACE}" get pods -l app="${svc}" -o wide
    
    # Debugging adicional para Pending pods
    PENDING_PODS=$(kubectl --namespace "${K8S_NAMESPACE}" get pods -l app="${svc}" -o jsonpath='{.items[?(@.status.phase=="Pending")].metadata.name}')
    if [[ -n "${PENDING_PODS}" ]]; then
      for pod in ${PENDING_PODS}; do
        log_warn "Pod ${pod} está en Pending. Describiendo..."
        kubectl --namespace "${K8S_NAMESPACE}" describe pod "${pod}" | grep -A 5 "Events:" || true
      done
    fi
    
    # CAPTURA DE LOGS AUTOMÁTICA PARA DIAGNOSTICAR
    log_warn "📋 Capturando logs del servicio ${svc}..."
    RUNNING_PODS=$(kubectl --namespace "${K8S_NAMESPACE}" get pods -l app="${svc}" -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}')
    if [[ -n "${RUNNING_PODS}" ]]; then
      for pod in ${RUNNING_PODS}; do
        log_warn "Logs de ${pod}:"
        kubectl --namespace "${K8S_NAMESPACE}" logs "${pod}" --tail=50 --all-containers=true 2>/dev/null || true
        log_warn "---"
      done
    fi
    
    exit 1
  fi
  log_success "${svc} desplegado correctamente."
done

for svc in "${LB_SERVICES[@]}"; do
  log_info "Esperando IP externa para ${svc}..."
  tries=0
  max_tries=30
  external_ip=""
  while [[ ${tries} -lt ${max_tries} ]]; do
    external_ip="$(kubectl --namespace "${K8S_NAMESPACE}" get svc "${svc}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
    if [[ -n "${external_ip}" ]]; then
      log_success "${svc} disponible en ${external_ip}"
      break
    fi
    tries=$((tries + 1))
    sleep 5
  done
  if [[ -z "${external_ip}" ]]; then
    log_warn "No se obtuvo IP externa para ${svc} tras ${max_tries} intentos."
  fi
done

log_info "Resumen de despliegue en namespace ${K8S_NAMESPACE}:"
kubectl --namespace "${K8S_NAMESPACE}" get deployments
kubectl --namespace "${K8S_NAMESPACE}" get services
kubectl --namespace "${K8S_NAMESPACE}" get pods -o wide

log_success "Despliegue completado."

