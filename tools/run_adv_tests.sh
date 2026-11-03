#!/usr/bin/env bash
# Pruebas del modo Aventura en una JVM normal (no necesita Android).
# Uso: tools/run_adv_tests.sh [god|real] [ancho] [full|rooms|structs]
#   real  = sin modo dios: el bot recibe daño y debe ganar cada mundo (hasta 3 intentos con distinto ritmo)
#   god   = modo dios: comprueba que todo se puede recorrer (salas, estructuras, jefe)
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLINC="${KOTLINC:-kotlinc}"
SRC=app/src/main/java/com/nioro/spectrumcolor
OUT="${TMPDIR:-/tmp}/spectrum-adv-tests"
mkdir -p "$OUT"
"$KOTLINC" "$SRC/Adventure.kt" "$SRC/AdvStructs.kt" "$SRC/AdvAi.kt" tools/AdvTest.kt -include-runtime -d "$OUT/adv.jar" 2>&1 | grep -v "^warning" || true
java -cp "$OUT/adv.jar" AdvTestKt "${1:-real}" "${2:-640}" "${3:-full}"
