package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Herramientas de dibujo "liquid glass" compartidas (un solo hilo: el de la interfaz). */
object Gl {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF()
    val rect2 = RectF()
    val path = Path()
    private val clip = Path()

    val thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Pincel limpio (cada llamada lo reinicia: no guardes la referencia entre dibujos). */
    fun p(): Paint {
        paint.reset()
        paint.isAntiAlias = true
        return paint
    }

    fun alpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    fun white(a: Int): Int = alpha(Color.WHITE, a)

    fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16) and 0xFF) * f
        val g = ((c shr 8) and 0xFF) * f
        val b = (c and 0xFF) * f
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    fun glow(c: Canvas, x: Float, y: Float, rad: Float, color: Int, a: Int) {
        val pp = p()
        pp.shader = RadialGradient(x, y, rad, alpha(color, a), alpha(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, pp)
    }

    fun arc(c: Canvas, cx: Float, cy: Float, rad: Float, start: Float, sweep: Float, width: Float, color: Int) {
        rect.set(cx - rad, cy - rad, cx + rad, cy + rad)
        val pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = width
        pp.color = color
        c.drawArc(rect, start, sweep, false, pp)
    }

    fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = width
        pp.strokeCap = Paint.Cap.ROUND
        pp.color = color
        c.drawLine(x0, y0, x1, y1, pp)
    }

    fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, tf: Typeface, color: Int,
        spacing: Float = 0f, shader: Shader? = null, glowColor: Int = 0,
        align: Paint.Align = Paint.Align.CENTER
    ) {
        val str = I18n.t(s)
        val pp = p()
        pp.textAlign = align
        pp.textSize = size
        pp.typeface = tf
        pp.letterSpacing = spacing
        pp.color = color
        if (shader != null) pp.shader = shader
        if (glowColor != 0) pp.setShadowLayer(size * 0.25f, 0f, 0f, glowColor)
        c.drawText(str, x, y, pp)
    }

    fun textWidth(s: String, size: Float, tf: Typeface, spacing: Float = 0f): Float {
        val pp = p()
        pp.textSize = size
        pp.typeface = tf
        pp.letterSpacing = spacing
        return pp.measureText(I18n.t(s))
    }

    /** Texto que se achica solo si no cabe en maxW. */
    fun textFit(
        c: Canvas, s: String, x: Float, y: Float, size: Float, maxW: Float, tf: Typeface, color: Int,
        spacing: Float = 0f, align: Paint.Align = Paint.Align.CENTER
    ) {
        var sz = size
        val w = textWidth(s, sz, tf, spacing)
        if (w > maxW && w > 0f) sz = size * maxW / w
        text(c, s, x, y, sz, tf, color, spacing, null, 0, align)
    }

    /** Panel de cristal líquido: sombra, relleno translúcido, brillos y borde de luz. */
    fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, cr: Float, k: Float, tint: Int) {
        rect.set(l, t, r, b)

        var pp = p()
        pp.color = Color.argb((70 * k).toInt(), 0, 0, 0)
        rect2.set(l - 2f, t + 6f, r + 2f, b + 16f)
        c.drawRoundRect(rect2, cr, cr, pp)

        pp = p()
        pp.shader = LinearGradient(
            l, t, r, b,
            intArrayOf(white((70 * k).toInt()), white((22 * k).toInt())),
            null, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, cr, cr, pp)

        c.save()
        clip.reset()
        clip.addRoundRect(rect, cr, cr, Path.Direction.CW)
        c.clipPath(clip)
        pp = p()
        pp.shader = RadialGradient(
            l + (r - l) * 0.25f, t, max1((r - l) * 0.8f),
            white((90 * k).toInt()), white(0), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, pp)
        pp = p()
        pp.shader = RadialGradient(
            r, b, max1((r - l) * 0.7f),
            alpha(tint, (60 * k).toInt()), alpha(tint, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, pp)
        c.restore()

        pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.4f
        pp.shader = LinearGradient(
            l, t, r, b,
            intArrayOf(white((200 * k).toInt()), white((30 * k).toInt()), white((120 * k).toInt())),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, cr, cr, pp)
    }

    private fun max1(v: Float): Float = if (v < 1f) 1f else v
}
