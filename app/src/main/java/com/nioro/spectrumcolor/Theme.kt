package com.nioro.spectrumcolor

/** Los 12 mundos: nombre, 4 colores del juego y degradado del fondo. */
object Worlds {
    val names = arrayOf(
        "Prisma", "Aurora", "Océano", "Neón", "Cristal", "Atardecer",
        "Galaxia", "Lava", "Bosque", "Hielo", "Cósmico", "Espectro"
    )

    val colors: Array<IntArray> = arrayOf(
        intArrayOf(0xFF2BE4FF.toInt(), 0xFFFF4FB8.toInt(), 0xFFFFD84D.toInt(), 0xFF9A6BFF.toInt()),
        intArrayOf(0xFF2BFFB0.toInt(), 0xFF9A6BFF.toInt(), 0xFFFF5FA8.toInt(), 0xFF4DB8FF.toInt()),
        intArrayOf(0xFF2BC4FF.toInt(), 0xFFFF7A59.toInt(), 0xFF2BFFB0.toInt(), 0xFFFFD84D.toInt()),
        intArrayOf(0xFFFF2BD6.toInt(), 0xFF2BFF6B.toInt(), 0xFF2B9BFF.toInt(), 0xFFFFE12B.toInt()),
        intArrayOf(0xFFA0E8FF.toInt(), 0xFFFF9AD5.toInt(), 0xFFFFF09A.toInt(), 0xFFB79CFF.toInt()),
        intArrayOf(0xFFFF6B4D.toInt(), 0xFF8E5BFF.toInt(), 0xFFFFC94D.toInt(), 0xFFFF4F9A.toInt()),
        intArrayOf(0xFF8E5BFF.toInt(), 0xFF2BE4FF.toInt(), 0xFFFF4FB8.toInt(), 0xFF4DFFB0.toInt()),
        intArrayOf(0xFFFF5A3D.toInt(), 0xFF2BE4FF.toInt(), 0xFFFFB02B.toInt(), 0xFFFF2B6B.toInt()),
        intArrayOf(0xFF4DFF8E.toInt(), 0xFFFF6BA8.toInt(), 0xFFFFD84D.toInt(), 0xFF2BC4FF.toInt()),
        intArrayOf(0xFF7FD8FF.toInt(), 0xFFFF8FC4.toInt(), 0xFF5FFFD0.toInt(), 0xFFB58BFF.toInt()),
        intArrayOf(0xFFFF4FB8.toInt(), 0xFF2BFFD0.toInt(), 0xFF4D7BFF.toInt(), 0xFFFFB84D.toInt()),
        intArrayOf(0xFFFF4D4D.toInt(), 0xFF4D9BFF.toInt(), 0xFFFFD84D.toInt(), 0xFF4DFF8E.toInt())
    )

    /** Arriba, medio, abajo. */
    val bg: Array<IntArray> = arrayOf(
        intArrayOf(0xFF050816.toInt(), 0xFF0B1230.toInt(), 0xFF170F3C.toInt()),
        intArrayOf(0xFF04121A.toInt(), 0xFF082235.toInt(), 0xFF1A0F3A.toInt()),
        intArrayOf(0xFF030E1F.toInt(), 0xFF06223F.toInt(), 0xFF0A3550.toInt()),
        intArrayOf(0xFF0A0618.toInt(), 0xFF170A33.toInt(), 0xFF260B44.toInt()),
        intArrayOf(0xFF081423.toInt(), 0xFF11213B.toInt(), 0xFF1D2D52.toInt()),
        intArrayOf(0xFF1A0A14.toInt(), 0xFF2B1124.toInt(), 0xFF3D1733.toInt()),
        intArrayOf(0xFF05030F.toInt(), 0xFF0E0828.toInt(), 0xFF1D0B42.toInt()),
        intArrayOf(0xFF150605.toInt(), 0xFF270A09.toInt(), 0xFF3B1210.toInt()),
        intArrayOf(0xFF03140D.toInt(), 0xFF062417.toInt(), 0xFF0B3524.toInt()),
        intArrayOf(0xFF06121F.toInt(), 0xFF0C2238.toInt(), 0xFF153858.toInt()),
        intArrayOf(0xFF080414.toInt(), 0xFF150826.toInt(), 0xFF280C40.toInt()),
        intArrayOf(0xFF05060F.toInt(), 0xFF0D1030.toInt(), 0xFF1A1245.toInt())
    )
}
