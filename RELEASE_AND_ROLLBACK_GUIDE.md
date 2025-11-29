# 📖 Release Notes & Change Management Documentation

## Resumen General

Este documento describe cómo se generan automáticamente las **Release Notes** y cómo ejecutar **Rollback Procedures** para todos los 7 servicios del e-commerce.

---

## 📋 Servicios Incluidos

| Servicio | Repo | Función |
|----------|------|---------|
| 🔍 service-discovery | [link](https://github.com/Ecommerce-Microservice-Lab/service-discovery) | Descubrimiento de servicios (Eureka) |
| 💳 payment-service | [link](https://github.com/Ecommerce-Microservice-Lab/payment-service) | Procesamiento de pagos |
| 📦 product-service | [link](https://github.com/Ecommerce-Microservice-Lab/product-service) | Catálogo de productos |
| 👤 user-service | [link](https://github.com/Ecommerce-Microservice-Lab/user-service) | Gestión de usuarios |
| 🛒 order-service | [link](https://github.com/Ecommerce-Microservice-Lab/order-service) | Gestión de órdenes |
| 🚚 shipping-service | [link](https://github.com/Ecommerce-Microservice-Lab/shipping-service) | Gestión de envíos |
| ❤️ favourite-service | [link](https://github.com/Ecommerce-Microservice-Lab/favourite-service) | Gestión de favoritos |

---

## 🚀 Pipeline de Release

### Flujo Automático

```
Push Tag (v1.0.0)
        ↓
GitHub Actions Triggered
        ↓
Validate Release (Semantic Versioning)
        ↓
Build & Test
        ↓
Generate Changelog (desde commits)
        ↓
Create GitHub Release
        ↓
Release Notes Publicadas ✅
```

### Cómo Crear un Release

1. **Crear el tag localmente:**
   ```bash
   git tag -a v1.1.0 -m "Release v1.1.0 - Description"
   ```

2. **Hacer push del tag:**
   ```bash
   git push origin v1.1.0
   ```

3. **El workflow se ejecuta automáticamente:**
   - ✅ Valida que sea Semantic Versioning (vX.Y.Z)
   - ✅ Compila y ejecuta tests
   - ✅ Genera release notes automáticas
   - ✅ Crea el release en GitHub con badge "Latest"

---

## 📝 Release Notes Automáticas

Los **Release Notes** se generan automáticamente desde:

### 1. Commits (Categorizado por tipo)

```
🚀 Features (feat: )
🐛 Bug Fixes (fix: )
📝 Documentation (docs: )
⚡ Performance (perf: )
🔧 Other Changes
```

### 2. Información Incluida

- **Fecha de release:** Automática
- **Versión:** Del tag (v1.0.0)
- **Rama:** Del commit que se tagueó
- **Commit SHA:** Identificación única
- **JAR build:** Archivo compilado

### 3. Ejemplo de Release Notes

```markdown
# 🚀 Release v1.0.0 - payment-service

**📅 Release Date:** November 28, 2025
**🔖 Version:** v1.0.0
**📦 Service:** payment-service
**🌿 Branch:** master
**🔗 Commit:** abc123def456

## 📊 Release Summary

| Attribute | Value |
|-----------|-------|
| Version | v1.0.0 |
| Service | payment-service |
| Build Status | ✅ Passed |
| Tests | ✅ Passed |

## 📋 Changelog

### 🚀 Features
- feat: integrate payment gateway

### 🐛 Bug Fixes
- fix: handle payment timeout

### 🔄 Rollback Plan
See ROLLBACK.md for detailed procedures
```

---

## 🔄 Rollback Procedures

### Prerequisitos

**Todos los rollbacks requieren conexión a GCP:**

```bash
# 1. Autenticarse en GCP
gcloud auth login

# 2. Configurar proyecto
gcloud config set project YOUR_PROJECT_ID

# 3. Obtener credenciales de Kubernetes
gcloud container clusters get-credentials ecommerce-cluster --zone us-central1-a

# 4. Verificar conexión
kubectl cluster-info
```

### Tipos de Rollback

#### 1. Rollback Inmediato (Recomendado)

```bash
# Rollback a la versión anterior
kubectl rollout undo deployment/SERVICE_NAME -n ecommerce

# Verificar estado
kubectl rollout status deployment/SERVICE_NAME -n ecommerce
```

#### 2. Rollback a Revisión Específica

```bash
# Ver historial
kubectl rollout history deployment/SERVICE_NAME -n ecommerce

# Rollback a revisión específica
kubectl rollout undo deployment/SERVICE_NAME -n ecommerce --to-revision=5
```

#### 3. Rollback de Emergencia

```bash
# Pausar deployment
kubectl rollout pause deployment/SERVICE_NAME -n ecommerce

# Scale down
kubectl scale deployment/SERVICE_NAME --replicas=0 -n ecommerce

# Esperar 30 segundos
sleep 30

# Cambiar imagen manualmente
kubectl set image deployment/SERVICE_NAME \
  SERVICE_NAME=ecommerce/SERVICE_NAME:v1.0.0 \
  -n ecommerce

# Scale up
kubectl scale deployment/SERVICE_NAME --replicas=2 -n ecommerce

# Reanudar deployment
kubectl rollout resume deployment/SERVICE_NAME -n ecommerce
```

---

## 👥 Contactos de Escalación

| Rol | Nombre | Email |
|-----|--------|-------|
| DevOps Lead | Oscar Muñoz | oscar.munoz@ieee.org |
| Service Owner | Ricardo Chamorro | chamorroricardo29@gmail.com |

---

## 📁 Archivos Importantes

En cada repositorio de servicio encontrarás:

| Archivo | Descripción |
|---------|-------------|
| `.github/workflows/release.yml` | Pipeline de GitHub Actions |
| `CHANGELOG.md` | Historial de cambios |
| `ROLLBACK.md` | Procedimientos de rollback |
| `README.md` | Documentación del servicio |

---

## 🔗 Enlaces Útiles

- [GitHub Organization](https://github.com/Ecommerce-Microservice-Lab)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [GCP Container Clusters](https://cloud.google.com/kubernetes-engine/docs)
- [Semantic Versioning](https://semver.org/)

---

## 📊 Release Status

Verifica el estado de cada release visitando:

| Servicio | Release Link |
|----------|-------------|
| service-discovery | https://github.com/Ecommerce-Microservice-Lab/service-discovery/releases |
| payment-service | https://github.com/Ecommerce-Microservice-Lab/payment-service/releases |
| product-service | https://github.com/Ecommerce-Microservice-Lab/product-service/releases |
| user-service | https://github.com/Ecommerce-Microservice-Lab/user-service/releases |
| order-service | https://github.com/Ecommerce-Microservice-Lab/order-service/releases |
| shipping-service | https://github.com/Ecommerce-Microservice-Lab/shipping-service/releases |
| favourite-service | https://github.com/Ecommerce-Microservice-Lab/favourite-service/releases |

---

## ✅ Checklist de Cambios

- [x] GitHub Actions workflow creados (`release.yml`)
- [x] CHANGELOG.md templates implementados
- [x] ROLLBACK.md con procedimientos documentados
- [x] GCP prerequisites incluidos
- [x] Semantic versioning implementado
- [x] Generación automática de release notes
- [x] Etiquetado de releases con v1.0.0
- [x] Contactos de escalación configurados

---

## 📚 Próximos Pasos

1. **Verificar Releases:** Visita GitHub para ver el badge "Latest v1.0.0"
2. **Probar Rollback:** Ejecuta un rollback de prueba en desarrollo
3. **Monitorear Deployments:** Usa `kubectl` para monitorear los servicios
4. **Documentar Issues:** Cualquier problema, crear un issue en el repo

---

*Última actualización: November 28, 2025*
*Versión: v1.0.0*
