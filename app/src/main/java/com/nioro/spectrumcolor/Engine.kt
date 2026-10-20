package com.nioro.spectrumcolor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
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
    const val BAR_DY = 240f
    const val BAR_H = 26f
    const val BAR_SEG = 540f
    const val START_GAP = 620f
    const val ROUND_TIME = 75f
}

object T {
    const val RING = 0
    const val DOUBLE = 1
    const val BARS = 2
    const val SQUARE = 3
    const val TRI = 4
    const val COUNT = 5
}

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

class Ob(
    val type: Int,
    val y: Float,
    val n: Int,         // cantidad de colores de este obstáculo
    val shift: Int,
    val speed: Float,   // grados/s (giro) o unidades/s (barras)
    val phase: Float,
    val size: Float,    // radio, semilado o circunradio según el tipo
    val inner: Float    // radio del anillo interior (solo DOUBLE)
) {
    /** Distancia máxima desde el centro hasta cualquier parte del obstáculo. */
    val reach: Float = when (type) {
        T.BARS -> Cfg.BAR_DY + Cfg.BAR_H + 6f
        T.SQUARE -> size * 1.4143f + Cfg.THICK / 2f
        else -> size + Cfg.THICK / 2f
    }

    /** Máscara de colores que de verdad aparecen en este obstáculo. */
    val present: Int = run {
        var m = 0
        when (type) {
            T.SQUARE -> for (k in 0 until 4) m = m or (1 shl ((k + shift) % n))
            T.TRI -> for (k in 0 until 3) m = m or (1 shl ((k + shift) % n))
            else -> for (k in 0 until n) m = m or (1 shl k)
        }
        m
    }
}

class Sw(val y: Float, val toColor: Int)

object Geo {
    private const val PI_F = 3.14159265f

    fun ringSegs(n: Int): Int = if (n == 2) 4 else n

    fun rot(o: Ob, t: Float): Float = o.phase + o.speed * t
    fun rotInner(o: Ob, t: Float): Float = o.phase - o.speed * t

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

    fun barColor(o: Ob, lane: Int, x: Float, t: Float): Int {
        val dir = if (lane == 0) 1f else -1f
        val off = o.phase * 3f + o.speed * dir * t
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
                for (lane in 0..1) {
                    val ly = o.y + if (lane == 0) -Cfg.BAR_DY else Cfg.BAR_DY
                    if (abs(by - ly) > Cfg.BAR_H + graze) continue
                    if (barColor(o, lane, bx, t) != color) return true
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
    const val PLAYABLE = 20
    const val WORLDS = 12
    const val PER_WORLD = 10

    fun isPlayable(level: Int): Boolean = level in 1..PLAYABLE

    /** Posición del nivel en la curva de dificultad: 0 = primer nivel, 1 = último jugable. */
    fun frac(level: Int): Float = ((level - 1).coerceAtLeast(0) / (PLAYABLE - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    /** Cada 5.º nivel es un poco más largo. */
    fun gates(level: Int): Int {
        val base = 5 + (frac(level) * 7f).toInt()
        return if (level % 5 == 0) base + 2 else base
    }

    fun colors(level: Int): Int {
        val f = frac(level)
        return if (f < 0.2f) 2 else if (f < 0.5f) 3 else 4
    }

    fun difficulty(level: Int): Float = frac(level)

    fun world(level: Int): Int = ((level - 1) / PER_WORLD).coerceIn(0, WORLDS - 1)

    /** Primer nivel de un mundo (0..11). */
    fun worldFirst(world: Int): Int = world * PER_WORLD + 1

    /** Un mundo está disponible si su primer nivel es jugable. */
    fun worldAvailable(world: Int): Boolean = worldFirst(world) <= PLAYABLE

    /** Desde qué punto de la curva aparece cada tipo de obstáculo (índice = T.*). */
    private val unlockFrac = floatArrayOf(0f, 0.65f, 0.05f, 0.2f, 0.4f)

    fun unlockLevel(type: Int): Int = 1 + Math.ceil((unlockFrac[type] * (PLAYABLE - 1)).toDouble() - 1e-6).toInt()

    /** Obstáculo desde el que aparece cada tipo en el modo infinito. */
    val unlockGate = intArrayOf(0, 22, 2, 6, 12)
}

class Course private constructor(
    val seed: Long,
    val isLevel: Boolean,
    val level: Int,
    val gateTotal: Int,      // -1 = infinito
    private val gateOffset: Int
) {
    companion object {
        fun level(level: Int): Course {
            val c = Course(level * 7919L + 13L, true, level, Levels.gates(level), 0)
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

    private fun colorsFor(g: Int): Int =
        if (isLevel) Levels.colors(level) else if (g < 5) 3 else 4

    private fun diffFor(i: Int, g: Int): Float =
        if (isLevel) {
            val d = Levels.difficulty(level)
            (d * 0.94f + 0.06f * (i / gateTotal.toFloat())).coerceIn(0f, 1f)
        } else {
            min(0.85f, g / 65f)
        }

    private fun pickType(i: Int, g: Int): Int {
        val w = floatArrayOf(3.0f, 1.4f, 2.4f, 2.2f, 2.2f)
        var sum = 0f
        for (t in 0 until T.COUNT) {
            val unlocked = if (isLevel) level >= Levels.unlockLevel(t) else g >= Levels.unlockGate[t]
            if (!unlocked) {
                w[t] = 0f
                continue
            }
            if (isLevel && t != T.RING && (level == Levels.unlockLevel(t) || level == Levels.unlockLevel(t) + 1)) {
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
        val n = colorsFor(g)
        val d = diffFor(i, g)
        val type = pickType(i, g)
        lastType = type

        val dirS = if (rnd.nextBoolean()) 1f else -1f
        val vari = 0.92f + rnd.nextFloat() * 0.16f
        val shift = rnd.nextInt(n)
        val phase = rnd.nextFloat() * 360f
        var speed: Float
        var size: Float
        var inner = 0f
        // Con 3 o 4 colores hay que esperar dentro del obstáculo a que llegue "tu" color arriba:
        // si gira muy lento esa espera se hace eterna, así que más colores = gira un poco más rápido.
        val spinLo = if (n <= 2) 32f else if (n == 3) 50f else 58f
        val spinHi = if (n <= 2) 46f else if (n == 3) 64f else 76f
        val spin = lerp(spinLo, spinHi, d) * vari
        when (type) {
            T.RING -> { speed = dirS * spin; size = 320f }
            T.DOUBLE -> { speed = dirS * spin * 0.9f; size = 345f; inner = 195f }
            T.BARS -> { speed = dirS * lerp(140f, 300f, d) * vari; size = 0f }
            T.SQUARE -> { speed = dirS * spin * 0.95f; size = 250f }
            else -> { speed = dirS * spin * 0.9f; size = 370f }
        }

        val gapFree = lerp(500f, 400f, d)
        val probe = Ob(type, 0f, n, shift, speed, phase, size, inner)
        val y: Float
        if (i == 0) {
            y = cursor - Cfg.START_GAP - probe.reach
        } else {
            y = cursor - gapFree - probe.reach
        }
        val ob = Ob(type, y, n, shift, speed, phase, size, inner)

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
            bvy = min(bvy + Cfg.GRAVITY * dt, Cfg.MAX_FALL)
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
