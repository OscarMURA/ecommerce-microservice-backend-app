# 🚀 Jenkins Pipelines para Despliegue en Minikube

Este directorio contiene pipelines de Jenkins para automatizar el despliegue de microservicios de ecommerce en Minikube usando VMs de DigitalOcean.

## 📋 Pipelines Disponibles

### 1. `Jenkins_Create_VM.groovy`
Pipeline para crear y configurar VMs en DigitalOcean con Minikube preinstalado.

**Características:**
- ✅ Crea VMs con Terraform + Ansible
- ✅ Instala Minikube, kubectl y Docker automáticamente
- ✅ Configura acceso a GCP (opcional)
- ✅ Soporte para diferentes configuraciones de VM

### 2. `Jenkins_Deploy_Minikube.groovy`
Pipeline básico para desplegar microservicios en Minikube.

**Características:**
- ✅ Se conecta a VM existente
- ✅ Clona repositorio desde cualquier rama
- ✅ Ejecuta script de despliegue en Minikube
- ✅ Health checks automáticos
- ✅ Archiva logs de despliegue

### 3. `Jenkins_Deploy_Minikube_Develop.groovy` ⭐ **RECOMENDADO**
Pipeline optimizado que **solo se ejecuta en la rama `develop`**.

**Características:**
- ✅ **Filtro de rama**: Solo ejecuta en `develop`
- ✅ **Triggers automáticos**: Se ejecuta con push a `develop`
- ✅ Validación de rama antes del despliegue
- ✅ Todas las características del pipeline básico

## 🛠️ Configuración Requerida

### Credenciales de Jenkins
Asegúrate de tener configuradas estas credenciales en Jenkins:

1. **`digitalocean-token`** (String)
   - Token de API de DigitalOcean
   - Usado para gestionar VMs

2. **`integration-vm-password`** (String)
   - Contraseña de la VM de integración
   - Usado para conexión SSH

3. **`gcp-project-id`** (String) - Opcional
   - ID del proyecto de Google Cloud
   - Solo si usas `CONFIGURE_GCP_ACCESS=true`

4. **`gcp-service-account`** (File) - Opcional
   - Archivo de credenciales de servicio de GCP
   - Solo si usas `CONFIGURE_GCP_ACCESS=true`

### Plugins de Jenkins Requeridos
- GitHub Integration
- Pipeline
- Credentials Binding
- SSH Agent

## 🚀 Flujo de Trabajo Recomendado

### Paso 1: Crear VM con Minikube
```bash
# Ejecutar pipeline Jenkins_Create_VM con:
# - ACTION: create
# - VM_CONFIG: ecommerce_minikube
# - CONFIGURE_GCP_ACCESS: true (opcional)
```

### Paso 2: Desplegar Microservicios
```bash
# Ejecutar pipeline Jenkins_Deploy_Minikube_Develop con:
# - VM_CONFIG: ecommerce_minikube
# - CLEAN_DEPLOYMENT: false (primera vez)
# - RUN_HEALTH_CHECKS: true
# - ARCHIVE_LOGS: true
```

## 📊 Servicios Desplegados

El pipeline despliega los siguientes microservicios:

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| `service-discovery` | 8761 | Eureka Server |
| `zipkin` | 9411 | Distributed Tracing |
| `order-service` | 8081 | Gestión de pedidos |
| `payment-service` | 8082 | Procesamiento de pagos |
| `product-service` | 8083 | Catálogo de productos |
| `shipping-service` | 8084 | Gestión de envíos |
| `user-service` | 8085 | Gestión de usuarios |
| `favourite-service` | 8086 | Productos favoritos |

## 🔧 Parámetros de Configuración

### Jenkins_Deploy_Minikube_Develop

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `VM_CONFIG` | Choice | `ecommerce_minikube` | Configuración de VM |
| `CLEAN_DEPLOYMENT` | Boolean | `false` | Limpiar despliegue anterior |
| `RUN_HEALTH_CHECKS` | Boolean | `true` | Ejecutar health checks |
| `ARCHIVE_LOGS` | Boolean | `true` | Archivar logs como artefactos |

## 🌐 Acceso a Servicios

Después del despliegue exitoso, puedes acceder a los servicios usando:

```bash
# Service Discovery (Eureka Dashboard)
minikube service service-discovery -n ecommerce

# Zipkin (Tracing Dashboard)
minikube service zipkin -n ecommerce
```

## 📋 Health Checks

El pipeline ejecuta health checks automáticos en todos los servicios:

```bash
# Ejemplo de respuesta esperada
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

## 🐛 Solución de Problemas

### VM no encontrada
```
❌ VM 'ecommerce-minikube-dev' no encontrada
```
**Solución**: Ejecuta primero `Jenkins_Create_VM` con `VM_CONFIG=ecommerce_minikube`

### SSH no disponible
```
❌ No fue posible establecer conexión SSH
```
**Solución**: 
- Verifica que la VM esté funcionando
- Espera unos minutos para que cloud-init termine
- Verifica la contraseña en las credenciales

### Minikube no instalado
```
❌ Minikube no está instalado
```
**Solución**: Ejecuta `Jenkins_Create_VM` con `VM_CONFIG=ecommerce_minikube` para instalar Minikube

### Health checks fallan
```
❌ service-name: DOWN
```
**Solución**:
- Revisa los logs del servicio en los artefactos
- Verifica recursos de la VM (memoria/CPU)
- Considera usar `CLEAN_DEPLOYMENT=true`

### Rama incorrecta
```
⚠️ Pipeline configurado para ejecutarse solo en rama 'develop'
```
**Solución**: Usa `Jenkins_Deploy_Minikube.groovy` para otras ramas, o cambia a la rama `develop`

## 📦 Artefactos Generados

El pipeline genera los siguientes artefactos:

- `deployment-logs/` - Logs de todos los servicios
- `health-check-results.json` - Resultados de health checks
- `access-urls.txt` - URLs de acceso a servicios
- `cluster-status.txt` - Estado final del cluster

## 🔄 Automatización con Webhooks

Para automatizar el despliegue con push a `develop`:

1. Configura webhook en GitHub apuntando a Jenkins
2. Usa `Jenkins_Deploy_Minikube_Develop.groovy`
3. El pipeline se ejecutará automáticamente en cada push a `develop`

## 💡 Mejores Prácticas

1. **Siempre usa `Jenkins_Deploy_Minikube_Develop.groovy`** para producción
2. **Ejecuta health checks** para verificar el estado
3. **Archiva logs** para debugging
4. **Usa `CLEAN_DEPLOYMENT=true`** solo cuando sea necesario
5. **Monitorea recursos** de la VM (memoria/CPU)

## 📞 Soporte

Si encuentras problemas:

1. Revisa los logs en los artefactos
2. Verifica que todas las credenciales estén configuradas
3. Asegúrate de que la VM tenga suficientes recursos
4. Consulta la documentación de `minikube-deployment/`

---

**Nota**: Este pipeline está optimizado para el repositorio `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git` y la rama `develop`.
