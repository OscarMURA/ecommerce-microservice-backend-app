# Jenkins Pipelines

Este directorio contiene los pipelines declarativos utilizados para automatizar la construcción y pruebas del backend de microservicios.

## Jenkins_Create_VM (en repositorio de infraestructura)

Este pipeline vive en el repositorio `infra-ecommerce-microservice-backend-app` y se encarga de crear la VM en DigitalOcean. El pipeline `Jenkins_Dev` depende de él para garantizar que exista un ambiente donde ejecutar las pruebas.

## Jenkins_Dev.groovy

Pipeline pensado para un Job de Jenkins que:

1. Verifica que la VM de integración exista (y en caso contrario invoca `Jenkins_Create_VM`).
2. Sincroniza el repositorio `ecommerce-microservice-backend-app` en la VM.
3. Ejecuta pruebas unitarias, de integración y E2E.
4. Descarga los reportes a Jenkins como artefactos.

### Requisitos

- Jenkins 2.401+ (Pipeline + Script Security)
- Plugins: Pipeline, Credentials, Workspace Cleanup, SSH Pipeline Steps (opcional si prefieres `sshCommand`; el pipeline usa `sshpass` por shell).
- Dependencias en el agente Jenkins: `curl`, `jq`, `sshpass`.
- Credenciales:
  - `digitalocean-token`: Secret Text con token de DigitalOcean (mismo que usa `Jenkins_Create_VM`).
  - `integration-vm-password`: Secret Text con la contraseña del usuario `jenkins` de la VM.

### Parámetros del job

| Parámetro | Descripción | Valor por defecto |
|-----------|-------------|-------------------|
| `VM_NAME` | Nombre del droplet creado por `Jenkins_Create_VM` | `ecommerce-integration-runner` |
| `VM_REGION` | Región de la VM (solo para crearla en caso de ausencia) | `nyc3` |
| `VM_SIZE` | Plan de DigitalOcean | `s-1vcpu-2gb` |
| `VM_IMAGE` | Imagen base | `ubuntu-22-04-x64` |
| `JENKINS_CREATE_VM_JOB` | Nombre del job que crea la VM | `Jenkins_Create_VM` |
| `REPO_URL` | URL del repositorio Git a sincronizar | `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git` |
| `APP_BRANCH` | Rama a desplegar y probar | `main` |

### Salida

Se publica el artefacto `reports/test-reports.tar.gz` con los informes de Surefire de los microservicios y pruebas E2E.
