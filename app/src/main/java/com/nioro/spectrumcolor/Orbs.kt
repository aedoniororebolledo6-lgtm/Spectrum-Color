package com.nioro.spectrumcolor

/** Esfera creada por el usuario en el editor (solo versión de desarrollador). */
class Orb(
    var kind: Int = 1,   // 0 clásica, 1 estelar, 2 animada
    var pat: Int = 0,    // patrón dentro del tipo
    var c1: Int = 0,     // color principal (índice de Orbs.pal)
    var c2: Int = 3,     // color secundario
    var n: Int = 5,      // cantidad 1..8
    var sz: Int = 3,     // tamaño 1..5
    var sp: Int = 3,     // velocidad 1..5 (las clásicas no se mueven)
    var bd: Int = 0      // borde: 0 normal, 1 color, 2 dorado, 3 arcoíris
) {
    fun copy() = Orb(kind, pat, c1, c2, n, sz, sp, bd)

    fun serialize(): String = "$kind,$pat,$c1,$c2,$n,$sz,$sp,$bd"

    companion object {
        const val SLOTS = 12
        const val FIRST_ID = 100
        const val PREVIEW_ID = 999

        val pal = intArrayOf(
            0xFFFF4F6B.toInt(), 0xFFFF9A3D.toInt(), 0xFFFFD84F.toInt(), 0xFF4FFF8A.toInt(),
            0xFF4FE6FF.toInt(), 0xFF4F8BFF.toInt(), 0xFFB04FFF.toInt(), 0xFFFF6FD8.toInt(),
            0xFFFFFFFF.toInt(), 0xFFB8C4D6.toInt(), 0xFF8A5A3C.toInt(), 0xFF1B1F3B.toInt()
        )
        val kindNames = arrayOf("Clásica", "Estelar", "Animada")
        val patNames = arrayOf(
            arrayOf("Liso", "Rayas", "Lunares", "Cuadros", "Anillos", "Rombos"),
            arrayOf("Estrellas", "Constelación", "Nebulosa", "Órbitas", "Eclipse", "Supernova"),
            arrayOf("Remolino", "Pulso", "Ondas", "Destellos", "Ruleta", "Burbujas")
        )
        val borderNames = arrayOf("Normal", "Color", "Dorado", "Arcoíris")

        fun parse(s: String?): Orb? {
            if (s == null) return null
            val p = s.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (p.size < 8) return null
            return Orb(
                p[0].coerceIn(0, 2), p[1].coerceIn(0, 5), p[2].coerceIn(0, pal.size - 1), p[3].coerceIn(0, pal.size - 1),
                p[4].coerceIn(1, 8), p[5].coerceIn(1, 5), p[6].coerceIn(1, 5), p[7].coerceIn(0, 3)
            )
        }
    }
}
