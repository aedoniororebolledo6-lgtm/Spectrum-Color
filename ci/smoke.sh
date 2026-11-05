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
  go icono:0;           shot 00_icono_a "ícono: esfera Aurora boreal"; alive
  go icono:1;           shot 00_icono_b "ícono: con disco de vidrio"; alive
  go ajustes;           shot 02_ajustes "ajustes"; alive
  go borrar1;           shot 03_borrar_aviso "ventana roja: lista de lo que se borra"; alive
  go borrar2;           shot 04_borrar_seguro "¿Estás seguro?"; alive
  go apariencias;       shot 05_apariencias "apariencias"; alive
  go esfera:81;         shot 05b_esfera_saturno "esfera nueva: Anillos de Saturno"; alive
  go esfera:91;         shot 05c_esfera_engranajes "esfera nueva: Engranajes"; alive
  go esfera:98;         shot 05d_esfera_caleidoscopio "esfera nueva: Caleidoscopio"; alive
  go estela:27;         shot 05e_estela_aurora "estela nueva: Aurora boreal"; alive
  go estela:40;         shot 05f_estela_supernova "estela nueva: Supernova"; alive
  go nivel:1;           shot 06_nivel1 "nivel 1"; alive
  go nivel:14 7;        shot 06b_nivel14 "nivel 14 (obstáculos de catálogo)"; alive
  go nivel:75 7;        shot 06c_nivel75 "nivel 75"; alive
  go nivel:118 7;       shot 06d_nivel118 "nivel 118"; alive
  go editor:60 6;       shot 06e_editor "editor de niveles"; alive
  go galeria:60 6;      shot 06f_galeria "galería del catálogo"; alive
  go infinito;          shot 07_infinito "modo infinito"; alive
  go mundos 6;          shot 08_mundos "carrusel de mundos de la Aventura"; alive
  go aventura:0 9;      shot 10_aventura_m1 "Aventura mundo 1, sala inicial"; alive
  go vitrina:4:0 9;     shot 11_vitrina_a "arquero, slime, bombardero"; sleep 2; shot 11b_vitrina_a "1 s después"; alive
  go vitrina:8:1 9;     shot 12_vitrina_b "mago, lobo, calavera"; sleep 2; shot 12b_vitrina_b "1 s después"; alive
  go vitrina:11:2 9;    shot 13_vitrina_c "gólem élite, caballero, arquero élite"; sleep 2; shot 13b_vitrina_c "1 s después"; alive
  go aventura:11 9;     shot 14_aventura_m12 "Aventura mundo 12, sala inicial"; alive
  go taller 9;          shot 15_taller "Taller: editor de salas de la Aventura"; alive
  # capturas para la ficha de Google Play, con la esfera Aurora boreal (la del ícono)
  go esfera:56;         shot 20_play_apariencias "Play: apariencias con Aurora boreal"; alive
  go inicio 8;          shot 21_play_inicio "Play: menú"; alive
  go nivel:5 7;         shot 22_play_nivel5 "Play: nivel 5"; alive
  go nivel:33 7;        shot 23_play_nivel33 "Play: nivel 33"; alive
  go nivel:75 7;        shot 24_play_nivel75 "Play: nivel 75"; alive
  go nivel:112 7;       shot 25_play_nivel112 "Play: nivel 112"; alive
  go infinito;          shot 26_play_infinito "Play: modo infinito"; alive
} > steps.txt 2>&1

# Fluidez: cuadros dibujados y cuántos fueron lentos en 8 s de cada pantalla (con toques en las que se juegan).
# El Android virtual dibuja sin tarjeta gráfica: los números sirven para comparar antes/después, no son los de un teléfono.
perf() {
  local name="$1" taps="$2"
  timeout 20 adb shell dumpsys gfxinfo $P reset > /dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8; do
    [ "$taps" = "1" ] && timeout 5 adb shell input tap 540 1700 > /dev/null 2>&1
    sleep 1
  done
  echo "[$name] $(timeout 20 adb shell dumpsys gfxinfo $P | grep -E 'Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile' | head -5 | sed 's/^ *//' | tr '\n' ';')"
}
{
  echo "== FLUIDEZ (8 s por pantalla)"
  go inicio 6;        perf menu 0
  go apariencias 5;   perf apariencias 0
  go nivel:14 6;      perf nivel14_jugando 1
  go nivel:118 6;     perf nivel118_jugando 1
  go infinito 5;      perf infinito_jugando 1
  go mundos 5;        perf mundos 0
  go aventura:0 8;    perf aventura_m1 0
  go vitrina:11:2 8;  perf aventura_enemigos 0
  echo "== MEMORIA"
  timeout 20 adb shell dumpsys meminfo $P | grep -E "TOTAL PSS|TOTAL:|Java Heap|Native Heap|Graphics" | head -6
} > perf.txt 2>&1

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
  echo; cat perf.txt
  echo "== lineas clave del log"; grep -E "spectrumcolor|AndroidRuntime|ANR|OutOfMemory|has died|Fatal|SIGSEGV|Davey" live.log | cut -c1-240 | tail -n 60
} > report.txt 2>&1
