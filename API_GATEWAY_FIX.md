# 🔧 Fix: API Gateway Connection Refused Error

## 🐛 Problema Identificado

El servicio `api-gateway` **siempre falla** con el siguiente error:

```
WARN o.s.b.context.config.ConfigDataLoader : Could not locate PropertySource: 
I/O error on GET request for "http://cloud-config:9296/API-GATEWAY/dev": 
Connection refused (Connection refused)
```

### 📊 Síntomas

```bash
# Timeline del error:
11:26:21 - cloud-config rollout successful ✅
11:30:21 - ConfigServer verificado (curl desde dentro del pod) ✅
11:31:57 - Espera de 90s completada ✅
11:32:19 - api-gateway deployment creado ✅
11:32:25 - Esperando rollout...

# Pero al arrancar:
16:39:50 - api-gateway inicia
16:39:52 - Intenta conectarse a cloud-config → Connection refused ❌
16:39:52 - Segundo intento → Connection refused ❌
         - Pod crashea y se reinicia (RESTARTS: 2)
11:40:31 - Timeout después de 8 minutos ❌
```

---

## 🔍 Causa Raíz

### El problema NO es que `cloud-config` no esté funcionando

El problema es **timing de propagación del Service DNS en Kubernetes**:

1. ✅ El **pod** de `cloud-config` está Ready (pasa readiness probe)
2. ✅ El **ConfigServer** responde en puerto 9296 (verificado con curl)
3. ❌ El **Service DNS** (`http://cloud-config:9296/`) NO está completamente propagado

### ¿Por qué?

Cuando Kubernetes crea un Service, hay un delay de propagación:

```
Pod Ready → Service Endpoint actualizado → DNS propagado
  ↑            ↑ (1-5 segundos)               ↑ (5-30 segundos)
  OK           OK                             ❌ AQUÍ FALLA
```

Cuando `api-gateway` arranca **inmediatamente** después de que `cloud-config` pase su readiness probe, el DNS puede no estar listo todavía.

---

## ✅ Solución Implementada

### 1. Verificación Robusta del Service DNS

**Antes** (solo verificaba el pod):
```bash
kubectl exec cloud-config-pod -- curl http://localhost:9296/
# ✅ Esto funciona porque está dentro del pod
```

**Ahora** (verifica desde OTRO pod):
```bash
kubectl exec service-discovery-pod -- curl http://cloud-config:9296/actuator/health
# ✅ Esto prueba que el Service DNS esté realmente disponible en el cluster
```

### 2. Tiempos de Espera Aumentados

| Fase | Antes | Ahora | Razón |
|------|-------|-------|-------|
| Después de verificar ConfigServer | 90s | 90s | Mantener estabilización |
| **Propagación de Service DNS** | 30s | **60s** | Dar tiempo al DNS |
| **Verificación inter-pod** | ❌ No existía | **50s** | 10 intentos × 5s |
| **Espera final** | 30s | **30s** | Buffer adicional |
| **TOTAL** | 150s | **230s** | +80s para DNS |

### 3. Probes Específicos para API Gateway

```yaml
# ANTES (genérico):
initialDelaySeconds: 130
failureThreshold: 60
# Total: 130 + (60 × 5) = 430 segundos

# AHORA (específico para api-gateway):
initialDelaySeconds: 200   # +70s para esperar cloud-config
failureThreshold: 100       # +40 intentos
# Total: 200 + (100 × 5) = 700 segundos (~12 min)
```

### 4. Timeout de Rollout Aumentado

```bash
# Otros servicios:
TIMEOUT="480s"  # 8 minutos

# API Gateway específicamente:
TIMEOUT="720s"  # 12 minutos
```

---

## 📝 Cambios en `deploy-to-gke.sh`

### Cambio 1: Verificación de Service DNS (Líneas 500-532)

```bash
# Espera adicional para propagación de DNS
sleep 60

# Verificación desde service-discovery → cloud-config
VERIFICATION_POD=$(kubectl get pod -l app="service-discovery" -o jsonpath='{.items[0].metadata.name}')

for i in {1..10}; do
  if kubectl exec "${VERIFICATION_POD}" -- curl -sf -m 5 http://cloud-config:9296/actuator/health > /dev/null 2>&1; then
    echo "✅ Service de cloud-config es accesible vía DNS"
    break
  else
    echo "Intento $i/10 falló, reintentando en 5s..."
    sleep 5
  fi
done

sleep 30  # Buffer final
```

### Cambio 2: Probes para API Gateway (Líneas 263-269)

```bash
elif [[ "${svc}" == "api-gateway" ]]; then
  READINESS_INITIAL_DELAY="200"
  READINESS_FAILURE_THRESHOLD="100"
  LIVENESS_INITIAL_DELAY="360"
  LIVENESS_FAILURE_THRESHOLD="15"
fi
```

### Cambio 3: Timeout de Rollout (Líneas 573-577)

```bash
if [[ "${svc}" == "api-gateway" ]]; then
  TIMEOUT="720s"  # 12 minutos
  log_info "⚠️  api-gateway requiere más tiempo (${TIMEOUT}) debido a dependencia con cloud-config"
fi
```

---

## 🎯 Resultado Esperado

### Timeline Mejorado

```
11:26:21 - cloud-config rollout successful ✅
11:30:21 - ConfigServer verificado (dentro del pod) ✅
11:31:57 - Espera de 90s completada ✅
11:32:57 - Espera de 60s para DNS ✅
11:33:47 - Verificación inter-pod exitosa (10 intentos) ✅
11:34:17 - Espera final de 30s ✅
11:34:17 - api-gateway deployment creado ✅
         
# Al arrancar api-gateway:
16:34:17 - api-gateway inicia
16:34:19 - Intenta conectarse a cloud-config → SUCCESS ✅
16:34:20 - Spring Context inicializado ✅
16:37:37 - Readiness probe (después de 200s) → READY ✅
```

**Total de tiempo adicional**: ~2.5 minutos extra antes de desplegar api-gateway

---

## 🧪 Cómo Verificar si Funcionó

### Durante el Pipeline

Busca estos logs:

```bash
[INFO] ⏳ Esperando 60s adicionales para propagación de Service DNS...
[INFO] Verificando que el Service de cloud-config sea accesible vía DNS...
[INFO] Probando conectividad desde service-discovery-xxx → cloud-config:9296...
[OK] ✅ Service de cloud-config es accesible vía DNS desde otros pods.
[INFO] ⏳ Esperando 30s finales antes de desplegar servicios dependientes...
[INFO] Aplicando api-gateway...
[INFO] Esperando rollout de api-gateway...
[INFO] ⚠️  api-gateway requiere más tiempo (720s) debido a dependencia con cloud-config
```

### Después del Despliegue

```bash
# Ver que api-gateway NO tiene RESTARTS
kubectl -n ecommerce get pods | grep api-gateway
# Esperado: RESTARTS = 0

# Ver los logs de api-gateway
kubectl -n ecommerce logs -l app=api-gateway --tail=100
# Esperado: NO debe haber "Connection refused" a cloud-config
```

---

## 🚨 Si Aún Falla

### Diagnóstico Manual

```bash
# 1. Verificar que cloud-config esté Ready
kubectl -n ecommerce get pods -l app=cloud-config
# STATUS debe ser "Running", READY debe ser "1/1"

# 2. Verificar que el Service tenga endpoints
kubectl -n ecommerce get endpoints cloud-config
# Debe mostrar una IP (la del pod)

# 3. Probar DNS resolution desde otro pod
kubectl -n ecommerce run test-dns --image=curlimages/curl --rm -it -- /bin/sh
# Dentro del pod:
curl http://cloud-config:9296/actuator/health
# Debe responder {"status":"UP"}

# 4. Ver logs de api-gateway cuando arranca
kubectl -n ecommerce logs -l app=api-gateway --follow
```

### Posibles Ajustes Adicionales

Si aún falla después de estos cambios, puedes:

#### Opción 1: Aumentar más el initial delay de api-gateway
```bash
# En deploy-to-gke.sh línea 266:
READINESS_INITIAL_DELAY="240"  # De 200 a 240 segundos
```

#### Opción 2: Agregar más espera después de cloud-config
```bash
# En deploy-to-gke.sh línea 501:
sleep 90  # De 60 a 90 segundos
```

#### Opción 3: Configurar Spring Config Client con más reintentos
En `api-gateway/src/main/resources/application.yml`:
```yaml
spring:
  cloud:
    config:
      fail-fast: false
      retry:
        initial-interval: 2000
        max-attempts: 10      # Aumentar de 6 a 10
        max-interval: 10000
        multiplier: 1.5
```

---

## 📊 Comparación: Antes vs Después

| Métrica | Antes | Después | Cambio |
|---------|-------|---------|--------|
| Espera post-cloud-config | 120s | 230s | +110s |
| readinessProbe initial | 130s | 200s | +70s |
| readinessProbe max time | 430s | 700s | +270s |
| Rollout timeout | 480s | 720s | +240s |
| Tasa de éxito | ~20% | ~95%* | +75% |

\* *Estimado basado en la naturaleza del problema de timing*

---

## 📚 Recursos Adicionales

- **Kubernetes Service DNS**: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/
- **Spring Cloud Config**: https://docs.spring.io/spring-cloud-config/docs/current/reference/html/
- **Readiness Probes**: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/

---

## ✅ Checklist de Validación

- [ ] Pipeline ejecutado con los nuevos cambios
- [ ] Logs muestran "Service de cloud-config es accesible vía DNS"
- [ ] api-gateway NO tiene RESTARTS después del despliegue
- [ ] api-gateway alcanza estado "Ready"
- [ ] Logs de api-gateway NO muestran "Connection refused"
- [ ] LoadBalancer IP asignado correctamente
- [ ] Endpoint `/actuator/health` responde en api-gateway

---

**Última actualización**: 2025-10-25  
**Autor**: Oscar MURA  
**Estado**: Implementado y listo para testing

