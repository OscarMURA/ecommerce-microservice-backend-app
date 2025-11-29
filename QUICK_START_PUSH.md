# 🚀 Guía Rápida de Push a Repositorios Individuales

## ✅ Estado Actual

**11 servicios detectados y listos para push:**

1. ✅ service-discovery (232K)
2. ✅ api-gateway (57M)
3. ✅ cloud-config (60M)
4. ✅ favourite-service (540K)
5. ✅ order-service (628K)
6. ✅ payment-service (524K)
7. ✅ product-service (616K)
8. ✅ shipping-service (532K)
9. ✅ user-service (900K)
10. ✅ e2e-tests (556K)
11. ✅ performance-tests (7.1M)

**Destino:** https://github.com/Ecommerce-Microservice-Lab/

---

## 📋 PASOS PARA EJECUTAR

### Paso 1: Verificar Autenticación

Asegúrate de tener configurado GitHub:

```bash
# Verificar configuración actual
git config --global user.name
git config --global user.email

# Si necesitas configurar:
git config --global user.name "Tu Nombre"
git config --global user.email "tu@email.com"

# Verificar autenticación con GitHub CLI (recomendado)
gh auth status

# O autenticar si es necesario
gh auth login
```

### Paso 2: Ejecutar el Push (OPCIÓN RECOMENDADA)

**Método Interactivo** - Te permite revisar antes de pushear cada servicio:

```bash
cd /home/oscar/Documents/Taller\ 2\ Ingesoft/ecommerce-microservice-backend-app
./push-to-individual-repos-interactive.sh
```

### Paso 2 (Alternativa): Push Automático

**Método Automático** - Pushea todos los servicios sin preguntar:

```bash
cd /home/oscar/Documents/Taller\ 2\ Ingesoft/ecommerce-microservice-backend-app
./push-to-individual-repos.sh
```

---

## ⚠️ IMPORTANTE

- **Esto sobrescribirá** todo el contenido actual de los repositorios remotos
- Usa `git push --force` por lo que **no hay vuelta atrás**
- Asegúrate de que los repositorios existan en GitHub
- Necesitas permisos de escritura en la organización `Ecommerce-Microservice-Lab`

---

## 🔍 Verificación Post-Push

Después del push, verifica algunos repositorios:

```bash
# Ejemplo: verificar service-discovery
git clone https://github.com/Ecommerce-Microservice-Lab/service-discovery.git /tmp/verify-sd
ls -la /tmp/verify-sd
```

---

## 📞 Troubleshooting

### Error: "Authentication failed"
```bash
gh auth login
# O configura tu token manualmente
```

### Error: "Repository not found"
- Verifica que el repo exista en GitHub
- Verifica que seas miembro de la organización

### Error: "Permission denied"
- Verifica tus permisos en la organización
- Contacta al administrador de la organización

---

## 🧩 Alternativa: Submódulos (Configurado)

Ya se convirtieron las carpetas de servicios en **submódulos git** que apuntan a los repositorios remotos individuales. Esto permite que cada servicio tenga su propio control de versiones y ciclo de vida independiente mientras permanece referenciado desde el repositorio raíz.

### 🔎 Ver estado de submódulos
```bash
git submodule status
```

### ⬇️ Clonar repositorio con submódulos
```bash
git clone https://github.com/OscarMURA/ecommerce-microservice-backend-app.git
cd ecommerce-microservice-backend-app
git submodule update --init --recursive
```

### 🔄 Actualizar todos los submódulos
```bash
git submodule update --remote --merge
```

### ✏️ Trabajar dentro de un submódulo (ejemplo product-service)
```bash
cd product-service
# Hacer cambios...
git add .
git commit -m "Fix algo en product-service"
git push origin main   # o master según el submódulo
cd ..
git add product-service   # registra el nuevo commit del submódulo en el repo raíz
git commit -m "Update product-service submodule pointer"
git push origin master
```

### 🧪 Branch detectado por servicio
| Servicio | Branch |
|----------|--------|
| service-discovery | main |
| api-gateway | (no convertido aún) |
| cloud-config | (no convertido aún) |
| favourite-service | master |
| order-service | master |
| payment-service | master |
| product-service | main |
| shipping-service | master |
| user-service | main |
| e2e-tests | master |
| performance-tests | main |

Si algún repositorio cambia su branch principal (por ejemplo de master a main), actualiza localmente:
```bash
cd nombre-servicio
git checkout main
git pull
cd ..
git add nombre-servicio
git commit -m "Update submodule branch"
git push origin master
```

### ❌ Revertir conversión (volver carpeta normal)
```bash
git rm -f nombre-servicio
rm -rf .git/modules/nombre-servicio
git commit -m "Remove submodule nombre-servicio"
git push origin master
git clone https://github.com/Ecommerce-Microservice-Lab/nombre-servicio.git temp-clone
mv temp-clone nombre-servicio
git add nombre-servicio
git commit -m "Inline nombre-servicio again"
git push origin master
```

## 🎯 Scripts Disponibles

1. **check-services-dry-run.sh** - Verificar qué se va a pushear (ya ejecutado ✅)
2. **push-to-individual-repos-interactive.sh** - Push interactivo (RECOMENDADO)
3. **push-to-individual-repos.sh** - Push automático

---

## 📝 Notas

- Cada commit incluye timestamp
- Se limpian archivos temporales automáticamente
- Los logs muestran el progreso en tiempo real
- Puedes interrumpir con Ctrl+C en cualquier momento (modo interactivo)

---

## ✨ Ejemplo de Uso

```bash
# 1. Ir al directorio
cd /home/oscar/Documents/Taller\ 2\ Ingesoft/ecommerce-microservice-backend-app

# 2. (Opcional) Ver qué se va a pushear
./check-services-dry-run.sh

# 3. Ejecutar push interactivo
./push-to-individual-repos-interactive.sh

# 4. Responder 's' para continuar
# 5. Para cada servicio, responder 's' para pushear o 'n' para saltar
```

---

**¿Todo listo? Ejecuta el comando del Paso 2!** 🚀
