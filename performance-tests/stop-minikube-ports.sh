#!/bin/bash

# Script to stop port-forwarding for Minikube services

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

# Function to kill port-forward processes
stop_port_forwards() {
  log_info "Deteniendo port-forwards de Minikube..."
  
  local stopped=0
  
  # Kill processes using PID file
  if [ -f /tmp/minikube-port-forwards.pid ]; then
    while read pid; do
      if ps -p "$pid" > /dev/null 2>&1; then
        kill -9 "$pid" 2>/dev/null || true
        stopped=$((stopped + 1))
      fi
    done < /tmp/minikube-port-forwards.pid
    rm -f /tmp/minikube-port-forwards.pid
  fi
  
  # Also kill any remaining kubectl port-forward processes
  local pids=$(pgrep -f "kubectl port-forward.*ecommerce" || true)
  if [ -n "$pids" ]; then
    echo "$pids" | while read pid; do
      if [ -n "$pid" ]; then
        kill -9 "$pid" 2>/dev/null || true
        stopped=$((stopped + 1))
      fi
    done
  fi
  
  if [ $stopped -gt 0 ]; then
    log_success "Detenidos $stopped port-forward(s)"
  else
    log_info "No se encontraron port-forwards activos"
  fi
  
  # Kill processes on specific ports
  local ports=(8081 8082 8083 8084 8085 8086)
  for port in "${ports[@]}"; do
    local pid=$(lsof -ti:$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
      kill -9 "$pid" 2>/dev/null || true
      log_info "Proceso en puerto $port detenido"
    fi
  done
}

main() {
  stop_port_forwards
  log_success "Port-forwards detenidos"
}

main "$@"

