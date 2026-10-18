package com.nioro.glassswitch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Glass Switch: una pelota de cristal que sube atravesando obstaculos de colores.
 * Solo puedes pasar por el color de tu pelota. Todo dibujado a mano con Canvas,
 * con estetica "liquid glass" (paneles translucidos, brillos, bordes de luz).
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    private enum class State { MENU, PLAYING, DEAD }

    private class Obstacle(
        val type: Int, // 0 anillo, 1 doble anillo, 2 barras
        val y: Float,
        val halfH: Float,
        val shift: Int,
        val speed: Float,
        val phase: Float
    ) {
        var starTaken = false
    }

    private class Switcher(val y: Float) {
        var taken = false
    }

    private class Shard(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        val size: Float,
        var life: Float,
        val maxLife: Float
    )

    private class Bubble(
        val nx: Float,
        val ny: Float,
        val size: Float,
        val depth: Float,
        val sway: Float
    )

    private val prefs = context.getSharedPreferences("glassswitch", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)
    private var newRecord = false

    private var state = State.MENU
    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var topInset = 0f
    private var time = 0f
    private var lastNanos = 0L
    private var running = false

    private val palette = intArrayOf(
        0xFF2BE4FF.toInt(), // cian
        0xFFFF4FB8.toInt(), // magenta
        0xFFFFD84D.toInt(), // amarillo
        0xFF9A6BFF.toInt() // violeta
    )

    // Pelota y camara
    private var bx = 0f
    private var by = 0f
    private var bvy = 0f
    private var colorIdx = 0
    private var camY = 0f
    private var score = 0
    private var deadTime = 0f

    private val obstacles = ArrayList<Obstacle>()
    private val switchers = ArrayList<Switcher>()
    private val shards = ArrayList<Shard>()
    private val trail = ArrayList<FloatArray>()
    private val bubbles = ArrayList<Bubble>()
    private var topEdge = 0f
    private var spawned = 0
    private val rnd = Random(System.nanoTime())

    // Herramientas de dibujo reutilizables
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rect2 = RectF()
    private val path = Path()
    private val clip = Path()
    private val pif = Math.PI.toFloat()
    private var bgShader: Shader? = null

    private val thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    fun resume() {
        if (!running) {
            running = true
            lastNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastNanos == 0L) 0.016f
        else min(0.033f, (frameTimeNanos - lastNanos) / 1_000_000_000f)
        lastNanos = frameTimeNanos
        update(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        w = nw.toFloat()
        h = nh.toFloat()
        u = w / 1080f
        bgShader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF050816.toInt(), 0xFF0B1230.toInt(), 0xFF170F3C.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val r = Random(7)
        bubbles.clear()
        for (i in 0 until 16) {
            bubbles.add(
                Bubble(
                    r.nextFloat(),
                    r.nextFloat(),
                    26f + r.nextFloat() * 90f,
                    0.04f + r.nextFloat() * 0.22f,
                    r.nextFloat() * 6.28f
                )
            )
        }
        reset()
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var top = insets.systemWindowInsetTop.toFloat()
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            if (cut != null) top = max(top, cut.safeInsetTop.toFloat())
        }
        topInset = top
        return super.onApplyWindowInsets(insets)
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN ||
            e.actionMasked == MotionEvent.ACTION_POINTER_DOWN
        ) {
            when (state) {
                State.MENU -> {
                    state = State.PLAYING
                    flap()
                }
                State.PLAYING -> flap()
                State.DEAD -> {
                    if (deadTime > 0.55f) {
                        reset()
                        state = State.PLAYING
                        flap()
                    }
                }
            }
        }
        return true
    }

    private fun flap() {
        bvy = -1400f * u
    }

    // ------------------------------------------------------------------
    // Logica
    // ------------------------------------------------------------------

    private fun ballR(): Float = 34f * u

    private fun reset() {
        obstacles.clear()
        switchers.clear()
        shards.clear()
        trail.clear()
        score = 0
        newRecord = false
        camY = 0f
        bx = w / 2f
        by = h * 0.78f
        bvy = 0f
        colorIdx = rnd.nextInt(4)
        topEdge = by - 820f * u
        spawned = 0
        state = State.MENU
        fill()
    }

    private fun update(dt: Float) {
        time += dt

        var i = shards.size - 1
        while (i >= 0) {
            val s = shards[i]
            s.life -= dt
            if (s.life <= 0f) {
                shards.removeAt(i)
            } else {
                s.vy += 2200f * u * dt
                s.x += s.vx * dt
                s.y += s.vy * dt
            }
            i--
        }

        var k = trail.size - 1
        while (k >= 0) {
            trail[k][2] += dt
            if (trail[k][2] > 0.35f) trail.removeAt(k)
            k--
        }

        when (state) {
            State.MENU -> {
                bx = w / 2f
                by = camY + h * 0.78f + sin(time * 3f) * 10f * u
            }
            State.PLAYING -> {
                bvy = min(bvy + 3500f * u * dt, 2600f * u)
                by += bvy * dt
                trail.add(floatArrayOf(bx, by, 0f))

                val target = by - h * 0.45f
                if (target < camY) camY += (target - camY) * min(1f, dt * 14f)

                if (by - camY > h + ballR()) {
                    die()
                } else {
                    collide()
                }
                fill()
            }
            State.DEAD -> deadTime += dt
        }
    }

    private fun fill() {
        while (topEdge > camY - h * 1.4f) spawnNext()
        obstacles.removeAll { it.y - it.halfH > camY + h + 400f * u }
        switchers.removeAll { it.y > camY + h + 400f * u }
    }

    private fun spawnNext() {
        val roll = rnd.nextInt(100)
        val type = when {
            spawned == 0 -> 0
            spawned < 3 -> if (roll < 70) 0 else 2
            else -> if (roll < 38) 0 else if (roll < 70) 1 else 2
        }
        val halfUnits = when (type) {
            0 -> 342f
            1 -> 402f
            else -> 121f
        }
        val half = halfUnits * u

        if (spawned > 0) {
            switchers.add(Switcher(topEdge - 170f * u))
            topEdge -= 340f * u
        }
        val y = topEdge - half
        val dirSign = if (rnd.nextBoolean()) 1f else -1f
        val speed = if (type == 2) {
            dirSign * min(300f + spawned * 14f, 620f) * u
        } else {
            dirSign * min(62f + spawned * 3f, 125f)
        }
        obstacles.add(Obstacle(type, y, half, rnd.nextInt(4), speed, rnd.nextFloat() * 360f))
        topEdge = y - half
        spawned++
    }

    private fun collide() {
        val r = ballR()
        val cx = w / 2f
        for (o in obstacles) {
            if (abs(by - o.y) > o.halfH + r + 20f * u) continue
            val ok = when (o.type) {
                0 -> ringSafe(o, 320f * u, o.speed, r)
                1 -> ringSafe(o, 380f * u, o.speed, r) && ringSafe(o, 260f * u, -o.speed, r)
                else -> barSafe(o, -95f * u, 1f, r) && barSafe(o, 95f * u, -1f, r)
            }
            if (!ok) {
                die()
                return
            }
            if (!o.starTaken) {
                val dx = bx - cx
                val dy = by - o.y
                val rr = r + 28f * u
                if (dx * dx + dy * dy < rr * rr) {
                    o.starTaken = true
                    score++
                    sparkle(cx, o.y, Color.WHITE, 14)
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }
        for (s in switchers) {
            if (s.taken) continue
            val dx = bx - cx
            val dy = by - s.y
            val rr = r + 34f * u
            if (dx * dx + dy * dy < rr * rr) {
                s.taken = true
                var n = rnd.nextInt(4)
                if (n == colorIdx) n = (n + 1) % 4
                colorIdx = n
                sparkle(cx, s.y, palette[n], 18)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    /** true si la pelota no choca (o pasa por su propio color). */
    private fun ringSafe(o: Obstacle, rad: Float, spd: Float, r: Float): Boolean {
        val dx = bx - w / 2f
        val dy = by - o.y
        val dist = sqrt(dx * dx + dy * dy)
        if (abs(dist - rad) > 22f * u + r * 0.55f) return true
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - (o.phase + spd * time)
        a = ((a % 360f) + 360f) % 360f
        val seg = (a / 90f).toInt() % 4
        return (seg + o.shift) % 4 == colorIdx
    }

    private fun barSafe(o: Obstacle, dyOffset: Float, dir: Float, r: Float): Boolean {
        val barY = o.y + dyOffset
        if (abs(by - barY) > 26f * u + r * 0.55f) return true
        val segW = w / 2f
        val off = o.phase * 3f * u + o.speed * dir * time
        val j = floor((bx - off) / segW).toInt()
        val seg = ((j % 4) + 4) % 4
        return (seg + o.shift) % 4 == colorIdx
    }

    private fun die() {
        if (state == State.DEAD) return
        state = State.DEAD
        deadTime = 0f
        newRecord = score > best
        if (newRecord) {
            best = score
            prefs.edit().putInt("best", best).apply()
        }
        for (i in 0 until 34) {
            val a = rnd.nextFloat() * 6.2832f
            val sp = (250f + rnd.nextFloat() * 900f) * u
            val life = 0.8f + rnd.nextFloat() * 0.7f
            val col = if (i % 2 == 0) palette[colorIdx] else palette[rnd.nextInt(4)]
            shards.add(
                Shard(
                    bx, by, cos(a) * sp, sin(a) * sp - 300f * u,
                    col, (8f + rnd.nextFloat() * 16f) * u, life, life
                )
            )
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun sparkle(x: Float, y: Float, color: Int, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f
            val sp = (160f + rnd.nextFloat() * 520f) * u
            val life = 0.45f + rnd.nextFloat() * 0.4f
            shards.add(
                Shard(
                    x, y, cos(a) * sp, sin(a) * sp,
                    color, (5f + rnd.nextFloat() * 9f) * u, life, life
                )
            )
        }
    }

    // ------------------------------------------------------------------
    // Helpers de dibujo
    // ------------------------------------------------------------------

    private fun p(): Paint {
        paint.reset()
        paint.isAntiAlias = true
        return paint
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun white(a: Int): Int = withAlpha(Color.WHITE, a)

    private fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16) and 0xFF) * f
        val g = ((c shr 8) and 0xFF) * f
        val b = (c and 0xFF) * f
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    private fun glow(c: Canvas, x: Float, y: Float, rad: Float, color: Int, alpha: Int) {
        val pp = p()
        pp.shader = RadialGradient(
            x, y, rad,
            withAlpha(color, alpha), withAlpha(color, 0),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, rad, pp)
    }

    private fun arc(
        c: Canvas, cx: Float, cy: Float, rad: Float,
        start: Float, sweep: Float, width: Float, color: Int
    ) {
        rect.set(cx - rad, cy - rad, cx + rad, cy + rad)
        val pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = width
        pp.color = color
        c.drawArc(rect, start, sweep, false, pp)
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, tf: Typeface,
        color: Int, spacing: Float = 0f, shader: Shader? = null, glowColor: Int = 0
    ) {
        val pp = p()
        pp.textAlign = Paint.Align.CENTER
        pp.textSize = size
        pp.typeface = tf
        pp.letterSpacing = spacing
        pp.color = color
        if (shader != null) pp.shader = shader
        if (glowColor != 0) pp.setShadowLayer(size * 0.25f, 0f, 0f, glowColor)
        c.drawText(s, x, y, pp)
    }

    private fun textWidth(s: String, size: Float, tf: Typeface, spacing: Float): Float {
        val pp = p()
        pp.textSize = size
        pp.typeface = tf
        pp.letterSpacing = spacing
        return pp.measureText(s)
    }

    /** Panel de cristal liquido: sombra, relleno translucido, brillos y borde de luz. */
    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, cr: Float, k: Float) {
        rect.set(l, t, r, b)

        var pp = p()
        pp.color = Color.argb((70 * k).toInt(), 0, 0, 0)
        rect2.set(l - 2f * u, t + 6f * u, r + 2f * u, b + 16f * u)
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
            l + (r - l) * 0.25f, t, (r - l) * 0.8f,
            white((90 * k).toInt()), white(0), Shader.TileMode.CLAMP
        )
        c.drawRect(rect, pp)
        pp = p()
        pp.shader = RadialGradient(
            r, b, (r - l) * 0.7f,
            withAlpha(palette[0], (60 * k).toInt()), withAlpha(palette[0], 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(rect, pp)
        c.restore()

        pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.4f * u
        pp.shader = LinearGradient(
            l, t, r, b,
            intArrayOf(white(200), white(30), white(120)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, cr, cr, pp)
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (w == 0f) return
        drawBackground(canvas)
        drawBubbles(canvas)

        canvas.save()
        canvas.translate(0f, -camY)
        for (o in obstacles) {
            if (o.y + o.halfH - camY < -60f * u || o.y - o.halfH - camY > h + 60f * u) continue
            drawObstacle(canvas, o)
        }
        for (s in switchers) {
            if (!s.taken) drawSwitcher(canvas, s)
        }
        drawTrail(canvas)
        if (state != State.DEAD) drawBall(canvas)
        drawShards(canvas)
        canvas.restore()

        when (state) {
            State.MENU -> drawMenu(canvas)
            State.PLAYING -> drawHud(canvas)
            State.DEAD -> {
                drawHud(canvas)
                drawGameOver(canvas)
            }
        }
    }

    private fun drawBackground(c: Canvas) {
        val pp = p()
        pp.shader = bgShader
        c.drawRect(0f, 0f, w, h, pp)
        for (i in 0 until 4) {
            val rad = (520f + i * 60f) * u
            val span = h + 2f * rad
            val depth = 0.05f + i * 0.03f
            val x = w * (0.15f + 0.25f * i) + sin(time * 0.25f + i * 1.7f) * 90f * u
            val y = (((0.2f + 0.27f * i) * h - camY * depth) % span + span) % span - rad
            glow(c, x, y, rad, palette[i], 70)
        }
    }

    private fun drawBubbles(c: Canvas) {
        for (b in bubbles) {
            val s = b.size * u
            val span = h + 2f * s
            val x = b.nx * w + sin(time * 0.6f + b.sway) * 14f * u
            val y = ((b.ny * h - camY * b.depth - time * 8f * u * (b.depth * 4f)) % span + span) % span - s

            var pp = p()
            pp.shader = RadialGradient(
                x - s * 0.25f, y - s * 0.3f, s * 1.1f,
                intArrayOf(white(6), white(10), white(52)),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(x, y, s, pp)

            pp = p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 1.6f * u
            pp.color = white(48)
            c.drawCircle(x, y, s, pp)

            arc(c, x, y, s * 0.72f, 205f, 50f, 2.4f * u, white(90))
        }
    }

    private fun drawObstacle(c: Canvas, o: Obstacle) {
        val cx = w / 2f
        when (o.type) {
            0 -> drawRing(c, cx, o.y, 320f * u, o.phase + o.speed * time, o.shift)
            1 -> {
                drawRing(c, cx, o.y, 380f * u, o.phase + o.speed * time, o.shift)
                drawRing(c, cx, o.y, 260f * u, o.phase - o.speed * time, o.shift)
            }
            else -> {
                drawBar(c, o, -95f * u, 1f)
                drawBar(c, o, 95f * u, -1f)
            }
        }
        if (!o.starTaken) drawStar(c, cx, o.y)
    }

    private fun drawRing(c: Canvas, cx: Float, cy: Float, rad: Float, rot: Float, shift: Int) {
        val t = 44f * u
        for (i in 0 until 4) {
            val col = palette[(i + shift) % 4]
            val st = rot + i * 90f + 2f
            val sw = 86f
            arc(c, cx, cy, rad, st, sw, t * 2.0f, withAlpha(col, 34)) // halo
            arc(c, cx, cy, rad, st, sw, t, withAlpha(col, 165)) // cuerpo de cristal
            arc(c, cx, cy, rad - t * 0.22f, st, sw, t * 0.4f, withAlpha(darken(col, 0.6f), 110))
            arc(c, cx, cy, rad + t * 0.5f, st, sw, 2.2f * u, white(125)) // borde exterior
            arc(c, cx, cy, rad - t * 0.5f, st, sw, 2.2f * u, white(70)) // borde interior
            arc(c, cx, cy, rad + t * 0.2f, st + 10f, sw - 38f, t * 0.16f, white(150)) // reflejo
        }
    }

    private fun drawBar(c: Canvas, o: Obstacle, dy: Float, dir: Float) {
        val y = o.y + dy
        val segW = w / 2f
        val off = o.phase * 3f * u + o.speed * dir * time
        val jStart = floor((0f - off) / segW).toInt() - 1
        val jEnd = floor((w - off) / segW).toInt() + 1
        val half = 26f * u
        for (j in jStart..jEnd) {
            val x0 = off + j * segW + 3f * u
            val x1 = off + (j + 1) * segW - 3f * u
            val col = palette[(((j % 4) + 4) % 4 + o.shift) % 4]

            var pp = p()
            pp.color = withAlpha(col, 34)
            rect2.set(x0 - 10f * u, y - half - 10f * u, x1 + 10f * u, y + half + 10f * u)
            c.drawRoundRect(rect2, half + 10f * u, half + 10f * u, pp)

            rect.set(x0, y - half, x1, y + half)
            pp = p()
            pp.color = withAlpha(col, 165)
            c.drawRoundRect(rect, half, half, pp)

            pp = p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 2.2f * u
            pp.color = white(110)
            c.drawRoundRect(rect, half, half, pp)

            pp = p()
            pp.color = white(130)
            rect2.set(x0 + half * 0.7f, y - half * 0.72f, x1 - half * 0.7f, y - half * 0.42f)
            c.drawRoundRect(rect2, half * 0.15f, half * 0.15f, pp)
        }
    }

    private fun drawStar(c: Canvas, x: Float, y: Float) {
        val outer = 26f * u
        val inner = 11f * u
        val rot = time * 0.8f
        glow(c, x, y, 70f * u, Color.WHITE, 55)
        path.reset()
        for (k in 0 until 10) {
            val ang = -pif / 2f + k * pif / 5f + rot
            val rr = if (k % 2 == 0) outer else inner
            val px = x + cos(ang) * rr
            val py = y + sin(ang) * rr
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        var pp = p()
        pp.shader = LinearGradient(
            x - outer, y - outer, x + outer, y + outer,
            white(245), withAlpha(palette[0], 200), Shader.TileMode.CLAMP
        )
        c.drawPath(path, pp)
        pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2f * u
        pp.strokeJoin = Paint.Join.ROUND
        pp.color = white(200)
        c.drawPath(path, pp)
    }

    private fun drawSwitcher(c: Canvas, s: Switcher) {
        val x = w / 2f
        val r = 34f * u
        glow(c, x, s.y, r * 2.6f, Color.WHITE, 50)
        rect.set(x - r, s.y - r, x + r, s.y + r)
        val rot = time * 90f
        for (i in 0 until 4) {
            val pp = p()
            pp.color = withAlpha(palette[i], 235)
            c.drawArc(rect, rot + i * 90f, 90f, true, pp)
        }
        var pp = p()
        pp.shader = RadialGradient(
            x - r * 0.3f, s.y - r * 0.35f, r * 1.2f,
            white(130), white(0), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, s.y, r, pp)
        pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.6f * u
        pp.color = white(170)
        c.drawCircle(x, s.y, r, pp)
    }

    private fun drawTrail(c: Canvas) {
        val col = palette[colorIdx]
        val r = ballR()
        for (t in trail) {
            val frac = 1f - t[2] / 0.35f
            if (frac <= 0f) continue
            val pp = p()
            pp.color = withAlpha(col, (frac * 70f).toInt())
            c.drawCircle(t[0], t[1], r * (0.3f + 0.55f * frac), pp)
        }
    }

    private fun drawBall(c: Canvas) {
        val r = ballR()
        val col = palette[colorIdx]
        glow(c, bx, by, r * 2.8f, col, 110)

        var pp = p()
        pp.shader = RadialGradient(
            bx - r * 0.35f, by - r * 0.4f, r * 1.5f,
            intArrayOf(white(235), withAlpha(col, 235), withAlpha(darken(col, 0.5f), 245)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(bx, by, r, pp)

        pp = p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.4f * u
        pp.color = white(160)
        c.drawCircle(bx, by, r, pp)

        arc(c, bx, by, r * 0.72f, 40f, 100f, r * 0.16f, white(70)) // causticas inferiores

        pp = p()
        pp.color = white(215)
        rect.set(bx - r * 0.64f, by - r * 0.74f, bx - r * 0.06f, by - r * 0.3f)
        c.drawOval(rect, pp)
    }

    private fun drawShards(c: Canvas) {
        for (s in shards) {
            val a = (s.life / s.maxLife).coerceIn(0f, 1f)
            var pp = p()
            pp.color = withAlpha(s.color, (a * 230f).toInt())
            c.drawCircle(s.x, s.y, s.size * (0.5f + 0.5f * a), pp)
            pp = p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 1.5f * u
            pp.color = white((a * 160f).toInt())
            c.drawCircle(s.x, s.y, s.size * (0.5f + 0.5f * a), pp)
        }
    }

    // ------------------------------------------------------------------
    // Interfaz (todo en cristal)
    // ------------------------------------------------------------------

    private fun drawHud(c: Canvas) {
        val top = topInset + 70f * u
        val label = score.toString()
        val size = 84f * u
        val pw = max(230f * u, textWidth(label, size, medium, 0f) + 130f * u)
        val ph = 150f * u
        panel(c, w / 2f - pw / 2f, top, w / 2f + pw / 2f, top + ph, ph / 2f, 1f)
        text(c, label, w / 2f, top + ph / 2f + size * 0.34f, size, medium, white(245), 0f, null, palette[0])
    }

    private fun drawMenu(c: Canvas) {
        val top = topInset + 90f * u
        panel(c, 110f * u, top, w - 110f * u, top + 430f * u, 70f * u, 1f)
        val shader = LinearGradient(
            w * 0.2f, 0f, w * 0.8f, 0f,
            intArrayOf(palette[0], palette[3], palette[1]), null, Shader.TileMode.CLAMP
        )
        text(c, "GLASS", w / 2f, top + 190f * u, 150f * u, light, Color.WHITE, 0.12f, shader)
        text(c, "SWITCH", w / 2f, top + 350f * u, 150f * u, thin, white(240), 0.12f)

        val pt = top + 470f * u
        panel(c, w / 2f - 200f * u, pt, w / 2f + 200f * u, pt + 120f * u, 60f * u, 0.8f)
        text(c, "Récord  $best", w / 2f, pt + 60f * u + 18f * u, 52f * u, medium, white(230))

        val yy = h * 0.905f
        val pulse = 0.55f + 0.45f * sin(time * 3f)
        panel(c, w / 2f - 330f * u, yy - 70f * u, w / 2f + 330f * u, yy + 70f * u, 70f * u, 1f)
        text(c, "Toca para jugar", w / 2f, yy + 19f * u, 56f * u, medium, white((pulse * 255f).toInt()))
    }

    private fun drawGameOver(c: Canvas) {
        val a = min(1f, deadTime / 0.35f)
        var pp = p()
        pp.color = Color.argb((120 * a).toInt(), 2, 4, 14)
        c.drawRect(0f, 0f, w, h, pp)

        val cardT = h * 0.5f - 420f * u + (1f - a) * 60f * u
        val l = 120f * u
        val r = w - 120f * u
        panel(c, l, cardT, r, cardT + 840f * u, 70f * u, a)

        text(c, "FIN DEL JUEGO", w / 2f, cardT + 130f * u, 54f * u, medium, white((220 * a).toInt()), 0.25f)

        val shader = LinearGradient(
            w * 0.3f, 0f, w * 0.7f, 0f,
            intArrayOf(palette[0], palette[3], palette[1]), null, Shader.TileMode.CLAMP
        )
        text(c, score.toString(), w / 2f, cardT + 400f * u, 260f * u, thin, white((255 * a).toInt()), 0f, shader)

        if (newRecord) {
            text(c, "¡NUEVO RÉCORD!", w / 2f, cardT + 505f * u, 54f * u, medium, withAlpha(palette[2], (255 * a).toInt()), 0.12f)
        } else {
            text(c, "Récord  $best", w / 2f, cardT + 505f * u, 52f * u, medium, white((200 * a).toInt()))
        }

        val bt = cardT + 600f * u
        panel(c, w / 2f - 300f * u, bt, w / 2f + 300f * u, bt + 130f * u, 65f * u, a)
        text(c, "Jugar de nuevo", w / 2f, bt + 65f * u + 19f * u, 54f * u, medium, white((240 * a).toInt()))
    }
}
