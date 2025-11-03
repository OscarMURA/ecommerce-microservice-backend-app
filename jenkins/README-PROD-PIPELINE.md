# 🚀 Pipeline de Producción con Release Notes Automáticas

## 📋 Descripción

`All-Services-Prod.groovy` es el pipeline de Jenkins para despliegues en el ambiente de **PRODUCCIÓN**. Este pipeline incluye:

- ✅ Validación de branch (solo `master` o `main`)
- 📝 **Generación automática de Release Notes**
- 🔍 Detección inteligente de cambios en servicios
- 🚀 Despliegue a GKE en namespace `prod`
- 🏥 Health checks exhaustivos
- 🧪 Pruebas E2E en producción
- ⚡ Pruebas de rendimiento
- 📊 Resumen completo del despliegue

---

## 🎯 Características del Pipeline de Producción

### Diferencias con Staging

| Característica | Staging | Producción |
|---------------|---------|------------|
| **Branch** | `staging` | `master` o `main` |
| **Namespace** | `staging` | `prod` |
| **Réplicas por defecto** | 1 | 2 |
| **Recursos CPU** | 200m - 500m | 250m - 1000m |
| **Recursos Memoria** | 512Mi - 1Gi | 768Mi - 2Gi |
| **Health check timeout** | 300s (5 min) | 300s (5 min) |
| **Health check retries** | 12 (cada 10s) | 15 (cada 15s) |
| **Pruebas de rendimiento** | 20 usuarios, 1m30s | 50 usuarios, 3m |
| **Release Notes** | No | ✅ **Sí** |

---

## 📝 Release Notes Automáticas

### ¿Qué incluyen las Release Notes?

El pipeline genera automáticamente un documento completo con:

#### 1. **Resumen de Cambios (últimos 7 días)**
Tabla con cantidad de cambios por categoría:
- 🚀 Nuevas Funcionalidades (feat)
- 🐛 Correcciones (fix)
- 📝 Documentación (docs)
- ⚡ Mejoras/Refactoring
- 🧪 Tests/CI
- 📋 Otros

#### 2. **Últimos 5 Releases/Tags**
Información detallada de cada tag:
- Fecha del release
- Autor
- Mensaje del tag
- Commit asociado

#### 3. **Commit Más Significativo**
El commit con más cambios en los últimos 7 días:
- Hash y mensaje del commit
- Autor
- Archivos cambiados
- Líneas modificadas

#### 4. **Top 10 Commits Más Representativos**
Los 10 commits más recientes de los últimos 7 días:
- Mensaje del commit con emoji según categoría
- Autor
- Fecha
- Tiempo relativo

#### 5. **Cambios Detallados por Categoría**
Listado completo de commits agrupados por tipo:
- Nuevas funcionalidades
- Correcciones de bugs
- Documentación
- Mejoras y refactoring
- Tests y CI/CD

#### 6. **Información Adicional**
- URL del repositorio
- Total de commits
- Número de contribuidores

---

## 🔧 Configuración del Pipeline en Jenkins

### 1. Crear Job Multibranch Pipeline

1. **New Item** → `All-Services-Prod` → **Multibranch Pipeline**

2. **Branch Sources:**
   - **Git**
   - Repository URL: `https://github.com/OscarMURA/ecommerce-microservice-backend-app.git`
   - Credentials: `github-token`
   - Behaviors:
     - Discover branches: `Filter by name (with regular expression)`
     - Include: `^(master|main)$`

3. **Build Configuration:**
   - Mode: `by Jenkinsfile`
   - Script Path: `jenkins/All-Services-Prod.groovy`

4. **Scan Multibranch Pipeline Triggers:**
   - Periodically if not otherwise run: `1 day` (producción requiere intervención manual)

### 2. Credenciales Requeridas

Asegúrate de tener configuradas estas credenciales en Jenkins:

| ID de Credencial | Tipo | Descripción |
|-----------------|------|-------------|
| `gcp-project-id` | Secret Text | ID del proyecto GCP |
| `gcp-service-account` | Secret File | Service account JSON de GCP |
| `docker-user` | Secret Text | Usuario de Docker Hub |
| `github-token` | Secret Text | Token de GitHub para API |

---

## 📊 Parámetros del Pipeline

### Parámetros Principales

| Parámetro | Valor por Defecto | Descripción |
|-----------|-------------------|-------------|
| `DOCKER_IMAGE_TAG` | `latest` | Tag de la imagen en Docker Hub |
| `GKE_CLUSTER_NAME` | `ecommerce-prod-gke` | Nombre del cluster de producción |
| `GKE_LOCATION` | `us-central1-a` | Zona del cluster GKE |
| `K8S_NAMESPACE` | `prod` | Namespace de Kubernetes |
| `REPLICA_COUNT` | `2` | Número de réplicas por servicio |

### Parámetros de Servicios

Todos con valor por defecto `true`:
- `DEPLOY_SERVICE_DISCOVERY`
- `DEPLOY_USER_SERVICE`
- `DEPLOY_PRODUCT_SERVICE`
- `DEPLOY_ORDER_SERVICE`
- `DEPLOY_SHIPPING_SERVICE`
- `DEPLOY_PAYMENT_SERVICE`
- `DEPLOY_FAVOURITE_SERVICE`

### Parámetros de Pruebas

| Parámetro | Valor por Defecto | Descripción |
|-----------|-------------------|-------------|
| `PERF_TEST_USERS` | `50` | Usuarios concurrentes |
| `PERF_TEST_SPAWN_RATE` | `5` | Usuarios por segundo |
| `PERF_TEST_DURATION` | `3m` | Duración de las pruebas |
| `FORCE_DEPLOY_ALL` | `false` | Forzar despliegue de todos |

---

## 🚀 Cómo Ejecutar un Despliegue a Producción

### Proceso Recomendado

#### 1. **Preparación**
```bash
# Asegúrate de estar en la rama correcta
git checkout master  # o main

# Verifica que todo esté actualizado
git pull origin master

# Opcional: Crear un tag de versión
git tag -a v1.2.3 -m "Release v1.2.3 - Descripción de cambios"
git push origin v1.2.3
```

#### 2. **Ejecutar el Pipeline en Jenkins**

1. Ve a Jenkins → `All-Services-Prod` → `master` (o `main`)
2. Click en **"Build with Parameters"**
3. Configura los parámetros según necesites:
   - `DOCKER_IMAGE_TAG`: Usa un tag específico (ej: `v1.2.3`, `abc1234`) en lugar de `latest`
   - Verifica el cluster y namespace
   - Selecciona los servicios a desplegar
4. Click en **"Build"**

#### 3. **Monitorear el Despliegue**

El pipeline ejecutará los siguientes stages:

1. ✅ **Validate Branch** - Verifica que sea master/main
2. 📝 **Generate Release Notes** - Genera las notas de versión
3. 🔍 **Detect Service Changes** - Detecta qué servicios cambiaron
4. 🚀 **Deploy Services** - Despliega a GKE producción
5. 🏥 **Health Check** - Verifica que todos los servicios estén UP
6. 🧪 **Run E2E Tests** - Ejecuta pruebas end-to-end
7. ⚡ **Run Performance Tests** - Ejecuta pruebas de rendimiento
8. 📊 **Deployment Summary** - Muestra resumen final

#### 4. **Revisar Release Notes**

- Las Release Notes se generan en el **Stage 2** del pipeline
- Se archivan automáticamente como **artefacto del build**
- Puedes descargarlas desde Jenkins:
  - Ve al build → **Build Artifacts** → `RELEASE_NOTES_<BUILD_NUMBER>.md`

---

## 📝 Ejemplo de Release Notes Generadas

```markdown
# 🚀 Release Notes - Build #42

**Ambiente:** prod  
**Fecha de Despliegue:** 2025-11-03 14:30:45  
**Branch:** master  
**Commit:** abc1234  

---

## 📊 Resumen de Cambios (últimos 7 días)

| Tipo | Cantidad |
|------|----------|
| 🚀 Nuevas Funcionalidades (feat) | 5 |
| 🐛 Correcciones (fix) | 3 |
| 📝 Documentación (docs) | 2 |
| ⚡ Mejoras/Refactoring | 4 |
| 🧪 Tests/CI | 6 |
| 📋 Otros | 1 |
| **Total** | **21** |

---

## 🏷️ Últimos 5 Releases/Tags

### 1. `v1.2.2` - 2025-10-28
**Autor:** Oscar Murillo  
**Mensaje:** Release v1.2.2 - Fix critical bug in payment service  

### 2. `v1.2.1` - 2025-10-25
**Autor:** Oscar Murillo  
**Mensaje:** Release v1.2.1 - Performance improvements  

...

---

## 💡 Commit Más Significativo (últimos 7 días)

**Commit:** `abc1234`  
**Mensaje:** feat: implement new recommendation engine  
**Autor:** Oscar Murillo  
**Archivos cambiados:** 23 archivos  
**Líneas modificadas:** 1247 cambios  

---

## 📝 Top 10 Commits Más Representativos (últimos 7 días)

1. 🚀 **feat: add product recommendation engine** (`abc1234`)  
   _Oscar Murillo_ - 2025-11-02 (hace 1 día)  

2. 🐛 **fix: resolve payment gateway timeout** (`def5678`)  
   _Maria Garcia_ - 2025-11-01 (hace 2 días)  

...
```

---

## 🔍 Script de Release Notes

### Ubicación
`jenkins/scripts/generate-release-notes.sh`

### Variables de Entorno

El script usa estas variables de entorno:

| Variable | Descripción |
|----------|-------------|
| `BUILD_NUMBER` | Número del build de Jenkins |
| `BRANCH_NAME` | Nombre de la rama |
| `GIT_COMMIT` | Hash del commit actual |
| `ENVIRONMENT` | Ambiente (prod, staging, dev) |

### Uso Manual

```bash
# Dentro del repositorio
cd ecommerce-microservice-backend-app

# Ejecutar el script
./jenkins/scripts/generate-release-notes.sh output.md

# Ver el resultado
cat output.md
```

---

## 🎯 Mejores Prácticas

### 1. **Usar Tags Específicos**
Nunca uses `latest` en producción:
```bash
# Mal
DOCKER_IMAGE_TAG = "latest"

# Bien
DOCKER_IMAGE_TAG = "v1.2.3"
DOCKER_IMAGE_TAG = "abc1234-build-42"
```

### 2. **Mensajes de Commit Descriptivos**
Usa convención de commits para mejores release notes:
```bash
feat: agregar autenticación de dos factores
fix: corregir fuga de memoria en order-service
docs: actualizar README con instrucciones de despliegue
perf: optimizar consultas de base de datos
test: agregar pruebas unitarias para user-service
```

### 3. **Crear Tags de Versión**
Antes de cada despliegue importante:
```bash
git tag -a v1.2.3 -m "Release v1.2.3 - Descripción"
git push origin v1.2.3
```

### 4. **Revisar Release Notes Antes del Despliegue**
Las release notes se generan al inicio del pipeline, úsalas para:
- Validar qué cambios se van a desplegar
- Comunicar al equipo qué incluye el release
- Documentar el historial de cambios

### 5. **Monitoreo Post-Despliegue**
Después del despliegue:
```bash
# Ver pods en producción
kubectl get pods -n prod -l deployed-by=all-services-prod-pipeline

# Ver logs de un servicio
kubectl logs -n prod deployment/user-service --tail=100 -f

# Ver métricas
kubectl top pods -n prod
```

---

## 🆘 Troubleshooting

### Pipeline falla en "Generate Release Notes"
**Problema:** El script no tiene permisos de ejecución
```bash
# Solución
chmod +x jenkins/scripts/generate-release-notes.sh
git add jenkins/scripts/generate-release-notes.sh
git commit -m "fix: add execute permissions to release notes script"
```

### No se encuentran tags/releases
**Problema:** El repositorio no tiene tags
```bash
# Crear tags históricos
git tag -a v1.0.0 <commit-hash> -m "Initial release"
git push origin --tags
```

### Health checks fallan en producción
**Problema:** Los servicios tardan más en iniciar
- Aumenta `initialDelaySeconds` en los probes
- Verifica recursos disponibles en el cluster
- Revisa logs del pod: `kubectl logs -n prod deployment/<service-name>`

---

## 📚 Referencias

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [Git Tagging Best Practices](https://git-scm.com/book/en/v2/Git-Basics-Tagging)
- [Kubernetes Production Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)

---

## 🤝 Contribuir

Si encuentras mejoras para el pipeline o las release notes:

1. Crea una rama feature
2. Implementa los cambios
3. Prueba en staging primero
4. Crea un Pull Request con descripción detallada

---

**Última actualización:** 2025-11-03  
**Autor:** Oscar Murillo  
**Versión del pipeline:** 1.0

