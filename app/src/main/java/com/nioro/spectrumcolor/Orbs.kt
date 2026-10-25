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

/** Texto ↔ esfera: arma el pedido para el modelo de lenguaje, lee su respuesta y tiene reglas de respaldo. */
object OrbText {
    private val system = "Eres un diseñador de esferas para un videojuego. Responde SOLO con 8 números separados por comas, sin texto: " +
        "tipo,patron,color1,color2,cantidad,tamano,velocidad,borde.\n" +
        "tipo: 0=clasica 1=estelar 2=animada.\n" +
        "patron (0-5): clasica 0 liso,1 rayas,2 lunares,3 cuadros,4 anillos,5 rombos; " +
        "estelar 0 estrellas,1 constelacion,2 nebulosa,3 orbitas,4 eclipse,5 supernova; " +
        "animada 0 remolino,1 pulso,2 ondas,3 destellos,4 ruleta,5 burbujas.\n" +
        "colores (0-11): 0 rojo,1 naranja,2 amarillo,3 verde,4 celeste,5 azul,6 violeta,7 rosa,8 blanco,9 plata,10 cafe,11 azul oscuro.\n" +
        "cantidad 1-8, tamano 1-5, velocidad 1-5, borde: 0 normal,1 color,2 dorado,3 arcoiris."

    private val shots = arrayOf(
        "remolino de fuego" to "2,0,0,1,4,3,4,2",
        "galaxia morada con estrellas" to "1,2,6,5,4,3,2,0",
        "pelota clasica con rayas azules" to "0,1,5,8,5,3,1,0",
        "burbujas de agua" to "2,5,4,8,6,3,3,1",
        "sol brillante" to "1,5,2,1,8,4,3,2"
    )

    fun prompt(text: String): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        for ((q, a) in shots) sb.append("<|im_start|>user\n").append(q).append("<|im_end|>\n<|im_start|>assistant\n").append(a).append("<|im_end|>\n")
        sb.append("<|im_start|>user\n").append(text.trim().take(120)).append("<|im_end|>\n<|im_start|>assistant\n")
        return sb.toString()
    }

    /** Los 8 primeros números de la respuesta, o null si no hay 8. */
    fun parse(reply: String): Orb? {
        val nums = Regex("-?\\d+").findAll(reply).map { it.value.toInt() }.toList()
        if (nums.size < 8) return null
        val n = nums.take(8)
        return Orb(n[0].coerceIn(0, 2), n[1].coerceIn(0, 5), n[2].coerceIn(0, 11), n[3].coerceIn(0, 11),
            n[4].coerceIn(1, 8), n[5].coerceIn(1, 5), n[6].coerceIn(1, 5), n[7].coerceIn(0, 3))
    }

    private val colorWords = arrayOf(
        0 to listOf("roj", "fuego", "lava", "sangre", "rubi"), 1 to listOf("naranj", "ambar", "calabaza"),
        2 to listOf("amarill", "dorad", "oro", "sol", "rayo"), 3 to listOf("verd", "hoja", "esmeralda", "slime", "bosque"),
        4 to listOf("celest", "cian", "hielo", "agua", "cielo"), 5 to listOf("azul", "oceano", "mar "),
        6 to listOf("moro", "morad", "violet", "lila", "galaxia", "magia"), 7 to listOf("rosa", "rosad", "chicle"),
        8 to listOf("blanc", "nieve", "perla"), 9 to listOf("plat", "metal", "acero", "gris"),
        10 to listOf("caf", "chocolate", "madera", "tierra"), 11 to listOf("oscur", "noche", "negr", "vacio", "abismo")
    )

    private fun colorsIn(t: String): List<Int> {
        val low = " " + t.lowercase() + " "
        val hits = ArrayList<Pair<Int, Int>>()
        for ((c, ws) in colorWords) for (w in ws) { val i = low.indexOf(w); if (i >= 0) hits.add(i to c) }
        return hits.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** Reglas simples por palabras clave: respaldo cuando el modelo no responde bien. */
    fun rules(text: String): Orb {
        val t = text.lowercase()
        fun has(vararg ws: String) = ws.any { t.contains(it) }
        val o = Orb()
        when {
            has("remolino", "espiral", "tornado", "torbellino") -> { o.kind = 2; o.pat = 0 }
            has("pulso", "latido", "sonar") -> { o.kind = 2; o.pat = 1 }
            has("onda", "agua", "mar", "oceano") -> { o.kind = 2; o.pat = 2 }
            has("destello", "chispa", "brillo", "purpurina") -> { o.kind = 2; o.pat = 3 }
            has("ruleta", "giratori", "rueda") -> { o.kind = 2; o.pat = 4 }
            has("burbuja", "espuma") -> { o.kind = 2; o.pat = 5 }
            has("constelacion") -> { o.kind = 1; o.pat = 1 }
            has("nebulosa", "galaxia") -> { o.kind = 1; o.pat = 2 }
            has("orbita", "planeta", "sistema solar") -> { o.kind = 1; o.pat = 3 }
            has("eclipse", "luna", "agujero") -> { o.kind = 1; o.pat = 4 }
            has("supernova", "sol", "explosion") -> { o.kind = 1; o.pat = 5 }
            has("estrella", "espacio", "cosmos", "noche") -> { o.kind = 1; o.pat = 0 }
            has("raya") -> { o.kind = 0; o.pat = 1 }
            has("lunar", "punto") -> { o.kind = 0; o.pat = 2 }
            has("cuadro", "ajedrez") -> { o.kind = 0; o.pat = 3 }
            has("anillo", " aro") -> { o.kind = 0; o.pat = 4 }
            has("rombo", "diamante") -> { o.kind = 0; o.pat = 5 }
            has("clasic", "liso", "simple") -> { o.kind = 0; o.pat = 0 }
            has("fuego", "llama", "lava") -> { o.kind = 2; o.pat = 0 }
            else -> { o.kind = 1; o.pat = 0 }
        }
        val cs = colorsIn(text)
        o.c1 = cs.getOrElse(0) { if (has("fuego", "llama", "lava")) 0 else if (o.kind == 1) 6 else 4 }
        o.c2 = cs.getOrElse(1) { if (has("fuego", "llama", "lava")) 1 else if (o.c1 == 8) 4 else 8 }
        o.bd = if (has("arcoiris")) 3 else if (has("dorad", "oro")) 2 else 0
        o.sp = if (has("rapid")) 5 else if (has("lent")) 1 else 3
        return o
    }

    /** Une la respuesta del modelo con los colores que pediste (los modelos chicos se equivocan en eso). */
    fun finish(reply: String, text: String): Pair<Orb, Boolean> {
        val m = parse(reply) ?: return Pair(rules(text), false)
        val cs = colorsIn(text)
        if (cs.isNotEmpty()) m.c1 = cs[0]
        if (cs.size > 1) m.c2 = cs[1]
        if (m.c1 == m.c2) m.c2 = if (m.c1 == 8) 4 else 8
        return Pair(m, true)
    }
}
