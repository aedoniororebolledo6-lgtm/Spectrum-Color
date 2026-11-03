package com.nioro.spectrumcolor

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * IA de los enemigos del modo Aventura, sin dependencias de Android (se prueba en la JVM).
 *
 * Ideas tomadas de proyectos de código abierto y reescritas para este motor:
 *  - libgdx/gdx-ai (Apache 2.0): comportamientos de dirección (Seek, Arrive, Pursue, Flee, Wander)
 *    y árboles de comportamiento (Selector / Sequence / Condition / Action).
 *  - HoriK816/EnemyBehaviorTreeDemo y MysticWoods: "acercarse y atacar si el jugador está cerca", con rango de aggro.
 *  - 4bush/Game: jefes con fases por porcentaje de vida.
 *  - Quillraven/QuillyJumper: velocidad de salto a partir de la altura deseada, v = sqrt(2·g·h).
 *  - DavidLastra8/Development-platform-project: pisotón sobre enemigos con enfriamiento del daño.
 */

/** Comportamientos de dirección (steering). Devuelven la aceleración en out[0], out[1]. */
object Steer {
    /** Seek: acelerar al máximo hacia el objetivo. */
    fun seek(x: Float, y: Float, tx: Float, ty: Float, maxAcc: Float, out: FloatArray) {
        val dx = tx - x
        val dy = ty - y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1e-3f) { out[0] = 0f; out[1] = 0f; return }
        out[0] = dx / d * maxAcc
        out[1] = dy / d * maxAcc
    }

    /** Flee: lo contrario de Seek. */
    fun flee(x: Float, y: Float, tx: Float, ty: Float, maxAcc: Float, out: FloatArray) {
        seek(x, y, tx, ty, maxAcc, out)
        out[0] = -out[0]; out[1] = -out[1]
    }

    /**
     * Arrive: como Seek pero frena dentro de decelRadius para llegar sin pasarse.
     * timeToTarget suaviza el cambio de velocidad (igual que en gdx-ai).
     */
    fun arrive(x: Float, y: Float, vx: Float, vy: Float, tx: Float, ty: Float, maxSpeed: Float, maxAcc: Float,
               decelRadius: Float, tolerance: Float, out: FloatArray, timeToTarget: Float = 0.1f) {
        val dx = tx - x
        val dy = ty - y
        val d = sqrt(dx * dx + dy * dy)
        if (d <= tolerance) { out[0] = -vx / timeToTarget; out[1] = -vy / timeToTarget; limit(out, maxAcc); return }
        var sp = maxSpeed
        if (d <= decelRadius) sp *= d / decelRadius
        out[0] = (dx / d * sp - vx) / timeToTarget
        out[1] = (dy / d * sp - vy) / timeToTarget
        limit(out, maxAcc)
    }

    /**
     * Pursue: Seek hacia donde ESTARÁ el objetivo. El tiempo de predicción es distancia / rapidez propia,
     * con un tope (maxPrediction), igual que en gdx-ai.
     */
    fun pursue(x: Float, y: Float, vx: Float, vy: Float, tx: Float, ty: Float, tvx: Float, tvy: Float,
               maxAcc: Float, maxPrediction: Float, out: FloatArray) {
        val t = predictTime(x, y, vx, vy, tx, ty, maxPrediction)
        seek(x, y, tx + tvx * t, ty + tvy * t, maxAcc, out)
    }

    fun predictTime(x: Float, y: Float, vx: Float, vy: Float, tx: Float, ty: Float, maxPrediction: Float): Float {
        val d2 = (tx - x) * (tx - x) + (ty - y) * (ty - y)
        val s2 = vx * vx + vy * vy
        if (s2 <= 0f) return maxPrediction
        val p2 = d2 / s2
        return if (p2 < maxPrediction * maxPrediction) sqrt(p2) else maxPrediction
    }

    /**
     * Wander: un punto que gira al azar sobre un círculo delante del agente. orient es el estado (se actualiza).
     * Devuelve la nueva orientación.
     */
    fun wander(x: Float, y: Float, heading: Float, orient: Float, rate: Float, offset: Float, radius: Float,
               maxAcc: Float, rnd: () -> Float, out: FloatArray): Float {
        val o = orient + (rnd() - rnd()) * rate
        val cx = x + cos(heading) * offset
        val cy = y + sin(heading) * offset
        seek(x, y, cx + cos(heading + o) * radius, cy + sin(heading + o) * radius, maxAcc, out)
        return o
    }

    fun limit(v: FloatArray, max: Float) {
        val l = sqrt(v[0] * v[0] + v[1] * v[1])
        if (l > max && l > 0f) { v[0] *= max / l; v[1] *= max / l }
    }
}

/** Física simple de tiro. */
object Ballistics {
    /**
     * Ángulo de elevación (radianes, hacia arriba) para alcanzar (dx, dy) — dy positivo hacia ABAJO como en pantalla —
     * con rapidez v y gravedad g. Usa la trayectoria baja. NaN si no alcanza.
     */
    fun lowAngle(dx: Float, dy: Float, v: Float, g: Float): Float {
        val x = abs(dx)
        if (x < 1f) return Float.NaN
        val y = -dy
        val v2 = v * v
        val disc = v2 * v2 - g * (g * x * x + 2f * y * v2)
        if (disc < 0f) return Float.NaN
        return atan((v2 - sqrt(disc)) / (g * x))
    }

    /**
     * Velocidad (vx, vy) para dar en el blanco adelantándose a su movimiento horizontal (tvx).
     * Si no se alcanza, tira a 45°. Devuelve en out.
     */
    fun aim(sx: Float, sy: Float, tx: Float, ty: Float, tvx: Float, v: Float, g: Float, out: FloatArray) {
        var ax = tx
        var th = Float.NaN
        for (i in 0 until 3) {
            th = lowAngle(ax - sx, ty - sy, v, g)
            if (th.isNaN()) break
            val tFlight = abs(ax - sx) / (v * cos(th)).coerceAtLeast(1f)
            ax = tx + tvx * tFlight.coerceAtMost(1.2f)
        }
        if (th.isNaN()) th = 0.785f
        val dir = if (ax >= sx) 1f else -1f
        out[0] = dir * v * cos(th)
        out[1] = -v * sin(th)
    }

    /** Velocidad vertical de salto para alcanzar la altura h con gravedad g (QuillyJumper). */
    fun jumpV(h: Float, g: Float): Float = sqrt(2f * g * h)
}

/**
 * Árbol de comportamiento mínimo (como el de gdx-ai pero sin reflexión ni clonación).
 * Se evalúa entero en cada paso desde la raíz: un Selector prueba sus hijos por prioridad.
 */
class BTree<C>(private val root: Node<C>) {
    enum class St { OK, FAIL, RUN }

    abstract class Node<C> { abstract fun tick(c: C, dt: Float): St }

    class Selector<C>(private vararg val kids: Node<C>) : Node<C>() {
        override fun tick(c: C, dt: Float): St {
            for (k in kids) { val s = k.tick(c, dt); if (s != St.FAIL) return s }
            return St.FAIL
        }
    }

    class Sequence<C>(private vararg val kids: Node<C>) : Node<C>() {
        override fun tick(c: C, dt: Float): St {
            for (k in kids) { val s = k.tick(c, dt); if (s != St.OK) return s }
            return St.OK
        }
    }

    class Cond<C>(private val f: (C) -> Boolean) : Node<C>() {
        override fun tick(c: C, dt: Float): St = if (f(c)) St.OK else St.FAIL
    }

    class Act<C>(private val f: (C, Float) -> St) : Node<C>() {
        override fun tick(c: C, dt: Float): St = f(c, dt)
    }

    fun tick(c: C, dt: Float): St = root.tick(c, dt)
}
