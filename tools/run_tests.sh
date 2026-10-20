#!/usr/bin/env bash
# Pruebas del motor y de la red en una JVM normal (no necesita Android).
# Uso: tools/run_tests.sh [quick]   (necesita kotlinc en el PATH o en $KOTLINC)
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLINC="${KOTLINC:-kotlinc}"
SRC=app/src/main/java/com/nioro/spectrumcolor
OUT="${TMPDIR:-/tmp}/spectrum-tests"
mkdir -p "$OUT"
"$KOTLINC" "$SRC/Engine.kt" "$SRC/Net.kt" "$SRC/Match.kt" tools/SimTest.kt -include-runtime -d "$OUT/tests.jar" 2>&1 | grep -v "^warning" || true
java -cp "$OUT/tests.jar" com.nioro.spectrumcolor.SimTestKt "$@"
