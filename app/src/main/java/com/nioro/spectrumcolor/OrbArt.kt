package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/** Dibujo de las esferas propias de tipo Animal (receta de rasgos) y Dibujo IA (formas escritas por el modelo). */
object OrbArt {
    private fun h(i: Int, k: Int): Float {
        val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // ---------------------------------------------------------------- Dibujo IA
    fun drawArt(c: Canvas, x: Float, y: Float, r: Float, o: Orb, tm: Float) {
        val t = tm * (0.35f + 0.35f * o.sp)
        val k = r / 100f * (0.55f + 0.1f * o.sz) * (1f + 0.03f * sin(t * 3f))
        val oy = y + sin(t * 2f) * r * 0.02f
        if (o.art.isEmpty()) {
            Gl.text(c, "?", x, y + r * 0.3f, r * 1.0f, Gl.medium, Gl.white(160))
            return
        }
        // fondo oscuro para que los colores del dibujo se vean sobre la bola brillante
        c.drawCircle(x, y, r, Gl.p().also { it.color = Color.argb(215, 12, 14, 30) })
        for (sh in o.art.split(';')) {
            val q = sh.trim().split(' ')
            if (q.size < 2) continue
            val v = q.drop(1).mapNotNull { it.toIntOrNull() }
            fun colAt(i: Int) = Orb.pal[v.getOrElse(i) { 0 }.coerceIn(0, Orb.pal.size - 1)]
            when (q[0]) {
                "c" -> if (v.size >= 4) c.drawCircle(x + v[0] * k, oy + v[1] * k, v[2] * k, Gl.p().also { it.color = colAt(3) })
                "o" -> if (v.size >= 5) {
                    Gl.rect.set(x + minOf(v[0], v[2]) * k, oy + minOf(v[1], v[3]) * k, x + maxOf(v[0], v[2]) * k, oy + maxOf(v[1], v[3]) * k)
                    c.drawOval(Gl.rect, Gl.p().also { it.color = colAt(4) })
                }
                "l" -> if (v.size >= 6) Gl.line(c, x + v[0] * k, oy + v[1] * k, x + v[2] * k, oy + v[3] * k, v[4] * k, colAt(5))
                "p" -> if (v.size >= 7) {
                    Gl.path.reset()
                    var i = 1
                    while (i + 1 < v.size) {
                        if (i == 1) Gl.path.moveTo(x + v[i] * k, oy + v[i + 1] * k) else Gl.path.lineTo(x + v[i] * k, oy + v[i + 1] * k)
                        i += 2
                    }
                    Gl.path.close()
                    c.drawPath(Gl.path, Gl.p().also { it.color = colAt(0) })
                }
            }
        }
    }

    // ---------------------------------------------------------------- Animales
    fun drawAnimal(c: Canvas, x: Float, y: Float, r: Float, o: Orb, tm: Float) {
        val d = Animals.all[o.pat.coerceIn(0, Animals.count - 1)]
        val a = Orb.pal[o.c1]
        val b = Orb.pal[o.c2]
        val t = tm * (0.35f + 0.35f * o.sp)
        val s = r * (0.62f + 0.1f * o.sz) * (1f + 0.03f * sin(t * 3f))
        val hw = d.hw / 100f
        val hh = d.hh / 100f
        val cy = 0.08f + sin(t * 2f) * 0.02f
        val esz = d.esz / 100f
        val dark = Color.argb(255, 20, 22, 38)
        val light = 0xFFFFF3E0.toInt()
        val shade = Gl.darken(a, 0.7f)
        val blink = if (sin(t * 1.3f) > 0.96f) 0.15f else 1f

        fun px(u: Float) = x + u * s
        fun py(v: Float) = y + v * s
        fun circ(u: Float, v: Float, rr: Float, col: Int) = c.drawCircle(px(u), py(v), rr * s, Gl.p().also { it.color = col })
        fun oval(u0: Float, v0: Float, u1: Float, v1: Float, col: Int) {
            Gl.rect.set(px(u0), py(v0), px(u1), py(v1))
            c.drawOval(Gl.rect, Gl.p().also { it.color = col })
        }
        fun poly(col: Int, vararg q: Float) {
            Gl.path.reset()
            Gl.path.moveTo(px(q[0]), py(q[1]))
            var i = 2
            while (i < q.size) { Gl.path.lineTo(px(q[i]), py(q[i + 1])); i += 2 }
            Gl.path.close()
            c.drawPath(Gl.path, Gl.p().also { it.color = col })
        }
        fun seg(u0: Float, v0: Float, u1: Float, v1: Float, w: Float, col: Int) = Gl.line(c, px(u0), py(v0), px(u1), py(v1), w * s, col)

        Gl.glow(c, x, y, r * 1.1f, b, 80)

        // ---- adorno por detrás de la cabeza
        when (d.crown) {
            3 -> for (i in 0 until 12) {
                val an = i * 0.5236f + t * 0.2f
                circ(cos(an) * hw * 1.05f, cy + sin(an) * hh * 1.05f, 0.23f, b)
            }
            9 -> oval(-hw * 1.3f, cy - hh * 0.9f, hw * 1.3f, cy + hh * 1.5f, b)
            1 -> for (sg in intArrayOf(-1, 1)) {
                seg(sg * hw * 0.7f, cy - hh * 0.7f, sg * (hw + 0.2f), cy - hh - 0.15f, 0.08f, light)
                seg(sg * (hw + 0.2f), cy - hh - 0.15f, sg * (hw + 0.1f), cy - hh - 0.4f, 0.06f, light)
            }
            2 -> for (sg in intArrayOf(-1, 1)) {
                seg(sg * hw * 0.5f, cy - hh * 0.8f, sg * (hw + 0.3f), cy - hh - 0.5f, 0.06f, b)
                seg(sg * (hw * 0.5f + 0.15f), cy - hh - 0.1f, sg * (hw * 0.5f + 0.05f), cy - hh - 0.45f, 0.05f, b)
                seg(sg * (hw + 0.1f), cy - hh - 0.25f, sg * (hw - 0.05f), cy - hh - 0.55f, 0.05f, b)
                seg(sg * (hw + 0.3f), cy - hh - 0.5f, sg * (hw + 0.45f), cy - hh - 0.62f, 0.05f, b)
            }
            5 -> poly(light, -0.1f, cy - hh * 0.8f, 0.1f, cy - hh * 0.8f, 0f, cy - hh - 0.42f)
            6 -> for (i in 0 until 9) {
                val an = (-170f + i * 20f) * 0.01745f
                seg(cos(an) * hw * 0.9f, cy + sin(an) * hh * 0.9f, cos(an) * (hw + 0.28f), cy + sin(an) * (hh + 0.28f), 0.07f, shade)
            }
            7 -> poly(shade, -0.14f, cy - hh * 0.85f, 0.22f, cy - hh * 0.85f, 0.02f, cy - hh - 0.38f)
            4 -> for (i in -1..1) {
                seg(i * 0.12f, cy - hh * 0.9f, i * 0.2f, cy - hh - 0.3f, 0.05f, b)
                circ(i * 0.2f, cy - hh - 0.32f, 0.06f, b)
            }
            8 -> for (i in 0 until 5) {
                val bx = (-0.4f + 0.2f * i) * hw * 1.6f
                for (j in 0 until 4) circ(bx + sin(t * 3f + i + j) * 0.06f, cy + hh * 0.7f + j * 0.15f, 0.1f - j * 0.01f, a)
            }
            10 -> for (i in 0 until 5) {
                val an = t * 0.3f + i * 1.2566f - 1.5708f
                val l = an - 0.45f
                val rr = an + 0.45f
                poly(a, cos(an) * 0.92f, sin(an) * 0.92f, cos(l) * 0.3f, sin(l) * 0.3f, cos(rr) * 0.3f, sin(rr) * 0.3f)
            }
            11 -> for (sg in intArrayOf(-1, 1)) for (k in 0 until 4) {
                val v0 = cy + (k - 1.5f) * 0.16f
                seg(sg * hw * 0.7f, v0, sg * (hw + 0.3f), v0 + (k - 1.5f) * 0.14f, 0.05f, dark)
                seg(sg * (hw + 0.3f), v0 + (k - 1.5f) * 0.14f, sg * (hw + 0.5f), v0 + (k - 1.5f) * 0.14f + 0.3f, 0.05f, dark)
            }
            12 -> for (sg in intArrayOf(-1, 1)) {
                seg(sg * hw * 0.8f, cy, sg * (hw + 0.2f), cy - 0.25f, 0.08f, shade)
                circ(sg * (hw + 0.22f), cy - 0.3f, 0.2f, a)
                poly(Gl.darken(a, 0.45f), sg * (hw + 0.22f), cy - 0.3f, sg * (hw + 0.42f), cy - 0.55f, sg * (hw + 0.1f), cy - 0.5f)
            }
        }
        // ---- orejas
        val sgs = intArrayOf(-1, 1)
        when (d.ears) {
            1 -> for (sg in sgs) {
                poly(a, sg * hw * 0.95f, cy - hh * 0.1f, sg * hw * 0.2f, cy - hh * 0.85f, sg * hw * 0.8f, cy - hh - esz)
                poly(b, sg * hw * 0.8f, cy - hh * 0.3f, sg * hw * 0.35f, cy - hh * 0.75f, sg * hw * 0.75f, cy - hh - esz * 0.65f)
            }
            2 -> for (sg in sgs) {
                circ(sg * hw * 0.72f, cy - hh * 0.78f, esz * 0.55f, a)
                circ(sg * hw * 0.72f, cy - hh * 0.78f, esz * 0.3f, b)
            }
            3 -> for (sg in sgs) {
                oval(sg * hw * 0.5f - 0.1f, cy - hh - esz, sg * hw * 0.5f + 0.1f, cy - hh * 0.6f, a)
                oval(sg * hw * 0.5f - 0.05f, cy - hh - esz * 0.8f, sg * hw * 0.5f + 0.05f, cy - hh * 0.7f, b)
            }
            4 -> for (sg in sgs) oval(sg * hw * 0.95f - 0.13f, cy - hh * 0.5f, sg * hw * 0.95f + 0.13f, cy - hh * 0.5f + esz + 0.2f, shade)
            5 -> for (sg in sgs) {
                circ(sg * hw * 0.85f, cy - hh * 0.7f, esz * 0.5f, a)
                circ(sg * hw * 0.85f, cy - hh * 0.7f, esz * 0.28f, b)
            }
            6 -> for (sg in sgs) {
                oval(minOf(sg * hw * 0.6f, sg * (hw + esz)), cy - 0.38f, maxOf(sg * hw * 0.6f, sg * (hw + esz)), cy + 0.38f, a)
                oval(minOf(sg * hw * 0.7f, sg * (hw + esz * 0.8f)), cy - 0.26f, maxOf(sg * hw * 0.7f, sg * (hw + esz * 0.8f)), cy + 0.26f, b)
            }
            7 -> for (sg in sgs) {
                seg(sg * hw * 0.3f, cy - hh * 0.9f, sg * hw * 0.75f, cy - hh - esz, 0.04f, dark)
                circ(sg * hw * 0.75f, cy - hh - esz, 0.06f, dark)
            }
            8 -> for (sg in sgs) {
                poly(a, sg * hw * 0.9f, cy - hh * 0.3f, sg * hw * 0.25f, cy - hh * 0.8f, sg * hw * 1.0f, cy - hh - esz)
                poly(b, sg * hw * 0.8f, cy - hh * 0.4f, sg * hw * 0.35f, cy - hh * 0.75f, sg * hw * 0.92f, cy - hh - esz * 0.7f)
            }
        }
        // ---- cabeza
        oval(-hw, cy - hh, hw, cy + hh, a)
        // ---- marcas
        when (d.marks) {
            1 -> for (sg in sgs) for (i in 0 until 3) seg(sg * (hw * 0.95f), cy - hh * 0.5f + i * 0.17f, sg * (hw * 0.55f), cy - hh * 0.6f + i * 0.2f, 0.05f, dark)
            2 -> for (i in 0 until 7) {
                val an = h(i, 1) * 6.2832f
                val rr = 0.75f * h(i, 2)
                circ(cos(an) * hw * rr, cy + sin(an) * hh * rr, 0.05f + 0.04f * h(i, 3), dark)
            }
            3 -> for (sg in sgs) oval(sg * hw * 0.42f - 0.17f, cy - hh * 0.12f - 0.13f, sg * hw * 0.42f + 0.17f, cy - hh * 0.12f + 0.13f, dark)
            4 -> oval(-hw * 0.8f, cy + hh * 0.0f, hw * 0.8f, cy + hh * 0.98f, Gl.white(225))
            5 -> oval(-0.08f, cy - hh * 0.95f, 0.08f, cy + hh * 0.1f, Gl.white(225))
            6 -> for (sg in sgs) circ(sg * hw * 0.62f, cy + hh * 0.28f, 0.1f, Color.argb(160, 255, 120, 150))
            7 -> for (sg in sgs) oval(sg * 0.22f - 0.07f, cy - hh * 0.95f, sg * 0.22f + 0.07f, cy + hh * 0.95f, Gl.white(235))
        }
        // ---- ojos
        val ey = cy - hh * 0.12f
        val ex = hw * 0.42f
        if (d.ears == 9) {
            for (sg in sgs) {
                circ(sg * hw * 0.55f, cy - hh * 0.8f, esz, a)
                circ(sg * hw * 0.55f, cy - hh * 0.8f, esz * 0.72f, Gl.white(245))
                circ(sg * hw * 0.55f, cy - hh * 0.8f, esz * 0.36f * blink + 0.01f, dark)
            }
        } else for (sg in sgs) when (d.eyes) {
            0 -> circ(sg * ex, ey, 0.07f * blink + 0.01f, dark)
            1 -> { circ(sg * ex, ey, 0.14f, Gl.white(245)); circ(sg * ex, ey, 0.075f * blink + 0.01f, dark) }
            2 -> { oval(sg * ex - 0.1f, ey - 0.08f, sg * ex + 0.1f, ey + 0.08f, 0xFFFFE066.toInt()); oval(sg * ex - 0.025f, ey - 0.08f, sg * ex + 0.025f, ey + 0.08f, dark) }
            else -> seg(sg * ex - 0.09f, ey, sg * ex + 0.09f, ey + 0.02f, 0.05f, dark)
        }
        // ---- hocico
        when (d.snout) {
            1 -> { oval(-0.22f, cy + hh * 0.18f, 0.22f, cy + hh * 0.18f + 0.3f, Gl.white(230)); circ(0f, cy + hh * 0.18f + 0.06f, 0.06f, dark) }
            2 -> { oval(-0.17f, cy + hh * 0.0f, 0.17f, cy + hh * 0.95f, Gl.white(230)); circ(0f, cy + hh * 0.78f, 0.07f, dark) }
            3 -> poly(b, -0.14f, cy + hh * 0.12f, 0.14f, cy + hh * 0.12f, 0f, cy + hh * 0.12f + 0.36f)
            4 -> { c.drawRoundRect(px(-0.14f), py(cy + hh * 0.2f), px(0.14f), py(cy + hh * 0.2f + 0.58f), 0.14f * s, 0.14f * s, Gl.p().also { it.color = shade }) }
            5 -> { oval(-0.22f, cy + hh * 0.15f, 0.22f, cy + hh * 0.15f + 0.28f, b); circ(-0.08f, cy + hh * 0.15f + 0.14f, 0.035f, dark); circ(0.08f, cy + hh * 0.15f + 0.14f, 0.035f, dark) }
            6 -> oval(-0.32f, cy + hh * 0.15f, 0.32f, cy + hh * 0.15f + 0.22f, b)
            7 -> {
                val tl = 0.18f + 0.08f * sin(t * 6f)
                seg(0f, cy + hh * 0.7f, 0f, cy + hh * 0.7f + tl, 0.04f, 0xFFFF3B5C.toInt())
                seg(0f, cy + hh * 0.7f + tl, -0.06f, cy + hh * 0.7f + tl + 0.08f, 0.03f, 0xFFFF3B5C.toInt())
                seg(0f, cy + hh * 0.7f + tl, 0.06f, cy + hh * 0.7f + tl + 0.08f, 0.03f, 0xFFFF3B5C.toInt())
            }
        }
    }
}
