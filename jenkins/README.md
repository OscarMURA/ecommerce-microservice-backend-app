# Jenkins Pipelines

Este directorio contiene los pipelines declarativos utilizados para automatizar la construcción y pruebas del backend de microservicios.

## Jenkins_Create_VM (en repositorio de infraestructura)

Este pipeline vive en el repositorio `infra-ecommerce-microservice-backend-app` y se encarga de crear la VM en DigitalOcean. El pipeline `Jenkins_Dev` depende de él para garantizar que exista un ambiente donde ejecutar las pruebas.

### Parámetros relevantes

| Parámetro | Descripción | Valor por defecto |
|-----------|-------------|-------------------|
| `ACTION` | `create`, `rebuild`, `destroy` o `status` | `create` |
| `CONFIGURE_GCP_ACCESS` | Si está en `true`, copia la credencial `gcp-service-account` a la VM recién creada, ejecuta `gcloud auth activate-service-account`, fija el proyecto (`gcp-project-id`) y deja configurados los registros `us-docker.pkg.dev` y `gcr.io` para Docker | `true` |

> Para que esta etapa funcione necesitas, además de `digitalocean-token` y `integration-vm-password`, las credenciales `gcp-project-id` (Secret Text) y `gcp-service-account` (Secret file) dadas de alta en Jenkins. Si prefieres gestionar las credenciales manualmente en la VM, ejecuta el job con `CONFIGURE_GCP_ACCESS=false`.

## Jenkins_Dev.groovy

Pipeline pensado para un Job de Jenkins que:

1. Verifica que la VM de integración exista (y en caso contrario invoca `Jenkins_Create_VM`).
2. Sincroniza el repositorio `ecommerce-microservice-backend-app` en la VM.
3. Ejecuta pruebas unitarias, de integración y E2E.
4. Descarga los reportes a Jenkins como artefactos.
5. (Opcional) Despliega los microservicios seleccionados en Kubernetes (GKE) y reporta su salud.

### Requisitos

- Jenkins 2.401+ (Pipeline + Script Security)
- Plugins: Pipeline, Credentials, Workspace Cleanup, SSH Pipeline Steps (opcional si prefieres `sshCommand`; el pipeline usa `sshpass` por shell).
- Dependencias en el agente Jenkins: `curl`, `jq`, `sshpass`, `kubectl`, `gcloud` (incluyendo el plugin `gke-gcloud-auth-plugin`).
- La VM creada por `Jenkins_Create_VM` instala Docker y OpenJDK 17 automáticamente mediante cloud-init.
- Credenciales:
  - `digitalocean-token`: Secret Text con token de DigitalOcean (mismo que usa `Jenkins_Create_VM`).
  - `integration-vm-password`: Secret Text con la contraseña del usuario `jenkins` de la VM.
- `gcp-project-id`: Secret Text con el ID del proyecto de GCP donde vive el cluster.
- `gcp-service-account`: Credential tipo “Secret file” con la key JSON de una service account con permisos en GKE **y** Artifact/Container Registry (`roles/artifactregistry.writer` o `roles/storage.objectAdmin` según el registro).

### Parámetros del job

| Parámetro | Descripción | Valor por defecto |
|-----------|-------------|-------------------|
| `VM_NAME` | Nombre del droplet creado por `Jenkins_Create_VM` | `ecommerce-integration-runner` |
| `VM_REGION` | Región de la VM (solo para crearla en caso de ausencia) | `nyc3` |
| `VM_SIZE` | Plan de DigitalOcean | `s-1vcpu-2gb` |
| `VM_IMAGE` | Imagen base | `ubuntu-22-04-x64` |
| `JENKINS_CREATE_VM_JOB` | Nombre base del job que crea la VM (para multibranch deja solo el job y usa `VM_JOB_BRANCH_HINTS`) | `Jenkins_Create_VM` |
| `VM_JOB_BRANCH_HINTS` | Sufijos (comma separated) que se probarán si el job es multibranch | `main,master,infra/main,infra/master` |
| `VM_JOB_EXTRA_PATHS` | Rutas completas adicionales a intentar antes de los sufijos (separadas por comas) | `` |
| `REPO_URL` | URL del repositorio Git a sincronizar | `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git` |
| `APP_BRANCH` | Rama a desplegar y probar (`""` usa la rama del pipeline) | `""` |
| `DEPLOY_TO_K8S` | Ejecuta (true) o no (false) el despliegue en Kubernetes al finalizar las pruebas | `false` |
| `K8S_ENVIRONMENT` | Ambiente objetivo usado en labels y perfiles (`dev`, `staging`, `prod`) | `dev` |
| `K8S_NAMESPACE` | Namespace destino dentro del cluster | `ecommerce` |
| `K8S_SERVICES` | Lista separada por espacios o comas de microservicios a desplegar | `cloud-config service-discovery api-gateway proxy-client user-service product-service favourite-service order-service shipping-service payment-service` |
| `GKE_CLUSTER_NAME` | Nombre del cluster de Google Kubernetes Engine | `ecommerce-dev-gke-v2` |
| `GKE_LOCATION` | Zona o región donde corre el cluster (p. ej. `us-central1-a`) | `us-central1-a` |
| `K8S_IMAGE_REGISTRY` | Prefijo del registro donde se publican las imágenes Docker | `us-docker.pkg.dev/devops-activity/app-images` |
| `K8S_IMAGE_TAG` | Tag de imagen a desplegar (vacío usa `GIT_COMMIT[0..6]`) | `` |
| `INFRA_REPO_URL` | Repositorio Git con manifiestos/base de infraestructura | `https://github.com/OscarMURA/infra-ecommerce-microservice-backend-app.git` |
| `INFRA_REPO_BRANCH` | Rama del repositorio de infraestructura | `infra/master` |

#### Parámetros para Kubernetes

- `DEPLOY_TO_K8S` en `true` habilita una etapa adicional que:
  1. Clona el repositorio de infraestructura indicado.
  2. Configura credenciales contra GKE con `gcp-project-id` y `gcp-service-account`.
  3. Renderiza y aplica manifiestos por servicio, actualiza `ConfigMap`/`Secret` base y espera a que los `Deployment` reporten `Available`.
  4. Muestra un resumen (`kubectl get deployments|services|pods`) y, para servicios `LoadBalancer`, espera su IP pública (por defecto solo `api-gateway`).
- `K8S_SERVICES` debe incluir las dependencias (`cloud-config`, `service-discovery`). Si se omiten, el pipeline las agrega automáticamente.
- `K8S_IMAGE_TAG` vacío deriva el tag del commit (`GIT_COMMIT.take(7)`), útil si las imágenes se publican con ese mismo tag durante el pipeline.
- Si utilizas Artifact Registry, crea primero el repositorio (por ejemplo `gcloud artifacts repositories create app-images --repository-format=docker --location=us`) y ejecuta `gcloud auth configure-docker us-docker.pkg.dev`. Para Container Registry clásico (`gcr.io/...`), habilita la API “Container Registry” y otorga a la service account permisos sobre el bucket `artifacts.<PROJECT_ID>.appspot.com`.

### Salida

Se publica el artefacto `reports/test-reports.tar.gz` con los informes de Surefire de los microservicios y pruebas E2E.
