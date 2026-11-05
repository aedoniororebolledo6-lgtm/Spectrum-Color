#!/usr/bin/env bash
# Descarga las capturas y el informe de la última prueba en el Android virtual (release "diagnostico").
# Uso: bash tools/capturas.sh [carpeta_destino] [filtro]
#   bash tools/capturas.sh /tmp/shots        → todas
#   bash tools/capturas.sh /tmp/shots 1      → solo las que empiezan por "1" (Aventura)
set -e
DEST="${1:-./capturas}"
FILTRO="${2:-}"
REPO="aedoniororebolledo6-lgtm/Spectrum-Color"
BASE="https://github.com/$REPO/releases/download/diagnostico"
mkdir -p "$DEST"
curl -sSL -f -o "$DEST/report.txt" "$BASE/report.txt" || { echo "Todavía no hay informe (release diagnostico)"; exit 1; }
# nombres de las capturas: salen de los pasos "[nombre]" del informe
grep -v 'Total frames' "$DEST/report.txt" | grep -o '^\[[0-9a-z][0-9a-z_]*\]' | tr -d '[]' | sort -u | while read -r n; do
  case "$n" in "$FILTRO"*) ;; *) continue ;; esac
  curl -sSL -f -o "$DEST/$n.png" "$BASE/$n.png" && echo "ok  $n.png" || echo "sin captura: $n"
done
echo "== errores graves del informe:"
sed -n '/== errores graves/,/== PASOS/p' "$DEST/report.txt" | sed '1d;$d'
grep -E "LA APP SE CERRÓ" "$DEST/report.txt" || true
