package com.nioro.spectrumcolor

import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modo Aventura (estilo Castlevania): plataformas de scroll lateral con látigo de luz, arma secundaria
 * que gasta corazones, enemigos, velas rompibles y un jefe por mundo. Todo en unidades de diseño con
 * 360 de alto. Sin dependencias de Android, para poder probarlo en la JVM.
 */
object AdvCfg {
    const val H = 360f
    const val STEP = 1f / 120f
    const val GRAV = 1500f
    const val RUN = 175f
    const val JUMP_V = 545f
    const val GROUND = 300f
    const val PHALF = 10f
    const val PH = 30f
    const val MAXHP = 12
    const val WORLDS = 12
    const val MAXHEARTS = 99

    // armas secundarias
    const val SUB_DAGGER = 1
    const val SUB_AXE = 2
    const val SUB_CROSS = 3
    const val SUB_WATER = 4
    const val SUB_WATCH = 5
    val subCost = intArrayOf(0, 1, 2, 2, 2, 4)
    val subNames = arrayOf("Nada", "Daga de luz", "Hacha prisma", "Aro bumerán", "Fuego líquido", "Reloj del tiempo")

    // patrones de jefe
    const val P_FAN = 1
    const val P_CHARGE = 2
    const val P_SLAM = 3
    const val P_RAIN = 4
    const val P_SUMMON = 5
    const val P_BEAM = 6
    const val P_TELE = 7
    const val P_ORBS = 8
    const val P_ULT = 9

    class Boss(val name: String, val w: Float, val h: Float, val pats: IntArray)

    val bosses = arrayOf(
        Boss("Guardián Prisma", 46f, 56f, intArrayOf(P_FAN, P_CHARGE, P_RAIN)),
        Boss("Reina Aurora", 40f, 62f, intArrayOf(P_BEAM, P_FAN, P_TELE)),
        Boss("Kraken Abisal", 62f, 60f, intArrayOf(P_SLAM, P_RAIN, P_SUMMON)),
        Boss("Rey Neón", 48f, 62f, intArrayOf(P_CHARGE, P_FAN, P_BEAM)),
        Boss("Coloso de Cristal", 66f, 74f, intArrayOf(P_SLAM, P_BEAM, P_ORBS)),
        Boss("Señor del Ocaso", 44f, 64f, intArrayOf(P_TELE, P_FAN, P_RAIN)),
        Boss("Devorador Galáctico", 60f, 60f, intArrayOf(P_ORBS, P_CHARGE, P_SUMMON)),
        Boss("Titán de Lava", 70f, 78f, intArrayOf(P_SLAM, P_FAN, P_RAIN)),
        Boss("Ent Ancestral", 64f, 84f, intArrayOf(P_SUMMON, P_SLAM, P_BEAM)),
        Boss("Emperatriz de Hielo", 44f, 66f, intArrayOf(P_BEAM, P_RAIN, P_TELE)),
        Boss("Oráculo Cósmico", 50f, 64f, intArrayOf(P_ORBS, P_TELE, P_BEAM)),
        Boss("El Espectro", 58f, 78f, intArrayOf(P_CHARGE, P_SLAM, P_ORBS, P_BEAM, P_TELE, P_RAIN))
    )

    val worldIntro = arrayOf(
        "El Prisma se apagó. Sus colores huyeron del cielo y un guardián ocupa su corazón.",
        "En la Aurora ya no brillan las luces del norte. Una reina de hielo y viento las encadenó.",
        "El Océano se vació de azul. Algo enorme duerme en el fondo y despierta con hambre.",
        "Las luces del Neón parpadean sin fuego. Un rey de pura electricidad reclama la ciudad.",
        "El Cristal se agrietó. Un coloso de espejos aplasta todo lo que refleja.",
        "El Atardecer no termina nunca. Un señor de las sombras detuvo el sol.",
        "La Galaxia se está apagando estrella por estrella. Un devorador se las traga.",
        "La Lava se desbordó. Un titán forjado en fuego quema todo a su paso.",
        "El Bosque despertó con rabia. Un ent milenario ataca a quien se acerque.",
        "El Hielo lo congeló todo. Una emperatriz sin corazón vigila el silencio.",
        "El Cósmico perdió su rumbo. Un oráculo ciego dispara el destino al azar.",
        "El Espectro: el último mundo. Aquí nació el vacío que robó todos los colores."
    )
    val worldOutro = arrayOf(
        "El Prisma vuelve a brillar. Un color regresó a casa.",
        "Las auroras bailan otra vez sobre el norte.",
        "El Océano recupera su azul profundo.",
        "El Neón se enciende, y la ciudad canta.",
        "El Cristal se repara, reflejando la luz.",
        "El sol por fin cae, y mañana volverá a salir.",
        "Las estrellas se encienden una a una.",
        "La Lava vuelve a su cauce, tibia y tranquila.",
        "El Bosque respira en paz.",
        "El Hielo se derrite en un manto de rocío.",
        "El Cósmico encuentra su camino otra vez.",
        "El vacío se rompe. Todos los colores regresan a la Esfera."
    )
}

class AdvPlat(val x0: Float, val x1: Float, val y: Float)
class AdvCandle(val x: Float, val y: Float, val drop: Int, val arg: Int) { var alive = true }
class AdvItem(var x: Float, var y: Float, val kind: Int, val arg: Int) {
    var vy = -140f
    var t = 0f
}
class AdvEnemy(val type: Int, var x: Float, var y: Float, val x0: Float, val x1: Float, var hp: Int) {
    var vx = 0f
    var vy = 0f
    var t = 0f
    var st = 0
    var face = -1
    var flash = 0f
    var baseY = y
    var dead = false
}
class AdvProj(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val dmg: Int, val mine: Boolean, val r: Float) {
    var grav = 0f
    var life = 3f
    var pierce = false
    var t = 0f
    val hit = HashSet<Any>()
}
/** Zona de daño: rayos, rayos láser, ondas de choque y fuego. `mine` = la lanzó el jugador. */
class AdvHaz(var x0: Float, var y0: Float, var x1: Float, var y1: Float, var vx: Float, var warn: Float, var life: Float, val dmg: Int, val mine: Boolean, val kind: Int) {
    var tick = 0f
}
class AdvEv(val t: Int, val x: Float, val y: Float)
class AdvInput {
    var left = false
    var right = false
    var jumpHeld = false
    var jumpPressed = false
    var atkPressed = false
    var spPressed = false
    fun clearPressed() { jumpPressed = false; atkPressed = false; spPressed = false }
}

class Adv(val world: Int, val viewW: Float, val god: Boolean = false) {
    companion object {
        const val EV_JUMP = 1
        const val EV_SWING = 2
        const val EV_HIT = 3
        const val EV_HURT = 4
        const val EV_PICK = 5
        const val EV_CANDLE = 6
        const val EV_SPECIAL = 7
        const val EV_BOSSHIT = 8
        const val EV_BOSSDIE = 9
        const val EV_EDIE = 10
        const val EV_CHECK = 11
        const val EV_LAND = 12
        const val EV_SLAM = 13
        const val EV_BOSSWARN = 14
        const val EV_WIN = 15
        const val EV_DIE = 16
        const val EV_STUN = 17

        const val IT_HEART = 0
        const val IT_BIG = 1
        const val IT_MEAT = 2
        const val IT_SUB = 3
        const val IT_WHIP = 4
        const val IT_ORB = 5

        // enemigos
        const val E_WALKER = 0
        const val E_BAT = 1
        const val E_THROWER = 2
        const val E_HOPPER = 3
        const val E_TURRET = 4
        const val E_CHARGER = 5

        const val BS_INTRO = 0
        const val BS_WALK = 1
        const val BS_TELE = 2
        const val BS_ACT = 3
        const val BS_STUN = 4
        const val BS_DEAD = 5

        private val ehp = intArrayOf(2, 1, 3, 2, 3, 4)
    }

    val rnd = Random(world * 7919L + 31L)
    val plats = ArrayList<AdvPlat>()
    val spikes = ArrayList<FloatArray>()
    val candles = ArrayList<AdvCandle>()
    val enemies = ArrayList<AdvEnemy>()
    val checks = ArrayList<Float>()
    val items = ArrayList<AdvItem>()
    val projs = ArrayList<AdvProj>()
    val hazs = ArrayList<AdvHaz>()
    val events = ArrayList<AdvEv>()
    var arenaX = 0f
    var endX = 0f

    // jugador
    var px = 40f
    var py = AdvCfg.GROUND
    var pvx = 0f
    var pvy = 0f
    var face = 1
    var onGround = true
    var hp = AdvCfg.MAXHP
    var hearts = 5
    var sub = 0
    var whip = 0
    var invuln = 0f
    var hitstun = 0f
    var atkT = 0f
    var atkCd = 0f
    var spCd = 0f
    var coyote = 0f
    var jumpBuf = 0f
    var jumping = false
    var deadT = 0f
    var deaths = 0
    var won = false
    var freezeT = 0f
    var time = 0f
    var camX = 0f
    private var acc = 0f
    private val swingHit = HashSet<Any>()
    private var lastCheck = 40f

    // jefe
    val bdef = AdvCfg.bosses[world.coerceIn(0, AdvCfg.WORLDS - 1)]
    var bossStarted = false
    var bx = 0f
    var bvx = 0f
    var bvy = 0f
    var bhp = 16 + 4 * world
    val bmax = 16 + 4 * world
    var bstate = BS_INTRO
    var bst = 0f
    var bpat = 0
    var bface = -1
    var bflash = 0f
    var bcd = 1.4f
    var bacc = 0
    var benraged = false
    var bult = false
    private var bsub = 0
    private var bcount = 0
    private var bt2 = 0f
    var bLane = 0
    var shake = 0f
    private var clearPending = false

    init { build() }

    // ------------------------------------------------------------------ construcción del nivel
    private fun ground(x0: Float, x1: Float) { plats.add(AdvPlat(x0, x1, AdvCfg.GROUND)) }

    private fun allowed(): IntArray = when {
        world == 0 -> intArrayOf(E_WALKER, E_BAT)
        world == 1 -> intArrayOf(E_WALKER, E_BAT, E_HOPPER)
        world == 2 -> intArrayOf(E_WALKER, E_BAT, E_THROWER)
        world == 3 -> intArrayOf(E_WALKER, E_HOPPER, E_TURRET)
        world == 4 -> intArrayOf(E_BAT, E_THROWER, E_CHARGER)
        else -> intArrayOf(E_WALKER, E_BAT, E_THROWER, E_HOPPER, E_TURRET, E_CHARGER)
    }

    private fun addEnemy(type: Int, x: Float, y: Float, x0: Float, x1: Float) {
        val hp = ehp[type] + (if (world >= 6) 1 else 0) + (if (world >= 10) 1 else 0)
        val e = AdvEnemy(type, x, if (type == E_BAT) y - 70f - rnd.nextInt(30) else y, x0, x1, hp)
        e.face = if (rnd.nextBoolean()) 1 else -1
        e.baseY = e.y
        e.t = rnd.nextFloat() * 2f
        enemies.add(e)
    }

    private fun addCandle(x: Float, y: Float) {
        val r = rnd.nextInt(100)
        var drop = IT_HEART
        var arg = 0
        when {
            r < 48 -> drop = IT_HEART
            r < 60 -> drop = IT_BIG
            r < 72 -> drop = IT_MEAT
            r < 94 -> { drop = IT_SUB; arg = 1 + rnd.nextInt(5) }
            else -> drop = IT_WHIP
        }
        candles.add(AdvCandle(x, y, drop, arg))
    }

    private fun scatter(x0: Float, x1: Float, y: Float, count: Int) {
        val al = allowed()
        for (i in 0 until count) {
            val t = al[rnd.nextInt(al.size)]
            val m = if (x1 - x0 > 200f) 55f else 22f
            val x = x0 + m + rnd.nextFloat() * max(4f, x1 - x0 - 2f * m)
            addEnemy(t, x, y, x0 + m - 10f, x1 - m + 10f)
        }
    }

    private fun build() {
        val diff = world / 11f
        ground(0f, 360f)
        checks.add(40f)
        var cx = 360f
        val target = 2600f + world * 140f
        var lastCk = 0f
        var wins = 0
        while (cx < target) {
            val k = rnd.nextInt(100)
            when {
                k < 28 -> { // tramo llano con enemigos
                    val len = 260f + rnd.nextInt(180)
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 1 + (diff * 2f).toInt() + rnd.nextInt(2))
                    if (rnd.nextInt(3) == 0) addCandle(cx + 60f + rnd.nextInt(max(1, len.toInt() - 120)), AdvCfg.GROUND)
                    cx += len
                }
                k < 52 -> { // foso con plataforma flotante opcional
                    val gap = 55f + rnd.nextInt(30) + 10f * diff
                    if (rnd.nextBoolean()) {
                        val pw = 46f
                        plats.add(AdvPlat(cx + gap / 2f - pw / 2f, cx + gap / 2f + pw / 2f, AdvCfg.GROUND - 55f))
                    }
                    cx += gap
                    val len = 220f + rnd.nextInt(120)
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 1 + rnd.nextInt(2))
                    cx += len
                }
                k < 76 -> { // zona de plataformas
                    val len = 380f
                    ground(cx, cx + len)
                    var px0 = cx + 40f
                    var lvl = 0
                    while (px0 < cx + len - 100f) {
                        val pw = 80f + rnd.nextInt(60)
                        val h = AdvCfg.GROUND - (60f + 55f * (lvl % 2) + rnd.nextInt(15))
                        plats.add(AdvPlat(px0, px0 + pw, h))
                        if (rnd.nextInt(3) != 0) addCandle(px0 + pw / 2f, h)
                        if (rnd.nextInt(2) == 0) scatter(px0, px0 + pw, h, 1)
                        px0 += pw + 30f + rnd.nextInt(30)
                        lvl++
                    }
                    scatter(cx, cx + len, AdvCfg.GROUND, 1 + rnd.nextInt(2))
                    cx += len
                }
                k < 88 && world >= 2 -> { // pinchos
                    val len = 320f
                    ground(cx, cx + len)
                    val sw = 40f + rnd.nextInt(30)
                    val sx = cx + len / 2f - sw / 2f
                    spikes.add(floatArrayOf(sx, sx + sw))
                    scatter(cx, cx + len, AdvCfg.GROUND, 1)
                    cx += len
                }
                else -> { // prueba de enemigos
                    val len = 330f
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 2 + (diff * 2f).toInt())
                    addCandle(cx + len / 2f, AdvCfg.GROUND)
                    cx += len
                }
            }
            if (cx - lastCk > 1000f) { checks.add(cx - 120f); lastCk = cx; wins++ }
        }
        // entrada a la arena del jefe
        ground(cx, cx + 140f)
        checks.add(cx + 40f)
        arenaX = cx + 140f
        ground(arenaX, arenaX + viewW + 80f)
        endX = arenaX + viewW
        bx = arenaX + viewW * 0.78f
        // quita enemigos pegados al inicio
        enemies.removeAll { it.x < 220f }
        px = 40f
        py = AdvCfg.GROUND
        lastCheck = 40f
    }

    // ------------------------------------------------------------------ utilidades
    private fun ev(t: Int, x: Float, y: Float) { events.add(AdvEv(t, x, y)) }

    fun groundBelow(x: Float, y: Float): Float {
        var best = AdvCfg.H + 400f
        for (p in plats) if (x >= p.x0 && x <= p.x1 && p.y >= y - 2f && p.y < best) best = p.y
        return best
    }

    private fun overlap(ax0: Float, ay0: Float, ax1: Float, ay1: Float, bx0: Float, by0: Float, bx1: Float, by1: Float) =
        ax0 < bx1 && ax1 > bx0 && ay0 < by1 && ay1 > by0

    private fun playerHurt(dmg: Int, fromX: Float) {
        if (invuln > 0f || deadT > 0f || won) return
        if (!god) hp -= dmg
        invuln = 1.3f
        hitstun = 0.28f
        pvx = if (px >= fromX) 170f else -170f
        pvy = -230f
        onGround = false
        atkT = 0f
        ev(EV_HURT, px, py - 15f)
        if (hp <= 0) { hp = 0; die() }
    }

    private fun die() {
        deadT = 1.4f
        deaths++
        ev(EV_DIE, px, py - 15f)
    }

    private fun respawn() {
        hp = AdvCfg.MAXHP
        hearts = max(hearts, 5)
        px = lastCheck
        py = AdvCfg.GROUND
        pvx = 0f; pvy = 0f
        invuln = 1.5f
        hitstun = 0f
        deadT = 0f
        projs.removeAll { !it.mine }
        hazs.removeAll { !it.mine }
        if (bossStarted && bstate != BS_DEAD) resetBoss()
        if (bstate == BS_DEAD) { /* el jefe ya cayó: se queda así */ }
        for (e in enemies) if (e.type != E_BAT && abs(e.x - px) < 100f) e.x += 130f
    }

    private fun resetBoss() {
        bossStarted = false
        bhp = bmax
        bstate = BS_INTRO
        bst = 0f
        bx = arenaX + viewW * 0.78f
        bvx = 0f; bvy = 0f
        bcd = 1.4f
        benraged = false
        bult = false
        bacc = 0
        enemies.removeAll { it.dead || it.x > arenaX - 5f }
    }

    // ------------------------------------------------------------------ paso
    fun step(dt: Float, inp: AdvInput) {
        acc += dt
        var n = 0
        while (acc >= AdvCfg.STEP && n < 12) {
            tick(AdvCfg.STEP, inp)
            inp.clearPressed()
            acc -= AdvCfg.STEP
            n++
        }
        if (n == 12) acc = 0f
    }

    private fun lockedMin() = if (bossStarted) arenaX + AdvCfg.PHALF + 2f else AdvCfg.PHALF
    private fun lockedMax() = if (bossStarted) arenaX + viewW - AdvCfg.PHALF - 2f else endX + 200f

    private fun tick(dt: Float, inp: AdvInput) {
        time += dt
        if (shake > 0f) shake = max(0f, shake - dt * 30f)
        if (deadT > 0f) {
            deadT -= dt
            if (deadT <= 0f) respawn()
            updateCam(dt)
            return
        }
        if (freezeT > 0f) freezeT -= dt
        updatePlayer(dt, inp)
        updateCandlesItems(dt)
        updateEnemies(dt)
        updateProjs(dt)
        updateHazs(dt)
        updateBoss(dt)
        updateCam(dt)
    }

    private fun updateCam(dt: Float) {
        val target = if (bossStarted) arenaX else (px - viewW * 0.38f + face * 30f)
        val maxCam = max(0f, endX - viewW)
        val t = target.coerceIn(0f, maxCam)
        camX += (t - camX) * min(1f, dt * 6f)
        camX = camX.coerceIn(0f, maxCam)
    }

    private fun updatePlayer(dt: Float, inp: AdvInput) {
        invuln = max(0f, invuln - dt)
        hitstun = max(0f, hitstun - dt)
        atkCd = max(0f, atkCd - dt)
        spCd = max(0f, spCd - dt)
        val dir = (if (inp.right) 1 else 0) - (if (inp.left) 1 else 0)
        val control = hitstun <= 0f && !won
        if (control) {
            if (dir != 0) face = dir
            val tv = dir * AdvCfg.RUN * (if (atkT > 0f && onGround) 0.55f else 1f)
            pvx += (tv - pvx) * min(1f, dt * (if (onGround) 22f else 12f))
        } else {
            pvx *= (1f - min(1f, dt * 2f))
        }
        if (onGround) coyote = 0.1f else coyote -= dt
        if (inp.jumpPressed) jumpBuf = 0.12f else jumpBuf -= dt
        if (control && jumpBuf > 0f && coyote > 0f) {
            pvy = -AdvCfg.JUMP_V
            coyote = 0f
            jumpBuf = 0f
            onGround = false
            jumping = true
            ev(EV_JUMP, px, py)
        }
        if (jumping && !inp.jumpHeld && pvy < -190f) { pvy = -190f; jumping = false }
        if (pvy >= 0f) jumping = false
        pvy = min(900f, pvy + AdvCfg.GRAV * dt)
        px = (px + pvx * dt).coerceIn(lockedMin(), lockedMax())
        val prev = py
        py += pvy * dt
        var landed = false
        if (pvy >= 0f) {
            for (p in plats) {
                if (px + AdvCfg.PHALF > p.x0 && px - AdvCfg.PHALF < p.x1 && prev <= p.y + 1f && py >= p.y) {
                    py = p.y
                    landed = true
                    break
                }
            }
        }
        if (landed) {
            if (!onGround) ev(EV_LAND, px, py)
            pvy = 0f
            onGround = true
        } else onGround = false
        if (py > AdvCfg.H + 40f) { // se cayó
            hp = max(0, hp - 3)
            if (god) hp = AdvCfg.MAXHP
            die()
            return
        }
        // pinchos
        if (onGround && invuln <= 0f) {
            for (s in spikes) if (px + 6f > s[0] && px - 6f < s[1] && py >= AdvCfg.GROUND - 2f) playerHurt(2, (s[0] + s[1]) / 2f)
        }
        // checkpoints
        for (c in checks) if (c > lastCheck && px >= c && !bossStarted) { lastCheck = c; ev(EV_CHECK, c, AdvCfg.GROUND) }
        // arena
        if (!bossStarted && px > arenaX + 60f && bstate != BS_DEAD) {
            bossStarted = true
            bstate = BS_INTRO
            bst = 1.6f
            ev(EV_BOSSWARN, bx, AdvCfg.GROUND)
        }
        // ataque normal (látigo de luz)
        if (control && inp.atkPressed && atkCd <= 0f) {
            atkT = 0.34f
            atkCd = 0.36f
            swingHit.clear()
            ev(EV_SWING, px, py - 18f)
        }
        if (atkT > 0f) {
            atkT -= dt
            if (atkT in 0.12f..0.27f) whipHit()
        }
        // ataque especial
        if (control && inp.spPressed) useSub()
    }

    val whipReach: Float get() = 60f + 18f * whip
    val whipDmg: Int get() = if (whip == 0) 1 else 2

    private fun whipRect(): FloatArray {
        val r = whipReach
        return if (face > 0) floatArrayOf(px + 2f, py - 27f, px + 2f + r, py - 9f) else floatArrayOf(px - 2f - r, py - 27f, px - 2f, py - 9f)
    }

    private fun whipHit() {
        val w = whipRect()
        for (e in enemies) {
            if (e.dead || swingHit.contains(e)) continue
            if (overlap(w[0], w[1], w[2], w[3], e.x - ew(e) / 2f, e.y - eh(e), e.x + ew(e) / 2f, e.y)) {
                swingHit.add(e)
                hurtEnemy(e, whipDmg)
            }
        }
        for (c in candles) {
            if (!c.alive || swingHit.contains(c)) continue
            if (overlap(w[0], w[1], w[2], w[3], c.x - 7f, c.y - 28f, c.x + 7f, c.y)) {
                swingHit.add(c)
                breakCandle(c)
            }
        }
        if (bossStarted && bstate != BS_DEAD && bstate != BS_INTRO && !swingHit.contains(this)) {
            if (overlap(w[0], w[1], w[2], w[3], bx - bdef.w / 2f, AdvCfg.GROUND - bdef.h, bx + bdef.w / 2f, AdvCfg.GROUND)) {
                swingHit.add(this)
                hurtBoss(whipDmg)
            }
        }
    }

    private fun useSub() {
        if (sub == 0 || spCd > 0f) return
        val cost = AdvCfg.subCost[sub]
        if (hearts < cost) return
        val x = px + face * 10f
        val y = py - 18f
        when (sub) {
            AdvCfg.SUB_DAGGER -> { projs.add(AdvProj(x, y, 430f * face, 0f, 1, 2, true, 6f).also { it.life = 1.2f }); spCd = 0.3f }
            AdvCfg.SUB_AXE -> { projs.add(AdvProj(x, y - 6f, 140f * face, -440f, 2, 3, true, 9f).also { it.grav = 1100f; it.pierce = true; it.life = 2.5f }); spCd = 0.55f }
            AdvCfg.SUB_CROSS -> {
                if (projs.any { it.mine && it.kind == 3 }) return
                projs.add(AdvProj(x, y, 270f * face, 0f, 3, 2, true, 8f).also { it.pierce = true; it.life = 2.4f })
                spCd = 0.7f
            }
            AdvCfg.SUB_WATER -> { projs.add(AdvProj(x, y - 4f, 120f * face, -230f, 4, 0, true, 6f).also { it.grav = 900f; it.life = 3f }); spCd = 0.8f }
            AdvCfg.SUB_WATCH -> { freezeT = 2.6f; spCd = 1f }
        }
        hearts -= cost
        ev(EV_SPECIAL, px, py - 18f)
    }

    // ------------------------------------------------------------------ daños
    fun ew(e: AdvEnemy) = when (e.type) { E_BAT -> 18f; E_TURRET -> 22f; E_CHARGER -> 28f; else -> 22f }
    fun eh(e: AdvEnemy) = when (e.type) { E_BAT -> 14f; E_TURRET -> 24f; E_CHARGER -> 26f; else -> 28f }

    private fun hurtEnemy(e: AdvEnemy, dmg: Int) {
        if (e.dead) return
        e.hp -= dmg
        e.flash = 0.15f
        ev(EV_HIT, e.x, e.y - eh(e) / 2f)
        if (e.hp <= 0) {
            e.dead = true
            ev(EV_EDIE, e.x, e.y - eh(e) / 2f)
            val r = rnd.nextInt(100)
            if (r < 32) items.add(AdvItem(e.x, e.y - 12f, IT_HEART, 0))
            else if (r < 38) items.add(AdvItem(e.x, e.y - 12f, IT_BIG, 0))
        }
    }

    private fun breakCandle(c: AdvCandle) {
        c.alive = false
        ev(EV_CANDLE, c.x, c.y - 14f)
        items.add(AdvItem(c.x, c.y - 20f, c.drop, c.arg))
    }

    fun hurtBoss(dmg: Int) {
        if (bstate == BS_DEAD || bstate == BS_INTRO) return
        bhp -= dmg
        bflash = 0.12f
        ev(EV_BOSSHIT, bx, AdvCfg.GROUND - bdef.h / 2f)
        bacc += dmg
        if (bhp <= 0) {
            bhp = 0
            bstate = BS_DEAD
            bst = 2f
            clearPending = true
            ev(EV_BOSSDIE, bx, AdvCfg.GROUND - bdef.h / 2f)
            return
        }
        if (bacc >= 10 && (bstate == BS_WALK || bstate == BS_TELE)) {
            bacc = 0
            bstate = BS_STUN
            bst = 1.1f
            ev(EV_STUN, bx, AdvCfg.GROUND - bdef.h)
        }
        if (!benraged && bhp * 3 <= bmax) { benraged = true; ev(EV_BOSSWARN, bx, AdvCfg.GROUND) }
    }

    // ------------------------------------------------------------------ velas y objetos
    private fun updateCandlesItems(dt: Float) {
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            i.t += dt
            val gy = groundBelow(i.x, i.y)
            i.vy = min(500f, i.vy + 900f * dt)
            i.y += i.vy * dt
            if (i.y >= gy - 6f) { i.y = gy - 6f; i.vy = 0f }
            if (overlap(px - 11f, py - 32f, px + 11f, py, i.x - 8f, i.y - 8f, i.x + 8f, i.y + 8f)) {
                when (i.kind) {
                    IT_HEART -> hearts = min(AdvCfg.MAXHEARTS, hearts + 1)
                    IT_BIG -> hearts = min(AdvCfg.MAXHEARTS, hearts + 5)
                    IT_MEAT -> hp = min(AdvCfg.MAXHP, hp + 4)
                    IT_SUB -> sub = i.arg
                    IT_WHIP -> whip = min(2, whip + 1)
                    IT_ORB -> { hp = AdvCfg.MAXHP; won = true; ev(EV_WIN, px, py - 20f) }
                }
                ev(EV_PICK, i.x, i.y)
                it.remove()
            }
        }
    }

    // ------------------------------------------------------------------ enemigos
    private fun updateEnemies(dt: Float) {
        val frozen = freezeT > 0f
        val it = enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.dead) { it.remove(); continue }
            e.flash = max(0f, e.flash - dt)
            if (abs(e.x - px) > 700f) continue
            if (!frozen) enemyAi(e, dt)
            if (!e.dead && invuln <= 0f && deadT <= 0f &&
                overlap(px - 9f, py - 29f, px + 9f, py - 1f, e.x - ew(e) / 2f, e.y - eh(e), e.x + ew(e) / 2f, e.y)) {
                playerHurt(if (e.type == E_BAT) 1 else 2, e.x)
            }
        }
    }

    private fun enemyAi(e: AdvEnemy, dt: Float) {
        val toP = if (px >= e.x) 1 else -1
        when (e.type) {
            E_WALKER -> {
                e.x += e.face * 40f * dt
                if (e.x < e.x0 + 10f) e.face = 1
                if (e.x > e.x1 - 10f) e.face = -1
            }
            E_BAT -> {
                e.t += dt
                if (abs(px - e.x) < 300f) { e.x += toP * 62f * dt; e.face = toP }
                e.y = e.baseY + sin(e.t * 3f) * 26f
            }
            E_THROWER -> {
                e.face = toP
                if (abs(px - e.x) < 340f) e.t += dt
                if (e.t > 2.3f) {
                    e.t = 0f
                    projs.add(AdvProj(e.x, e.y - 26f, 120f * toP, -310f, 0, 2, false, 6f).also { it.grav = 700f; it.life = 3f })
                }
            }
            E_HOPPER -> {
                if (e.y >= e.baseY && e.vy >= 0f) {
                    e.y = e.baseY; e.vy = 0f; e.vx = 0f
                    e.t += dt
                    e.face = toP
                    if (e.t > 1.1f && abs(px - e.x) < 320f) { e.t = 0f; e.vy = -340f; e.vx = toP * 95f }
                } else {
                    e.vy += 900f * dt
                    e.x = (e.x + e.vx * dt).coerceIn(e.x0 + 8f, e.x1 - 8f)
                    e.y += e.vy * dt
                    if (e.y >= e.baseY) { e.y = e.baseY; e.vy = 0f }
                }
            }
            E_TURRET -> {
                e.face = toP
                if (abs(px - e.x) < 340f) e.t += dt
                if (e.t > 2.0f) {
                    e.t = 0f
                    projs.add(AdvProj(e.x, e.y - 14f, 160f * toP, 0f, 0, 2, false, 5f).also { it.life = 3f })
                }
            }
            E_CHARGER -> {
                when (e.st) {
                    0 -> {
                        e.x += e.face * 35f * dt
                        if (e.x < e.x0 + 12f) e.face = 1
                        if (e.x > e.x1 - 12f) e.face = -1
                        if (abs(px - e.x) < 210f && abs(py - e.y) < 40f && toP == e.face) { e.st = 1; e.t = 0f }
                    }
                    1 -> { e.t += dt; if (e.t > 0.55f) { e.st = 2; e.t = 0f } }
                    2 -> {
                        e.t += dt
                        e.x += e.face * 255f * dt
                        if (e.x < e.x0 + 12f) { e.x = e.x0 + 12f; e.st = 3; e.t = 0f }
                        if (e.x > e.x1 - 12f) { e.x = e.x1 - 12f; e.st = 3; e.t = 0f }
                        if (e.t > 0.8f) { e.st = 3; e.t = 0f }
                    }
                    else -> { e.t += dt; if (e.t > 0.7f) { e.st = 0; e.face = toP } }
                }
            }
        }
    }

    // ------------------------------------------------------------------ proyectiles
    private fun updateProjs(dt: Float) {
        val frozen = freezeT > 0f
        val it = projs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.mine && frozen) continue
            p.t += dt
            p.life -= dt
            if (p.kind == 3 && p.mine) { // bumerán: vuelve
                if (p.t > 0.55f) p.vx -= (if (p.vx > 0f) 1f else -1f) * 760f * dt
                if (p.t > 0.55f) { val back = if (px >= p.x) 1f else -1f; if (abs(p.vx) < 30f) p.vx = back * 30f }
                if (p.t > 0.9f && abs(px - p.x) < 20f && abs(py - 15f - p.y) < 30f) { it.remove(); continue }
            }
            p.vy += p.grav * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f || p.x < camX - 120f || p.x > camX + viewW + 120f || p.y > AdvCfg.H + 40f) { it.remove(); continue }
            if (p.mine) {
                if (p.kind == 4) { // agua bendita: al tocar suelo hace fuego
                    val g = groundBelow(p.x, p.y - 4f)
                    if (p.y >= g - 4f && p.vy > 0f) {
                        hazs.add(AdvHaz(p.x - 28f, g - 24f, p.x + 28f, g, 0f, 0f, 2.6f, 1, true, 2))
                        ev(EV_SLAM, p.x, g)
                        it.remove()
                        continue
                    }
                    continue
                }
                var gone = false
                for (e in enemies) {
                    if (e.dead || p.hit.contains(e)) continue
                    val dx = abs(p.x - e.x) - ew(e) / 2f
                    val dy = abs(p.y - (e.y - eh(e) / 2f)) - eh(e) / 2f
                    if (dx < p.r && dy < p.r) {
                        p.hit.add(e)
                        hurtEnemy(e, p.dmg)
                        if (!p.pierce) { gone = true; break }
                    }
                }
                if (!gone) for (c in candles) {
                    if (!c.alive || p.hit.contains(c)) continue
                    if (abs(p.x - c.x) < 8f + p.r && abs(p.y - (c.y - 14f)) < 16f + p.r) { p.hit.add(c); breakCandle(c); if (!p.pierce) { gone = true; break } }
                }
                if (!gone && bossStarted && bstate != BS_DEAD && bstate != BS_INTRO && !p.hit.contains(this)) {
                    val dx = abs(p.x - bx) - bdef.w / 2f
                    val dy = abs(p.y - (AdvCfg.GROUND - bdef.h / 2f)) - bdef.h / 2f
                    if (dx < p.r && dy < p.r) {
                        p.hit.add(this)
                        hurtBoss(p.dmg)
                        if (!p.pierce) gone = true
                    }
                }
                if (gone) it.remove()
            } else {
                val dx = abs(p.x - px) - 8f
                val dy = abs(p.y - (py - 15f)) - 14f
                if (dx < p.r && dy < p.r && invuln <= 0f && deadT <= 0f) {
                    playerHurt(p.dmg, p.x)
                    it.remove()
                }
            }
        }
    }

    private fun updateHazs(dt: Float) {
        val frozen = freezeT > 0f
        val it = hazs.iterator()
        while (it.hasNext()) {
            val h = it.next()
            if (!h.mine && frozen) continue
            if (h.warn > 0f) { h.warn -= dt; continue }
            h.life -= dt
            if (h.vx != 0f) { h.x0 += h.vx * dt; h.x1 += h.vx * dt }
            if (h.life <= 0f || h.x1 < camX - 60f || h.x0 > camX + viewW + 60f) { it.remove(); continue }
            if (h.mine) {
                h.tick -= dt
                if (h.tick <= 0f) {
                    h.tick = 0.35f
                    for (e in enemies) if (!e.dead && overlap(h.x0, h.y0, h.x1, h.y1, e.x - ew(e) / 2f, e.y - eh(e), e.x + ew(e) / 2f, e.y)) hurtEnemy(e, h.dmg)
                    if (bossStarted && bstate != BS_DEAD && bstate != BS_INTRO &&
                        overlap(h.x0, h.y0, h.x1, h.y1, bx - bdef.w / 2f, AdvCfg.GROUND - bdef.h, bx + bdef.w / 2f, AdvCfg.GROUND)) hurtBoss(h.dmg)
                }
            } else if (invuln <= 0f && deadT <= 0f && overlap(h.x0, h.y0, h.x1, h.y1, px - 8f, py - 29f, px + 8f, py - 1f)) {
                playerHurt(h.dmg, (h.x0 + h.x1) / 2f)
            }
        }
    }

    // ------------------------------------------------------------------ jefe
    private fun fire(x: Float, y: Float, ang: Float, speed: Float, dmg: Int = 2, r: Float = 6f) {
        projs.add(AdvProj(x, y, cos(ang) * speed, sin(ang) * speed, 0, dmg, false, r).also { it.life = 4f })
    }

    private fun aimAng(x: Float, y: Float): Float = atan2((py - 15f) - y, px - x)

    private fun pickPattern(): Int {
        val ps = bdef.pats
        val lim = if (bhp * 3 > bmax * 2) min(2, ps.size) else if (bhp * 3 > bmax) min(3, ps.size) else ps.size
        var p: Int
        var tries = 0
        do { p = ps[rnd.nextInt(max(2, lim).coerceAtMost(ps.size))]; tries++ } while (p == bpat && tries < 4)
        return p
    }

    private fun teleTime(p: Int) = when (p) {
        AdvCfg.P_CHARGE -> 0.7f; AdvCfg.P_SLAM -> 0.6f; AdvCfg.P_BEAM -> 0.8f; AdvCfg.P_TELE -> 0.45f
        AdvCfg.P_ULT -> 1.1f; AdvCfg.P_RAIN -> 0.35f; else -> 0.4f
    } * (if (benraged) 0.8f else 1f)

    private fun updateBoss(dt: Float) {
        if (!bossStarted && bstate != BS_DEAD) return
        bflash = max(0f, bflash - dt)
        if (clearPending) {
            clearPending = false
            projs.removeAll { !it.mine }
            hazs.removeAll { !it.mine }
            enemies.removeAll { it.x > arenaX }
        }
        if (bstate == BS_DEAD) {
            bst -= dt
            if (bst <= 0f && items.none { it.kind == IT_ORB } && !won) items.add(AdvItem(arenaX + viewW / 2f, AdvCfg.GROUND - 80f, IT_ORB, 0))
            return
        }
        if (freezeT > 0f && bstate != BS_INTRO) {
            // el reloj detiene al jefe, pero lo deja a tiro
            return
        }
        val toP = if (px >= bx) 1 else -1
        // contacto
        if (bstate != BS_INTRO && invuln <= 0f && deadT <= 0f &&
            overlap(px - 9f, py - 29f, px + 9f, py - 1f, bx - bdef.w / 2f + 4f, AdvCfg.GROUND - bdef.h + 4f, bx + bdef.w / 2f - 4f, AdvCfg.GROUND)) {
            playerHurt(if (bstate == BS_ACT && bpat == AdvCfg.P_CHARGE) 3 else 2, bx)
        }
        // furia final (movimiento desesperado, como en Bloodstained)
        if (!bult && bhp * 4 <= bmax && bstate != BS_STUN) {
            bult = true
            bpat = AdvCfg.P_ULT
            bstate = BS_TELE
            bst = teleTime(AdvCfg.P_ULT)
            ev(EV_BOSSWARN, bx, AdvCfg.GROUND)
        }
        when (bstate) {
            BS_INTRO -> {
                bst -= dt
                bface = -toP
                if (bst <= 0f) { bstate = BS_WALK; bcd = 1.0f }
            }
            BS_WALK -> {
                bface = toP
                val sp = (34f + world * 1.5f) * (if (benraged) 1.35f else 1f)
                if (abs(px - bx) > 90f) bx += toP * sp * dt
                bx = bx.coerceIn(arenaX + bdef.w / 2f + 6f, arenaX + viewW - bdef.w / 2f - 6f)
                bcd -= dt
                if (bcd <= 0f) {
                    bpat = pickPattern()
                    bstate = BS_TELE
                    bst = teleTime(bpat)
                    bface = toP
                    bLane = rnd.nextInt(2)
                }
            }
            BS_TELE -> {
                bst -= dt
                if (bst <= 0f) { bstate = BS_ACT; bst = 0f; bsub = 0; bcount = 0; bt2 = 0f; startAct() }
            }
            BS_ACT -> { bst += dt; actPattern(dt) }
            BS_STUN -> {
                bst -= dt
                if (bst <= 0f) { bstate = BS_WALK; bcd = 0.9f }
            }
        }
    }

    private fun endAct(cd: Float) {
        bstate = BS_WALK
        bcd = cd * (if (benraged) 0.65f else 1f)
    }

    private fun startAct() {
        val my = AdvCfg.GROUND - bdef.h * 0.6f
        when (bpat) {
            AdvCfg.P_FAN -> {
                val n = if (benraged) 5 else 3
                val a0 = aimAng(bx, my)
                for (i in 0 until n) fire(bx, my, a0 + (i - (n - 1) / 2f) * 0.3f, 175f)
            }
            AdvCfg.P_CHARGE -> bvx = bface * (if (benraged) 340f else 290f)
            AdvCfg.P_SLAM -> { bvy = -560f; bvx = ((px - bx) / 0.9f).coerceIn(-230f, 230f) }
            AdvCfg.P_RAIN -> {
                val n = if (benraged) 8 else 5
                for (i in 0 until n) {
                    val x = if (i == 0) px else arenaX + 30f + rnd.nextFloat() * (viewW - 60f)
                    hazs.add(AdvHaz(x - 13f, 0f, x + 13f, AdvCfg.GROUND, 0f, 0.9f + i * 0.08f, 0.28f, 3, false, 1))
                }
            }
            AdvCfg.P_SUMMON -> {
                val live = enemies.count { !it.dead && it.x > arenaX }
                if (live < 3) for (i in 0 until 2) {
                    val t = if (world % 2 == 0) E_WALKER else E_BAT
                    val x = arenaX + 80f + rnd.nextFloat() * (viewW - 160f)
                    addEnemy(t, x, AdvCfg.GROUND, arenaX + 10f, arenaX + viewW - 10f)
                }
            }
            AdvCfg.P_BEAM -> {
                val y0 = if (bLane == 0) AdvCfg.GROUND - 24f else AdvCfg.GROUND - 104f
                hazs.add(AdvHaz(arenaX, y0, arenaX + viewW, y0 + 18f, 0f, 0.1f, 0.55f, 3, false, 3))
            }
            AdvCfg.P_TELE -> {
                bx = if (px < arenaX + viewW / 2f) arenaX + viewW - bdef.w / 2f - 20f else arenaX + bdef.w / 2f + 20f
                bface = if (px >= bx) 1 else -1
                val a0 = aimAng(bx, my)
                for (i in 0 until 3) fire(bx, my, a0 + (i - 1) * 0.3f, 185f)
            }
            AdvCfg.P_ULT -> { bcount = 0 }
        }
    }

    private fun actPattern(dt: Float) {
        val my = AdvCfg.GROUND - bdef.h * 0.6f
        when (bpat) {
            AdvCfg.P_FAN -> if (bst > 0.5f) endAct(1.3f)
            AdvCfg.P_CHARGE -> {
                bx += bvx * dt
                val lo = arenaX + bdef.w / 2f + 4f
                val hi = arenaX + viewW - bdef.w / 2f - 4f
                if (bx <= lo || bx >= hi) {
                    bx = bx.coerceIn(lo, hi)
                    bvx = 0f
                    bstate = BS_STUN
                    bst = 0.95f
                    shake = 6f
                    ev(EV_SLAM, bx, AdvCfg.GROUND)
                }
            }
            AdvCfg.P_SLAM -> {
                bvy += 1500f * dt
                bx = (bx + bvx * dt).coerceIn(arenaX + bdef.w / 2f + 4f, arenaX + viewW - bdef.w / 2f - 4f)
                bt2 += dt
                // sube como un salto: se mide con una altura virtual
                bsub = if (bsub == 0 && bvy > 0f && bt2 > 0.3f) 1 else bsub
                if (bt2 > 0.9f || (bsub == 1 && bt2 > 0.8f)) {
                    bvx = 0f; bvy = 0f
                    shake = 8f
                    ev(EV_SLAM, bx, AdvCfg.GROUND)
                    hazs.add(AdvHaz(bx - 10f, AdvCfg.GROUND - 16f, bx + 10f, AdvCfg.GROUND, 210f, 0f, 2.2f, 2, false, 4))
                    hazs.add(AdvHaz(bx - 10f, AdvCfg.GROUND - 16f, bx + 10f, AdvCfg.GROUND, -210f, 0f, 2.2f, 2, false, 4))
                    endAct(1.2f)
                }
            }
            AdvCfg.P_RAIN -> if (bst > 1.5f) endAct(1.2f)
            AdvCfg.P_SUMMON -> if (bst > 0.6f) endAct(1.4f)
            AdvCfg.P_BEAM -> if (bst > 1.0f) endAct(1.0f)
            AdvCfg.P_TELE -> if (bst > 0.5f) endAct(1.1f)
            AdvCfg.P_ORBS -> {
                bt2 += dt
                val period = if (benraged) 0.14f else 0.2f
                if (bt2 >= period) {
                    bt2 -= period
                    bcount++
                    fire(bx, my, bcount * 0.7f, 125f)
                }
                if (bst > 2.4f) endAct(1.0f)
            }
            AdvCfg.P_ULT -> {
                bt2 += dt
                if (bt2 >= 0.55f && bcount < 3) {
                    bt2 = 0f
                    bcount++
                    val n = 12
                    for (i in 0 until n) fire(bx, my, i * 6.2832f / n + bcount * 0.2f, 140f)
                    if (bcount == 2) for (i in 0 until 5) {
                        val x = arenaX + 30f + rnd.nextFloat() * (viewW - 60f)
                        hazs.add(AdvHaz(x - 13f, 0f, x + 13f, AdvCfg.GROUND, 0f, 0.8f, 0.28f, 3, false, 1))
                    }
                }
                if (bcount >= 3 && bt2 > 1.0f) endAct(1.4f)
            }
        }
        if (bpat != AdvCfg.P_CHARGE && bpat != AdvCfg.P_SLAM) bx = bx.coerceIn(arenaX + bdef.w / 2f + 4f, arenaX + viewW - bdef.w / 2f - 4f)
    }
}
