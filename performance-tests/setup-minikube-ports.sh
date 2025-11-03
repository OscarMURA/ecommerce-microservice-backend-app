#!/bin/bash

# Script to set up port-forwarding for Minikube services
# This allows performance tests to access services running in Minikube

set -euo pipefail

# Colors for output
YELLOW="\033[1;33m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
RED="\033[0;31m"
NC="\033[0m"

log_info() {
  echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
  echo -e "${GREEN}[OK]${NC} $*"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $*"
}

# Check if Minikube is running
if ! minikube status >/dev/null 2>&1; then
  log_error "Minikube no está ejecutándose"
  log_info "Por favor ejecuta: minikube start"
  exit 1
fi

# Check if namespace exists
if ! kubectl get namespace ecommerce >/dev/null 2>&1; then
  log_error "Namespace 'ecommerce' no existe en Minikube"
  log_info "Por favor ejecuta el script de despliegue: ./minikube-deployment/test-minikube.sh"
  exit 1
fi

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
  log_error "kubectl no está instalado o no está en el PATH"
  exit 1
fi

# Function to check if a port is already in use
check_port_available() {
  local port=$1
  if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
    return 1  # Port is in use
  else
    return 0  # Port is available
  fi
}

# Function to kill existing port-forward processes
kill_existing_port_forwards() {
  log_info "Verificando port-forwards existentes..."
  
  # Get all port-forward PIDs
  local pids=$(pgrep -f "kubectl port-forward.*ecommerce" || true)
  
  if [ -n "$pids" ]; then
    log_warn "Encontrados port-forwards existentes. Matándolos..."
    echo "$pids" | xargs kill -9 2>/dev/null || true
    sleep 1
  fi
}

# Function to start port-forward for a service
start_port_forward() {
  local service=$1
  local port=$2
  local namespace="ecommerce"
  
  # Check if service exists
  if ! kubectl get service "$service" -n "$namespace" >/dev/null 2>&1; then
    log_warn "Servicio '$service' no encontrado en namespace '$namespace'. Saltando..."
    return 1
  fi
  
  # Check if port is available
  if ! check_port_available "$port"; then
    log_warn "Puerto $port ya está en uso. Intentando matar proceso existente..."
    lsof -ti:$port | xargs kill -9 2>/dev/null || true
    sleep 1
  fi
  
  # Start port-forward in background
  log_info "Iniciando port-forward para $service (localhost:$port)..."
  kubectl port-forward -n "$namespace" service/"$service" "$port:$port" >/dev/null 2>&1 &
  local pid=$!
  
  # Wait a moment to check if it started successfully
  sleep 1
  if ps -p $pid > /dev/null 2>&1; then
    log_success "Port-forward iniciado para $service (PID: $pid)"
    echo "$pid" >> /tmp/minikube-port-forwards.pid
    return 0
  else
    log_error "No se pudo iniciar port-forward para $service"
    return 1
  fi
}

# Function to verify port-forwards are working
verify_port_forwards() {
  log_info "Verificando que los port-forwards estén funcionando..."
  
  local services=(
    "order-service:8081"
    "payment-service:8082"
    "product-service:8083"
    "shipping-service:8084"
    "user-service:8085"
    "favourite-service:8086"
  )
  
  local failed=0
  
  for service_port in "${services[@]}"; do
    IFS=':' read -r service port <<< "$service_port"
    
    # Try to connect to the service with correct path: /{SERVICE_NAME}/actuator/health
    local health_url="http://localhost:$port/$service/actuator/health"
    if curl -s -f --max-time 3 "$health_url" >/dev/null 2>&1; then
      log_success "$service está accesible en localhost:$port"
    else
      log_warn "$service no responde en localhost:$port (puede que aún esté iniciando)"
      failed=$((failed + 1))
    fi
  done
  
  if [ $failed -eq 0 ]; then
    log_success "Todos los servicios están accesibles"
    return 0
  else
    log_warn "$failed servicio(s) no respondieron. Esto es normal si los servicios aún están iniciando."
    log_info "Los port-forwards están activos. Puedes verificar manualmente más tarde."
    return 0  # No fallar, los port-forwards están bien configurados
  fi
}

# Main function
main() {
  log_info "Configurando port-forwards para servicios de Minikube..."
  
  # Clean up any existing port-forwards
  kill_existing_port_forwards
  
  # Create PID file
  > /tmp/minikube-port-forwards.pid
  
  # Start port-forwards for each service
  # Order: order-service, payment-service, product-service, shipping-service, user-service, favourite-service
  local services=(
    "order-service:8081"
    "payment-service:8082"
    "product-service:8083"
    "shipping-service:8084"
    "user-service:8085"
    "favourite-service:8086"
  )
  
  local started=0
  for service_port in "${services[@]}"; do
    IFS=':' read -r service port <<< "$service_port"
    if start_port_forward "$service" "$port"; then
      started=$((started + 1))
    fi
    sleep 0.5  # Small delay between starts
  done
  
  log_info "Esperando 3 segundos para que los port-forwards se estabilicen..."
  sleep 3
  
  # Verify (pero no fallar si los servicios aún no están listos)
  verify_port_forwards
  
  log_success "Port-forwards configurados. $started de ${#services[@]} servicios configurados."
  log_info ""
  log_info "Los servicios ahora están accesibles en:"
  for service_port in "${services[@]}"; do
    IFS=':' read -r service port <<< "$service_port"
    log_info "  - $service: http://localhost:$port/$service/actuator/health"
  done
  log_info ""
  log_info "Para detener los port-forwards, ejecuta: ./stop-minikube-ports.sh"
  log_info "O manualmente: kill \$(cat /tmp/minikube-port-forwards.pid)"
}

# Handle script termination (solo limpiar si hay error crítico)
cleanup() {
  # No limpiar automáticamente en EXIT normal, solo en errores
  # Los port-forwards deben mantenerse activos
  if [ $? -ne 0 ]; then
    if [ -f /tmp/minikube-port-forwards.pid ]; then
      log_info "Limpiando port-forwards debido a error..."
      cat /tmp/minikube-port-forwards.pid 2>/dev/null | xargs kill -9 2>/dev/null || true
      rm -f /tmp/minikube-port-forwards.pid
    fi
  fi
}

# Solo limpiar en señales de interrupción
trap cleanup INT TERM

# Run main function
main "$@"

