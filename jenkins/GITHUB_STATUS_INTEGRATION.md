# Integración de Jenkins con GitHub Status

## 📋 Descripción

Esta guía explica cómo configurar la integración entre Jenkins y GitHub para que el estado de los pipelines se muestre en GitHub.

## 🎯 Lo que hace

Después de configurar esto, los compañeros podrán ver en GitHub:
- ✅ **Estado de los pipelines** directamente en los commits
- ✅ **Check status** en Pull Requests
- ✅ **Notificaciones** cuando un pipeline falla o tiene éxito

## 🔧 Configuración en Jenkins

### 1. Instalar Plugin

1. Ve a **Manage Jenkins** → **Manage Plugins**
2. Busca **"GitHub Status Notifier"** o **"GitHub Plugin"**
3. Instálalo y reinicia Jenkins

### 2. Configurar GitHub Credentials

1. Ve a **Manage Jenkins** → **Manage Credentials**
2. Agrega credenciales de tipo **"Secret text"**
3. Usa tu **GitHub Personal Access Token** con permisos:
   - `repo:status` - Para actualizar el estado
   - `repo` - Para acceder al repositorio

### 3. Configurar GitHub Server (Opcional pero recomendado)

1. Ve a **Manage Jenkins** → **Configure System**
2. Busca la sección **"GitHub"**
3. Agrega **"GitHub Server"**:
   - Name: `github.com`
   - API URL: `https://api.github.com`
   - Credentials: Selecciona las credenciales creadas anteriormente
4. Guarda los cambios

## 📊 Cómo Funciona

### En el Pipeline

Los pipelines ahora incluyen código en la sección `post` que:

1. **En caso de éxito:**
   ```groovy
   step([$class: 'GitHubCommitStatusSetter',
     reposSource: [$class: 'ManuallyEnteredRepositorySource', 
       url: 'https://github.com/OscarMURA/ecommerce-microservice-backend-app.git'],
     commitShaSource: [$class: 'StringSource', sha: env.GIT_COMMIT],
     contextSource: [$class: 'ManuallyEnteredCommitContextSource', 
       context: 'ci/jenkins/user-service'],
     statusResultSource: [state: 'SUCCESS']
   ])
   ```

2. **En caso de fallo:**
   ```groovy
   step([state: 'FAILURE'])
   ```

### Contextos por Servicio

Cada microservicio tiene su propio contexto:

- `ci/jenkins/user-service`
- `ci/jenkins/product-service`
- `ci/jenkins/payment-service`
- `ci/jenkins/order-service`
- `ci/jenkins/shipping-service`
- `ci/jenkins/favourite-service`
- `ci/jenkins/api-gateway`
- `ci/jenkins/service-discovery`
- `ci/jenkins/cloud-config`

## 📍 Dónde Ver el Estado

### 1. En los Commits

Ve a cualquier commit en GitHub:
```
✅ ci/jenkins/user-service - Build completed
✅ ci/jenkins/product-service - Build completed
...
```

### 2. En Pull Requests

Al abrir un PR, verás todos los checks:
```
All checks have passed
✅ ci/jenkins/user-service
✅ ci/jenkins/product-service
...
```

### 3. En la Página Principal del Repo

Verás el estado del último commit:
```
🔴 2 failing checks
🟡 1 pending check
🟢 6 successful checks
```

## 🚨 Troubleshooting

### El estado no aparece en GitHub

1. **Verifica que el plugin esté instalado:**
   - Manage Jenkins → Manage Plugins → Installed
   - Busca "GitHub Status Notifier"

2. **Verifica las credenciales:**
   - El token debe tener permisos `repo:status`

3. **Verifica el log del pipeline:**
   ```groovy
   echo "⚠️ No se pudo actualizar estado en GitHub: ${e.message}"
   ```
   Esto aparece si hay un problema

### El estado aparece pero muestra error 403

1. El token no tiene los permisos correctos
2. Regenera el token con permisos `repo:status`

### El estado no se actualiza

1. Verifica que el pipeline tenga acceso a `env.GIT_COMMIT`
2. Verifica que la URL del repositorio sea correcta

## 🔐 Permisos del Token de GitHub

Tu GitHub Personal Access Token necesita estos permisos:

```
✓ repo:status - Update commit status
✓ repo - Full control of private repositories (opcional, solo si el repo es privado)
```

Para crear un token:
1. GitHub → Settings → Developer settings → Personal access tokens
2. Generate new token (classic)
3. Marca `repo:status` y `repo`
4. Genera y copia el token
5. Agréguelo a Jenkins como credencial

## ✅ Verificación

Después de configurar:

1. Haz un commit a `develop`
2. Ve al commit en GitHub
3. Deberías ver los checks de Jenkins:
   ```
   ✅ ci/jenkins/user-service
   ✅ ci/jenkins/product-service
   ...
   ```

## 📚 Más Información

- [Jenkins GitHub Plugin Documentation](https://plugins.jenkins.io/github/)
- [GitHub Status API](https://docs.github.com/en/rest/commits/statuses)


