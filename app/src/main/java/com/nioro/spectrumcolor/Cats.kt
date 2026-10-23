package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/** Dibujos del tema Gatitos: caras de gatito (siamés, naranjo, negro y gris rayado), orejas y bigotes. */
object Cats {
    private val path = Path()

    fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        val p = Gl.p()
        p.color = color
        c.drawPath(path, p)
    }

    /** Bigotes: tres líneas curvas hacia el lado `dir` (-1 = izquierda, 1 = derecha) desde (x, y). */
    fun whiskers(c: Canvas, x: Float, y: Float, len: Float, dir: Int, w: Float, a: Int) {
        for (k in -1..1) {
            Gl.line(c, x, y + k * len * 0.05f, x + dir * len, y + k * len * 0.2f - len * 0.04f, w, Gl.white(a))
        }
    }

    /**
     * Gatito. kind: 0 siamés, 1 naranjo, 2 negro, 3 gris rayado.
     * lookX/lookY (-1..1): hacia dónde miran los ojos. t: tiempo (parpadeo).
     */
    fun kitten(c: Canvas, x: Float, y: Float, r: Float, kind: Int, t: Float, lookX: Float, lookY: Float) {
        val base: Int
        val dark: Int
        val eye: Int
        val inner = 0xFFFFA8B8.toInt()
        when (kind) {
            0 -> { base = 0xFFF6E7CC.toInt(); dark = 0xFF5B3B2C.toInt(); eye = 0xFF63B8FF.toInt() }
            1 -> { base = 0xFFFF9B3D.toInt(); dark = 0xFFD2670F.toInt(); eye = 0xFF7CD16B.toInt() }
            2 -> { base = 0xFF22222B.toInt(); dark = 0xFF0E0E14.toInt(); eye = 0xFFFFD84D.toInt() }
            else -> { base = 0xFF9CA3AB.toInt(); dark = 0xFF5A616B.toInt(); eye = 0xFFFFC24D.toInt() }
        }
        val earColor = if (kind == 0) dark else base
        // orejas
        for (sgn in intArrayOf(-1, 1)) {
            tri(c, x + sgn * r * 0.92f, y - r * 0.25f, x + sgn * r * 0.2f, y - r * 0.95f, x + sgn * r * 0.78f, y - r * 1.28f, earColor)
            tri(c, x + sgn * r * 0.78f, y - r * 0.42f, x + sgn * r * 0.36f, y - r * 0.86f, x + sgn * r * 0.72f, y - r * 1.06f, if (kind == 2) 0xFF5A3A48.toInt() else inner)
        }
        // cabeza
        val hp = Gl.p()
        hp.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.4f, r * 1.4f,
            intArrayOf(lighten(base), base, shade(base)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        Gl.rect.set(x - r * 1.05f, y - r * 0.92f, x + r * 1.05f, y + r * 0.92f)
        c.drawOval(Gl.rect, hp)
        // marcas
        when (kind) {
            0 -> {
                val mp = Gl.p()
                mp.shader = RadialGradient(x, y + r * 0.32f, r * 0.7f, Gl.alpha(dark, 235), Gl.alpha(dark, 0), Shader.TileMode.CLAMP)
                c.drawCircle(x, y + r * 0.32f, r * 0.7f, mp)
            }
            else -> {
                val sw = max(1.5f, r * 0.07f)
                val sc = Gl.alpha(dark, if (kind == 2) 120 else 210)
                for (k in -1..1) Gl.line(c, x + k * r * 0.26f, y - r * 0.9f, x + k * r * 0.22f, y - r * 0.5f, sw, sc)
                for (sgn in intArrayOf(-1, 1)) {
                    Gl.line(c, x + sgn * r * 1.03f, y - r * 0.05f, x + sgn * r * 0.66f, y, sw, sc)
                    Gl.line(c, x + sgn * r * 1.0f, y + r * 0.25f, x + sgn * r * 0.66f, y + r * 0.2f, sw, sc)
                }
            }
        }
        // ojos (parpadean de vez en cuando)
        val ph = (t + r * 0.37f) % 4.2f
        val blink = if (ph < 0.14f) 0.12f else 1f
        for (sgn in intArrayOf(-1, 1)) {
            val ex = x + sgn * r * 0.43f
            val ey = y - r * 0.12f
            val ew = r * 0.27f
            val eh = r * 0.29f * blink
            Gl.rect.set(ex - ew, ey - eh, ex + ew, ey + eh)
            val ep = Gl.p()
            ep.color = eye
            c.drawOval(Gl.rect, ep)
            if (blink > 0.5f) {
                val px = ex + lookX * ew * 0.35f
                val py = ey + lookY * eh * 0.35f
                Gl.rect.set(px - ew * 0.2f, py - eh * 0.85f, px + ew * 0.2f, py + eh * 0.85f)
                val pp = Gl.p()
                pp.color = 0xFF14141C.toInt()
                c.drawOval(Gl.rect, pp)
                val hl = Gl.p()
                hl.color = Gl.white(230)
                c.drawCircle(ex - ew * 0.3f, ey - eh * 0.4f, ew * 0.16f, hl)
            }
        }
        // nariz y boca
        tri(c, x - r * 0.12f, y + r * 0.2f, x + r * 0.12f, y + r * 0.2f, x, y + r * 0.34f, 0xFFFF7F9A.toInt())
        val mw = max(1.4f, r * 0.045f)
        val mc = Gl.alpha(if (kind == 2) 0xFFB8B8C4.toInt() else dark, 230)
        Gl.line(c, x, y + r * 0.34f, x, y + r * 0.44f, mw, mc)
        Gl.line(c, x, y + r * 0.44f, x - r * 0.15f, y + r * 0.5f, mw, mc)
        Gl.line(c, x, y + r * 0.44f, x + r * 0.15f, y + r * 0.5f, mw, mc)
        // bigotes
        whiskers(c, x - r * 0.55f, y + r * 0.34f, r * 0.8f, -1, max(1.2f, r * 0.03f), 200)
        whiskers(c, x + r * 0.55f, y + r * 0.34f, r * 0.8f, 1, max(1.2f, r * 0.03f), 200)
    }

    private fun lighten(col: Int): Int {
        val r = ((col shr 16) and 255)
        val g = ((col shr 8) and 255)
        val b = (col and 255)
        return (0xFF shl 24) or (minOf(255, r + 45) shl 16) or (minOf(255, g + 45) shl 8) or minOf(255, b + 45)
    }

    private fun shade(col: Int): Int {
        val r = ((col shr 16) and 255) * 80 / 100
        val g = ((col shr 8) and 255) * 80 / 100
        val b = (col and 255) * 80 / 100
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Pequeño balanceo de la cola/cabeza no usado: se deja el tiempo para parpadeos. */
    fun wobble(t: Float, k: Float): Float = sin(t * 1.3f + k) * 0.06f
}
