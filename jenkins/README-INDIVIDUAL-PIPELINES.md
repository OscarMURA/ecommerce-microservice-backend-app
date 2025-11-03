# Pipelines Individuales de Microservicios

Este directorio contiene pipelines de Jenkins individuales para cada microservicio del proyecto ecommerce.

## Estructura

Cada microservicio tiene su propio directorio `jenkins/` con un pipeline específico:

```
microservicio/
└── jenkins/
    └── microservicio-dev.groovy
```

## Microservicios con Pipelines

- **user-service** - Gestión de usuarios
- **product-service** - Catálogo de productos  
- **api-gateway** - Gateway de API
- **cloud-config** - Configuración centralizada
- **favourite-service** - Favoritos de usuarios
- **order-service** - Gestión de pedidos
- **payment-service** - Procesamiento de pagos
- **service-discovery** - Descubrimiento de servicios
- **shipping-service** - Gestión de envíos

## Características de los Pipelines

### Etapas del Pipeline

1. **Validate Branch** - Valida que se ejecute solo en ramas `develop` o `feat/*`
2. **Checkout Pipeline Repo** - Clona el repositorio del pipeline
3. **Ensure VM Available** - Asegura que la VM de integración esté disponible
4. **Sync Repository on VM** - Sincroniza el código en la VM
5. **Unit Tests** - Ejecuta pruebas unitarias del microservicio
6. **Integration Tests** - Ejecuta pruebas de integración del microservicio
7. **Recolectar Reportes** - Recopila y archiva reportes de pruebas
8. **Build and Push Docker Image** - Construye y sube imagen Docker a GCP (opcional)
9. **Deploy to Kubernetes** - Despliega el servicio en GKE (opcional)

### Parámetros Configurables

- **VM_NAME** - Nombre de la VM de integración
- **VM_REGION** - Región de DigitalOcean
- **VM_SIZE** - Tamaño de la VM
- **VM_IMAGE** - Imagen base de la VM
- **REPO_URL** - URL del repositorio
- **APP_BRANCH** - Rama específica a usar
- **DEPLOY_TO_K8S** - Habilitar despliegue en Kubernetes
- **K8S_ENVIRONMENT** - Ambiente (dev/staging/prod)
- **K8S_NAMESPACE** - Namespace de Kubernetes
- **GKE_CLUSTER_NAME** - Nombre del cluster GKE
- **GKE_LOCATION** - Ubicación del cluster
- **K8S_IMAGE_REGISTRY** - Registro de contenedores
- **K8S_IMAGE_TAG** - Tag de la imagen
- **INFRA_REPO_URL** - Repositorio de infraestructura
- **INFRA_REPO_BRANCH** - Rama de infraestructura

## Configuración en Jenkins

### 1. Crear Pipeline Multibranch

Para cada microservicio, crear un pipeline multibranch:

1. Ir a **New Item** → **Multibranch Pipeline**
2. Nombre: `{microservicio}-dev`
3. Branch Sources: Git
4. Repository URL: `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git`
5. Behaviors: 
   - Add: `Filter by name (with wildcards)`
   - Include: `develop, feat/*`
6. Build Configuration:
   - Mode: `by Jenkinsfile`
   - Script Path: `{microservicio}/jenkins/{microservicio}-dev.groovy`

### 2. Credenciales Requeridas

Asegurar que estén configuradas las siguientes credenciales:

- `digitalocean-token` - Token de DigitalOcean
- `integration-vm-password` - Contraseña de la VM de integración
- `gcp-project-id` - ID del proyecto GCP
- `gcp-service-account` - Archivo de credenciales de GCP
- `github-token` - Token de GitHub

### 3. Configuración de la VM

La VM debe tener instalado:
- Java 11+
- Maven
- Docker
- Google Cloud SDK
- Python 3
- Git

## Uso

### Ejecución Automática

Los pipelines se ejecutan automáticamente cuando:
- Se hace push a la rama `develop`
- Se hace push a una rama que comience con `feat/`

### Ejecución Manual

1. Ir al pipeline del microservicio en Jenkins
2. Hacer clic en **Build with Parameters**
3. Configurar los parámetros según sea necesario
4. Hacer clic en **Build**

### Despliegue en Kubernetes

Para desplegar en Kubernetes:

1. Marcar `DEPLOY_TO_K8S` como `true`
2. Configurar los parámetros de Kubernetes
3. Ejecutar el pipeline

## Monitoreo

### Logs

Los logs están disponibles en la interfaz de Jenkins:
- **Console Output** - Logs completos del pipeline
- **Test Results** - Resultados de las pruebas
- **Artifacts** - Reportes de pruebas archivados

### Reportes

Los reportes de pruebas se archivan como:
- `reports/test-reports-{microservicio}.tar.gz`

### Estado del Despliegue

Para verificar el estado del despliegue en Kubernetes:

```bash
# Ver pods del servicio
kubectl get pods -n ecommerce -l app={microservicio}

# Ver servicios
kubectl get services -n ecommerce -l app={microservicio}

# Ver logs
kubectl logs -n ecommerce -l app={microservicio}
```

## Troubleshooting

### Problemas Comunes

1. **VM no disponible**
   - Verificar que la VM esté corriendo en DigitalOcean
   - Revisar las credenciales de DigitalOcean

2. **Fallos en pruebas**
   - Revisar los logs de las pruebas
   - Verificar que las dependencias estén disponibles

3. **Error en construcción de Docker**
   - Verificar que el Dockerfile existe
   - Revisar las credenciales de GCP

4. **Error en despliegue de Kubernetes**
   - Verificar que el cluster GKE esté disponible
   - Revisar los manifiestos de Kubernetes

### Logs de Debug

Para obtener más información de debug:

1. Habilitar **Timestamps** en las opciones del pipeline
2. Revisar los logs de la VM de integración
3. Verificar los logs de Kubernetes

## Mantenimiento

### Actualización de Pipelines

Para actualizar un pipeline:

1. Editar el archivo `.groovy` correspondiente
2. Hacer commit y push a la rama `develop`
3. El pipeline se ejecutará automáticamente

### Adición de Nuevos Microservicios

Para agregar un nuevo microservicio:

1. Crear el directorio `{microservicio}/jenkins/`
2. Copiar un pipeline existente como plantilla
3. Actualizar el nombre del servicio en el pipeline
4. Crear el pipeline multibranch en Jenkins

## Contacto

Para soporte o preguntas sobre estos pipelines, contactar al equipo de DevOps.
