#!/bin/bash

# Script para actualizar todos los ROLLBACK.md con GCP prerequisites

SERVICES=("payment-service" "product-service" "user-service" "order-service" "shipping-service" "favourite-service")

echo "Actualizando todos los ROLLBACK.md con GCP prerequisites..."

for service in "${SERVICES[@]}"; do
    echo "📦 Actualizando: $service"
    cd "$service"
    
    # Agregar GCP prerequisites después de "Información General"
    sed -i '/^## 📋 Información General/a\
\ \
---\ \
\ \
## 🔐 Prerequisitos - Conexión a GCP\ \
\ \
Antes de ejecutar cualquier procedimiento de rollback, debes conectarte a GCP:\ \
\ \
```bash\ \
# 1. Autenticarse en GCP\ \
gcloud auth login\ \
\ \
# 2. Configurar el proyecto\ \
gcloud config set project YOUR_PROJECT_ID\ \
\ \
# 3. Obtener credenciales del cluster Kubernetes\ \
gcloud container clusters get-credentials ecommerce-cluster --zone us-central1-a\ \
\ \
# 4. Verificar conexión\ \
kubectl cluster-info\ \
kubectl get nodes\ \
```' ROLLBACK.md
    
    cd ..
done

echo "✅ Completado"
