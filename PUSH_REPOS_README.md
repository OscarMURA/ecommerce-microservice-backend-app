# Push de Servicios a Repositorios Individuales

Este documento explica cómo sincronizar el contenido de cada microservicio desde este repositorio monolítico hacia los repositorios individuales en GitHub.

## 📋 Repositorios Objetivo

Los siguientes servicios serán pusheados a sus repositorios correspondientes en:
`https://github.com/Ecommerce-Microservice-Lab/`

- `service-discovery`
- `api-gateway`
- `cloud-config`
- `favourite-service`
- `order-service`
- `payment-service`
- `product-service`
- `shipping-service`
- `user-service`
- `e2e-tests`
- `performance-tests`

## 🚀 Métodos de Push

### Opción 1: Push Automático (Todos los servicios)

Este script pushea todos los servicios sin preguntar:

```bash
cd ecommerce-microservice-backend-app
./push-to-individual-repos.sh
```

**⚠️ ADVERTENCIA**: Esto sobrescribirá todo el contenido de los repositorios individuales con `git push --force`.

### Opción 2: Push Interactivo (Recomendado)

Este script te pregunta antes de pushear cada servicio:

```bash
cd ecommerce-microservice-backend-app
./push-to-individual-repos-interactive.sh
```

Ventajas:
- ✅ Puedes revisar los archivos antes de pushear
- ✅ Puedes saltar servicios específicos
- ✅ Más control sobre el proceso

## 📝 Requisitos Previos

1. **Autenticación en GitHub**
   - Asegúrate de tener configurado tu token de GitHub o credenciales
   - Para HTTPS, necesitas un Personal Access Token
   - Para SSH, necesitas tu clave SSH configurada

2. **Configuración de Git**
   ```bash
   git config --global user.name "Tu Nombre"
   git config --global user.email "tu@email.com"
   ```

3. **Permisos en los Repositorios**
   - Debes tener permisos de escritura en todos los repositorios de `Ecommerce-Microservice-Lab`

## 🔧 Cómo Funciona

Para cada servicio, el script:

1. ✅ Crea una copia temporal del servicio
2. ✅ Inicializa un nuevo repositorio git
3. ✅ Hace commit de todos los archivos
4. ✅ Pushea con `--force` al repositorio remoto
5. ✅ Limpia archivos temporales

## 📊 Ejemplo de Ejecución

```bash
$ ./push-to-individual-repos-interactive.sh

======================================
Push INTERACTIVO de servicios
======================================

Este script pusheará cada servicio a su repositorio en:
https://github.com/Ecommerce-Microservice-Lab/

ADVERTENCIA: Esto sobrescribirá el contenido actual (push --force)

¿Deseas continuar? (s/N): s

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Servicio: service-discovery
Repositorio: https://github.com/Ecommerce-Microservice-Lab/service-discovery.git
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Archivos principales a pushear:
total 96K
drwxr-xr-x. 8 user user 4.0K Nov 24 10:30 .
drwxr-xr-x. 23 user user 4.0K Nov 24 10:00 ..
-rw-r--r--. 1 user user  123 Nov 20 15:45 .gitignore
-rw-r--r--. 1 user user 1.2K Nov 20 15:45 Dockerfile
...

¿Pushear este servicio? (s/N): s
  → Copiando archivos...
  → Inicializando repositorio...
  → Creando commit...
  → Pusheando a GitHub...
  ✓ service-discovery pusheado exitosamente
```

## 🛠️ Solución de Problemas

### Error: "Authentication failed"

Configura tu token de acceso personal de GitHub:

```bash
# Opción 1: Usar GitHub CLI
gh auth login

# Opción 2: Configurar credenciales
git config --global credential.helper store
```

### Error: "Repository not found"

Verifica que:
1. El repositorio exista en GitHub
2. El nombre del repositorio sea correcto
3. Tengas permisos de acceso

### Error: "Permission denied"

Verifica que:
1. Seas miembro de la organización `Ecommerce-Microservice-Lab`
2. Tengas permisos de escritura en los repositorios

## 🔄 Push Manual (Un Solo Servicio)

Si prefieres pushear manualmente un servicio específico:

```bash
# Ejemplo para service-discovery
cd /tmp
cp -r ~/path/to/ecommerce-microservice-backend-app/service-discovery ./
cd service-discovery
rm -rf .git
git init -b master
git add .
git commit -m "Update from main repository"
git remote add origin https://github.com/Ecommerce-Microservice-Lab/service-discovery.git
git push -f origin master
```

## 📌 Notas Importantes

- ⚠️ El push es con `--force`, lo que significa que **sobrescribirá** todo el historial del repositorio remoto
- 🔒 Asegúrate de tener backups si hay cambios importantes en los repositorios remotos
- 📝 Cada commit incluye la fecha y hora del push
- 🧹 Los archivos temporales se limpian automáticamente después del proceso

## 🎯 Verificación Post-Push

Después de ejecutar el script, verifica manualmente algunos repositorios:

```bash
# Clonar y verificar
git clone https://github.com/Ecommerce-Microservice-Lab/service-discovery.git
cd service-discovery
ls -la
```

## 📞 Soporte

Si encuentras problemas:
1. Revisa los logs del script
2. Verifica tus credenciales de GitHub
3. Confirma que los repositorios existan en la organización
4. Verifica los permisos de acceso
