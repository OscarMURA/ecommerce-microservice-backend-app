# Configuración Paso a Paso - GitHub Status en Jenkins

## 🎯 Objetivo
Configurar `https://jenkins.icesi.tech/` para que muestre el estado de los pipelines en GitHub.

## 📋 Pasos Detallados

### 1️⃣ Acceder a Jenkins
1. Abre tu navegador
2. Ve a: `https://jenkins.icesi.tech/`
3. Inicia sesión con tus credenciales

### 2️⃣ Instalar Plugin GitHub Status

1. En el menú izquierdo, haz clic en **"Manage Jenkins"**
2. Haz clic en **"Manage Plugins"**
3. Ve a la pestaña **"Available"**
4. En el buscador, escribe: `GitHub Status Notifier`
5. Busca el plugin llamado **"GitHub Status Notifier"** o **"GitHub Plugin"**
6. Marca la casilla ✓
7. Haz clic en **"Install without restart"** (o **"Download now and install after restart"**)
8. Espera a que se instale
9. Si te pidió reinicio, haz clic en **"Restart Jenkins when installation is complete and no jobs are running"**

### 3️⃣ Generar Token de GitHub

1. Ve a GitHub.com
2. Haz clic en tu foto de perfil (arriba a la derecha)
3. Selecciona **"Settings"**
4. En el menú lateral izquierdo, busca y haz clic en **"Developer settings"**
5. Haz clic en **"Personal access tokens"** → **"Tokens (classic)"**
6. Haz clic en **"Generate new token"** → **"Generate new token (classic)"**
7. Dale un nombre: `Jenkins GitHub Status`
8. Selecciona el alcance (scope) **"repo"** (esto incluye `repo:status`)
9. Haz clic en **"Generate token"** al final
10. **COPIA EL TOKEN** inmediatamente (solo se muestra una vez)
11. Guarda el token en un lugar seguro

### 4️⃣ Agregar Credenciales en Jenkins

1. En Jenkins, ve a **"Manage Jenkins"**
2. Haz clic en **"Manage Credentials"**
3. Haz clic en **"(global)"**
4. En el menú lateral, haz clic en **"Add Credentials"**
5. Configura:
   - **Kind:** Secret text
   - **Secret:** Pega el token que copiaste
   - **ID:** `github-status-token`
   - **Description:** `GitHub Personal Access Token for Status Updates`
6. Haz clic en **"OK"**

### 5️⃣ Configurar GitHub Server (Opcional pero Recomendado)

1. En Jenkins, ve a **"Manage Jenkins"**
2. Haz clic en **"Configure System"**
3. Busca la sección **"GitHub"**
4. Haz clic en **"Add GitHub Server"**
5. Configura:
   - **Name:** `github.com`
   - **API URL:** `https://api.github.com`
   - **Manage hooks:** (deja desmarcado)
   - **Credentials:** Selecciona `github-status-token` del dropdown
6. Opcional: Haz clic en **"Test connection"** para verificar
7. Haz clic en **"Save"**

### 6️⃣ Verificar en un Pipeline

1. Ve a cualquier pipeline de microservicio, por ejemplo: `user-service-dev`
2. Haz clic en **"Scan Repository Now"**
3. Esto disparará los pipelines
4. Los pipelines ahora enviarán el estado a GitHub

---

## ⚠️ IMPORTANTE: Configuración del Webhook en GitHub

**ANTES** de hacer tu próximo push, asegúrate de configurar el webhook en GitHub:

### Configuración del Webhook

1. Ve a tu repositorio en GitHub: `https://github.com/OscarMURA/ecommerce-microservice-backend-app`
2. Haz clic en **Settings** → **Webhooks**
3. Haz clic en **Add webhook**
4. Configura:
   - **Payload URL:** `https://jenkins.icesi.tech/github-webhook/`
   - **Content type:** `application/json`
   - **Secret:** (opcional, déjalo vacío)
   - **SSL verification:** Enable
   - **Events:** Selecciona "Let me select individual events"
     - ✅ Marca **Pushes**
     - ✅ Marca **Pull requests**
   - **Active:** ✅ Marca la casilla
5. Haz clic en **Add webhook**

**NOTA:** El webhook se activará en tu próximo push. Los commits anteriores no mostrarán el estado.

---

### 7️⃣ Probar la Integración

Ahora vamos a hacer un push para verificar que todo funciona:

1. Abre la terminal en tu proyecto
2. Ejecuta estos comandos:
   ```bash
   echo "# GitHub Status Integration" >> README.md
   git add .
   git commit -m "docs: update GitHub status setup documentation"
   git push origin develop
   ```
3. Espera unos minutos a que los pipelines se ejecuten
4. Ve a GitHub y abre el commit
5. Deberías ver los checks de Jenkins:

```
All checks have passed
✅ ci/jenkins/user-service - Build completed
✅ ci/jenkins/product-service - Build completed
✅ ci/jenkins/payment-service - Build completed
...
```

## 🔍 Verificación

### En GitHub Commits
1. Ve a tu repositorio: `https://github.com/OscarMURA/ecommerce-microservice-backend-app`
2. Haz clic en cualquier commit reciente
3. Deberías ver:

```
All checks have passed
✅ ci/jenkins/user-service - Build completed
✅ ci/jenkins/product-service - Build completed
...
```

### En Pull Requests
1. Abre o crea un Pull Request
2. Verás todos los checks de Jenkins:
```
Checks passed
✅ ci/jenkins/user-service
✅ ci/jenkins/product-service
...
```

## 🚨 Troubleshooting

### El estado no aparece en GitHub

**Posibles causas:**
1. El plugin no está instalado
   - Verifica: Manage Jenkins → Manage Plugins → Installed
   - Busca "GitHub Status Notifier"

2. Las credenciales no están configuradas
   - Verifica: Manage Jenkins → Manage Credentials
   - Debe existir `github-status-token`

3. El token no tiene permisos
   - Ve a GitHub → Settings → Developer settings → Personal access tokens
   - Verifica que tenga el scope `repo:status`

### Error 403 en los logs

```
ERROR: No credential found
```

**Solución:**
- Verifica que las credenciales tengan el ID correcto: `github-status-token`
- Regenera el token con permisos completos de `repo`

### El estado dice "pending" indefinidamente

**Solución:**
- Verifica que el pipeline se esté ejecutando
- Revisa los logs del pipeline en Jenkins
- Busca el mensaje: `⚠️ No se pudo actualizar estado en GitHub`

## 📞 Apoyo

Si tienes problemas:

1. Revisa los logs del pipeline en Jenkins
2. Verifica que el plugin esté instalado
3. Verifica las credenciales
4. Revisa la documentación en `jenkins/GITHUB_STATUS_INTEGRATION.md`

## ✅ Checklist Final

- [ ] Plugin "GitHub Status Notifier" instalado
- [ ] Token de GitHub generado
- [ ] Credenciales agregadas en Jenkins
- [ ] GitHub Server configurado (opcional)
- [ ] Pipeline ejecutado con éxito
- [ ] Estado visible en GitHub

## 🎉 ¡Listo!

Una vez configurado, todos los commits y PRs mostrarán automáticamente el estado de tus pipelines de Jenkins en GitHub.
