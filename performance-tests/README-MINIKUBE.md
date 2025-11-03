# 🚀 Pruebas de Rendimiento con Minikube

Esta guía explica cómo ejecutar las pruebas de rendimiento contra servicios desplegados en Minikube.

## 📋 Resumen

Las pruebas de rendimiento ahora soportan ejecutarse contra servicios desplegados en Minikube. A diferencia del modo de desarrollo que asume servicios corriendo directamente en `localhost`, el modo Minikube requiere configurar port-forwarding para acceder a los servicios dentro del cluster de Kubernetes.

## ⚠️ Limitaciones

**Importante**: El despliegue de Minikube no incluye un API Gateway. Por lo tanto:
- ❌ **NO se pueden ejecutar** pruebas completas de "ecommerce" (que requieren API Gateway)
- ✅ **SÍ se pueden ejecutar** pruebas de servicios individuales:
  - `user-service`
  - `product-service`
  - `order-service`
  - `payment-service`
  - `favourite-service`
  - `shipping-service`

## 🛠️ Requisitos Previos

1. **Minikube ejecutándose**
   ```bash
   minikube status
   ```

2. **Servicios desplegados en Minikube**
   ```bash
   # Verificar que los servicios estén desplegados
   kubectl get pods -n ecommerce
   
   # Si no están desplegados, ejecutar:
   ./minikube-deployment/test-minikube.sh
   ```

3. **Dependencias de Python instaladas**
   ```bash
   cd performance-tests
   ./run_tests.sh install
   ```

## 🚀 Uso Rápido

### Paso 1: Configurar Port-Forwarding

El script `setup-minikube-ports.sh` configura automáticamente port-forwarding para todos los servicios:

```bash
cd performance-tests
./setup-minikube-ports.sh
```

Esto mapeará los servicios a los siguientes puertos en localhost:
- `order-service`: `localhost:8081`
- `payment-service`: `localhost:8082`
- `product-service`: `localhost:8083`
- `shipping-service`: `localhost:8084`
- `user-service`: `localhost:8085`
- `favourite-service`: `localhost:8086`

### Paso 2: Verificar Servicios

```bash
PERF_TEST_ENV=minikube ./run_tests.sh check
```

Este comando:
- Verifica que los port-forwards estén configurados
- Prueba que todos los servicios respondan correctamente

### Paso 3: Ejecutar Pruebas

```bash
# Prueba de rendimiento en user-service
PERF_TEST_ENV=minikube ./run_tests.sh performance user-service 30 3 5m

# Prueba de estrés en product-service
PERF_TEST_ENV=minikube ./run_tests.sh stress product-service 100 10 5m

# Prueba interactiva
PERF_TEST_ENV=minikube ./run_tests.sh interactive user-service
```

## 📝 Ejemplos Completos

### Ejemplo 1: Prueba de Rendimiento de User Service

```bash
# 1. Configurar port-forwards
cd performance-tests
./setup-minikube-ports.sh

# 2. Ejecutar prueba
PERF_TEST_ENV=minikube ./run_tests.sh performance user-service 30 3 5m
```

### Ejemplo 2: Suite de Pruebas Múltiples

```bash
# Configurar port-forwards (solo una vez)
./setup-minikube-ports.sh

# Ejecutar múltiples pruebas
PERF_TEST_ENV=minikube ./run_tests.sh performance user-service 20 2 3m
PERF_TEST_ENV=minikube ./run_tests.sh performance product-service 25 3 3m
PERF_TEST_ENV=minikube ./run_tests.sh performance order-service 15 2 3m
```

### Ejemplo 3: Pruebas de Estrés

```bash
# Prueba de estrés en product-service
PERF_TEST_ENV=minikube ./run_tests.sh stress product-service 100 20 10m
```

## 🔧 Gestión de Port-Forwards

### Iniciar Port-Forwards

```bash
./setup-minikube-ports.sh
```

El script:
- Detecta si Minikube está ejecutándose
- Verifica que el namespace `ecommerce` exista
- Inicia port-forwards para todos los servicios
- Verifica que los servicios respondan

### Detener Port-Forwards

```bash
./stop-minikube-ports.sh
```

O manualmente:
```bash
# Ver procesos de port-forward
pgrep -f "kubectl port-forward.*ecommerce"

# Matar todos los port-forwards
pkill -f "kubectl port-forward.*ecommerce"
```

### Verificar Port-Forwards Activos

```bash
# Ver procesos
ps aux | grep "kubectl port-forward"

# Verificar puertos en uso
lsof -i :8081
lsof -i :8082
# ... etc
```

## 📊 Mapeo de Puertos

| Servicio | Puerto Minikube | Puerto Local (Port-Forward) |
|----------|-----------------|-----------------------------|
| order-service | 8081 | 8081 |
| payment-service | 8082 | 8082 |
| product-service | 8083 | 8083 |
| shipping-service | 8084 | 8084 |
| user-service | 8085 | 8085 |
| favourite-service | 8086 | 8086 |

## 🐛 Solución de Problemas

### Problema: Port-Forwards No Inician

**Síntomas**: El script `setup-minikube-ports.sh` falla o los servicios no responden.

**Soluciones**:
```bash
# 1. Verificar que Minikube esté ejecutándose
minikube status

# 2. Verificar que los servicios estén desplegados
kubectl get pods -n ecommerce

# 3. Verificar que los servicios estén corriendo
kubectl get pods -n ecommerce | grep Running

# 4. Verificar logs de un servicio
kubectl logs -n ecommerce deployment/user-service --tail=20
```

### Problema: Puerto Ya en Uso

**Síntomas**: Error "Address already in use" o el puerto está ocupado.

**Solución**:
```bash
# Matar proceso en el puerto específico
lsof -ti:8081 | xargs kill -9

# O usar el script de limpieza
./stop-minikube-ports.sh
```

### Problema: Servicios No Responden

**Síntomas**: Health checks fallan aunque los port-forwards estén activos.

**Soluciones**:
```bash
# 1. Verificar que los pods estén listos
kubectl get pods -n ecommerce

# 2. Verificar que los servicios de Kubernetes estén creados
kubectl get services -n ecommerce

# 3. Probar health check directamente en el pod
kubectl exec -n ecommerce deployment/user-service -- \
  curl -s http://localhost:8085/user-service/actuator/health

# 4. Reiniciar un servicio si es necesario
kubectl rollout restart deployment/user-service -n ecommerce
```

### Problema: Tests Fallan con Timeouts

**Causas posibles**:
- Los servicios en Minikube son más lentos (menos recursos)
- Los port-forwards tienen latencia adicional

**Soluciones**:
```bash
# Ajustar tiempo de espera en las pruebas
# O aumentar recursos de Minikube
minikube stop
minikube start --memory=6000 --cpus=4
```

## 📈 Configuración de Entorno

### Variables de Entorno

```bash
# Activar modo Minikube
export PERF_TEST_ENV=minikube

# Desactivar (volver a modo desarrollo)
export PERF_TEST_ENV=development
# o simplemente no definir la variable
```

### Configuración en environment_config.py

La configuración de Minikube está definida en `environment_config.py`:

```python
MINIKUBE_CONFIG = {
    "host": "http://localhost:8080",  # No usado (no hay API Gateway)
    "max_users": 100,
    "default_duration": "10m",
    "spawn_rate": 5,
    "expected_metrics": {
        "response_time_p95": 3000,
        "error_rate": 0.05,
        "throughput": 50
    },
    "services": {
        "user-service": "http://localhost:8085",
        "product-service": "http://localhost:8083",
        # ... etc
    }
}
```

## ✅ Checklist Antes de Ejecutar Pruebas

- [ ] Minikube está ejecutándose (`minikube status`)
- [ ] Servicios están desplegados (`kubectl get pods -n ecommerce`)
- [ ] Port-forwards están configurados (`./setup-minikube-ports.sh`)
- [ ] Servicios responden (`PERF_TEST_ENV=minikube ./run_tests.sh check`)
- [ ] Dependencias de Python instaladas (`./run_tests.sh install`)

## 📚 Comandos de Referencia Rápida

```bash
# Configurar port-forwards
./setup-minikube-ports.sh

# Detener port-forwards
./stop-minikube-ports.sh

# Verificar servicios
PERF_TEST_ENV=minikube ./run_tests.sh check

# Ejecutar prueba
PERF_TEST_ENV=minikube ./run_tests.sh performance user-service 30 3 5m

# Ver estado de Minikube
kubectl get pods -n ecommerce
kubectl get services -n ecommerce
```

## 🔄 Flujo de Trabajo Recomendado

1. **Desplegar servicios en Minikube**
   ```bash
   ./minikube-deployment/test-minikube.sh
   ```

2. **Configurar port-forwards**
   ```bash
   cd performance-tests
   ./setup-minikube-ports.sh
   ```

3. **Verificar que todo funcione**
   ```bash
   PERF_TEST_ENV=minikube ./run_tests.sh check
   ```

4. **Ejecutar pruebas**
   ```bash
   PERF_TEST_ENV=minikube ./run_tests.sh performance user-service 30 3 5m
   ```

5. **Analizar resultados**
   - Los resultados se guardan en `results/`
   - Revisar reportes HTML generados

6. **Limpiar port-forwards** (opcional, al finalizar)
   ```bash
   ./stop-minikube-ports.sh
   ```

## 📞 Soporte

Si tienes problemas:
1. Revisa los logs de Minikube: `kubectl logs -n ecommerce deployment/<service-name>`
2. Verifica el estado de los pods: `kubectl describe pod -n ecommerce <pod-name>`
3. Consulta la documentación principal: `README.md`

---

**Nota**: Los port-forwards se ejecutan en background. Para detenerlos, usa `./stop-minikube-ports.sh` o reinicia tu terminal (los procesos se detendrán automáticamente).

