#!/usr/bin/env bash
# Compila TODA la app en el contenedor sin el SDK de Android (dl.google.com está bloqueado):
# usa kotlinc y el framework de Android 14 que Robolectric publica en Maven Central, con R/BuildConfig de relleno.
# Solo verifica que el código compila; el APK de verdad lo hace GitHub Actions.
# Uso: tools/compilar_local.sh [carpeta_cache]
set -euo pipefail
cd "$(dirname "$0")/.."
CACHE="${1:-${TMPDIR:-/tmp}/colorflow-build}"
mkdir -p "$CACHE"
if [ ! -x "$CACHE/kotlinc/bin/kotlinc" ]; then
  curl -sSL -o "$CACHE/kc.zip" https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip
  (cd "$CACHE" && unzip -q -o kc.zip)
fi
JAR="$CACHE/android-all.jar"
[ -s "$JAR" ] || curl -sSL -o "$JAR" https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
SRC=app/src/main/java/com/nioro/spectrumcolor
# R y BuildConfig de relleno con los recursos que usa el código
{
  echo "package com.nioro.spectrumcolor"
  echo "object BuildConfig { const val DEV = true; const val STORE = false }"
  echo "object R { object raw {"
  i=1; for f in app/src/main/res/raw/*; do n=$(basename "$f"); echo "  const val ${n%.*} = $i"; i=$((i+1)); done
  echo "} }"
} > "$CACHE/R.kt"
rm -rf "$CACHE/out"
if "$CACHE/kotlinc/bin/kotlinc" -J-Xmx4g -jvm-target 17 -cp "$JAR" $SRC/*.kt "$CACHE/R.kt" -d "$CACHE/out" > "$CACHE/build.log" 2>&1; then
  echo "COMPILA OK ($(grep -c 'warning:' "$CACHE/build.log" || true) avisos)"
else
  grep -E ": error:" -A3 "$CACHE/build.log" | head -60
  echo "ERRORES DE COMPILACIÓN"; exit 1
fi
