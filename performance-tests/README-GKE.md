# 🚀 Pruebas de Rendimiento en GKE (Google Kubernetes Engine)

Esta guía explica cómo se ejecutan las pruebas de rendimiento contra servicios desplegados en GKE Staging.

## 📋 Descripción General

Las pruebas de rendimiento están integradas en el pipeline `All-Services-Stage.groovy` y se ejecutan automáticamente después del despliegue y health checks de los servicios en GKE.

## ⚙️ Cómo Funciona

### Flujo en el Pipeline

1. **Deploy Services** → Los servicios se despliegan en GKE Staging
2. **Health Check** → Se verifica que todos los servicios estén saludables
3. **Run Performance Tests** → Se ejecutan las pruebas de rendimiento ⚡ (NUEVO)
4. **Run E2E Tests** → Se ejecutan las pruebas end-to-end
5. **Deployment Summary** → Resumen final del despliegue

### Método de Acceso

Los servicios en GKE usan `ClusterIP`, por lo que las pruebas utilizan **port-forwarding** para acceder a los servicios:

- `kubectl port-forward` mapea los servicios del cluster a `localhost`
- Las pruebas de Locust se ejecutan contra `localhost` con los puertos mapeados
- Después de las pruebas, los port-forwards se limpian automáticamente

## 🔧 Configuración

### Parámetros del Pipeline

El pipeline `All-Services-Stage` acepta los siguientes parámetros para las pruebas de rendimiento:

| Parámetro | Valor por Defecto | Descripción |
|-----------|-------------------|-------------|
| `PERF_TEST_USERS` | `20` | Número de usuarios concurrentes |
| `PERF_TEST_SPAWN_RATE` | `2` | Usuarios creados por segundo |
| `PERF_TEST_DURATION` | `5m` | Duración de las pruebas (ej: `5m`, `10m`) |

### Variables de Entorno

El script `run-performance-gke.sh` acepta las siguientes variables de entorno:

```bash
K8S_NAMESPACE          # Namespace de Kubernetes (default: staging)
PERF_TEST_USERS        # Usuarios concurrentes (default: 20)
PERF_TEST_SPAWN_RATE   # Spawn rate (default: 2)
PERF_TEST_DURATION     # Duración (default: 5m)
SERVICES_TO_TEST       # Lista de servicios a probar (opcional, separada por comas)
```

## 📊 Servicios Probados

Las pruebas se ejecutan para los siguientes servicios:

- ✅ `user-service` (puerto 8085)
- ✅ `product-service` (puerto 8083)
- ✅ `order-service` (puerto 8081)
- ✅ `payment-service` (puerto 8082)
- ✅ `shipping-service` (puerto 8084)
- ✅ `favourite-service` (puerto 8086)

**Nota**: `service-discovery` se excluye automáticamente ya que no tiene pruebas de rendimiento específicas.

## 🚀 Ejecución Manual

Si necesitas ejecutar las pruebas manualmente:

```bash
# 1. Configurar credenciales de GCP
gcloud auth activate-service-account --key-file="path/to/service-account.json"
gcloud config set project YOUR_PROJECT_ID
gcloud container clusters get-credentials CLUSTER_NAME --zone ZONE

# 2. Instalar dependencias
cd performance-tests
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 3. Ejecutar pruebas
export K8S_NAMESPACE=staging
export PERF_TEST_USERS=20
export PERF_TEST_SPAWN_RATE=2
export PERF_TEST_DURATION=5m
export SERVICES_TO_TEST=user-service,product-service  # Opcional

./run-performance-gke.sh
```

## 📈 Resultados

### Ubicación de Resultados

Los resultados se guardan en:
- **En Jenkins**: `performance-results/` (artefactos del pipeline)
- **Localmente**: `performance-tests/results/`

### Formatos de Salida

Cada prueba genera:
- **HTML Report**: `{service}_gke_{timestamp}.html` - Reporte visual completo
- **CSV Files**: 
  - `{service}_gke_{timestamp}_requests.csv` - Datos de requests
  - `{service}_gke_{timestamp}_stats.csv` - Estadísticas agregadas
  - `{service}_gke_{timestamp}_failures.csv` - Errores encontrados

### Métricas Incluidas

- **Response Time**: Promedio, mediana, percentiles (P50, P90, P95, P99)
- **Throughput**: Requests por segundo
- **Error Rate**: Porcentaje de requests fallidos
- **Request Distribution**: Por tipo de request (GET, POST, etc.)

## 🎯 Ejemplos de Uso

### Ejemplo 1: Pruebas Rápidas (CI/CD)

```groovy
// En el pipeline con valores por defecto
PERF_TEST_USERS = '10'
PERF_TEST_SPAWN_RATE = '1'
PERF_TEST_DURATION = '2m'
```

### Ejemplo 2: Pruebas de Carga Normal

```groovy
PERF_TEST_USERS = '30'
PERF_TEST_SPAWN_RATE = '3'
PERF_TEST_DURATION = '10m'
```

### Ejemplo 3: Pruebas de Estrés

```groovy
PERF_TEST_USERS = '100'
PERF_TEST_SPAWN_RATE = '10'
PERF_TEST_DURATION = '15m'
```

## 🔍 Troubleshooting

### Problema: Port-Forwards No Inician

**Síntomas**: Los port-forwards fallan al iniciar

**Soluciones**:
```bash
# Verificar que el namespace existe
kubectl get namespace staging

# Verificar que los deployments existen
kubectl get deployments -n staging

# Verificar logs del script
# (los logs se muestran en la salida del pipeline)
```

### Problema: Servicios No Responden

**Síntomas**: Health checks fallan aunque los port-forwards estén activos

**Soluciones**:
```bash
# Verificar que los pods estén corriendo
kubectl get pods -n staging

# Verificar health directamente en el pod
kubectl exec -n staging deployment/user-service -- \
  curl -s http://localhost:8085/user-service/actuator/health

# Verificar logs del servicio
kubectl logs -n staging deployment/user-service --tail=50
```

### Problema: Locust No Se Instala

**Síntomas**: Error al instalar Locust o dependencias

**Soluciones**:
```bash
# Verificar que Python 3 está instalado
python3 --version

# Verificar que pip está instalado
pip3 --version

# Instalar manualmente
pip3 install locust==2.17.0 requests faker pandas matplotlib seaborn
```

### Problema: Pruebas Muy Lentas

**Causas posibles**:
- Servicios con pocos recursos en GKE
- Latencia de red entre Jenkins y GKE
- Carga alta en el cluster

**Soluciones**:
- Aumentar recursos de los pods en GKE
- Reducir número de usuarios concurrentes
- Ejecutar pruebas en horarios de menor carga

## 📝 Notas Importantes

1. **Port-Forwarding**: Los port-forwards se crean y destruyen automáticamente durante la ejecución
2. **Servicios Excluidos**: `service-discovery` se excluye automáticamente
3. **Limpieza Automática**: Los port-forwards se limpian incluso si las pruebas fallan
4. **Resultados**: Los resultados se archivan como artefactos de Jenkins
5. **Duración**: Las pruebas no deben exceder 30 minutos para evitar timeouts

## 🔄 Integración con CI/CD

Las pruebas de rendimiento se ejecutan automáticamente en el pipeline cuando:
- ✅ Los servicios se despliegan exitosamente
- ✅ Los health checks pasan
- ✅ La variable `SERVICES_TO_DEPLOY` contiene servicios válidos

El pipeline fallará si:
- ❌ Las pruebas de rendimiento fallan para algún servicio
- ❌ No se pueden establecer los port-forwards
- ❌ Los servicios no responden después de múltiples intentos

## 📚 Referencias

- [Locust Documentation](https://docs.locust.io/)
- [Kubernetes Port-Forwarding](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/)
- [GKE Documentation](https://cloud.google.com/kubernetes-engine/docs)

---

**Última actualización**: Noviembre 2025  
**Integrado en**: `All-Services-Stage.groovy`  
**Script principal**: `run-performance-gke.sh`

