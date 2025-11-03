# Fix: cloud-config Bucle Infinito en GKE

## 🔍 Problema Encontrado

`cloud-config` estaba fallando en GKE con **reinic ios infinitos** (6+ restarts) y nunca alcanzaba estado `Ready`, causando que todos los servicios dependientes fallaran.

### Estado del Problema
```
cloud-config-76fd4596dd-xsznr  0/1   Running  6 (3m16s ago)  28m
```

### Síntomas
- Health check fallaba constantemente con "Connection refused"
- Container se reiniciaba cada 2-3 minutos
- Logs mostraban: `Fetching config from server at : http://cloud-config:9296/`
- Todos los servicios dependientes (api-gateway, etc.) también fallaban

## 🐛 Causa Raíz

`cloud-config` estaba intentando conectarse **a sí mismo** para obtener configuración, creando un **bucle infinito (deadlock)**:

### Flujo del Problema:
1. ✅ Kubernetes inicia el pod `cloud-config`
2. ⏳ Kubernetes hace health check → GET `/actuator/health`
3. 🔄 Spring Boot Actuator intenta inicializar completamente
4. 🔄 Spring Cloud Config **Client** (incluido en `spring-cloud-starter`) intenta conectarse a `http://cloud-config:9296/`
5. ❌ Conexión falla porque `cloud-config` aún no está listo
6. ❌ Health check falla
7. 💀 Kubernetes mata el contenedor
8. 🔁 Vuelve al paso 1 → **Bucle infinito**

### ¿Por qué `cloud-config` tenía Config Client?

En el `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter</artifactId>
</dependency>
```

Esta dependencia incluye **Config Client**, que automáticamente intenta conectarse a un Config Server (incluso si el propio servicio ES el Config Server).

## ✅ Solución Aplicada

**Archivo**: `cloud-config/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    config:
      enabled: false  # ← NUEVO: Deshabilitar Config Client
      server:
        native:
          searchLocations: classpath:/configs
```

### Por qué funciona:
- `cloud-config` **ES** un Config **Server**, no un Config **Client**
- Debe usar solo su configuración local (`application.yml`)
- NO debe intentar obtener configuración de otro servidor
- Con `enabled: false`, el Config Client no se inicializa y no intenta conectarse a sí mismo

## 📊 Comparación: Minikube vs GKE

### ✅ Minikube (Local) - Funcionaba
- **1 nodo**: DNS propagation rápida
- **Recursos limitados**: menos concurrencia en health checks
- **Timing diferente**: el problema podía ocurrir pero menos frecuentemente

### ❌ GKE (Producción) - Fallaba
- **3 nodos**: DNS propagation más lenta
- **Alta concurrencia**: múltiples health checks simultáneos
- **Timing crítico**: el problema se manifestaba consistentemente

## 🚀 Próximos Pasos

### 1. Ejecutar Pipeline de Jenkins
```bash
Jenkins → Deploy_K8k_Dev_develop → "Build with Parameters" → Build
```

Esto construirá la nueva imagen con el fix incluido.

### 2. Monitorear el Despliegue
```bash
# Cambiar a contexto GKE
export USE_GKE_GCLOUD_AUTH_PLUGIN=True
gcloud auth activate-service-account --key-file=/home/oscar/Downloads/devops-activity-a05cd08d9974.json
gcloud config set project devops-activity
gcloud container clusters get-credentials ecommerce-dev-gke-v2 --zone=us-central1-a

# Ver estado de pods
kubectl -n ecommerce get pods -w

# Verificar que cloud-config NO tenga restarts
kubectl -n ecommerce get pods | grep cloud-config
# Esperado: 
# cloud-config-xxxxx  1/1  Running  0  5m
#                                   ^ DEBE SER 0
```

### 3. Verificar Health Check
```bash
# Una vez que cloud-config esté Ready
kubectl -n ecommerce exec -it deployment/cloud-config -- curl http://localhost:9296/actuator/health

# Esperado:
# {"status":"UP", ...}
```

### 4. Verificar Logs Limpios
```bash
kubectl -n ecommerce logs -f deployment/cloud-config

# NO debe aparecer:
# "Fetching config from server at : http://cloud-config:9296/"
```

## 🎯 Resultado Esperado

Después del fix:
```
NAME                             READY   STATUS    RESTARTS   AGE
service-discovery-xxxxx           1/1    Running    0         8m
cloud-config-xxxxx                1/1    Running    0         6m  ← 0 RESTARTS
api-gateway-xxxxx                 1/1    Running    0         4m
user-service-xxxxx                1/1    Running    0         3m
...todos los demás servicios...   1/1    Running    0         3m
```

## 📚 Lecciones Aprendidas

1. **Config Server ≠ Config Client**: Un servicio que actúa como Config Server NO debe tener Config Client habilitado.

2. **Health Checks pueden desencadenar inicializaciones**: Los endpoints de Actuator pueden causar que Spring intente inicializar componentes completos.

3. **Diferencias de entorno importan**: Lo que funciona en Minikube puede comportarse diferente en GKE debido a:
   - Networking (multi-nodo vs single-nodo)
   - Timing de DNS propagation
   - Concurrencia en health checks

4. **Logs exhaustivos ayudan**: Ver los logs completos del contenedor anterior (`--previous`) fue clave para identificar el bucle.

## 🔗 Referencias

- Commit: `829f634`
- Branch: `develop`
- Servicios afectados: Todos (porque cloud-config es crítico)
- Tiempo de diagnóstico: ~30 minutos
- Gravedad: **CRÍTICA** (bloqueaba todo el despliegue)

---

**Autor**: AI Assistant  
**Fecha**: 2025-10-25  
**Status**: ✅ FIXED - Pendiente validación en GKE

