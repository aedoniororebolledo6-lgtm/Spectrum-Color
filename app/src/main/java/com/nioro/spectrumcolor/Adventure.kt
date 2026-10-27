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
    const val SUB_BIBLE = 6
    const val SUB_STAR = 7
    const val SUB_BOLT = 8
    const val SUB_ORB = 9
    const val SUBS = 9
    val subCost = intArrayOf(0, 1, 2, 2, 2, 4, 3, 3, 4, 3)
    val subNames = arrayOf("Nada", "Daga de luz", "Hacha prisma", "Aro bumerán", "Fuego líquido", "Reloj del tiempo",
        "Libro de luz", "Estrellas ninja", "Rayo celeste", "Orbes guía")
    const val subLetters = "-DHAFRLETO"

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
    const val P_WAVE = 10
    const val P_METEOR = 11
    const val P_SPIRAL = 12

    class Boss(val name: String, val w: Float, val h: Float, val pats: IntArray)

    val bosses = arrayOf(
        Boss("Guardián Prisma", 46f, 56f, intArrayOf(P_FAN, P_CHARGE, P_RAIN, P_WAVE)),
        Boss("Reina Aurora", 40f, 62f, intArrayOf(P_BEAM, P_FAN, P_TELE, P_SPIRAL)),
        Boss("Kraken Abisal", 62f, 60f, intArrayOf(P_SLAM, P_RAIN, P_SUMMON, P_WAVE)),
        Boss("Rey Neón", 48f, 62f, intArrayOf(P_CHARGE, P_FAN, P_BEAM, P_METEOR)),
        Boss("Coloso de Cristal", 66f, 74f, intArrayOf(P_SLAM, P_BEAM, P_ORBS, P_WAVE)),
        Boss("Señor del Ocaso", 44f, 64f, intArrayOf(P_TELE, P_FAN, P_RAIN, P_SPIRAL)),
        Boss("Devorador Galáctico", 60f, 60f, intArrayOf(P_ORBS, P_CHARGE, P_SUMMON, P_METEOR)),
        Boss("Titán de Lava", 70f, 78f, intArrayOf(P_SLAM, P_FAN, P_RAIN, P_METEOR, P_WAVE)),
        Boss("Ent Ancestral", 64f, 84f, intArrayOf(P_SUMMON, P_SLAM, P_BEAM, P_WAVE)),
        Boss("Emperatriz de Hielo", 44f, 66f, intArrayOf(P_BEAM, P_RAIN, P_TELE, P_SPIRAL)),
        Boss("Oráculo Cósmico", 50f, 64f, intArrayOf(P_ORBS, P_TELE, P_BEAM, P_SPIRAL, P_METEOR)),
        Boss("El Espectro", 58f, 78f, intArrayOf(P_CHARGE, P_SLAM, P_ORBS, P_BEAM, P_TELE, P_RAIN, P_SPIRAL, P_METEOR, P_WAVE))
    )

    const val finalText = "Los doce mundos recuperaron sus colores. La Esfera, por fin completa, vuelve a girar en el centro del Prisma y un nuevo ciclo de luz comienza."
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
    var floor = y
    var aggro = false
}
class AdvProj(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val dmg: Int, val mine: Boolean, val r: Float) {
    var grav = 0f
    var life = 3f
    var pierce = false
    var armor = false
    var homing = false
    var t = 0f
    val hit = HashSet<Any>()
}
/** Zona de daño: rayos, rayos láser, ondas de choque y fuego. `mine` = la lanzó el jugador. */
class AdvHaz(var x0: Float, var y0: Float, var x1: Float, var y1: Float, var vx: Float, var warn: Float, var life: Float, val dmg: Int, val mine: Boolean, val kind: Int) {
    var tick = 0f
}
class AdvEv(val t: Int, val x: Float, val y: Float, val n: Int = 0)

/** Plataforma móvil: su centro va de (x0,y0) a (x1,y1). */
class AdvMover(val x0: Float, val x1: Float, val y0: Float, val y1: Float, val w: Float, val speed: Float, val phase: Float) {
    var x = x0
    var y = y0
    var dx = 0f
    var dy = 0f
    val vertical: Boolean get() = abs(y1 - y0) > abs(x1 - x0)
}
/** Plataforma que se rompe poco después de pisarla. st: 0 sólida, 1 temblando, 2 caída. */
class AdvCrumble(val x0: Float, val x1: Float, val y: Float) {
    var st = 0
    var t = 0f
}
/** Chorro de fuego periódico. */
class AdvJet(val x: Float, val y: Float, val period: Float, val phase: Float) {
    var warn = false
    var on = false
}
/** Cuchilla colgante que oscila. */
class AdvPend(val cx: Float, val cy: Float, val len: Float, val speed: Float, val phase: Float) {
    var bx = cx
    var by = cy + len
}
/** Roca que cae al acercarse. st: 0 espera, 1 temblando, 2 cayendo, 3 rota. */
class AdvRock(val x: Float, val y0: Float) {
    var st = 0
    var y = y0
    var vy = 0f
    var t = 0f
}
/** Emboscada: la puerta se cierra y llegan oleadas. st: 0 espera, 1 activa, 2 vencida. */
class AdvAmbush(val x0: Float, val x1: Float) {
    var st = 0
    var t = 0f
    var wave = 0
    val mine = ArrayList<AdvEnemy>()
}
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
        const val EV_BLOCK = 18
        const val EV_JUMP2 = 19
        const val EV_CRUMBLE = 20
        const val EV_ROCK = 21
        const val EV_AMBUSH = 22
        const val EV_CLEAR = 23
        const val EV_BOLT = 24
        const val EV_POTION = 25

        const val IT_HEART = 0
        const val IT_BIG = 1
        const val IT_MEAT = 2
        const val IT_SUB = 3
        const val IT_WHIP = 4
        const val IT_ORB = 5
        const val IT_BOOTS = 6
        const val IT_POTION = 7

        // enemigos
        const val E_WALKER = 0
        const val E_BAT = 1
        const val E_THROWER = 2
        const val E_HOPPER = 3
        const val E_TURRET = 4
        const val E_CHARGER = 5
        const val E_MEDUSA = 6
        const val E_KNIGHT = 7
        const val E_GHOST = 8
        const val E_SPIDER = 9

        const val BS_INTRO = 0
        const val BS_WALK = 1
        const val BS_TELE = 2
        const val BS_ACT = 3
        const val BS_STUN = 4
        const val BS_DEAD = 5

        private val ehp = intArrayOf(2, 1, 3, 2, 3, 4, 1, 5, 2, 2)

        // tipos de tramo
        private const val S_FLAT = 0
        private const val S_PIT = 1
        private const val S_PLATS = 2
        private const val S_SPIKES = 3
        private const val S_TRIAL = 4
        private const val S_MOVER = 5
        private const val S_CRUMBLE = 6
        private const val S_JETS = 7
        private const val S_PEND = 8
        private const val S_ROCKS = 9
        private const val S_AMBUSH = 10
        private const val S_VMOVER = 11
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
    val movers = ArrayList<AdvMover>()
    val crumbles = ArrayList<AdvCrumble>()
    val jets = ArrayList<AdvJet>()
    val pends = ArrayList<AdvPend>()
    val rocks = ArrayList<AdvRock>()
    val ambushes = ArrayList<AdvAmbush>()
    var arenaX = 0f
    var endX = 0f
    val ice: Boolean get() = world == 9

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
    var boots = false
    var jumpsLeft = 0
    var potionT = 0f
    var bibleT = 0f
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
    var hitStop = 0f
    var riding: AdvMover? = null
    var kills = 0
    private var acc = 0f
    private val swingHit = HashSet<Any>()
    private var bibleTick = 0f
    private var lastCheck = 40f

    // jefe
    val bdef = AdvCfg.bosses[world.coerceIn(0, AdvCfg.WORLDS - 1)]
    var bossStarted = false
    var bx = 0f
    var bvx = 0f
    var bvy = 0f
    val bmax = 18 + 4 * world
    var bhp = bmax
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

    private fun allowed(): IntArray = when (world) {
        0 -> intArrayOf(E_WALKER, E_BAT)
        1 -> intArrayOf(E_WALKER, E_BAT, E_HOPPER, E_MEDUSA)
        2 -> intArrayOf(E_WALKER, E_BAT, E_THROWER, E_SPIDER)
        3 -> intArrayOf(E_WALKER, E_HOPPER, E_TURRET, E_KNIGHT)
        4 -> intArrayOf(E_BAT, E_THROWER, E_CHARGER, E_MEDUSA)
        5 -> intArrayOf(E_KNIGHT, E_GHOST, E_BAT, E_THROWER)
        6 -> intArrayOf(E_GHOST, E_MEDUSA, E_TURRET, E_CHARGER)
        7 -> intArrayOf(E_KNIGHT, E_CHARGER, E_HOPPER, E_TURRET)
        8 -> intArrayOf(E_SPIDER, E_WALKER, E_MEDUSA, E_KNIGHT)
        9 -> intArrayOf(E_GHOST, E_HOPPER, E_THROWER, E_KNIGHT)
        else -> intArrayOf(E_WALKER, E_BAT, E_THROWER, E_HOPPER, E_TURRET, E_CHARGER, E_MEDUSA, E_KNIGHT, E_GHOST, E_SPIDER)
    }

    private fun addEnemy(type: Int, x: Float, y: Float, x0: Float, x1: Float): AdvEnemy {
        val hp = ehp[type] + (if (world >= 6) 1 else 0) + (if (world >= 10) 1 else 0)
        val ey = when (type) {
            E_BAT -> y - 70f - rnd.nextInt(30)
            E_MEDUSA -> y - 55f - rnd.nextInt(30)
            E_GHOST -> y - 40f - rnd.nextInt(20)
            E_SPIDER -> y - 105f
            else -> y
        }
        val e = AdvEnemy(type, x, ey, x0, x1, hp)
        e.face = if (rnd.nextBoolean()) 1 else -1
        e.baseY = e.y
        e.floor = y
        e.t = rnd.nextFloat() * 2f
        enemies.add(e)
        return e
    }

    private fun addCandle(x: Float, y: Float) {
        val r = rnd.nextInt(100)
        var drop = IT_HEART
        var arg = 0
        when {
            r < 38 -> drop = IT_HEART
            r < 48 -> drop = IT_BIG
            r < 58 -> drop = IT_MEAT
            r < 82 -> { drop = IT_SUB; arg = 1 + rnd.nextInt(AdvCfg.SUBS) }
            r < 88 -> drop = IT_WHIP
            r < 93 -> drop = IT_BOOTS
            else -> drop = IT_POTION
        }
        candles.add(AdvCandle(x, y, drop, arg))
    }

    private fun scatter(x0: Float, x1: Float, y: Float, count: Int, grounded: Boolean = false) {
        var al = allowed()
        if (grounded) {
            val g = al.filter { it != E_BAT && it != E_MEDUSA && it != E_GHOST && it != E_SPIDER && it != E_TURRET && it != E_THROWER }.toIntArray()
            al = if (g.isNotEmpty()) g else intArrayOf(E_WALKER)
        }
        for (i in 0 until count) {
            val t = al[rnd.nextInt(al.size)]
            val m = if (x1 - x0 > 200f) 55f else 22f
            val x = x0 + m + rnd.nextFloat() * max(4f, x1 - x0 - 2f * m)
            addEnemy(t, x, y, x0 + m - 10f, x1 - m + 10f)
        }
    }

    private fun pickSeg(cx: Float, target: Float, amb: Int, last: Int): Int {
        if (amb == 0 && cx > target * 0.5f && world >= 1) return S_AMBUSH
        if (amb == 1 && cx > target * 0.82f && world >= 5) return S_AMBUSH
        val w = IntArray(12)
        w[S_FLAT] = 18; w[S_PIT] = 14; w[S_PLATS] = 14; w[S_TRIAL] = 8
        if (world >= 2) w[S_SPIKES] = 8
        if (world >= 1) w[S_MOVER] = 11
        if (world >= 3) w[S_VMOVER] = 7
        if (world >= 2) w[S_CRUMBLE] = 10
        if (world >= 3) w[S_JETS] = 10
        if (world >= 4) w[S_PEND] = 9
        if (world >= 5) w[S_ROCKS] = 8
        // sabor de cada mundo
        when (world) {
            7 -> { w[S_JETS] += 12; w[S_CRUMBLE] += 6 }
            8 -> { w[S_CRUMBLE] += 10; w[S_ROCKS] += 6 }
            9 -> { w[S_PIT] += 8; w[S_MOVER] += 6 }
            2 -> { w[S_PIT] += 6; w[S_MOVER] += 6 }
            4 -> { w[S_SPIKES] += 8; w[S_PEND] += 4 }
            11 -> { for (i in 5..9) w[i] += 4 }
        }
        if (last in 5..9) { w[last] = 0 } // no repetir dos obstáculos grandes seguidos
        if (last == S_AMBUSH) { w[S_AMBUSH] = 0 }
        var tot = 0
        for (v in w) tot += v
        var r = rnd.nextInt(max(1, tot))
        for (i in w.indices) { if (r < w[i]) return i; r -= w[i] }
        return S_FLAT
    }

    private fun build() {
        val diff = world / 11f
        ground(0f, 360f)
        checks.add(40f)
        var cx = 360f
        val target = 3700f + world * 180f
        var lastCk = 0f
        var amb = 0
        var last = -1
        while (cx < target) {
            val k = pickSeg(cx, target, amb, last)
            when (k) {
                S_FLAT -> {
                    val len = 260f + rnd.nextInt(180)
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 1 + (diff * 2f).toInt() + rnd.nextInt(2))
                    if (rnd.nextInt(3) == 0) addCandle(cx + 60f + rnd.nextInt(max(1, len.toInt() - 120)), AdvCfg.GROUND)
                    cx += len
                }
                S_PIT -> {
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
                S_PLATS -> {
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
                S_SPIKES -> {
                    val len = 320f
                    ground(cx, cx + len)
                    val sw = 40f + rnd.nextInt(30)
                    val sx = cx + len / 2f - sw / 2f
                    spikes.add(floatArrayOf(sx, sx + sw))
                    scatter(cx, cx + len, AdvCfg.GROUND, 1)
                    cx += len
                }
                S_TRIAL -> {
                    val len = 330f
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 2 + (diff * 2f).toInt())
                    addCandle(cx + len / 2f, AdvCfg.GROUND)
                    cx += len
                }
                S_MOVER -> {
                    val g = 200f + rnd.nextInt(30)
                    movers.add(AdvMover(cx + 34f, cx + g - 34f, AdvCfg.GROUND, AdvCfg.GROUND, 56f, 0.9f + 0.1f * rnd.nextInt(3), rnd.nextFloat() * 6f))
                    if (world >= 4 && rnd.nextBoolean()) addEnemy(E_BAT, cx + g / 2f, AdvCfg.GROUND, cx, cx + g)
                    cx += g
                    val len = 240f
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 1, true)
                    addCandle(cx + 120f, AdvCfg.GROUND)
                    cx += len
                }
                S_VMOVER -> {
                    val g = 110f
                    val hh = 90f
                    movers.add(AdvMover(cx + g / 2f, cx + g / 2f, AdvCfg.GROUND, AdvCfg.GROUND - hh, 78f, 0.7f, 0f))
                    cx += g
                    val len = 200f
                    plats.add(AdvPlat(cx, cx + len, AdvCfg.GROUND - hh)) // cornisa alta
                    addCandle(cx + len / 2f, AdvCfg.GROUND - hh)
                    scatter(cx, cx + len, AdvCfg.GROUND - hh, 1, true)
                    cx += len
                    ground(cx, cx + 240f)
                    scatter(cx, cx + 240f, AdvCfg.GROUND, 1, true)
                    cx += 240f
                }
                S_CRUMBLE -> {
                    val n = 4 + rnd.nextInt(2)
                    val g = n * 54f + 8f
                    for (i in 0 until n) crumbles.add(AdvCrumble(cx + 8f + i * 54f, cx + 8f + i * 54f + 46f, AdvCfg.GROUND))
                    if (world >= 4) addEnemy(E_BAT, cx + g / 2f, AdvCfg.GROUND, cx, cx + g)
                    cx += g
                    val len = 240f
                    ground(cx, cx + len)
                    scatter(cx, cx + len, AdvCfg.GROUND, 1, true)
                    cx += len
                }
                S_JETS -> {
                    val len = 480f
                    ground(cx, cx + len)
                    for (i in 0 until 3) jets.add(AdvJet(cx + 130f + i * 120f, AdvCfg.GROUND, 2.7f, rnd.nextFloat() * 2.7f))
                    addCandle(cx + 40f, AdvCfg.GROUND)
                    cx += len
                }
                S_PEND -> {
                    val len = 480f
                    ground(cx, cx + len)
                    for (i in 0 until 2) pends.add(AdvPend(cx + 170f + i * 160f, AdvCfg.GROUND - 170f, 150f, 1.4f, rnd.nextFloat() * 6f))
                    addCandle(cx + 40f, AdvCfg.GROUND)
                    cx += len
                }
                S_ROCKS -> {
                    val len = 460f
                    ground(cx, cx + len)
                    for (i in 0 until 2) rocks.add(AdvRock(cx + 170f + i * 130f, AdvCfg.GROUND - 220f))
                    addCandle(cx + 40f, AdvCfg.GROUND)
                    scatter(cx, cx + 100f, AdvCfg.GROUND, 0)
                    cx += len
                }
                else -> { // S_AMBUSH
                    val len = viewW + 60f
                    ground(cx, cx + len)
                    ambushes.add(AdvAmbush(cx, cx + viewW))
                    checks.add(cx + 30f)
                    amb++
                    cx += len
                }
            }
            last = k
            if (cx - lastCk > 1000f) { checks.add(cx - 120f); lastCk = cx }
        }
        // entrada a la arena del jefe
        ground(cx, cx + 140f)
        checks.add(cx + 40f)
        arenaX = cx + 140f
        ground(arenaX, arenaX + viewW + 80f)
        endX = arenaX + viewW
        bx = arenaX + viewW * 0.78f
        enemies.removeAll { it.x < 220f }
        px = 40f
        py = AdvCfg.GROUND
        lastCheck = 40f
    }

    // ------------------------------------------------------------------ utilidades
    private fun ev(t: Int, x: Float, y: Float, n: Int = 0) { events.add(AdvEv(t, x, y, n)) }

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
        hitStop = 0.07f
        pvx = if (px >= fromX) 170f else -170f
        pvy = -230f
        onGround = false
        riding = null
        atkT = 0f
        ev(EV_HURT, px, py - 15f, dmg)
        if (hp <= 0) { hp = 0; die() }
    }

    private fun die() {
        deadT = 1.4f
        deaths++
        ev(EV_DIE, px, py - 15f)
    }

    private fun lastAmbushActive(): AdvAmbush? { for (a in ambushes) if (a.st == 1) return a; return null }

    private fun respawn() {
        hp = AdvCfg.MAXHP
        hearts = max(hearts, 5)
        px = lastCheck
        py = AdvCfg.GROUND
        pvx = 0f; pvy = 0f
        invuln = 1.5f
        hitstun = 0f
        deadT = 0f
        riding = null
        bibleT = 0f
        projs.removeAll { !it.mine }
        hazs.removeAll { !it.mine }
        // una emboscada a medias se reinicia
        for (a in ambushes) if (a.st == 1) {
            for (e in a.mine) e.dead = true
            a.mine.clear()
            a.st = 0
            a.wave = 0
            a.t = 0f
        }
        if (bossStarted && bstate != BS_DEAD) resetBoss()
        for (e in enemies) if (e.type != E_BAT && e.type != E_MEDUSA && abs(e.x - px) < 100f) e.x += 130f
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

    private fun lockedMin(): Float {
        val a = lastAmbushActive()
        if (a != null) return a.x0 + AdvCfg.PHALF + 2f
        return if (bossStarted) arenaX + AdvCfg.PHALF + 2f else AdvCfg.PHALF
    }

    private fun lockedMax(): Float {
        val a = lastAmbushActive()
        if (a != null) return a.x1 - AdvCfg.PHALF - 2f
        return if (bossStarted) arenaX + viewW - AdvCfg.PHALF - 2f else endX + 200f
    }

    private fun tick(dt: Float, inp: AdvInput) {
        time += dt
        if (shake > 0f) shake = max(0f, shake - dt * 30f)
        if (deadT > 0f) {
            deadT -= dt
            if (deadT <= 0f) respawn()
            updateCam(dt)
            return
        }
        if (hitStop > 0f) { hitStop -= dt; return }
        if (freezeT > 0f) freezeT -= dt
        updateMovers(dt)
        updatePlayer(dt, inp)
        updateTraps(dt)
        updateAmbush(dt)
        updateCandlesItems(dt)
        updateEnemies(dt)
        updateProjs(dt)
        updateHazs(dt)
        updateBoss(dt)
        updateCam(dt)
    }

    private fun updateCam(dt: Float) {
        val a = lastAmbushActive()
        val target = if (bossStarted) arenaX else if (a != null) a.x0 else (px - viewW * 0.38f + face * 30f)
        val maxCam = max(0f, endX - viewW)
        val t = target.coerceIn(0f, maxCam)
        camX += (t - camX) * min(1f, dt * 6f)
        camX = camX.coerceIn(0f, maxCam)
    }

    // ------------------------------------------------------------------ plataformas
    private fun updateMovers(dt: Float) {
        for (m in movers) {
            val u = 0.5f + 0.5f * sin(time * m.speed + m.phase)
            val nx = m.x0 + (m.x1 - m.x0) * u
            val ny = m.y0 + (m.y1 - m.y0) * u
            m.dx = nx - m.x
            m.dy = ny - m.y
            m.x = nx
            m.y = ny
        }
        val r = riding
        if (r != null && onGround) {
            if (abs(px - r.x) <= r.w / 2f + AdvCfg.PHALF - 2f) { px += r.dx; py = r.y } else riding = null
        } else if (r != null) riding = null
    }

    private fun updateTraps(dt: Float) {
        // plataformas que se rompen
        for (c in crumbles) {
            when (c.st) {
                1 -> { c.t -= dt; if (c.t <= 0f) { c.st = 2; c.t = 2.8f } }
                2 -> { c.t -= dt; if (c.t <= 0f) { c.st = 0 } }
            }
        }
        // chorros de fuego
        for (j in jets) {
            val per = j.period
            val cyc = (time + j.phase) % per
            j.warn = cyc >= per - 1.5f && cyc < per - 0.8f
            j.on = cyc >= per - 0.8f
            if (j.on && invuln <= 0f && deadT <= 0f &&
                overlap(px - 8f, py - 29f, px + 8f, py - 1f, j.x - 9f, j.y - 74f, j.x + 9f, j.y)) playerHurt(2, j.x)
        }
        // péndulos
        for (p in pends) {
            val ang = 0.95f * sin(time * p.speed + p.phase)
            p.bx = p.cx + sin(ang) * p.len
            p.by = p.cy + cos(ang) * p.len
            if (invuln <= 0f && deadT <= 0f) {
                val dx = max(abs(p.bx - px) - 8f, 0f)
                val dy = max(abs(p.by - (py - 15f)) - 14f, 0f)
                if (dx * dx + dy * dy < 11f * 11f) playerHurt(2, p.bx)
            }
        }
        // rocas
        for (r in rocks) {
            when (r.st) {
                0 -> if (abs(px - r.x) < 95f && abs(py - AdvCfg.GROUND) < 40f) { r.st = 1; r.t = 0.5f }
                1 -> { r.t -= dt; if (r.t <= 0f) { r.st = 2; r.vy = 0f } }
                2 -> {
                    r.vy += 1100f * dt
                    r.y += r.vy * dt
                    if (invuln <= 0f && deadT <= 0f &&
                        overlap(px - 8f, py - 29f, px + 8f, py - 1f, r.x - 11f, r.y - 22f, r.x + 11f, r.y)) { playerHurt(3, r.x) }
                    if (r.y >= AdvCfg.GROUND) {
                        r.y = AdvCfg.GROUND
                        r.st = 3
                        r.t = 4f
                        shake = max(shake, 4f)
                        ev(EV_ROCK, r.x, AdvCfg.GROUND)
                    }
                }
                3 -> { r.t -= dt; if (r.t <= 0f && abs(px - r.x) > 300f) { r.st = 0; r.y = r.y0 } }
            }
        }
    }

    private fun updateAmbush(dt: Float) {
        for (a in ambushes) {
            if (a.st == 0 && px >= a.x0 + 70f && px < a.x1 - 10f && deadT <= 0f) {
                a.st = 1
                a.wave = 0
                ev(EV_AMBUSH, a.x0 + viewW / 2f, AdvCfg.GROUND)
                spawnWave(a, 2 + (world / 5))
            } else if (a.st == 1) {
                a.t += dt
                if (a.t > 50f) for (e in a.mine) if (!e.dead) { e.dead = true; ev(EV_EDIE, e.x, e.y - 10f) } // seguro contra bloqueos
                a.mine.removeAll { it.dead }
                if (a.wave == 1 && a.mine.size <= 1) { a.wave = 2; spawnWave(a, 3 + (world / 3)) }
                else if (a.wave >= 2 && a.mine.isEmpty()) {
                    a.st = 2
                    ev(EV_CLEAR, px, py - 20f)
                    items.add(AdvItem((a.x0 + a.x1) / 2f, AdvCfg.GROUND - 60f, IT_BIG, 0))
                    items.add(AdvItem((a.x0 + a.x1) / 2f + 24f, AdvCfg.GROUND - 60f, if (rnd.nextBoolean()) IT_SUB else IT_MEAT, 1 + rnd.nextInt(AdvCfg.SUBS)))
                }
            }
        }
    }

    private fun spawnWave(a: AdvAmbush, n: Int) {
        a.wave++
        val al = allowed().filter { it != E_SPIDER }.toIntArray()
        for (i in 0 until n) {
            val t = al[rnd.nextInt(al.size)]
            var x = a.x0 + 60f + rnd.nextFloat() * (viewW - 120f)
            if (abs(x - px) < 140f) x = if (px < a.x0 + viewW / 2f) a.x1 - 40f - rnd.nextFloat() * 80f else a.x0 + 40f + rnd.nextFloat() * 80f
            val e = addEnemy(t, x, AdvCfg.GROUND, a.x0 + 10f, a.x1 - 10f)
            e.aggro = true
            a.mine.add(e)
            ev(EV_POTION, e.x, e.y - 10f, 1) // chispa de aparición
        }
    }

    /** y de la superficie sobre la que podría aterrizar el jugador, o NaN. */
    private fun landing(prev: Float, now: Float): Float {
        val x0 = px - AdvCfg.PHALF
        val x1 = px + AdvCfg.PHALF
        var best = Float.NaN
        for (p in plats) {
            if (x1 > p.x0 && x0 < p.x1 && prev <= p.y + 1f && now >= p.y && (best.isNaN() || p.y < best)) best = p.y
        }
        riding = null
        for (m in movers) {
            if (x1 > m.x - m.w / 2f && x0 < m.x + m.w / 2f && prev <= m.y + 10f && now >= m.y && (best.isNaN() || m.y <= best)) {
                best = m.y
                riding = m
            }
        }
        for (c in crumbles) {
            if (c.st == 2) continue
            if (x1 > c.x0 && x0 < c.x1 && prev <= c.y + 1f && now >= c.y && (best.isNaN() || c.y <= best)) {
                best = c.y
                riding = null
                if (c.st == 0) { c.st = 1; c.t = 0.5f; ev(EV_CRUMBLE, (c.x0 + c.x1) / 2f, c.y) }
            }
        }
        return best
    }

    // ------------------------------------------------------------------ jugador
    private fun updatePlayer(dt: Float, inp: AdvInput) {
        invuln = max(0f, invuln - dt)
        if (potionT > 0f) { potionT -= dt; invuln = max(invuln, 0.12f) }
        hitstun = max(0f, hitstun - dt)
        atkCd = max(0f, atkCd - dt)
        spCd = max(0f, spCd - dt)
        val dir = (if (inp.right) 1 else 0) - (if (inp.left) 1 else 0)
        val control = hitstun <= 0f && !won
        if (control) {
            if (dir != 0) face = dir
            val tv = dir * AdvCfg.RUN * (if (atkT > 0f && onGround) 0.55f else 1f)
            val k = if (onGround) (if (ice) 9f else 22f) else 12f
            pvx += (tv - pvx) * min(1f, dt * k)
        } else {
            pvx *= (1f - min(1f, dt * 2f))
        }
        if (onGround) { coyote = 0.1f; jumpsLeft = if (boots) 1 else 0 } else coyote -= dt
        if (inp.jumpPressed) jumpBuf = 0.12f else jumpBuf -= dt
        if (control && jumpBuf > 0f && coyote > 0f) {
            pvy = -AdvCfg.JUMP_V
            coyote = 0f
            jumpBuf = 0f
            onGround = false
            riding = null
            jumping = true
            ev(EV_JUMP, px, py)
        } else if (control && jumpBuf > 0f && !onGround && boots && jumpsLeft > 0) {
            pvy = -AdvCfg.JUMP_V * 0.9f
            jumpsLeft--
            jumpBuf = 0f
            jumping = true
            ev(EV_JUMP2, px, py - 10f)
        }
        if (jumping && !inp.jumpHeld && pvy < -190f) { pvy = -190f; jumping = false }
        if (pvy >= 0f) jumping = false
        pvy = min(900f, pvy + AdvCfg.GRAV * dt)
        px = (px + pvx * dt).coerceIn(lockedMin(), lockedMax())
        val prev = py
        py += pvy * dt
        var landed = false
        if (pvy >= 0f) {
            val y = landing(prev, py)
            if (!y.isNaN()) { py = y; landed = true }
        }
        if (landed) {
            if (!onGround) ev(EV_LAND, px, py)
            pvy = 0f
            onGround = true
        } else { onGround = false; riding = null }
        if (py > AdvCfg.H + 40f) {
            hp = max(0, hp - 3)
            if (god) hp = AdvCfg.MAXHP
            die()
            return
        }
        if (onGround && invuln <= 0f) {
            for (s in spikes) if (px + 6f > s[0] && px - 6f < s[1] && py >= AdvCfg.GROUND - 2f) playerHurt(2, (s[0] + s[1]) / 2f)
        }
        for (c in checks) if (c > lastCheck && px >= c && !bossStarted) { lastCheck = c; ev(EV_CHECK, c, AdvCfg.GROUND) }
        if (!bossStarted && px > arenaX + 60f && bstate != BS_DEAD) {
            bossStarted = true
            bstate = BS_INTRO
            bst = 1.6f
            ev(EV_BOSSWARN, bx, AdvCfg.GROUND)
        }
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
        if (control && inp.spPressed) useSub()
        if (bibleT > 0f) updateBible(dt)
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
                hurtEnemy(e, whipDmg, px, whip >= 2)
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
            AdvCfg.SUB_AXE -> { projs.add(AdvProj(x, y - 6f, 140f * face, -440f, 2, 3, true, 9f).also { it.grav = 1100f; it.pierce = true; it.armor = true; it.life = 2.5f }); spCd = 0.55f }
            AdvCfg.SUB_CROSS -> {
                if (projs.any { it.mine && it.kind == 3 }) return
                projs.add(AdvProj(x, y, 270f * face, 0f, 3, 2, true, 8f).also { it.pierce = true; it.armor = true; it.life = 2.4f })
                spCd = 0.7f
            }
            AdvCfg.SUB_WATER -> { projs.add(AdvProj(x, y - 4f, 120f * face, -230f, 4, 0, true, 6f).also { it.grav = 900f; it.life = 3f }); spCd = 0.8f }
            AdvCfg.SUB_WATCH -> { freezeT = 2.6f; spCd = 1f }
            AdvCfg.SUB_BIBLE -> { if (bibleT > 0f) return; bibleT = 4.5f; bibleTick = 0f; spCd = 0.5f }
            AdvCfg.SUB_STAR -> {
                for (i in -1..1) projs.add(AdvProj(x, y, 400f * face * cos(i * 0.2f), 400f * sin(i * 0.2f), 6, 1, true, 5f).also { it.life = 0.9f })
                spCd = 0.4f
            }
            AdvCfg.SUB_BOLT -> {
                val targets = ArrayList<Float>()
                for (e in enemies) if (!e.dead && e.x > camX && e.x < camX + viewW && targets.size < 3) targets.add(e.x)
                if (bossStarted && bstate != BS_DEAD && bstate != BS_INTRO && targets.size < 3) targets.add(bx)
                var k = 1
                while (targets.size < 3) { targets.add(px + face * 70f * k); k++ }
                for ((i, tx) in targets.withIndex()) hazs.add(AdvHaz(tx - 11f, 0f, tx + 11f, AdvCfg.GROUND, 0f, 0.25f + 0.1f * i, 0.18f, 3, true, 1))
                spCd = 1.0f
                ev(EV_BOLT, px, py - 18f)
            }
            AdvCfg.SUB_ORB -> {
                for (i in 0 until 2) projs.add(AdvProj(x, y, 90f * face, -100f + i * 200f, 5, 2, true, 6f).also { it.homing = true; it.armor = true; it.life = 2.6f })
                spCd = 0.6f
            }
        }
        hearts -= cost
        ev(EV_SPECIAL, px, py - 18f)
    }

    private fun updateBible(dt: Float) {
        bibleT -= dt
        bibleTick -= dt
        val cx = px
        val cy = py - 15f
        val ang0 = time * 6.5f
        val tickNow = bibleTick <= 0f
        if (tickNow) bibleTick = 0.3f
        for (k in 0 until 4) {
            val a = ang0 + k * 1.5708f
            val bx0 = cx + cos(a) * 36f
            val by0 = cy + sin(a) * 28f
            if (tickNow) {
                for (e in enemies) if (!e.dead && abs(e.x - bx0) < ew(e) / 2f + 8f && abs(e.y - eh(e) / 2f - by0) < eh(e) / 2f + 8f) hurtEnemy(e, 1, e.x, true)
                if (bossStarted && bstate != BS_DEAD && bstate != BS_INTRO && abs(bx - bx0) < bdef.w / 2f + 8f && abs(AdvCfg.GROUND - bdef.h / 2f - by0) < bdef.h / 2f + 8f) hurtBoss(1)
            }
            projs.removeAll { !it.mine && abs(it.x - bx0) < 11f && abs(it.y - by0) < 11f }
        }
    }

    // ------------------------------------------------------------------ daños
    fun ew(e: AdvEnemy) = when (e.type) { E_BAT -> 18f; E_TURRET -> 22f; E_CHARGER -> 28f; E_MEDUSA -> 18f; E_KNIGHT -> 26f; E_GHOST -> 22f; E_SPIDER -> 18f; else -> 22f }
    fun eh(e: AdvEnemy) = when (e.type) { E_BAT -> 14f; E_TURRET -> 24f; E_CHARGER -> 26f; E_MEDUSA -> 16f; E_KNIGHT -> 38f; E_GHOST -> 26f; E_SPIDER -> 14f; else -> 28f }

    private fun hurtEnemy(e: AdvEnemy, dmg: Int, srcX: Float, armor: Boolean) {
        if (e.dead) return
        if (e.type == E_KNIGHT && !armor && (srcX - e.x) * e.face > 0f) {
            e.flash = 0.05f
            ev(EV_BLOCK, e.x + e.face * 12f, e.y - 22f)
            return
        }
        e.hp -= dmg
        e.flash = 0.15f
        ev(EV_HIT, e.x, e.y - eh(e) / 2f, dmg)
        if (e.hp <= 0) {
            e.dead = true
            kills++
            hitStop = max(hitStop, 0.03f)
            ev(EV_EDIE, e.x, e.y - eh(e) / 2f)
            val r = rnd.nextInt(100)
            if (r < 30) items.add(AdvItem(e.x, e.y - 12f, IT_HEART, 0))
            else if (r < 36) items.add(AdvItem(e.x, e.y - 12f, IT_BIG, 0))
            else if (r < 39 && e.type == E_KNIGHT) items.add(AdvItem(e.x, e.y - 12f, IT_MEAT, 0))
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
        hitStop = max(hitStop, 0.045f)
        ev(EV_BOSSHIT, bx, AdvCfg.GROUND - bdef.h / 2f, dmg)
        bacc += dmg
        if (bhp <= 0) {
            bhp = 0
            bstate = BS_DEAD
            bst = 2f
            clearPending = true
            hitStop = 0.15f
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
            if (i.y > AdvCfg.H + 60f) { it.remove(); continue }
            if (overlap(px - 11f, py - 32f, px + 11f, py, i.x - 8f, i.y - 8f, i.x + 8f, i.y + 8f)) {
                when (i.kind) {
                    IT_HEART -> hearts = min(AdvCfg.MAXHEARTS, hearts + 1)
                    IT_BIG -> hearts = min(AdvCfg.MAXHEARTS, hearts + 5)
                    IT_MEAT -> hp = min(AdvCfg.MAXHP, hp + 4)
                    IT_SUB -> sub = i.arg.coerceIn(1, AdvCfg.SUBS)
                    IT_WHIP -> whip = min(2, whip + 1)
                    IT_BOOTS -> boots = true
                    IT_POTION -> potionT = 9f
                    IT_ORB -> { hp = AdvCfg.MAXHP; won = true; ev(EV_WIN, px, py - 20f) }
                }
                ev(EV_PICK, i.x, i.y, i.kind)
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
            if (e.dead) continue
            if (invuln <= 0f && deadT <= 0f &&
                overlap(px - 9f, py - 29f, px + 9f, py - 1f, e.x - ew(e) / 2f, e.y - eh(e), e.x + ew(e) / 2f, e.y)) {
                playerHurt(if (e.type == E_BAT || e.type == E_MEDUSA) 1 else 2, e.x)
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
            E_KNIGHT -> {
                if (abs(px - e.x) < 170f && abs(py - e.y) < 50f) {
                    e.face = toP
                    if (abs(px - e.x) > 46f) e.x += e.face * 30f * dt
                } else {
                    e.x += e.face * 24f * dt
                    if (e.x < e.x0 + 10f) e.face = 1
                    if (e.x > e.x1 - 10f) e.face = -1
                }
                e.x = e.x.coerceIn(e.x0 + 4f, e.x1 - 4f)
            }
            E_BAT -> {
                e.t += dt
                if (abs(px - e.x) < 300f) { e.x += toP * 62f * dt; e.face = toP }
                e.y = e.baseY + sin(e.t * 3f) * 26f
            }
            E_MEDUSA -> {
                e.t += dt
                if (e.st == 0 && abs(px - e.x) < 430f) { e.st = 1; e.face = toP }
                if (e.st == 1) {
                    e.x += e.face * 78f * dt
                    e.y = e.baseY + sin(e.t * 3.4f) * 36f
                    if (abs(e.x - px) > 520f) e.dead = true
                }
            }
            E_GHOST -> {
                e.t += dt
                val behind = e.aggro || (e.x - px) * face < 0f // persigue si el jugador mira hacia otro lado
                e.st = if (behind) 1 else 0 // 1 = persigue
                if (e.st == 1 && abs(px - e.x) < 380f) {
                    e.x += toP * 52f * dt
                    e.y += ((py - 24f) - e.y).coerceIn(-40f * dt, 40f * dt)
                } else e.y += (e.baseY + sin(e.t * 2f) * 8f - e.y) * min(1f, dt * 2f)
                e.face = toP
            }
            E_SPIDER -> {
                when (e.st) {
                    0 -> if (abs(px - e.x) < 42f && py > e.floor - 10f) { e.st = 1; e.vy = 0f }
                    1 -> {
                        e.vy = min(420f, e.vy + 1100f * dt)
                        e.y += e.vy * dt
                        if (e.y >= e.floor) { e.y = e.floor; e.st = 2; ev(EV_LAND, e.x, e.floor) }
                    }
                    else -> {
                        e.face = toP
                        if (abs(px - e.x) < 260f) e.x += toP * 44f * dt
                        e.x = e.x.coerceIn(e.x0 + 4f, e.x1 - 4f)
                    }
                }
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
    private fun nearestTarget(x: Float, y: Float): FloatArray? {
        var best: FloatArray? = null
        var bd = 1e9f
        for (e in enemies) if (!e.dead) {
            val d = (e.x - x) * (e.x - x) + (e.y - eh(e) / 2f - y) * (e.y - eh(e) / 2f - y)
            if (d < bd && abs(e.x - x) < 420f) { bd = d; best = floatArrayOf(e.x, e.y - eh(e) / 2f) }
        }
        if (bossStarted && bstate != BS_DEAD && bstate != BS_INTRO) {
            val d = (bx - x) * (bx - x) + (AdvCfg.GROUND - bdef.h / 2f - y) * (AdvCfg.GROUND - bdef.h / 2f - y)
            if (d < bd) best = floatArrayOf(bx, AdvCfg.GROUND - bdef.h / 2f)
        }
        return best
    }

    private fun updateProjs(dt: Float) {
        val frozen = freezeT > 0f
        val it = projs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.mine && frozen) continue
            p.t += dt
            p.life -= dt
            if (p.homing && p.mine) {
                val tg = nearestTarget(p.x, p.y)
                if (tg != null) {
                    val ax = tg[0] - p.x
                    val ay = tg[1] - p.y
                    val d = sqrt(ax * ax + ay * ay).coerceAtLeast(1f)
                    p.vx += (ax / d * 260f - p.vx) * min(1f, dt * 5f)
                    p.vy += (ay / d * 260f - p.vy) * min(1f, dt * 5f)
                }
            }
            if (p.kind == 3 && p.mine) {
                if (p.t > 0.55f) p.vx -= (if (p.vx > 0f) 1f else -1f) * 760f * dt
                if (p.t > 0.55f) { val back = if (px >= p.x) 1f else -1f; if (abs(p.vx) < 30f) p.vx = back * 30f }
                if (p.t > 0.9f && abs(px - p.x) < 20f && abs(py - 15f - p.y) < 30f) { it.remove(); continue }
            }
            p.vy += p.grav * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f || p.x < camX - 120f || p.x > camX + viewW + 120f || p.y > AdvCfg.H + 40f || (p.y < -80f)) { it.remove(); continue }
            if (p.mine) {
                if (p.kind == 4) {
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
                        val blocked = e.type == E_KNIGHT && !p.armor && (p.x - e.x) * e.face > 0f
                        hurtEnemy(e, p.dmg, p.x, p.armor)
                        if (!p.pierce || blocked) { gone = true; break }
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
                    for (e in enemies) if (!e.dead && overlap(h.x0, h.y0, h.x1, h.y1, e.x - ew(e) / 2f, e.y - eh(e), e.x + ew(e) / 2f, e.y)) hurtEnemy(e, h.dmg, e.x, true)
                    if (bossStarted && bstate != BS_DEAD && bstate != BS_INTRO &&
                        overlap(h.x0, h.y0, h.x1, h.y1, bx - bdef.w / 2f, AdvCfg.GROUND - bdef.h, bx + bdef.w / 2f, AdvCfg.GROUND)) hurtBoss(h.dmg)
                    for (c in candles) if (c.alive && c.x > h.x0 - 6f && c.x < h.x1 + 6f && c.y > h.y0 && c.y - 28f < h.y1) breakCandle(c)
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
        val lim = if (bhp * 3 > bmax * 2) min(3, ps.size) else if (bhp * 3 > bmax) min(5, ps.size) else ps.size
        var p: Int
        var tries = 0
        do { p = ps[rnd.nextInt(max(2, lim).coerceAtMost(ps.size))]; tries++ } while (p == bpat && tries < 5)
        return p
    }

    private fun teleTime(p: Int) = when (p) {
        AdvCfg.P_CHARGE -> 0.7f; AdvCfg.P_SLAM -> 0.6f; AdvCfg.P_BEAM -> 0.8f; AdvCfg.P_TELE -> 0.45f
        AdvCfg.P_ULT -> 1.1f; AdvCfg.P_RAIN -> 0.35f; AdvCfg.P_WAVE -> 0.55f; AdvCfg.P_METEOR -> 0.5f
        AdvCfg.P_SPIRAL -> 0.5f; else -> 0.4f
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
        if (freezeT > 0f && bstate != BS_INTRO) return
        val toP = if (px >= bx) 1 else -1
        if (bstate != BS_INTRO && invuln <= 0f && deadT <= 0f &&
            overlap(px - 9f, py - 29f, px + 9f, py - 1f, bx - bdef.w / 2f + 4f, AdvCfg.GROUND - bdef.h + 4f, bx + bdef.w / 2f - 4f, AdvCfg.GROUND)) {
            playerHurt(if (bstate == BS_ACT && bpat == AdvCfg.P_CHARGE) 3 else 2, bx)
        }
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
            AdvCfg.P_WAVE -> {
                hazs.add(AdvHaz(bx - 10f, AdvCfg.GROUND - 18f, bx + 10f, AdvCfg.GROUND, 170f, 0f, 3.2f, 2, false, 4))
                hazs.add(AdvHaz(bx - 10f, AdvCfg.GROUND - 18f, bx + 10f, AdvCfg.GROUND, -170f, 0f, 3.2f, 2, false, 4))
                shake = 5f
                ev(EV_SLAM, bx, AdvCfg.GROUND)
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
            AdvCfg.P_WAVE -> if (bst > 0.5f) endAct(1.1f)
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
            AdvCfg.P_SPIRAL -> {
                bt2 += dt
                val period = if (benraged) 0.09f else 0.12f
                if (bt2 >= period) {
                    bt2 -= period
                    bcount++
                    fire(bx, my, bcount * 0.5f, 118f, 2, 5f)
                    fire(bx, my, bcount * 0.5f + 3.1416f, 118f, 2, 5f)
                }
                if (bst > 2.2f) endAct(1.1f)
            }
            AdvCfg.P_METEOR -> {
                bt2 += dt
                val period = if (benraged) 0.14f else 0.2f
                if (bt2 >= period) {
                    bt2 -= period
                    bcount++
                    val x = if (bcount % 3 == 0) px else arenaX + 30f + rnd.nextFloat() * (viewW - 60f)
                    projs.add(AdvProj(x, -20f, (rnd.nextFloat() - 0.5f) * 40f, 210f, 0, 2, false, 8f).also { it.life = 3.2f })
                }
                if (bst > 1.8f) endAct(1.1f)
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
