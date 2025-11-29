#!/bin/bash
# Convert tracked service folders into git submodules pointing to individual repos.
# Usage:
#   ./convert-folders-to-submodules.sh            # executes conversion
#   ./convert-folders-to-submodules.sh --dry-run  # shows planned actions only
# Idempotent: skips folders already submodules.

set -euo pipefail

ORG="Ecommerce-Microservice-Lab"
# Branch por defecto tentativa (se auto-detectará por repo: usa master si existe; si no, main)
DEFAULT_BRANCH="main"
SERVICES=(
  service-discovery
  api-gateway
  cloud-config
  favourite-service
  order-service
  payment-service
  product-service
  shipping-service
  user-service
  e2e-tests
  performance-tests
)

DRY_RUN=false
if [[ ${1:-} == "--dry-run" ]]; then
  DRY_RUN=true
  echo "[DRY-RUN] Mostrando pasos sin aplicar cambios";
fi

if [[ ! -d .git ]]; then
  echo "Debe ejecutarse desde la raíz del repositorio principal" >&2
  exit 1
fi

# Detect already a submodule: folder contains .git file (not directory) that references gitdir.

function is_submodule() {
  local path="$1"
  if [[ -f "$path/.git" && ! -d "$path/.git" ]]; then
    return 0
  else
    return 1
  fi
}

function detect_branch() {
  local remote_url="$1"
  # Devuelve master si existe, sino main, sino valor por defecto
  local heads
  heads=$(git ls-remote "$remote_url" 2>/dev/null | awk '{print $2}') || true
  if echo "$heads" | grep -q "refs/heads/master"; then
    echo "master"; return
  fi
  if echo "$heads" | grep -q "refs/heads/main"; then
    echo "main"; return
  fi
  echo "$DEFAULT_BRANCH"
}

function convert_one() {
  local svc="$1"
  local remote="https://github.com/${ORG}/${svc}.git"
  echo "------------------------------------------------------------"
  echo "Servicio: $svc"
  echo "Remote:   $remote"
  local BRANCH
  BRANCH=$(detect_branch "$remote")
  echo "Branch destino detectado: $BRANCH"

  if ! [[ -d "$svc" ]]; then
    echo "[SKIP] No existe carpeta $svc"; return
  fi

  if is_submodule "$svc"; then
    echo "[SKIP] $svc ya es submodule"; return
  fi

  # Prepare temp backup
  local backup="${svc}-backup-local"
  if [[ -d "$backup" ]]; then
    echo "El directorio temporal $backup ya existe. Abortando para evitar pérdida de datos." >&2
    exit 2
  fi

  echo "Respaldando contenido local en $backup";
  $DRY_RUN || mv "$svc" "$backup"

  echo "Removiendo carpeta del índice git (sin borrar respaldo)";
  $DRY_RUN || git rm -r --cached "$svc" || true

  echo "Agregando submodule $svc -> $remote";
  if ! $DRY_RUN; then
    git submodule add -f "$remote" "$svc" || {
      echo "Fallo al agregar submodule para $svc. Restaurando contenido";
      mv "$backup" "$svc"
      return
    }
  fi

  # Sync local backup content into submodule clone
  if ! $DRY_RUN; then
    rsync -a --delete --exclude='.git' "$backup/" "$svc/"
    pushd "$svc" >/dev/null
    git add .
    if ! git diff --cached --quiet; then
      git commit -m "Sync content from monorepo conversion"
      git push origin "$BRANCH" || echo "WARNING: push falló para $svc";
    else
      echo "Nada que commitear dentro de submodule $svc"
    fi
    popd >/dev/null
  fi

  echo "Eliminando respaldo $backup";
  $DRY_RUN || rm -rf "$backup"

  echo "[OK] $svc convertido"
}

  echo "Iniciando conversión a submodules (detección dinámica de branch por servicio)"
for s in "${SERVICES[@]}"; do
  convert_one "$s"
  echo
done

if $DRY_RUN; then
  echo "[DRY-RUN] Conversión no aplicada. Ejecuta sin --dry-run para aplicar."; exit 0; fi

# Commit root changes (.gitmodules + submodule references)
if ! git diff --cached --quiet || ! git diff --quiet; then
  git add .gitmodules || true
  git add ${SERVICES[@]} || true
  git commit -m "Convert service folders to submodules"
  echo "Pusheando cambios del repo raíz";
  ROOT_BRANCH=$(git branch --show-current 2>/dev/null || echo "master")
  git push origin "$ROOT_BRANCH"
else
  echo "No hay cambios que commitear en el repo raíz"
fi

echo "Conversión finalizada. Usa 'git submodule status' para ver el estado."