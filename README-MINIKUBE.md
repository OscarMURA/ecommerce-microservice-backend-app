# 🚀 Despliegue Definitivo de Microservicios en Minikube

Script optimizado para desplegar microservicios de ecommerce en Minikube con configuración embebida y recursos ajustados para evitar problemas de memoria.

## 📋 Servicios Desplegados

- ✅ **service-discovery** (Eureka Server) - Puerto 8761
- ✅ **zipkin** (Tracing) - Puerto 9411
- ✅ **order-service** - Puerto 8081
- ✅ **payment-service** - Puerto 8082
- ✅ **product-service** - Puerto 8083
- ✅ **shipping-service** - Puerto 8084
- ✅ **user-service** - Puerto 8085
- ✅ **favourite-service** - Puerto 8086

## 🛠️ Recursos Optimizados

### Minikube
- **RAM**: 3-6GB (detectado automáticamente según Docker)
- **CPUs**: 2-4 (detectado automáticamente)
- **Disco**: 20GB

### Microservicios
- **Service Discovery**: 256Mi RAM, 300m CPU
- **Microservicios de Negocio**: 512Mi RAM, 400m CPU
- **Zipkin**: 512Mi RAM, 400m CPU

## 🚀 Uso Rápido

### Despliegue Completo
```bash
./test-minikube.sh
```

### Limpieza Completa
```bash
# Eliminar namespace (elimina todos los recursos)
kubectl delete namespace ecommerce

# Detener Minikube
minikube stop

# Eliminar Minikube completamente
minikube delete
```

## 📊 Verificar Estado

```bash
# Ver todos los pods
kubectl -n ecommerce get pods

# Ver todos los servicios
kubectl -n ecommerce get services

# Ver logs de un servicio
kubectl -n ecommerce logs -f deployment/SERVICE_NAME
```

## 🌐 Acceder a Servicios

```bash
# Service Discovery (Eureka Dashboard)
minikube service service-discovery -n ecommerce

# Zipkin (Tracing Dashboard)
minikube service zipkin -n ecommerce
```

## 🔍 Probar Endpoints

### Health Checks Completos
```bash
# Service Discovery Health
kubectl exec -n ecommerce deployment/service-discovery -- curl -s http://localhost:8761/actuator/health

# Order Service Health
kubectl exec -n ecommerce deployment/order-service -- curl -s http://localhost:8081/order-service/actuator/health

# Payment Service Health
kubectl exec -n ecommerce deployment/payment-service -- curl -s http://localhost:8082/payment-service/actuator/health

# Product Service Health
kubectl exec -n ecommerce deployment/product-service -- curl -s http://localhost:8083/product-service/actuator/health

# Shipping Service Health
kubectl exec -n ecommerce deployment/shipping-service -- curl -s http://localhost:8084/shipping-service/actuator/health

# User Service Health
kubectl exec -n ecommerce deployment/user-service -- curl -s http://localhost:8085/user-service/actuator/health

# Favourite Service Health
kubectl exec -n ecommerce deployment/favourite-service -- curl -s http://localhost:8086/favourite-service/actuator/health
```

### Health Check Rápido (Todos los Servicios)
```bash
# Verificar estado de todos los pods
kubectl get pods -n ecommerce

# Verificar que todos estén "Running" y "Ready"
kubectl get pods -n ecommerce -o wide
```

### ✅ Respuesta Esperada de Health Checks

**Todos los servicios deberían responder con:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "H2" } },
    "discoveryComposite": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

**Servicios descubiertos en Eureka:**
- `favourite-service` (1 instancia)
- `payment-service` (1 instancia) 
- `order-service` (1 instancia)
- `product-service` (1 instancia)
- `shipping-service` (1 instancia)
- `user-service` (1 instancia)

### Endpoints de Negocio
```bash
# Order Service
kubectl exec -n ecommerce deployment/order-service -- curl -s http://localhost:8081/order-service/

# Payment Service
kubectl exec -n ecommerce deployment/payment-service -- curl -s http://localhost:8082/payment-service/

# Shipping Service
kubectl exec -n ecommerce deployment/shipping-service -- curl -s http://localhost:8084/shipping-service/
```

## ⚡ Características del Script

- **🚀 Rápido**: Despliegue completo en 8-10 minutos
- **🛡️ Estable**: Recursos optimizados para evitar OOMKilled
- **🔧 Automático**: Detección automática de recursos de Docker
- **📦 Completo**: Incluye construcción de imágenes y despliegue
- **✅ Verificación**: Health checks automáticos al final

## 📝 Notas

- Los servicios usan configuración embebida (no dependen de cloud-config)
- Todos los servicios se registran automáticamente en Eureka
- Zipkin está configurado para tracing distribuido
- Los recursos están optimizados para evitar problemas de memoria (OOMKilled)
- El script detecta automáticamente los recursos disponibles de Docker

## ⚠️ Requisitos

- Docker con al menos 3GB de RAM asignada (recomendado 6GB+)
- Minikube instalado
- kubectl instalado
- curl instalado (para health checks)

## 🐛 Solución de Problemas

### Si un servicio se reinicia (OOMKilled)
```bash
# Verificar recursos del pod
kubectl describe pod -n ecommerce -l app=SERVICE_NAME

# Si aparece "OOMKilled", aumentar memoria en el script
# Editar test-minikube.sh y cambiar memory: 256Mi a memory: 512Mi
```

### Si Minikube no inicia
```bash
# Verificar memoria de Docker
docker info | grep "Total Memory"

# Aumentar memoria de Docker en Docker Desktop
# Settings → Resources → Memory → 6GB+
```

### Si un Health Check falla
```bash
# Verificar logs del servicio
kubectl logs -n ecommerce deployment/SERVICE_NAME --tail=20

# Verificar si el pod está corriendo
kubectl get pods -n ecommerce -l app=SERVICE_NAME

# Verificar recursos del pod
kubectl describe pod -n ecommerce -l app=SERVICE_NAME

# Reiniciar el servicio si es necesario
kubectl rollout restart deployment/SERVICE_NAME -n ecommerce
```

### Si un servicio no responde
```bash
# Verificar conectividad interna
kubectl exec -n ecommerce deployment/SERVICE_NAME -- curl -s http://localhost:PUERTO/actuator/health

# Verificar si el servicio está registrado en Eureka
kubectl exec -n ecommerce deployment/service-discovery -- curl -s http://localhost:8761/eureka/apps

# Verificar configuración del servicio
kubectl get configmap ecommerce-config -n ecommerce -o yaml
```
