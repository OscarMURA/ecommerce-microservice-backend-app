# Resumen de Pipelines de Jenkins para Microservicios

## 🎯 Objetivo Completado

Se han creado pipelines individuales de Jenkins para cada microservicio del proyecto ecommerce, permitiendo:

- ✅ Pruebas unitarias e integración por microservicio
- ✅ Construcción y push de imágenes Docker a GCP
- ✅ Despliegue individual en Kubernetes
- ✅ Monitoreo y reportes independientes

## 📁 Estructura Creada

```
ecommerce-microservice-backend-app/
├── api-gateway/
│   └── jenkins/
│       └── api-gateway-dev.groovy
├── cloud-config/
│   └── jenkins/
│       └── cloud-config-dev.groovy
├── favourite-service/
│   └── jenkins/
│       └── favourite-service-dev.groovy
├── order-service/
│   └── jenkins/
│       └── order-service-dev.groovy
├── payment-service/
│   └── jenkins/
│       └── payment-service-dev.groovy
├── product-service/
│   └── jenkins/
│       └── product-service-dev.groovy
├── service-discovery/
│   └── jenkins/
│       └── service-discovery-dev.groovy
├── shipping-service/
│   └── jenkins/
│       └── shipping-service-dev.groovy
├── user-service/
│   └── jenkins/
│       └── user-service-dev.groovy
├── jenkins/
│   ├── scripts/
│   │   ├── deploy-to-gke.sh (existente)
│   │   └── deploy-single-service-to-gke.sh (nuevo)
│   └── README-INDIVIDUAL-PIPELINES.md
├── validate_jenkins_pipelines.sh
├── setup_jenkins_pipelines.sh
└── JENKINS_PIPELINES_SUMMARY.md
```

## 🔧 Características de los Pipelines

### Etapas Implementadas

1. **Validate Branch** - Solo ejecuta en `develop` o `feat/*`
2. **Checkout Pipeline Repo** - Clona el repositorio
3. **Ensure VM Available** - Gestiona la VM de integración
4. **Sync Repository on VM** - Sincroniza código en la VM
5. **Unit Tests** - Ejecuta pruebas unitarias del microservicio
6. **Integration Tests** - Ejecuta pruebas de integración
7. **Recolectar Reportes** - Archiva reportes de pruebas
8. **Build and Push Docker Image** - Construye y sube imagen a GCP (opcional)
9. **Deploy to Kubernetes** - Despliega en GKE (opcional)

### Parámetros Configurables

- **VM_NAME** - Nombre de la VM de integración
- **VM_REGION** - Región de DigitalOcean
- **VM_SIZE** - Tamaño de la VM
- **REPO_URL** - URL del repositorio
- **APP_BRANCH** - Rama específica
- **DEPLOY_TO_K8S** - Habilitar despliegue en K8s
- **K8S_ENVIRONMENT** - Ambiente (dev/staging/prod)
- **K8S_NAMESPACE** - Namespace de Kubernetes
- **GKE_CLUSTER_NAME** - Nombre del cluster GKE
- **K8S_IMAGE_REGISTRY** - Registro de contenedores
- **K8S_IMAGE_TAG** - Tag de la imagen

## 🚀 Scripts de Utilidad

### 1. `validate_jenkins_pipelines.sh`
Valida que todos los pipelines estén correctamente configurados.

```bash
./validate_jenkins_pipelines.sh
```

### 2. `setup_jenkins_pipelines.sh`
Configura automáticamente los pipelines en Jenkins.

```bash
# Configuración automática
JENKINS_URL=http://tu-jenkins.com JENKINS_PASSWORD=tu-password ./setup_jenkins_pipelines.sh

# Ver ayuda
./setup_jenkins_pipelines.sh --help
```

### 3. `deploy-single-service-to-gke.sh`
Script especializado para desplegar un solo servicio en GKE.

## 📋 Configuración en Jenkins

### Pipelines a Crear

Para cada microservicio, crear un **Multibranch Pipeline**:

| Servicio | Nombre del Pipeline | Script Path |
|----------|-------------------|-------------|
| api-gateway | `api-gateway-dev` | `api-gateway/jenkins/api-gateway-dev.groovy` |
| cloud-config | `cloud-config-dev` | `cloud-config/jenkins/cloud-config-dev.groovy` |
| favourite-service | `favourite-service-dev` | `favourite-service/jenkins/favourite-service-dev.groovy` |
| order-service | `order-service-dev` | `order-service/jenkins/order-service-dev.groovy` |
| payment-service | `payment-service-dev` | `payment-service/jenkins/payment-service-dev.groovy` |
| product-service | `product-service-dev` | `product-service/jenkins/product-service-dev.groovy` |
| service-discovery | `service-discovery-dev` | `service-discovery/jenkins/service-discovery-dev.groovy` |
| shipping-service | `shipping-service-dev` | `shipping-service/jenkins/shipping-service-dev.groovy` |
| user-service | `user-service-dev` | `user-service/jenkins/user-service-dev.groovy` |

### Configuración de Branch Sources

- **Repository URL**: `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git`
- **Credentials**: `github-token`
- **Behaviors**:
  - Add: `Filter by name (with wildcards)`
  - Include: `develop, feat/*`

### Credenciales Requeridas

| ID | Tipo | Descripción |
|----|------|-------------|
| `digitalocean-token` | Secret text | Token de DigitalOcean para gestión de VMs |
| `integration-vm-password` | Secret text | Contraseña de la VM de integración |
| `gcp-project-id` | Secret text | ID del proyecto GCP |
| `gcp-service-account` | Secret file | Archivo de credenciales de GCP |
| `github-token` | Secret text | Token de GitHub para acceso al repositorio |

## 🔄 Flujo de Trabajo

### Desarrollo Normal

1. **Desarrollador** hace cambios en un microservicio
2. **Push** a rama `feat/nueva-funcionalidad`
3. **Pipeline** se ejecuta automáticamente:
   - Ejecuta pruebas unitarias e integración
   - Genera reportes
   - (Opcional) Construye y sube imagen Docker
   - (Opcional) Despliega en Kubernetes

### Merge a Develop

1. **Pull Request** de `feat/*` a `develop`
2. **Pipeline** se ejecuta automáticamente
3. **Merge** después de validación exitosa

### Despliegue en Producción

1. **Pipeline** con `DEPLOY_TO_K8S=true`
2. **Construcción** de imagen Docker
3. **Push** a GCP Container Registry
4. **Despliegue** en GKE

## 📊 Monitoreo y Reportes

### Logs Disponibles

- **Console Output** - Logs completos del pipeline
- **Test Results** - Resultados de pruebas
- **Artifacts** - Reportes archivados

### Reportes Archivados

- `reports/test-reports-{microservicio}.tar.gz`

### Estado en Kubernetes

```bash
# Ver pods del servicio
kubectl get pods -n ecommerce -l app={microservicio}

# Ver servicios
kubectl get services -n ecommerce -l app={microservicio}

# Ver logs
kubectl logs -n ecommerce -l app={microservicio}
```

## 🛠️ Mantenimiento

### Actualización de Pipelines

1. Editar archivo `.groovy` correspondiente
2. Commit y push a `develop`
3. Pipeline se ejecuta automáticamente

### Adición de Nuevos Microservicios

1. Crear directorio `{microservicio}/jenkins/`
2. Copiar pipeline existente como plantilla
3. Actualizar nombre del servicio
4. Crear pipeline multibranch en Jenkins

### Troubleshooting

Ver documentación completa en: `jenkins/README-INDIVIDUAL-PIPELINES.md`

## ✅ Validación

Ejecutar el script de validación para verificar configuración:

```bash
./validate_jenkins_pipelines.sh
```

## 🎉 Beneficios Obtenidos

1. **Desarrollo Independiente** - Cada microservicio tiene su propio pipeline
2. **Pruebas Específicas** - Solo se ejecutan pruebas del microservicio modificado
3. **Despliegue Selectivo** - Posibilidad de desplegar solo servicios específicos
4. **Monitoreo Granular** - Reportes y logs específicos por servicio
5. **Escalabilidad** - Fácil adición de nuevos microservicios
6. **Mantenibilidad** - Pipelines independientes y modulares

## 📞 Soporte

Para soporte o preguntas sobre estos pipelines, contactar al equipo de DevOps o revisar la documentación en `jenkins/README-INDIVIDUAL-PIPELINES.md`.
