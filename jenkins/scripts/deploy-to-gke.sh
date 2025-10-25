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
  [cloud-config]=9296
  [service-discovery]=8761
  [api-gateway]=8080
  [proxy-client]=8900
  [user-service]=8700
  [product-service]=8500
  [favourite-service]=8800
  [order-service]=8300
  [shipping-service]=8600
  [payment-service]=8400
)

# Configuración de tipos de servicio
declare -A SERVICE_TYPES
SERVICE_TYPES[api-gateway]="LoadBalancer"

# Rutas de health check para cada servicio (CRÍTICO: usadas por readiness probes)
declare -A SERVICE_HEALTH_PATH=(
  [service-discovery]="/actuator/health"
  [cloud-config]="/actuator/health"
  [api-gateway]="/actuator/health"
  [proxy-client]="/actuator/health"
  [user-service]="/actuator/health"
  [product-service]="/actuator/health"
  [favourite-service]="/actuator/health"
  [order-service]="/actuator/health"
  [shipping-service]="/actuator/health"
  [payment-service]="/actuator/health"
)

declare -A SERVICE_REPLICAS=(
  [api-gateway]=2
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

for dep in cloud-config service-discovery; do
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

# Limpieza optimizada: eliminar deployments en paralelo sin bloquear
log_info "Limpiando deployments viejos (en paralelo)..."
for svc in "${SERVICES[@]}"; do
  # Eliminar en background sin esperar (--cascade=background es más rápido)
  if kubectl --namespace "${K8S_NAMESPACE}" get deployment "${svc}" &>/dev/null 2>&1; then
    kubectl --namespace "${K8S_NAMESPACE}" delete deployment "${svc}" --cascade=background --ignore-not-found=true 2>/dev/null &
  fi
done

# Esperar a que todos los deletes terminen en paralelo
wait

# Espera rápida para que los pods se terminen
log_info "Esperando a que los pods antiguos se terminen..."
for svc in "${SERVICES[@]}"; do
  kubectl --namespace "${K8S_NAMESPACE}" delete pods -l app="${svc}" --grace-period=5 --ignore-not-found=true 2>/dev/null || true &
done
wait

log_success "Limpieza de deployments completada."

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
  # Ultra-low CPU requests debido a limitaciones del cluster
  local cpu_request="15m"
  local cpu_limit="100m"
  local mem_request="96Mi"
  local mem_limit="192Mi"

  # Asignar ligeramente más recursos a servicios críticos (pero todavía muy bajos)
  if [[ "${svc}" == "cloud-config" || "${svc}" == "service-discovery" ]]; then
    cpu_request="25m"
    cpu_limit="150m"
    mem_request="128Mi"
    mem_limit="256Mi"
  fi

  # Probes específicas: dar mucho más tiempo para inicialización de beans Spring
  # Los servicios tardan 90-120 segundos en completar la inicialización del contexto
  # inicialDelaySeconds: esperar antes de la primera prueba (debe ser >= startup time)
  # failureThreshold: número de fallos antes de marcar como NOT Ready
  if [[ "${svc}" == "cloud-config" ]]; then
    # Cloud-config es crítico y tarda más
    READINESS_INITIAL_DELAY="150"
    READINESS_FAILURE_THRESHOLD="100"
    LIVENESS_INITIAL_DELAY="300"
    LIVENESS_FAILURE_THRESHOLD="15"
  elif [[ "${svc}" == "service-discovery" ]]; then
    # Service discovery también es crítico y tarda más por Jersey/Eureka UI
    READINESS_INITIAL_DELAY="180"
    READINESS_FAILURE_THRESHOLD="80"
    LIVENESS_INITIAL_DELAY="300"
    LIVENESS_FAILURE_THRESHOLD="10"
  else
    # Otros servicios: dar 120+ segundos para que terminen la inicialización
    READINESS_INITIAL_DELAY="130"
    READINESS_FAILURE_THRESHOLD="60"
    LIVENESS_INITIAL_DELAY="300"
    LIVENESS_FAILURE_THRESHOLD="10"
  fi

  if [[ "${svc}" == "cloud-config" ]]; then
    local active_profiles="native"
    if [[ -n "${K8S_ENVIRONMENT}" && "${K8S_ENVIRONMENT}" != "native" ]]; then
      active_profiles="native,${K8S_ENVIRONMENT}"
    fi
    extra_env_block="            - name: SPRING_PROFILES_ACTIVE
              value: \"${active_profiles}\"
            - name: SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS
              value: \"classpath:/configs\""
  fi

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
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: ${port}
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
              port: http
            initialDelaySeconds: ${READINESS_INITIAL_DELAY:-90}
            periodSeconds: 5
            failureThreshold: ${READINESS_FAILURE_THRESHOLD:-50}
            timeoutSeconds: 3
            successThreshold: 1
          livenessProbe:
            httpGet:
              path: ${health_path}
              port: http
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

# Primera fase: desplegar service-discovery PRIMERO (cloud-config depende de esto)
if [[ -n "${SEEN[service-discovery]:-}" ]]; then
  if [[ -z "${SERVICE_PORTS[service-discovery]+_}" ]]; then
    log_error "Servicio 'service-discovery' no está soportado por el pipeline."
    exit 1
  fi
  render_manifest "service-discovery"
  log_info "Aplicando servicio crítico (PRIMERO): service-discovery..."
  kubectl --namespace "${K8S_NAMESPACE}" apply -f "${RENDER_DIR}/service-discovery.yaml"
  
  log_info "Esperando rollout de service-discovery (dependencia crítica)..."
  # Timeout aumentado para permitir que el probe inicial tenga suficiente tiempo (180s initial + margin)
  if ! kubectl --namespace "${K8S_NAMESPACE}" rollout status "deployment/service-discovery" --timeout="600s"; then
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

# Segunda fase (después de service-discovery): desplegar cloud-config
  if [[ -n "${SEEN[cloud-config]:-}" ]]; then
    if [[ -z "${SERVICE_PORTS[cloud-config]+_}" ]]; then
      log_error "Servicio 'cloud-config' no está soportado por el pipeline."
      exit 1
    fi
    render_manifest "cloud-config"
    log_info "Aplicando servicio crítico (SEGUNDO): cloud-config (después de service-discovery)..."
    kubectl --namespace "${K8S_NAMESPACE}" apply -f "${RENDER_DIR}/cloud-config.yaml"
    
    log_info "Esperando rollout de cloud-config..."
    # Timeout aumentado para permitir que el probe inicial tenga suficiente tiempo (150s initial + margin)
    if ! kubectl --namespace "${K8S_NAMESPACE}" rollout status "deployment/cloud-config" --timeout="600s"; then
      log_error "El servicio cloud-config no alcanzó el estado Ready dentro del tiempo esperado."
      log_info "Estado de pods para cloud-config:"
      kubectl --namespace "${K8S_NAMESPACE}" get pods -l app="cloud-config" -o wide
      log_info "Eventos del deployment cloud-config:"
      kubectl --namespace "${K8S_NAMESPACE}" describe deployment "cloud-config" | grep -A 20 "Events:" || true
      log_info "Logs del contenedor:"
      kubectl --namespace "${K8S_NAMESPACE}" logs -l app="cloud-config" --tail=50 2>/dev/null || true
      log_error "Disponibilidad de recursos en cluster:"
      kubectl top nodes 2>/dev/null || log_warn "No hay métrica de recursos disponible"
      exit 1
    fi
    
    # VERIFICACIÓN ACTIVA: cloud-config puede reportar healthy pero no estar listo para recibir requests
    # cloud-config necesita más tiempo para abrir el puerto 9296 internamente
    log_info "⏳ Esperando que cloud-config esté disponible en puerto 9296..."
    
    # Máximo 5 minutos intentando verificar disponibilidad
    MAX_WAIT_TIME=300  # 5 minutos
    ELAPSED=0
    RETRY_INTERVAL=5
    CLOUD_CONFIG_POD=""
    
    # Obtener el nombre del pod de cloud-config
    while [[ -z "${CLOUD_CONFIG_POD}" && ${ELAPSED} -lt 60 ]]; do
      CLOUD_CONFIG_POD=$(kubectl --namespace "${K8S_NAMESPACE}" get pod -l app="cloud-config" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
      if [[ -z "${CLOUD_CONFIG_POD}" ]]; then
        sleep ${RETRY_INTERVAL}
        ELAPSED=$((ELAPSED + RETRY_INTERVAL))
      fi
    done
    
    if [[ -z "${CLOUD_CONFIG_POD}" ]]; then
      log_warn "No se encontró pod de cloud-config, continuando con espera fija..."
      sleep 120
    else
      # Verificar que ConfigServer esté completamente listo para servir requests
      # No solo que /actuator/health responda, sino que el ConfigServer endpoint específico esté listo
      log_info "Verificando ConfigServer en pod: ${CLOUD_CONFIG_POD}..."
      ELAPSED=0
      VERIFIED=false
      
      while [[ ${ELAPSED} -lt ${MAX_WAIT_TIME} ]]; do
        # Test de conectividad al ConfigServer endpoint específico que usan los clientes
        # Intentar obtener config para service-discovery como prueba
        if kubectl --namespace "${K8S_NAMESPACE}" exec "${CLOUD_CONFIG_POD}" -- curl -sf http://localhost:9296/service-discovery/dev > /dev/null 2>&1; then
          log_success "✅ ConfigServer de cloud-config verificado y respondiendo en puerto 9296."
          VERIFIED=true
          break
        else
          if [[ $((ELAPSED % 30)) -eq 0 ]]; then
            log_info "⏳ Esperando ConfigServer listo... (${ELAPSED}s / ${MAX_WAIT_TIME}s)"
          fi
          sleep ${RETRY_INTERVAL}
          ELAPSED=$((ELAPSED + RETRY_INTERVAL))
        fi
      done
      
      if [[ "${VERIFIED}" != "true" ]]; then
        log_warn "⚠️  No se pudo verificar puerto 9296 después de ${MAX_WAIT_TIME}s, continuando de todas formas..."
        sleep 60  # Espera mínima antes de continuar
      else
        # Si la verificación fue exitosa, esperar tiempo adicional para estabilización
        log_info "⏳ Esperando 90s adicionales para que ConfigServer se estabilice completamente..."
        sleep 90
      fi
    fi
    
    log_success "cloud-config verificación completada."
  fi

log_info "Servicios críticos listos. Desplegando servicios restantes..."
# Espera adicional para asegurar que cloud-config esté 100% estable antes de desplegar otros servicios
log_info "⏳ Esperando 30s adicionales para estabilización del cluster..."
sleep 30

# NOTE: Ya NO necesitamos verificar el puerto 9296 porque:
# 1. Los servicios usan fail-fast: false (opcional config-server)
# 2. Esperamos suficiente tiempo (120s) para que cloud-config esté listo
# 3. Si cloud-config falla, los servicios usan configuración embebida

# Segunda fase: desplegar el resto de los servicios
for svc in "${SERVICES[@]}"; do
  if [[ "${svc}" == "cloud-config" || "${svc}" == "service-discovery" ]]; then
    continue  # Ya fueron desplegados
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
  # Saltar servicios críticos que ya fueron esperados en fase 1
  if [[ "${svc}" == "cloud-config" || "${svc}" == "service-discovery" ]]; then
    continue
  fi
  
  log_info "Esperando rollout de ${svc}..."
  # Timeout ajustado para permitir que los probes tengan suficiente tiempo (130s initial + margin)
  TIMEOUT="480s"
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
