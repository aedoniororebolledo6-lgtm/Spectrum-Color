package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Dibujos del tema Halloween: telas de araña, calabazas, arañas colgando y murciélagos. */
object Spooky {
    private val path = Path()

    /** Tela de araña. Círculo completo (sweep = 360) o un abanico desde un rincón. */
    fun web(c: Canvas, x: Float, y: Float, r: Float, a: Int, from: Float = 0f, sweep: Float = 360f, rings: Int = 3) {
        val full = sweep >= 359f
        val n = if (full) 8 else max(2, Math.round(sweep / 30f))
        val cnt = if (full) n else n + 1
        val sw = max(1.2f, r * 0.018f)
        for (i in 0 until cnt) {
            val ang = Math.toRadians((from + sweep * i / n).toDouble())
            Gl.line(c, x, y, x + cos(ang).toFloat() * r, y + sin(ang).toFloat() * r, sw, Gl.white(a))
        }
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = sw
        pp.color = Gl.white((a * 0.9f).toInt())
        for (k in 1..rings) {
            val rad = r * k / rings
            path.reset()
            for (i in 0..n) {
                val ang = Math.toRadians((from + sweep * i / n).toDouble())
                val px = x + cos(ang).toFloat() * rad
                val py = y + sin(ang).toFloat() * rad
                if (i == 0) path.moveTo(px, py)
                else {
                    val mid = Math.toRadians((from + sweep * (i - 0.5f) / n).toDouble())
                    val sag = rad * (if (full) 0.9f else 0.88f)
                    path.quadTo(x + cos(mid).toFloat() * sag, y + sin(mid).toFloat() * sag, px, py)
                }
                if (full && i == n - 1) {
                    // cierra el círculo hacia el primer radio
                    val mid = Math.toRadians((from + sweep * (i + 0.5f) / n).toDouble())
                    val sag = rad * 0.9f
                    val a0 = Math.toRadians(from.toDouble())
                    path.quadTo(x + cos(mid).toFloat() * sag, y + sin(mid).toFloat() * sag, x + cos(a0).toFloat() * rad, y + sin(a0).toFloat() * rad)
                    break
                }
            }
            c.drawPath(path, pp)
        }
    }

    /** Calabaza de Halloween; con `face` lleva cara iluminada. */
    fun pumpkin(c: Canvas, x: Float, y: Float, r: Float, face: Boolean, t: Float) {
        val dark = 0xFFB84A00.toInt()
        val parts = arrayOf(floatArrayOf(-0.46f, 0.56f, 0.9f), floatArrayOf(0.46f, 0.56f, 0.9f), floatArrayOf(0f, 0.6f, 0.98f))
        for ((idx, p) in parts.withIndex()) {
            val cx = x + p[0] * r
            val pp = Gl.p()
            pp.shader = RadialGradient(
                cx - p[1] * r * 0.3f, y - r * 0.35f, r * 1.2f,
                intArrayOf(0xFFFFB347.toInt(), 0xFFFF7A1A.toInt(), 0xFFC4510A.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
            Gl.rect.set(cx - p[1] * r, y - p[2] * r, cx + p[1] * r, y + p[2] * r)
            c.drawOval(Gl.rect, pp)
            val po = Gl.p()
            po.style = Paint.Style.STROKE
            po.strokeWidth = max(1.2f, r * 0.05f)
            po.color = dark
            c.drawOval(Gl.rect, po)
            if (idx == 2) continue
        }
        // tallo
        val st = Gl.p()
        st.color = 0xFF3F7F22.toInt()
        Gl.rect.set(x - r * 0.1f, y - r * 1.22f, x + r * 0.12f, y - r * 0.8f)
        c.drawRoundRect(Gl.rect, r * 0.06f, r * 0.06f, st)
        if (face && r >= 34f) {
            val fl = 0.75f + 0.25f * sin(t * 5f + x * 0.01f)
            Gl.glow(c, x, y + r * 0.05f, r * 0.9f, 0xFFFFC83D.toInt(), (110 * fl).toInt())
            val fp = Gl.p()
            fp.color = 0xFFFFE680.toInt()
            for (sgn in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(x + sgn * r * 0.36f, y - r * 0.42f)
                path.lineTo(x + sgn * r * 0.14f, y - r * 0.08f)
                path.lineTo(x + sgn * r * 0.5f, y - r * 0.08f)
                path.close()
                c.drawPath(path, fp)
            }
            path.reset()
            path.moveTo(x - r * 0.5f, y + r * 0.2f)
            var up = false
            for (i in 1..6) {
                path.lineTo(x - r * 0.5f + r * i / 6f, y + r * (if (up) 0.2f else 0.42f))
                up = !up
            }
            path.lineTo(x + r * 0.5f, y + r * 0.2f)
            path.close()
            c.drawPath(path, fp)
        }
    }

    /** Araña colgando de un hilo desde (x, y0). */
    fun spider(c: Canvas, x: Float, y0: Float, y: Float, r: Float, t: Float) {
        Gl.line(c, x, y0, x, y - r * 0.4f, max(1.2f, r * 0.05f), Gl.white(120))
        val body = 0xFF160A20.toInt()
        for (sgn in intArrayOf(-1, 1)) {
            for (i in 0 until 4) {
                val wig = sin(t * 4f + i * 1.3f + sgn) * r * 0.12f
                val y1 = y - r * 0.1f + i * r * 0.22f - r * 0.2f
                val kx = x + sgn * r * (0.9f + 0.12f * i)
                val ky = y1 - r * 0.55f + wig
                val fx = x + sgn * r * (1.5f - 0.05f * i)
                val fy = y1 + r * 0.45f + wig
                Gl.line(c, x + sgn * r * 0.25f, y1 * 0.5f + y * 0.5f, kx, ky, max(1.4f, r * 0.08f), body)
                Gl.line(c, kx, ky, fx, fy, max(1.4f, r * 0.08f), body)
            }
        }
        val pp = Gl.p()
        pp.color = body
        Gl.rect.set(x - r * 0.55f, y - r * 0.2f, x + r * 0.55f, y + r * 0.85f)
        c.drawOval(Gl.rect, pp)
        Gl.rect.set(x - r * 0.38f, y - r * 0.7f, x + r * 0.38f, y - r * 0.05f)
        c.drawOval(Gl.rect, pp)
        val e = Gl.p()
        e.color = 0xFFFF3B3B.toInt()
        c.drawCircle(x - r * 0.15f, y - r * 0.4f, r * 0.09f, e)
        c.drawCircle(x + r * 0.15f, y - r * 0.4f, r * 0.09f, e)
    }

    /** Murciélago volando (s = tamaño). */
    fun bat(c: Canvas, x: Float, y: Float, s: Float, t: Float) {
        val f = sin(t * 9f)
        val pp = Gl.p()
        pp.color = 0xFF0C0614.toInt()
        for (sgn in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(x + sgn * s * 0.12f, y)
            path.quadTo(x + sgn * s * 0.6f, y - s * 0.9f * (0.4f + 0.6f * f), x + sgn * s * 1.25f, y - s * 0.55f * f)
            path.quadTo(x + sgn * s * 1.0f, y + s * 0.05f, x + sgn * s * 0.85f, y + s * 0.38f)
            path.quadTo(x + sgn * s * 0.6f, y + s * 0.1f, x + sgn * s * 0.45f, y + s * 0.42f)
            path.quadTo(x + sgn * s * 0.28f, y + s * 0.15f, x + sgn * s * 0.12f, y + s * 0.34f)
            path.close()
            c.drawPath(path, pp)
            path.reset()
            path.moveTo(x + sgn * s * 0.04f, y - s * 0.22f)
            path.lineTo(x + sgn * s * 0.14f, y - s * 0.5f)
            path.lineTo(x + sgn * s * 0.22f, y - s * 0.18f)
            path.close()
            c.drawPath(path, pp)
        }
        Gl.rect.set(x - s * 0.17f, y - s * 0.26f, x + s * 0.17f, y + s * 0.38f)
        c.drawOval(Gl.rect, pp)
        val e = Gl.p()
        e.color = 0xFFFFD84D.toInt()
        c.drawCircle(x - s * 0.07f, y - s * 0.1f, s * 0.035f, e)
        c.drawCircle(x + s * 0.07f, y - s * 0.1f, s * 0.035f, e)
    }
}
