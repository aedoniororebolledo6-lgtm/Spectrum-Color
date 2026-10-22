package com.nioro.spectrumcolor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Motor del juego: física, colisiones y generación de recorridos.
 * No depende de Android, así que se puede probar en cualquier JVM (ver carpeta tools/).
 * Todo está en "unidades de diseño": el ancho de la pantalla siempre vale 1080.
 */

object Cfg {
    const val W = 1080f
    const val BALL_R = 34f
    const val GRAVITY = 2900f
    const val JUMP_V = 1120f
    const val MAX_FALL = 1700f
    const val STEP = 1f / 120f
    const val THICK = 40f
    const val GRAZE = 0.4f      // cuánto "roce" se perdona (fracción del radio de la pelota)
    const val STAR_R = 28f
    const val SW_R = 34f
    const val BAR_DY = 0f        // separación entre carriles; con un solo carril las barras quedan en el centro
    const val BAR_LANES = 1      // un solo carril de barras: se cruza con un solo salto, como en Color Switch
    const val BAR_H = 26f
    const val BAR_SEG = 540f
    const val START_GAP = 620f
    const val ROUND_TIME = 75f

    /**
     * Anillo doble concéntrico: el hueco entre los dos anillos (~80) es más chico que lo que sube la pelota
     * en un toque (~216), así que no se puede esperar ahí y casi nunca hay un cruce posible
     * (la búsqueda de tools/ no encontró solución). Se deja el código pero sin usarlo.
     */
    const val ENABLE_DOUBLE = false

    /** Velocidad de la barra inferior de COUNTER respecto a la superior: distinta, para que con el tiempo coincidan todos los colores. */
    const val COUNTER_RATIO = 0.62f

    const val ORB_R = 36f        // radio de las bolitas de las órbitas
    const val SPIKE_H = 22f      // altura de los pinchos
}

object T {
    const val RING = 0
    const val DOUBLE = 1
    const val BARS = 2
    const val SQUARE = 3
    const val TRI = 4
    const val SPIKES = 5     // anillo con pinchos en las uniones de color
    const val PEND = 6       // barra de dos colores que oscila de lado a lado
    const val CROSS = 7      // cruz de cuatro brazos de color con el centro vacío
    const val GATE = 8       // muro mortal con un hueco que se desliza
    const val ORBIT = 9      // bolitas de color que giran en dos órbitas
    const val COUNTER = 10   // dos barras que se mueven en sentidos opuestos
    const val COUNT = 11
}

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

class Ob(
    val type: Int,
    val y: Float,
    val n: Int,         // cantidad de colores de este obstáculo
    val shift: Int,
    val speed: Float,   // grados/s (giro) o unidades/s (barras)
    val phase: Float,
    val size: Float,    // radio, semilado o circunradio según el tipo (amplitud en PEND y GATE)
    val inner: Float,   // radio del anillo interior (DOUBLE) o de la órbita interior (ORBIT)
    val aux: Float = 0f,        // dato propio del tipo: pinchos (grados), largo (PEND), hueco (GATE), carril (COUNTER), radio interior (CROSS)
    val drift: Float = 0f,      // aceleración vertical extra en la zona del obstáculo (corriente de aire)
    val fade: Boolean = false,  // el obstáculo se ve solo una parte del tiempo
    val wob: Float = 0f         // vaivén extra del giro, en grados
) {
    /** Distancia máxima desde el centro hasta cualquier parte del obstáculo. */
    val reach: Float = when (type) {
        T.BARS, T.PEND, T.GATE -> Cfg.BAR_DY + Cfg.BAR_H + 6f
        T.COUNTER -> aux + Cfg.BAR_H + 6f
        T.SQUARE -> size * 1.4143f + Cfg.THICK / 2f
        T.SPIKES -> size + Cfg.THICK / 2f + Cfg.SPIKE_H
        T.ORBIT -> size + Cfg.ORB_R
        else -> size + Cfg.THICK / 2f
    }

    /** Máscara de colores que de verdad aparecen en este obstáculo. */
    val present: Int = run {
        var m = 0
        when (type) {
            T.SQUARE -> for (k in 0 until 4) m = m or (1 shl ((k + shift) % n))
            T.TRI -> for (k in 0 until 3) m = m or (1 shl ((k + shift) % n))
            T.PEND -> m = (1 shl (shift % n)) or (1 shl ((shift + 1) % n))
            T.CROSS -> for (k in 0 until 4) m = m or (1 shl ((k + shift) % n))
            T.ORBIT -> {
                for (k in 0 until 3) m = m or (1 shl ((k + shift) % n))
                for (k in 0 until 2) m = m or (1 shl ((k + shift + 1) % n))
            }
            else -> for (k in 0 until n) m = m or (1 shl k)
        }
        m
    }
}

class Sw(val y: Float, val toColor: Int)

object Geo {
    private const val PI_F = 3.14159265f

    /**
     * Segmentos de un anillo: el patrón de colores se repite dos veces por vuelta, así el color de abajo
     * y el de arriba son el mismo y se cruza el anillo de una sola vez (sin tener que esperar dentro).
     */
    fun ringSegs(n: Int): Int = 2 * n

    fun wobble(o: Ob, t: Float): Float = if (o.wob == 0f) 0f else o.wob * sin(t * 1.35f + o.phase * 0.05f)
    fun rot(o: Ob, t: Float): Float = o.phase + o.speed * t + wobble(o, t)
    fun rotInner(o: Ob, t: Float): Float = o.phase - o.speed * t - wobble(o, t)

    /** Opacidad con la que se dibuja un obstáculo "que se desvanece" (la colisión no cambia). */
    fun fadeAlpha(o: Ob, t: Float): Float {
        if (!o.fade) return 1f
        val per = 2.8f
        val u = ((((t + o.phase / 360f * per) % per) + per) % per) / per
        return if (u < 0.45f) 1f else if (u < 0.6f) 1f - (u - 0.45f) / 0.15f * 0.85f else if (u < 0.9f) 0.15f else 0.15f + (u - 0.9f) / 0.1f * 0.85f
    }

    fun swingX(o: Ob, t: Float): Float = Cfg.W / 2f + o.size * sin(rot(o, t) * PI_F / 180f)

    fun orbX(o: Ob, t: Float, k: Int, inner: Boolean): Float {
        val a = (if (inner) rotInner(o, t) + k * 180f else rot(o, t) + k * 120f) * PI_F / 180f
        return Cfg.W / 2f + cos(a) * (if (inner) o.inner else o.size)
    }

    fun orbY(o: Ob, t: Float, k: Int, inner: Boolean): Float {
        val a = (if (inner) rotInner(o, t) + k * 180f else rot(o, t) + k * 120f) * PI_F / 180f
        return o.y + sin(a) * (if (inner) o.inner else o.size)
    }

    fun crossAng(o: Ob, t: Float, k: Int): Float = (rot(o, t) + k * 90f) * PI_F / 180f

    fun laneColor(o: Ob, lane: Int, x: Float, t: Float): Int {
        val off = o.phase * 3f + (if (lane == 0) o.speed else -o.speed * Cfg.COUNTER_RATIO) * t
        val j = floor((x - off) / Cfg.BAR_SEG).toInt()
        return (((j % o.n) + o.n) % o.n + o.shift) % o.n
    }

    private fun spikeHit(o: Ob, bx: Float, by: Float, color: Int, t: Float): Boolean {
        val graze = Cfg.BALL_R * Cfg.GRAZE
        val dx = bx - Cfg.W / 2f
        val dy = by - o.y
        val dist = sqrt(dx * dx + dy * dy)
        val band = Cfg.THICK / 2f + graze
        val dr = abs(dist - o.size)
        if (dr > band + Cfg.SPIKE_H) return false
        val segs = ringSegs(o.n)
        val segW = 360f / segs
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - rot(o, t)
        a = ((a % 360f) + 360f) % 360f
        val seg = min(segs - 1, (a / segW).toInt())
        val p = a - seg * segW
        val edge = min(p, segW - p)
        if (edge < o.aux) {
            if (dr <= band + Cfg.SPIKE_H * (1f - edge / o.aux)) return true
        }
        if (dr > band) return false
        return (seg + o.shift) % o.n != color
    }

    fun polySides(o: Ob): Int = if (o.type == T.SQUARE) 4 else 3

    fun vx(o: Ob, t: Float, i: Int): Float {
        val k = polySides(o)
        val rad = if (o.type == T.SQUARE) o.size * 1.4142136f else o.size
        val base = if (o.type == T.SQUARE) 45f else -90f
        val ang = (rot(o, t) + base + i * (360f / k)) * PI_F / 180f
        return Cfg.W / 2f + cos(ang) * rad
    }

    fun vy(o: Ob, t: Float, i: Int): Float {
        val k = polySides(o)
        val rad = if (o.type == T.SQUARE) o.size * 1.4142136f else o.size
        val base = if (o.type == T.SQUARE) 45f else -90f
        val ang = (rot(o, t) + base + i * (360f / k)) * PI_F / 180f
        return o.y + sin(ang) * rad
    }

    fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val l2 = abx * abx + aby * aby
        var s = if (l2 > 0f) ((px - ax) * abx + (py - ay) * aby) / l2 else 0f
        if (s < 0f) s = 0f else if (s > 1f) s = 1f
        val dx = px - (ax + s * abx)
        val dy = py - (ay + s * aby)
        return sqrt(dx * dx + dy * dy)
    }

    /** Los dos carriles de barras se mueven juntos (mismo color a la misma altura): se cruzan de una sola vez. */
    fun barColor(o: Ob, lane: Int, x: Float, t: Float): Int {
        val off = o.phase * 3f + o.speed * t
        val j = floor((x - off) / Cfg.BAR_SEG).toInt()
        return (((j % o.n) + o.n) % o.n + o.shift) % o.n
    }

    private fun ringHit(
        o: Ob, bx: Float, by: Float, color: Int,
        rad: Float, rotDeg: Float, shift: Int, band: Float
    ): Boolean {
        val dx = bx - Cfg.W / 2f
        val dy = by - o.y
        val dist = sqrt(dx * dx + dy * dy)
        if (abs(dist - rad) > band) return false
        val segs = ringSegs(o.n)
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - rotDeg
        a = ((a % 360f) + 360f) % 360f
        val seg = min(segs - 1, (a / (360f / segs)).toInt())
        return (seg + shift) % o.n != color
    }

    /** true si la pelota (con ese color) choca contra el obstáculo en el instante t. */
    fun hits(o: Ob, bx: Float, by: Float, color: Int, t: Float): Boolean {
        val graze = Cfg.BALL_R * Cfg.GRAZE
        when (o.type) {
            T.RING -> return ringHit(o, bx, by, color, o.size, rot(o, t), o.shift, Cfg.THICK / 2f + graze)
            T.DOUBLE -> {
                val band = Cfg.THICK / 2f + graze
                return ringHit(o, bx, by, color, o.size, rot(o, t), o.shift, band) ||
                    ringHit(o, bx, by, color, o.inner, rotInner(o, t), (o.shift + 1) % o.n, band)
            }
            T.BARS -> {
                for (lane in 0 until Cfg.BAR_LANES) {
                    val ly = o.y + if (lane == 0) -Cfg.BAR_DY else Cfg.BAR_DY
                    if (abs(by - ly) > Cfg.BAR_H + graze) continue
                    if (barColor(o, lane, bx, t) != color) return true
                }
                return false
            }
            T.SPIKES -> return spikeHit(o, bx, by, color, t)
            T.PEND -> {
                if (abs(by - o.y) > Cfg.BAR_H + graze) return false
                val rel = Cfg.W / 2f - swingX(o, t)
                if (abs(rel) > o.aux + Cfg.BALL_R * 0.6f) return false
                val cc = if (rel < 0f) o.shift % o.n else (o.shift + 1) % o.n
                return cc != color
            }
            T.CROSS -> {
                val band = Cfg.THICK / 2f + graze
                for (k in 0 until 4) {
                    if ((k + o.shift) % o.n == color) continue
                    val a = crossAng(o, t, k)
                    val ca = cos(a)
                    val sa = sin(a)
                    val d = segDist(
                        bx, by,
                        Cfg.W / 2f + ca * o.aux, o.y + sa * o.aux,
                        Cfg.W / 2f + ca * o.size, o.y + sa * o.size
                    )
                    if (d < band) return true
                }
                return false
            }
            T.GATE -> {
                if (abs(by - o.y) > Cfg.BAR_H + graze) return false
                return abs(Cfg.W / 2f - swingX(o, t)) > o.aux - Cfg.BALL_R * 0.75f
            }
            T.ORBIT -> {
                val rr = Cfg.ORB_R + Cfg.BALL_R * 0.85f
                val r2 = rr * rr
                for (k in 0 until 3) {
                    if ((k + o.shift) % o.n == color) continue
                    val dx = bx - orbX(o, t, k, false)
                    val dy = by - orbY(o, t, k, false)
                    if (dx * dx + dy * dy < r2) return true
                }
                for (k in 0 until 2) {
                    if ((k + o.shift + 1) % o.n == color) continue
                    val dx = bx - orbX(o, t, k, true)
                    val dy = by - orbY(o, t, k, true)
                    if (dx * dx + dy * dy < r2) return true
                }
                return false
            }
            T.COUNTER -> {
                for (lane in 0 until 2) {
                    val ly = o.y + if (lane == 0) -o.aux else o.aux
                    if (abs(by - ly) > Cfg.BAR_H + graze) continue
                    if (laneColor(o, lane, bx, t) != color) return true
                }
                return false
            }
            else -> {
                val k = polySides(o)
                val band = Cfg.THICK / 2f + graze
                for (i in 0 until k) {
                    val j = (i + 1) % k
                    val d = segDist(bx, by, vx(o, t, i), vy(o, t, i), vx(o, t, j), vy(o, t, j))
                    if (d < band && (i + o.shift) % o.n != color) return true
                }
                return false
            }
        }
    }
}

/**
 * Reglas de niveles. El diseño completo tiene 12 mundos de 10 niveles (COUNT = 120), pero por ahora solo
 * están disponibles los primeros PLAYABLE niveles; los demás aparecen bloqueados ("Próximamente").
 * La dificultad, los colores y los obstáculos nuevos se reparten a lo largo de los niveles jugables:
 * al subir PLAYABLE, la curva se estira sola.
 */
object Levels {
    const val COUNT = 120
    const val PLAYABLE = 120
    const val WORLDS = 12
    const val PER_WORLD = 10

    /** Los primeros EASY niveles usan la curva suave original; del 21 en adelante empieza la parte difícil. */
    const val EASY = 20

    fun isPlayable(level: Int): Boolean = level in 1..PLAYABLE

    /** Variante del recorrido de cada nivel (0 por defecto). Se usa para cambiar un nivel que resultó muy difícil. */
    private val salts = IntArray(COUNT + 1).also {
        it[5] = 2   // variantes elegidas con `tools/run_tests.sh salt`: posibles incluso con 66 ms de retraso al tocar
        it[15] = 2
    }

    fun salt(level: Int): Int = if (level in 1..COUNT) salts[level] else 0

    /** Posición del nivel en la curva suave: 0 = primer nivel, 1 = nivel 20. */
    fun frac(level: Int): Float = ((level - 1).coerceAtLeast(0) / (EASY - 1).toFloat()).coerceIn(0f, 1f)

    /** Posición del nivel en la curva difícil: 0 = nivel 21, 1 = nivel 120. */
    fun hf(level: Int): Float = ((level - EASY - 1).coerceAtLeast(0) / (COUNT - EASY - 1).toFloat()).coerceIn(0f, 1f)

    fun isHard(level: Int): Boolean = level > EASY

    /** Cantidad de obstáculos. Los niveles 5 y 10 de cada mundo son más largos. */
    fun gates(level: Int): Int {
        if (!isHard(level)) {
            val base = 5 + (frac(level) * 7f).toInt()
            return if (level % 5 == 0) base + 2 else base
        }
        val base = 11 + (hf(level) * 11f).toInt()
        return if (level % 10 == 0) base + 5 else if (level % 5 == 0) base + 2 else base
    }

    /** Colores de la rueda del nivel (2 al empezar, 3 en el grueso, 4 en lo difícil). */
    fun colors(level: Int): Int {
        if (isHard(level)) return if (level <= 40) 3 else 4
        val f = frac(level)
        return if (f < 0.3f) 2 else if (f < 0.85f) 3 else 4
    }

    fun difficulty(level: Int): Float = if (isHard(level)) hf(level) else frac(level)

    fun world(level: Int): Int = ((level - 1) / PER_WORLD).coerceIn(0, WORLDS - 1)

    /** Primer nivel de un mundo (0..11). */
    fun worldFirst(world: Int): Int = world * PER_WORLD + 1

    /** Un mundo está disponible si su primer nivel es jugable. */
    fun worldAvailable(world: Int): Boolean = worldFirst(world) <= PLAYABLE

    /** Probabilidades (por obstáculo) de los modificadores de los mundos 9, 10 y 11. */
    fun driftProb(level: Int): Float = if (level < 81) 0f else if (level < 111) 0.35f else 0.45f
    fun fadeProb(level: Int): Float = if (level < 91) 0f else if (level < 111) 0.4f else 0.5f
    fun wobProb(level: Int): Float = if (level < 101) 0f else if (level < 111) 0.45f else 0.6f

    /** Desde qué punto de la curva suave aparece cada tipo básico (índice = T.*). */
    private val unlockFrac = floatArrayOf(0f, 0.65f, 0.05f, 0.2f, 0.4f)

    /** Nivel en que se estrena cada obstáculo nuevo (T.SPIKES .. T.COUNTER): uno por mundo, del 3 al 8. */
    private val newUnlock = intArrayOf(21, 31, 41, 51, 61, 71)

    fun unlockLevel(type: Int): Int =
        if (type < 5) 1 + Math.ceil((unlockFrac[type] * (EASY - 1)).toDouble() - 1e-6).toInt() else newUnlock[type - 5]

    /** Obstáculo desde el que aparece cada tipo en el modo infinito. */
    val unlockGate = intArrayOf(0, 22, 2, 6, 12, 28, 34, 40, 46, 52, 58)
}

class Course private constructor(
    val seed: Long,
    val isLevel: Boolean,
    val level: Int,
    val gateTotal: Int,      // -1 = infinito
    private val gateOffset: Int
) {
    companion object {
        /** [salt] cambia el recorrido de un nivel sin tocar los demás (ver Levels.salt). */
        fun level(level: Int, salt: Int = Levels.salt(level)): Course {
            val c = Course(level * 7919L + 13L + salt * 104729L, true, level, Levels.gates(level), 0)
            c.build()
            return c
        }

        /** Recorrido infinito. gateOffset sube la dificultad inicial (multijugador). */
        fun endless(seed: Long, gateOffset: Int = 0): Course = Course(seed, false, 0, -1, gateOffset)
    }

    val obs = ArrayList<Ob>()
    val sws = ArrayList<Sw>()
    var startColor = 0
        private set
    var finishY = Float.NEGATIVE_INFINITY
        private set

    private val rnd = Random(seed)
    private var cursor = 0f          // y del borde superior del último elemento generado
    private var curColor = -1
    private var lastType = -1

    val totalGates: Int get() = gateTotal

    private fun build() {
        while (obs.size < gateTotal) genNext()
        finishY = cursor - 560f
    }

    /** Genera obstáculos hasta cubrir la altura topY (solo modo infinito). */
    fun ensure(topY: Float) {
        if (isLevel) return
        while (cursor > topY) genNext()
    }

    private fun colorsFor(i: Int, g: Int): Int =
        if (isLevel) {
            if (!Levels.isHard(level)) Levels.colors(level)
            else if (level <= 40) 3
            else if (level <= 100) (if (i % 2 == 0) 4 else 3)
            else (if (i % 3 == 2) 3 else 4)
        } else if (g < 5) 3 else 4

    private fun diffFor(i: Int, g: Int): Float =
        if (isLevel) {
            // En los niveles difíciles la velocidad se queda en el tramo que la búsqueda comprobó posible;
            // la dificultad extra viene de más obstáculos, mezclas, viento, desvanecimientos y vaivén.
            val cap = if (Levels.isHard(level)) 0.8f else 1f
            val d = Levels.difficulty(level) * cap
            (d * 0.94f + 0.06f * cap * (i / gateTotal.toFloat())).coerceIn(0f, 1f)
        } else {
            min(0.85f, g / 65f)
        }

    private fun pickType(i: Int, g: Int): Int {
        val hard = isLevel && Levels.isHard(level)
        val w = if (hard) floatArrayOf(2.0f, 0f, 1.4f, 1.5f, 1.5f, 2.0f, 1.6f, 1.8f, 1.4f, 1.6f, 1.4f)
        else floatArrayOf(3.0f, 1.4f, 2.4f, 2.2f, 2.2f, 1.6f, 1.6f, 1.6f, 1.4f, 1.6f, 1.4f)
        val newT = if (hard) 5 + Levels.world(level) - 2 else -1
        var sum = 0f
        for (t in 0 until T.COUNT) {
            if (t == T.DOUBLE && !Cfg.ENABLE_DOUBLE) {
                w[t] = 0f
                continue
            }
            val unlocked = if (isLevel) level >= Levels.unlockLevel(t) else g >= Levels.unlockGate[t]
            if (!unlocked) {
                w[t] = 0f
                continue
            }
            if (hard) {
                // el obstáculo nuevo del mundo aparece más seguido, sobre todo en sus dos primeros niveles
                if (t == newT) w[t] *= if (level <= Levels.worldFirst(Levels.world(level)) + 1) 4f else 1.8f
            } else if (isLevel && t != T.RING && (level == Levels.unlockLevel(t) || level == Levels.unlockLevel(t) + 1)) {
                w[t] *= 4f // el obstáculo nuevo aparece más seguido para aprenderlo
            }
            if (t == lastType) w[t] *= 0.3f
            sum += w[t]
        }
        if (i == 0 || sum <= 0f) return T.RING
        var r = rnd.nextFloat() * sum
        for (t in 0 until T.COUNT) {
            r -= w[t]
            if (r <= 0f && w[t] > 0f) return t
        }
        return T.RING
    }

    private fun genNext() {
        val i = obs.size
        val g = i + gateOffset
        val d = diffFor(i, g)
        val type = pickType(i, g)
        lastType = type
        val hard = isLevel && Levels.isHard(level)
        var n = colorsFor(i, g)
        // en los niveles difíciles solo los obstáculos con "espera natural" usan 4 colores
        if (hard && n == 4 && type != T.RING && type != T.SPIKES && type != T.CROSS && type != T.ORBIT) n = 3

        val dirS = if (rnd.nextBoolean()) 1f else -1f
        val vari = 0.92f + rnd.nextFloat() * 0.16f
        val shift = rnd.nextInt(n)
        val phase = rnd.nextFloat() * 360f
        var speed: Float
        var size: Float
        var inner = 0f
        var aux = 0f
        // Con 3 o 4 colores hay que esperar dentro del obstáculo a que llegue "tu" color arriba:
        // si gira muy lento esa espera se hace eterna, así que más colores = gira un poco más rápido.
        val spinLo = if (n <= 2) 32f else if (n == 3) 50f else 58f
        val spinHi = if (n <= 2) 46f else if (n == 3) 64f else 76f
        val spin = if (hard) lerp(if (n <= 3) 58f else 62f, if (n <= 3) 94f else 100f, d) * vari else lerp(spinLo, spinHi, d) * vari
        val barSpd = if (hard) lerp(190f, 310f, d) else lerp(120f, 250f, d)
        when (type) {
            T.RING -> { speed = dirS * spin; size = 320f }
            T.DOUBLE -> { speed = dirS * spin * 0.9f; size = 345f; inner = 195f }
            T.BARS -> { speed = dirS * barSpd * vari; size = 0f }
            T.SQUARE -> { speed = dirS * spin * 0.95f; size = 250f }
            T.SPIKES -> { speed = dirS * spin * 0.85f; size = 330f; aux = lerp(4f, 8f, d) }
            T.PEND -> { speed = dirS * lerp(70f, 120f, d) * vari; size = 330f; aux = lerp(250f, 205f, d) }
            T.CROSS -> { speed = dirS * spin * 0.8f; size = 290f; aux = 95f }
            T.GATE -> { speed = dirS * lerp(70f, 115f, d) * vari; size = 330f; aux = lerp(175f, 135f, d) }
            T.ORBIT -> { speed = dirS * spin * 0.9f; size = 270f; inner = 130f }
            T.COUNTER -> { speed = dirS * lerp(150f, 250f, d) * vari; size = 0f; aux = 120f }
            else -> { speed = dirS * spin * 0.9f; size = 370f }
        }

        if (i == 0) speed *= 0.65f // el primer obstáculo gira más lento: da tiempo de orientarse

        // modificadores de los mundos difíciles
        var drift = 0f
        var fade = false
        var wob = 0f
        if (hard) {
            if (rnd.nextFloat() < Levels.driftProb(level)) drift = (if (rnd.nextBoolean()) 1f else -1f) * lerp(1100f, 1700f, d)
            if (rnd.nextFloat() < Levels.fadeProb(level)) fade = true
            val rotating = type == T.RING || type == T.SQUARE || type == T.TRI || type == T.SPIKES || type == T.CROSS || type == T.ORBIT
            val pw = if (type == T.SPIKES && level > 100) max(0.9f, Levels.wobProb(level)) else Levels.wobProb(level)
            if (rotating && rnd.nextFloat() < pw) wob = lerp(25f, 40f, d)
        }

        val gapFree = if (hard) lerp(430f, 385f, d) else lerp(500f, 400f, d)
        val probe = Ob(type, 0f, n, shift, speed, phase, size, inner, aux)
        val y: Float
        if (i == 0) {
            y = cursor - Cfg.START_GAP - probe.reach
        } else {
            y = cursor - gapFree - probe.reach
        }
        val ob = Ob(type, y, n, shift, speed, phase, size, inner, aux, drift, fade, wob)

        if (i == 0) {
            curColor = firstSetBit(ob.present, rnd.nextInt(Integer.bitCount(ob.present)))
            startColor = curColor
        } else {
            val pSw = lerp(0.65f, 1f, d)
            val need = (ob.present and (1 shl curColor)) == 0
            if (need || rnd.nextFloat() < pSw) {
                val cands = ArrayList<Int>()
                for (c in 0 until 4) if ((ob.present and (1 shl c)) != 0 && c != curColor) cands.add(c)
                if (cands.isNotEmpty()) {
                    val to = cands[rnd.nextInt(cands.size)]
                    sws.add(Sw(cursor - gapFree / 2f, to))
                    curColor = to
                }
            }
        }
        obs.add(ob)
        cursor = y - ob.reach
    }

    private fun firstSetBit(mask: Int, k: Int): Int {
        var left = k
        for (c in 0 until 4) {
            if ((mask and (1 shl c)) != 0) {
                if (left == 0) return c
                left--
            }
        }
        return 0
    }
}

class Sim(val course: Course, val visH: Float) {
    var t = 0f
    var by = 0f
    var bvy = 0f
    var camY = -visH * 0.78f
    var color = course.startColor
    var swCount = 0
    var starCount = 0
    var started = false
    var dead = false
    var finished = false
    var taps = 0

    /** Causa de la muerte (solo diagnóstico): -2 = sigue vivo, -1 = cayó fuera de la pantalla, >=0 = índice del obstáculo. */
    var deathObs = -2

    /** Velocidad de giro de los obstáculos (1 = normal; menos = modo fácil). La física de la pelota no cambia. */
    var timeScale = 1f

    fun copyFrom(o: Sim) {
        t = o.t; by = o.by; bvy = o.bvy; camY = o.camY; color = o.color
        swCount = o.swCount; starCount = o.starCount; started = o.started
        dead = o.dead; finished = o.finished; taps = o.taps; timeScale = o.timeScale; deathObs = o.deathObs
    }

    fun copy(): Sim {
        val s = Sim(course, visH)
        s.copyFrom(this)
        return s
    }

    fun tap() {
        if (dead || finished) return
        started = true
        bvy = -Cfg.JUMP_V
        taps++
    }

    fun step() {
        val dt = Cfg.STEP
        t += dt * timeScale
        if (dead || finished) return
        course.ensure(camY - visH * 1.6f)

        if (started) {
            var acc = Cfg.GRAVITY
            // corrientes de aire: algunos obstáculos empujan la pelota hacia arriba o hacia abajo
            val a0 = max(0, starCount - 1)
            val a1 = min(course.obs.size - 1, starCount + 1)
            for (k in a0..a1) {
                val o = course.obs[k]
                if (o.drift != 0f && abs(by - o.y) < o.reach + 60f) acc += o.drift
            }
            bvy = min(bvy + acc * dt, Cfg.MAX_FALL)
            if (bvy < -2400f) bvy = -2400f
            by += bvy * dt
        }

        val target = by - visH * 0.52f
        if (target < camY) camY += (target - camY) * (1f - exp(-14f * dt))

        if (by - camY > visH + Cfg.BALL_R) {
            dead = true
            deathObs = -1
            return
        }

        // cambiador de color
        if (swCount < course.sws.size) {
            val s = course.sws[swCount]
            val dy = by - s.y
            val rr = Cfg.BALL_R + Cfg.SW_R
            if (dy * dy < rr * rr) {
                color = s.toColor
                swCount++
            }
        }
        // estrella (una por obstáculo)
        if (starCount < course.obs.size) {
            val o = course.obs[starCount]
            val dy = by - o.y
            val rr = Cfg.BALL_R + Cfg.STAR_R
            if (dy * dy < rr * rr) starCount++
        }
        // choques
        val lo = maxOf(0, starCount - 2)
        val hi = min(course.obs.size - 1, starCount + 2)
        for (i in lo..hi) {
            val o = course.obs[i]
            if (abs(by - o.y) > o.reach + Cfg.BALL_R) continue
            if (Geo.hits(o, Cfg.W / 2f, by, color, t)) {
                dead = true
                deathObs = i
                return
            }
        }
        // meta
        if (course.isLevel && by < course.finishY) finished = true
    }
}
