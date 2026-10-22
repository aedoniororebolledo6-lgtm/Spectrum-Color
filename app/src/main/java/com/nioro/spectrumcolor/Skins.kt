package com.nioro.spectrumcolor

/** Apariencias de la bola. `need` = estrellas totales necesarias para usarlas. Las 0..39 son clásicas y las 40..59 animadas. */
object Skins {
    const val ANIM_FIRST = 40

    val names = arrayOf(
        "Cristal", "Aurora", "Rayas", "Lunares", "Estrella", "Planeta",
        "Diamante", "Núcleo", "Oro", "Neón", "Arcoíris", "Galaxia",
        "Relámpago", "Corazón", "Ojo", "Cuadros", "Burbujas", "Ondas", "Sol", "Pixel",
        "Voleibol", "Cápsula", "Expansión", "Calabaza", "Cine", "Bomba", "Hongo", "Pergamino",
        "Enfoque", "Flor", "Escudo", "Trofeo", "Mariposa", "Alerta", "Corona", "Equilibrio",
        "Hoja", "Copo", "Gota", "Luna",
        // animadas: naturaleza
        "Fuego", "Humo", "Agua", "Tormenta", "Lava", "Escarcha", "Remolino", "Lluvia", "Luciérnagas", "Brasas",
        // animadas: espacio
        "Nebulosa", "Agujero negro", "Supernova", "Cometa", "Sistema solar", "Pulsar", "Aurora boreal", "Espiral", "Eclipse", "Plasma"
    )

    val need = intArrayOf(
        0, 0, 4, 8, 12, 16, 20, 25, 30, 36, 42, 48, 55, 62, 70, 78, 86, 95, 104, 114,
        124, 135, 146, 158, 170, 183, 196, 210, 224, 238, 252, 266, 280, 294, 308, 322, 334, 344, 352, 358,
        10, 28, 46, 64, 82, 100, 118, 136, 154, 172, 190, 208, 226, 244, 262, 280, 298, 316, 334, 352
    )

    fun isAnim(k: Int): Boolean = k >= ANIM_FIRST
}
