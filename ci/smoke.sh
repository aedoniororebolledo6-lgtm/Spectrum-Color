#!/usr/bin/env bash
# Abre Color Flow (versión de desarrollador) en un Android virtual, recorre las pantallas con los atajos
# `--es ir <atajo>` (ver GameView.debugGo) y guarda report.txt + capturas PNG.
# La app se dibuja en un Canvas: no hay textos que tocar, por eso se usan atajos en vez de toques.
set +e
P=com.nioro.spectrumcolor
APK="${APK:-ColorFlowDev.apk}"
adb install -r "$APK" > install.txt 2>&1
adb logcat -c
(adb logcat -v time > live.log 2>&1 &)

# go <atajo> [segundos]: reinicia la app directo en esa pantalla
go()   { timeout 60 adb shell am start -S -W -n $P/.MainActivity --es ir "$1" >> start.txt 2>&1; sleep "${2:-5}"; }
shot() { timeout 30 adb exec-out screencap -p > "$1.png"; echo "[$1] $2"; }
alive() { adb shell pidof $P > /dev/null 2>&1 && echo "  app viva" || echo "  LA APP SE CERRÓ"; }

{
  echo "== PASOS"
  go inicio 8;          shot 01_inicio "menú principal"; alive
  go ajustes;           shot 02_ajustes "ajustes"; alive
  go borrar1;           shot 03_borrar_aviso "ventana roja: lista de lo que se borra"; alive
  go borrar2;           shot 04_borrar_seguro "¿Estás seguro?"; alive
  go apariencias;       shot 05_apariencias "apariencias"; alive
  go nivel:1;           shot 06_nivel1 "nivel 1"; alive
  go infinito;          shot 07_infinito "modo infinito"; alive
  go mundos 6;          shot 08_mundos "carrusel de mundos de la Aventura"; alive
  go aventura:0 9;      shot 10_aventura_m1 "Aventura mundo 1, sala inicial"; alive
  go vitrina:4:0 9;     shot 11_vitrina_a "arquero, slime, bombardero"; sleep 2; shot 11b_vitrina_a "1 s después"; alive
  go vitrina:8:1 9;     shot 12_vitrina_b "mago, lobo, calavera"; sleep 2; shot 12b_vitrina_b "1 s después"; alive
  go vitrina:11:2 9;    shot 13_vitrina_c "gólem élite, caballero, arquero élite"; sleep 2; shot 13b_vitrina_c "1 s después"; alive
  go aventura:11 9;     shot 14_aventura_m12 "Aventura mundo 12, sala inicial"; alive
} > steps.txt 2>&1

timeout 60 adb logcat -d > full.log 2>&1
{
  echo "== instalacion"; cat install.txt
  echo "== arranque"; tail -n 20 start.txt
  echo "== proceso vivo?"; adb shell pidof $P || echo "NO ESTA VIVO"
  echo "== errores graves"
  grep -E "FATAL EXCEPTION|ANR in" full.log | head -20
  grep -E -A14 "FATAL EXCEPTION" full.log | head -80
  grep -E "ColorFlow" full.log | head -30
  echo; cat steps.txt
  echo "== lineas clave del log"; grep -E "spectrumcolor|AndroidRuntime|ANR|OutOfMemory|has died|Fatal|SIGSEGV|Davey" live.log | cut -c1-240 | tail -n 60
} > report.txt 2>&1
