# Glass Switch

Juego de celular (Android nativo, Kotlin) tipo *Color Switch* con diseño **liquid glass**.
Una pelota de cristal sube atravesando anillos y barras de colores: solo puedes pasar por
el color de tu pelota. Junta estrellas, cruza los orbes de 4 colores para cambiar de color
y rompe tu récord.

## Cómo se juega

- Toca la pantalla para impulsar la pelota hacia arriba (cae sola por la gravedad).
- Atraviesa solo el segmento del mismo color que tu pelota.
- El orbe de 4 colores cambia el color de tu pelota.
- Cada estrella suma 1 punto.

## Instalar en el celular (QR)

1. Sube este proyecto a la rama `main` de tu repositorio de GitHub (el repositorio debe ser **público**).
2. GitHub Actions compila el APK solo (pestaña **Actions**, tarda unos 5 minutos).
3. Al terminar, se publica en **Releases → "Glass Switch (última versión)"** con dos archivos:
   - `GlassSwitch.apk`: el juego.
   - `qr.png`: el QR que apunta siempre a la última versión.
4. Escanea el QR con el celular, descarga el APK y permite "instalar apps desconocidas" cuando Android lo pida.

Cada vez que subas cambios a `main`, se compila una versión nueva y se instala encima de la anterior
(el número de versión sube solo y el APK siempre va firmado con la misma llave).

Enlace directo, siempre a la última versión:

```
https://github.com/TU_USUARIO/TU_REPOSITORIO/releases/latest/download/GlassSwitch.apk
```

## Estructura

- `app/src/main/java/com/nioro/glassswitch/GameView.kt`: el juego completo (lógica y dibujo en Canvas).
- `app/src/main/java/com/nioro/glassswitch/MainActivity.kt`: pantalla completa e inmersiva.
- `.github/workflows/build.yml`: compila, firma, publica el APK y genera el QR.
- `signing/glassswitch.jks`: llave de firma (para que las actualizaciones se instalen sin desinstalar).
