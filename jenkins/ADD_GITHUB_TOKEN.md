# Agregar GitHub Token a Jenkins

## 🎯 Objetivo
Para que los checks de Jenkins aparezcan en GitHub, necesitas agregar el token de GitHub como credencial en Jenkins.

## 📋 Pasos

### 1. Generar Token de GitHub (si ya lo tienes, salta este paso)

1. Ve a GitHub.com
2. Tu foto de perfil → **Settings** → **Developer settings** → **Personal access tokens** → **Tokens (classic)**
3. **Generate new token (classic)**
4. Dale un nombre: `Jenkins Status Updates`
5. Selecciona el scope: **`repo`** (esto incluye `repo:status`)
6. **Generate token**
7. **COPIA EL TOKEN** inmediatamente

### 2. Agregar Credencial en Jenkins

1. Ve a tu Jenkins: `https://jenkins.icesi.tech/`
2. **Manage Jenkins** → **Manage Credentials**
3. Haz clic en **(global)**
4. En el menú lateral, haz clic en **Add Credentials**
5. Configura:
   - **Kind:** Secret text
   - **Secret:** Pega el token de GitHub
   - **ID:** `github-token` (ESTO ES CRÍTICO - debe ser exactamente este ID)
   - **Description:** `GitHub Personal Access Token for Status Updates`
6. Haz clic en **OK**

### 3. Verificar

1. Ve a cualquier pipeline (ej: `user-service-dev`)
2. Haz clic en **Build Now** o espera el próximo push
3. Revisa los logs del pipeline
4. Busca el mensaje: `⚠️ No se pudo actualizar estado en GitHub`

**Si NO aparece el mensaje de error**, significa que está funcionando ✅

### 4. Verificar en GitHub

1. Ve a tu commit en GitHub
2. Deberías ver los checks de Jenkins:

```
✅ ci/jenkins/user-service
✅ ci/jenkins/product-service
...
```

## 🚨 Troubleshooting

### Error: "No credential found"

**Causa:** El ID de la credencial no es correcto
**Solución:** Verifica que el ID sea exactamente `github-token`

### Error: "Authentication failed"

**Causa:** El token no tiene los permisos correctos
**Solución:** Regenera el token con el scope `repo`

### Los checks no aparecen en GitHub

**Verifica:**
1. Que la credencial `github-token` esté configurada
2. Que el token tenga permisos `repo:status`
3. Que el pipeline haya terminado exitosamente
4. Que en los logs no aparezca `⚠️ No se pudo actualizar estado en GitHub`

## ✅ Checklist

- [ ] Token de GitHub generado con scope `repo`
- [ ] Credencial agregada en Jenkins con ID `github-token`
- [ ] Pipeline ejecutado exitosamente
- [ ] No hay errores en los logs sobre GitHub
- [ ] Los checks aparecen en GitHub

## 📝 Nota Importante

El ID de la credencial **DEBE** ser exactamente `github-token` (sin mayúsculas, sin espacios).

Si cambias el ID, debes actualizar también todos los pipelines de Jenkins.
