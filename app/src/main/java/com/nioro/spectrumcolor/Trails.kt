package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Estelas (rastro) de la bola. 0 = clásica; `need` = estrellas totales para usarla. */
object Trails {
    val names = arrayOf(
        "Clásica", "Chispas", "Láser", "Doble láser", "Arcoíris", "Cinta", "Neón", "Estrellas", "Corazones",
        "Burbujas", "Diamantes", "Fuego", "Humo", "Relámpago", "Píxeles", "Anillos", "Cometa", "Ondas",
        "Galaxia", "Hilos de luz", "Confeti"
    )
    val need = intArrayOf(0, 5, 12, 20, 30, 40, 52, 64, 78, 92, 108, 124, 142, 160, 180, 200, 222, 244, 268, 292, 318)
    val life = floatArrayOf(
        0.35f, 0.38f, 0.40f, 0.40f, 0.50f, 0.45f, 0.40f, 0.45f, 0.50f, 0.55f, 0.45f,
        0.50f, 0.60f, 0.25f, 0.40f, 0.45f, 0.55f, 0.50f, 0.50f, 0.50f, 0.60f
    )
    /** Tiempo máximo que se conserva un punto del rastro (el mayor de `life`). */
    const val MAX_LIFE = 0.62f
    val COUNT get() = names.size

    private val path = Path()
    private var nx = 0f
    private var ny = 0f

    private fun h(i: Int, k: Int): Float {
        val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f
        return v - floor(v)
    }

    private fun seg(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) {
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeCap = Paint.Cap.ROUND
        pp.strokeWidth = max(0.8f, w)
        pp.color = col
        c.drawLine(x0, y0, x1, y1, pp)
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        if (r <= 0.2f) return
        val pp = Gl.p()
        pp.color = col
        c.drawCircle(x, y, r, pp)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, w: Float, col: Int) {
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = w
        pp.color = col
        c.drawCircle(x, y, r, pp)
    }

    private fun normal(p: List<FloatArray>, i: Int) {
        val a = p[max(0, i - 1)]
        val b = p[minOf(p.size - 1, i + 1)]
        var dx = b[0] - a[0]
        var dy = b[1] - a[1]
        val l = sqrt(dx * dx + dy * dy)
        if (l < 0.001f) { nx = 1f; ny = 0f; return }
        dx /= l; dy /= l
        nx = -dy; ny = dx
    }

    private fun poly(c: Canvas, x: FloatArray, y: FloatArray, n: Int, w: Float, col: Int) {
        if (n < 2) return
        path.reset()
        path.moveTo(x[0], y[0])
        for (i in 1 until n) path.lineTo(x[i], y[i])
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeCap = Paint.Cap.ROUND
        pp.strokeJoin = Paint.Join.ROUND
        pp.strokeWidth = max(0.8f, w)
        pp.color = col
        c.drawPath(path, pp)
    }

    private fun spark(c: Canvas, x: Float, y: Float, r: Float, col: Int, a: Int) {
        seg(c, x - r, y, x + r, y, r * 0.22f, Gl.alpha(col, a))
        seg(c, x, y - r, x, y + r, r * 0.22f, Gl.alpha(col, a))
        dot(c, x, y, r * 0.3f, Gl.white(a))
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, rot: Float, fill: Int, stroke: Int) {
        path.reset()
        for (k in 0 until 10) {
            val ang = rot + k * 3.14159f / 5f
            val rr = if (k % 2 == 0) r else r * 0.45f
            val px = x + cos(ang) * rr
            val py = y + sin(ang) * rr
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        var pp = Gl.p()
        pp.color = fill
        c.drawPath(path, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 1.6f
        pp.color = stroke
        c.drawPath(path, pp)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float, fill: Int, stroke: Int) {
        path.reset()
        path.moveTo(x, y + s * 0.9f)
        path.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.7f, y - s * 1.3f, x, y - s * 0.4f)
        path.cubicTo(x + s * 0.7f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s * 0.9f)
        path.close()
        var pp = Gl.p()
        pp.color = fill
        c.drawPath(path, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 1.5f
        pp.color = stroke
        c.drawPath(path, pp)
    }

    private fun quad(c: Canvas, x: Float, y: Float, a: Float, b: Float, rot: Float, fill: Int, stroke: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        var pp = Gl.p()
        pp.color = fill
        c.drawRect(-a, -b, a, b, pp)
        if (stroke != 0) {
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 1.5f
            pp.color = stroke
            c.drawRect(-a, -b, a, b, pp)
        }
        c.restore()
    }

    private val bx = FloatArray(300)
    private val by = FloatArray(300)

    /** Dibuja el rastro `st`. p: puntos (x, y, edad en segundos), del más viejo al más nuevo. R: radio de la bola. */
    fun draw(c: Canvas, st: Int, p: List<FloatArray>, col: Int, pal: IntArray, R: Float, tm: Float) {
        if (p.isEmpty()) return
        val L = life[st.coerceIn(0, life.size - 1)]
        val n0 = p.size
        when (st) {
            0 -> for (t in p) {
                val f = 1f - t[2] / L
                if (f > 0f) dot(c, t[0], t[1], R * (0.3f + 0.55f * f), Gl.alpha(col, (f * 70f).toInt()))
            }
            1 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 2 != 0) continue
                spark(c, t[0] + (h(i, 1) - 0.5f) * R * 1.5f, t[1] + (h(i, 2) - 0.5f) * R * 1.5f, R * (0.25f + 0.4f * f), if (i % 4 == 1) Color.WHITE else col, (f * 255f).toInt())
            }
            2, 3, 6 -> {
                for (i in 1 until n0) {
                    val a = p[i - 1]; val b = p[i]; val f = 1f - b[2] / L
                    if (f <= 0f) continue
                    when (st) {
                        2 -> {
                            seg(c, a[0], a[1], b[0], b[1], R * 0.7f * f, Gl.alpha(col, (f * 80f).toInt()))
                            seg(c, a[0], a[1], b[0], b[1], R * 0.2f * f + 1f, Gl.white((f * 250f).toInt()))
                        }
                        3 -> {
                            normal(p, i)
                            val o = R * 0.5f * (0.4f + 0.6f * f)
                            seg(c, a[0] + nx * o, a[1] + ny * o, b[0] + nx * o, b[1] + ny * o, R * 0.2f * f + 1f, Gl.alpha(col, (f * 255f).toInt()))
                            seg(c, a[0] - nx * o, a[1] - ny * o, b[0] - nx * o, b[1] - ny * o, R * 0.2f * f + 1f, Gl.white((f * 255f).toInt()))
                        }
                        else -> {
                            seg(c, a[0], a[1], b[0], b[1], R * 1.4f * f, Gl.alpha(col, (f * 40f).toInt()))
                            seg(c, a[0], a[1], b[0], b[1], R * 0.8f * f, Gl.alpha(col, (f * 100f).toInt()))
                            seg(c, a[0], a[1], b[0], b[1], R * 0.22f * f + 1f, Gl.white((f * 255f).toInt()))
                        }
                    }
                }
            }
            4 -> for (i in 1 until n0) {
                val a = p[i - 1]; val b = p[i]; val f = 1f - b[2] / L
                if (f <= 0f) continue
                val hc = Color.HSVToColor(floatArrayOf((i * 22f + tm * 90f) % 360f, 0.85f, 1f))
                seg(c, a[0], a[1], b[0], b[1], R * 0.85f * (0.35f + 0.65f * f), Gl.alpha(hc, (f * 235f).toInt()))
            }
            5 -> for (i in 1 until n0) {
                val a = p[i - 1]; val b = p[i]; val f = 1f - b[2] / L
                if (f <= 0f) continue
                seg(c, a[0], a[1], b[0], b[1], R * 1.5f * f, Gl.alpha(col, (f * 190f).toInt()))
                seg(c, a[0], a[1], b[0], b[1], R * 0.35f * f, Gl.white((f * 200f).toInt()))
            }
            7 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 2 != 0) continue
                star(c, t[0], t[1], R * 0.6f * f + 2f, tm * 3f + i, Gl.alpha(col, (f * 220f).toInt()), Gl.white((f * 230f).toInt()))
            }
            8 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 3 != 0) continue
                heart(c, t[0] + (h(i, 3) - 0.5f) * R, t[1] - t[2] * R * 2f, R * 0.5f * f + 2f, Gl.alpha(if (i % 2 == 0) Color.rgb(255, 90, 140) else col, (f * 230f).toInt()), Gl.white((f * 200f).toInt()))
            }
            9 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 2 != 0) continue
                val r = R * (0.2f + 0.45f * h(i, 4))
                val x = t[0] + (h(i, 5) - 0.5f) * R * 1.2f
                val y = t[1] - t[2] * R * 3f
                dot(c, x, y, r, Gl.alpha(col, (f * 45f).toInt()))
                ring(c, x, y, r, 2f, Gl.white((f * 210f).toInt()))
                dot(c, x - r * 0.35f, y - r * 0.35f, r * 0.2f, Gl.white((f * 230f).toInt()))
            }
            10 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 2 != 0) continue
                val s = R * 0.42f * f + 1.5f
                quad(c, t[0], t[1], s, s, 45f + t[2] * 200f, Gl.alpha(col, (f * 210f).toInt()), Gl.white((f * 230f).toInt()))
            }
            11 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f) continue
                val fc = if (f > 0.66f) Color.rgb(255, 235, 110) else if (f > 0.33f) Color.rgb(255, 150, 40) else Color.rgb(230, 50, 30)
                dot(c, t[0] + (h(i, 6) - 0.5f) * R * 0.6f, t[1] - t[2] * R * 4f, R * 0.75f * f, Gl.alpha(fc, (f * 170f).toInt()))
            }
            12 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f) continue
                dot(c, t[0] + (h(i, 7) - 0.5f) * R * 0.8f, t[1] - t[2] * R * 2.2f, R * (0.35f + (1f - f) * 0.9f), Gl.alpha(Color.rgb(170, 176, 190), (f * 85f).toInt()))
            }
            13 -> {
                val seed = (tm * 28f).toInt()
                var m = 0
                for (i in 0 until n0) {
                    val t = p[i]
                    if (1f - t[2] / L <= 0f || m >= 299) continue
                    bx[m] = t[0] + (h(i + seed, 8) - 0.5f) * R * 1.3f
                    by[m] = t[1] + (h(i + seed, 9) - 0.5f) * R * 1.3f
                    m++
                }
                poly(c, bx, by, m, R * 0.7f, Gl.alpha(col, 70))
                poly(c, bx, by, m, R * 0.22f, Gl.white(245))
            }
            14 -> {
                val g = R * 0.5f
                for (i in 0 until n0) {
                    val t = p[i]; val f = 1f - t[2] / L
                    if (f <= 0f || i % 2 != 0) continue
                    val x = floor(t[0] / g) * g
                    val y = floor(t[1] / g) * g
                    val cc = if (i % 6 == 0) Color.WHITE else col
                    c.drawRect(x, y, x + g * 0.88f, y + g * 0.88f, Gl.p().also { it.color = Gl.alpha(cc, (f * 235f).toInt()) })
                }
            }
            15 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 3 != 0) continue
                ring(c, t[0], t[1], R * (0.3f + (1f - f) * 1.3f), 2.5f + 2f * f, Gl.alpha(col, (f * 210f).toInt()))
            }
            16 -> for (i in 1 until n0) {
                val a = p[i - 1]; val b = p[i]
                for (k in 0 until 3) {
                    val u = k / 3f
                    val age = a[2] + (b[2] - a[2]) * u
                    val f = 1f - age / L
                    if (f <= 0f) continue
                    val x = a[0] + (b[0] - a[0]) * u
                    val y = a[1] + (b[1] - a[1]) * u
                    val cc = Color.rgb(
                        (255 * f + Color.red(col) * (1f - f)).toInt().coerceIn(0, 255),
                        (255 * f + Color.green(col) * (1f - f)).toInt().coerceIn(0, 255),
                        (255 * f + Color.blue(col) * (1f - f)).toInt().coerceIn(0, 255)
                    )
                    dot(c, x, y, R * 0.95f * f, Gl.alpha(cc, (f * 150f).toInt()))
                }
            }
            17 -> for (s in -1..1 step 2) {
                var m = 0
                for (i in 0 until n0) {
                    val t = p[i]; val f = 1f - t[2] / L
                    if (f <= 0f || m >= 299) continue
                    normal(p, i)
                    val o = sin(t[2] * 20f + tm * 5f) * R * 0.7f * s
                    bx[m] = t[0] + nx * o; by[m] = t[1] + ny * o; m++
                }
                poly(c, bx, by, m, R * 0.3f, Gl.alpha(if (s > 0) col else Color.WHITE, 210))
            }
            18 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f) continue
                val tw = 0.55f + 0.45f * sin(tm * 9f + i * 1.7f)
                dot(c, t[0] + (h(i, 10) - 0.5f) * R * 2f, t[1] + (h(i, 11) - 0.5f) * R * 2f, R * 0.28f * f * (0.6f + tw) + 1f, Gl.alpha(pal[i % 4], (f * 255f * tw).toInt()))
            }
            19 -> for (s in 0 until 3) {
                var m = 0
                for (i in 0 until n0) {
                    val t = p[i]; val f = 1f - t[2] / L
                    if (f <= 0f || m >= 299) continue
                    normal(p, i)
                    val o = sin(t[2] * 16f + s * 2.094f + tm * 3f) * R * 0.8f
                    bx[m] = t[0] + nx * o; by[m] = t[1] + ny * o; m++
                }
                poly(c, bx, by, m, R * 0.22f, Gl.alpha(pal[s % 4], 230))
            }
            20 -> for (i in 0 until n0) {
                val t = p[i]; val f = 1f - t[2] / L
                if (f <= 0f || i % 2 != 0) continue
                quad(c, t[0] + (h(i, 12) - 0.5f) * R * 1.4f, t[1] + t[2] * R * 3f, R * 0.28f, R * 0.14f, i * 47f + t[2] * 500f, Gl.alpha(pal[i % 4], (f * 240f).toInt()), 0)
            }
            else -> {}
        }
    }
}
