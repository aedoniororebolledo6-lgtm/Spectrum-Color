package com.nioro.spectrumcolor

/** Esfera creada por el usuario en el editor (solo versión de desarrollador). */
class Orb(
    var kind: Int = 1,   // 0 clásica, 1 estelar, 2 animada, 3 figura
    var pat: Int = 0,    // patrón dentro del tipo
    var c1: Int = 0,     // color principal (índice de Orbs.pal)
    var c2: Int = 3,     // color secundario
    var n: Int = 5,      // cantidad 1..8
    var sz: Int = 3,     // tamaño 1..5
    var sp: Int = 3,     // velocidad 1..5 (las clásicas no se mueven)
    var bd: Int = 0,     // borde: 0 normal, 1 color, 2 dorado, 3 arcoíris
    var art: String = "" // dibujo creado por la IA (tipo 5): formas separadas por «;»
) {
    fun copy() = Orb(kind, pat, c1, c2, n, sz, sp, bd, art)

    fun serialize(): String = "$kind,$pat,$c1,$c2,$n,$sz,$sp,$bd" + (if (art.isNotEmpty()) "|$art" else "")

    companion object {
        const val SLOTS = 12
        const val FIRST_ID = 100
        const val PREVIEW_ID = 999

        val pal = intArrayOf(
            0xFFFF4F6B.toInt(), 0xFFFF9A3D.toInt(), 0xFFFFD84F.toInt(), 0xFF4FFF8A.toInt(),
            0xFF4FE6FF.toInt(), 0xFF4F8BFF.toInt(), 0xFFB04FFF.toInt(), 0xFFFF6FD8.toInt(),
            0xFFFFFFFF.toInt(), 0xFFB8C4D6.toInt(), 0xFF8A5A3C.toInt(), 0xFF1B1F3B.toInt()
        )
        val kindNames = arrayOf("Clásica", "Estelar", "Animada", "Figura", "Animal", "Dibujo IA")
        fun patCount(kind: Int): Int = if (kind == 5) 1 else if (kind == 4) Animals.count else if (kind == 3) 16 else 6
        fun patName(kind: Int, pat: Int): String = if (kind == 4) Animals.names.getOrElse(pat) { "?" } else patNames[kind][pat]
        val patNames = arrayOf(
            arrayOf("Liso", "Rayas", "Lunares", "Cuadros", "Anillos", "Rombos"),
            arrayOf("Estrellas", "Constelación", "Nebulosa", "Órbitas", "Eclipse", "Supernova"),
            arrayOf("Remolino", "Pulso", "Ondas", "Destellos", "Ruleta", "Burbujas"),
            arrayOf("Gato", "Perro", "Corazón", "Estrella", "Luna", "Flor", "Calavera", "Fantasma",
                "Pez", "Mariposa", "Rayo", "Corona", "Hoja", "Gota", "Huella", "Sol")
        )
        val borderNames = arrayOf("Normal", "Color", "Dorado", "Arcoíris")

        fun parse(s: String?): Orb? {
            if (s == null) return null
            val p = s.substringBefore('|').split(",").mapNotNull { it.trim().toIntOrNull() }
            if (p.size < 8) return null
            val k = p[0].coerceIn(0, 5)
            return Orb(
                k, p[1].coerceIn(0, patCount(k) - 1), p[2].coerceIn(0, pal.size - 1), p[3].coerceIn(0, pal.size - 1),
                p[4].coerceIn(1, 8), p[5].coerceIn(1, 5), p[6].coerceIn(1, 5), p[7].coerceIn(0, 3), s.substringAfter('|', "")
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
    const val DIM = 6 + 44 + 12 + 12 + 3 + 4 + 1 + 1

    fun feat(o: Orb): FloatArray {
        val x = FloatArray(DIM)
        x[o.kind] = 1f
        x[6 + (if (o.kind < 3) o.kind * 6 + o.pat else if (o.kind == 3) 18 + o.pat else if (o.kind == 4) 34 + o.pat / 10 else 34)] = 1f
        x[50 + o.c1] = 1f
        x[62 + o.c2] = 1f
        x[74] = (o.n - 1) / 7f
        x[75] = (o.sz - 1) / 4f
        x[76] = (o.sp - 1) / 4f
        x[77 + o.bd] = 1f
        x[81] = if (o.c1 != o.c2) 1f else 0f
        x[82] = 1f
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
        val k = rnd.nextInt(5)
        return Orb(k, rnd.nextInt(Orb.patCount(k)), rnd.nextInt(Orb.pal.size), rnd.nextInt(Orb.pal.size), 1 + rnd.nextInt(8), 1 + rnd.nextInt(5), 1 + rnd.nextInt(5), rnd.nextInt(4))
    }

    /** Cambia 1 a 3 rasgos de la esfera. */
    fun mutate(o: Orb, rnd: java.util.Random, genes: Int = 1 + rnd.nextInt(3)): Orb {
        val m = o.copy()
        repeat(genes) {
            when (rnd.nextInt(8)) {
                0 -> { m.kind = rnd.nextInt(5); m.pat = rnd.nextInt(Orb.patCount(m.kind)) }
                1 -> m.pat = rnd.nextInt(Orb.patCount(m.kind))
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
        if (parts.size != DIM) return w   // pesos de una versión anterior: se empieza de cero
        for (i in 0 until DIM) w[i] = parts[i].toFloatOrNull() ?: 0f
        return w
    }
}

/** Texto ↔ esfera: arma el pedido para el modelo de lenguaje, lee su respuesta y tiene reglas de respaldo. */
/** Formato de conversación de cada modelo: 0 = ChatML (Qwen), 1 = Gemma. */
object Chat {
    fun build(tpl: Int, system: String, shots: List<Pair<String, String>>, user: String): String {
        val u = user.trim().take(120)
        val sb = StringBuilder()
        if (tpl == 1) {
            var first = true
            for ((q, a) in shots) {
                sb.append("<start_of_turn>user\n")
                if (first) { sb.append(system).append("\n\n"); first = false }
                sb.append(q).append("<end_of_turn>\n<start_of_turn>model\n").append(a).append("<end_of_turn>\n")
            }
            sb.append("<start_of_turn>user\n")
            if (first) sb.append(system).append("\n\n")
            sb.append(u).append("<end_of_turn>\n<start_of_turn>model\n")
        } else {
            sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            for ((q, a) in shots) sb.append("<|im_start|>user\n").append(q).append("<|im_end|>\n<|im_start|>assistant\n").append(a).append("<|im_end|>\n")
            sb.append("<|im_start|>user\n").append(u).append("<|im_end|>\n<|im_start|>assistant\n")
        }
        return sb.toString()
    }
}

object OrbText {
    private val system = "Eres un diseñador de esferas para un videojuego. Responde SOLO con 8 números separados por comas, sin texto: " +
        "tipo,patron,color1,color2,cantidad,tamano,velocidad,borde.\n" +
        "tipo: 0=clasica 1=estelar 2=animada 3=figura 4=animal (si pide un animal pon tipo 4 y patron 0; el juego elige cual).\n" +
        "patron (0-5): clasica 0 liso,1 rayas,2 lunares,3 cuadros,4 anillos,5 rombos; " +
        "estelar 0 estrellas,1 constelacion,2 nebulosa,3 orbitas,4 eclipse,5 supernova; " +
        "animada 0 remolino,1 pulso,2 ondas,3 destellos,4 ruleta,5 burbujas; " +
        "figura (0-15) 0 gato,1 perro,2 corazon,3 estrella,4 luna,5 flor,6 calavera,7 fantasma,8 pez,9 mariposa,10 rayo,11 corona,12 hoja,13 gota,14 huella,15 sol.\n" +
        "colores (0-11): 0 rojo,1 naranja,2 amarillo,3 verde,4 celeste,5 azul,6 violeta,7 rosa,8 blanco,9 plata,10 cafe,11 azul oscuro.\n" +
        "cantidad 1-8, tamano 1-5, velocidad 1-5, borde: 0 normal,1 color,2 dorado,3 arcoiris."

    private val extraShots = arrayOf("pelota clasica con rayas azules" to "0,1,5,8,5,3,1,0")
    private val shots = arrayOf(
        "remolino de fuego" to "2,0,0,1,4,3,4,2",
        "galaxia morada con estrellas" to "1,2,6,5,4,3,2,0",
        "pelota clasica con rayas azules" to "0,1,5,8,5,3,1,0",
        "burbujas de agua" to "2,5,4,8,6,3,3,1",
        "sol brillante" to "1,5,2,1,8,4,3,2",
        "un gato naranja" to "3,0,1,2,5,3,2,0",
        "corazon rosa" to "3,2,7,0,5,3,3,1",
        "un leon" to "4,0,2,10,5,3,3,0"
    )

    fun prompt(text: String, tpl: Int = 0, rich: Boolean = false): String =
        Chat.build(tpl, system, (if (rich) shots + extraShots else shots).toList(), text)

    /** Los 8 primeros números de la respuesta, o null si no hay 8. */
    fun parse(reply: String): Orb? {
        val nums = Regex("-?\\d+").findAll(reply).map { it.value.toInt() }.toList()
        if (nums.size < 8) return null
        val n = nums.take(8)
        return Orb(n[0].coerceIn(0, 4), n[1].coerceIn(0, Orb.patCount(n[0].coerceIn(0, 4)) - 1), n[2].coerceIn(0, 11), n[3].coerceIn(0, 11),
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
        val an = Animals.find(text)
        if (an >= 0) {
            val d = Animals.all[an]
            o.kind = 4; o.pat = an
            val cs2 = colorsIn(text)
            o.c1 = cs2.getOrElse(0) { d.c1 }
            o.c2 = cs2.getOrElse(1) { d.c2 }
            if (o.c1 == o.c2) o.c2 = if (o.c1 == 8) 4 else 8
            return o
        }
        when {
            has("gato", "gatito", "minino", "felino") -> { o.kind = 3; o.pat = 0 }
            has("perro", "perrito", "cachorro", "husky") -> { o.kind = 3; o.pat = 1 }
            has("corazon", "amor") -> { o.kind = 3; o.pat = 2 }
            has("forma de estrella", "estrella de") -> { o.kind = 3; o.pat = 3 }
            has("luna") -> { o.kind = 3; o.pat = 4 }
            has("flor", "rosa ") -> { o.kind = 3; o.pat = 5 }
            has("calavera", "craneo") -> { o.kind = 3; o.pat = 6 }
            has("fantasma") -> { o.kind = 3; o.pat = 7 }
            has("pez", "pescado") -> { o.kind = 3; o.pat = 8 }
            has("mariposa") -> { o.kind = 3; o.pat = 9 }
            has("rayo", "relampago") -> { o.kind = 3; o.pat = 10 }
            has("corona", "rey") -> { o.kind = 3; o.pat = 11 }
            has("hoja", "planta") -> { o.kind = 3; o.pat = 12 }
            has("gota") -> { o.kind = 3; o.pat = 13 }
            has("huella", "pata") -> { o.kind = 3; o.pat = 14 }
            has("remolino", "espiral", "tornado", "torbellino") -> { o.kind = 2; o.pat = 0 }
            has("pulso", "latido", "sonar") -> { o.kind = 2; o.pat = 1 }
            has("onda", "agua", "mar", "oceano") -> { o.kind = 2; o.pat = 2 }
            has("destello", "chispa", "brillo", "purpurina") -> { o.kind = 2; o.pat = 3 }
            has("ruleta", "giratori", "rueda") -> { o.kind = 2; o.pat = 4 }
            has("burbuja", "espuma") -> { o.kind = 2; o.pat = 5 }
            has("constelacion") -> { o.kind = 1; o.pat = 1 }
            has("nebulosa", "galaxia") -> { o.kind = 1; o.pat = 2 }
            has("orbita", "planeta", "sistema solar") -> { o.kind = 1; o.pat = 3 }
            has("eclipse", "agujero") -> { o.kind = 1; o.pat = 4 }
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
        o.c1 = cs.getOrElse(0) { if (has("fuego", "llama", "lava")) 0 else if (o.kind == 3) intArrayOf(1, 10, 0, 2, 2, 7, 8, 8, 4, 6, 2, 2, 3, 4, 10, 2)[o.pat] else if (o.kind == 1) 6 else 4 }
        o.c2 = cs.getOrElse(1) { if (has("fuego", "llama", "lava")) 1 else if (o.c1 == 8) 4 else 8 }
        o.bd = if (has("arcoiris")) 3 else if (has("dorad", "oro")) 2 else 0
        o.sp = if (has("rapid")) 5 else if (has("lent")) 1 else 3
        return o
    }

    /** Une la respuesta del modelo con los colores que pediste (los modelos chicos se equivocan en eso). */
    fun finish(reply: String, text: String): Pair<Orb, Boolean> {
        val m = parse(reply) ?: return Pair(rules(text), false)
        val cs = colorsIn(text)
        val an = Animals.find(text)
        if (an >= 0) {
            m.kind = 4; m.pat = an
            if (cs.isEmpty()) { m.c1 = Animals.all[an].c1; m.c2 = Animals.all[an].c2 }
        }
        if (cs.isNotEmpty()) m.c1 = cs[0]
        if (cs.size > 1) m.c2 = cs[1]
        if (m.c1 == m.c2) m.c2 = if (m.c1 == 8) 4 else 8
        return Pair(m, true)
    }
}

/** 100 animales para el tipo «Animal»: cada uno es una receta de rasgos (cabeza, orejas, hocico, adornos, marcas, ojos). */
object Animals {
    class Def(val name: String, val c1: Int, val c2: Int, val hw: Int, val hh: Int, val ears: Int, val esz: Int, val snout: Int, val crown: Int, val marks: Int, val eyes: Int)

    // nombre,color1,color2,ancho,alto,orejas,tamañoOrejas,hocico,adorno,marcas,ojos   (ancho/alto/tamaño en centésimas)
    // orejas: 0 -, 1 punta, 2 redondas arriba, 3 largas, 4 caídas, 5 laterales chicas, 6 grandes, 7 antenas, 8 murciélago, 9 ojos saltones
    // hocico: 0 -, 1 corto, 2 largo, 3 pico, 4 trompa, 5 cerdo, 6 pico de pato, 7 lengua de serpiente
    // adorno: 0 -, 1 cuernos, 2 astas, 3 melena, 4 cresta, 5 cuerno, 6 púas, 7 aleta, 8 tentáculos, 9 caparazón, 10 brazos de estrella, 11 patas de araña, 12 pinzas
    // marcas: 0 -, 1 rayas, 2 manchas, 3 antifaz, 4 cara clara, 5 franja, 6 mejillas, 7 bandas
    // ojos: 0 punto, 1 grandes, 2 rendija, 3 soñolientos
    private val raw = """
León,2,10,52,50,5,20,1,3,0,0
Tigre,1,8,55,50,2,22,1,0,1,2
Leopardo,2,10,52,48,2,20,1,0,2,2
Guepardo,2,11,48,50,2,18,1,0,2,2
Lobo,9,8,52,56,1,34,2,0,4,2
Zorro,1,8,56,50,1,38,2,0,4,2
Oso,10,1,56,52,5,22,1,0,0,0
Panda,8,11,58,52,5,24,1,0,3,0
Oso polar,8,9,54,50,5,18,1,0,0,0
Conejo,8,7,46,50,3,60,1,0,0,0
Ardilla,10,1,48,46,2,18,1,0,4,1
Ratón,9,7,46,46,5,28,1,0,0,0
Hámster,1,8,56,50,5,16,1,0,4,1
Castor,10,8,54,50,5,16,1,0,4,0
Erizo,10,1,46,44,5,14,1,6,0,0
Mapache,9,11,54,46,2,20,1,0,3,0
Nutria,10,8,52,46,5,12,1,0,4,0
Murciélago,11,6,46,44,8,64,1,0,0,1
Mono,10,1,50,48,6,22,1,0,4,0
Gorila,11,9,58,54,5,16,1,0,4,3
Lémur,9,8,50,48,5,22,1,0,3,1
Perezoso,10,1,56,48,5,12,1,0,3,3
Koala,9,8,56,50,6,34,1,0,0,0
Canguro,1,8,44,56,1,40,2,0,4,0
Elefante,9,8,54,52,6,52,4,0,0,0
Rinoceronte,9,11,60,50,5,18,1,5,0,0
Hipopótamo,7,6,64,52,5,16,1,0,0,0
Jirafa,2,10,44,58,5,18,1,1,2,1
Cebra,8,11,46,58,2,22,2,0,1,0
Caballo,10,11,44,60,1,26,2,4,0,0
Vaca,8,11,56,52,5,20,1,1,2,0
Toro,10,8,56,52,5,20,1,1,0,2
Oveja,11,8,52,48,4,26,1,3,0,0
Cabra,8,10,44,54,4,24,2,1,0,2
Cerdo,7,8,56,52,1,18,5,0,0,0
Ciervo,10,2,46,52,2,24,2,2,0,0
Alce,10,1,48,58,2,22,2,2,0,0
Camello,2,10,46,56,5,14,2,0,0,3
Llama,8,10,44,58,3,34,2,0,0,3
Alpaca,8,7,50,54,3,26,1,0,0,3
Bisonte,10,11,60,54,5,16,1,1,0,0
Jabalí,10,11,56,50,1,18,5,0,0,0
Suricata,2,10,46,50,5,14,2,0,3,1
Tejón,9,8,46,46,5,14,2,0,1,0
Lince,10,11,52,50,1,40,1,0,2,2
Pantera,11,2,52,50,1,28,1,0,0,2
Gato,1,8,52,48,1,32,1,0,4,1
Gato siamés,2,10,52,48,1,36,1,0,3,1
Perro,10,8,52,52,4,30,2,0,4,0
Husky,9,8,54,52,1,30,2,0,4,2
Chihuahua,2,8,48,48,1,50,2,0,0,1
Pug,2,11,56,50,4,24,1,0,3,1
Poodle,8,8,50,50,4,34,2,3,0,0
Dálmata,8,11,50,52,4,26,2,0,2,0
Delfín,4,8,48,54,0,0,2,7,0,0
Ballena,5,8,66,54,0,0,0,7,4,3
Foca,9,8,52,48,0,0,1,0,2,1
Morsa,10,8,60,50,0,0,1,0,4,3
Pingüino,11,1,50,54,0,0,3,0,4,0
Águila,10,2,50,48,0,0,3,4,4,2
Búho,10,2,56,52,1,22,3,0,3,1
Lechuza,8,10,58,50,0,0,3,0,4,1
Loro,0,5,48,50,0,0,3,4,6,1
Tucán,11,1,46,48,0,0,3,0,4,0
Flamenco,7,0,44,48,0,0,3,4,0,0
Pato,2,1,52,48,0,0,6,0,0,0
Cisne,8,1,44,54,0,0,3,0,0,0
Gallina,8,0,50,50,0,0,3,4,6,0
Gallo,0,2,48,52,0,0,3,4,0,2
Pollito,2,1,56,50,0,0,3,4,6,0
Pavo,10,0,48,52,0,0,3,4,0,0
Pavo real,5,3,44,48,0,0,3,4,0,1
Colibrí,3,0,40,44,0,0,3,0,6,0
Cuervo,11,9,48,48,0,0,3,0,0,2
Paloma,9,8,52,48,0,0,3,0,0,0
Pelícano,8,2,48,50,0,0,6,0,0,0
Rana,3,2,62,44,9,22,0,0,0,1
Sapo,10,3,64,46,9,22,0,0,2,1
Tortuga,3,10,44,44,0,0,0,9,0,1
Cocodrilo,3,2,50,40,9,14,2,0,0,2
Serpiente,3,2,44,40,0,0,7,0,2,2
Camaleón,3,0,46,46,9,20,0,4,2,2
Dragón,0,2,56,50,8,40,2,5,0,2
Pulpo,6,7,56,50,0,0,0,8,0,1
Cangrejo,0,1,56,42,9,16,0,12,0,0
Medusa,7,6,60,50,0,0,0,8,0,0
Tiburón,9,8,58,48,0,0,0,7,0,2
Pez payaso,1,8,52,48,0,0,0,7,7,1
Pez globo,2,10,62,58,0,0,0,6,0,1
Estrella de mar,1,2,36,36,0,0,0,10,0,0
Caballito de mar,2,1,36,54,0,0,2,4,0,0
Abeja,2,11,52,50,7,34,0,0,1,1
Mariquita,0,11,56,50,7,28,0,0,2,0
Hormiga,11,10,46,48,7,44,0,0,0,1
Araña,11,0,46,46,0,0,0,11,0,1
Escarabajo,11,3,48,50,7,30,0,5,0,0
Libélula,4,3,40,40,7,22,0,0,3,1
Saltamontes,3,2,46,50,7,50,0,0,0,1
Luciérnaga,10,2,50,48,7,30,0,0,6,1
Caracol,2,10,40,40,7,40,0,9,0,0
"""
    val all: List<Def> = raw.trim().lines().map {
        val p = it.split(",")
        Def(p[0], p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7].toInt(), p[8].toInt(), p[9].toInt(), p[10].toInt())
    }
    val names: Array<String> = Array(all.size) { all[it].name }
    val count: Int get() = all.size

    private fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private val extra = mapOf("osito" to "oso", "osa" to "oso", "leona" to "leon", "pajaro" to "paloma", "ave" to "paloma",
        "cachorro" to "perro", "minino" to "gato", "felino" to "gato", "caballito" to "caballo", "pez" to "pez payaso",
        "pescado" to "pez payaso", "tiburon" to "tiburon", "mono" to "mono", "simio" to "mono", "insecto" to "abeja", "oruga" to "luciernaga")

    /** Busca un animal en el texto («un gatito», «oso polar»…). Devuelve su número o -1. */
    fun find(text: String): Int {
        val t = " " + norm(text) + " "
        var best = -1
        var bestLen = 0
        for ((i, a) in all.withIndex()) {
            val n = norm(a.name)
            if (n.contains(' ') && t.contains(n) && n.length > bestLen) { best = i; bestLen = n.length }
        }
        if (best >= 0) return best
        val words = t.trim().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        for ((i, a) in all.withIndex()) {
            val n = norm(a.name)
            if (n.contains(' ')) continue
            val stem = if (n.length >= 5 && n.last() in "aoe") n.dropLast(1) else n
            for (w in words) if (w.startsWith(stem) && stem.length > bestLen) { best = i; bestLen = stem.length }
        }
        if (best >= 0) return best
        for (w in words) {
            val target = extra[w] ?: continue
            val k = all.indexOfFirst { norm(it.name) == target }
            if (k >= 0) return k
        }
        return -1
    }
}

/**
 * Dibujo generativo: el modelo de lenguaje «dibuja» escribiendo formas (círculos, óvalos, polígonos, líneas)
 * sobre un lienzo de -100 a 100. El juego las valida y las pinta dentro de la esfera.
 */
object OrbDraw {
    private val system = "Dibujas sobre un lienzo cuadrado de -100 a 100 (x hacia la derecha, y hacia abajo, centro 0 0). " +
        "Responde SOLO con formas, una por línea, sin explicar:\n" +
        "c x y radio color  (círculo)\n" +
        "o x1 y1 x2 y2 color  (óvalo dentro de esa caja)\n" +
        "p color x1 y1 x2 y2 x3 y3 ...  (polígono)\n" +
        "l x1 y1 x2 y2 grosor color  (línea)\n" +
        "color: 0 rojo,1 naranja,2 amarillo,3 verde,4 celeste,5 azul,6 violeta,7 rosa,8 blanco,9 plata,10 cafe,11 azul oscuro. " +
        "Dibuja primero el fondo y el cuerpo, luego los detalles. Usa entre 8 y 20 formas."

    private val extraShots = arrayOf(
        "casa roja" to "p 0 -70 -5 0 -65 70 -5\no -60 -5 60 80 8\no -15 25 15 80 10\no -48 8 -25 30 4\no 25 8 48 30 4\nl 0 -65 0 -75 4 8",
        "luna con estrellas" to "c 0 0 60 2\nc 28 -12 52 11\nc -60 -55 6 8\nc 62 55 5 8\nc -50 50 4 8"
    )
    private val shots = arrayOf(
        "gato naranja" to "c 0 10 55 1\np 1 -50 -10 -45 -80 -10 -40\np 1 50 -10 45 -80 10 -40\np 7 -42 -20 -40 -60 -20 -38\np 7 42 -20 40 -60 20 -38\nc -22 5 10 8\nc 22 5 10 8\nc -22 5 5 11\nc 22 5 5 11\np 7 -7 26 7 26 0 34\nl 20 34 80 24 3 8\nl 20 42 80 46 3 8\nl -20 34 -80 24 3 8\nl -20 42 -80 46 3 8",
        "pez azul" to "p 4 30 0 78 -32 78 32\no -75 -38 40 38 5\np 4 -10 -34 20 -62 22 -22\nc -45 -8 10 8\nc -47 -8 5 11\nl -62 10 -50 16 3 11",
        "arbol" to "l 0 95 0 5 16 10\nc 0 -25 52 3\nc -35 8 32 3\nc 35 8 32 3\nc -15 -40 10 2\nc 25 -10 9 0"
    )

    fun prompt(text: String, tpl: Int = 0, rich: Boolean = false): String =
        Chat.build(tpl, system, (if (rich) shots + extraShots else shots).toList(), text)

    /** Valida la respuesta y la devuelve limpia («forma;forma;…») o null si no hay al menos 3 formas. */
    fun parse(reply: String): String? {
        val out = ArrayList<String>()
        for (line in reply.split('\n', ';')) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val cmd = t[0].lowercaseChar()
            val nums = Regex("-?\\d+").findAll(t.substring(1)).map { it.value.toInt() }.toList()
            fun co(v: Int) = v.coerceIn(-120, 120)
            fun col(v: Int) = v.coerceIn(0, Orb.pal.size - 1)
            when (cmd) {
                'c' -> if (nums.size >= 4) out.add("c ${co(nums[0])} ${co(nums[1])} ${nums[2].coerceIn(1, 120)} ${col(nums[3])}")
                'o' -> if (nums.size >= 5) out.add("o ${co(nums[0])} ${co(nums[1])} ${co(nums[2])} ${co(nums[3])} ${col(nums[4])}")
                'l' -> if (nums.size >= 6) out.add("l ${co(nums[0])} ${co(nums[1])} ${co(nums[2])} ${co(nums[3])} ${nums[4].coerceIn(1, 24)} ${col(nums[5])}")
                'p' -> if (nums.size >= 7) {
                    val pts = nums.drop(1)
                    val n = minOf(pts.size / 2, 12)
                    if (n >= 3) out.add("p ${col(nums[0])} " + (0 until n * 2).joinToString(" ") { co(pts[it]).toString() })
                }
            }
            if (out.size >= 60) break
        }
        return if (out.size >= 3) out.joinToString(";") else null
    }
}
