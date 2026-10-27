package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Dibujo, controles táctiles y flujo (historia, pausa, victoria) del modo Aventura. Va en horizontal. */
class AdvUi(
    private val ball: (Canvas, Float, Float, Float, Int) -> Unit,
    private val sfx: (Int) -> Unit,
    private val onWin: (Int) -> Int,
    private val onExit: () -> Unit
) {
    private class Pt(var x: Float, var y: Float, var vx: Float, var vy: Float, val col: Int, val sz: Float, var life: Float, val max: Float, val grav: Float)
    private class Num(val x: Float, var y: Float, val s: String, var life: Float, val col: Int)
    private class Btn(val id: String, val x: Float, val y: Float, val r: Float)

    var adv: Adv? = null
    var world = 0
    private var phase = 0 // 0 historia, 1 jugando, 2 pausa, 3 victoria
    private var phT = 0f
    private val inp = AdvInput()
    private val parts = ArrayList<Pt>()
    private val nums = ArrayList<Num>()
    private val ptrs = HashMap<Int, String>()
    private var winSent = false
    private var time = 0f
    private var vw = 760f
    private var flash = 0f
    private var squash = 0f
    private var lastPx = 0f
    private var runPhase = 0f
    private var dustT = 0f
    private var bannerT = 0f
    private var banner = ""
    private var zoom = 0f
    private var lastHp = 0
    private var al = 0f
    private var swapBuf = FloatArray(512)
    private class Trail(val x: Float, val y: Float, var life: Float)
    private class Corpse(val x: Float, val y: Float, val col: Int, var t: Float)
    private val trail = ArrayList<Trail>()
    private val corpses = ArrayList<Corpse>()
    private var trailT = 0f
    private var hpDisp = 12f
    private var hpChip = 12f
    private var bossDisp = 1f
    private var bossChip = 1f
    private var heartPop = 0f
    private var lastHearts = 0
    private var bannerAge = 0f
    private var bossSquash = 0f
    private var uiT = 0f
    private val rnd = java.util.Random(5)
    private val pth = Path()
    private val rf = RectF()
    private val rf2 = RectF()

    val active: Boolean get() = adv != null

    fun start(w: Int, viewW: Float, god: Boolean) {
        world = w
        vw = viewW
        adv = Adv(w, viewW, god)
        phase = 0
        phT = 0f
        winSent = false
        parts.clear()
        nums.clear()
        ptrs.clear()
        bannerT = 0f
        squash = 0f
        zoom = 0f
        trail.clear()
        corpses.clear()
        hpDisp = AdvCfg.MAXHP.toFloat(); hpChip = hpDisp; bossDisp = 1f; bossChip = 1f; heartPop = 0f; lastHearts = 5; bossSquash = 0f
        inp.left = false; inp.right = false; inp.jumpHeld = false
    }

    fun stop() { adv = null; ptrs.clear() }

    /** Botón "atrás": pausa, o sale si ya estaba en pausa. */
    fun back(): Boolean {
        return when (phase) {
            1 -> { phase = 2; phT = 0f; false }
            else -> { stop(); onExit(); true }
        }
    }

    // ------------------------------------------------------------------ controles
    private fun buttons(): List<Btn> = listOf(
        Btn("L", 62f, 305f, 38f), Btn("R", 158f, 305f, 38f),
        Btn("A", vw - 62f, 300f, 40f), Btn("J", vw - 152f, 322f, 34f),
        Btn("S", vw - 100f, 210f, 30f), Btn("P", vw - 30f, 32f, 22f)
    )

    private fun hitBtn(x: Float, y: Float): String? {
        var best: String? = null
        var bd = 1e9f
        for (b in buttons()) {
            val d = Math.hypot((x - b.x).toDouble(), (y - b.y).toDouble()).toFloat()
            if (d <= b.r + 14f && d < bd) { bd = d; best = b.id }
        }
        return best
    }

    fun touch(e: MotionEvent, w: Int, h: Int) {
        if (adv == null || h <= 0) return
        val s = h / AdvCfg.H
        val ai = e.actionIndex
        val am = e.actionMasked
        if (am == MotionEvent.ACTION_DOWN || am == MotionEvent.ACTION_POINTER_DOWN) {
            val x = e.getX(ai) / s
            val y = e.getY(ai) / s
            when (phase) {
                0 -> if (phT > 0.4f) { phase = 1; phT = 0f; sfx(Sfx.GO) }
                3 -> if (phT > 1.2f) { stop(); onExit() }
                2 -> {
                    if (abs(x - vw / 2f) < 120f && abs(y - 165f) < 26f) phase = 1
                    else if (abs(x - vw / 2f) < 120f && abs(y - 225f) < 26f) { stop(); onExit() }
                }
                1 -> {
                    val id = hitBtn(x, y)
                    if (id != null) {
                        ptrs[e.getPointerId(ai)] = id
                        when (id) {
                            "A" -> inp.atkPressed = true
                            "J" -> inp.jumpPressed = true
                            "S" -> inp.spPressed = true
                            "P" -> { phase = 2; phT = 0f }
                        }
                    }
                }
            }
        }
        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) ptrs.clear()
        else if (am == MotionEvent.ACTION_POINTER_UP) ptrs.remove(e.getPointerId(ai))
        else if (am == MotionEvent.ACTION_MOVE && phase == 1) {
            for (i in 0 until e.pointerCount) {
                val pid = e.getPointerId(i)
                val cur = ptrs[pid]
                if (cur == "L" || cur == "R") {
                    val id = hitBtn(e.getX(i) / s, e.getY(i) / s)
                    if (id == "L" || id == "R") ptrs[pid] = id
                }
            }
        }
        inp.left = ptrs.containsValue("L")
        inp.right = ptrs.containsValue("R")
        inp.jumpHeld = ptrs.containsValue("J")
    }

    // ------------------------------------------------------------------ actualización
    fun update(dt: Float) {
        val a = adv ?: return
        time += dt
        phT += dt
        flash = max(0f, flash - dt * 2.5f)
        squash = max(0f, squash - dt * 5f)
        bannerT = max(0f, bannerT - dt)
        bannerAge += dt
        bossSquash = max(0f, bossSquash - dt * 4f)
        heartPop = max(0f, heartPop - dt * 3.5f)
        zoom = max(0f, zoom - dt * 1.2f)
        if (phase == 1 || phase == 3) {
            if (phase == 3) { inp.left = false; inp.right = false; inp.jumpHeld = false }
            a.step(dt, inp)
            handleEvents(a)
            animate(a, dt)
            if (a.won && phase == 1) { phase = 3; phT = 0f }
            if (phase == 3 && !winSent) { winSent = true; savedCount = onWin(world) }
        } else inp.clearPressed()
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += p.grav * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        val nt = nums.iterator()
        while (nt.hasNext()) {
            val n = nt.next()
            n.life -= dt
            if (n.life <= 0f) { nt.remove(); continue }
            n.y -= 30f * dt
        }
    }

    private fun animate(a: Adv, dt: Float) {
        // fase de carrera (rueda la esfera) y polvo
        val dx = a.px - lastPx
        lastPx = a.px
        runPhase += dx / 11f
        if (a.onGround && abs(a.pvx) > 90f) {
            dustT -= dt
            if (dustT <= 0f) {
                dustT = if (a.ice) 0.05f else 0.09f
                val col = if (a.ice) 0xFFCCEEFF.toInt() else Gl.white(160)
                parts.add(Pt(a.px - a.face * 8f, a.py - 2f, -a.face * 20f, -14f - rnd.nextFloat() * 10f, col, 1.6f + rnd.nextFloat() * 1.4f, 0.35f, 1f, 20f))
            }
        }
        // estela de pociones y biblia
        if (a.potionT > 0f && rnd.nextInt(3) == 0) parts.add(Pt(a.px + (rnd.nextFloat() - 0.5f) * 16f, a.py - rnd.nextFloat() * 28f, 0f, -30f, 0xFF80FFC0.toInt(), 2f, 0.5f, 1f, 0f))
        if (a.hp < lastHp) flash = max(flash, 0.8f)
        lastHp = a.hp
        // estela de imágenes residuales al correr o saltar rápido
        trailT -= dt
        if (trailT <= 0f && a.deadT <= 0f && (abs(a.pvx) > 150f || (!a.onGround && abs(a.pvy) > 260f))) {
            trailT = 0.04f
            trail.add(Trail(a.px, a.py - 11f, 0.22f))
        }
        val ti = trail.iterator()
        while (ti.hasNext()) { val t = ti.next(); t.life -= dt; if (t.life <= 0f) ti.remove() }
        val ci = corpses.iterator()
        while (ci.hasNext()) { val k = ci.next(); k.t += dt; if (k.t > 0.5f) ci.remove() }
        // barras con retraso (chip) y contador de corazones con rebote
        hpDisp += (a.hp - hpDisp) * min(1f, dt * 14f)
        if (hpChip > a.hp) hpChip += (a.hp - hpChip) * min(1f, dt * 2.4f) else hpChip = a.hp.toFloat()
        val bf = if (a.bossStarted) a.bhp / a.bmax.toFloat() else 1f
        bossDisp += (bf - bossDisp) * min(1f, dt * 14f)
        if (bossChip > bf) bossChip += (bf - bossChip) * min(1f, dt * 2.4f) else bossChip = bf
        if (a.hearts > lastHearts) heartPop = 1f
        lastHearts = a.hearts
    }

    private fun burst(x: Float, y: Float, col: Int, n: Int, sp: Float, grav: Float = 220f) {
        for (i in 0 until n) {
            if (parts.size > 320) return
            val ang = rnd.nextFloat() * 6.2832f
            val v = sp * (0.3f + rnd.nextFloat())
            parts.add(Pt(x, y, cos(ang) * v, sin(ang) * v - 30f, col, 1.5f + rnd.nextFloat() * 2.5f, 0.5f + rnd.nextFloat() * 0.5f, 1f, grav))
        }
    }

    private fun say(s: String, t: Float = 2.2f) { banner = s; bannerT = t; bannerAge = 0f }

    private fun handleEvents(a: Adv) {
        val pal = Worlds.colors[world.coerceIn(0, Worlds.colors.size - 1)]
        for (e in a.events) {
            when (e.t) {
                Adv.EV_JUMP -> { burst(e.x, e.y, Gl.white(200), 5, 45f, 60f); sfx(Sfx.TAP) }
                Adv.EV_JUMP2 -> { for (k in 0 until 10) { val an = k * 0.628f; parts.add(Pt(e.x, e.y, cos(an) * 80f, sin(an) * 30f, 0xFF80E0FF.toInt(), 2f, 0.4f, 1f, 0f)) }; sfx(Sfx.SWITCH) }
                Adv.EV_LAND -> { squash = 1f; burst(e.x, e.y - 1f, Gl.white(170), 5, 50f, 40f) }
                Adv.EV_SWING -> sfx(Sfx.SWITCH)
                Adv.EV_HIT -> { burst(e.x, e.y, pal[1], 7, 120f); sfx(Sfx.TAP); if (e.n > 0) nums.add(Num(e.x, e.y - 12f, e.n.toString(), 0.7f, Color.WHITE)) }
                Adv.EV_BLOCK -> { burst(e.x, e.y, 0xFFE0E8FF.toInt(), 8, 140f, 0f); sfx(Sfx.TAP); nums.add(Num(e.x, e.y - 8f, "¡Bloqueado!", 0.7f, 0xFFB0C0FF.toInt())) }
                Adv.EV_HURT -> { burst(e.x, e.y, 0xFFFF5060.toInt(), 14, 160f); flash = 1f; sfx(Sfx.DIE); if (e.n > 0) nums.add(Num(e.x, e.y - 14f, "-${e.n}", 0.9f, 0xFFFF6070.toInt())) }
                Adv.EV_PICK -> { burst(e.x, e.y, 0xFFFFE070.toInt(), 9, 100f); sfx(Sfx.STAR); pickText(e.n) }
                Adv.EV_CANDLE -> { burst(e.x, e.y, 0xFFFFB040.toInt(), 14, 120f); sfx(Sfx.TAP) }
                Adv.EV_SPECIAL -> sfx(Sfx.SWITCH)
                Adv.EV_BOLT -> { flash = max(flash, 0.5f); sfx(Sfx.WIN) }
                Adv.EV_BOSSHIT -> { burst(e.x, e.y, Color.WHITE, 9, 140f); sfx(Sfx.TAP); if (e.n > 0) nums.add(Num(e.x, e.y - 20f, e.n.toString(), 0.7f, 0xFFFFE070.toInt())) }
                Adv.EV_BOSSDIE -> { for (k in 0 until 5) burst(e.x, e.y, pal[k % 4], 24, 240f); flash = 1f; zoom = 1f; sfx(Sfx.WIN) }
                Adv.EV_EDIE -> { burst(e.x, e.y, pal[2], 12, 130f); corpses.add(Corpse(e.x, e.y, pal[2], 0f)) }
                Adv.EV_CHECK -> { burst(e.x, e.y - 10f, 0xFF80FFA0.toInt(), 12, 90f, 0f); sfx(Sfx.STAR); say("Punto de control", 1.4f) }
                Adv.EV_SLAM -> { sfx(Sfx.DIE); bossSquash = 1f; burst(e.x, e.y - 2f, Gl.white(170), 8, 90f, 60f) }
                Adv.EV_ROCK -> { burst(e.x, e.y - 4f, 0xFFB0A090.toInt(), 14, 120f); sfx(Sfx.DIE) }
                Adv.EV_CRUMBLE -> burst(e.x, e.y, 0xFFB0A090.toInt(), 6, 60f, 120f)
                Adv.EV_AMBUSH -> { say("¡EMBOSCADA!", 2.0f); sfx(Sfx.COUNT) }
                Adv.EV_CLEAR -> { say("¡Despejado!", 2.0f); sfx(Sfx.WIN) }
                Adv.EV_POTION -> burst(e.x, e.y, 0xFFFFFFFF.toInt(), 6, 70f, 0f)
                Adv.EV_BOSSWARN -> sfx(Sfx.COUNT)
                Adv.EV_DIE -> { burst(e.x, e.y, pal[0], 28, 210f); corpses.add(Corpse(e.x, e.y, pal[0], 0f)); sfx(Sfx.DIE) }
                Adv.EV_WIN -> { for (k in 0 until 4) burst(e.x, e.y, pal[k], 22, 210f); sfx(Sfx.WIN) }
                Adv.EV_STUN -> nums.add(Num(e.x, e.y - 10f, "¡Aturdido!", 1.0f, 0xFFFFE070.toInt()))
            }
        }
        a.events.clear()
    }

    private fun pickText(kind: Int) {
        when (kind) {
            Adv.IT_WHIP -> say("¡Látigo mejorado!")
            Adv.IT_BOOTS -> say("¡Botas aéreas: doble salto!")
            Adv.IT_POTION -> say("¡Invulnerable un rato!")
            Adv.IT_SUB -> adv?.let { say(AdvCfg.subNames[it.sub], 1.6f) }
            Adv.IT_MEAT -> say("+4 vida", 1.0f)
        }
    }

    // ------------------------------------------------------------------ utilidades de dibujo (acrílico)
    private fun opaque(c: Int) = c or (0xFF shl 24)

    private fun lighten(c: Int, f: Float): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return Color.rgb((r + (255 - r) * f).toInt(), (g + (255 - g) * f).toInt(), (b + (255 - b) * f).toInt())
    }

    private val gradCache = HashMap<Long, LinearGradient>()
    private val glowCache = HashMap<Int, android.graphics.RadialGradient>()

    private fun vGrad(col: Int, h: Float): LinearGradient {
        if (gradCache.size > 700) gradCache.clear()
        val hk = (h * 2f).toInt().coerceIn(1, 4000)
        val key = (col.toLong() and 0xFFFFFFFFL) * 8192L + hk
        var g = gradCache[key]
        if (g == null) {
            g = LinearGradient(0f, 0f, 0f, hk / 2f, intArrayOf(lighten(col, 0.42f), col, Gl.darken(col, 0.5f)), floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
            gradCache[key] = g
        }
        return g
    }

    /** Resplandor con degradado reutilizado (evita crear uno nuevo en cada cuadro). */
    private fun glow(c: Canvas, x: Float, y: Float, rad: Float, color: Int, a: Int) {
        if (a <= 0 || rad < 0.5f) return
        val key = color and 0xFFFFFF
        var g = glowCache[key]
        if (g == null) {
            g = android.graphics.RadialGradient(0f, 0f, 64f, (0xFF shl 24) or key, key, Shader.TileMode.CLAMP)
            glowCache[key] = g
        }
        val pp = Gl.p()
        pp.shader = g
        pp.alpha = a.coerceIn(0, 255)
        c.save()
        c.translate(x, y)
        val k = rad / 64f
        c.scale(k, k)
        c.drawCircle(0f, 0f, 64f, pp)
        c.restore()
    }

    /** Cuerpo opaco tipo acrílico: degradado vertical, borde oscuro, brillo en la esquina y reflejo. kind 0=óvalo 1=rect redondeado. */
    private fun gloss(c: Canvas, kind: Int, l: Float, t: Float, r: Float, b: Float, rad: Float, col0: Int, alpha: Int = 255) {
        val col = opaque(col0)
        val w = r - l
        val h = b - t
        if (w <= 0f || h <= 0f) return
        c.save()
        c.translate(l, t)
        rf.set(0f, 0f, w, h)
        var pp = Gl.p()
        pp.shader = vGrad(col, h)
        pp.alpha = alpha
        if (kind == 0) c.drawOval(rf, pp) else c.drawRoundRect(rf, rad, rad, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 1.6f
        pp.color = Gl.alpha(Gl.darken(col, 0.3f), alpha)
        if (kind == 0) c.drawOval(rf, pp) else c.drawRoundRect(rf, rad, rad, pp)
        if (w > 7f && h > 7f) {
            rf2.set(w * 0.14f, h * 0.07f, w * 0.64f, h * 0.34f)
            pp = Gl.p()
            pp.color = Gl.white((110 * alpha) / 255)
            c.drawRoundRect(rf2, h * 0.16f, h * 0.16f, pp)
            pp = Gl.p()
            pp.color = Gl.white((70 * alpha) / 255)
            c.drawCircle(w * 0.2f, h * 0.2f, max(1f, min(w, h) * 0.05f), pp)
        }
        c.restore()
    }

    /** Igual que gloss pero para un contorno ya construido en `pth` (coordenadas absolutas). */
    private fun glossPath(c: Canvas, l: Float, t: Float, r: Float, b: Float, col0: Int, alpha: Int = 255) {
        val col = opaque(col0)
        val w = r - l
        val h = b - t
        if (w <= 0f || h <= 0f) return
        pth.offset(-l, -t)
        c.save()
        c.translate(l, t)
        var pp = Gl.p()
        pp.shader = vGrad(col, h)
        pp.alpha = alpha
        c.drawPath(pth, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 1.6f
        pp.strokeJoin = Paint.Join.ROUND
        pp.color = Gl.alpha(Gl.darken(col, 0.3f), alpha)
        c.drawPath(pth, pp)
        if (w > 7f && h > 7f) {
            rf2.set(w * 0.16f, h * 0.08f, w * 0.5f, h * 0.28f)
            pp = Gl.p()
            pp.color = Gl.white((95 * alpha) / 255)
            c.drawOval(rf2, pp)
        }
        c.restore()
    }

    private fun eyes(c: Canvas, x: Float, y: Float, face: Int, s: Float, col: Int = Color.WHITE) {
        for (k in intArrayOf(-1, 1)) {
            val ex = x + k * s * 1.6f + face * s * 0.3f
            rf.set(ex - s, y - s * 1.1f, ex + s, y + s * 1.1f)
            var pp = Gl.p(); pp.color = col
            c.drawOval(rf, pp)
            pp = Gl.p(); pp.color = 0xFF14101C.toInt()
            c.drawCircle(ex + face * s * 0.35f, y + s * 0.1f, s * 0.55f, pp)
        }
    }

    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) = Gl.line(c, x0, y0, x1, y1, w, col)

    // ------------------------------------------------------------------ dibujo
    fun draw(c: Canvas, w: Int, h: Int) {
        val a = adv ?: return
        al = if (phase == 1 || phase == 3) a.alpha else 1f
        swapPos(a, true)
        try { drawInner(c, a, w, h) } finally { swapPos(a, false) }
    }

    /** Pone las posiciones interpoladas entre el paso anterior y el actual (y las restaura después). */
    private fun swapPos(a: Adv, forward: Boolean) {
        val need = 8 + 2 * (a.enemies.size + a.projs.size + a.movers.size + a.items.size)
        if (swapBuf.size < need) {
            if (!forward) return
            swapBuf = FloatArray(need + 64)
        }
        var k = 0
        val b = swapBuf
        if (forward) {
            b[k++] = a.px; b[k++] = a.py; b[k++] = a.bx; b[k++] = a.camX
            a.px = ip(a.opx, a.px); a.py = ip(a.opy, a.py); a.bx = ip(a.obx, a.bx); a.camX = ip(a.ocam, a.camX)
            for (e in a.enemies) { b[k++] = e.x; b[k++] = e.y; e.x = ip(e.ox, e.x); e.y = ip(e.oy, e.y) }
            for (p in a.projs) { b[k++] = p.x; b[k++] = p.y; p.x = ip(p.ox, p.x); p.y = ip(p.oy, p.y) }
            for (m in a.movers) { b[k++] = m.x; b[k++] = m.y; m.x = ip(m.ox, m.x); m.y = ip(m.oy, m.y) }
            for (i in a.items) { b[k++] = i.x; b[k++] = i.y; i.x = ip(i.ox, i.x); i.y = ip(i.oy, i.y) }
        } else {
            a.px = b[k++]; a.py = b[k++]; a.bx = b[k++]; a.camX = b[k++]
            for (e in a.enemies) { e.x = b[k++]; e.y = b[k++] }
            for (p in a.projs) { p.x = b[k++]; p.y = b[k++] }
            for (m in a.movers) { m.x = b[k++]; m.y = b[k++] }
            for (i in a.items) { i.x = b[k++]; i.y = b[k++] }
        }
    }

    private fun ip(o: Float, n: Float): Float = if (abs(n - o) > 90f) n else o + (n - o) * al

    private fun easeBack(t: Float): Float {
        val x = t.coerceIn(0f, 1f) - 1f
        return 1f + 2.70158f * x * x * x + 1.70158f * x * x
    }

    private fun drawInner(c: Canvas, a: Adv, w: Int, h: Int) {
        val s = h / AdvCfg.H
        val pal = Worlds.colors[world.coerceIn(0, Worlds.colors.size - 1)]
        val bg = Worlds.bg[world.coerceIn(0, Worlds.bg.size - 1)]
        c.save()
        c.scale(s, s)
        vw = w / s
        if (zoom > 0f) { val z = 1f + 0.05f * zoom; c.scale(z, z, vw / 2f, AdvCfg.H / 2f) }
        var pp = Gl.p()
        pp.shader = LinearGradient(0f, 0f, 0f, AdvCfg.H, intArrayOf(bg[0], bg[1], bg[2]), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, vw, AdvCfg.H, pp)
        drawFar(c, pal, a.camX)
        val sh = a.shake
        c.save()
        if (sh > 0f) c.translate((rnd.nextFloat() - 0.5f) * sh, (rnd.nextFloat() - 0.5f) * sh)
        c.translate(-a.camX, 0f)
        drawWorld(c, a, pal)
        for (p in parts) {
            pp = Gl.p()
            pp.color = Gl.alpha(p.col, (255 * min(1f, p.life * 2f)).toInt())
            c.drawCircle(p.x, p.y, p.sz, pp)
        }
        for (n in nums) Gl.text(c, n.s, n.x, n.y, 13f, Gl.medium, Gl.alpha(n.col, (255 * min(1f, n.life * 2f)).toInt()))
        c.restore()
        if (a.freezeT > 0f) { pp = Gl.p(); pp.color = Gl.alpha(0xFF60A0FF.toInt(), 36); c.drawRect(0f, 0f, vw, AdvCfg.H, pp) }
        if (flash > 0f) { pp = Gl.p(); pp.color = Gl.alpha(0xFFFF3040.toInt(), (70 * flash).toInt()); c.drawRect(0f, 0f, vw, AdvCfg.H, pp) }
        if (a.potionT > 0f) { pp = Gl.p(); pp.color = Gl.alpha(0xFF60FFB0.toInt(), 14); c.drawRect(0f, 0f, vw, AdvCfg.H, pp) }
        drawHud(c, a, pal)
        if (phase != 0) drawControls(c)
        if (bannerT > 0f && phase == 1) {
            val ba = (255 * min(1f, bannerT * 2.5f)).toInt()
            val bs = 0.6f + 0.4f * easeBack(bannerAge / 0.28f)
            c.save()
            c.scale(bs, bs, vw / 2f, 96f)
            Gl.text(c, banner, vw / 2f, 96f, 26f, Gl.medium, Gl.alpha(Color.WHITE, ba), 0.06f, null, Gl.alpha(pal[0], ba))
            c.restore()
        }
        if (phase != 1) {
            val ov = Gl.p(); ov.color = Gl.alpha(Color.BLACK, (150 * min(1f, phT * 4f)).toInt())
            c.drawRect(0f, 0f, vw, AdvCfg.H, ov)
            val pk = 0.8f + 0.2f * easeBack(phT / 0.35f)
            c.save()
            c.scale(pk, pk, vw / 2f, AdvCfg.H / 2f)
            when (phase) {
                0 -> drawStory(c, pal)
                2 -> drawPause(c, pal)
                3 -> drawWin(c, pal)
            }
            c.restore()
        }
        c.restore()
    }

    private fun drawFar(c: Canvas, pal: IntArray, cam: Float) {
        val style = world % 3
        for (layer in 0 until 3) {
            val par = 0.12f + 0.16f * layer
            val base = 250f + layer * 28f
            val col = Gl.alpha(Gl.darken(pal[(layer + world) % 4], 0.35f + 0.1f * layer), 200)
            val ox = -(cam * par) % 240f
            val pp = Gl.p()
            pp.color = col
            pth.reset()
            pth.moveTo(ox - 240f, AdvCfg.H)
            var x = ox - 240f
            var i = 0
            while (x < vw + 260f) {
                val hh = 40f + 34f * (0.5f + 0.5f * sin((i + layer * 3 + world) * 1.7f)) - layer * 6f
                when (style) {
                    0 -> { pth.lineTo(x, base); pth.lineTo(x + 30f, base - hh); pth.lineTo(x + 60f, base) }
                    1 -> { pth.lineTo(x, base); pth.quadTo(x + 40f, base - hh * 1.6f, x + 80f, base) }
                    else -> { pth.lineTo(x, base); pth.lineTo(x, base - hh); pth.lineTo(x + 36f, base - hh); pth.lineTo(x + 36f, base) }
                }
                x += when (style) { 0 -> 60f; 1 -> 80f; else -> 52f }
                i++
            }
            pth.lineTo(x, AdvCfg.H)
            pth.close()
            c.drawPath(pth, pp)
        }
        for (i in 0 until 22) {
            val fx = ((i * 97f + time * (8f + (i % 5) * 3f) - cam * 0.3f) % (vw + 40f) + vw + 40f) % (vw + 40f)
            val fy = 40f + (i * 53f) % 220f + sin(time * 0.8f + i) * 10f
            glow(c, fx, fy, 5f + (i % 3) * 3f, pal[i % 4], 90)
        }
    }

    private fun drawWorld(c: Canvas, a: Adv, pal: IntArray) {
        val l = a.camX - 60f
        val r = a.camX + vw + 60f
        // fondo de los fosos: lava, agua, hielo o vacío
        val pitCol = when (world) { 7 -> 0xFFFF5020.toInt(); 2 -> 0xFF2080FF.toInt(); 9 -> 0xFF9AE0FF.toInt(); 8 -> 0xFF50D060.toInt(); else -> pal[1] }
        run {
            val pp = Gl.p()
            pp.shader = LinearGradient(0f, AdvCfg.H - 40f, 0f, AdvCfg.H, Gl.alpha(pitCol, 0), Gl.alpha(pitCol, 200), Shader.TileMode.CLAMP)
            c.drawRect(l, AdvCfg.H - 40f, r, AdvCfg.H, pp)
            for (k in 0 until 14) {
                val bx = l + ((k * 83f + time * 14f) % (vw + 120f))
                glow(c, bx, AdvCfg.H - 10f + sin(time * 2f + k) * 4f, 16f, pitCol, 90)
            }
        }
        for (p in a.plats) {
            if (p.x1 < l || p.x0 > r) continue
            val thick = if (p.y >= AdvCfg.GROUND) AdvCfg.H - p.y else 14f
            val baseCol = if (a.ice) 0xFF9CC8E8.toInt() else Gl.darken(pal[0], 0.55f)
            var pp = Gl.p()
            pp.shader = LinearGradient(0f, p.y, 0f, p.y + thick, intArrayOf(opaque(lighten(baseCol, 0.25f)), opaque(baseCol), opaque(Gl.darken(baseCol, 0.35f))), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(p.x0, p.y, p.x1, p.y + thick, pp)
            line(c, p.x0, p.y + 1.5f, p.x1, p.y + 1.5f, 3f, Gl.alpha(pal[1], 240))
            line(c, p.x0, p.y + 4.5f, p.x1, p.y + 4.5f, 1.2f, Gl.white(110))
            if (a.ice) { // reflejos de hielo
                var gx = p.x0 + 14f
                while (gx < p.x1 - 20f) { line(c, gx, p.y + 2f, gx + 18f, p.y + 2f, 1.4f, Gl.white(150)); gx += 70f }
            }
            if (p.y >= AdvCfg.GROUND) {
                var bx = p.x0 + 20f
                while (bx < p.x1 - 4f) { line(c, bx, p.y + 10f, bx, p.y + 26f, 1.2f, Gl.white(26)); bx += 36f }
                line(c, p.x0, p.y + 26f, p.x1, p.y + 26f, 1.2f, Gl.white(22))
            }
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 1.4f
            pp.color = Gl.alpha(Gl.darken(baseCol, 0.25f), 220)
            c.drawRect(p.x0, p.y, p.x1, p.y + thick, pp)
        }
        for (cr in a.crumbles) {
            if (cr.st == 2 || cr.x1 < l || cr.x0 > r) continue
            val sx = if (cr.st == 1) sin(time * 70f) * 1.4f else 0f
            gloss(c, 1, cr.x0 + sx, cr.y, cr.x1 + sx, cr.y + 12f, 4f, 0xFFB09878.toInt())
            line(c, cr.x0 + 12f + sx, cr.y + 2f, cr.x0 + 18f + sx, cr.y + 10f, 1.2f, Gl.darken(0xFFB09878.toInt(), 0.4f))
        }
        for (m in a.movers) {
            if (m.x1 + m.w < l || m.x0 - m.w > r) continue
            if (m.vertical) line(c, m.x, m.y1 + 12f, m.x, m.y0 + 12f, 2f, Gl.white(40))
            else line(c, m.x0, m.y0 + 12f, m.x1, m.y0 + 12f, 2f, Gl.white(40))
            glow(c, m.x, m.y + 14f, m.w * 0.7f, pal[1], 60)
            gloss(c, 1, m.x - m.w / 2f, m.y, m.x + m.w / 2f, m.y + 13f, 5f, pal[2])
            for (k in intArrayOf(-1, 1)) glow(c, m.x + k * (m.w / 2f - 7f), m.y + 6f, 7f, 0xFFFFFFFF.toInt(), 110)
        }
        for (sp in a.spikes) {
            if (sp[1] < l || sp[0] > r) continue
            var x = sp[0]
            while (x < sp[1] - 2f) {
                pth.reset()
                pth.moveTo(x, AdvCfg.GROUND); pth.lineTo(x + 5f, AdvCfg.GROUND - 16f); pth.lineTo(x + 10f, AdvCfg.GROUND); pth.close()
                glossPath(c, x, AdvCfg.GROUND - 16f, x + 10f, AdvCfg.GROUND, 0xFFD8E0F0.toInt())
                x += 10f
            }
            glow(c, (sp[0] + sp[1]) / 2f, AdvCfg.GROUND - 8f, 40f, 0xFFFF4050.toInt(), 40)
        }
        for (ck in a.checks) {
            if (ck < l || ck > r || ck <= 40f) continue
            line(c, ck, AdvCfg.GROUND, ck, AdvCfg.GROUND - 34f, 2f, Gl.white(120))
            glow(c, ck, AdvCfg.GROUND - 38f, 22f, 0xFF80FFA0.toInt(), 120)
        }
        for (j in a.jets) if (j.x > l && j.x < r) drawJet(c, j)
        for (rk in a.rocks) if (rk.x > l && rk.x < r) drawRock(c, rk, pal)
        for (cd in a.candles) {
            if (!cd.alive || cd.x < l || cd.x > r) continue
            line(c, cd.x, cd.y, cd.x, cd.y - 18f, 3f, 0xFFB09070.toInt())
            line(c, cd.x - 6f, cd.y, cd.x + 6f, cd.y, 3f, 0xFF806040.toInt())
            val fl = 1f + 0.2f * sin(time * 12f + cd.x)
            glow(c, cd.x, cd.y - 24f, 18f * fl, 0xFFFFA030.toInt(), 130)
            rf.set(cd.x - 3.5f, cd.y - 31f, cd.x + 3.5f, cd.y - 19f)
            val pp = Gl.p(); pp.color = 0xFFFFE080.toInt()
            c.drawOval(rf, pp)
        }
        for (p in a.pends) if (p.cx > l - 160f && p.cx < r + 160f) drawPend(c, p, pal)
        for (i in a.items) drawItem(c, i, pal)
        for (e in a.enemies) if (e.x > l - 40f && e.x < r + 40f) drawEnemy(c, e, a, pal)
        drawBoss(c, a, pal)
        for (h in a.hazs) drawHaz(c, h)
        for (p in a.projs) drawProj(c, p, pal)
        for (am in a.ambushes) if (am.st == 1) drawGate(c, am, pal)
        for (k in corpses) drawCorpse(c, k)
        drawPlayer(c, a, pal)
        if (a.bibleT > 0f) drawBible(c, a)
    }

    private fun drawCorpse(c: Canvas, k: Corpse) {
        val u = (k.t / 0.5f).coerceIn(0f, 1f)
        val sc = if (u < 0.15f) 1f + 0.35f * (u / 0.15f) else 1.35f * (1f - (u - 0.15f) / 0.85f)
        val r = 11f * max(0.05f, sc)
        val al = (255 * (1f - u)).toInt()
        gloss(c, 0, k.x - r, k.y - r, k.x + r, k.y + r, 0f, k.col, al)
        Gl.arc(c, k.x, k.y, 8f + 34f * u, 0f, 360f, 2.4f * (1f - u) + 0.4f, Gl.alpha(Color.WHITE, (200 * (1f - u)).toInt()))
        for (n in 0 until 6) {
            val an = n * 1.047f + 0.4f
            val d = 6f + 30f * u
            val sx = k.x + cos(an) * d
            val sy = k.y + sin(an) * d + 40f * u * u
            c.save(); c.rotate(u * 360f + n * 40f, sx, sy)
            val pp = Gl.p(); pp.color = Gl.alpha(opaque(k.col), (230 * (1f - u)).toInt())
            c.drawRect(sx - 2.5f, sy - 2.5f, sx + 2.5f, sy + 2.5f, pp)
            c.restore()
        }
    }

    private fun drawGate(c: Canvas, am: AdvAmbush, pal: IntArray) {
        for (x in floatArrayOf(am.x0 + 4f, am.x1 - 4f)) {
            val pp = Gl.p()
            pp.shader = LinearGradient(x - 8f, 0f, x + 8f, 0f, intArrayOf(Gl.alpha(pal[1], 0), Gl.alpha(pal[1], 150), Gl.alpha(pal[1], 0)), null, Shader.TileMode.CLAMP)
            c.drawRect(x - 8f, 0f, x + 8f, AdvCfg.GROUND, pp)
            for (k in 0 until 6) {
                val yy = ((time * 80f + k * 50f) % AdvCfg.GROUND)
                glow(c, x, yy, 9f, Color.WHITE, 120)
            }
        }
    }

    private fun drawJet(c: Canvas, j: AdvJet) {
        gloss(c, 1, j.x - 9f, j.y - 7f, j.x + 9f, j.y, 3f, 0xFF707890.toInt())
        if (j.warn) {
            val f = 6f + 5f * sin(time * 40f)
            glow(c, j.x, j.y - 8f, 14f, 0xFFFF8020.toInt(), 110)
            pth.reset(); pth.moveTo(j.x - 4f, j.y - 7f); pth.quadTo(j.x, j.y - 7f - f, j.x + 4f, j.y - 7f); pth.close()
            val pp = Gl.p(); pp.color = 0xFFFFB040.toInt(); c.drawPath(pth, pp)
        }
        if (j.on) {
            val fl = 1f + 0.1f * sin(time * 50f + j.x)
            glow(c, j.x, j.y - 36f, 46f, 0xFFFF7020.toInt(), 130)
            pth.reset(); pth.moveTo(j.x - 9f, j.y - 7f); pth.quadTo(j.x - 12f, j.y - 40f * fl, j.x, j.y - 76f * fl); pth.quadTo(j.x + 12f, j.y - 40f * fl, j.x + 9f, j.y - 7f); pth.close()
            var pp = Gl.p(); pp.color = 0xFFFF6A20.toInt(); c.drawPath(pth, pp)
            pth.reset(); pth.moveTo(j.x - 5f, j.y - 7f); pth.quadTo(j.x - 6f, j.y - 30f * fl, j.x, j.y - 56f * fl); pth.quadTo(j.x + 6f, j.y - 30f * fl, j.x + 5f, j.y - 7f); pth.close()
            pp = Gl.p(); pp.color = 0xFFFFE070.toInt(); c.drawPath(pth, pp)
        }
    }

    private fun drawRock(c: Canvas, r: AdvRock, pal: IntArray) {
        if (r.st == 3) {
            gloss(c, 0, r.x - 14f, AdvCfg.GROUND - 8f, r.x - 2f, AdvCfg.GROUND, 0f, 0xFF9A8E86.toInt())
            gloss(c, 0, r.x + 1f, AdvCfg.GROUND - 6f, r.x + 12f, AdvCfg.GROUND, 0f, 0xFF8A7E76.toInt())
            return
        }
        val sx = if (r.st == 1) sin(time * 60f) * 1.5f else 0f
        val x = r.x + sx
        val y = r.y
        pth.reset()
        pth.moveTo(x - 12f, y - 22f); pth.lineTo(x + 12f, y - 22f); pth.lineTo(x + 5f, y - 4f); pth.lineTo(x, y); pth.lineTo(x - 5f, y - 4f); pth.close()
        glossPath(c, x - 12f, y - 22f, x + 12f, y, 0xFF9A8E86.toInt())
        if (r.st == 0 || r.st == 1) line(c, x, y - 22f, x, y - 26f, 2f, Gl.white(60))
        if (r.st == 1) glow(c, x, AdvCfg.GROUND - 2f, 22f, 0xFFFF3040.toInt(), 70 + (50 * sin(time * 30f)).toInt())
    }

    private fun drawPend(c: Canvas, p: AdvPend, pal: IntArray) {
        line(c, p.cx, 0f, p.cx, p.cy, 3f, Gl.white(70))
        gloss(c, 0, p.cx - 6f, p.cy - 6f, p.cx + 6f, p.cy + 6f, 0f, 0xFF8890A8.toInt())
        line(c, p.cx, p.cy, p.bx, p.by, 2.2f, 0xFFB8C0D8.toInt())
        c.save()
        c.rotate(time * 220f, p.bx, p.by)
        for (k in 0 until 8) {
            val an = k * 0.785f
            pth.reset()
            pth.moveTo(p.bx + cos(an - 0.2f) * 9f, p.by + sin(an - 0.2f) * 9f)
            pth.lineTo(p.bx + cos(an) * 15f, p.by + sin(an) * 15f)
            pth.lineTo(p.bx + cos(an + 0.2f) * 9f, p.by + sin(an + 0.2f) * 9f)
            pth.close()
            val pp = Gl.p(); pp.color = 0xFFD8E0F0.toInt(); c.drawPath(pth, pp)
        }
        c.restore()
        gloss(c, 0, p.bx - 9f, p.by - 9f, p.bx + 9f, p.by + 9f, 0f, 0xFFA8B0C8.toInt())
        glow(c, p.bx, p.by, 20f, pal[1], 40)
    }

    private fun drawBible(c: Canvas, a: Adv) {
        for (k in 0 until 4) {
            val an = time * 6.5f + k * 1.5708f
            val x = a.px + cos(an) * 36f
            val y = a.py - 15f + sin(an) * 28f
            glow(c, x, y, 16f, 0xFFFFE070.toInt(), 120)
            gloss(c, 1, x - 6f, y - 7f, x + 6f, y + 7f, 2f, 0xFFE8D8A0.toInt())
            line(c, x, y - 6f, x, y + 6f, 1.2f, 0xFF806030.toInt())
        }
    }

    private fun drawItem(c: Canvas, i: AdvItem, pal: IntArray) {
        val pop = if (i.t < 0.3f) easeBack(i.t / 0.3f) else 1f
        if (pop != 1f) { c.save(); c.scale(pop, pop, i.x, i.y) }
        drawItemBody(c, i, pal)
        if (pop != 1f) c.restore()
    }

    private fun drawItemBody(c: Canvas, i: AdvItem, pal: IntArray) {
        val bob = sin(i.t * 5f) * 1.5f
        val x = i.x
        val y = i.y + bob
        when (i.kind) {
            Adv.IT_HEART, Adv.IT_BIG -> {
                val k = if (i.kind == Adv.IT_BIG) 1.6f else 1f
                glow(c, x, y, 14f * k, 0xFFFF4070.toInt(), 90)
                heart(c, x, y, k, 0xFFFF4070.toInt())
            }
            Adv.IT_MEAT -> {
                glow(c, x, y, 14f, 0xFFFFA040.toInt(), 70)
                gloss(c, 0, x - 7f, y - 6f, x + 7f, y + 6f, 0f, 0xFFD0803A.toInt())
                line(c, x + 4f, y - 4f, x + 9f, y - 9f, 2.5f, Gl.white(230))
            }
            Adv.IT_BOOTS -> {
                glow(c, x, y, 18f, 0xFF60E0FF.toInt(), 110)
                gloss(c, 1, x - 5f, y - 8f, x + 3f, y + 4f, 2f, 0xFF40C0F0.toInt())
                gloss(c, 1, x - 6f, y + 1f, x + 8f, y + 6f, 2.5f, 0xFF2090D0.toInt())
                line(c, x + 4f, y - 4f, x + 11f, y - 8f, 1.8f, Color.WHITE)
                line(c, x + 4f, y - 1f, x + 12f, y - 3f, 1.8f, Color.WHITE)
            }
            Adv.IT_POTION -> {
                glow(c, x, y, 18f, 0xFF60FFB0.toInt(), 110)
                gloss(c, 0, x - 6f, y - 3f, x + 6f, y + 8f, 0f, 0xFF40E090.toInt())
                gloss(c, 1, x - 2.5f, y - 9f, x + 2.5f, y - 2f, 1.5f, 0xFFD0E8F0.toInt())
            }
            Adv.IT_SUB, Adv.IT_WHIP, Adv.IT_ORB -> {
                val col = if (i.kind == Adv.IT_ORB) pal[((time * 4f).toInt()) % 4] else if (i.kind == Adv.IT_WHIP) 0xFFFFD060.toInt() else 0xFF70C8FF.toInt()
                val rr = if (i.kind == Adv.IT_ORB) 13f else 8f
                glow(c, x, y, rr * 2.6f, col, 120)
                gloss(c, 0, x - rr, y - rr, x + rr, y + rr, 0f, col)
                val ch = if (i.kind == Adv.IT_WHIP) "L" else if (i.kind == Adv.IT_ORB) "" else AdvCfg.subLetters.substring(i.arg.coerceIn(1, AdvCfg.SUBS), i.arg.coerceIn(1, AdvCfg.SUBS) + 1)
                if (ch.isNotEmpty()) Gl.text(c, ch, x, y + 4f, 11f, Gl.medium, Color.BLACK)
                else Gl.arc(c, x, y, rr + 5f, time * 200f, 270f, 2f, Gl.white(200))
            }
        }
    }

    private fun heart(c: Canvas, x: Float, y: Float, k: Float, col: Int) {
        val pp = Gl.p()
        pp.color = col
        c.drawCircle(x - 3f * k, y - 2f * k, 3.6f * k, pp)
        c.drawCircle(x + 3f * k, y - 2f * k, 3.6f * k, pp)
        pth.reset()
        pth.moveTo(x - 6.4f * k, y - 0.5f * k); pth.lineTo(x + 6.4f * k, y - 0.5f * k); pth.lineTo(x, y + 7f * k); pth.close()
        c.drawPath(pth, pp)
        val hp = Gl.p(); hp.color = Gl.white(150)
        c.drawCircle(x - 4f * k, y - 3.4f * k, 1.1f * k, hp)
    }

    // ------------------------------------------------------------------ enemigos (opacos, tipo acrílico)
    private fun drawEnemy(c: Canvas, e: AdvEnemy, a: Adv, pal: IntArray) {
        val pop = if (e.age < 0.35f) easeBack(e.age / 0.35f) else 1f
        if (pop != 1f) { c.save(); c.scale(pop, pop, e.x, e.y) }
        drawEnemyBody(c, e, a, pal)
        if (pop != 1f) c.restore()
    }

    private fun drawEnemyBody(c: Canvas, e: AdvEnemy, a: Adv, pal: IntArray) {
        val hit = e.flash > 0f
        val base0 = when (e.type) {
            Adv.E_WALKER -> pal[1]; Adv.E_BAT -> pal[2]; Adv.E_THROWER -> pal[3]; Adv.E_HOPPER -> pal[0]
            Adv.E_TURRET -> 0xFF7A88A8.toInt(); Adv.E_CHARGER -> pal[2]; Adv.E_MEDUSA -> 0xFF50C878.toInt()
            Adv.E_KNIGHT -> 0xFF8898B8.toInt(); Adv.E_GHOST -> 0xFFE8EEFF.toInt(); else -> 0xFF6A3A8A.toInt()
        }
        val col = if (hit) Color.WHITE else opaque(base0)
        val x = e.x
        val y = e.y
        val f = e.face
        val tt = time * 6f + e.x * 0.05f
        // sombra de contacto
        if (e.type != Adv.E_BAT && e.type != Adv.E_GHOST && e.type != Adv.E_MEDUSA) {
            val sp = Gl.p(); sp.color = Gl.alpha(Color.BLACK, 70)
            rf.set(x - 10f, e.floor - 2f, x + 10f, e.floor + 2f)
            c.drawOval(rf, sp)
        }
        when (e.type) {
            Adv.E_WALKER -> {
                val bob = abs(sin(tt)) * 2f
                val sw = sin(tt) * 4f
                gloss(c, 0, x - 8f + sw, y - 4f, x - 1f + sw, y, 0f, Gl.darken(col, 0.7f))
                gloss(c, 0, x + 1f - sw, y - 4f, x + 8f - sw, y, 0f, Gl.darken(col, 0.7f))
                gloss(c, 1, x - 11f, y - 28f - bob, x + 11f, y - 4f - bob, 9f, col)
                gloss(c, 0, x + f * 9f - 3f, y - 15f - bob, x + f * 9f + 4f, y - 8f - bob, 0f, Gl.darken(col, 0.8f))
                eyes(c, x + f * 2f, y - 21f - bob, f, 2.3f, 0xFFFFF4C0.toInt())
            }
            Adv.E_BAT -> {
                val flap = sin(time * 18f + e.t) * 9f
                for (k in intArrayOf(-1, 1)) {
                    pth.reset()
                    pth.moveTo(x, y - 9f); pth.lineTo(x + k * 18f, y - 16f + flap); pth.lineTo(x + k * 15f, y - 4f + flap * 0.4f); pth.lineTo(x + k * 8f, y - 6f); pth.lineTo(x + k * 3f, y - 2f); pth.close()
                    glossPath(c, x - 18f, y - 16f, x + 18f, y - 2f, Gl.darken(col, 0.75f))
                }
                gloss(c, 0, x - 6f, y - 14f, x + 6f, y - 1f, 0f, col)
                for (k in intArrayOf(-1, 1)) { pth.reset(); pth.moveTo(x + k * 2f, y - 13f); pth.lineTo(x + k * 6f, y - 20f); pth.lineTo(x + k * 6f, y - 12f); pth.close(); val pp = Gl.p(); pp.color = Gl.darken(col, 0.6f); c.drawPath(pth, pp) }
                eyes(c, x, y - 8f, f, 1.7f, 0xFFFFF4C0.toInt())
            }
            Adv.E_THROWER -> {
                val ar = (e.t / 2.3f).coerceIn(0f, 1f)
                gloss(c, 1, x - 11f, y - 26f, x + 11f, y - 2f, 9f, col)
                gloss(c, 0, x - 10f, y - 36f, x + 10f, y - 20f, 0f, Gl.darken(col, 0.9f))
                eyes(c, x + f * 2f, y - 28f, f, 2.1f, 0xFFFFF4C0.toInt())
                gloss(c, 0, x + f * 12f - 4f, y - 30f - ar * 8f - 4f, x + f * 12f + 4f, y - 30f - ar * 8f + 4f, 0f, 0xFFFF5060.toInt())
            }
            Adv.E_HOPPER -> {
                val air = e.y < e.baseY - 2f
                val sy = if (air) 1.2f else 0.86f + 0.06f * sin(tt)
                val sxs = 1f / sy
                gloss(c, 0, x - 12f * sxs, y - 24f * sy, x + 12f * sxs, y, 0f, col)
                eyes(c, x + f * 2f, y - 15f * sy, f, 2.4f, 0xFFFFF4C0.toInt())
                val mp = Gl.p(); mp.color = Gl.darken(col, 0.3f); mp.style = Paint.Style.STROKE; mp.strokeWidth = 1.6f
                rf.set(x - 4f, y - 11f * sy, x + 4f, y - 5f * sy); c.drawArc(rf, 0f, 180f, false, mp)
            }
            Adv.E_TURRET -> {
                val tx = a.px - x
                val ty = (a.py - 15f) - (y - 17f)
                val ang = Math.toDegrees(Math.atan2(ty.toDouble(), tx.toDouble())).toFloat()
                pth.reset(); pth.moveTo(x - 12f, y); pth.lineTo(x + 12f, y); pth.lineTo(x + 8f, y - 12f); pth.lineTo(x - 8f, y - 12f); pth.close()
                glossPath(c, x - 12f, y - 12f, x + 12f, y, Gl.darken(col, 0.8f))
                c.save(); c.rotate(ang, x, y - 17f)
                gloss(c, 1, x, y - 20f, x + 17f, y - 14f, 2f, Gl.darken(col, 0.7f))
                c.restore()
                gloss(c, 0, x - 8f, y - 25f, x + 8f, y - 9f, 0f, col)
                val charge = (e.t / 2f).coerceIn(0f, 1f)
                glow(c, x, y - 17f, 8f + 6f * charge, 0xFFFF4060.toInt(), (60 + 140 * charge).toInt())
            }
            Adv.E_CHARGER -> {
                val wind = e.st == 1
                val sh = if (wind) sin(time * 60f) * 1.2f else 0f
                val runb = if (e.st == 2) sin(time * 30f) * 1.5f else 0f
                gloss(c, 1, x - 14f + sh, y - 24f + runb, x + 14f + sh, y - 3f + runb, 10f, col)
                gloss(c, 0, x - 12f + sh, y - 5f, x - 4f + sh, y, 0f, Gl.darken(col, 0.6f))
                gloss(c, 0, x + 4f + sh, y - 5f, x + 12f + sh, y, 0f, Gl.darken(col, 0.6f))
                pth.reset(); pth.moveTo(x + f * 12f, y - 16f); pth.lineTo(x + f * 25f, y - 23f); pth.lineTo(x + f * 12f, y - 23f); pth.close()
                glossPath(c, x - 14f, y - 24f, x + 26f, y - 14f, 0xFFF0F0E8.toInt())
                eyes(c, x + f * 4f, y - 17f, f, 2.2f, if (wind) 0xFFFF6070.toInt() else 0xFFFFF4C0.toInt())
                if (wind) for (k in 0 until 2) glow(c, x - f * 14f, y - 6f - k * 6f, 6f, Gl.white(200), 120)
            }
            Adv.E_MEDUSA -> {
                for (k in -2..2) {
                    val wv = sin(time * 8f + k) * 4f
                    line(c, x + k * 3f, y - 6f, x + k * 6f - f * 8f, y + 4f + wv, 2.4f, 0xFF2E8A58.toInt())
                }
                gloss(c, 0, x - 9f, y - 16f, x + 9f, y + 2f, 0f, col)
                eyes(c, x, y - 8f, f, 2f, 0xFFFFF4C0.toInt())
            }
            Adv.E_KNIGHT -> {
                val sw = if (abs(a.px - x) < 180f) sin(tt) * 2f else 0f
                gloss(c, 1, x - 11f + sw, y - 12f, x - 1f + sw, y, 3f, Gl.darken(col, 0.7f))
                gloss(c, 1, x + 1f - sw, y - 12f, x + 11f - sw, y, 3f, Gl.darken(col, 0.7f))
                gloss(c, 1, x - 11f, y - 30f, x + 11f, y - 10f, 6f, col)
                gloss(c, 0, x - 9f, y - 40f, x + 9f, y - 24f, 0f, lighten(col, 0.15f))
                val vp = Gl.p(); vp.color = 0xFF14101C.toInt()
                c.drawRect(x - 7f + f * 2f, y - 34f, x + 7f + f * 2f, y - 31f, vp)
                line(c, x, y - 40f, x, y - 46f, 3f, 0xFFE05060.toInt())
                gloss(c, 1, x + f * 8f - 5f, y - 34f, x + f * 8f + 5f, y - 8f, 4f, 0xFF4C6A9C.toInt())
                gloss(c, 0, x + f * 8f - 2.5f, y - 24f, x + f * 8f + 2.5f, y - 18f, 0f, 0xFFFFD060.toInt())
            }
            Adv.E_GHOST -> {
                val chase = e.st == 1
                val al = if (chase) 235 else 185
                val w = 12f
                pth.reset()
                pth.moveTo(x - w, y - 4f)
                pth.quadTo(x - w, y - 28f, x, y - 28f)
                pth.quadTo(x + w, y - 28f, x + w, y - 4f)
                for (k in 0..3) { val wx = x + w - k * (w / 2f); pth.lineTo(wx - w / 4f, y + 3f * sin(time * 8f + k)); pth.lineTo(wx - w / 2f, y - 4f) }
                pth.close()
                glossPath(c, x - w, y - 28f, x + w, y + 3f, col, al)
                val ep = Gl.p(); ep.color = 0xFF2A2440.toInt()
                c.drawOval(rf.also { it.set(x - 7f, y - 20f, x - 2f, y - 12f) }, ep)
                c.drawOval(rf.also { it.set(x + 2f, y - 20f, x + 7f, y - 12f) }, ep)
                if (chase) glow(c, x, y - 14f, 22f, 0xFFA0B0FF.toInt(), 50)
            }
            else -> { // araña
                if (e.st == 0 || e.st == 1) line(c, x, 0f, x, y - 6f, 1.2f, Gl.white(130))
                for (k in intArrayOf(-1, 1)) for (j in 0 until 3) {
                    val lw = sin(time * 14f + j * 2f) * 2f
                    line(c, x + k * 4f, y - 6f, x + k * (11f + j * 2f), y - 11f + j * 4f + lw, 1.6f, 0xFF3A1E50.toInt())
                }
                gloss(c, 0, x - 8f, y - 12f, x + 8f, y, 0f, col)
                for (k in intArrayOf(-1, 1)) { val ep = Gl.p(); ep.color = 0xFFFF5060.toInt(); c.drawCircle(x + k * 3f, y - 7f, 1.6f, ep) }
            }
        }
    }

    private fun drawProj(c: Canvas, p: AdvProj, pal: IntArray) {
        if (!p.mine) {
            glow(c, p.x, p.y, p.r * 2.2f, 0xFFFF4060.toInt(), 80)
            gloss(c, 0, p.x - p.r, p.y - p.r, p.x + p.r, p.y + p.r, 0f, 0xFFFF6078.toInt())
            return
        }
        when (p.kind) {
            1 -> { val s = if (p.vx >= 0f) 1f else -1f; line(c, p.x - 9f * s, p.y, p.x + 9f * s, p.y, 3.4f, Color.WHITE); line(c, p.x - 2f * s, p.y - 3f, p.x - 2f * s, p.y + 3f, 2f, 0xFF9AB0D0.toInt()); glow(c, p.x, p.y, 14f, pal[1], 100) }
            2 -> { c.save(); c.rotate(p.t * 700f, p.x, p.y); gloss(c, 1, p.x - 7f, p.y - 7f, p.x + 7f, p.y + 7f, 2f, pal[2]); c.restore(); glow(c, p.x, p.y, 18f, pal[2], 70) }
            3 -> { Gl.arc(c, p.x, p.y, 8f, p.t * 600f, 250f, 3.4f, Color.WHITE); glow(c, p.x, p.y, 16f, pal[3], 90) }
            4 -> { gloss(c, 0, p.x - 5f, p.y - 5f, p.x + 5f, p.y + 5f, 0f, 0xFF60C0FF.toInt()); glow(c, p.x, p.y, 12f, 0xFF60C0FF.toInt(), 80) }
            5 -> { glow(c, p.x, p.y, 16f, 0xFFB080FF.toInt(), 130); gloss(c, 0, p.x - 5f, p.y - 5f, p.x + 5f, p.y + 5f, 0f, 0xFFC8A0FF.toInt()) }
            6 -> { c.save(); c.rotate(p.t * 1100f, p.x, p.y)
                for (k in 0 until 4) { val an = k * 1.5708f; pth.reset(); pth.moveTo(p.x, p.y); pth.lineTo(p.x + cos(an) * 7f, p.y + sin(an) * 7f); pth.lineTo(p.x + cos(an + 0.7f) * 3.5f, p.y + sin(an + 0.7f) * 3.5f); pth.close(); val pp = Gl.p(); pp.color = 0xFFE8F0FF.toInt(); c.drawPath(pth, pp) }
                c.restore(); glow(c, p.x, p.y, 10f, pal[1], 80) }
        }
    }

    private fun sgn(v: Float) = if (v >= 0f) 1f else -1f

    private fun drawHaz(c: Canvas, h: AdvHaz) {
        if (h.warn > 0f) {
            val pp = Gl.p()
            pp.color = Gl.alpha(if (h.mine) 0xFF80C0FF.toInt() else 0xFFFF3040.toInt(), 60 + (50 * sin(time * 30f)).toInt())
            c.drawRect(h.x0, h.y0, h.x1, h.y1, pp)
            return
        }
        val pp = Gl.p()
        when (h.kind) {
            2 -> {
                val f = 0.8f + 0.2f * sin(time * 18f + h.x0)
                pth.reset()
                val cx = (h.x0 + h.x1) / 2f
                pth.moveTo(h.x0, h.y1); pth.quadTo(h.x0 + 4f, h.y1 - (h.y1 - h.y0) * f, cx, h.y1 - (h.y1 - h.y0) * f * 1.3f); pth.quadTo(h.x1 - 4f, h.y1 - (h.y1 - h.y0) * f, h.x1, h.y1); pth.close()
                pp.color = 0xFFFF8020.toInt(); c.drawPath(pth, pp)
                glow(c, cx, h.y1 - 10f, 36f, 0xFFFFB040.toInt(), 110)
            }
            1 -> { // rayo en zigzag
                val cx = (h.x0 + h.x1) / 2f
                val col = if (h.mine) 0xFFCCE8FF.toInt() else Color.WHITE
                var yy = 0f
                var px0 = cx
                while (yy < h.y1) {
                    val nx = cx + (rnd.nextFloat() - 0.5f) * 16f
                    line(c, px0, yy, nx, yy + 22f, 4f, col)
                    px0 = nx; yy += 22f
                }
                glow(c, cx, h.y1 - 20f, 40f, if (h.mine) 0xFF80C0FF.toInt() else 0xFFFF6070.toInt(), 110)
            }
            4 -> {
                pth.reset(); pth.moveTo(h.x0, h.y1); pth.lineTo((h.x0 + h.x1) / 2f, h.y0); pth.lineTo(h.x1, h.y1); pth.close()
                glossPath(c, h.x0, h.y0, h.x1, h.y1, 0xFFFFD060.toInt())
                glow(c, (h.x0 + h.x1) / 2f, h.y1 - 6f, 20f, 0xFFFF8040.toInt(), 90)
            }
            else -> {
                pp.color = Gl.alpha(Color.WHITE, 235)
                c.drawRect(h.x0, h.y0, h.x1, h.y1, pp)
                glow(c, (h.x0 + h.x1) / 2f, (h.y0 + h.y1) / 2f, max(h.x1 - h.x0, h.y1 - h.y0) * 0.5f + 12f, 0xFFFF6070.toInt(), 80)
            }
        }
    }

    // ------------------------------------------------------------------ jugador
    private fun drawPlayer(c: Canvas, a: Adv, pal: IntArray) {
        for (t in trail) glow(c, t.x, t.y, 9f * (t.life / 0.22f) + 3f, pal[0], (95 * t.life / 0.22f).toInt())
        if (a.deadT > 0f) return
        val blink = a.invuln > 0f && a.potionT <= 0f && ((time * 18f).toInt() % 2 == 0)
        val gy = a.groundBelow(a.px, a.py - 1f)
        if (gy < AdvCfg.H) {
            val k = (1f - ((gy - a.py) / 90f)).coerceIn(0.3f, 1f)
            val sp = Gl.p(); sp.color = Gl.alpha(Color.BLACK, (80 * k).toInt())
            rf.set(a.px - 9f * k, gy - 2f, a.px + 9f * k, gy + 2f)
            c.drawOval(rf, sp)
        }
        val atkU = if (a.atkT > 0f) (0.34f - a.atkT) / 0.34f else -1f
        if (!blink) {
            var sy = 1f
            if (!a.onGround) sy = 1f + min(0.22f, abs(a.pvy) / 2400f)
            else if (squash > 0f) sy = 1f - 0.24f * squash
            else if (abs(a.pvx) > 60f) sy = 1f + 0.04f * sin(runPhase * 3f)
            val sx = 1f / sy
            // anticipación (se encoge hacia atrás), golpe y seguimiento del ataque
            var ap = 0f
            if (atkU >= 0f) ap = if (atkU < 0.2f) -atkU / 0.2f else if (atkU < 0.45f) (atkU - 0.2f) / 0.25f else max(0f, 1f - (atkU - 0.45f) / 0.4f)
            val lean = (a.pvx / 175f).coerceIn(-1.2f, 1.2f) * 7f + a.face * ap * 6f
            c.save()
            c.translate(a.px + a.face * ap * 2.5f, a.py)
            c.rotate(lean)
            c.scale(sx * (1f + 0.07f * abs(ap)), sy * (1f - 0.04f * abs(ap)))
            ball(c, 0f, -11f, 11f, pal[0])
            val look = if (a.pvy < -80f) -1.5f else if (a.pvy > 120f) 1.5f else 0f
            val ep = Gl.p(); ep.color = Color.WHITE
            c.drawCircle(a.face * 4f, -14f, 2.7f, ep)
            ep.color = Color.BLACK
            c.drawCircle(a.face * 5f, -14f + look * 0.4f, 1.3f, ep)
            if (a.invuln > 1.0f) { ep.color = Gl.white(150); c.drawCircle(0f, -11f, 11f, ep) }
            c.restore()
            if (a.potionT > 0f) glow(c, a.px, a.py - 15f, 28f, 0xFF60FFB0.toInt(), 110)
            if (a.boots) { line(c, a.px - 8f, a.py + 1f, a.px + 8f, a.py + 1f, 1.6f, 0xFF60E0FF.toInt()) }
        }
        if (atkU >= 0f) {
            val u = atkU
            val ext = if (u < 0.2f) -0.18f * sin(u / 0.2f * 3.1416f)
            else if (u < 0.5f) { val t = (u - 0.2f) / 0.3f; val q = 1f - t; (1f - q * q * q) * (1f + 0.08f * sin(t * 3.1416f)) }
            else { val t = (u - 0.5f) / 0.5f; max(0f, 1f - t * t) }
            val len = a.whipReach * ext
            val x0 = a.px + a.face * 8f
            val y0 = a.py - 18f
            val col = if (a.whip > 0) 0xFFFFD060.toInt() else Color.WHITE
            var px0 = x0
            var py0 = y0
            for (k in 1..9) {
                val t = k / 9f
                val x1 = x0 + a.face * len * t
                val y1 = y0 + sin(t * 9f - time * 30f) * 3f * (1f - t * 0.4f) * (0.4f + abs(ext))
                Gl.line(c, px0, py0, x1, y1, 4f - 2.6f * t, Gl.alpha(col, 245))
                Gl.line(c, px0, py0 + 1.5f, x1, y1 + 1.5f, 6.5f - 4f * t, Gl.alpha(pal[1], 70))
                px0 = x1; py0 = y1
            }
            glow(c, px0, py0, 18f, col, 160)
            if (u in 0.2f..0.62f) {
                val fa = (1f - abs(u - 0.38f) / 0.24f).coerceIn(0f, 1f)
                Gl.arc(c, x0, y0, a.whipReach * 0.92f, if (a.face > 0) -24f else 156f, 48f, 3.2f, Gl.alpha(col, (190 * fa).toInt()))
            }
            if (a.whip >= 2) for (k in 0 until 3) glow(c, x0 + a.face * len * (0.3f + 0.3f * k), y0, 8f, 0xFFFF9040.toInt(), 90)
        }
    }

    // ------------------------------------------------------------------ jefes (uno distinto por mundo)
    private fun drawBoss(c: Canvas, a: Adv, pal: IntArray) {
        if (!a.bossStarted) return
        val d = a.bdef
        val x = a.bx
        val gy = AdvCfg.GROUND
        if (a.bstate == Adv.BS_DEAD && a.bst < 1.6f && ((time * 20f).toInt() % 2 == 0)) return
        val fade = if (a.bstate == Adv.BS_INTRO) (1f - a.bst / 1.6f).coerceIn(0.1f, 1f) else 1f
        val al = (255 * fade).toInt()
        val col = if (a.bflash > 0f) Color.WHITE else opaque(pal[world % 4])
        val acc = opaque(pal[(world + 1) % 4])
        val breathe = 1f + 0.025f * sin(time * 2.6f)
        val shake = if (a.bstate == Adv.BS_TELE) sin(time * 70f) * 1.6f else 0f
        val lunge = if (a.bstate == Adv.BS_ACT && a.bpat == AdvCfg.P_CHARGE) 1.06f else 1f
        val sp = Gl.p(); sp.color = Gl.alpha(Color.BLACK, 90)
        rf.set(x - d.w * 0.6f, gy - 3f, x + d.w * 0.6f, gy + 3f)
        c.drawOval(rf, sp)
        glow(c, x, gy - d.h / 2f, d.w * 1.4f, acc, (50 * fade).toInt())
        if (a.bstate == Adv.BS_TELE) glow(c, x, gy - d.h / 2f, d.w * 1.7f, 0xFFFF3040.toInt(), (70 + 60 * sin(time * 30f)).toInt().coerceIn(0, 140))
        if (a.benraged) glow(c, x, gy - d.h / 2f, d.w * 1.2f, 0xFFFF5030.toInt(), 45)
        var bsx = breathe * lunge
        var bsy = breathe * lunge
        var rot = 0f
        if (a.bstate == Adv.BS_TELE) {
            val pr = (1f - a.bst / max(0.05f, a.bteleMax)).coerceIn(0f, 1f)
            val ez = pr * pr
            when (a.bpat) {
                AdvCfg.P_SLAM, AdvCfg.P_WAVE, AdvCfg.P_ULT -> { bsy *= 1f - 0.17f * ez; bsx *= 1f + 0.11f * ez }
                AdvCfg.P_CHARGE -> { rot = -a.bface * 9f * ez; bsy *= 1f - 0.08f * ez; bsx *= 1f + 0.06f * ez }
                else -> { bsy *= 1f + 0.08f * ez; bsx *= 1f - 0.04f * ez }
            }
        } else if (a.bstate == Adv.BS_ACT) {
            if (a.bpat == AdvCfg.P_CHARGE) rot = a.bface * 7f
            if (a.bpat == AdvCfg.P_SLAM && a.bz > 1f) { bsy *= 1.14f; bsx *= 0.92f }
        } else if (a.bstate == Adv.BS_STUN) rot = sin(time * 16f) * 4f
        if (bossSquash > 0f) { bsy *= 1f - 0.18f * bossSquash; bsx *= 1f + 0.12f * bossSquash }
        c.save()
        c.translate(x + shake, gy - a.bz)
        c.rotate(rot)
        c.scale(bsx, bsy)
        val f = a.bface
        val w = d.w
        val h = d.h
        when (world) {
            0 -> { // guardián de cristal: rombo con esquirlas
                pth.reset(); pth.moveTo(0f, -h); pth.lineTo(w / 2f, -h * 0.5f); pth.lineTo(0f, 0f); pth.lineTo(-w / 2f, -h * 0.5f); pth.close()
                glossPath(c, -w / 2f, -h, w / 2f, 0f, col, al)
                pth.reset(); pth.moveTo(0f, -h); pth.lineTo(w * 0.18f, -h * 0.5f); pth.lineTo(0f, 0f); pth.close()
                val pp = Gl.p(); pp.color = Gl.white(50); c.drawPath(pth, pp)
                for (k in 0 until 3) { val an = time * 1.6f + k * 2.09f; gloss(c, 1, cos(an) * w * 0.75f - 4f, -h * 0.55f + sin(an) * 12f - 7f, cos(an) * w * 0.75f + 4f, -h * 0.55f + sin(an) * 12f + 7f, 2f, acc, al) }
                gloss(c, 0, -9f, -h * 0.62f, 9f, -h * 0.42f, 0f, Color.WHITE, al)
                val ip = Gl.p(); ip.color = 0xFF14101C.toInt(); c.drawCircle(f * 3f, -h * 0.52f, 4f, ip)
            }
            1 -> { // reina: vestido, corona y cintas de aurora
                for (k in 0 until 3) { val wv = sin(time * 3f + k) * 8f; line(c, -w * 0.2f, -h * 0.85f, -w * 0.9f - k * 6f, -h * 0.4f + wv, 4f, Gl.alpha(opaque(pal[(k + 2) % 4]), al)); line(c, w * 0.2f, -h * 0.85f, w * 0.9f + k * 6f, -h * 0.4f - wv, 4f, Gl.alpha(opaque(pal[(k + 2) % 4]), al)) }
                pth.reset(); pth.moveTo(-w / 2f, 0f); pth.lineTo(w / 2f, 0f); pth.lineTo(w * 0.12f, -h * 0.6f); pth.lineTo(-w * 0.12f, -h * 0.6f); pth.close()
                glossPath(c, -w / 2f, -h * 0.6f, w / 2f, 0f, col, al)
                gloss(c, 1, -w * 0.16f, -h * 0.82f, w * 0.16f, -h * 0.56f, 6f, lighten(col, 0.2f), al)
                gloss(c, 0, -w * 0.2f, -h * 1.0f, w * 0.2f, -h * 0.74f, 0f, 0xFFF0D8C8.toInt(), al)
                for (k in -1..1) { pth.reset(); pth.moveTo(k * 7f - 3f, -h * 0.99f); pth.lineTo(k * 7f, -h * 1.12f + abs(k) * 4f); pth.lineTo(k * 7f + 3f, -h * 0.99f); pth.close(); glossPath(c, k * 7f - 3f, -h * 1.12f, k * 7f + 3f, -h * 0.99f, acc, al) }
                eyes(c, f * 1.5f, -h * 0.88f, f, 2.2f, Color.WHITE)
            }
            2 -> { // kraken
                for (k in -3..3) { val wv = sin(time * 3f + k) * 6f; line(c, k * w * 0.13f, -h * 0.35f, k * w * 0.2f + wv, -3f, 7f - abs(k) * 0.8f, Gl.alpha(Gl.darken(col, 0.8f), al)) }
                gloss(c, 0, -w / 2f, -h, w / 2f, -h * 0.25f, 0f, col, al)
                gloss(c, 0, -w * 0.38f, -h * 0.9f, -w * 0.18f, -h * 0.72f, 0f, acc, al)
                gloss(c, 0, w * 0.18f, -h * 0.9f, w * 0.38f, -h * 0.72f, 0f, acc, al)
                eyes(c, f * 2f, -h * 0.62f, f, 4.2f, Color.WHITE)
            }
            3 -> { // rey neón
                gloss(c, 1, -w * 0.3f, -h * 0.78f, w * 0.3f, 0f, 8f, col, al)
                gloss(c, 1, -w * 0.44f, -h * 0.74f, -w * 0.28f, -h * 0.3f, 5f, Gl.darken(col, 0.8f), al)
                gloss(c, 1, w * 0.28f, -h * 0.74f, w * 0.44f, -h * 0.3f, 5f, Gl.darken(col, 0.8f), al)
                gloss(c, 0, -w * 0.22f, -h, w * 0.22f, -h * 0.72f, 0f, lighten(col, 0.15f), al)
                pth.reset(); pth.moveTo(-w * 0.22f, -h * 0.98f); for (k in 0..4) { pth.lineTo(-w * 0.22f + k * w * 0.11f, -h * (if (k % 2 == 0) 1.14f else 1.02f)) }; pth.lineTo(w * 0.22f, -h * 0.98f); pth.close()
                glossPath(c, -w * 0.22f, -h * 1.14f, w * 0.22f, -h * 0.98f, acc, al)
                Gl.arc(c, 0f, -h * 0.5f, w * 0.14f, 0f, 360f, 3f, Gl.alpha(Color.WHITE, 220))
                eyes(c, f * 2f, -h * 0.87f, f, 2.4f, 0xFFFFF4C0.toInt())
            }
            4 -> { // coloso de cristal: bloques apilados
                gloss(c, 1, -w * 0.38f, -h * 0.5f, w * 0.38f, 0f, 6f, Gl.darken(col, 0.85f), al)
                gloss(c, 1, -w * 0.46f, -h * 0.84f, w * 0.46f, -h * 0.44f, 8f, col, al)
                gloss(c, 1, -w * 0.28f, -h, w * 0.28f, -h * 0.8f, 6f, lighten(col, 0.1f), al)
                gloss(c, 1, -w * 0.62f, -h * 0.7f, -w * 0.44f, -h * 0.28f, 6f, Gl.darken(col, 0.8f), al)
                gloss(c, 1, w * 0.44f, -h * 0.7f, w * 0.62f, -h * 0.28f, 6f, Gl.darken(col, 0.8f), al)
                pth.reset(); pth.moveTo(0f, -h * 0.74f); pth.lineTo(8f, -h * 0.62f); pth.lineTo(0f, -h * 0.5f); pth.lineTo(-8f, -h * 0.62f); pth.close()
                glossPath(c, -8f, -h * 0.74f, 8f, -h * 0.5f, acc, al)
                eyes(c, f * 2f, -h * 0.91f, f, 2.6f, 0xFFFFF4C0.toInt())
            }
            5 -> { // señor del ocaso: capa y cuernos
                pth.reset(); pth.moveTo(-w * 0.55f, 0f); pth.lineTo(w * 0.55f, 0f); pth.lineTo(w * 0.2f, -h * 0.78f); pth.lineTo(-w * 0.2f, -h * 0.78f); pth.close()
                glossPath(c, -w * 0.55f, -h * 0.78f, w * 0.55f, 0f, Gl.darken(col, 0.8f), al)
                gloss(c, 0, -w * 0.24f, -h * 0.98f, w * 0.24f, -h * 0.68f, 0f, col, al)
                for (k in intArrayOf(-1, 1)) { pth.reset(); pth.moveTo(k * w * 0.16f, -h * 0.9f); pth.lineTo(k * w * 0.4f, -h * 1.12f); pth.lineTo(k * w * 0.26f, -h * 0.82f); pth.close(); glossPath(c, -w * 0.4f, -h * 1.12f, w * 0.4f, -h * 0.82f, acc, al) }
                val ip = Gl.p(); ip.color = 0xFFFFE070.toInt(); c.drawCircle(f * 2f - 5f, -h * 0.83f, 2.6f, ip); c.drawCircle(f * 2f + 5f, -h * 0.83f, 2.6f, ip)
                line(c, w * 0.5f * f, 0f, w * 0.5f * f, -h * 0.9f, 3f, Gl.alpha(0xFFB0B0C0.toInt(), al))
            }
            6 -> { // devorador: boca con dientes
                gloss(c, 0, -w / 2f, -h, w / 2f, 0f, 0f, col, al)
                val mp = Gl.p(); mp.color = 0xFF18101C.toInt()
                rf.set(-w * 0.34f, -h * 0.5f, w * 0.34f, -h * 0.08f); c.drawOval(rf, mp)
                val open = 0.5f + 0.5f * sin(time * 3f)
                for (k in 0 until 6) { val tx = -w * 0.28f + k * w * 0.112f; pth.reset(); pth.moveTo(tx - 4f, -h * 0.46f); pth.lineTo(tx, -h * (0.46f - 0.1f - open * 0.04f)); pth.lineTo(tx + 4f, -h * 0.46f); pth.close(); val tp = Gl.p(); tp.color = 0xFFF4F4F0.toInt(); c.drawPath(pth, tp) }
                eyes(c, f * 2f, -h * 0.72f, f, 3.6f, 0xFFFFF4C0.toInt())
            }
            7 -> { // titán de lava: golem
                gloss(c, 1, -w * 0.4f, -h * 0.72f, w * 0.4f, 0f, 8f, col, al)
                gloss(c, 1, -w * 0.58f, -h * 0.7f, -w * 0.38f, -h * 0.14f, 6f, Gl.darken(col, 0.8f), al)
                gloss(c, 1, w * 0.38f, -h * 0.7f, w * 0.58f, -h * 0.14f, 6f, Gl.darken(col, 0.8f), al)
                gloss(c, 1, -w * 0.24f, -h, w * 0.24f, -h * 0.68f, 6f, lighten(col, 0.08f), al)
                line(c, -w * 0.2f, -h * 0.6f, -w * 0.05f, -h * 0.3f, 2.4f, 0xFFFFB040.toInt()); line(c, w * 0.2f, -h * 0.55f, w * 0.08f, -h * 0.22f, 2.4f, 0xFFFFB040.toInt()); line(c, 0f, -h * 0.7f, w * 0.05f, -h * 0.45f, 2.4f, 0xFFFFB040.toInt())
                glow(c, 0f, -h * 0.45f, w * 0.5f, 0xFFFF7020.toInt(), 60)
                eyes(c, f * 2f, -h * 0.84f, f, 2.8f, 0xFFFFE070.toInt())
            }
            8 -> { // ent ancestral
                for (k in intArrayOf(-1, 1)) { line(c, k * w * 0.18f, -h * 0.6f, k * w * 0.62f, -h * (0.78f + 0.04f * sin(time * 2f + k)), 7f, Gl.alpha(0xFF6A4A2A.toInt(), al)); gloss(c, 0, k * w * 0.62f - 9f, -h * 0.84f - 9f, k * w * 0.62f + 9f, -h * 0.84f + 9f, 0f, 0xFF4CB04C.toInt(), al) }
                gloss(c, 1, -w * 0.28f, -h * 0.86f, w * 0.28f, 0f, 10f, 0xFF7A5430.toInt(), al)
                for (k in 0 until 4) gloss(c, 0, -w * 0.4f + k * w * 0.27f - 12f, -h * (0.98f - 0.05f * (k % 2)) - 12f, -w * 0.4f + k * w * 0.27f + 12f, -h * (0.98f - 0.05f * (k % 2)) + 12f, 0f, if (a.bflash > 0f) Color.WHITE else 0xFF3CA040.toInt(), al)
                eyes(c, f * 2f, -h * 0.66f, f, 2.8f, 0xFFFFE070.toInt())
            }
            9 -> { // emperatriz de hielo
                pth.reset(); pth.moveTo(-w * 0.55f, 0f); pth.lineTo(-w * 0.4f, -h * 0.2f); pth.lineTo(-w * 0.3f, -h * 0.04f); pth.lineTo(0f, -h * 0.22f); pth.lineTo(w * 0.3f, -h * 0.04f); pth.lineTo(w * 0.4f, -h * 0.2f); pth.lineTo(w * 0.55f, 0f); pth.lineTo(w * 0.14f, -h * 0.62f); pth.lineTo(-w * 0.14f, -h * 0.62f); pth.close()
                glossPath(c, -w * 0.55f, -h * 0.62f, w * 0.55f, 0f, col, al)
                gloss(c, 0, -w * 0.2f, -h * 0.94f, w * 0.2f, -h * 0.64f, 0f, 0xFFEAF6FF.toInt(), al)
                for (k in -2..2) { pth.reset(); pth.moveTo(k * 6f - 3f, -h * 0.9f); pth.lineTo(k * 6f, -h * (1.08f + 0.04f * (2 - abs(k)))); pth.lineTo(k * 6f + 3f, -h * 0.9f); pth.close(); glossPath(c, k * 6f - 3f, -h * 1.16f, k * 6f + 3f, -h * 0.9f, 0xFFBFE8FF.toInt(), al) }
                line(c, w * 0.5f * f, 0f, w * 0.5f * f, -h * 0.95f, 3f, Gl.alpha(0xFFBFE8FF.toInt(), al)); glow(c, w * 0.5f * f, -h * 0.97f, 10f, 0xFF80D0FF.toInt(), 130)
                eyes(c, f * 1.5f, -h * 0.8f, f, 2.2f, 0xFF60C0FF.toInt())
            }
            10 -> { // oráculo: ojo flotante
                val hv = -14f + sin(time * 2f) * 4f
                for (k in 0 until 3) { c.save(); c.rotate(time * (40f + 25f * k) * (if (k % 2 == 0) 1f else -1f), 0f, -h * 0.5f + hv); Gl.arc(c, 0f, -h * 0.5f + hv, w * (0.52f + 0.1f * k), 20f * k, 250f, 2.4f, Gl.alpha(acc, 200)); c.restore() }
                gloss(c, 0, -w * 0.46f, -h * 0.82f + hv, w * 0.46f, -h * 0.18f + hv, 0f, 0xFFF0F0FF.toInt(), al)
                gloss(c, 0, -w * 0.22f + f * 6f, -h * 0.66f + hv, w * 0.22f + f * 6f, -h * 0.34f + hv, 0f, col, al)
                val ip = Gl.p(); ip.color = 0xFF14101C.toInt(); c.drawCircle(f * 9f, -h * 0.5f + hv, w * 0.09f, ip)
            }
            else -> { // el espectro
                for (k in 2 downTo 0) {
                    val sc2 = 1f - k * 0.12f
                    pth.reset(); pth.moveTo(-w * 0.5f * sc2, 0f); pth.lineTo(-w * 0.5f * sc2, -h * 0.6f * sc2)
                    pth.quadTo(-w * 0.5f * sc2, -h * 0.96f * sc2, 0f, -h * 0.96f * sc2); pth.quadTo(w * 0.5f * sc2, -h * 0.96f * sc2, w * 0.5f * sc2, -h * 0.6f * sc2); pth.lineTo(w * 0.5f * sc2, 0f)
                    for (j in 0 until 5) { pth.lineTo(w * 0.5f * sc2 - (j + 0.5f) * w * sc2 / 5f, -6f + sin(time * 5f + j + k) * 5f); pth.lineTo(w * 0.5f * sc2 - (j + 1f) * w * sc2 / 5f, 0f) }
                    pth.close()
                    glossPath(c, -w * 0.5f, -h, w * 0.5f, 0f, if (k == 0) col else Gl.darken(opaque(pal[(world + k) % 4]), 0.7f), al)
                }
                gloss(c, 1, -w * 0.26f, -h * 0.82f, w * 0.26f, -h * 0.52f, 10f, 0xFFF4F0E8.toInt(), al)
                val ip = Gl.p(); ip.color = 0xFF14101C.toInt()
                c.drawOval(rf.also { it.set(-w * 0.18f + f * 2f, -h * 0.74f, -w * 0.06f + f * 2f, -h * 0.6f) }, ip)
                c.drawOval(rf.also { it.set(w * 0.06f + f * 2f, -h * 0.74f, w * 0.18f + f * 2f, -h * 0.6f) }, ip)
                for (k in 0 until 4) line(c, -w * 0.5f + k * w * 0.33f, -h * 0.5f, -w * 0.5f + k * w * 0.33f + sin(time * 2f + k) * 8f, -h * 0.1f, 2f, Gl.alpha(0xFFB0B0C0.toInt(), al))
            }
        }
        c.restore()
        if (a.bstate == Adv.BS_STUN) for (k in 0 until 3) {
            val an = time * 6f + k * 2.1f
            glow(c, x + cos(an) * 18f, gy - d.h - 6f + sin(an) * 5f, 7f, 0xFFFFE070.toInt(), 200)
        }
    }

    // ------------------------------------------------------------------ interfaz
    private fun drawHud(c: Canvas, a: Adv, pal: IntArray) {
        val bwid = AdvCfg.MAXHP * 12f
        var pp = Gl.p(); pp.color = Gl.white(40)
        c.drawRoundRect(rf.also { it.set(18f, 14f, 18f + bwid, 28f) }, 5f, 5f, pp)
        if (hpChip > hpDisp + 0.05f) { pp = Gl.p(); pp.color = 0xFFFFC0C8.toInt(); c.drawRoundRect(rf.also { it.set(18f, 14f, 18f + bwid * hpChip / AdvCfg.MAXHP, 28f) }, 5f, 5f, pp) }
        if (hpDisp > 0.1f) gloss(c, 1, 18f, 14f, 18f + max(6f, bwid * hpDisp / AdvCfg.MAXHP), 28f, 5f, 0xFFFF4A60.toInt())
        for (i in 1 until AdvCfg.MAXHP) line(c, 18f + i * 12f, 15f, 18f + i * 12f, 27f, 1f, Gl.alpha(Color.BLACK, 70))
        val hs = 1f + 0.4f * heartPop
        heart(c, 24f, 42f, 0.9f * hs, 0xFFFF4070.toInt())
        Gl.text(c, "${a.hearts}", 36f, 49f, 15f * hs, Gl.medium, Gl.white(235), 0f, null, 0, Paint.Align.LEFT)
        gloss(c, 1, 18f, 58f, 44f, 84f, 7f, if (a.sub == 0) 0xFF505868.toInt() else 0xFF70C8FF.toInt())
        if (a.sub != 0) Gl.text(c, AdvCfg.subLetters.substring(a.sub, a.sub + 1), 31f, 77f, 17f, Gl.medium, Color.BLACK)
        Gl.text(c, if (a.sub == 0) "Sin arma" else "${AdvCfg.subNames[a.sub]} (${AdvCfg.subCost[a.sub]})", 50f, 76f, 12f, Gl.regular, Gl.white(190), 0f, null, 0, Paint.Align.LEFT)
        var ix = 18f
        if (a.boots) { gloss(c, 1, ix, 90f, ix + 22f, 106f, 4f, 0xFF40C0F0.toInt()); ix += 28f }
        if (a.whip > 0) { gloss(c, 1, ix, 90f, ix + 22f, 106f, 4f, 0xFFFFD060.toInt()); Gl.text(c, "L${a.whip}", ix + 11f, 103f, 11f, Gl.medium, Color.BLACK); ix += 28f }
        if (a.potionT > 0f) { pp = Gl.p(); pp.color = Gl.white(40); c.drawRoundRect(rf.also { it.set(ix, 94f, ix + 50f, 102f) }, 4f, 4f, pp); pp.color = 0xFF60FFB0.toInt(); c.drawRoundRect(rf.also { it.set(ix, 94f, ix + 50f * (a.potionT / 9f).coerceIn(0f, 1f), 102f) }, 4f, 4f, pp) }
        if (a.bibleT > 0f) { pp = Gl.p(); pp.color = 0xFFFFE070.toInt(); c.drawRoundRect(rf.also { it.set(18f, 110f, 18f + 50f * (a.bibleT / 4.5f).coerceIn(0f, 1f), 116f) }, 3f, 3f, pp) }
        val bw = min(180f, vw * 0.24f)
        val x0 = vw - 60f - bw
        pp = Gl.p(); pp.color = Gl.white(45)
        c.drawRoundRect(rf.also { it.set(x0, 26f, x0 + bw, 32f) }, 3f, 3f, pp)
        for (ck in a.checks) { pp = Gl.p(); pp.color = Gl.white(110); c.drawRect(x0 + bw * (ck / a.arenaX).coerceIn(0f, 1f) - 1f, 22f, x0 + bw * (ck / a.arenaX).coerceIn(0f, 1f) + 1f, 36f, pp) }
        val fx = x0 + bw * (a.px / a.arenaX).coerceIn(0f, 1f)
        glow(c, fx, 29f, 9f, pal[0], 160)
        pp = Gl.p(); pp.color = Color.WHITE; c.drawCircle(fx, 29f, 4f, pp)
        if (a.bossStarted && a.bstate != Adv.BS_INTRO) {
            val bb = min(260f, vw * 0.36f)
            val bx0 = vw / 2f - bb / 2f
            Gl.text(c, a.bdef.name, vw / 2f, 15f, 12f, Gl.medium, Gl.white(220))
            pp = Gl.p(); pp.color = Gl.white(40)
            c.drawRoundRect(rf.also { it.set(bx0, 20f, bx0 + bb, 30f) }, 5f, 5f, pp)
            if (bossChip > bossDisp + 0.003f) { pp = Gl.p(); pp.color = 0xFFFFE0A0.toInt(); c.drawRoundRect(rf.also { it.set(bx0, 20f, bx0 + bb * bossChip, 30f) }, 5f, 5f, pp) }
            if (bossDisp > 0.002f) gloss(c, 1, bx0, 20f, bx0 + max(6f, bb * bossDisp), 30f, 5f, pal[0])
        }
        pp = Gl.p(); pp.color = Gl.white(50)
        c.drawCircle(vw - 30f, 32f, 20f, pp)
        pp.color = Gl.white(220)
        c.drawRect(vw - 36f, 24f, vw - 31f, 40f, pp)
        c.drawRect(vw - 27f, 24f, vw - 22f, 40f, pp)
    }

    private fun drawControls(c: Canvas) {
        for (b in buttons()) {
            if (b.id == "P") continue
            val held = ptrs.containsValue(b.id)
            c.save()
            if (held) c.scale(0.92f, 0.92f, b.x, b.y)
            if (held) glow(c, b.x, b.y, b.r * 1.6f, Color.WHITE, 70)
            var pp = Gl.p()
            pp.color = Gl.white(if (held) 95 else 40)
            c.drawCircle(b.x, b.y, b.r, pp)
            Gl.arc(c, b.x, b.y, b.r, 0f, 360f, 2f, Gl.white(if (held) 230 else 110))
            pp = Gl.p()
            pp.color = Gl.white(235)
            val r = b.r
            pth.reset()
            when (b.id) {
                "L" -> { pth.moveTo(b.x - r * 0.4f, b.y); pth.lineTo(b.x + r * 0.25f, b.y - r * 0.42f); pth.lineTo(b.x + r * 0.25f, b.y + r * 0.42f) }
                "R" -> { pth.moveTo(b.x + r * 0.4f, b.y); pth.lineTo(b.x - r * 0.25f, b.y - r * 0.42f); pth.lineTo(b.x - r * 0.25f, b.y + r * 0.42f) }
                "J" -> { pth.moveTo(b.x, b.y - r * 0.42f); pth.lineTo(b.x + r * 0.45f, b.y + r * 0.3f); pth.lineTo(b.x - r * 0.45f, b.y + r * 0.3f) }
                "S" -> { for (k in 0 until 8) { val an = k * 0.785f - 1.5708f; val rr = if (k % 2 == 0) r * 0.55f else r * 0.2f; if (k == 0) pth.moveTo(b.x + cos(an) * rr, b.y + sin(an) * rr) else pth.lineTo(b.x + cos(an) * rr, b.y + sin(an) * rr) } }
                else -> {}
            }
            if (b.id == "A") {
                line(c, b.x - r * 0.38f, b.y + r * 0.38f, b.x + r * 0.42f, b.y - r * 0.42f, 3.6f, Gl.white(240))
                line(c, b.x - r * 0.46f, b.y + r * 0.04f, b.x - r * 0.04f, b.y + r * 0.46f, 3.4f, Gl.white(240))
                line(c, b.x - r * 0.46f, b.y + r * 0.46f, b.x - r * 0.3f, b.y + r * 0.3f, 3.6f, Gl.white(240))
            } else { pth.close(); c.drawPath(pth, pp) }
            c.restore()
        }
    }

    private fun panelBox(c: Canvas, pal: IntArray, l: Float, t: Float, r: Float, b: Float) {
        Gl.panel(c, l, t, r, b, 18f, 1f, pal[0])
    }

    private fun drawStory(c: Canvas, pal: IntArray) {
        panelBox(c, pal, vw / 2f - 280f, 50f, vw / 2f + 280f, 310f)
        Gl.text(c, "MUNDO ${world + 1}: ${Worlds.names[world]}", vw / 2f, 92f, 22f, Gl.medium, Gl.white(245), 0.08f)
        Gl.text(c, "Jefe: ${AdvCfg.bosses[world].name}", vw / 2f, 118f, 15f, Gl.regular, pal[1])
        wrapText(c, AdvCfg.worldIntro[world], vw / 2f, 156f, 500f, 17f, 24f)
        Gl.text(c, "Botón espada: ataque normal   Botón estrella: arma especial (gasta corazones)", vw / 2f, 262f, 12f, Gl.regular, Gl.white(150))
        Gl.text(c, "Toca para empezar", vw / 2f, 292f, 15f, Gl.regular, Gl.white((150 + 90 * sin(time * 4f)).toInt()))
    }

    private fun drawPause(c: Canvas, pal: IntArray) {
        panelBox(c, pal, vw / 2f - 160f, 80f, vw / 2f + 160f, 270f)
        Gl.text(c, "PAUSA", vw / 2f, 125f, 24f, Gl.medium, Gl.white(245), 0.14f)
        Gl.panel(c, vw / 2f - 120f, 139f, vw / 2f + 120f, 191f, 26f, 1f, pal[1])
        Gl.text(c, "Continuar", vw / 2f, 172f, 20f, Gl.medium, Gl.white(245))
        Gl.panel(c, vw / 2f - 120f, 199f, vw / 2f + 120f, 251f, 26f, 1f, pal[2])
        Gl.text(c, "Salir", vw / 2f, 232f, 20f, Gl.medium, Gl.white(245))
    }

    private var savedCount = 0

    private fun drawWin(c: Canvas, pal: IntArray) {
        val a = ((phT - 0.8f) / 0.6f).coerceIn(0f, 1f)
        if (a <= 0f) return
        panelBox(c, pal, vw / 2f - 280f, 60f, vw / 2f + 280f, 300f)
        Gl.text(c, "¡${Worlds.names[world]} salvado!", vw / 2f, 108f, 26f, Gl.medium, Gl.white(245), 0.06f)
        wrapText(c, AdvCfg.worldOutro[world], vw / 2f, 148f, 500f, 18f, 25f)
        if (savedCount >= AdvCfg.WORLDS) wrapText(c, AdvCfg.finalText, vw / 2f, 196f, 500f, 15f, 21f)
        else Gl.text(c, "Mundos salvados: $savedCount/${AdvCfg.WORLDS}", vw / 2f, 214f, 15f, Gl.regular, pal[1])
        Gl.text(c, "Toca para volver", vw / 2f, 282f, 15f, Gl.regular, Gl.white((150 + 90 * sin(time * 4f)).toInt()))
    }

    private fun wrapText(c: Canvas, s: String, cx: Float, y0: Float, maxW: Float, size: Float, lh: Float) {
        val words = s.split(" ")
        var line = ""
        var y = y0
        for (wd in words) {
            val t = if (line.isEmpty()) wd else "$line $wd"
            if (Gl.textWidth(t, size, Gl.regular) > maxW && line.isNotEmpty()) {
                Gl.text(c, line, cx, y, size, Gl.regular, Gl.white(235))
                y += lh
                line = wd
            } else line = t
        }
        if (line.isNotEmpty()) Gl.text(c, line, cx, y, size, Gl.regular, Gl.white(235))
    }
}
