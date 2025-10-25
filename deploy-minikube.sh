#!/usr/bin/env bash
# Script definitivo para desplegar microservicios en Minikube
# Basado en pruebas y fixes validados
#
# Uso:
#   ./deploy-minikube.sh [servicios...]
#
# Ejemplos:
#   ./deploy-minikube.sh                              # Despliega servicios críticos
#   ./deploy-minikube.sh all                          # Despliega todos los servicios
#   ./deploy-minikube.sh api-gateway user-service     # Despliega servicios específicos

set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# CONFIGURACIÓN Y COLORES
# ═══════════════════════════════════════════════════════════════════════════

BLUE="\033[0;34m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
CYAN="\033[0;36m"
BOLD="\033[1m"
NC="\033[0m"

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step() { echo -e "${CYAN}${BOLD}▶${NC} $*"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${SCRIPT_DIR}"

# ═══════════════════════════════════════════════════════════════════════════
# CONFIGURACIÓN DE SERVICIOS
# ═══════════════════════════════════════════════════════════════════════════

# Puertos de servicios
declare -A SERVICE_PORTS=(
  [service-discovery]=8761
  [cloud-config]=9296
  [api-gateway]=8080
  [proxy-client]=8900
  [user-service]=8700
  [product-service]=8500
  [favourite-service]=8800
  [order-service]=8300
  [shipping-service]=8600
  [payment-service]=8400
)

# Servicios críticos (siempre se despliegan primero)
CRITICAL_SERVICES=("service-discovery" "cloud-config")

# Todos los servicios disponibles
ALL_SERVICES=(
  "service-discovery"
  "cloud-config"
  "api-gateway"
  "proxy-client"
  "user-service"
  "product-service"
  "favourite-service"
  "order-service"
  "shipping-service"
  "payment-service"
)

# ═══════════════════════════════════════════════════════════════════════════
# FUNCIONES DE AYUDA
# ═══════════════════════════════════════════════════════════════════════════

show_banner() {
  echo ""
  echo -e "${CYAN}╔═══════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║${NC}    ${BOLD}Despliegue de Microservicios en Minikube${NC}             ${CYAN}║${NC}"
  echo -e "${CYAN}║${NC}    Versión con todos los fixes validados                 ${CYAN}║${NC}"
  echo -e "${CYAN}╚═══════════════════════════════════════════════════════════════╝${NC}"
  echo ""
}

show_usage() {
  cat << EOF
${BOLD}Uso:${NC}
  $0 [opciones] [servicios...]

${BOLD}Opciones:${NC}
  -h, --help          Mostrar esta ayuda
  -c, --clean         Limpiar Minikube existente antes de iniciar
  -k, --keep          Mantener Minikube corriendo al finalizar (default: stop)
  
${BOLD}Servicios:${NC}
  all                 Desplegar todos los servicios
  critical            Solo servicios críticos (service-discovery, cloud-config, api-gateway)
  [nombres...]        Lista de servicios específicos

${BOLD}Servicios disponibles:${NC}
  - service-discovery (Eureka) - CRÍTICO
  - cloud-config (Config Server) - CRÍTICO
  - api-gateway (Gateway) 
  - proxy-client
  - user-service
  - product-service
  - favourite-service
  - order-service
  - shipping-service
  - payment-service

${BOLD}Ejemplos:${NC}
  $0                                    # Servicios críticos + api-gateway
  $0 all                                # Todos los servicios
  $0 critical                           # Solo críticos
  $0 api-gateway user-service           # Servicios específicos
  $0 --clean all                        # Limpiar y desplegar todos

${BOLD}Requisitos:${NC}
  - Minikube instalado
  - Docker corriendo
  - kubectl instalado
  - Mínimo 3GB RAM en Docker (recomendado: 8GB)

EOF
}

detect_docker_memory() {
  local mem_mb=0
  
  if command -v docker &> /dev/null; then
    # Intentar obtener memoria total
    local mem_info=$(docker info 2>/dev/null | grep "Total Memory")
    if [[ -n "${mem_info}" ]]; then
      # Extraer número (puede estar en GiB)
      local mem_value=$(echo "${mem_info}" | awk '{print $3}')
      mem_mb=$(echo "${mem_value}" | awk '{printf "%.0f", $1 * 1024}')
    fi
  fi
  
  # Default si no se detectó
  if [[ ${mem_mb} -eq 0 || ${mem_mb} -lt 1000 ]]; then
    mem_mb=4096
  fi
  
  echo "${mem_mb}"
}

configure_minikube_resources() {
  local docker_mem_mb=$1
  
  if [[ ${docker_mem_mb} -lt 4096 ]]; then
    MINIKUBE_MEM="3000"
    MINIKUBE_CPUS="2"
    log_warn "Docker tiene ${docker_mem_mb}MB. Usando configuración mínima."
    log_info "Para mejor rendimiento, aumenta RAM de Docker a 8GB+"
  elif [[ ${docker_mem_mb} -lt 8192 ]]; then
    MINIKUBE_MEM="4096"
    MINIKUBE_CPUS="2"
    log_info "Configuración media: ${MINIKUBE_MEM}MB RAM, ${MINIKUBE_CPUS} CPUs"
  else
    MINIKUBE_MEM="8192"
    MINIKUBE_CPUS="4"
    log_info "Configuración completa: ${MINIKUBE_MEM}MB RAM, ${MINIKUBE_CPUS} CPUs"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════
# FUNCIÓN PARA GENERAR MANIFIESTOS
# ═══════════════════════════════════════════════════════════════════════════

generate_manifest() {
  local service=$1
  local port=$2
  local manifest_file=$3
  
  # Configurar probes según el servicio
  local readiness_initial=80
  local readiness_failures=50
  local liveness_initial=120
  local liveness_failures=5
  
  case "${service}" in
    "service-discovery")
      readiness_initial=80
      readiness_failures=50
      ;;
    "cloud-config")
      readiness_initial=80
      readiness_failures=50
      ;;
    "api-gateway")
      readiness_initial=100
      readiness_failures=60
      liveness_initial=180
      ;;
  esac
  
  # Service type
  local service_type="ClusterIP"
  if [[ "${service}" == "api-gateway" || "${service}" == "service-discovery" ]]; then
    service_type="NodePort"
  fi
  
  # Extra env vars
  local extra_env=""
  if [[ "${service}" == "cloud-config" ]]; then
    extra_env="        - name: SPRING_PROFILES_ACTIVE
          value: \"native,dev\"
        - name: SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS
          value: \"classpath:/configs\""
  fi
  
  cat > "${manifest_file}" <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service}
  namespace: ecommerce
  labels:
    app: ${service}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${service}
  template:
    metadata:
      labels:
        app: ${service}
    spec:
      containers:
      - name: ${service}
        image: local/${service}:latest
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: ${port}
        env:
        - name: SERVER_PORT
          value: "${port}"
${extra_env}
        envFrom:
        - configMapRef:
            name: ecommerce-config
        - secretRef:
            name: ecommerce-secrets
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: ${readiness_initial}
          periodSeconds: 5
          failureThreshold: ${readiness_failures}
          timeoutSeconds: 3
          successThreshold: 1
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: ${liveness_initial}
          periodSeconds: 30
          failureThreshold: ${liveness_failures}
          timeoutSeconds: 3
        resources:
          requests:
            cpu: 50m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: ${service}
  namespace: ecommerce
  labels:
    app: ${service}
spec:
  selector:
    app: ${service}
  type: ${service_type}
  ports:
  - name: http
    port: ${port}
    targetPort: http
EOF
}

# ═══════════════════════════════════════════════════════════════════════════
# FUNCIÓN PARA BUILD DE IMÁGENES
# ═══════════════════════════════════════════════════════════════════════════

build_images() {
  local services=("$@")
  
  log_step "Construyendo imágenes Docker..."
  
  cd "${REPO_ROOT}"
  eval $(minikube docker-env --profile=ecommerce)
  
  for service in "${services[@]}"; do
    if [[ ! -d "${service}" ]]; then
      log_warn "Omitiendo ${service} (directorio no existe)"
      continue
    fi
    
    log_info "🔨 Construyendo ${service}..."
    
    if docker build -t "local/${service}:latest" \
         -f "${service}/Dockerfile" \
         --build-arg SERVICE_NAME="${service}" \
         . --quiet; then
      log_success "✅ ${service} construido"
    else
      log_error "❌ Error construyendo ${service}"
      return 1
    fi
  done
  
  log_success "Todas las imágenes construidas exitosamente"
}

# ═══════════════════════════════════════════════════════════════════════════
# FUNCIÓN PARA DESPLEGAR UN SERVICIO
# ═══════════════════════════════════════════════════════════════════════════

deploy_service() {
  local service=$1
  local timeout=${2:-300}
  
  log_info "Aplicando manifest de ${service}..."
  kubectl apply -f "/tmp/${service}.yaml"
  
  sleep 5
  
  log_info "⏳ Esperando que ${service} esté Ready (timeout: ${timeout}s)..."
  if kubectl -n ecommerce wait --for=condition=ready \
       pod -l app="${service}" --timeout="${timeout}s"; then
    log_success "✅ ${service} está Ready"
    return 0
  else
    log_error "❌ ${service} no alcanzó Ready"
    kubectl -n ecommerce get pods -l app="${service}"
    kubectl -n ecommerce logs -l app="${service}" --tail=50 || true
    return 1
  fi
}

# ═══════════════════════════════════════════════════════════════════════════
# FUNCIÓN PARA VERIFICACIONES CRÍTICAS
# ═══════════════════════════════════════════════════════════════════════════

verify_config_server() {
  log_info "🔍 Verificando ConfigServer..."
  
  local cloud_pod=$(kubectl -n ecommerce get pod -l app=cloud-config \
                    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
  
  if [[ -z "${cloud_pod}" ]]; then
    log_warn "No se encontró pod de cloud-config"
    return 1
  fi
  
  log_info "Pod: ${cloud_pod}"
  
  for i in {1..12}; do
    if kubectl -n ecommerce exec "${cloud_pod}" -- \
         curl -sf http://localhost:9296/service-discovery/dev > /dev/null 2>&1; then
      log_success "✅ ConfigServer respondiendo"
      return 0
    fi
    
    if [[ $i -eq 12 ]]; then
      log_warn "⚠️  ConfigServer no verificado después de 12 intentos"
      return 1
    fi
    
    sleep 5
  done
}

verify_dns_propagation() {
  log_info "🔍 Verificando propagación de Service DNS..."
  
  local disc_pod=$(kubectl -n ecommerce get pod -l app=service-discovery \
                   -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
  
  if [[ -z "${disc_pod}" ]]; then
    log_warn "No se encontró pod de service-discovery"
    return 1
  fi
  
  log_info "Verificando desde pod: ${disc_pod}"
  
  for i in {1..10}; do
    if kubectl -n ecommerce exec "${disc_pod}" -- \
         curl -sf -m 5 http://cloud-config:9296/actuator/health > /dev/null 2>&1; then
      log_success "✅ Service DNS funcionando"
      return 0
    fi
    
    if [[ $((i % 3)) -eq 0 ]]; then
      log_info "Intento $i/10..."
    fi
    
    sleep 5
  done
  
  log_warn "⚠️  DNS inter-pod no verificado (pero puede funcionar para api-gateway)"
  return 1
}

# ═══════════════════════════════════════════════════════════════════════════
# FUNCIÓN PRINCIPAL DE DESPLIEGUE
# ═══════════════════════════════════════════════════════════════════════════

deploy_services() {
  local services=("$@")
  
  log_step "Fase 1: Preparando entorno..."
  
  # Limpiar pods anteriores
  log_info "Limpiando pods anteriores..."
  kubectl -n ecommerce delete pods --all --grace-period=5 --force 2>/dev/null || true
  sleep 5
  
  # Generar manifiestos
  log_info "Generando manifiestos Kubernetes..."
  for service in "${services[@]}"; do
    local port="${SERVICE_PORTS[${service}]}"
    if [[ -z "${port}" ]]; then
      log_warn "Puerto no definido para ${service}, omitiendo..."
      continue
    fi
    generate_manifest "${service}" "${port}" "/tmp/${service}.yaml"
  done
  
  log_success "Manifiestos generados"
  
  # FASE 2: Desplegar service-discovery
  echo ""
  log_step "Fase 2: Desplegando service-discovery (CRÍTICO)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  deploy_service "service-discovery" 300 || return 1
  
  # FASE 3: Desplegar cloud-config
  echo ""
  log_step "Fase 3: Desplegando cloud-config (CRÍTICO)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  deploy_service "cloud-config" 300 || return 1
  
  # FASE 4: Verificaciones críticas
  echo ""
  log_step "Fase 4: Verificaciones críticas"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  verify_config_server || log_warn "ConfigServer verificación falló, pero continuando..."
  
  log_info "⏳ Esperando 60s para propagación de DNS..."
  sleep 60
  
  verify_dns_propagation || log_warn "DNS propagation check falló, pero continuando..."
  
  log_info "⏳ Esperando 30s adicionales para estabilización..."
  sleep 30
  
  # FASE 5: Desplegar servicios restantes
  echo ""
  log_step "Fase 5: Desplegando servicios restantes"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  for service in "${services[@]}"; do
    if [[ "${service}" == "service-discovery" || "${service}" == "cloud-config" ]]; then
      continue  # Ya fueron desplegados
    fi
    
    echo ""
    log_info "Desplegando ${service}..."
    
    # Timeout más largo para api-gateway
    local timeout=300
    if [[ "${service}" == "api-gateway" ]]; then
      timeout=480
      log_warn "api-gateway usa timeout extendido (8 minutos)"
    fi
    
    deploy_service "${service}" "${timeout}" || {
      log_error "Fallo al desplegar ${service}"
      return 1
    }
  done
  
  # RESUMEN FINAL
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  log_success "✅ DESPLIEGUE COMPLETADO"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  echo ""
  log_info "📊 Estado de pods:"
  kubectl -n ecommerce get pods -o wide
  
  echo ""
  log_info "📊 Servicios:"
  kubectl -n ecommerce get svc
  
  echo ""
  log_info "🌐 Para acceder a los servicios:"
  echo "  minikube service api-gateway -n ecommerce --profile=ecommerce"
  echo "  minikube service service-discovery -n ecommerce --profile=ecommerce"
  
  echo ""
  log_info "📋 Comandos útiles:"
  echo "  kubectl -n ecommerce get pods -w"
  echo "  kubectl -n ecommerce logs -f <pod-name>"
  echo "  minikube dashboard --profile=ecommerce"
}

# ═══════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════

main() {
  show_banner
  
  # Parsear argumentos
  local clean_minikube=false
  local keep_running=false
  local selected_services=()
  
  while [[ $# -gt 0 ]]; do
    case $1 in
      -h|--help)
        show_usage
        exit 0
        ;;
      -c|--clean)
        clean_minikube=true
        shift
        ;;
      -k|--keep)
        keep_running=true
        shift
        ;;
      all)
        selected_services=("${ALL_SERVICES[@]}")
        shift
        ;;
      critical)
        selected_services=("service-discovery" "cloud-config" "api-gateway")
        shift
        ;;
      *)
        selected_services+=("$1")
        shift
        ;;
    esac
  done
  
  # Si no se especificaron servicios, usar critical
  if [[ ${#selected_services[@]} -eq 0 ]]; then
    log_info "No se especificaron servicios, usando: critical"
    selected_services=("service-discovery" "cloud-config" "api-gateway")
  fi
  
  # Validar servicios
  for service in "${selected_services[@]}"; do
    if [[ -z "${SERVICE_PORTS[${service}]:-}" ]]; then
      log_error "Servicio desconocido: ${service}"
      echo ""
      log_info "Servicios disponibles:"
      for s in "${ALL_SERVICES[@]}"; do
        echo "  - ${s}"
      done
      exit 1
    fi
  done
  
  log_info "Servicios a desplegar: ${selected_services[*]}"
  
  # Detectar memoria y configurar Minikube
  local docker_mem_mb=$(detect_docker_memory)
  configure_minikube_resources "${docker_mem_mb}"
  
  # Limpiar Minikube si se solicitó
  if [[ "${clean_minikube}" == "true" ]]; then
    log_info "Limpiando Minikube existente..."
    minikube delete --profile=ecommerce 2>/dev/null || true
  fi
  
  # Iniciar Minikube
  log_step "Iniciando Minikube..."
  if ! minikube status --profile=ecommerce &>/dev/null; then
    minikube start \
      --profile=ecommerce \
      --driver=docker \
      --memory="${MINIKUBE_MEM}" \
      --cpus="${MINIKUBE_CPUS}" \
      --kubernetes-version=v1.28.0 \
      --disk-size=20g
  else
    log_info "Minikube ya está corriendo, usando perfil existente"
    minikube start --profile=ecommerce
  fi
  
  kubectl config use-context ecommerce
  
  # Crear namespace y recursos base
  log_info "Configurando namespace y recursos base..."
  kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -
  
  # ConfigMap básico
  kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: ecommerce-config
  namespace: ecommerce
data:
  SPRING_PROFILES_ACTIVE: "dev"
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: "http://service-discovery:8761/eureka/"
  SPRING_CONFIG_IMPORT: "optional:configserver:http://cloud-config:9296/"
  LOGGING_LEVEL_ROOT: "INFO"
EOF
  
  # Secret básico
  kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ecommerce-secrets
  namespace: ecommerce
type: Opaque
stringData:
  DB_PASSWORD: "postgres"
  JWT_SECRET: "test-secret-key-for-minikube"
EOF
  
  log_success "Recursos base configurados"
  
  # Build de imágenes
  build_images "${selected_services[@]}" || exit 1
  
  # Desplegar servicios
  deploy_services "${selected_services[@]}" || exit 1
  
  # Finalización
  if [[ "${keep_running}" == "false" ]]; then
    echo ""
    log_info "Para detener Minikube:"
    echo "  minikube stop --profile=ecommerce"
    echo "  minikube delete --profile=ecommerce"
  fi
  
  echo ""
  log_success "🎉 ¡Despliegue completado exitosamente!"
}

# Ejecutar main
main "$@"

