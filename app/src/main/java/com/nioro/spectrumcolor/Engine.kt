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
    const val STAR_R = 42f      // radio de recogida de la estrella (un poco más grande que el dibujo: roza y cuenta)
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

    /** Zonas de deslizar: velocidad máxima lateral de la bola y rapidez con que vuelve al centro al salir de la zona. */
    const val SLIDE_VMAX = 2400f
    const val SLIDE_RETURN = 12f
    const val PILLAR_HH = 44f    // semialtura de un pilar
    const val SLIDE_MARGIN = 6f  // margen de la bola contra las paredes
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
    const val ARC = 11       // anillo abierto: un tramo del círculo está vacío y se puede cruzar por ahí
    const val PULSE = 12     // barra de ancho completo que cambia de color con el tiempo
    const val HEX = 13       // hexágono giratorio
    const val LASER = 14     // rayo que se enciende y se apaga (mata sin importar el color)
    const val SGATE = 15     // muro con un hueco que se mueve: hay que deslizarse hasta el hueco
    const val CBAND = 16     // franja de tramos de color: desliza hasta un tramo de tu color
    const val SAW = 17       // sierras que patrullan de lado a lado (matan sin importar el color)
    const val PILLAR = 18    // pilar que oscila: hay que rodearlo
    const val COUNT = 19

    /** Obstáculos que solo aparecen dentro de las zonas de deslizar. */
    fun isSlide(type: Int): Boolean = type >= SGATE
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
    fun copy(
        type: Int = this.type, y: Float = this.y, n: Int = this.n, shift: Int = this.shift, speed: Float = this.speed,
        phase: Float = this.phase, size: Float = this.size, inner: Float = this.inner, aux: Float = this.aux,
        drift: Float = this.drift, fade: Boolean = this.fade, wob: Float = this.wob
    ): Ob = Ob(type, y, n, shift, speed, phase, size, inner, aux, drift, fade, wob)

    /** Distancia máxima desde el centro hasta cualquier parte del obstáculo. */
    val reach: Float = when (type) {
        T.BARS, T.PEND, T.GATE, T.PULSE, T.LASER, T.SGATE, T.CBAND -> Cfg.BAR_DY + Cfg.BAR_H + 6f
        T.SAW -> aux + 6f
        T.PILLAR -> Cfg.PILLAR_HH + 6f
        T.HEX -> size + Cfg.THICK / 2f
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
            T.HEX -> for (k in 0 until 6) m = m or (1 shl ((k + shift) % n))
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

    /** Centro del hueco de una compuerta SGATE: siempre a un lado del centro (inner = desplazamiento), con un vaivén suave. */
    fun sgateX(o: Ob, t: Float): Float = Cfg.W / 2f + o.inner + o.size * sin(rot(o, t) * PI_F / 180f)

    /** Posición de la sierra k de un obstáculo SAW (inner = cantidad de sierras). */
    fun sawX(o: Ob, t: Float, k: Int): Float {
        val cnt = max(1, o.inner.toInt())
        return Cfg.W / 2f + o.size * sin((rot(o, t) + k * 360f / cnt) * PI_F / 180f)
    }

    /** Color del tramo de una franja CBAND en la posición x (ancho de tramo = size). */
    fun cbandColor(o: Ob, x: Float, t: Float): Int {
        val off = o.phase * 3f + o.speed * t
        val j = floor((x - off) / o.size).toInt()
        return (((j % o.n) + o.n) % o.n + o.shift) % o.n
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

    /** Índice del color que muestra una barra PULSE en el instante t. */
    fun pulseIndex(o: Ob, t: Float): Int {
        val j = floor(o.phase / 360f * o.n + o.speed * t).toInt()
        return (((j % o.n) + o.n) % o.n + o.shift) % o.n
    }

    /** Fracción (0..1) del color actual de PULSE que ya pasó. */
    fun pulseFrac(o: Ob, t: Float): Float {
        val x = o.phase / 360f * o.n + o.speed * t
        return x - floor(x)
    }

    /** Fracción del ciclo del rayo (0..1) y si está encendido. */
    fun laserU(o: Ob, t: Float): Float {
        val x = o.phase / 360f + o.speed * t
        return x - floor(x)
    }

    fun laserOn(o: Ob, t: Float): Boolean = laserU(o, t) >= 1f - o.aux

    /** true si el rayo está a punto de encenderse (aviso). */
    fun laserWarn(o: Ob, t: Float): Boolean {
        val u = laserU(o, t)
        return u >= 1f - o.aux - 0.14f && u < 1f - o.aux
    }

    fun polySides(o: Ob): Int = if (o.type == T.SQUARE) 4 else if (o.type == T.HEX) 6 else 3

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
                val rel = bx - swingX(o, t)
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
                return abs(bx - swingX(o, t)) > o.aux - Cfg.BALL_R * 0.75f
            }
            T.SGATE -> {
                if (abs(by - o.y) > Cfg.BAR_H + graze) return false
                return abs(bx - sgateX(o, t)) > o.aux - Cfg.BALL_R * 0.75f
            }
            T.CBAND -> {
                if (abs(by - o.y) > Cfg.BAR_H + graze) return false
                return cbandColor(o, bx, t) != color
            }
            T.SAW -> {
                val rr = o.aux + Cfg.BALL_R * 0.8f
                val dy = by - o.y
                for (k in 0 until max(1, o.inner.toInt())) {
                    val dx = bx - sawX(o, t, k)
                    if (dx * dx + dy * dy < rr * rr) return true
                }
                return false
            }
            T.PILLAR -> {
                return abs(bx - swingX(o, t)) < o.aux + Cfg.BALL_R * 0.8f && abs(by - o.y) < Cfg.PILLAR_HH + Cfg.BALL_R * 0.8f
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
            T.ARC -> {
                val dx = bx - Cfg.W / 2f
                val dy = by - o.y
                val dist = sqrt(dx * dx + dy * dy)
                if (abs(dist - o.size) > Cfg.THICK / 2f + graze) return false
                var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - rot(o, t)
                a = ((a % 360f) + 360f) % 360f
                // un margen pequeño en los bordes del hueco, para que rozarlo no mate
                val gap = o.aux
                if (a < gap) return false
                val span = (360f - gap) / o.n
                val seg = min(o.n - 1, ((a - gap) / span).toInt())
                return (seg + o.shift) % o.n != color
            }
            T.PULSE -> {
                if (abs(by - o.y) > Cfg.BAR_H + graze) return false
                return pulseIndex(o, t) != color
            }
            T.LASER -> {
                if (abs(by - o.y) > Cfg.BAR_H * 0.75f + graze) return false
                return laserOn(o, t)
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
        // variantes elegidas con `DELAYS=0,4,8 tools/run_tests.sh salt`: posibles incluso con 66 ms de retraso al tocar
        it[11] = 3
        it[12] = 2
        it[15] = 2
        it[17] = 5
        it[18] = 1
        it[20] = 1
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

    /** Nivel en que se estrena cada tipo de obstáculo (índice = T.*). El anillo doble no se usa. */
    private val unlockAt = intArrayOf(
        1, 999, 1, 5, 9,        // anillo, doble, barras, cuadrado, triángulo
        21, 31, 41, 51, 61, 71, // pinchos, péndulo, cruz, puertas, órbitas, opuestas
        4, 7, 13, 46,           // arco abierto, barra que cambia de color, hexágono, rayo
        999, 999, 999, 999      // obstáculos de deslizar: solo dentro de las zonas
    )

    fun unlockLevel(type: Int): Int = unlockAt[type]

    /** Primer nivel con zonas de deslizar (mundo 2). */
    const val SLIDE_FROM = 11

    /** Tamaños de los bloques de deslizar de un nivel (cada obstáculo del bloque cuenta como un obstáculo más). */
    fun slidePlan(level: Int): IntArray = when {
        level < SLIDE_FROM -> IntArray(0)
        level <= 20 -> intArrayOf(2)
        level <= 40 -> intArrayOf(if (level % 2 == 0) 3 else 2)
        level <= 90 -> if (level % 2 == 0) intArrayOf(2, 3) else intArrayOf(3)
        level <= 110 -> intArrayOf(3, 3)
        else -> if (level % 10 == 0) intArrayOf(3, 3, 3) else intArrayOf(3, 3)
    }

    /** Cuántos obstáculos normales se generan antes de que empiece el bloque b. */
    fun slideStart(level: Int, b: Int, normal: Int, nb: Int): Int {
        val raw = ((b + 1) * normal) / (nb + 1)
        return max(3, raw)
    }

    /** Dificultad propia de los bloques (0 = nivel 11, 1 = nivel 120). */
    fun slideDiff(level: Int): Float = ((level - SLIDE_FROM).coerceAtLeast(0) / (COUNT - SLIDE_FROM).toFloat()).coerceIn(0f, 1f)

    fun totalGates(level: Int): Int = gates(level) + slidePlan(level).sum()

    /** Obstáculo desde el que aparece cada tipo en el modo infinito. */
    val unlockGate = intArrayOf(0, 22, 0, 4, 8, 28, 34, 40, 46, 52, 58, 2, 5, 10, 64, 9999, 9999, 9999, 9999)
}

class Zone(val bottom: Float, val top: Float)

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
            Custom.map[level]?.let { return custom(level, it) }
            val c = Course(level * 7919L + 13L + salt * 104729L, true, level, Levels.totalGates(level), 0)
            c.build()
            return c
        }

        /** Nivel editado a mano (solo la versión de desarrollador guarda estos datos). */
        fun custom(level: Int, d: LevelData): Course {
            val c = Course(level * 7919L + 13L, true, level, d.obs.size, 0)
            c.obs.addAll(d.obs.sortedByDescending { it.y })
            c.sws.addAll(d.sws.sortedByDescending { it.y })
            c.zones.addAll(d.zones.sortedByDescending { it.bottom })
            c.startColor = d.startColor
            c.finishY = d.finishY
            c.palette = d.palette
            return c
        }

        /** Recorrido infinito. gateOffset sube la dificultad inicial (multijugador). */
        fun endless(seed: Long, gateOffset: Int = 0): Course = Course(seed, false, 0, -1, gateOffset)
    }

    val obs = ArrayList<Ob>()
    val sws = ArrayList<Sw>()
    val zones = ArrayList<Zone>()
    private var normalCount = 0
    private val normalTotal: Int = if (isLevel) Levels.gates(level) else -1

    /** true si la altura y está dentro de una zona de deslizar (ahí la bola se puede mover de lado). */
    fun inZone(y: Float): Boolean {
        for (z in zones) if (y <= z.bottom && y >= z.top) return true
        return false
    }
    var startColor = 0
        private set
    /** Colores del mundo elegidos a mano en el editor (-1 = los del mundo del nivel). */
    var palette = -1
        private set
    var finishY = Float.NEGATIVE_INFINITY
        private set

    private val rnd = Random(seed)
    private var cursor = 0f          // y del borde superior del último elemento generado
    private var curColor = -1
    private var lastType = -1

    val totalGates: Int get() = gateTotal

    /** Copia editable del recorrido (para el editor de niveles). */
    fun toData(): LevelData = LevelData(startColor, finishY, ArrayList(obs), ArrayList(sws), ArrayList(zones), palette)

    private fun build() {
        val plan = if (isLevel) Levels.slidePlan(level) else IntArray(0)
        val starts = IntArray(plan.size) { Levels.slideStart(level, it, normalTotal, plan.size) }
        for (b in 1 until starts.size) if (starts[b] <= starts[b - 1] + 1) starts[b] = starts[b - 1] + 2
        var nb = 0
        while (normalCount < normalTotal) {
            genNext()
            while (nb < plan.size && normalCount == starts[nb]) {
                genBlock(plan[nb], nb)
                nb++
            }
        }
        finishY = cursor - 560f
    }

    /**
     * Zona de deslizar: un tramo con obstáculos laterales. Dentro de la zona la bola se mueve de lado con el dedo;
     * al salir vuelve sola al centro (y se salta como siempre). Usa su propio azar para no cambiar los obstáculos normales.
     */
    private fun genBlock(size: Int, idx: Int) {
        val sr = Random(seed * 31L + 977L * (idx + 1) + 5L)
        val sd = Levels.slideDiff(level)
        val zoneBottom = cursor - 110f
        cursor -= 190f
        val kinds = ArrayList<Int>()
        kinds.add(T.SGATE)
        kinds.add(T.CBAND)
        if (level >= 21) kinds.add(T.SAW)
        if (level >= 31) kinds.add(T.PILLAR)
        var last = -1
        for (k in 0 until size) {
            var type = kinds[sr.nextInt(kinds.size)]
            if (type == last) type = kinds[(kinds.indexOf(type) + 1) % kinds.size]
            last = type
            val n = max(3, curColor + 1) // incluye el color con el que llega la bola (no hay cambiadores dentro de la zona)
            val dirS = if (sr.nextBoolean()) 1f else -1f
            val vari = 0.92f + sr.nextFloat() * 0.16f
            val shift = sr.nextInt(n)
            val phase = sr.nextFloat() * 360f
            var speed = 0f
            var sz = 0f
            var inner = 0f
            var aux = 0f
            when (type) {
                T.SGATE -> { speed = dirS * lerp(40f, 85f, sd) * vari; sz = lerp(40f, 160f, sd); aux = lerp(175f, 135f, sd); inner = (if (sr.nextBoolean()) 1f else -1f) * lerp(260f, 320f, sd) }
                T.CBAND -> { speed = dirS * lerp(110f, 240f, sd) * vari; sz = lerp(360f, 280f, sd) }
                T.SAW -> { speed = dirS * lerp(75f, 120f, sd) * vari; sz = lerp(300f, 430f, sd); aux = lerp(52f, 68f, sd); inner = if (sd < 0.5f) 2f else 3f }
                else -> { speed = dirS * lerp(40f, 80f, sd) * vari; sz = lerp(0f, 200f, sd); aux = lerp(120f, 170f, sd) }
            }
            val probe = Ob(type, 0f, n, shift, speed, phase, sz, inner, aux)
            val gap = lerp(430f, 385f, sd)
            val y = cursor - gap - probe.reach
            obs.add(Ob(type, y, n, shift, speed, phase, sz, inner, aux))
            cursor = y - probe.reach
        }
        val zoneTop = cursor - 140f
        zones.add(Zone(zoneBottom, zoneTop))
        cursor = zoneTop - 200f
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
            (d * 0.94f + 0.06f * cap * (i / normalTotal.toFloat())).coerceIn(0f, 1f)
        } else {
            min(0.85f, g / 65f)
        }

    /** Tipos que pueden ser el primer obstáculo de un recorrido (los más "amables"). */
    private fun firstOk(t: Int): Boolean =
        t == T.RING || t == T.BARS || t == T.SQUARE || t == T.TRI || t == T.SPIKES || t == T.PEND ||
            t == T.CROSS || t == T.ARC || t == T.PULSE || t == T.HEX

    private var lastType2 = -1

    private fun pickType(i: Int, g: Int): Int {
        val hard = isLevel && Levels.isHard(level)
        val w = if (hard) floatArrayOf(1.6f, 0f, 1.3f, 1.4f, 1.4f, 1.4f, 1.5f, 1.6f, 1.4f, 1.5f, 1.2f, 1.6f, 1.5f, 1.5f, 1.3f, 0f, 0f, 0f, 0f)
        else floatArrayOf(2.2f, 0f, 2.2f, 2.0f, 2.0f, 1.6f, 1.6f, 1.6f, 1.4f, 1.6f, 1.4f, 2.0f, 1.8f, 1.6f, 1.3f, 0f, 0f, 0f, 0f)
        val wf = if (isLevel) Levels.worldFirst(Levels.world(level)) else 0
        var sum = 0f
        for (t in 0 until T.COUNT) {
            if (t == T.DOUBLE && !Cfg.ENABLE_DOUBLE) {
                w[t] = 0f
                continue
            }
            val ul = if (isLevel) Levels.unlockLevel(t) else 0
            val unlocked = if (isLevel) level >= ul else g >= Levels.unlockGate[t]
            if (!unlocked || (i == 0 && !firstOk(t))) {
                w[t] = 0f
                continue
            }
            if (isLevel && ul > 1) {
                // el obstáculo nuevo aparece más seguido para aprenderlo
                if (hard) {
                    if (ul >= wf && ul < wf + Levels.PER_WORLD) w[t] *= if (level <= wf + 1) 3f else 1.5f
                } else if (level == ul || level == ul + 1) {
                    w[t] *= 4f
                }
            }
            if (t == lastType) w[t] *= 0.3f else if (t == lastType2) w[t] *= 0.65f
            sum += w[t]
        }
        if (sum <= 0f) return T.RING
        var r = rnd.nextFloat() * sum
        for (t in 0 until T.COUNT) {
            r -= w[t]
            if (r <= 0f && w[t] > 0f) return t
        }
        return T.RING
    }

    private fun genNext() {
        val i = normalCount
        normalCount++
        val g = i + gateOffset
        val d = diffFor(i, g)
        val type = pickType(i, g)
        lastType2 = lastType
        lastType = type
        val hard = isLevel && Levels.isHard(level)
        var n = colorsFor(i, g)
        // en los niveles difíciles solo los obstáculos con "espera natural" usan 4 colores
        if ((hard || !isLevel) && n == 4 && type != T.RING && type != T.SPIKES && type != T.CROSS && type != T.ORBIT && type != T.ARC) n = 3

        val dirS = if (rnd.nextBoolean()) 1f else -1f
        val vari = 0.92f + rnd.nextFloat() * 0.16f
        val sizeVar = rnd.nextFloat()
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
            T.RING -> { speed = dirS * spin; size = lerp(265f, 340f, sizeVar) }
            T.DOUBLE -> { speed = dirS * spin * 0.9f; size = 345f; inner = 195f }
            T.BARS -> { speed = dirS * barSpd * vari; size = 0f }
            T.SQUARE -> { speed = dirS * spin * 0.95f; size = lerp(225f, 260f, sizeVar) }
            T.SPIKES -> { speed = dirS * spin * 0.85f; size = 330f; aux = lerp(4f, 8f, d) }
            T.PEND -> { speed = dirS * lerp(70f, 120f, d) * vari; size = 330f; aux = lerp(250f, 205f, d) }
            T.CROSS -> { speed = dirS * spin * 0.8f; size = 290f; aux = 95f }
            T.GATE -> { speed = dirS * lerp(70f, 115f, d) * vari; size = 330f; aux = lerp(175f, 135f, d) }
            T.ORBIT -> { speed = dirS * spin * 0.9f; size = 270f; inner = 130f }
            T.COUNTER -> { speed = dirS * lerp(150f, 250f, d) * vari; size = 0f; aux = 120f }
            T.ARC -> { speed = dirS * spin * 0.9f; size = lerp(280f, 330f, sizeVar); aux = lerp(125f, 90f, d) }
            T.PULSE -> { speed = lerp(0.62f, 0.85f, d) * vari; size = 0f }
            T.HEX -> { speed = dirS * spin * 0.9f; size = lerp(280f, 310f, sizeVar) }
            T.LASER -> { speed = lerp(0.36f, 0.45f, d) * vari; size = 0f; aux = lerp(0.42f, 0.52f, d) }
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
            val rotating = type == T.RING || type == T.ARC || type == T.HEX || type == T.SQUARE || type == T.TRI || type == T.SPIKES || type == T.CROSS || type == T.ORBIT
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

    /** Posición lateral de la bola (solo cambia dentro de las zonas de deslizar) y hacia dónde la lleva el dedo. */
    var bx = Cfg.W / 2f
    var targetX = Cfg.W / 2f
    var vmax = Cfg.SLIDE_VMAX

    /** Causa de la muerte (solo diagnóstico): -2 = sigue vivo, -1 = cayó fuera de la pantalla, >=0 = índice del obstáculo. */
    var deathObs = -2

    /** Velocidad de giro de los obstáculos (1 = normal; menos = modo fácil). La física de la pelota no cambia. */
    var timeScale = 1f

    fun copyFrom(o: Sim) {
        t = o.t; by = o.by; bvy = o.bvy; camY = o.camY; color = o.color
        swCount = o.swCount; starCount = o.starCount; started = o.started
        dead = o.dead; finished = o.finished; taps = o.taps; timeScale = o.timeScale; deathObs = o.deathObs
        bx = o.bx; targetX = o.targetX; vmax = o.vmax
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

        // movimiento lateral: libre dentro de una zona; fuera, la bola vuelve al centro
        if (course.zones.isNotEmpty() && course.inZone(by)) {
            val mv = vmax * dt
            bx += (targetX - bx).coerceIn(-mv, mv)
            bx = bx.coerceIn(Cfg.BALL_R + Cfg.SLIDE_MARGIN, Cfg.W - Cfg.BALL_R - Cfg.SLIDE_MARGIN)
        } else if (bx != Cfg.W / 2f) {
            bx += (Cfg.W / 2f - bx) * (1f - exp(-Cfg.SLIDE_RETURN * dt))
            if (abs(bx - Cfg.W / 2f) < 0.3f) bx = Cfg.W / 2f
            targetX = bx
        } else {
            targetX = bx
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
            if (Geo.hits(o, bx, by, color, t)) {
                dead = true
                deathObs = i
                return
            }
        }
        // meta
        if (course.isLevel && by < course.finishY) finished = true
    }
}


/** Un nivel completo como datos: lo que edita el editor (solo versión de desarrollador). */
class LevelData(var startColor: Int, var finishY: Float, val obs: ArrayList<Ob>, val sws: ArrayList<Sw>, val zones: ArrayList<Zone>, var palette: Int = -1) {
    fun sortAll() {
        obs.sortByDescending { it.y }
        sws.sortByDescending { it.y }
        zones.sortByDescending { it.bottom }
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("S ").append(startColor).append(' ').append(finishY).append('\n')
        sb.append("P ").append(palette).append('\n')
        for (o in obs) {
            sb.append("O ").append(o.type).append(' ').append(o.y).append(' ').append(o.n).append(' ').append(o.shift).append(' ')
                .append(o.speed).append(' ').append(o.phase).append(' ').append(o.size).append(' ').append(o.inner).append(' ')
                .append(o.aux).append(' ').append(o.drift).append(' ').append(if (o.fade) 1 else 0).append(' ').append(o.wob).append('\n')
        }
        for (s in sws) sb.append("W ").append(s.y).append(' ').append(s.toColor).append('\n')
        for (z in zones) sb.append("Z ").append(z.bottom).append(' ').append(z.top).append('\n')
        return sb.toString()
    }

    fun copy(): LevelData = LevelData(startColor, finishY, ArrayList(obs), ArrayList(sws), ArrayList(zones), palette)

    companion object {
        fun parse(s: String): LevelData? {
            try {
                var sc = 0
                var fy = -3000f
                var pal = -1
                val o = ArrayList<Ob>()
                val w = ArrayList<Sw>()
                val z = ArrayList<Zone>()
                for (line in s.split('\n')) {
                    val p = line.trim().split(' ')
                    if (p.isEmpty() || p[0].isEmpty()) continue
                    when (p[0]) {
                        "P" -> pal = p[1].toInt().coerceIn(-1, 40)
                        "S" -> { sc = p[1].toInt().coerceIn(0, 3); fy = p[2].toFloat() }
                        "O" -> {
                            val type = p[1].toInt()
                            val n = p[3].toInt().coerceIn(2, 4)
                            if (type !in 0 until T.COUNT) return null
                            o.add(Ob(type, p[2].toFloat(), n, p[4].toInt().coerceIn(0, n - 1), p[5].toFloat(), p[6].toFloat(), p[7].toFloat(),
                                p[8].toFloat(), p[9].toFloat(), p[10].toFloat(), p[11] == "1", p[12].toFloat()))
                        }
                        "W" -> w.add(Sw(p[1].toFloat(), p[2].toInt().coerceIn(0, 3)))
                        "Z" -> z.add(Zone(p[1].toFloat(), p[2].toFloat()))
                    }
                }
                if (o.isEmpty() || fy >= 0f) return null
                return LevelData(sc, fy, o, w, z, pal)
            } catch (e: Exception) {
                return null
            }
        }
    }
}

/** Niveles reemplazados por el editor (vacío en la versión normal). */
object Custom {
    val map = HashMap<Int, LevelData>()
}
