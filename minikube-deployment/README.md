# 🚀 Despliegue de Microservicios en Minikube

Esta carpeta contiene todos los archivos necesarios para desplegar los microservicios de ecommerce en Minikube.

## 📁 Archivos Incluidos

- **`test-minikube.sh`** - Script principal de despliegue optimizado
- **`README-MINIKUBE.md`** - Documentación completa del despliegue
- **`minikube-configmap.yaml`** - Configuración de los microservicios
- **`minikube-secrets.yaml`** - Secretos para los microservicios

## 🚀 Uso Rápido

```bash
# Ejecutar desde la carpeta raíz del proyecto
cd /path/to/ecommerce-microservice-backend-app

# Desplegar todos los microservicios
./minikube-deployment/test-minikube.sh
```

## 📋 Servicios Desplegados

- ✅ **service-discovery** (Eureka Server) - Puerto 8761
- ✅ **zipkin** (Tracing) - Puerto 9411
- ✅ **order-service** - Puerto 8081
- ✅ **payment-service** - Puerto 8082
- ✅ **product-service** - Puerto 8083
- ✅ **shipping-service** - Puerto 8084
- ✅ **user-service** - Puerto 8085
- ✅ **favourite-service** - Puerto 8086

## 📖 Documentación Completa

Ver `README-MINIKUBE.md` para documentación detallada, troubleshooting y ejemplos de uso.

## ⚡ Características

- **🚀 Rápido**: Despliegue completo en 8-10 minutos
- **🛡️ Estable**: Recursos optimizados para evitar OOMKilled
- **🔧 Automático**: Detección automática de recursos de Docker
- **📦 Completo**: Incluye construcción de imágenes y despliegue
- **✅ Verificación**: Health checks automáticos al final
