#!/bin/bash

# Script para ejecutar pruebas de rendimiento contra servicios en GKE
# Uso: ./run-performance-gke.sh <namespace> <gcp-project-id> <cluster-name> <zone> <services-to-test> <users> <spawn-rate> <duration>

set -e

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Parámetros
NAMESPACE=${1:-staging}
GCP_PROJECT_ID=${2}
CLUSTER_NAME=${3}
ZONE=${4}
SERVICES_TO_TEST=${5:-""}  # Comma-separated list, empty means all services
PERF_TEST_USERS=${6:-20}
PERF_TEST_SPAWN_RATE=${7:-2}
PERF_TEST_DURATION=${8:-5m}

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}⚡ Ejecutando Pruebas de Rendimiento contra GKE${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BLUE}📦 Namespace: ${NAMESPACE}${NC}"
echo -e "${BLUE}👥 Usuarios: ${PERF_TEST_USERS}${NC}"
echo -e "${BLUE}🚀 Spawn Rate: ${PERF_TEST_SPAWN_RATE} usuarios/segundo${NC}"
echo -e "${BLUE}⏱️  Duración: ${PERF_TEST_DURATION}${NC}"
if [ -n "$SERVICES_TO_TEST" ]; then
  echo -e "${BLUE}📋 Servicios: ${SERVICES_TO_TEST}${NC}"
else
  echo -e "${BLUE}📋 Servicios: Todos los servicios disponibles${NC}"
fi
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Configure PATH for gcloud and kubectl
export PATH="/usr/local/bin:/usr/bin:/bin:/opt/google-cloud-sdk/google-cloud-sdk/bin:/opt/google-cloud-sdk/bin:$PATH"

# Verify gcloud is available
if ! command -v gcloud > /dev/null 2>&1; then
  echo -e "${RED}❌ Error: gcloud no encontrado${NC}"
  exit 1
fi

# Verify kubectl is available
if ! command -v kubectl > /dev/null 2>&1; then
  echo -e "${RED}❌ Error: kubectl no encontrado${NC}"
  exit 1
fi

# Verify Python3 is available
if ! command -v python3 > /dev/null 2>&1; then
  echo -e "${RED}❌ Error: python3 no encontrado${NC}"
  exit 1
fi

# Verify pip3 is available
if ! command -v pip3 > /dev/null 2>&1; then
  echo -e "${RED}❌ Error: pip3 no encontrado${NC}"
  exit 1
fi

# Authenticate with GCP and configure kubectl
echo -e "${BLUE}🔐 Autenticando con Google Cloud...${NC}"
if [ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]; then
  gcloud auth activate-service-account --key-file="${GOOGLE_APPLICATION_CREDENTIALS}"
else
  echo -e "${YELLOW}⚠️  GOOGLE_APPLICATION_CREDENTIALS no está definido. Usando credenciales por defecto.${NC}"
fi

gcloud config set project "${GCP_PROJECT_ID}"
gcloud container clusters get-credentials "${CLUSTER_NAME}" \
  --zone "${ZONE}" \
  --project "${GCP_PROJECT_ID}"

# Verify namespace exists
if ! kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1; then
  echo -e "${RED}❌ Namespace '${NAMESPACE}' no existe${NC}"
  exit 1
fi

# Navigate to performance-tests directory
PERFORMANCE_TESTS_DIR="${WORKSPACE:-$(pwd)}/performance-tests"

if [ ! -d "$PERFORMANCE_TESTS_DIR" ]; then
  echo -e "${RED}❌ Directorio de pruebas de rendimiento no encontrado: ${PERFORMANCE_TESTS_DIR}${NC}"
  exit 1
fi

cd "$PERFORMANCE_TESTS_DIR"

# Install Python dependencies if needed
if [ ! -d "venv" ]; then
  echo -e "${BLUE}📦 Creando entorno virtual de Python...${NC}"
  python3 -m venv venv
fi

echo -e "${BLUE}📦 Instalando dependencias de Python...${NC}"
source venv/bin/activate
pip install --quiet --upgrade pip
pip install --quiet -r requirements.txt

# Install Locust if not already installed
if ! python -c "import locust" 2>/dev/null; then
  echo -e "${BLUE}📦 Instalando Locust...${NC}"
  pip install --quiet locust==2.17.0
fi

# Run performance tests
echo ""
echo -e "${BLUE}🚀 Ejecutando pruebas de rendimiento...${NC}"
export PERFORMANCE_TESTS_DIR="$(pwd)"
export K8S_NAMESPACE="${NAMESPACE}"
export PERF_TEST_USERS="${PERF_TEST_USERS}"
export PERF_TEST_SPAWN_RATE="${PERF_TEST_SPAWN_RATE}"
export PERF_TEST_DURATION="${PERF_TEST_DURATION}"
export SERVICES_TO_TEST="${SERVICES_TO_TEST}"

./run-performance-gke.sh

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Pruebas de rendimiento completadas${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Archive performance test results
if [ -d "results" ]; then
  echo -e "${BLUE}📦 Archivando resultados de pruebas de rendimiento...${NC}"
  RESULTS_DIR="${WORKSPACE:-$(pwd)/..}/performance-results"
  mkdir -p "${RESULTS_DIR}"
  cp -r results/* "${RESULTS_DIR}/" 2>/dev/null || true
  
  echo -e "${BLUE}📊 Resultados disponibles en: ${RESULTS_DIR}${NC}"
  ls -lh "${RESULTS_DIR}/" || true
fi

echo ""
echo -e "${GREEN}✅ Script de pruebas de rendimiento completado exitosamente${NC}"

