package com.nioro.spectrumcolor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Dibujo, controles táctiles y flujo (historia, pausa, victoria) del modo Aventura. Va en horizontal. */
class AdvUi(
    private val ball: (Canvas, Float, Float, Float, Int) -> Unit,
    private val sfx: (Int) -> Unit,
    private val onWin: (Int) -> Unit,
    private val onExit: () -> Unit
) {
    private class Pt(var x: Float, var y: Float, var vx: Float, var vy: Float, val col: Int, val sz: Float, var life: Float, val max: Float)
    private class Btn(val id: String, val x: Float, val y: Float, val r: Float)

    var adv: Adv? = null
    var world = 0
    private var phase = 0 // 0 historia, 1 jugando, 2 pausa, 3 victoria
    private var phT = 0f
    private val inp = AdvInput()
    private val parts = ArrayList<Pt>()
    private val ptrs = HashMap<Int, String>()
    private var winSent = false
    private var time = 0f
    private var vw = 760f
    private var flash = 0f
    private val rnd = java.util.Random(5)

    val active: Boolean get() = adv != null

    fun start(w: Int, viewW: Float, god: Boolean) {
        world = w
        vw = viewW
        adv = Adv(w, viewW, god)
        phase = 0
        phT = 0f
        winSent = false
        parts.clear()
        ptrs.clear()
        inp.left = false; inp.right = false; inp.jumpHeld = false
    }

    fun stop() { adv = null; ptrs.clear() }

    fun pauseToggle() {
        if (phase == 1) phase = 2 else if (phase == 2) phase = 1
    }

    /** Botón "atrás": pausa o continúa. Devuelve true si sale. */
    fun back(): Boolean {
        return when (phase) {
            1 -> { phase = 2; false }
            2 -> { stop(); onExit(); true }
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
                            "P" -> phase = 2
                        }
                    }
                }
            }
        }
        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) ptrs.clear()
        else if (am == MotionEvent.ACTION_POINTER_UP) ptrs.remove(e.getPointerId(ai))
        else if (am == MotionEvent.ACTION_MOVE && phase == 1) {
            // deslizar el pulgar entre ◀ y ▶
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
        if (phase == 1 || phase == 3) {
            if (phase == 3) { inp.left = false; inp.right = false; inp.jumpHeld = false }
            a.step(dt, inp)
            handleEvents(a)
            if (a.won && phase == 1) { phase = 3; phT = 0f }
            if (phase == 3 && !winSent) { winSent = true; onWin(world) }
        } else inp.clearPressed()
        // partículas
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += 220f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    private fun burst(x: Float, y: Float, col: Int, n: Int, sp: Float) {
        for (i in 0 until n) {
            if (parts.size > 260) return
            val ang = rnd.nextFloat() * 6.2832f
            val v = sp * (0.3f + rnd.nextFloat())
            parts.add(Pt(x, y, cos(ang) * v, sin(ang) * v - 30f, col, 1.5f + rnd.nextFloat() * 2.5f, 0.5f + rnd.nextFloat() * 0.5f, 1f))
        }
    }

    private fun handleEvents(a: Adv) {
        val pal = Worlds.colors[world.coerceIn(0, Worlds.colors.size - 1)]
        for (e in a.events) {
            when (e.t) {
                Adv.EV_JUMP -> { burst(e.x, e.y, Gl.white(200), 4, 40f); sfx(Sfx.TAP) }
                Adv.EV_SWING -> sfx(Sfx.SWITCH)
                Adv.EV_HIT -> { burst(e.x, e.y, pal[1], 6, 110f); sfx(Sfx.TAP) }
                Adv.EV_HURT -> { burst(e.x, e.y, 0xFFFF5060.toInt(), 12, 150f); flash = 1f; sfx(Sfx.DIE) }
                Adv.EV_PICK -> { burst(e.x, e.y, 0xFFFFE070.toInt(), 8, 90f); sfx(Sfx.STAR) }
                Adv.EV_CANDLE -> { burst(e.x, e.y, 0xFFFFB040.toInt(), 12, 110f); sfx(Sfx.TAP) }
                Adv.EV_SPECIAL -> sfx(Sfx.SWITCH)
                Adv.EV_BOSSHIT -> { burst(e.x, e.y, Color.WHITE, 8, 130f); sfx(Sfx.TAP) }
                Adv.EV_BOSSDIE -> { for (k in 0 until 4) burst(e.x, e.y, pal[k % 4], 22, 220f); flash = 1f; sfx(Sfx.WIN) }
                Adv.EV_EDIE -> burst(e.x, e.y, pal[2], 10, 120f)
                Adv.EV_CHECK -> { burst(e.x, e.y - 10f, 0xFF80FFA0.toInt(), 10, 80f); sfx(Sfx.STAR) }
                Adv.EV_SLAM -> sfx(Sfx.DIE)
                Adv.EV_BOSSWARN -> sfx(Sfx.COUNT)
                Adv.EV_DIE -> { burst(e.x, e.y, pal[0], 26, 200f); sfx(Sfx.DIE) }
                Adv.EV_WIN -> { for (k in 0 until 4) burst(e.x, e.y, pal[k], 20, 200f); sfx(Sfx.WIN) }
            }
        }
        a.events.clear()
    }

    // ------------------------------------------------------------------ dibujo
    fun draw(c: Canvas, w: Int, h: Int) {
        val a = adv ?: return
        val s = h / AdvCfg.H
        val pal = Worlds.colors[world.coerceIn(0, Worlds.colors.size - 1)]
        val bg = Worlds.bg[world.coerceIn(0, Worlds.bg.size - 1)]
        c.save()
        c.scale(s, s)
        vw = w / s
        // fondo
        var pp = Gl.p()
        pp.shader = LinearGradient(0f, 0f, 0f, AdvCfg.H, intArrayOf(bg[0], bg[1], bg[2]), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, vw, AdvCfg.H, pp)
        drawFar(c, pal, bg, a.camX)
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
        c.restore()
        if (a.freezeT > 0f) {
            pp = Gl.p(); pp.color = Gl.alpha(0xFF60A0FF.toInt(), 36); c.drawRect(0f, 0f, vw, AdvCfg.H, pp)
        }
        if (flash > 0f) {
            pp = Gl.p(); pp.color = Gl.alpha(0xFFFF3040.toInt(), (70 * flash).toInt()); c.drawRect(0f, 0f, vw, AdvCfg.H, pp)
        }
        drawHud(c, a, pal)
        if (phase != 0) drawControls(c, pal)
        when (phase) {
            0 -> drawStory(c, pal)
            2 -> drawPause(c, pal)
            3 -> drawWin(c, pal)
        }
        c.restore()
    }

    private fun drawFar(c: Canvas, pal: IntArray, bg: IntArray, cam: Float) {
        val style = world % 3
        for (layer in 0 until 3) {
            val par = 0.12f + 0.16f * layer
            val base = 250f + layer * 28f
            val col = Gl.alpha(Gl.darken(pal[(layer + world) % 4], 0.35f + 0.1f * layer), 150 + layer * 30)
            val ox = -(cam * par) % 240f
            val pp = Gl.p()
            pp.color = col
            Gl.path.reset()
            Gl.path.moveTo(ox - 240f, AdvCfg.H)
            var x = ox - 240f
            var i = 0
            while (x < vw + 260f) {
                val hh = 40f + 34f * (0.5f + 0.5f * sin((i + layer * 3 + world) * 1.7f)) + layer * -6f
                when (style) {
                    0 -> { Gl.path.lineTo(x, base); Gl.path.lineTo(x + 30f, base - hh); Gl.path.lineTo(x + 60f, base) }
                    1 -> { Gl.path.lineTo(x, base); Gl.path.quadTo(x + 40f, base - hh * 1.6f, x + 80f, base) }
                    else -> { Gl.path.lineTo(x, base); Gl.path.lineTo(x, base - hh); Gl.path.lineTo(x + 36f, base - hh); Gl.path.lineTo(x + 36f, base) }
                }
                val step = when (style) { 0 -> 60f; 1 -> 80f; else -> 52f }
                x += step
                i++
            }
            Gl.path.lineTo(x, AdvCfg.H)
            Gl.path.close()
            c.drawPath(Gl.path, pp)
        }
        // luces flotantes
        for (i in 0 until 22) {
            val fx = ((i * 97f + time * (8f + (i % 5) * 3f) - cam * 0.3f) % (vw + 40f) + vw + 40f) % (vw + 40f)
            val fy = 40f + (i * 53f) % 220f + sin(time * 0.8f + i) * 10f
            Gl.glow(c, fx, fy, 5f + (i % 3) * 3f, pal[i % 4], 90)
        }
    }

    private fun drawWorld(c: Canvas, a: Adv, pal: IntArray) {
        val l = a.camX - 60f
        val r = a.camX + vw + 60f
        // suelo y plataformas
        for (p in a.plats) {
            if (p.x1 < l || p.x0 > r) continue
            val thick = if (p.y >= AdvCfg.GROUND) AdvCfg.H - p.y else 14f
            val pp = Gl.p()
            pp.shader = LinearGradient(0f, p.y, 0f, p.y + thick, Gl.alpha(Gl.darken(pal[0], 0.5f), 255), Gl.alpha(Gl.darken(pal[2], 0.18f), 255), Shader.TileMode.CLAMP)
            c.drawRect(p.x0, p.y, p.x1, p.y + thick, pp)
            Gl.line(c, p.x0, p.y + 1f, p.x1, p.y + 1f, 3f, Gl.alpha(pal[1], 230))
            Gl.glow(c, (p.x0 + p.x1) / 2f, p.y, min(120f, (p.x1 - p.x0) / 2f + 20f), pal[1], 22)
            if (p.y >= AdvCfg.GROUND) { // ladrillos
                var bx = p.x0 + 20f
                while (bx < p.x1 - 4f) {
                    Gl.line(c, bx, p.y + 10f, bx, p.y + 26f, 1.2f, Gl.white(26))
                    bx += 36f
                }
                Gl.line(c, p.x0, p.y + 26f, p.x1, p.y + 26f, 1.2f, Gl.white(22))
            }
        }
        // pinchos
        for (sp in a.spikes) {
            if (sp[1] < l || sp[0] > r) continue
            val pp = Gl.p()
            pp.color = 0xFFD8E0F0.toInt()
            var x = sp[0]
            while (x < sp[1] - 2f) {
                Gl.path.reset()
                Gl.path.moveTo(x, AdvCfg.GROUND)
                Gl.path.lineTo(x + 5f, AdvCfg.GROUND - 16f)
                Gl.path.lineTo(x + 10f, AdvCfg.GROUND)
                Gl.path.close()
                c.drawPath(Gl.path, pp)
                x += 10f
            }
            Gl.glow(c, (sp[0] + sp[1]) / 2f, AdvCfg.GROUND - 8f, 40f, 0xFFFF4050.toInt(), 40)
        }
        // checkpoints (faroles)
        for (ck in a.checks) {
            if (ck < l || ck > r || ck <= 40f) continue
            Gl.line(c, ck, AdvCfg.GROUND, ck, AdvCfg.GROUND - 34f, 2f, Gl.white(120))
            Gl.glow(c, ck, AdvCfg.GROUND - 38f, 22f, 0xFF80FFA0.toInt(), 120)
        }
        // velas
        for (cd in a.candles) {
            if (!cd.alive || cd.x < l || cd.x > r) continue
            Gl.line(c, cd.x, cd.y, cd.x, cd.y - 18f, 3f, 0xFFB09070.toInt())
            Gl.line(c, cd.x - 6f, cd.y, cd.x + 6f, cd.y, 3f, 0xFF806040.toInt())
            val fl = 1f + 0.2f * sin(time * 12f + cd.x)
            Gl.glow(c, cd.x, cd.y - 24f, 18f * fl, 0xFFFFA030.toInt(), 130)
            val pp = Gl.p()
            pp.color = 0xFFFFE080.toInt()
            c.drawOval(Gl.rect.also { it.set(cd.x - 3.5f, cd.y - 31f * 1f, cd.x + 3.5f, cd.y - 19f) }, pp)
        }
        for (i in a.items) drawItem(c, i, pal)
        for (e in a.enemies) if (e.x > l - 40f && e.x < r + 40f) drawEnemy(c, e, a, pal)
        drawBoss(c, a, pal)
        for (h in a.hazs) drawHaz(c, h)
        for (p in a.projs) drawProj(c, p, pal)
        drawPlayer(c, a, pal)
    }

    private fun drawItem(c: Canvas, i: AdvItem, pal: IntArray) {
        val bob = sin(i.t * 5f) * 1.5f
        val x = i.x
        val y = i.y + bob
        when (i.kind) {
            Adv.IT_HEART, Adv.IT_BIG -> {
                val k = if (i.kind == Adv.IT_BIG) 1.6f else 1f
                Gl.glow(c, x, y, 14f * k, 0xFFFF4070.toInt(), 90)
                val pp = Gl.p()
                pp.color = 0xFFFF4070.toInt()
                c.drawCircle(x - 3f * k, y - 2f * k, 3.6f * k, pp)
                c.drawCircle(x + 3f * k, y - 2f * k, 3.6f * k, pp)
                Gl.path.reset()
                Gl.path.moveTo(x - 6.4f * k, y - 0.5f * k); Gl.path.lineTo(x + 6.4f * k, y - 0.5f * k); Gl.path.lineTo(x, y + 7f * k); Gl.path.close()
                c.drawPath(Gl.path, pp)
            }
            Adv.IT_MEAT -> {
                Gl.glow(c, x, y, 14f, 0xFFFFA040.toInt(), 70)
                val pp = Gl.p(); pp.color = 0xFFC8703A.toInt(); c.drawCircle(x, y, 6f, pp)
                Gl.line(c, x + 4f, y - 4f, x + 9f, y - 9f, 2.5f, Gl.white(230))
            }
            Adv.IT_SUB, Adv.IT_WHIP, Adv.IT_ORB -> {
                val col = if (i.kind == Adv.IT_ORB) pal[((time * 4f).toInt()) % 4] else if (i.kind == Adv.IT_WHIP) 0xFFFFD060.toInt() else 0xFF70C8FF.toInt()
                val rr = if (i.kind == Adv.IT_ORB) 13f else 8f
                Gl.glow(c, x, y, rr * 2.6f, col, 120)
                val pp = Gl.p(); pp.color = Gl.alpha(col, 230); c.drawCircle(x, y, rr, pp)
                val ch = if (i.kind == Adv.IT_WHIP) "L" else if (i.kind == Adv.IT_ORB) "" else "DHACR".substring(i.arg - 1, i.arg)
                if (ch.isNotEmpty()) Gl.text(c, ch, x, y + 4f, 11f, Gl.medium, Color.BLACK)
                else Gl.arc(c, x, y, rr + 5f, time * 200f, 270f, 2f, Gl.white(200))
            }
        }
    }

    private fun drawEnemy(c: Canvas, e: AdvEnemy, a: Adv, pal: IntArray) {
        val w = a.ew(e)
        val h = a.eh(e)
        val base = if (e.flash > 0f) Color.WHITE else Gl.darken(pal[(e.type + 1) % 4], 0.85f)
        val x = e.x
        val y = e.y
        val pp = Gl.p()
        pp.color = base
        Gl.glow(c, x, y - h / 2f, w * 1.1f, pal[(e.type + 1) % 4], 40)
        when (e.type) {
            Adv.E_BAT -> {
                val flap = sin(time * 14f + e.t) * 8f
                Gl.path.reset()
                Gl.path.moveTo(x, y - 8f); Gl.path.lineTo(x - 14f, y - 14f + flap); Gl.path.lineTo(x - 6f, y - 3f); Gl.path.close()
                Gl.path.moveTo(x, y - 8f); Gl.path.lineTo(x + 14f, y - 14f + flap); Gl.path.lineTo(x + 6f, y - 3f); Gl.path.close()
                c.drawPath(Gl.path, pp)
                c.drawCircle(x, y - 7f, 5.5f, pp)
            }
            Adv.E_TURRET -> {
                c.drawRoundRect(Gl.rect.also { it.set(x - 11f, y - 14f, x + 11f, y) }, 4f, 4f, pp)
                Gl.line(c, x, y - 17f, x + e.face * 14f, y - 17f, 5f, base)
                c.drawCircle(x, y - 17f, 8f, pp)
            }
            Adv.E_HOPPER -> {
                val sq = if (e.y < e.baseY - 2f) 1.2f else 0.9f
                c.drawOval(Gl.rect.also { it.set(x - 11f / sq, y - 22f * sq, x + 11f / sq, y) }, pp)
            }
            Adv.E_CHARGER -> {
                if (e.st == 1) Gl.glow(c, x, y - 14f, 40f, 0xFFFF3040.toInt(), 120)
                c.drawRoundRect(Gl.rect.also { it.set(x - 14f, y - 24f, x + 14f, y) }, 8f, 8f, pp)
                Gl.path.reset()
                Gl.path.moveTo(x + e.face * 14f, y - 16f); Gl.path.lineTo(x + e.face * 24f, y - 20f); Gl.path.lineTo(x + e.face * 14f, y - 24f); Gl.path.close()
                c.drawPath(Gl.path, pp)
            }
            else -> {
                c.drawRoundRect(Gl.rect.also { it.set(x - 11f, y - 28f, x + 11f, y) }, 8f, 8f, pp)
                if (e.type == Adv.E_THROWER) c.drawCircle(x + e.face * 11f, y - 26f - (e.t * 6f).coerceAtMost(8f), 4f, pp)
            }
        }
        val ep = Gl.p()
        ep.color = 0xFFFFF0A0.toInt()
        val ey = if (e.type == Adv.E_BAT) y - 8f else y - h * 0.65f
        c.drawCircle(x + e.face * 3f - 3f, ey, 1.8f, ep)
        c.drawCircle(x + e.face * 3f + 3f, ey, 1.8f, ep)
    }

    private fun drawProj(c: Canvas, p: AdvProj, pal: IntArray) {
        if (!p.mine) {
            Gl.glow(c, p.x, p.y, p.r * 2.6f, 0xFFFF4060.toInt(), 120)
            val pp = Gl.p(); pp.color = 0xFFFFC0C8.toInt(); c.drawCircle(p.x, p.y, p.r * 0.7f, pp)
            return
        }
        when (p.kind) {
            1 -> { Gl.line(c, p.x - 8f * sgn(p.vx), p.y, p.x + 8f * sgn(p.vx), p.y, 3f, Color.WHITE); Gl.glow(c, p.x, p.y, 14f, pal[1], 100) }
            2 -> { c.save(); c.rotate(p.t * 700f, p.x, p.y); val pp = Gl.p(); pp.color = pal[2]; c.drawRect(p.x - 7f, p.y - 7f, p.x + 7f, p.y + 7f, pp); c.restore(); Gl.glow(c, p.x, p.y, 18f, pal[2], 90) }
            3 -> { Gl.arc(c, p.x, p.y, 8f, p.t * 600f, 250f, 3f, Color.WHITE); Gl.glow(c, p.x, p.y, 16f, pal[3], 90) }
            4 -> { val pp = Gl.p(); pp.color = 0xFF60C0FF.toInt(); c.drawCircle(p.x, p.y, 5f, pp); Gl.glow(c, p.x, p.y, 12f, 0xFF60C0FF.toInt(), 90) }
        }
    }

    private fun sgn(v: Float) = if (v >= 0f) 1f else -1f

    private fun drawHaz(c: Canvas, h: AdvHaz) {
        if (h.warn > 0f) {
            val pp = Gl.p()
            pp.color = Gl.alpha(0xFFFF3040.toInt(), 60 + (50 * sin(time * 30f)).toInt())
            c.drawRect(h.x0, h.y0, h.x1, h.y1, pp)
            return
        }
        val pp = Gl.p()
        when (h.kind) {
            2 -> {
                val f = 0.8f + 0.2f * sin(time * 18f + h.x0)
                pp.color = Gl.alpha(0xFFFF8020.toInt(), 220)
                c.drawRoundRect(Gl.rect.also { it.set(h.x0, h.y1 - (h.y1 - h.y0) * f, h.x1, h.y1) }, 8f, 8f, pp)
                Gl.glow(c, (h.x0 + h.x1) / 2f, h.y1 - 10f, 36f, 0xFFFFB040.toInt(), 120)
            }
            else -> {
                pp.color = if (h.kind == 1 || h.kind == 3) Gl.alpha(Color.WHITE, 235) else Gl.alpha(0xFFFFD060.toInt(), 230)
                c.drawRect(h.x0, h.y0, h.x1, h.y1, pp)
                Gl.glow(c, (h.x0 + h.x1) / 2f, (h.y0 + h.y1) / 2f, max(h.x1 - h.x0, h.y1 - h.y0) * 0.5f + 12f, 0xFFFF6070.toInt(), 80)
            }
        }
    }

    private fun drawPlayer(c: Canvas, a: Adv, pal: IntArray) {
        if (a.deadT > 0f) return
        val blink = a.invuln > 0f && ((time * 18f).toInt() % 2 == 0)
        if (!blink) ball(c, a.px, a.py - 11f, 11f, pal[0])
        // ojos de dirección
        if (!blink) {
            val ep = Gl.p(); ep.color = Color.WHITE
            c.drawCircle(a.px + a.face * 4f, a.py - 14f, 2.6f, ep)
            ep.color = Color.BLACK
            c.drawCircle(a.px + a.face * 5f, a.py - 14f, 1.2f, ep)
        }
        if (a.atkT > 0f) {
            val u = (0.34f - a.atkT) / 0.34f
            val ext = if (u < 0.35f) u / 0.35f else max(0f, 1f - (u - 0.35f) / 0.65f * 0.4f)
            val len = a.whipReach * min(1f, ext + 0.05f)
            val x0 = a.px + a.face * 8f
            val y0 = a.py - 18f
            val col = if (a.whip > 0) 0xFFFFD060.toInt() else Color.WHITE
            var px0 = x0
            var py0 = y0
            for (k in 1..8) {
                val t = k / 8f
                val x1 = x0 + a.face * len * t
                val y1 = y0 + sin(t * 9f - time * 30f) * 3f * (1f - t * 0.4f)
                Gl.line(c, px0, py0, x1, y1, 3.6f - 2f * t, Gl.alpha(col, 240))
                px0 = x1; py0 = y1
            }
            Gl.glow(c, px0, py0, 16f, col, 150)
        }
    }

    private fun drawBoss(c: Canvas, a: Adv, pal: IntArray) {
        if (!a.bossStarted) return
        val d = a.bdef
        val x = a.bx
        val gy = AdvCfg.GROUND
        if (a.bstate == Adv.BS_DEAD && a.bst < 1.6f && ((time * 20f).toInt() % 2 == 0)) return
        val fade = if (a.bstate == Adv.BS_INTRO) (1f - a.bst / 1.6f).coerceIn(0.1f, 1f) else 1f
        val col = if (a.bflash > 0f) Color.WHITE else Gl.darken(pal[world % 4], 0.8f)
        val acc = pal[(world + 1) % 4]
        Gl.glow(c, x, gy - d.h / 2f, d.w * 1.5f, acc, (70 * fade).toInt())
        if (a.bstate == Adv.BS_TELE) Gl.glow(c, x, gy - d.h / 2f, d.w * 1.8f, 0xFFFF3040.toInt(), (80 + 60 * sin(time * 30f)).toInt().coerceIn(0, 160))
        val pp = Gl.p()
        pp.shader = LinearGradient(x, gy - d.h, x, gy, Gl.alpha(col, (255 * fade).toInt()), Gl.alpha(Gl.darken(col, 0.45f), (255 * fade).toInt()), Shader.TileMode.CLAMP)
        c.drawRoundRect(Gl.rect.also { it.set(x - d.w / 2f, gy - d.h * 0.72f, x + d.w / 2f, gy) }, 14f, 14f, pp)
        val hp2 = Gl.p()
        hp2.color = Gl.alpha(col, (255 * fade).toInt())
        c.drawCircle(x, gy - d.h * 0.72f, d.w * 0.34f, hp2)
        val ip = Gl.p()
        ip.color = Gl.alpha(acc, (255 * fade).toInt())
        // adorno distinto por jefe
        when (world % 4) {
            0 -> for (k in -1..1) { Gl.path.reset(); Gl.path.moveTo(x + k * 9f - 4f, gy - d.h * 0.72f - d.w * 0.3f); Gl.path.lineTo(x + k * 9f, gy - d.h * 0.72f - d.w * 0.55f); Gl.path.lineTo(x + k * 9f + 4f, gy - d.h * 0.72f - d.w * 0.3f); Gl.path.close(); c.drawPath(Gl.path, ip) }
            1 -> Gl.arc(c, x, gy - d.h * 0.72f - d.w * 0.4f, 12f, 0f, 360f, 3f, Gl.alpha(acc, 220))
            2 -> for (k in -2..2) Gl.line(c, x + k * d.w * 0.18f, gy - 4f, x + k * d.w * 0.26f + sin(time * 3f + k) * 5f, gy + 10f - d.h * 0.2f, 4f, Gl.alpha(col, 200))
            else -> for (sgn in intArrayOf(-1, 1)) { Gl.path.reset(); Gl.path.moveTo(x + sgn * d.w * 0.22f, gy - d.h * 0.78f); Gl.path.lineTo(x + sgn * d.w * 0.4f, gy - d.h * 1.02f); Gl.path.lineTo(x + sgn * d.w * 0.12f, gy - d.h * 0.9f); Gl.path.close(); c.drawPath(Gl.path, ip) }
        }
        val ex = a.bface * 4f
        ip.color = 0xFFFFE070.toInt()
        c.drawCircle(x - 7f + ex, gy - d.h * 0.74f, 3.2f, ip)
        c.drawCircle(x + 7f + ex, gy - d.h * 0.74f, 3.2f, ip)
        if (a.bstate == Adv.BS_STUN) for (k in 0 until 3) {
            val an = time * 6f + k * 2.1f
            Gl.glow(c, x + cos(an) * 18f, gy - d.h * 0.72f - d.w * 0.4f + sin(an) * 5f, 7f, 0xFFFFE070.toInt(), 200)
        }
    }

    private fun drawHud(c: Canvas, a: Adv, pal: IntArray) {
        // vida
        for (i in 0 until AdvCfg.MAXHP) {
            val pp = Gl.p()
            pp.color = if (i < a.hp) 0xFFFF4A60.toInt() else Gl.white(40)
            c.drawRoundRect(Gl.rect.also { it.set(18f + i * 12f, 14f, 18f + i * 12f + 9f, 28f) }, 3f, 3f, pp)
        }
        Gl.text(c, "♥ ${a.hearts}", 18f, 48f, 15f, Gl.medium, Gl.white(235), 0f, null, 0, Paint.Align.LEFT)
        val nm = AdvCfg.subNames[a.sub]
        Gl.text(c, if (a.sub == 0) "Sin arma" else "$nm (${AdvCfg.subCost[a.sub]}♥)", 18f, 66f, 12f, Gl.regular, Gl.white(180), 0f, null, 0, Paint.Align.LEFT)
        if (a.bossStarted && a.bstate != Adv.BS_INTRO) {
            val bw = min(260f, vw * 0.36f)
            val x0 = vw / 2f - bw / 2f
            Gl.text(c, a.bdef.name, vw / 2f, 15f, 12f, Gl.medium, Gl.white(220))
            var pp = Gl.p(); pp.color = Gl.white(40)
            c.drawRoundRect(Gl.rect.also { it.set(x0, 20f, x0 + bw, 30f) }, 5f, 5f, pp)
            pp = Gl.p(); pp.color = pal[0]
            c.drawRoundRect(Gl.rect.also { it.set(x0, 20f, x0 + bw * a.bhp / a.bmax.toFloat(), 30f) }, 5f, 5f, pp)
        }
        // pausa
        val pp = Gl.p(); pp.color = Gl.white(50)
        c.drawCircle(vw - 30f, 32f, 20f, pp)
        pp.color = Gl.white(220)
        c.drawRect(vw - 36f, 24f, vw - 31f, 40f, pp)
        c.drawRect(vw - 27f, 24f, vw - 22f, 40f, pp)
    }

    private fun drawControls(c: Canvas, pal: IntArray) {
        for (b in buttons()) {
            if (b.id == "P") continue
            val held = ptrs.containsValue(b.id)
            val pp = Gl.p()
            pp.color = Gl.white(if (held) 90 else 38)
            c.drawCircle(b.x, b.y, b.r, pp)
            Gl.arc(c, b.x, b.y, b.r, 0f, 360f, 2f, Gl.white(if (held) 220 else 110))
            val lab = when (b.id) { "L" -> "◀"; "R" -> "▶"; "A" -> "⚔"; "J" -> "▲"; else -> "✦" }
            Gl.text(c, lab, b.x, b.y + b.r * 0.3f, b.r * 0.85f, Gl.regular, Gl.white(230))
        }
    }

    private fun panelBox(c: Canvas, pal: IntArray, l: Float, t: Float, r: Float, b: Float) {
        val pp = Gl.p(); pp.color = Gl.alpha(Color.BLACK, 150)
        c.drawRect(0f, 0f, vw, AdvCfg.H, pp)
        Gl.panel(c, l, t, r, b, 18f, 1f, pal[0])
    }

    private fun drawStory(c: Canvas, pal: IntArray) {
        panelBox(c, pal, vw / 2f - 280f, 60f, vw / 2f + 280f, 300f)
        Gl.text(c, "MUNDO ${world + 1}: ${Worlds.names[world]}", vw / 2f, 100f, 22f, Gl.medium, Gl.white(245), 0.08f)
        Gl.text(c, "Jefe: ${AdvCfg.bosses[world].name}", vw / 2f, 126f, 15f, Gl.regular, pal[1])
        wrapText(c, AdvCfg.worldIntro[world], vw / 2f, 166f, 500f, 17f, 24f)
        Gl.text(c, "Toca para empezar", vw / 2f, 276f, 15f, Gl.regular, Gl.white((150 + 90 * sin(time * 4f)).toInt()))
    }

    private fun drawPause(c: Canvas, pal: IntArray) {
        panelBox(c, pal, vw / 2f - 160f, 80f, vw / 2f + 160f, 270f)
        Gl.text(c, "PAUSA", vw / 2f, 125f, 24f, Gl.medium, Gl.white(245), 0.14f)
        Gl.panel(c, vw / 2f - 120f, 139f, vw / 2f + 120f, 191f, 26f, 1f, pal[1])
        Gl.text(c, "Continuar", vw / 2f, 172f, 20f, Gl.medium, Gl.white(245))
        Gl.panel(c, vw / 2f - 120f, 199f, vw / 2f + 120f, 251f, 26f, 1f, pal[2])
        Gl.text(c, "Salir", vw / 2f, 232f, 20f, Gl.medium, Gl.white(245))
    }

    private fun drawWin(c: Canvas, pal: IntArray) {
        val a = ((phT - 0.8f) / 0.6f).coerceIn(0f, 1f)
        if (a <= 0f) return
        panelBox(c, pal, vw / 2f - 280f, 70f, vw / 2f + 280f, 290f)
        Gl.text(c, "¡${Worlds.names[world]} salvado!", vw / 2f, 118f, 26f, Gl.medium, Gl.white(245), 0.06f)
        wrapText(c, AdvCfg.worldOutro[world], vw / 2f, 160f, 500f, 18f, 25f)
        Gl.text(c, "Toca para volver", vw / 2f, 262f, 15f, Gl.regular, Gl.white((150 + 90 * sin(time * 4f)).toInt()))
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
