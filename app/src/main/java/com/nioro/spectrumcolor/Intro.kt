package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Animación de inicio del estudio (estilo Disney+): cielo estrellado, aparece la esfera de cristal azul con la luna,
 * un arco de luz recorre el logo de izquierda a derecha dejando chispas y descubriendo "nioro", destello final con
 * sonido, "sparks of life" y fundido al juego. Dura LEN segundos; tocar la pantalla o "atrás" la salta (GameView).
 * Se dibuja en unidades de diseño (1080 de ancho, vh de alto).
 */
class Intro {
    companion object {
        const val LEN = 3.7f
        private const val SWEEP_A = 0.9f   // empieza el arco
        private const val SWEEP_B = 2.0f   // termina el arco (destello y sonido)
    }

    var t = 0f
        private set
    /** Se llama una vez, cuando el arco llega al final (para el sonido). */
    var onChime: (() -> Unit)? = null

    private val rnd = java.util.Random(11)
    // estrellas: x, y (0..1), radio, fase, velocidad
    private val stars = Array(150) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.8f + rnd.nextFloat() * 2.4f, rnd.nextFloat() * 6.28f, 0.5f + rnd.nextFloat() * 1.6f)
    }

    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val r: Float)
    private val sparks = ArrayList<Spark>()

    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val thin: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgShader: Shader? = null
    private var bgKey = -1f
    private var moonFill: Shader? = null

    // luna creciente de radio 100 centrada en 0,0 (se lleva a su lugar moviendo el lienzo)
    private val moon = Path().apply {
        val a = Path().apply { addCircle(-5f, 6f, 60f, Path.Direction.CW) }
        val b = Path().apply { addCircle(19f, -18f, 54f, Path.Direction.CW) }
        op(a, b, Path.Op.DIFFERENCE)
    }

    private var headX = 0f
    private var headY = 0f
    private var endX = 0f
    private var endY = 0f
    private var chimed = false

    fun done(): Boolean = t >= LEN

    private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
    private fun easeOut(x: Float): Float { val u = 1f - clamp01(x); return 1f - u * u * u }
    private fun easeInOut(x: Float): Float { val u = clamp01(x); return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f }
    private fun backOut(x: Float): Float { val u = clamp01(x) - 1f; val k = 1.7f; return 1f + (k + 1f) * u * u * u + k * u * u }

    fun update(dt: Float) {
        t += dt
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= dt
            if (s.life <= 0f) { it.remove(); continue }
            s.vy += 40f * dt
            s.vx *= 0.985f
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
        // chispas que suelta la cabeza del arco
        if (t in SWEEP_A..SWEEP_B && headX != 0f) {
            repeat(3) {
                val l = 0.45f + rnd.nextFloat() * 0.5f
                sparks.add(Spark(headX, headY, (rnd.nextFloat() - 0.5f) * 220f, (rnd.nextFloat() - 0.3f) * 160f, l, l, 2.5f + rnd.nextFloat() * 4f))
            }
        }
        if (!chimed && t >= SWEEP_B) {
            chimed = true
            for (k in 0 until 46) {
                val an = rnd.nextFloat() * 6.283f
                val sp = 160f + rnd.nextFloat() * 420f
                val l = 0.5f + rnd.nextFloat() * 0.6f
                sparks.add(Spark(endX, endY, cos(an) * sp, sin(an) * sp, l, l, 2.5f + rnd.nextFloat() * 5f))
            }
            onChime?.invoke()
        }
    }

    fun draw(c: Canvas, vh: Float) {
        val cx = 540f
        val cy = vh * 0.46f
        // ---- fondo: azul noche con un halo al centro ----
        if (bgShader == null || bgKey != vh) {
            bgShader = RadialGradient(cx, cy, vh * 0.72f, intArrayOf(0xFF1C2A7C.toInt(), 0xFF0C1546.toInt(), 0xFF050817.toInt()),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            bgKey = vh
        }
        var pp = p()
        pp.shader = bgShader
        c.drawRect(0f, 0f, 1080f, vh, pp)
        // ---- estrellas que titilan y suben despacio ----
        for (s in stars) {
            val y = ((s[1] * vh - t * 10f * s[4]) % vh + vh) % vh
            val a = (130f + 110f * sin(t * s[4] * 2.2f + s[3])).toInt().coerceIn(0, 255)
            pp = p()
            pp.color = Gl.alpha(0xFFD6DEFF.toInt(), a)
            c.drawCircle(s[0] * 1080f, y, s[2], pp)
        }

        // ---- medidas del logo (esfera a la izquierda, "nioro" a la derecha, centrado) ----
        val r = 112f
        val ts = 190f
        pp = p()
        pp.typeface = bold
        pp.textSize = ts
        val tw = pp.measureText("nioro")
        val gap = 44f
        val gw = 2f * r + gap + tw
        val sx = cx - gw / 2f
        val ox = sx + r
        val tx = sx + 2f * r + gap
        val ty = cy + 52f

        // ---- arco de luz ----
        val ecx = cx
        val ecy = cy + 70f
        val rx = gw * 0.62f
        val ry = 235f
        fun ax(u: Float) = ecx + rx * cos(Math.toRadians((200.0 + 140.0 * u)).toFloat())
        fun ay(u: Float) = ecy + ry * sin(Math.toRadians((200.0 + 140.0 * u)).toFloat())
        val u = easeInOut((t - SWEEP_A) / (SWEEP_B - SWEEP_A))
        headX = ax(u); headY = ay(u)
        endX = ax(1f); endY = ay(1f)
        val logoA = easeOut((t - 0.25f) / 0.45f)
        val pulse = max(0f, 1f - abs(t - 2.15f) / 0.45f)

        // ---- esfera de cristal con la luna ----
        val s = 0.72f + 0.28f * backOut((t - 0.25f) / 0.65f)
        if (logoA > 0f) {
            if (logoA < 1f) c.saveLayerAlpha(ox - r * 3f, cy - r * 3f, ox + r * 3f, cy + r * 3f, (255 * logoA).toInt())
            Gl.glow(c, ox, cy, r * 2.5f * s, 0xFF3F5FFF.toInt(), (70 + 110 * pulse).toInt())
            Gl.shadedCircle(c, ox, cy, r * s, 7, 0xFF2F45D0.toInt(), -0.38f, -0.48f, 1.6f,
                intArrayOf(0xFF7A95FF.toInt(), 0xFF2B3FC4.toInt(), 0xFF0A1352.toInt()), floatArrayOf(0f, 0.45f, 1f))
            // brillo de vidrio arriba
            pp = p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = r * s * 0.11f
            pp.strokeCap = Paint.Cap.ROUND
            pp.color = Gl.white(80)
            Gl.rect.set(ox - r * s * 0.8f, cy - r * s * 0.8f, ox + r * s * 0.8f, cy + r * s * 0.8f)
            c.drawArc(Gl.rect, 212f, 70f, false, pp)
            // luna y su puntito
            c.save()
            c.translate(ox, cy)
            val k = r * s / 100f
            c.scale(k, k)
            c.rotate(-8f + 8f * easeOut((t - 0.3f) / 0.8f))
            if (moonFill == null) moonFill = LinearGradient(-60f, -60f, 60f, 60f, 0xFFF4F6FF.toInt(), 0xFFAEBBFF.toInt(), Shader.TileMode.CLAMP)
            pp = p()
            pp.shader = moonFill
            c.drawPath(moon, pp)
            pp = p()
            pp.color = 0xFFE9EDFF.toInt()
            c.drawCircle(43f, 31f, 7f, pp)
            c.restore()
            // borde de luz
            pp = p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 3.5f
            pp.color = Gl.alpha(0xFFB9C3FF.toInt(), 170)
            c.drawCircle(ox, cy, r * s, pp)
            if (logoA < 1f) c.restore()
        }

        // ---- "nioro": se descubre a medida que pasa el arco ----
        val revealX = if (t >= SWEEP_B) 2000f else if (t < SWEEP_A) -1f else headX + 30f
        if (revealX > tx) {
            c.save()
            c.clipRect(0f, 0f, revealX, vh)
            pp = p()
            pp.typeface = bold
            pp.textSize = ts
            pp.color = 0xFFEEF1FF.toInt()
            pp.setShadowLayer(18f + 26f * pulse, 0f, 0f, Gl.alpha(0xFF6F8CFF.toInt(), (150 + 100 * pulse).toInt()))
            c.drawText("nioro", tx, ty, pp)
            c.restore()
        }
        // "sparks of life"
        val sa = easeOut((t - 2.1f) / 0.5f)
        if (sa > 0f) {
            pp = p()
            pp.typeface = thin
            pp.textSize = 58f
            pp.letterSpacing = 0.04f
            pp.color = Gl.alpha(0xFFA3B4FF.toInt(), (255 * sa).toInt())
            c.drawText("sparks of life", tx + 6f, cy + 150f + 14f * (1f - sa), pp)
        }

        // ---- la estela del arco ----
        val fade = 1f - easeOut((t - SWEEP_B) / 0.7f)
        if (t >= SWEEP_A && fade > 0f) {
            val u1 = u
            val u0 = if (t < SWEEP_B) max(0f, u - 0.5f) else min(1f, u1 - 0.5f + 0.5f * easeOut((t - SWEEP_B) / 0.7f))
            val n = 48
            var px = ax(u0)
            var py = ay(u0)
            for (i in 1..n) {
                val f = i / n.toFloat()
                val uu = u0 + (u1 - u0) * f
                val qx = ax(uu)
                val qy = ay(uu)
                val a = f.pow(1.6f) * fade
                // halo azul ancho y núcleo blanco
                pp = p()
                pp.style = Paint.Style.STROKE
                pp.strokeCap = Paint.Cap.ROUND
                pp.strokeWidth = 6f + 30f * f
                pp.color = Gl.alpha(0xFF5C7CFF.toInt(), (60 * a).toInt())
                c.drawLine(px, py, qx, qy, pp)
                pp.strokeWidth = 1.5f + 7f * f
                pp.color = Gl.alpha(0xFFF4F7FF.toInt(), (235 * a).toInt())
                c.drawLine(px, py, qx, qy, pp)
                px = qx; py = qy
            }
            if (t < SWEEP_B) {
                Gl.glow(c, headX, headY, 150f, 0xFF6F8DFF.toInt(), 120)
                Gl.glow(c, headX, headY, 60f, Color.WHITE, 230)
            }
        }
        // destello al final del arco
        if (t >= SWEEP_B && t < SWEEP_B + 0.8f) {
            val k = (t - SWEEP_B) / 0.8f
            Gl.glow(c, endX, endY, 90f + 260f * k, 0xFF8FA6FF.toInt(), (220 * (1f - k)).toInt())
            Gl.glow(c, endX, endY, 40f + 60f * k, Color.WHITE, (255 * (1f - k)).toInt())
        }
        // chispas
        for (sp in sparks) {
            val a = sp.life / sp.max
            Gl.glow(c, sp.x, sp.y, sp.r * 4f, 0xFF7F9BFF.toInt(), (120 * a).toInt())
            pp = p()
            pp.color = Gl.white((240 * a).toInt())
            c.drawCircle(sp.x, sp.y, sp.r * (0.4f + 0.6f * a), pp)
        }

        // ---- entrada desde negro y salida a negro ----
        val black = max(1f - easeOut(t / 0.5f), easeOut((t - (LEN - 0.5f)) / 0.5f))
        if (black > 0.003f) {
            pp = p()
            pp.color = Gl.alpha(Color.BLACK, (255 * black).toInt())
            c.drawRect(0f, 0f, 1080f, vh, pp)
        }
    }

    private fun p(): Paint {
        paint.reset()
        paint.isAntiAlias = true
        return paint
    }
}
