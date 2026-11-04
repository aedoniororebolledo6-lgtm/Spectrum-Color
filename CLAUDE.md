# Color Flow (repositorio Spectrum-Color) — guía para Claude

Juego Android nativo en Kotlin, sin librerías externas: todo se dibuja a mano en un Canvas (`GameView`).
El dueño habla **español**: responde siempre en español, claro y sin tecnicismos innecesarios.

## Publicar (el dueño lo pidió: hacerlo SIEMPRE al terminar un cambio)
Las actualizaciones le llegan solas al celular (`checkUpdate` en `GameView.kt` compara el número de compilación).
1. Trabajar en la rama asignada → push → esperar **"Verificar rama"** (`check.yml`) en verde.
2. Mirar las capturas del Android virtual si el cambio es visual (ver abajo).
3. Crear el PR a `main` y fusionarlo (`mcp__github__create_pull_request` + `mcp__github__merge_pull_request`),
   sin preguntar: el dueño lo autorizó para siempre.
4. `build.yml` compila en `main` y reemplaza el release **`latest`** (`ColorFlow.apk`, `ColorFlowDev.apk`, QR).
   Enlace fijo: `https://github.com/aedoniororebolledo6-lgtm/Spectrum-Color/releases/latest/download/ColorFlow.apk`.
5. Si la rama ya se fusionó antes, reiníciala desde `origin/main` antes de seguir
   (`git fetch origin main && git checkout -B <rama> origin/main`; si no hay commits nuevos en la rama no hace falta forzar).
6. Cambios solo de documentación (`*.md`, `tools/`, `ci/`) no disparan una compilación nueva en `main`.
7. Si la compilación de `main` falla, el error queda en el release **`build-error`** (sus notas traen las líneas clave).
8. **Google Play:** `play.yml` compila en `main` la versión `play` (`BuildConfig.STORE`: sin botón de actualizar) como
   `ColorFlow.aab`, la deja en el release **`play`** y la sube sola a Play Console (pista `PLAY_TRACK`, por defecto `internal`)
   con los secrets `UPLOAD_KEYSTORE_B64`, `UPLOAD_KEYSTORE_PASSWORD` y `PLAY_SERVICE_ACCOUNT_JSON`. Guía: `docs/google_play.md`.
   Textos e imágenes de la ficha en `docs/play/`. Nada que Play prohíba (descargar APK, actualizarse sola) en la versión `play`.

## Ver la app (los "ojos")
No hay SDK de Android en el contenedor, pero la app **sí se puede ver**:
1. Cada push (rama → `check.yml`, `main` → `build.yml`) corre el job `smoke`: instala la **versión de desarrollador**
   en un Android virtual (Pixel 5, API 30), recorre las pantallas con `ci/smoke.sh` y sube **capturas PNG + `report.txt`**
   al release **`diagnostico`** (unos 10–15 min después del push).
2. Descárgalas al scratchpad y ábrelas con **Read** (lee imágenes):
   ```bash
   bash tools/capturas.sh <carpeta_scratchpad>      # todas
   bash tools/capturas.sh <carpeta_scratchpad> 1    # solo las que empiezan por 1 (Aventura)
   ```
3. `report.txt` trae: instalación, si la app sigue viva tras cada paso, **errores graves** (FATAL EXCEPTION / ANR)
   y qué muestra cada captura. La primera línea dice de qué rama y commit son.
4. La app es un Canvas: no se puede tocar por texto. Para llegar a una pantalla se usan **atajos** (solo versión dev):
   `adb shell am start -S -n com.nioro.spectrumcolor/.MainActivity --es ir <atajo>`, con
   `inicio`, `ajustes`, `borrar1`, `borrar2`, `apariencias`, `esfera:N` y `estela:N` (eligen una y abren su pestaña), `nivel:N`, `infinito`, `mundos`, `aventura:W`
   (mundo W desde 0, salta la historia) y `vitrina:W:K` (sala con 3 enemigos nuevos, K = 0, 1 o 2, en modo dios),
   `taller` (editor de salas de la Aventura), `editor:N` y `galeria:N` (editor de niveles y catálogo de obstáculos).
   Para una pantalla nueva: añade el atajo en `GameView.debugGo` y un paso `go …; shot …` en `ci/smoke.sh`.
5. El Android virtual está en **inglés**: las capturas sirven también para ver textos sin traducir
   (si un texto sale en español, falta su `d(...)` en `I18n.kt`).
6. **Después de cualquier cambio visual, mira las capturas** antes de dar el trabajo por terminado.
   Si el dueño manda capturas de su teléfono en el chat, úsalas también.

## Compilar y probar en el contenedor
- `dl.google.com` está bloqueado (no hay Gradle/SDK de Android), pero:
  - `tools/compilar_local.sh <carpeta_cache>` compila **toda la app** con `kotlinc` y el framework de Android 14 que
    Robolectric publica en Maven Central (con `R`/`BuildConfig` de relleno). Solo verifica que compila; el APK lo hace CI.
  - `tools/run_tests.sh quick` — motor del juego normal y multijugador por red (22 pruebas).
  - `tools/run_adv_tests.sh real` — el bot juega la Aventura **con daño** y debe ganar los 12 mundos (hasta 3 intentos).
  - `tools/run_adv_tests.sh god 640 rooms` y `… god 640 structs` — todo se puede recorrer.
  - Las pruebas necesitan `kotlinc` en el PATH (o `$KOTLINC`); `compilar_local.sh` lo descarga a la carpeta de caché.
- `java -cp <tests.jar> com.nioro.spectrumcolor.SimTestKt kits [desde] [hasta]` — cada diseño de catálogo por separado
  (variables: `FAM=2,3` familias, `NS=3` colores, `BEAM=6000` amplitud de la búsqueda; con poca amplitud hay falsos "NO").
- `… SimTestKt feas 0,4,8 <niveles>` — ¿se puede completar el nivel con toques retrasados 0/4/8 pasos? (`MAXS` = segundos
  de juego máximos, `FEASDBG=1` muestra dónde se atascó). `… salt <máx> <niveles>` prueba variantes (`Levels.salts`).
- Workflow **"Factibilidad"** (`factibilidad.yml`): corre `feas` para los niveles 11..120, un trabajo por mundo y todos a la
  vez; se lanza solo al cambiar `Engine.kt`, `Kit.kt` o `SimTest.kt` en una rama. Tarda 10–40 min.
- Ojo con el estado compartido: las pruebas corren en varios hilos; nada en `Engine.kt`/`Kit.kt` puede usar búferes
  globales mutables durante la colisión (ya pasó con los vértices de `Kit`).
- En CI, "Verificar rama" corre lo mismo: compilación dev + estas pruebas + capturas.
- Revisa los resultados con `mcp__github__actions_*` (y `get_job_logs` si falla). No esperes con `sleep` en primer plano.

## Mapa del código (`app/src/main/java/com/nioro/spectrumcolor/`)
- `MainActivity.kt` — actividad única, orientación, atajos de prueba (`--es ir`).
- `GameView.kt` — todas las pantallas (menú, niveles, ajustes, apariencias, multijugador, editor, menú de Aventura),
  dibujo, toques, actualización (`checkUpdate`), ventana de "Borrar progreso" (`drawResetDlg`).
- `Engine.kt` — motor del juego normal: física, colisiones, generación de los 120 niveles y del modo infinito. Sin Android.
- `Kit.kt` — 200 obstáculos de catálogo (tipo `T.KIT`, campo `Ob.design`): 20 familias × 10 variantes armadas con piezas
  (aro, polígono, estrella, aspas, satélites, cinta). Se estrenan del nivel 11 al 120 (`Kit.unlockLevel`). Sin Android.
  `KitDraw.kt` los dibuja (vidrio líquido) y hace las miniaturas de la galería del editor.
- Taller de la Aventura (editor estilo Mario Maker): interfaz en `AdventureUi.kt` (funciones `mk*`, fase 4), salas en
  `Adv.loadMaker` y letras de los planos en `AdvMk` (`Adventure.kt`); se guardan en `Prefs.makerSlot(0..2)`.
- `Adventure.kt` — modo Aventura (estilo Castlevania): mapa de salas, estructuras, enemigos, jefes. Sin Android.
- `AdvAi.kt` — IA: steering (Seek/Arrive/Pursue/Flee/Wander), tiro balístico y árbol de comportamiento (ideas de gdx-ai).
- `AdvStructs.kt` — 650+ planos de estructuras. `AdventureUi.kt` — dibujo y controles de la Aventura.
- `Net.kt` + `Match.kt` — multijugador local (TCP + aviso UDP de salas) y reglas de la partida.
- `Prefs.kt` (guardado), `Audio.kt`, `I18n.kt` (español/inglés/ruso: todo texto nuevo va con su `d(...)`),
  `Skins.kt`, `Trails.kt`, `Theme.kt`, `Spooky.kt`/`Cats.kt`/`Dogs.kt` (temas), `Gl.kt` (ayudas de dibujo).
- `tools/` — pruebas (`SimTest.kt`, `AdvTest.kt`), `apply_levels.py` (niveles publicados desde el editor), audio.
- `ci/` — `smoke.sh` (Android virtual) y `publicar_diagnostico.sh`.

## Reglas del proyecto
- Todo en "unidades de diseño": el ancho del juego normal vale 1080; la Aventura mide 360 de alto.
- `Engine.kt`, `Adventure.kt`, `AdvAi.kt`, `Net.kt` y `Match.kt` no deben depender de Android (se prueban en la JVM).
- Textos visibles: en español en el código y traducidos en `I18n.kt`.
- No incluir identificadores de modelo en commits, PR ni código.

## Pendiente conocido
- La llave de firma (`signing/glassswitch.jks`) y su contraseña están en el repositorio público: conviene moverlas a
  "secrets" de GitHub (lo tiene que hacer el dueño en Settings → Secrets) y adaptar `build.yml`.
- `MainActivity` desactiva los avisos de `StrictMode` (operaciones lentas en el hilo principal).
