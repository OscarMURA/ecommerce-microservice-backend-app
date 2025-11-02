#!/bin/bash

# Script para ejecutar pruebas E2E contra un cluster Kubernetes (Minikube)
# Uso: ./run-e2e-cluster.sh [port-forward|kubectl-exec|nodeport]

set -e

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuración por defecto
NAMESPACE=${KUBERNETES_NAMESPACE:-ecommerce}
MODE=${1:-port-forward}
TIMEOUT=${E2E_TIMEOUT:-300}

echo -e "${BLUE}🚀 Ejecutando pruebas E2E contra cluster Kubernetes${NC}"
echo -e "${BLUE}Namespace: ${NAMESPACE}${NC}"
echo -e "${BLUE}Modo: ${MODE}${NC}"
echo ""

# Verificar que kubectl está disponible
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl no está instalado${NC}"
    exit 1
fi

# Verificar que el namespace existe
if ! kubectl get namespace "${NAMESPACE}" &> /dev/null; then
    echo -e "${RED}❌ Namespace '${NAMESPACE}' no existe${NC}"
    echo -e "${YELLOW}💡 Asegúrate de que el cluster está desplegado${NC}"
    exit 1
fi

# Función para verificar que un servicio está corriendo
check_service() {
    local service=$1
    if ! kubectl get deployment -n "${NAMESPACE}" "${service}" &> /dev/null; then
        echo -e "${RED}❌ Deployment '${service}' no encontrado en namespace '${NAMESPACE}'${NC}"
        return 1
    fi
    
    local ready=$(kubectl get deployment -n "${NAMESPACE}" "${service}" -o jsonpath='{.status.readyReplicas}')
    if [ "${ready}" != "1" ]; then
        echo -e "${YELLOW}⚠️  Servicio '${service}' no está listo (ready: ${ready:-0}/1)${NC}"
        return 1
    fi
    
    return 0
}

# Verificar servicios críticos
echo -e "${BLUE}🔍 Verificando servicios en el cluster...${NC}"
SERVICES=("user-service" "product-service" "order-service" "payment-service" "shipping-service" "favourite-service")

for service in "${SERVICES[@]}"; do
    if check_service "${service}"; then
        echo -e "${GREEN}✅ ${service} está listo${NC}"
    else
        echo -e "${RED}❌ ${service} no está listo${NC}"
        exit 1
    fi
done

echo ""

# Función para ejecutar pruebas con port-forward
run_with_port_forward() {
    echo -e "${BLUE}📡 Configurando port-forward para servicios...${NC}"
    
    # Mapeo de servicios a puertos locales
    declare -A SERVICE_PORTS=(
        [user-service]=8085
        [product-service]=8083
        [order-service]=8081
        [payment-service]=8082
        [shipping-service]=8084
        [favourite-service]=8086
    )
    
    # Iniciar port-forwards en background
    PIDS=()
    for service in "${!SERVICE_PORTS[@]}"; do
        local local_port=${SERVICE_PORTS[$service]}
        local service_port=${local_port}
        
        echo -e "${BLUE}  → Port-forward ${service}:${local_port}...${NC}"
        kubectl port-forward -n "${NAMESPACE}" "deployment/${service}" "${local_port}:${service_port}" > /dev/null 2>&1 &
        PIDS+=($!)
        
        # Esperar un poco para que el port-forward se establezca
        sleep 2
    done
    
    # Función de limpieza
    cleanup() {
        echo -e "${YELLOW}🧹 Limpiando port-forwards...${NC}"
        for pid in "${PIDS[@]}"; do
            kill $pid 2>/dev/null || true
        done
    }
    trap cleanup EXIT
    
    # Esperar a que los port-forwards estén listos
    echo -e "${BLUE}⏳ Esperando que los port-forwards estén listos...${NC}"
    sleep 5
    
    # Verificar conectividad
    echo -e "${BLUE}🔍 Verificando conectividad...${NC}"
    for service in "${!SERVICE_PORTS[@]}"; do
        local port=${SERVICE_PORTS[$service]}
        local context_path=""
        
        case $service in
            user-service) context_path="/user-service" ;;
            product-service) context_path="/product-service" ;;
            order-service) context_path="/order-service" ;;
            payment-service) context_path="/payment-service" ;;
            shipping-service) context_path="/shipping-service" ;;
            favourite-service) context_path="/favourite-service" ;;
        esac
        
        if curl -s -f "http://localhost:${port}${context_path}/actuator/health" > /dev/null; then
            echo -e "${GREEN}✅ ${service} accesible${NC}"
        else
            echo -e "${YELLOW}⚠️  ${service} no responde aún${NC}"
        fi
    done
    
    echo ""
    echo -e "${BLUE}🧪 Ejecutando pruebas E2E...${NC}"
    
    # Ejecutar pruebas con perfil cluster
    cd "$(dirname "$0")"
    
    # Exportar variables de entorno para que Spring Boot las lea
    export CLUSTER_BASE_URL="http://localhost"
    export USER_SERVICE_URL="http://localhost:8085"
    export USER_SERVICE_CONTEXT="/user-service"
    export PRODUCT_SERVICE_URL="http://localhost:8083"
    export PRODUCT_SERVICE_CONTEXT="/product-service"
    export ORDER_SERVICE_URL="http://localhost:8081"
    export ORDER_SERVICE_CONTEXT="/order-service"
    export PAYMENT_SERVICE_URL="http://localhost:8082"
    export PAYMENT_SERVICE_CONTEXT="/payment-service"
    export SHIPPING_SERVICE_URL="http://localhost:8084"
    export SHIPPING_SERVICE_CONTEXT="/shipping-service"
    export FAVOURITE_SERVICE_URL="http://localhost:8086"
    export FAVOURITE_SERVICE_CONTEXT="/favourite-service"
    
    echo -e "${BLUE}Variables de entorno configuradas:${NC}"
    echo -e "  USER_SERVICE_URL=${USER_SERVICE_URL}"
    echo -e "  PRODUCT_SERVICE_URL=${PRODUCT_SERVICE_URL}"
    echo ""
    
    ./mvnw clean test \
        -Dspring.profiles.active=test,cluster \
        -Dtest=*E2E*Test \
        -DCLUSTER_BASE_URL="${CLUSTER_BASE_URL}" \
        -DUSER_SERVICE_URL="${USER_SERVICE_URL}" \
        -DUSER_SERVICE_CONTEXT="${USER_SERVICE_CONTEXT}" \
        -DPRODUCT_SERVICE_URL="${PRODUCT_SERVICE_URL}" \
        -DPRODUCT_SERVICE_CONTEXT="${PRODUCT_SERVICE_CONTEXT}" \
        -DORDER_SERVICE_URL="${ORDER_SERVICE_URL}" \
        -DORDER_SERVICE_CONTEXT="${ORDER_SERVICE_CONTEXT}" \
        -DPAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL}" \
        -DPAYMENT_SERVICE_CONTEXT="${PAYMENT_SERVICE_CONTEXT}" \
        -DSHIPPING_SERVICE_URL="${SHIPPING_SERVICE_URL}" \
        -DSHIPPING_SERVICE_CONTEXT="${SHIPPING_SERVICE_CONTEXT}" \
        -DFAVOURITE_SERVICE_URL="${FAVOURITE_SERVICE_URL}" \
        -DFAVOURITE_SERVICE_CONTEXT="${FAVOURITE_SERVICE_CONTEXT}" \
        || EXIT_CODE=$?
    
    return ${EXIT_CODE:-0}
}

# Función para ejecutar pruebas dentro del cluster usando kubectl exec
run_with_kubectl_exec() {
    echo -e "${BLUE}📦 Ejecutando pruebas dentro del cluster...${NC}"
    
    # Crear un pod temporal para ejecutar las pruebas
    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: e2e-test-runner
  namespace: ${NAMESPACE}
spec:
  containers:
  - name: test-runner
    image: maven:3.8-openjdk-11
    command: ["/bin/sh", "-c", "sleep 3600"]
  restartPolicy: Never
EOF
    
    echo -e "${BLUE}⏳ Esperando que el pod esté listo...${NC}"
    kubectl wait --for=condition=Ready pod/e2e-test-runner -n "${NAMESPACE}" --timeout=60s
    
    # Copiar código fuente al pod (simplificado - en producción usaría una imagen con el código)
    echo -e "${YELLOW}⚠️  Modo kubectl-exec requiere que el código esté en el pod${NC}"
    echo -e "${YELLOW}💡 Considera usar el modo port-forward o crear un Job de Kubernetes${NC}"
    
    # Limpiar
    kubectl delete pod e2e-test-runner -n "${NAMESPACE}" || true
    
    return 1
}

# Función para ejecutar pruebas usando NodePort (si está configurado)
run_with_nodeport() {
    echo -e "${BLUE}🌐 Usando NodePort para acceder a servicios...${NC}"
    
    # Obtener IP de Minikube
    MINIKUBE_IP=$(minikube ip 2>/dev/null || echo "")
    if [ -z "${MINIKUBE_IP}" ]; then
        echo -e "${RED}❌ No se puede obtener IP de Minikube${NC}"
        echo -e "${YELLOW}💡 Asegúrate de que Minikube está corriendo y configurado${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}Minikube IP: ${MINIKUBE_IP}${NC}"
    
    # Obtener NodePorts de los servicios
    echo -e "${BLUE}🔍 Obteniendo NodePorts...${NC}"
    
    # Nota: Esto requiere que los servicios tengan NodePort configurado
    # Por ahora, asumimos que usamos minikube service
    echo -e "${YELLOW}⚠️  Modo NodePort requiere configuración adicional${NC}"
    echo -e "${YELLOW}💡 Usando minikube service URLs...${NC}"
    
    # Ejecutar pruebas
    cd "$(dirname "$0")"
    
    # Obtener URLs de servicios usando minikube service
    USER_URL=$(minikube service -n "${NAMESPACE}" user-service --url 2>/dev/null | head -1 || echo "")
    PRODUCT_URL=$(minikube service -n "${NAMESPACE}" product-service --url 2>/dev/null | head -1 || echo "")
    
    if [ -z "${USER_URL}" ] || [ -z "${PRODUCT_URL}" ]; then
        echo -e "${RED}❌ No se pueden obtener URLs de servicios${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}🧪 Ejecutando pruebas E2E...${NC}"
    
    # Extraer base URL (sin puerto específico)
    BASE_URL=$(echo "${USER_URL}" | sed 's|:.*||')
    
    CLUSTER_BASE_URL="${BASE_URL}" \
    ./mvnw clean test -Dspring.profiles.active=test,cluster -Dtest=*E2E*Test || EXIT_CODE=$?
    
    return ${EXIT_CODE:-0}
}

# Ejecutar según el modo seleccionado
case "${MODE}" in
    port-forward)
        run_with_port_forward
        ;;
    kubectl-exec)
        run_with_kubectl_exec
        ;;
    nodeport)
        run_with_nodeport
        ;;
    *)
        echo -e "${RED}❌ Modo inválido: ${MODE}${NC}"
        echo -e "${YELLOW}Modos disponibles: port-forward, kubectl-exec, nodeport${NC}"
        exit 1
        ;;
esac

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ Pruebas E2E completadas exitosamente${NC}"
else
    echo ""
    echo -e "${RED}❌ Pruebas E2E fallaron${NC}"
fi

exit $EXIT_CODE

