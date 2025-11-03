#!/usr/bin/env bash
# Script para generar Release Notes automáticamente desde Git
# Muestra los últimos releases y commits más representativos

set -euo pipefail

# Colores para output
BLUE="\033[0;34m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m"

# Parámetros
OUTPUT_FILE="${1:-RELEASE_NOTES.md}"
BUILD_NUMBER="${BUILD_NUMBER:-N/A}"
BRANCH_NAME="${BRANCH_NAME:-master}"
GIT_COMMIT="${GIT_COMMIT:-$(git rev-parse HEAD 2>/dev/null || echo 'N/A')}"
ENVIRONMENT="${ENVIRONMENT:-prod}"

echo -e "${BLUE}📝 Generando Release Notes...${NC}"

# Función para obtener los últimos N tags/releases
get_recent_releases() {
  local count=${1:-5}
  git tag -l --sort=-version:refname | head -n "$count" 2>/dev/null || echo ""
}

# Función para obtener commits de los últimos N días
get_recent_commits() {
  local days=${1:-7}
  git log --since="${days} days ago" --pretty=format:"%h|%s|%an|%ad|%ar" --date=short 2>/dev/null || echo ""
}

# Función para encontrar el commit con más cambios en los últimos N días
get_biggest_commit() {
  local days=${1:-7}
  git log --since="${days} days ago" --pretty=format:"%h|%s|%an" --shortstat 2>/dev/null | \
    awk '/^[0-9a-f]/ {commit=$0; getline; files=$1; changes=$4+$6; if(changes>max){max=changes; bigcommit=commit; bigfiles=files}} END {print bigcommit"|"bigfiles" archivos|"max" cambios"}' 2>/dev/null || echo ""
}

# Función para contar cambios por tipo (feat, fix, etc.)
count_changes_by_type() {
  local days=${1:-7}
  local commits=$(git log --since="${days} days ago" --pretty=format:"%s" 2>/dev/null || echo "")
  
  local feat_count=$(echo "$commits" | grep -ciE "^(feat|feature|add)" || echo "0")
  local fix_count=$(echo "$commits" | grep -ciE "^(fix|bug|patch)" || echo "0")
  local docs_count=$(echo "$commits" | grep -ciE "^(docs|doc)" || echo "0")
  local refactor_count=$(echo "$commits" | grep -ciE "^(refactor|perf|improve)" || echo "0")
  local test_count=$(echo "$commits" | grep -ciE "^(test|ci)" || echo "0")
  local other_count=$(echo "$commits" | grep -vciE "^(feat|fix|docs|refactor|perf|improve|test|ci|feature|add|bug|patch|doc)" || echo "0")
  
  echo "$feat_count|$fix_count|$docs_count|$refactor_count|$test_count|$other_count"
}

# Obtener información
recent_releases=$(get_recent_releases 5)
recent_commits=$(get_recent_commits 7)
biggest_commit=$(get_biggest_commit 7)
change_stats=$(count_changes_by_type 7)

# Parsear estadísticas
IFS='|' read -r feat_count fix_count docs_count refactor_count test_count other_count <<< "$change_stats"

# Generar el archivo de release notes
cat > "$OUTPUT_FILE" <<EOF
# 🚀 Release Notes - Build #${BUILD_NUMBER}

**Ambiente:** ${ENVIRONMENT}  
**Fecha de Despliegue:** $(date +'%Y-%m-%d %H:%M:%S')  
**Branch:** ${BRANCH_NAME}  
**Commit:** ${GIT_COMMIT:0:7}  

---

## 📊 Resumen de Cambios (últimos 7 días)

| Tipo | Cantidad |
|------|----------|
| 🚀 Nuevas Funcionalidades (feat) | ${feat_count} |
| 🐛 Correcciones (fix) | ${fix_count} |
| 📝 Documentación (docs) | ${docs_count} |
| ⚡ Mejoras/Refactoring | ${refactor_count} |
| 🧪 Tests/CI | ${test_count} |
| 📋 Otros | ${other_count} |
| **Total** | **$((feat_count + fix_count + docs_count + refactor_count + test_count + other_count))** |

---

## 🏷️ Últimos 5 Releases/Tags

EOF

if [ -n "$recent_releases" ]; then
  release_num=1
  while IFS= read -r tag; do
    if [ -n "$tag" ]; then
      tag_date=$(git log -1 --format=%ai "$tag" 2>/dev/null | cut -d' ' -f1 || echo "N/A")
      tag_author=$(git log -1 --format=%an "$tag" 2>/dev/null || echo "N/A")
      tag_message=$(git tag -l --format='%(contents:subject)' "$tag" 2>/dev/null || echo "Release $tag")
      
      echo "### ${release_num}. \`${tag}\` - ${tag_date}" >> "$OUTPUT_FILE"
      echo "**Autor:** ${tag_author}  " >> "$OUTPUT_FILE"
      echo "**Mensaje:** ${tag_message}  " >> "$OUTPUT_FILE"
      echo "" >> "$OUTPUT_FILE"
      
      release_num=$((release_num + 1))
    fi
  done <<< "$recent_releases"
else
  echo "_No se encontraron tags/releases en el repositorio._" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF

---

## 💡 Commit Más Significativo (últimos 7 días)

EOF

if [ -n "$biggest_commit" ]; then
  IFS='|' read -r commit_info files_changed total_changes <<< "$biggest_commit"
  IFS='|' read -r commit_hash commit_msg commit_author <<< "$commit_info"
  
  cat >> "$OUTPUT_FILE" <<EOF
**Commit:** \`${commit_hash}\`  
**Mensaje:** ${commit_msg}  
**Autor:** ${commit_author}  
**Archivos cambiados:** ${files_changed}  
**Líneas modificadas:** ${total_changes}  

EOF
else
  echo "_No se encontraron commits en los últimos 7 días._" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF

---

## 📝 Top 10 Commits Más Representativos (últimos 7 días)

EOF

if [ -n "$recent_commits" ]; then
  commit_num=1
  echo "$recent_commits" | head -n 10 | while IFS='|' read -r hash message author date relative_date; do
    if [ -n "$hash" ]; then
      # Determinar el tipo de cambio y el emoji
      emoji="📋"
      if echo "$message" | grep -qiE "^(feat|feature|add)"; then
        emoji="🚀"
      elif echo "$message" | grep -qiE "^(fix|bug|patch)"; then
        emoji="🐛"
      elif echo "$message" | grep -qiE "^(docs|doc)"; then
        emoji="📝"
      elif echo "$message" | grep -qiE "^(refactor|perf|improve)"; then
        emoji="⚡"
      elif echo "$message" | grep -qiE "^(test|ci)"; then
        emoji="🧪"
      fi
      
      echo "${commit_num}. ${emoji} **${message}** (\`${hash}\`)  " >> "$OUTPUT_FILE"
      echo "   _${author}_ - ${date} (${relative_date})  " >> "$OUTPUT_FILE"
      echo "" >> "$OUTPUT_FILE"
      
      commit_num=$((commit_num + 1))
    fi
  done
else
  echo "_No se encontraron commits en los últimos 7 días._" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF

---

## 📋 Cambios Detallados por Categoría

### 🚀 Nuevas Funcionalidades

EOF

if [ "$feat_count" -gt 0 ]; then
  git log --since="7 days ago" --pretty=format:"- %s (\`%h\`) - %an" 2>/dev/null | grep -iE "^- (feat|feature|add)" >> "$OUTPUT_FILE" || echo "_No hay nuevas funcionalidades._" >> "$OUTPUT_FILE"
else
  echo "_No hay nuevas funcionalidades._" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF


### 🐛 Correcciones de Bugs

EOF

if [ "$fix_count" -gt 0 ]; then
  git log --since="7 days ago" --pretty=format:"- %s (\`%h\`) - %an" 2>/dev/null | grep -iE "^- (fix|bug|patch)" >> "$OUTPUT_FILE" || echo "_No hay correcciones de bugs._" >> "$OUTPUT_FILE"
else
  echo "_No hay correcciones de bugs._" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF


### 📝 Documentación

EOF

if [ "$docs_count" -gt 0 ]; then
  git log --since="7 days ago" --pretty=format:"- %s (\`%h\`) - %an" 2>/dev/null | grep -iE "^- (docs|doc)" >> "$OUTPUT_FILE" || echo "_No hay cambios en documentación._" >> "$OUTPUT_FILE"
else
  echo "_No hay cambios en documentación._" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF


### ⚡ Mejoras y Refactoring

EOF

if [ "$refactor_count" -gt 0 ]; then
  git log --since="7 days ago" --pretty=format:"- %s (\`%h\`) - %an" 2>/dev/null | grep -iE "^- (refactor|perf|improve)" >> "$OUTPUT_FILE" || echo "_No hay mejoras o refactoring._" >> "$OUTPUT_FILE"
else
  echo "_No hay mejoras o refactoring._" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF


### 🧪 Tests y CI/CD

EOF

if [ "$test_count" -gt 0 ]; then
  git log --since="7 days ago" --pretty=format:"- %s (\`%h\`) - %an" 2>/dev/null | grep -iE "^- (test|ci)" >> "$OUTPUT_FILE" || echo "_No hay cambios en tests o CI/CD._" >> "$OUTPUT_FILE"
else
  echo "_No hay cambios en tests o CI/CD._" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" <<EOF


---

## 🔧 Información Adicional

**Repositorio:** $(git config --get remote.origin.url 2>/dev/null || echo "N/A")  
**Total de commits:** $(git rev-list --count HEAD 2>/dev/null || echo "N/A")  
**Contribuidores:** $(git shortlog -sn --all | wc -l 2>/dev/null || echo "N/A")  

---

_Generado automáticamente por Jenkins Pipeline el $(date +'%Y-%m-%d %H:%M:%S')_

EOF

echo -e "${GREEN}✅ Release Notes generadas exitosamente: ${OUTPUT_FILE}${NC}"
echo ""
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
cat "$OUTPUT_FILE"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"


