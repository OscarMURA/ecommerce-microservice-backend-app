#!/usr/bin/env bash
# Script para configurar Minikube para que se inicie automáticamente al reiniciar la VM

set -euo pipefail

echo "🔧 Configurando auto-inicio de Minikube..."

# Crear servicio systemd para iniciar Minikube automáticamente
sudo tee /etc/systemd/system/minikube.service > /dev/null <<'EOF'
[Unit]
Description=Minikube Kubernetes Cluster
Documentation=https://minikube.sigs.k8s.io/
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
User=jenkins
Group=docker
WorkingDirectory=/home/jenkins

# Esperar a que Docker esté listo
ExecStartPre=/bin/sleep 10
ExecStartPre=/usr/bin/docker info

# Iniciar Minikube
ExecStart=/usr/local/bin/minikube start --driver=docker --memory=3072 --cpus=2 --disk-size=20g

# Detener Minikube al apagar
ExecStop=/usr/local/bin/minikube stop

# Configurar entorno
Environment="HOME=/home/jenkins"
Environment="USER=jenkins"

# Timeouts
TimeoutStartSec=600
TimeoutStopSec=120

[Install]
WantedBy=multi-user.target
EOF

# Recargar systemd
sudo systemctl daemon-reload

# Habilitar el servicio para que se inicie automáticamente
sudo systemctl enable minikube.service

echo "✅ Servicio systemd creado y habilitado"

# Verificar el estado
echo ""
echo "📊 Estado del servicio:"
sudo systemctl status minikube.service --no-pager || true

echo ""
echo "💡 Para iniciar Minikube ahora, ejecuta:"
echo "   sudo systemctl start minikube"
echo ""
echo "💡 Para ver los logs del servicio:"
echo "   sudo journalctl -u minikube -f"
echo ""
echo "✅ Configuración completada. Minikube se iniciará automáticamente al reiniciar la VM."

