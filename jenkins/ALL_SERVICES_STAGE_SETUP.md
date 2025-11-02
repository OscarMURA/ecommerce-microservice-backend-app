# Pipeline de Despliegue Multi-Servicio a Staging

## 📋 Información del Pipeline

### Nombre del Job
```
All-Services-Stage-Pipeline
```

### Descripción
```
Pipeline automatizado para desplegar múltiples microservicios (user, product, order, shipping, payment, favourite) al ambiente de staging en GKE. Detecta cambios automáticamente en cada servicio y despliega solo los que han sido modificados, o permite forzar el despliegue de servicios específicos mediante parámetros. Incluye health checks automáticos para cada servicio desplegado.
```

### URL del Pipeline en Jenkins
```
http://<JENKINS_URL>/job/All-Services-Stage-Pipeline/
```
*Reemplaza `<JENKINS_URL>` con la URL real de tu servidor Jenkins*

### Ubicación del Archivo
```
/home/oscar/Documents/Taller 2 Ingesoft/ecommerce-microservice-backend-app/jenkins/All-Services-Stage.groovy
```

**Ruta relativa en el repositorio:**
```
jenkins/All-Services-Stage.groovy
```

---

## 🚀 Configuración en Jenkins

### 1. Crear el Pipeline

1. **Accede a Jenkins** y haz clic en "Nueva Tarea" (New Item)

2. **Configura el Job:**
   - Nombre: `All-Services-Stage-Pipeline`
   - Tipo: Pipeline
   - Clic en "OK"

### 2. Configuración General

En la configuración del job:

#### General
- ✅ **GitHub project**: 
  - Project url: `https://github.com/OscarMURA/ecommerce-microservice-backend-app/`

#### Build Triggers
- ✅ **GitHub hook trigger for GITScm polling**

#### Pipeline
- **Definition**: Pipeline script from SCM
- **SCM**: Git
  - **Repository URL**: `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git`
  - **Credentials**: Selecciona tus credenciales de GitHub
  - **Branch Specifier**: `*/staging`
- **Script Path**: `jenkins/All-Services-Stage.groovy`

---

## 🔧 Parámetros del Pipeline

El pipeline incluye los siguientes parámetros configurables:

### Parámetros de Infraestructura

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `DOCKER_IMAGE_TAG` | String | `latest` | Tag de las imágenes en Docker Hub |
| `GKE_CLUSTER_NAME` | String | `ecommerce-dev-gke-v2` | Nombre del cluster GKE |
| `GKE_LOCATION` | String | `us-central1-a` | Zona o región del cluster |
| `K8S_NAMESPACE` | String | `staging` | Namespace de Kubernetes |
| `REPLICA_COUNT` | String | `1` | Número de réplicas por servicio |

### Parámetros de Servicios (Boolean)

| Parámetro | Default | Descripción |
|-----------|---------|-------------|
| `DEPLOY_USER_SERVICE` | `true` | Desplegar user-service (puerto 8085) |
| `DEPLOY_PRODUCT_SERVICE` | `true` | Desplegar product-service (puerto 8083) |
| `DEPLOY_ORDER_SERVICE` | `true` | Desplegar order-service (puerto 8081) |
| `DEPLOY_SHIPPING_SERVICE` | `true` | Desplegar shipping-service (puerto 8084) |
| `DEPLOY_PAYMENT_SERVICE` | `true` | Desplegar payment-service (puerto 8082) |
| `DEPLOY_FAVOURITE_SERVICE` | `true` | Desplegar favourite-service (puerto 8086) |
| `FORCE_DEPLOY_ALL` | `false` | Forzar despliegue de todos los servicios |

---

## 📦 Servicios Incluidos

El pipeline maneja los siguientes microservicios:

| Servicio | Puerto | Imagen Docker |
|----------|--------|---------------|
| user-service | 8085 | `${DOCKER_USER}/user-service:${TAG}` |
| product-service | 8083 | `${DOCKER_USER}/product-service:${TAG}` |
| order-service | 8081 | `${DOCKER_USER}/order-service:${TAG}` |
| shipping-service | 8084 | `${DOCKER_USER}/shipping-service:${TAG}` |
| payment-service | 8082 | `${DOCKER_USER}/payment-service:${TAG}` |
| favourite-service | 8086 | `${DOCKER_USER}/favourite-service:${TAG}` |

---

## 🎯 Funcionalidades Principales

### 1. Detección Automática de Cambios
- ✅ Detecta cambios en cada servicio individualmente
- ✅ Detecta cambios en archivos compartidos (`pom.xml`, `jenkins/`, `.github/`)
- ✅ Despliega solo los servicios que han cambiado
- ✅ Opción de forzar despliegue de servicios específicos

### 2. Validación de Rama
- ✅ Solo se ejecuta en la rama `staging`
- ✅ Valida la rama antes de iniciar el despliegue

### 3. Despliegue en GKE
- ✅ Autenticación automática con GCP
- ✅ Configuración de kubectl
- ✅ Creación de namespace si no existe
- ✅ Despliegue de Deployment y Service para cada microservicio
- ✅ Configuración de probes (liveness y readiness)
- ✅ Límites de recursos configurados

### 4. Health Checks
- ✅ Verifica el endpoint `/actuator/health` de cada servicio
- ✅ Reintentos automáticos (hasta 12 intentos)
- ✅ Muestra logs en caso de fallo
- ✅ Resumen consolidado de todos los health checks

### 5. Resumen Final
- ✅ Muestra todos los deployments desplegados
- ✅ Lista todos los services creados
- ✅ Muestra estado de todos los pods
- ✅ Etiquetas especiales (`deployed-by: all-services-pipeline`)

### 6. Integración con GitHub
- ✅ Actualiza el estado del commit en GitHub
- ✅ Contexto: `ci/jenkins/all-services-stage`

---

## 🔐 Credenciales Requeridas

Asegúrate de tener las siguientes credenciales configuradas en Jenkins:

1. **`gcp-project-id`** (Secret text)
   - ID del proyecto GCP

2. **`gcp-service-account`** (Secret file)
   - Archivo JSON de la cuenta de servicio GCP

3. **`docker-user`** (Secret text)
   - Usuario de Docker Hub

4. **`github-token`** (Secret text)
   - Token de GitHub para actualizar estados

---

## 💻 Uso del Pipeline

### Modo Automático (Detección de Cambios)

1. **Push a la rama staging:**
   ```bash
   git checkout staging
   git add .
   git commit -m "feat: actualización de servicios"
   git push origin staging
   ```

2. El pipeline se ejecutará automáticamente y:
   - Detectará qué servicios cambiaron
   - Desplegará solo los servicios modificados
   - Ejecutará health checks

### Modo Manual (Forzar Despliegue)

1. **Accede al job en Jenkins**

2. **Clic en "Build with Parameters"**

3. **Selecciona los servicios a desplegar:**
   - Marca/desmarca los checkboxes según necesites
   - Para forzar despliegue: marca `FORCE_DEPLOY_ALL`

4. **Configura parámetros si es necesario:**
   - Cambia el tag de Docker
   - Ajusta número de réplicas
   - Modifica configuración de GKE

5. **Clic en "Build"**

---

## 📊 Ejemplo de Ejecución

### Escenario 1: Cambios en user-service y payment-service

```
🔍 Detectando cambios en servicios...
✅ Cambios detectados en user-service
✅ Cambios detectados en payment-service
🚀 Servicios a desplegar: user-service,payment-service

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 Desplegando user-service
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ user-service desplegado exitosamente en staging

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 Desplegando payment-service
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ payment-service desplegado exitosamente en staging

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🏥 Verificando salud de todos los servicios
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ user-service está UP en staging
✅ payment-service está UP en staging

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 RESUMEN DE HEALTH CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ user-service: SUCCESS
✅ payment-service: SUCCESS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Escenario 2: Despliegue Forzado de Todos los Servicios

Usando el parámetro `FORCE_DEPLOY_ALL=true`:

```
🚀 Servicios a desplegar: user-service,product-service,order-service,shipping-service,payment-service,favourite-service

[Despliega todos los servicios seleccionados uno por uno]

📊 RESUMEN DE HEALTH CHECKS:
✅ user-service: SUCCESS
✅ product-service: SUCCESS
✅ order-service: SUCCESS
✅ shipping-service: SUCCESS
✅ payment-service: SUCCESS
✅ favourite-service: SUCCESS
```

---

## 🔍 Troubleshooting

### El pipeline se omite automáticamente

**Causa:** No se detectaron cambios en ningún servicio seleccionado

**Solución:**
- Marca `FORCE_DEPLOY_ALL` para forzar el despliegue
- O asegúrate de que hay cambios en los servicios

### Health check falla

**Causa:** El servicio no responde en el endpoint `/actuator/health`

**Solución:**
1. Verifica los logs del pod:
   ```bash
   kubectl logs -n staging deployment/<service-name> --tail=100
   ```
2. Verifica que la imagen Docker sea correcta
3. Verifica que el puerto esté configurado correctamente

### Error de autenticación con GCP

**Causa:** Credenciales de GCP incorrectas o expiradas

**Solución:**
1. Verifica que la credencial `gcp-service-account` esté actualizada
2. Verifica que la cuenta de servicio tenga permisos en GKE

### Timeout en deployment

**Causa:** El deployment tarda más de 5 minutos en estar disponible

**Solución:**
1. Verifica recursos del cluster
2. Revisa los logs del pod
3. Verifica la imagen Docker

---

## 🎨 Características Avanzadas

### Etiquetas Kubernetes

Todos los recursos desplegados incluyen las siguientes etiquetas:

```yaml
labels:
  app: <service-name>
  environment: staging
  deployed-by: all-services-pipeline
```

Esto permite filtrar recursos fácilmente:

```bash
# Ver solo recursos desplegados por este pipeline
kubectl get all -n staging -l deployed-by=all-services-pipeline

# Ver solo user-service
kubectl get all -n staging -l app=user-service
```

### Variables de Entorno

Cada contenedor incluye:

```yaml
env:
  - name: SERVER_PORT
    value: "<service-port>"
  - name: SPRING_PROFILES_ACTIVE
    value: "staging"
  - name: SPRING_CLOUD_CONFIG_ENABLED
    value: "false"
  - name: EUREKA_CLIENT_ENABLED
    value: "false"
```

### Recursos Configurados

```yaml
resources:
  requests:
    cpu: 200m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 1Gi
```

---

## 📚 Referencias

- Pipeline individual de servicios: `<service-name>/jenkins/<service-name>-stage.groovy`
- Documentación de pipelines: `jenkins/README.md`
- Documentación de GKE: [Google Kubernetes Engine](https://cloud.google.com/kubernetes-engine)

---

## ✅ Checklist de Configuración

- [ ] Pipeline creado en Jenkins con el nombre correcto
- [ ] Configurado para usar la rama `staging`
- [ ] Credenciales de GCP configuradas
- [ ] Credenciales de Docker Hub configuradas
- [ ] Credenciales de GitHub configuradas
- [ ] GitHub webhook configurado (opcional, para triggers automáticos)
- [ ] Cluster GKE accesible desde Jenkins
- [ ] `gcloud` y `kubectl` instalados en el agente Jenkins
- [ ] Probado con un despliegue manual

---

## 📞 Soporte

Para problemas o preguntas:
1. Revisa los logs del pipeline en Jenkins
2. Verifica el estado de los pods en GKE
3. Consulta la documentación de cada servicio individual

---

**Última actualización:** 2025-11-02
**Versión del Pipeline:** 1.0
**Autor:** Sistema de DevOps

