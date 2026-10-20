# Spectrum Color

Juego de celular (Android nativo, Kotlin) tipo *Color Switch* con diseño **liquid glass**.
Una pelota de cristal sube atravesando obstáculos de colores: solo puedes pasar por el color de tu pelota.

## Cómo se juega

- Toca la pantalla para impulsar la pelota hacia arriba (cae sola por la gravedad).
- Atraviesa solo el color de tu pelota. Los anillos repiten sus colores dos veces por vuelta (abajo y arriba igual) y las barras son
  de un solo carril: basta esperar a que llegue "tu" color y cruzar de un salto. En cuadrados y triángulos a veces toca esperar dentro.
- Los niveles empiezan con 2 colores, pasan a 3 y solo los últimos tres (18 a 20) usan 4.
- El orbe de colores cambia el color de tu pelota (el centro del orbe muestra el color que te tocará).
- Cada obstáculo superado suma 1 punto.

## Modos

| Modo | Qué es |
|------|--------|
| **Jugar** | 20 niveles jugables, de 3 estrellas cada uno (3 = sin morir, 2 = hasta 2 intentos fallidos). Se abren de uno en uno. |
| **Modo infinito** | Recorrido sin fin que se pone más difícil. Se guarda el récord. |
| **Multijugador local** | Dos celulares en el mismo Wi-Fi (o uno haciendo punto de acceso). Mismo recorrido para los dos; gana quien sume más puntos en total tras 1, 3 o 5 rondas (75 s por ronda). Hay revancha. |

El diseño completo tiene **120 niveles en 12 mundos**; por ahora solo están disponibles los dos primeros mundos (20 niveles).
Los demás mundos aparecen como *Coming soon*. Para abrir más basta con subir `Levels.PLAYABLE` en `Engine.kt`.

## Menús

Menú principal, selector de niveles por mundos (desliza o usa las flechas), pausa (continuar, reiniciar, música/efectos, salir al menú),
nivel completado, fin de partida y ajustes: música y efectos (activar y volumen), vibración, dificultad (Normal / Fácil),
nombre del jugador, abrir todos los niveles y borrar progreso. El botón "atrás" de Android funciona en todas las pantallas.

## Multijugador, paso a paso

1. Los dos celulares en el mismo Wi-Fi (o uno crea un punto de acceso y el otro se conecta).
2. Uno toca **Multijugador local → Crear sala**. El otro toca **Unirse a una sala** y elige la sala que aparece
   (si no aparece, **Escribir IP** con la IP que muestra el anfitrión).
3. El anfitrión toca **¡Empezar!**. Cuenta atrás y a jugar.

## Instalar en el celular (QR)

1. Sube este proyecto a la rama `main` del repositorio de GitHub (público).
2. GitHub Actions compila y firma el APK solo (pestaña **Actions**, ~2 minutos).
3. En **Releases → "Spectrum Color (última versión)"** quedan `SpectrumColor.apk` y `qr.png`.
4. Escanea el QR con el celular, descarga el APK y permite "instalar apps desconocidas".

Enlace directo, siempre a la última versión:

```
https://github.com/aedoniororebolledo6-lgtm/Spectrum-Color/releases/latest/download/SpectrumColor.apk
```

Las ramas que no son `main` (por ejemplo `dev`) solo compilan para comprobar que todo funciona; no publican el APK.

## Estructura

- `app/src/main/java/com/nioro/spectrumcolor/Engine.kt`: física, colisiones, niveles y recorridos (sin dependencias de Android).
- `GameView.kt`: pantallas, menús y dibujo (todo con Canvas). `Gl.kt`: herramientas de dibujo de cristal.
- `Net.kt` y `Match.kt`: red local (TCP + aviso por UDP) y reglas del duelo por puntos.
- `Audio.kt`, `Prefs.kt`, `Theme.kt`, `MainActivity.kt`: sonido, ajustes/progreso, colores de los mundos, actividad.
- `res/raw`: música y efectos (sintetizados con `tools/gen_audio.py`, sin samples de terceros).
- `tools/`: pruebas en JVM (`tools/run_tests.sh`): jugadores simulados, búsqueda de si cada nivel es posible (`feas`, `salt`), y red real en 127.0.0.1.
- `signing/glassswitch.jks`: llave de firma (para que las actualizaciones se instalen sin desinstalar).
