# Publicar Color Flow en Google Play

Funciona igual que las actualizaciones de GitHub: cada cambio que llega a `main` lo compila
`.github/workflows/play.yml` como paquete de Play (`ColorFlow.aab`, versión **play**: el botón "Actualización" abre la ficha del juego en Google Play,
porque Play no permite que una app se actualice sola) y, si está la cuenta de servicio, **lo sube solo a Play Console**.
El paquete también queda en el release **`play`** de GitHub.

## Secrets necesarios (GitHub → Settings → Secrets and variables → Actions → New repository secret)
| Nombre | Qué es |
|---|---|
| `UPLOAD_KEYSTORE_B64` | la llave de subida (`upload.jks`, en base64) |
| `UPLOAD_KEYSTORE_PASSWORD` | su contraseña |
| `PLAY_SERVICE_ACCOUNT_JSON` | la llave JSON de la cuenta de servicio de Google Cloud con acceso a Play Console |

Variable opcional `PLAY_TRACK` (pestaña Variables): `internal` (por defecto), `alpha` (prueba cerrada),
`beta` (prueba abierta) o `production`. También se puede elegir al lanzar el workflow a mano.

## Una sola vez, en Play Console
1. **Crear la app**: nombre "Color Flow", juego, gratis. Paquete: `com.nioro.spectrumcolor`.
2. **Primera subida a mano** (Google no deja crear la app por la API): Pruebas → Prueba interna → Crear versión →
   subir `ColorFlow.aab` del release `play`. Aceptar la **firma de apps de Play** (Google guarda la llave final;
   la nuestra es solo de subida).
3. **Cuenta de servicio** (para que GitHub suba solo):
   Google Cloud Console → crear proyecto → activar "Google Play Android Developer API" → IAM → Cuentas de servicio →
   crear → Claves → Agregar clave → JSON (se descarga). En Play Console → Usuarios y permisos → Invitar usuario →
   el correo de la cuenta de servicio → permisos de "Versiones" sobre Color Flow. Pegar el JSON entero en el secret
   `PLAY_SERVICE_ACCOUNT_JSON`.
4. Completar la ficha con `docs/play/ficha.md` y las imágenes de `docs/play/`.
5. Cuentas personales nuevas: Google exige una **prueba cerrada con 12 testers durante 14 días** antes de pedir
   producción. Mientras tanto usar `PLAY_TRACK=alpha`.

Mientras la app no esté publicada, Play solo acepta versiones en **borrador**: el workflow lo intenta normal y,
si falla, la sube como borrador (hay que pulsar "Revisar y publicar" en Play Console).

La versión de GitHub (`ColorFlow.apk`) sigue igual. Ojo: la de Play la firma Google con otra llave, así que para
pasar de la APK de GitHub a la de Play hay que desinstalar primero (el progreso se borra).
