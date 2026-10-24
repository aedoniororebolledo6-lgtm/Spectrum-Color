package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/** Dibujos del tema Perritos: caras de husky, pug, quiltro, poodle, golden y dálmata. */
object Dogs {
    const val KINDS = 6

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, rot: Float = 0f) {
        val p = Gl.p()
        p.color = color
        if (rot != 0f) {
            c.save()
            c.rotate(rot, cx, cy)
        }
        Gl.rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(Gl.rect, p)
        if (rot != 0f) c.restore()
    }

    private fun lighten(col: Int, k: Int): Int {
        val r = (col shr 16) and 255
        val g = (col shr 8) and 255
        val b = col and 255
        return (0xFF shl 24) or (minOf(255, r + k) shl 16) or (minOf(255, g + k) shl 8) or minOf(255, b + k)
    }

    private fun shade(col: Int): Int {
        val r = ((col shr 16) and 255) * 78 / 100
        val g = ((col shr 8) and 255) * 78 / 100
        val b = (col and 255) * 78 / 100
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Perrito. kind: 0 husky, 1 pug, 2 quiltro, 3 poodle, 4 golden, 5 dálmata.
     * lookX/lookY (-1..1): hacia dónde miran los ojos. t: tiempo (parpadeo y lengua).
     */
    fun dog(c: Canvas, x: Float, y: Float, r: Float, kind: Int, t: Float, lookX: Float, lookY: Float) {
        val base: Int
        val ear: Int
        val eye: Int
        when (kind) {
            0 -> { base = 0xFFB4BBC4.toInt(); ear = 0xFF59606A.toInt(); eye = 0xFF6EC8FF.toInt() }
            1 -> { base = 0xFFE5C48F.toInt(); ear = 0xFF2E2622.toInt(); eye = 0xFF2A1A14.toInt() }
            2 -> { base = 0xFF9A6A3C.toInt(); ear = 0xFF5C3A1E.toInt(); eye = 0xFF3A2414.toInt() }
            3 -> { base = 0xFFF2E6D2.toInt(); ear = 0xFFEBDCC2.toInt(); eye = 0xFF2A1A14.toInt() }
            4 -> { base = 0xFFE6B45C.toInt(); ear = 0xFFC48A2E.toInt(); eye = 0xFF3A2414.toInt() }
            else -> { base = 0xFFF7F6F2.toInt(); ear = 0xFF22222A.toInt(); eye = 0xFF2A1A14.toInt() }
        }
        // ---- orejas (detrás de la cabeza)
        for (sgn in intArrayOf(-1, 1)) {
            when (kind) {
                0 -> {
                    Cats.tri(c, x + sgn * r * 0.95f, y - r * 0.15f, x + sgn * r * 0.3f, y - r * 0.95f, x + sgn * r * 0.82f, y - r * 1.25f, ear)
                    Cats.tri(c, x + sgn * r * 0.8f, y - r * 0.35f, x + sgn * r * 0.42f, y - r * 0.85f, x + sgn * r * 0.74f, y - r * 1.04f, 0xFFF0B8C0.toInt())
                }
                3 -> for (k in 0..2) oval(c, x + sgn * r * (1.02f + 0.04f * k), y - r * 0.2f + k * r * 0.3f, r * 0.34f, r * 0.34f, ear)
                1 -> oval(c, x + sgn * r * 0.78f, y - r * 0.62f, r * 0.3f, r * 0.4f, ear, sgn * 28f)
                else -> oval(c, x + sgn * r * 1.0f, y + r * 0.05f, r * 0.34f, r * 0.72f, ear, sgn * 10f)
            }
        }
        // ---- cabeza
        val hp = Gl.p()
        hp.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.4f, r * 1.4f,
            intArrayOf(lighten(base, 40), base, shade(base)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        Gl.rect.set(x - r * 1.02f, y - r * 0.92f, x + r * 1.02f, y + r * 0.95f)
        c.drawOval(Gl.rect, hp)
        // poodle: copete esponjoso
        if (kind == 3) {
            for (k in -1..1) oval(c, x + k * r * 0.34f, y - r * (0.92f + (if (k == 0) 0.16f else 0.04f)), r * 0.34f, r * 0.3f, base)
        }
        // ---- marcas
        when (kind) {
            0 -> { // gorro oscuro y máscara blanca
                oval(c, x, y - r * 0.62f, r * 0.72f, r * 0.34f, Gl.alpha(ear, 230))
                oval(c, x, y + r * 0.22f, r * 0.7f, r * 0.62f, 0xFFF4F6F8.toInt())
                oval(c, x, y - r * 0.38f, r * 0.12f, r * 0.34f, 0xFFF4F6F8.toInt())
            }
            1 -> { // hocico negro y arrugas
                oval(c, x, y + r * 0.34f, r * 0.55f, r * 0.42f, 0xFF2E2622.toInt())
                Gl.line(c, x - r * 0.3f, y - r * 0.66f, x + r * 0.3f, y - r * 0.66f, max(1.5f, r * 0.05f), Gl.alpha(ear, 170))
                Gl.line(c, x - r * 0.22f, y - r * 0.52f, x + r * 0.22f, y - r * 0.52f, max(1.5f, r * 0.05f), Gl.alpha(ear, 140))
            }
            2 -> { // mancha blanca en el hocico y la frente
                oval(c, x, y - r * 0.3f, r * 0.17f, r * 0.62f, 0xFFF1E2C6.toInt())
                oval(c, x, y + r * 0.4f, r * 0.5f, r * 0.38f, 0xFFF1E2C6.toInt())
            }
            4 -> oval(c, x, y + r * 0.4f, r * 0.5f, r * 0.38f, lighten(base, 38))
            5 -> { // manchas
                oval(c, x - r * 0.62f, y - r * 0.5f, r * 0.17f, r * 0.15f, 0xFF22222A.toInt())
                oval(c, x + r * 0.55f, y - r * 0.2f, r * 0.2f, r * 0.17f, 0xFF22222A.toInt())
                oval(c, x + r * 0.2f, y - r * 0.72f, r * 0.12f, r * 0.1f, 0xFF22222A.toInt())
                oval(c, x - r * 0.72f, y + r * 0.15f, r * 0.12f, r * 0.11f, 0xFF22222A.toInt())
                oval(c, x, y + r * 0.4f, r * 0.5f, r * 0.38f, 0xFFFFFFFF.toInt())
            }
            else -> oval(c, x, y + r * 0.4f, r * 0.5f, r * 0.38f, lighten(base, 14))
        }
        // ---- ojos
        val ph = (t + r * 0.41f) % 4.6f
        val blink = if (ph < 0.14f) 0.12f else 1f
        for (sgn in intArrayOf(-1, 1)) {
            val ex = x + sgn * r * 0.4f
            val ey = y - r * 0.12f
            val ew = r * (if (kind == 0) 0.2f else 0.17f)
            val eh = ew * 1.1f * blink
            if (kind == 0) oval(c, ex, ey, ew, eh, eye) else oval(c, ex, ey, ew, eh, 0xFF1A1418.toInt())
            if (blink > 0.5f) {
                val px = ex + lookX * ew * 0.35f
                val py = ey + lookY * eh * 0.35f
                if (kind == 0) oval(c, px, py, ew * 0.45f, eh * 0.7f, 0xFF14141C.toInt())
                oval(c, ex - ew * 0.3f + lookX * ew * 0.15f, ey - eh * 0.35f, ew * 0.28f, ew * 0.28f, Gl.white(235))
            }
        }
        // ---- nariz, boca y lengua
        oval(c, x, y + r * 0.27f, r * 0.19f, r * 0.13f, 0xFF16111A.toInt())
        oval(c, x - r * 0.05f, y + r * 0.23f, r * 0.06f, r * 0.03f, Gl.white(170))
        val mw = max(1.4f, r * 0.045f)
        val mc = 0xFF2A1A14.toInt().let { if (kind == 1) 0xFFB8AFA8.toInt() else it }
        Gl.line(c, x, y + r * 0.38f, x, y + r * 0.5f, mw, mc)
        Gl.line(c, x, y + r * 0.5f, x - r * 0.2f, y + r * 0.55f, mw, mc)
        Gl.line(c, x, y + r * 0.5f, x + r * 0.2f, y + r * 0.55f, mw, mc)
        val tl = r * (0.2f + 0.06f * sin(t * 5f + r))
        oval(c, x, y + r * 0.58f + tl * 0.5f, r * 0.13f, tl, 0xFFFF7F9A.toInt())
        Gl.line(c, x, y + r * 0.58f, x, y + r * 0.58f + tl * 1.4f, max(1f, r * 0.025f), Gl.alpha(0xFFD9566F.toInt(), 200))
    }
}
