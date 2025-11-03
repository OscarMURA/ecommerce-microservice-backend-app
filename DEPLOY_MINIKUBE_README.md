# 🚀 Script de Despliegue en Minikube

Script definitivo para desplegar microservicios en Minikube con todos los fixes validados.

## 📋 Características

- ✅ **Detección automática** de recursos de Docker
- ✅ **Despliegue ordenado** (servicios críticos primero)
- ✅ **Verificaciones DNS** y ConfigServer
- ✅ **Selección flexible** de servicios
- ✅ **Probes configurados** correctamente
- ✅ **Todos los fixes** aplicados (CORS, Actuator, timeouts)

## 🎯 Uso Rápido

```bash
# Desplegar servicios críticos (recomendado para pruebas)
./deploy-minikube.sh

# Desplegar todos los servicios (requiere 8GB+ RAM)
./deploy-minikube.sh all

# Desplegar servicios específicos
./deploy-minikube.sh api-gateway user-service product-service

# Limpiar y empezar de cero
./deploy-minikube.sh --clean critical
```

## 📊 Niveles de Despliegue

| Comando | Servicios | RAM Recomendada | Tiempo |
|---------|-----------|-----------------|--------|
| `./deploy-minikube.sh` | critical (3) | 3-4 GB | ~10 min |
| `./deploy-minikube.sh all` | todos (10) | 8 GB | ~25 min |
| Servicios custom | selección | variable | variable |

## 🔧 Opciones

```bash
-h, --help    # Mostrar ayuda completa
-c, --clean   # Eliminar Minikube existente y empezar limpio
-k, --keep    # Mantener Minikube corriendo al finalizar
```

## 📦 Servicios Disponibles

### Críticos (siempre primero)
- `service-discovery` - Eureka Server (8761)
- `cloud-config` - Config Server (9296)

### Gateway
- `api-gateway` - API Gateway (8080) - **NodePort**

### Servicios de Negocio
- `proxy-client` - Frontend proxy (8900)
- `user-service` - Gestión de usuarios (8700)
- `product-service` - Catálogo de productos (8500)
- `favourite-service` - Favoritos (8800)
- `order-service` - Pedidos (8300)
- `shipping-service` - Envíos (8600)
- `payment-service` - Pagos (8400)

## 🎬 Flujo de Despliegue

1. **Inicio de Minikube**
   - Detección de memoria Docker
   - Configuración automática de recursos
   - Inicio del cluster

2. **Preparación**
   - Namespace `ecommerce`
   - ConfigMap con configuración base
   - Secrets para credenciales

3. **Build de Imágenes**
   - Construcción en daemon de Minikube
   - Tag: `local/<servicio>:latest`
   - Optimizado con cache

4. **Fase 1: service-discovery**
   - Despliegue de Eureka
   - Espera hasta Ready (5 min)

5. **Fase 2: cloud-config**
   - Despliegue de Config Server
   - Espera hasta Ready (5 min)

6. **Fase 3: Verificaciones**
   - ConfigServer respondiendo (60s)
   - Propagación DNS (60s)
   - Estabilización (30s)
   - **Total: ~2.5 minutos de espera**

7. **Fase 4: Servicios Restantes**
   - api-gateway (timeout: 8 min)
   - Otros servicios (timeout: 5 min)

## 📈 Tiempos Estimados

```
Servicio crítico × 3:    ~10 minutos
+ user-service:          +5 minutos
+ product-service:       +5 minutos
+ Otros servicios:       +3-5 min cada uno
```

## 🔍 Verificación Post-Despliegue

```bash
# Ver estado de pods
kubectl -n ecommerce get pods

# Ver servicios
kubectl -n ecommerce get svc

# Acceder a servicios (NodePort)
minikube service api-gateway -n ecommerce --profile=ecommerce
minikube service service-discovery -n ecommerce --profile=ecommerce

# Ver logs de un servicio
kubectl -n ecommerce logs -f <pod-name>

# Probar health endpoint
kubectl -n ecommerce exec <pod-name> -- curl localhost:8080/actuator/health
```

## 🐛 Troubleshooting

### Minikube no inicia
```bash
# Verificar Docker
docker ps

# Verificar memoria
docker info | grep Memory

# Limpiar y reintentar
./deploy-minikube.sh --clean
```

### Pod en CrashLoopBackOff
```bash
# Ver logs
kubectl -n ecommerce logs <pod-name>

# Describir pod
kubectl -n ecommerce describe pod <pod-name>

# Verificar eventos
kubectl -n ecommerce get events --sort-by='.lastTimestamp'
```

### DNS inter-pod falla
**Esto es normal en Minikube**. El script continúa porque:
- `spring.config.import` tiene `optional: true`
- Los servicios pueden arrancar sin ConfigServer
- La funcionalidad básica sigue funcionando

### Memoria insuficiente
```bash
# Desplegar solo críticos
./deploy-minikube.sh critical

# O aumentar RAM en Docker Desktop
# Settings → Resources → Memory: 8GB
```

## 🧹 Limpieza

```bash
# Detener Minikube (preserva estado)
minikube stop --profile=ecommerce

# Eliminar completamente
minikube delete --profile=ecommerce

# Limpiar imágenes Docker (opcional)
docker system prune -a
```

## ⚙️ Configuración Avanzada

### Modificar recursos de Minikube

Edita el script `deploy-minikube.sh` líneas 180-190:

```bash
MINIKUBE_MEM="8192"    # RAM en MB
MINIKUBE_CPUS="4"      # Número de CPUs
```

### Modificar probes

Edita la función `generate_manifest()` líneas 230-250:

```bash
readiness_initial=100   # Segundos antes de primera prueba
readiness_failures=60   # Intentos fallidos antes de marcar como NO Ready
```

### Agregar nuevo servicio

1. Agregar puerto en array `SERVICE_PORTS` (línea 35)
2. Agregar a `ALL_SERVICES` (línea 45)
3. Ejecutar: `./deploy-minikube.sh <nuevo-servicio>`

## 📚 Archivos Relacionados

- `deploy-to-gke.sh` - Script para despliegue en GKE (producción)
- `API_GATEWAY_FIX.md` - Documentación de fixes aplicados
- `MINIKUBE_TEST_RESULTS.md` - Resultados de pruebas
- `INFRASTRUCTURE.md` - Arquitectura completa del sistema

## 🎯 Próximos Pasos

Una vez validado en Minikube:

```bash
# 1. Commit de cambios (si hay alguno)
git add .
git commit -m "test: Validated deployment in Minikube"
git push origin develop

# 2. Ejecutar pipeline de GKE en Jenkins
# Jenkins → Deploy_K8k_Dev_develop → Build with Parameters

# 3. Monitorear despliegue en GKE
kubectl -n ecommerce get pods -w
```

## ✅ Checklist de Validación

Después del despliegue, verifica:

- [ ] Todos los pods en estado `Running`
- [ ] Todos los pods `READY 1/1`
- [ ] `RESTARTS = 0` (especialmente api-gateway)
- [ ] `/actuator/health` responde con `{"status":"UP"}`
- [ ] Eureka UI muestra servicios registrados
- [ ] api-gateway responde en NodePort

---

**Autor**: Oscar MURA  
**Versión**: 1.0  
**Fecha**: 2025-10-25  
**Basado en**: Pruebas exhaustivas y fixes validados

