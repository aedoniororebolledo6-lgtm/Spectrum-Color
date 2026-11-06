package com.nioro.spectrumcolor

/**
 * Términos y condiciones que se aceptan al abrir el juego por primera vez (pantalla en GameView.drawTerms).
 * Los textos están en español y se traducen en I18n.kt. Si se cambia algo importante, subir VERSION: el juego
 * vuelve a pedir que se acepten. La misma versión, para leer en la web, está en docs/terminos.md.
 */
object Terminos {
    const val VERSION = 1

    const val INTRO = "Bienvenido a Color Flow. Antes de jugar, lee estos términos. Al marcar la casilla y tocar «Aceptar y jugar» confirmas que los aceptas."

    val SECTIONS = arrayOf(
        "1. Licencia de uso" to
            "Con tu compra en Google Play obtienes una licencia personal, no exclusiva e intransferible para jugar Color Flow en tus dispositivos. El juego, sus gráficos, su música y su código pertenecen a su creador.",
        "2. Compras y reembolsos" to
            "Color Flow se compra una sola vez: no tiene anuncios ni compras dentro del juego. Los pagos y los reembolsos los gestiona Google Play según sus propias políticas.",
        "3. Uso correcto" to
            "No está permitido copiar, modificar, descompilar, revender ni distribuir el juego, ni usarlo con fines ilegales. En el multijugador local, usa nombres respetuosos.",
        "4. Tu contenido" to
            "Tu progreso y las salas que crees en el Taller se guardan solo en tu teléfono. Si desinstalas el juego o borras el progreso, se pierden.",
        "5. Privacidad" to
            "Color Flow no recopila datos personales y no necesita internet para funcionar. Puedes leer la política de privacidad completa con el botón «Privacidad».",
        "6. Aviso de salud" to
            "El juego tiene luces, destellos y cambios rápidos de color. Si tú o alguien de tu familia ha tenido crisis de epilepsia o es sensible a la luz, consulta a un médico antes de jugar. Si sientes mareo, molestias en la vista o malestar, deja de jugar y descansa.",
        "7. Garantía y responsabilidad" to
            "El juego se ofrece tal como está. Trabajamos para que funcione bien, pero no podemos garantizar que no tenga errores. En la medida en que lo permita la ley, el creador no responde por daños indirectos derivados de su uso.",
        "8. Cambios en los términos" to
            "Estos términos pueden actualizarse. Si cambian, el juego te pedirá que los aceptes de nuevo.",
        "9. Contacto y ley aplicable" to
            "Si tienes dudas o problemas, escribe al correo de contacto de la ficha de Google Play. Estos términos se rigen por las leyes de Chile, sin perjuicio de los derechos que te da como consumidor la ley de tu país.",
    )

    const val UPDATED = "Última actualización: 6 de noviembre de 2026"

    const val PRIVACY_URL = "https://github.com/aedoniororebolledo6-lgtm/Spectrum-Color/blob/main/docs/privacidad.md"
}
