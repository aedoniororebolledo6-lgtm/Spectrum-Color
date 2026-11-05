package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dibujo de los obstáculos de catálogo (Kit.kt) con el mismo estilo de vidrio líquido que el resto:
 * halo suave del color, cuerpo translúcido, filo interior más oscuro y brillos blancos.
 */
object KitDraw {
    private val ORB_STOPS = floatArrayOf(0f, 0.45f, 1f)
    private const val DEG = 3.14159265f / 180f
    private val va = FloatArray(2)
    private val vb = FloatArray(2)

    fun draw(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val d = Kit.design(o)
        for (p in d.parts) {
            val stretch = p.ey != 1f && p.kind != KPart.BAR
            val cx = Kit.cx(o, p, t)
            val cy = Kit.cy(o, p)
            when (p.kind) {
                KPart.ARC -> {
                    if (stretch) { c.save(); c.scale(1f, p.ey, cx, cy) }
                    arcPart(c, o, p, t, cx, cy, pal)
                    if (stretch) c.restore()
                }
                KPart.POLY, KPart.STAR -> polyPart(c, o, p, t, pal)
                KPart.SPOKE -> spokePart(c, o, p, t, cx, cy, pal)
                KPart.DOTS -> dotsPart(c, o, p, t, cx, cy, pal)
                KPart.BAR -> barPart(c, o, p, t, cy, pal)
            }
        }
    }

    private fun col(o: Ob, p: KPart, i: Int, pal: IntArray): Int = pal[(i + o.shift + p.coff) % o.n]

    private fun arcPart(c: Canvas, o: Ob, p: KPart, t: Float, cx: Float, cy: Float, pal: IntArray) {
        val k = Kit.segs(p, o.n)
        val rad = Kit.rad(o, p, t)
        val rot = Kit.ang(o, p, t)
        val span = (360f - p.gap) / k
        val th = Cfg.THICK
        // guía tenue del hueco
        if (p.gap > 0f) Gl.arc(c, cx, cy, rad, rot + 2f, p.gap - 4f, 2f, Gl.white(30))
        for (i in 0 until k) {
            val cc = col(o, p, i, pal)
            val st = rot + p.gap + i * span + 1.5f
            val sw = span - 3f
            Gl.arc(c, cx, cy, rad, st, sw, th * 1.9f, Gl.alpha(cc, 34))
            Gl.arc(c, cx, cy, rad, st, sw, th, Gl.alpha(cc, 170))
            Gl.arc(c, cx, cy, rad - th * 0.22f, st, sw, th * 0.4f, Gl.alpha(Gl.darken(cc, 0.6f), 110))
            Gl.arc(c, cx, cy, rad + th * 0.5f, st, sw, 2.2f, Gl.white(125))
            Gl.arc(c, cx, cy, rad - th * 0.5f, st, sw, 2.2f, Gl.white(70))
            Gl.arc(c, cx, cy, rad + th * 0.2f, st + 5f, max(4f, sw - 22f), th * 0.16f, Gl.white(150))
        }
    }

    private fun glassSeg(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, cc: Int, ox: Float, oy: Float) {
        val th = Cfg.THICK
        Gl.line(c, x0, y0, x1, y1, th * 1.9f, Gl.alpha(cc, 34))
        Gl.line(c, x0, y0, x1, y1, th, Gl.alpha(cc, 170))
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len > 1f) {
            val ux = dx / len
            val uy = dy / len
            var nx = uy
            var ny = -ux
            // el brillo va del lado de afuera (lejos del centro de la pieza)
            if (((x0 + x1) / 2f - ox) * nx + ((y0 + y1) / 2f - oy) * ny < 0f) { nx = -nx; ny = -ny }
            val inset = th * 0.45f
            val off = th * 0.2f
            Gl.line(c, x0 + ux * inset + nx * off, y0 + uy * inset + ny * off, x1 - ux * inset + nx * off, y1 - uy * inset + ny * off, th * 0.14f, Gl.white(150))
            Gl.line(c, x0 + ux * inset - nx * off * 1.4f, y0 + uy * inset - ny * off * 1.4f, x1 - ux * inset - nx * off * 1.4f, y1 - uy * inset - ny * off * 1.4f, th * 0.18f, Gl.alpha(Gl.darken(cc, 0.6f), 90))
        }
    }

    private fun polyPart(c: Canvas, o: Ob, p: KPart, t: Float, pal: IntArray) {
        val star = p.kind == KPart.STAR
        val k = Kit.segs(p, o.n) * (if (star) 2 else 1)
        val ox = Kit.cx(o, p, t)
        val oy = Kit.cy(o, p)
        for (i in 0 until k) {
            Kit.vert(o, p, t, i, va)
            Kit.vert(o, p, t, (i + 1) % k, vb)
            glassSeg(c, va[0], va[1], vb[0], vb[1], col(o, p, if (star) i / 2 else i, pal), ox, oy)
        }
        // juntas brillantes en los vértices
        for (i in 0 until k) {
            Kit.vert(o, p, t, i, va)
            val pp = Gl.p(); pp.color = Gl.white(70)
            c.drawCircle(va[0], va[1], Cfg.THICK * 0.22f, pp)
        }
    }

    private fun spokePart(c: Canvas, o: Ob, p: KPart, t: Float, cx: Float, cy: Float, pal: IntArray) {
        val k = Kit.segs(p, o.n)
        val r = Kit.rad(o, p, t)
        val r0 = p.r2 * Kit.scale(o)
        for (i in 0 until k) {
            val a = (Kit.ang(o, p, t) + i * 360f / k) * DEG
            val ca = cos(a)
            val sa = sin(a) * p.ey
            glassSeg(c, cx + ca * r0, cy + sa * r0, cx + ca * r, cy + sa * r, col(o, p, i, pal), cx + ca * r * 2f, cy + sa * r * 2f)
        }
        // eje central de vidrio
        if (r0 > 20f) {
            val pp = Gl.p(); pp.style = Paint.Style.STROKE; pp.strokeWidth = 2f; pp.color = Gl.white(40)
            c.drawCircle(cx, cy, r0 - Cfg.THICK * 0.6f, pp)
        }
    }

    private fun dotsPart(c: Canvas, o: Ob, p: KPart, t: Float, cx: Float, cy: Float, pal: IntArray) {
        val k = Kit.segs(p, o.n)
        val r = Kit.rad(o, p, t)
        val pp = Gl.p(); pp.style = Paint.Style.STROKE; pp.strokeWidth = 2f; pp.color = Gl.white(26)
        if (p.ey == 1f) c.drawCircle(cx, cy, r, pp)
        for (i in 0 until k) {
            val a = (Kit.ang(o, p, t) + i * 360f / k) * DEG
            orb(c, cx + cos(a) * r, cy + sin(a) * r * p.ey, p.r2, col(o, p, i, pal))
        }
    }

    fun orb(c: Canvas, x: Float, y: Float, r: Float, cc: Int) {
        Gl.glow(c, x, y, r * 1.9f, cc, 80)
        Gl.shadedCircle(
            c, x, y, r, 3, cc, -0.3f, -0.35f, 1.4f,
            intArrayOf(Gl.white(220), Gl.alpha(cc, 225), Gl.alpha(Gl.darken(cc, 0.55f), 235)), ORB_STOPS
        )
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.2f
        pp.color = Gl.white(150)
        c.drawCircle(x, y, r, pp)
    }

    private fun barPart(c: Canvas, o: Ob, p: KPart, t: Float, y: Float, pal: IntArray) {
        val half = Cfg.BAR_H
        val len = if (p.r2 > 0f) p.r2 else Cfg.BAR_SEG
        val off = o.phase * 3f + Kit.barSpeed(o, p) * t
        val j0 = floor((0f - off) / len).toInt() - 1
        val j1 = floor((Cfg.W - off) / len).toInt() + 1
        for (j in j0..j1) {
            val x0 = off + j * len + 4f
            val x1 = off + (j + 1) * len - 4f
            capsule(c, x0, x1, y, half, pal[((((j % o.n) + o.n) % o.n) + o.shift + p.coff) % o.n])
        }
    }

    private fun capsule(c: Canvas, x0: Float, x1: Float, y: Float, half: Float, cc: Int) {
        var pp = Gl.p()
        pp.color = Gl.alpha(cc, 34)
        Gl.rect2.set(x0 - 10f, y - half - 10f, x1 + 10f, y + half + 10f)
        c.drawRoundRect(Gl.rect2, half + 10f, half + 10f, pp)
        Gl.rect.set(x0, y - half, x1, y + half)
        pp = Gl.p()
        pp.color = Gl.alpha(cc, 170)
        c.drawRoundRect(Gl.rect, half, half, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.2f
        pp.color = Gl.white(110)
        c.drawRoundRect(Gl.rect, half, half, pp)
        pp = Gl.p()
        pp.color = Gl.white(130)
        Gl.rect2.set(x0 + half * 0.7f, y - half * 0.72f, max(x0 + half * 0.7f + 2f, x1 - half * 0.7f), y - half * 0.42f)
        c.drawRoundRect(Gl.rect2, half * 0.15f, half * 0.15f, pp)
    }

    /** Miniatura de un diseño para el editor (centrada en x, y y con radio aproximado `r`). */
    fun icon(c: Canvas, design: Int, x: Float, y: Float, r: Float, pal: IntArray, t: Float) {
        val probe = Ob(T.KIT, 0f, 3, 0, 40f, 0f, 300f, 0f, design = design)
        val k = r / probe.reach
        c.save()
        c.translate(x, y)
        c.scale(k, k)
        c.translate(-Cfg.W / 2f, 0f)
        draw(c, probe, t, pal)
        c.restore()
    }
}
