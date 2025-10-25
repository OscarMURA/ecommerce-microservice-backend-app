# 🏗️ Arquitectura de Infraestructura

Este documento describe la arquitectura de CI/CD utilizada para construir y desplegar la aplicación de e-commerce.

## 📊 Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                        FLUJO DE DESPLIEGUE                       │
└─────────────────────────────────────────────────────────────────┘

    ┌─────────────────┐
    │   Developer     │
    │   git push      │
    └────────┬────────┘
             │
             ↓
    ┌─────────────────┐
    │  GitHub Repo    │  Branch: develop
    │  OscarMURA/...  │  Webhook → Jenkins
    └────────┬────────┘
             │
             ↓ (Auto-trigger)
┏━━━━━━━━━━━━━━━━━━━━━┓
┃   Jenkins Server     ┃  🎯 Orquestador
┃   (Tu servidor)      ┃
┃                      ┃  Tareas:
┃  Componentes:        ┃  1. Checkout código
┃  - Pipeline Engine   ┃  2. SSH a VM para builds
┃  - kubectl           ┃  3. Deploy a Kubernetes
┃  - gcloud CLI        ┃  4. Verificación de salud
┃                      ┃
┃  Credenciales:       ┃  NO construye imágenes
┃  - GitHub token      ┃  (delega a VM)
┃  - GCP service acc.  ┃
┃  - DO token          ┃
┃  - VM SSH password   ┃
┗━━━━━━━━┯━━━━━━━━━━━┛
          │
          │ SSH (sshpass)
          │ User: jenkins@174.138.48.59
          │
          ↓
┏━━━━━━━━━━━━━━━━━━━━━┓
┃  DigitalOcean VM     ┃  🔨 Build Runner
┃  ecommerce-...       ┃
┃  174.138.48.59       ┃  Tareas:
┃                      ┃  1. Clonar/actualizar repo
┃  Recursos:           ┃  2. Construir imágenes Docker
┃  - RAM: 3.6GB        ┃  3. Push a Artifact Registry
┃  - CPU: Compartida   ┃
┃  - Disk: 25GB SSD    ┃  Optimizaciones:
┃                      ┃  - Cache de Maven (.m2/)
┃  Software:           ┃  - Layers de Docker cacheadas
┃  - Docker 28.4.0     ┃  - Builds incrementales
┃  - gcloud SDK        ┃
┃  - Git               ┃  Ubicación del código:
┃  - Maven 3.8+        ┃  /opt/ecommerce-app/backend
┃                      ┃
┃  Credenciales GCP:   ┃  Autenticación:
┃  ~/.config/gcloud/   ┃  - Service account configurada
┃  service-account.json┃  - Docker helper para GCR
┗━━━━━━━━┯━━━━━━━━━━━┛
          │
          │ docker push
          │ us-docker.pkg.dev/devops-activity/app-images
          │
          ↓
┏━━━━━━━━━━━━━━━━━━━━━┓
┃ GCP Artifact Registry┃  📦 Registro de Imágenes
┃                      ┃
┃ Proyecto:            ┃  Imágenes:
┃ devops-activity      ┃  - cloud-config:TAG
┃                      ┃  - service-discovery:TAG
┃ Registry:            ┃  - api-gateway:TAG
┃ us-docker.pkg.dev/   ┃  - proxy-client:TAG
┃ app-images/          ┃  - user-service:TAG
┃                      ┃  - product-service:TAG
┃ Versionado:          ┃  - favourite-service:TAG
┃ TAG = commit hash    ┃  - order-service:TAG
┃ (primeros 7 chars)   ┃  - shipping-service:TAG
┃                      ┃  - payment-service:TAG
┃ Retención:           ┃
┃ - latest: Always     ┃  Total: 10 servicios
┃ - tags: 30 días      ┃
┗━━━━━━━━┯━━━━━━━━━━━┛
          │
          │ imagePullSecrets: docker-registry-secret
          │
          ↓
┏━━━━━━━━━━━━━━━━━━━━━┓
┃   GKE Cluster        ┃  ☸️ Kubernetes Runtime
┃ ecommerce-dev-gke-v2 ┃
┃                      ┃  Configuración:
┃ Región:              ┃  - Region: us-central1-a
┃ us-central1-a        ┃  - K8s Version: 1.28+
┃                      ┃  - Nodos: 3x e2-medium
┃ Nodos (3):           ┃
┃ e2-medium            ┃  Por nodo:
┃ - 2 vCPU             ┃  - CPU: 2 vCPU
┃ - 4 GB RAM           ┃  - RAM: 4 GB
┃ - 100 GB disk        ┃  - Disk: 100 GB
┃                      ┃
┃ Namespace:           ┃  Deployments:
┃ ecommerce            ┃  - service-discovery (1 replica)
┃                      ┃  - cloud-config (1 replica)
┃ Servicios:           ┃  - api-gateway (2 replicas) ← LoadBalancer
┃ - ClusterIP: 8       ┃  - proxy-client (1 replica)
┃ - LoadBalancer: 1    ┃  - 6 business services (1 replica cada uno)
┃                      ┃
┃ Probes:              ┃  Health Checks:
┃ - Readiness: Custom  ┃  - /actuator/health
┃ - Liveness: Custom   ┃  - initialDelay: 130-180s
┃                      ┃  - timeout: 480-600s
┗━━━━━━━━━━━━━━━━━━━━━┛
```

---

## 🔄 Flujo de CI/CD Detallado

### Stage 1: Checkout (Jenkins)
```groovy
1. Git clone del repositorio
2. Checkout branch 'develop'
3. Obtener commit hash → TAG
```

### Stage 2: Get VM IP (Jenkins)
```groovy
1. Llamada API de DigitalOcean
2. Buscar droplet 'ecommerce-integration-runner'
3. Obtener IP pública: 174.138.48.59
```

### Stage 3: Build and Push Images (Jenkins → VM)
```bash
# Jenkins ejecuta vía SSH en la VM:

1. SSH a jenkins@174.138.48.59
2. Actualizar código en /opt/ecommerce-app/backend
   - git fetch origin develop
   - git checkout develop
   - git pull

3. Autenticar con GCP
   - gcloud auth activate-service-account
   - gcloud auth configure-docker us-docker.pkg.dev

4. Construir imágenes (10 servicios)
   FOR each service IN services:
     docker build -t us-docker.pkg.dev/devops-activity/app-images/$service:$TAG \
       -f $service/Dockerfile .
     docker push us-docker.pkg.dev/devops-activity/app-images/$service:$TAG

5. Tiempo total: ~2 minutos (gracias a cache)
```

### Stage 4: Deploy to Kubernetes (Jenkins)
```bash
# Jenkins ejecuta localmente (tiene kubectl):

1. Clonar repo de infraestructura (manifiestos K8s)
2. Autenticar con GKE
   - gcloud auth activate-service-account
   - gcloud container clusters get-credentials

3. Ejecutar script de despliegue
   - Limpiar deployments viejos
   - Aplicar manifiestos base (namespace, configmap, secrets)
   - Desplegar servicios críticos primero (service-discovery, cloud-config)
   - Esperar verificación de ConfigServer
   - Desplegar servicios restantes
   - Verificar rollouts (timeout: 8-10 min por servicio)

4. Tiempo total: ~25-30 minutos
```

---

## 🔑 Gestión de Credenciales

### Jenkins Credentials
```
- github-token:              PAT para clonar repos privados
- digitalocean-token:        API token para obtener IP de VM
- gcp-service-account:       JSON key para autenticar con GCP
- gcp-project-id:            devops-activity
- integration-vm-password:   Password SSH para jenkins@VM
```

### VM Credentials (Pre-configuradas)
```
Location: ~/.config/gcloud/service-account.json
Type: GCP Service Account JSON
Permissions:
- Artifact Registry Writer
- Kubernetes Engine Developer
- Storage Object Viewer
```

### GKE Secrets
```
docker-registry-secret:  Pull images from Artifact Registry
ecommerce-secrets:       Application secrets (DB, JWT, etc.)
```

---

## 📊 Recursos y Límites

### VM de DigitalOcean
```yaml
Tipo: Basic Droplet
RAM: 3.6 GB (ajustado para Minikube con 3GB)
CPU: Shared vCPU
Disk: 25 GB SSD
Network: 3 TB transfer
Costo: ~$18/mes

Uso:
- Pico durante builds: 80-90% CPU, 2.5GB RAM
- Idle: 5-10% CPU, 800MB RAM
```

### GKE Cluster
```yaml
Node Pool: default-pool
Machine Type: e2-medium (2 vCPU, 4 GB RAM)
Nodes: 3 (para alta disponibilidad)
Total Resources:
  CPU: 6 vCPU
  RAM: 12 GB
  Disk: 300 GB

Costo estimado: ~$150/mes
- Nodos: $120/mes
- Load Balancer: $18/mes
- Networking: ~$12/mes
```

### Límites por Pod
```yaml
Servicios críticos (cloud-config, service-discovery):
  requests:
    cpu: 25m
    memory: 128Mi
  limits:
    cpu: 150m
    memory: 256Mi

Otros servicios:
  requests:
    cpu: 15m
    memory: 96Mi
  limits:
    cpu: 100m
    memory: 192Mi
```

---

## 🚀 Ventajas de Esta Arquitectura

### ✅ Separación de Responsabilidades
- **Jenkins**: Solo orquestación (bajo uso de recursos)
- **VM**: Trabajo pesado (builds, compresión de imágenes)
- **GKE**: Solo runtime (sin overhead de CI/CD)

### ✅ Escalabilidad
- Fácil agregar más VMs de build si se necesita paralelismo
- GKE puede escalar horizontalmente (más nodos)
- Pipeline puede ejecutar múltiples jobs simultáneos

### ✅ Costos Optimizados
- VM pequeña es suficiente para builds (más barato que Jenkins grande)
- GKE solo cobra por pods activos, no por builds
- Puedes apagar VM cuando no hay builds activos

### ✅ Seguridad
- Credenciales de GCP aisladas (no todas en Jenkins)
- VM dedicada reduce superficie de ataque
- imagePullSecrets en K8s para control de acceso

### ✅ Mantenibilidad
- Componentes independientes (más fácil actualizar/debuggear)
- Logs centralizados en cada capa
- Rollback fácil (cambiar TAG de imagen)

---

## ⚠️ Puntos de Fallo y Mitigaciones

### VM no disponible
```
Síntoma: SSH connection refused
Mitigación:
1. Pipeline falla en stage "Get VM IP"
2. Verificar que droplet esté running en DigitalOcean
3. Verificar que IP no haya cambiado
4. Verificar firewall permite SSH desde Jenkins
```

### Artifact Registry no accesible
```
Síntoma: docker push fails
Mitigación:
1. Verificar credenciales en VM: gcloud auth list
2. Re-autenticar si es necesario
3. Verificar permisos del service account
4. Verificar que proyecto GCP esté activo
```

### GKE cluster no disponible
```
Síntoma: kubectl commands timeout
Mitigación:
1. Verificar cluster en Cloud Console
2. Verificar nodos están Running
3. Re-generar kubeconfig: gcloud container clusters get-credentials
4. Verificar service account tiene permisos de K8s Engine Admin
```

### Pods no alcanzan Ready
```
Síntoma: Rollout timeout
Mitigación:
1. Revisar logs del pod: kubectl logs <pod>
2. Describir pod: kubectl describe pod <pod>
3. Verificar probe configuration
4. Verificar recursos disponibles: kubectl top nodes
5. Ver eventos: kubectl get events -n ecommerce
```

---

## 🔧 Mantenimiento

### Limpieza de Imágenes Viejas
```bash
# En la VM cada semana:
docker system prune -a --filter "until=168h"  # Limpia > 7 días

# En Artifact Registry (automático con política):
# Retención: Mantener últimas 10 versiones
```

### Actualización de Dependencias
```bash
# VM:
sudo apt update && sudo apt upgrade
gcloud components update
docker --version  # Verificar versión

# Jenkins:
# Actualizar plugins desde UI
# Verificar compatibilidad con Pipeline DSL

# GKE:
# Actualizar cluster desde Cloud Console
# Rolling update, sin downtime
```

### Monitoreo
```bash
# VM:
- htop (recursos en tiempo real)
- docker stats (uso de contenedores)
- df -h (espacio en disco)

# GKE:
- Cloud Monitoring dashboards
- kubectl top nodes/pods
- GKE Workload status en Console

# Jenkins:
- Build history
- Console output de cada stage
- Prometheus metrics (si configurado)
```

---

## 📝 Troubleshooting Rápido

### Build falla en VM
```bash
# SSH a la VM
ssh jenkins@174.138.48.59

# Verificar espacio en disco
df -h

# Limpiar cache de Docker
docker system prune -a

# Verificar Maven cache
du -sh /opt/ecommerce-app/backend/.m2/

# Ver logs de builds
cd /opt/ecommerce-app/backend
git status
git log -1
```

### Deploy falla en GKE
```bash
# Desde Jenkins o local con kubectl configurado
kubectl -n ecommerce get pods
kubectl -n ecommerce describe pod <POD-NAME>
kubectl -n ecommerce logs <POD-NAME>
kubectl -n ecommerce get events --sort-by='.lastTimestamp'

# Ver recursos del cluster
kubectl top nodes
kubectl top pods -n ecommerce

# Verificar imagePullSecret
kubectl -n ecommerce get secret docker-registry-secret
```

### Pipeline se queda colgado
```bash
# En Jenkins
1. Revisar Console Output
2. Identificar stage que se colgó
3. Si es SSH a VM: Verificar que VM responde
4. Si es kubectl: Verificar GKE cluster disponible
5. Abortar build si es necesario (botón rojo)
6. Revisar timeouts en Jenkinsfile
```

---

## 🎯 Mejoras Futuras (Opcionales)

### Corto Plazo
- [ ] Implementar build cache distribuido para Maven
- [ ] Agregar health checks en Jenkins para la VM
- [ ] Automatizar limpieza de imágenes viejas
- [ ] Implementar notificaciones Slack/Discord

### Mediano Plazo
- [ ] Migrar builds a Google Cloud Build (eliminar VM)
- [ ] Implementar GitOps con ArgoCD/Flux
- [ ] Agregar ambiente de staging
- [ ] Implementar rollback automático en fallos

### Largo Plazo
- [ ] Multi-region deployment en GKE
- [ ] Implementar Istio service mesh
- [ ] Agregar observabilidad con Grafana/Prometheus
- [ ] Implementar pruebas E2E en el pipeline

---

## 📞 Contactos y Referencias

- **Repositorio Backend**: https://github.com/OscarMURA/ecommerce-microservice-backend-app
- **Repositorio Infra**: https://github.com/OscarMURA/infra-ecommerce-microservice-backend-app
- **GCP Project**: devops-activity
- **GKE Cluster**: ecommerce-dev-gke-v2
- **VM Name**: ecommerce-integration-runner
- **Jenkins URL**: [Tu URL de Jenkins]

---

## 📚 Documentación Relacionada

- [DEBUGGING_GUIDE.md](./DEBUGGING_GUIDE.md) - Guía de debugging con Minikube
- [jenkins/scripts/deploy-to-gke.sh](./jenkins/scripts/deploy-to-gke.sh) - Script de despliegue
- [jenkins/Jenkins_Deploy.groovy](./jenkins/Jenkins_Deploy.groovy) - Pipeline definition

---

**Última actualización**: $(date)  
**Versión**: 1.0  
**Autor**: Oscar MURA

