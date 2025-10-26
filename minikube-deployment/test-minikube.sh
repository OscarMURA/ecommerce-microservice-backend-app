#!/usr/bin/env bash
# Test Minikube deployment without API Gateway (only service-discovery, zipkin, and business microservices)

set -euo pipefail

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
  log_error "Minikube no está ejecutándose. Iniciando Minikube..."
  
  # Detect available Docker memory and configure Minikube accordingly
  DOCKER_MEMORY=$(docker info --format '{{.MemTotal}}' 2>/dev/null | awk '{print int($1/1024/1024)}' || echo "0")
  
  if [[ "${DOCKER_MEMORY}" -lt 4000 ]]; then
    log_warn "Docker tiene solo ${DOCKER_MEMORY}MB de memoria. Configurando Minikube con 3000MB..."
    minikube start --memory=3000 --cpus=2
  elif [[ "${DOCKER_MEMORY}" -lt 8000 ]]; then
    log_info "Docker tiene ${DOCKER_MEMORY}MB de memoria. Configurando Minikube con 4000MB..."
    minikube start --memory=4000 --cpus=2
  else
    log_info "Docker tiene ${DOCKER_MEMORY}MB de memoria. Configurando Minikube con 6000MB..."
    minikube start --memory=6000 --cpus=2
  fi
fi

# Set Minikube context
kubectl config use-context minikube

# Create namespace
log_info "Creando namespace ecommerce..."
kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -

# Apply ConfigMap and Secrets
log_info "Aplicando ConfigMap y Secrets..."
kubectl apply -f minikube-configmap.yaml
kubectl apply -f minikube-secrets.yaml

# Build and load images to Minikube
log_info "Construyendo imágenes para Minikube..."

# Build services (excluding api-gateway, cloud-config, proxy-client)
services=("service-discovery" "order-service" "payment-service" "product-service" "shipping-service" "user-service" "favourite-service")

for service in "${services[@]}"; do
  log_info "Construyendo ${service}..."
  docker build -t "${service}:minikube" -f "${service}/Dockerfile" .
  minikube image load "${service}:minikube"
done

# Deploy Zipkin
log_info "Desplegando Zipkin para tracing..."
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zipkin
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: zipkin
  template:
    metadata:
      labels:
        app: zipkin
    spec:
      containers:
      - name: zipkin
        image: openzipkin/zipkin:latest
        ports:
        - containerPort: 9411
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
  name: zipkin
  namespace: ecommerce
spec:
  selector:
    app: zipkin
  ports:
  - port: 9411
    targetPort: 9411
  type: LoadBalancer
EOF

# Deploy Service Discovery (if not already running)
log_info "Verificando service-discovery..."
if ! kubectl get deployment service-discovery -n ecommerce >/dev/null 2>&1; then
  log_info "Desplegando service-discovery..."
  kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: service-discovery
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: service-discovery
  template:
    metadata:
      labels:
        app: service-discovery
    spec:
      containers:
      - name: service-discovery
        image: service-discovery:minikube
        ports:
        - containerPort: 8761
        env:
        - name: SERVER_PORT
          value: "8761"
        - name: SPRING_CLOUD_CONFIG_ENABLED
          value: "false"
        envFrom:
        - configMapRef:
            name: ecommerce-config
        - secretRef:
            name: ecommerce-secrets
        resources:
          requests:
            cpu: 50m
            memory: 256Mi
          limits:
            cpu: 300m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: service-discovery
  namespace: ecommerce
spec:
  selector:
    app: service-discovery
  ports:
  - port: 8761
    targetPort: 8761
EOF
fi

# Wait for service-discovery
log_info "Esperando que service-discovery esté listo..."
kubectl wait --for=condition=available --timeout=300s deployment/service-discovery -n ecommerce

# Deploy business microservices
log_info "Desplegando microservicios de negocio..."

business_services=("order-service:8081" "payment-service:8082" "product-service:8083" "shipping-service:8084" "user-service:8085" "favourite-service:8086")

for service_port in "${business_services[@]}"; do
  IFS=':' read -r service port <<< "$service_port"
  log_info "Desplegando ${service}..."
  
  kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service}
  namespace: ecommerce
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
        image: ${service}:minikube
        ports:
        - containerPort: ${port}
        env:
        - name: SERVER_PORT
          value: "${port}"
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
  name: ${service}
  namespace: ecommerce
spec:
  selector:
    app: ${service}
  ports:
  - port: ${port}
    targetPort: ${port}
EOF

  # Wait for service to be ready
  log_info "Esperando que ${service} esté listo..."
  kubectl wait --for=condition=available --timeout=300s deployment/${service} -n ecommerce
done

# Show final status
log_info "Estado final de los deployments:"
kubectl get deployments -n ecommerce

log_info "Estado final de los pods:"
kubectl get pods -n ecommerce

log_info "Estado final de los services:"
kubectl get services -n ecommerce

# Test health endpoints
log_info "Probando health endpoints..."

# Test service-discovery
log_info "Probando service-discovery health..."
kubectl exec -n ecommerce deployment/service-discovery -- curl -s http://localhost:8761/actuator/health || log_warn "Service Discovery health check falló"

# Test business services
for service_port in "${business_services[@]}"; do
  IFS=':' read -r service port <<< "$service_port"
  log_info "Probando ${service} health..."
  kubectl exec -n ecommerce deployment/${service} -- curl -s http://localhost:${port}/actuator/health || log_warn "${service} health check falló"
done

log_success "✅ Despliegue sin API Gateway completado!"
log_info "Servicios desplegados:"
log_info "  - service-discovery (Eureka Server)"
log_info "  - zipkin (Tracing)"
log_info "  - order-service"
log_info "  - payment-service"
log_info "  - product-service"
log_info "  - shipping-service"
log_info "  - user-service"
log_info "  - favourite-service"

log_info "Para acceder a los servicios:"
log_info "  - Service Discovery: minikube service service-discovery -n ecommerce"
log_info "  - Zipkin: minikube service zipkin -n ecommerce"

log_info "Para ver logs de un servicio:"
log_info "  kubectl logs -f deployment/SERVICE_NAME -n ecommerce"

log_info "Para eliminar todo:"
log_info "  kubectl delete namespace ecommerce"
