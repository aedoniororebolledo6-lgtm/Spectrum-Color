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

/**
 * Mini IA 100 % local para inventar esferas: un modelo de gustos (regresión logística de 50 rasgos)
 * que aprende en el teléfono de lo que marcas con «Me gusta» / «No me gusta», más una búsqueda
 * evolutiva (muestras al azar + mutaciones de tus favoritas) que elige la mejor candidata.
 * No usa internet ni archivos de modelo: todo ocurre en el procesador del celular.
 */
object OrbAI {
    const val DIM = 3 + 18 + 12 + 12 + 3 + 4 + 1 + 1

    fun feat(o: Orb): FloatArray {
        val x = FloatArray(DIM)
        x[o.kind] = 1f
        x[3 + o.kind * 6 + o.pat] = 1f
        x[21 + o.c1] = 1f
        x[33 + o.c2] = 1f
        x[45] = (o.n - 1) / 7f
        x[46] = (o.sz - 1) / 4f
        x[47] = (o.sp - 1) / 4f
        x[48 + o.bd] = 1f
        x[52] = if (o.c1 != o.c2) 1f else 0f
        x[53] = 1f
        return x
    }

    private fun dot(w: FloatArray, x: FloatArray): Float {
        var s = 0f
        for (i in 0 until DIM) s += w[i] * x[i]
        return s
    }

    /** Gusto previsto (con un empujón para que los dos colores se distingan). */
    fun score(w: FloatArray, o: Orb): Float = dot(w, feat(o)) + (if (o.c1 == o.c2) -1.5f else 0.3f)

    /** Un voto: label 1 = me gusta, 0 = no me gusta. */
    fun train(w: FloatArray, o: Orb, label: Float) {
        val x = feat(o)
        val p = 1f / (1f + Math.exp(-dot(w, x).toDouble()).toFloat())
        val g = 0.7f * (label - p)
        for (i in 0 until DIM) w[i] = (w[i] + g * x[i]).coerceIn(-6f, 6f)
    }

    fun random(rnd: java.util.Random): Orb {
        val k = rnd.nextInt(3)
        return Orb(k, rnd.nextInt(6), rnd.nextInt(Orb.pal.size), rnd.nextInt(Orb.pal.size), 1 + rnd.nextInt(8), 1 + rnd.nextInt(5), 1 + rnd.nextInt(5), rnd.nextInt(4))
    }

    /** Cambia 1 a 3 rasgos de la esfera. */
    fun mutate(o: Orb, rnd: java.util.Random, genes: Int = 1 + rnd.nextInt(3)): Orb {
        val m = o.copy()
        repeat(genes) {
            when (rnd.nextInt(8)) {
                0 -> { m.kind = rnd.nextInt(3); m.pat = rnd.nextInt(6) }
                1 -> m.pat = rnd.nextInt(6)
                2 -> m.c1 = rnd.nextInt(Orb.pal.size)
                3 -> m.c2 = rnd.nextInt(Orb.pal.size)
                4 -> m.n = (m.n + rnd.nextInt(3) - 1).coerceIn(1, 8)
                5 -> m.sz = (m.sz + rnd.nextInt(3) - 1).coerceIn(1, 5)
                6 -> m.sp = (m.sp + rnd.nextInt(3) - 1).coerceIn(1, 5)
                else -> m.bd = rnd.nextInt(4)
            }
        }
        return m
    }

    /** Propone una esfera nueva: la de mayor gusto previsto entre 90 candidatas (con algo de azar). */
    fun generate(w: FloatArray, rnd: java.util.Random, favs: List<Orb>, avoid: Orb?): Orb {
        var best: Orb? = null
        var bestS = -1e9f
        for (i in 0 until 90) {
            val cand = if (i < 50 || favs.isEmpty()) random(rnd) else mutate(favs[rnd.nextInt(favs.size)], rnd)
            if (avoid != null && cand.serialize() == avoid.serialize()) continue
            val s = score(w, cand) + rnd.nextFloat() * 1.2f
            if (s > bestS) { bestS = s; best = cand }
        }
        return best ?: random(rnd)
    }

    fun encode(w: FloatArray, votes: Int): String = votes.toString() + ";" + w.joinToString(",") { "%.3f".format(java.util.Locale.US, it) }

    fun decodeVotes(s: String?): Int = s?.substringBefore(';')?.toIntOrNull() ?: 0

    fun decode(s: String?): FloatArray {
        val w = FloatArray(DIM)
        val parts = s?.substringAfter(';', "")?.split(",") ?: return w
        for (i in 0 until minOf(DIM, parts.size)) w[i] = parts[i].toFloatOrNull() ?: 0f
        return w
    }
}
