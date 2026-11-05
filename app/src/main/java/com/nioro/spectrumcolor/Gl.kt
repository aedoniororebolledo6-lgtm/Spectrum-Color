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
    private val legacyClip = Path()

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

    // Resplandores: se dibujan decenas por cuadro. En vez de crear un degradado nuevo cada vez (memoria nueva y
    // trabajo del recolector en cada cuadro), se guarda uno por color+transparencia, de radio fijo GLOW_R y centrado
    // en 0,0, y se lleva a su lugar y tamaño moviendo y escalando el lienzo. El resultado es idéntico.
    private const val GLOW_R = 256f
    private val glowCache = HashMap<Int, RadialGradient>()

    /** Solo pruebas (atajo `legado:1`): dibuja como antes, sin reutilizar degradados, para comparar la fluidez. */
    @Volatile var legacy = false

    fun glow(c: Canvas, x: Float, y: Float, rad: Float, color: Int, a: Int) {
        if (rad <= 0.5f || a <= 0) return
        if (legacy) {
            val lp = p()
            lp.shader = RadialGradient(x, y, rad, alpha(color, a), alpha(color, 0), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, rad, lp)
            return
        }
        val key = alpha(color, a)
        var sh = glowCache[key]
        if (sh == null) {
            if (glowCache.size >= 2048) glowCache.clear()
            sh = RadialGradient(0f, 0f, GLOW_R, key, alpha(color, 0), Shader.TileMode.CLAMP)
            glowCache[key] = sh
        }
        val pp = p()
        pp.shader = sh
        val s = rad / GLOW_R
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.drawCircle(0f, 0f, GLOW_R, pp)
        c.restore()
    }

    // Esferas con brillo (bola, burbujas, orbes): mismo truco que los resplandores. El degradado se crea una vez por
    // tipo+color en un círculo de radio ORB_U centrado en 0,0 (centro del brillo en ox,oy y radio rk, relativos a r).
    private const val ORB_U = 100f
    private val orbCache = HashMap<Long, Shader>()

    fun shadedCircle(
        c: Canvas, x: Float, y: Float, r: Float, kind: Int, col: Int,
        ox: Float, oy: Float, rk: Float, colors: IntArray, stops: FloatArray?
    ) {
        if (r <= 0.2f) return
        if (legacy) {
            val lp = p()
            lp.shader = RadialGradient(x + ox * r, y + oy * r, rk * r, colors, stops, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, lp)
            return
        }
        val key = (kind.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)
        var sh = orbCache[key]
        if (sh == null) {
            if (orbCache.size >= 1024) orbCache.clear()
            sh = RadialGradient(ox * ORB_U, oy * ORB_U, rk * ORB_U, colors, stops, Shader.TileMode.CLAMP)
            orbCache[key] = sh
        }
        val pp = p()
        pp.shader = sh
        val s = r / ORB_U
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.drawCircle(0f, 0f, ORB_U, pp)
        c.restore()
    }

    // Degradados de los paneles de cristal: la mayoría está quieta en pantalla, así que se reutilizan mientras
    // posición, tamaño, intensidad y color sean los mismos (tabla directa, sin crear objetos al reutilizar).
    private class PanelSh(
        val l: Float, val t: Float, val r: Float, val b: Float, val k: Float, val tint: Int,
        val fill: Shader, val hi: Shader, val tintSh: Shader, val rim: Shader
    )
    private val panelCache = arrayOfNulls<PanelSh>(512)

    private fun panelShaders(l: Float, t: Float, r: Float, b: Float, k: Float, tint: Int): PanelSh {
        var h = l.toBits()
        h = h * 31 + t.toBits(); h = h * 31 + r.toBits(); h = h * 31 + b.toBits()
        h = h * 31 + k.toBits(); h = h * 31 + tint
        val i = (h xor (h ushr 16)) and (panelCache.size - 1)
        val e = panelCache[i]
        if (e != null && e.l == l && e.t == t && e.r == r && e.b == b && e.k == k && e.tint == tint) return e
        val n = PanelSh(
            l, t, r, b, k, tint,
            LinearGradient(l, t, r, b, intArrayOf(white((70 * k).toInt()), white((22 * k).toInt())), null, Shader.TileMode.CLAMP),
            RadialGradient(l + (r - l) * 0.25f, t, max1((r - l) * 0.8f), white((90 * k).toInt()), white(0), Shader.TileMode.CLAMP),
            RadialGradient(r, b, max1((r - l) * 0.7f), alpha(tint, (60 * k).toInt()), alpha(tint, 0), Shader.TileMode.CLAMP),
            LinearGradient(
                l, t, r, b,
                intArrayOf(white((200 * k).toInt()), white((30 * k).toInt()), white((120 * k).toInt())),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        )
        panelCache[i] = n
        return n
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

        if (legacy) {
            pp = p()
            pp.shader = LinearGradient(l, t, r, b, intArrayOf(white((70 * k).toInt()), white((22 * k).toInt())), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, cr, cr, pp)
            c.save()
            legacyClip.reset()
            legacyClip.addRoundRect(rect, cr, cr, Path.Direction.CW)
            c.clipPath(legacyClip)
            pp = p()
            pp.shader = RadialGradient(l + (r - l) * 0.25f, t, max1((r - l) * 0.8f), white((90 * k).toInt()), white(0), Shader.TileMode.CLAMP)
            c.drawRect(rect, pp)
            pp = p()
            pp.shader = RadialGradient(r, b, max1((r - l) * 0.7f), alpha(tint, (60 * k).toInt()), alpha(tint, 0), Shader.TileMode.CLAMP)
            c.drawRect(rect, pp)
            c.restore()
            pp = p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 2.4f
            pp.shader = LinearGradient(l, t, r, b, intArrayOf(white((200 * k).toInt()), white((30 * k).toInt()), white((120 * k).toInt())), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, cr, cr, pp)
            return
        }
        val sh = panelShaders(l, t, r, b, k, tint)
        pp = p()
        pp.shader = sh.fill
        c.drawRoundRect(rect, cr, cr, pp)

        // brillos: antes se recortaba el lienzo con la forma redondeada (muy lento en la tarjeta gráfica);
        // dibujar la misma forma redondeada con el degradado da exactamente lo mismo sin recorte
        pp = p()
        pp.shader = sh.hi
        c.drawRoundRect(rect, cr, cr, pp)
        pp = p()
        pp.shader = sh.tintSh
        c.drawRoundRect(rect, cr, cr, pp)

        pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.4f
        pp.shader = sh.rim
        c.drawRoundRect(rect, cr, cr, pp)
    }

    private fun max1(v: Float): Float = if (v < 1f) 1f else v
}
