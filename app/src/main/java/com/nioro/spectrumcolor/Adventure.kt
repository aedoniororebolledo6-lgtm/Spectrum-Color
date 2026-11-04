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
class AdvCandle(val x: Float, val y: Float, val drop: Int, val arg: Int) { var alive = true; var hang = false }
class AdvItem(var x: Float, var y: Float, val kind: Int, val arg: Int) {
    var prize = false
    var ox = x
    var oy = y
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
    var ox = x
    var oy = y
    var age = 0f
    /** Retroceso al recibir un golpe (se frena solo) y aturdimiento breve que detiene la IA. */
    var kvx = 0f
    var stun = 0f
    /** Élite: más vida, más rápido y más daño (mundos avanzados). */
    var elite = false
    /** Slime pequeño (nace al dividirse uno grande). */
    var small = false
    var cd = 0f
    var tx = 0f
    var ty = 0f
    /** Visibilidad (el mago se desvanece al teletransportarse). */
    var vis = 1f
}
class AdvProj(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val dmg: Int, val mine: Boolean, val r: Float) {
    var grav = 0f
    var life = 3f
    var pierce = false
    var armor = false
    var homing = false
    var ox = x
    var oy = y
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
    var ox = x0
    var oy = y0
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
/** Sala del mapa. nb: vecinos W,E,ARRIBA,ABAJO (-1 = sin salida). lock: puerta con llave en ese lado. */
class AdvRoom(val id: Int, val gx: Int, val gy: Int) {
    var kind = 0
    val nb = IntArray(4) { -1 }
    val lock = BooleanArray(4)
    var seen = false
    var visited = false
    var prize = -1
    var prizeArg = 0
    var taken = false
    var cleared = false
    var guarded = false
    var secret = false
    var region = 0
    var depth = 0
    var len = 0f
    var seed = 0L
}
/** Bloque sólido (paredes, techos, pilares). Los rompibles (brk) se destruyen con el látigo. */
class AdvBlock(val x0: Float, val x1: Float, val y0: Float, val y1: Float, val brk: Boolean) {
    var plat: AdvPlat? = null
}
/** Resorte: al caer sobre él lanza hacia arriba. */
class AdvSpring(val x0: Float, val x1: Float, val y: Float) {
    var t = 0f
}
/** Trampa o zona de relleno. kind: ver Adv.TR_*; a, b, c dependen del tipo. */
class AdvTrap(val kind: Int, val x: Float, val y: Float, val a: Float, val b: Float, val c: Float) {
    var cx = x
    var cy = y
    var warn = false
    var on = false
    var ph = 0f
    var hitCd = 0f
}
/** Puerta de piso (escalera) hacia la sala de arriba o de abajo. */
class AdvDoor(val x: Float, val up: Boolean, val to: Int)
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
        const val EV_UNLOCK = 26
        const val EV_LOCKED = 27
        const val EV_ROOM = 28
        const val EV_FALL = 29
        const val EV_DOOR = 30
        const val EV_BREAK = 31
        const val EV_SPRING = 32
        const val EV_BOOM = 33
        const val EV_SPLIT = 34
        const val EV_TELEPORT = 35

        const val IT_HEART = 0
        const val IT_BIG = 1
        const val IT_MEAT = 2
        const val IT_SUB = 3
        const val IT_WHIP = 4
        const val IT_ORB = 5
        const val IT_BOOTS = 6
        const val IT_POTION = 7
        const val IT_KEY = 8
        const val IT_HPUP = 9

        // tipos de sala
        const val R_NORMAL = 0
        const val R_START = 1
        const val R_SAVE = 2
        const val R_BOSS = 3
        const val R_KEY = 4
        const val R_TREAS = 5
        const val D_W = 0
        const val D_E = 1
        const val D_UP = 2
        const val D_DN = 3

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
        const val E_POP = 10   // torreta que emerge del piso
        const val E_CEIL = 11  // gárgola colgante del techo
        const val E_ARCHER = 12 // arquero: mantiene la distancia y apunta con tiro parabólico (se adelanta a tu movimiento)
        const val E_SLIME = 13  // slime saltarín: al morir se divide en dos pequeños
        const val E_BOMBER = 14 // bombardero volador: se pone encima y suelta bombas que explotan en el suelo
        const val E_MAGE = 15   // mago: se teletransporta cerca, lanza orbes y queda expuesto un momento
        const val E_WOLF = 16   // lobo: se agacha y salta hacia donde vas a estar
        const val E_SKULL = 17  // calavera: vuela en círculos y se lanza en picada
        const val E_GOLEM = 18  // gólem: lento y duro; golpea el piso y lanza ondas por el suelo
        const val ENEMY_TYPES = 19
        const val TR_SAW = 0    // sierra rodante en el piso: x..a, velocidad b
        const val TR_RAIL = 1   // sierra en riel: (x,y)->(a,b), velocidad c
        const val TR_BOB = 2    // orbe que sube y baja: centro (x,y), amplitud a, velocidad b
        const val TR_ORBIT = 3  // orbes que giran: pivote (x,y), radio a, velocidad b, cantidad c
        const val TR_CRUSH = 4  // prensa de techo: x, ancho a, periodo b
        const val TR_LASER = 5  // rayo vertical: x, periodo b
        const val TR_ZAP = 6    // piso eléctrico: x..a, periodo b
        const val TR_CONV = 7   // cinta transportadora: x..a, velocidad b
        const val TR_ICE = 8    // piso resbaloso: x..a
        const val CRUSH_UP = 60f

        const val BS_INTRO = 0
        const val BS_WALK = 1
        const val BS_TELE = 2
        const val BS_ACT = 3
        const val BS_STUN = 4
        const val BS_DEAD = 5

        private val ehp = intArrayOf(2, 1, 3, 2, 3, 4, 1, 5, 2, 2, 3, 3, 2, 3, 2, 3, 3, 2, 7)

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
        private const val S_GAUNTLET = 12
    }

    val rnd = Random(world * 7919L + 31L)
    val plats = ArrayList<AdvPlat>()
    val spikes = ArrayList<FloatArray>()
    val candles = ArrayList<AdvCandle>()
    val enemies = ArrayList<AdvEnemy>()
    val rooms = ArrayList<AdvRoom>()
    val doors = ArrayList<AdvDoor>()
    val blocks = ArrayList<AdvBlock>()
    val springs = ArrayList<AdvSpring>()
    private val secretSpots = ArrayList<FloatArray>()
    private val recent = ArrayList<Int>()
    var cols = 9
    var rows = 5
    var cur = 0
    var keys = 0
    var maxHp = AdvCfg.MAXHP
    var statueX = -1f
    var roomLen = 1000f
    var fade = 0f
    var lockMsgT = 0f
    var saveRoom = 0
    var bossId = 0
    var lastSaveFlash = 0f
    private var touchedSave = false
    private var safeX = 52f
    private var safeY = AdvCfg.GROUND
    private var pendRoom = -1
    private var pendMode = 0
    private var pendX = 0f
    private var enterCd = 0f
    val items = ArrayList<AdvItem>()
    val projs = ArrayList<AdvProj>()
    val hazs = ArrayList<AdvHaz>()
    val events = ArrayList<AdvEv>()
    val movers = ArrayList<AdvMover>()
    val crumbles = ArrayList<AdvCrumble>()
    val jets = ArrayList<AdvJet>()
    val pends = ArrayList<AdvPend>()
    val rocks = ArrayList<AdvRock>()
    val traps = ArrayList<AdvTrap>()
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
    var opx = 40f
    var opy = AdvCfg.GROUND
    var ocam = 0f
    var obx = 0f
    var alpha = 1f
    var bz = 0f
    var bteleMax = 0.5f
    var riding: AdvMover? = null
    var kills = 0
    private var acc = 0f
    private val swingHit = HashSet<Any>()
    private var bibleTick = 0f

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


    // ------------------------------------------------------------------ construcción del nivel
    private fun ground(x0: Float, x1: Float) { plats.add(AdvPlat(x0, x1, AdvCfg.GROUND)) }

    private fun allowed(): IntArray = when (world) {
        0 -> intArrayOf(E_WALKER, E_BAT, E_SLIME)
        1 -> intArrayOf(E_WALKER, E_BAT, E_HOPPER, E_MEDUSA, E_SLIME)
        2 -> intArrayOf(E_WALKER, E_BAT, E_THROWER, E_SPIDER, E_BOMBER)
        3 -> intArrayOf(E_WALKER, E_HOPPER, E_TURRET, E_KNIGHT, E_ARCHER)
        4 -> intArrayOf(E_BAT, E_THROWER, E_CHARGER, E_MEDUSA, E_WOLF, E_SLIME)
        5 -> intArrayOf(E_KNIGHT, E_GHOST, E_BAT, E_THROWER, E_MAGE, E_SKULL)
        6 -> intArrayOf(E_GHOST, E_MEDUSA, E_TURRET, E_CHARGER, E_BOMBER, E_SKULL, E_MAGE)
        7 -> intArrayOf(E_KNIGHT, E_CHARGER, E_HOPPER, E_TURRET, E_GOLEM, E_WOLF, E_BOMBER)
        8 -> intArrayOf(E_SPIDER, E_WALKER, E_MEDUSA, E_KNIGHT, E_WOLF, E_SLIME, E_ARCHER, E_GOLEM)
        9 -> intArrayOf(E_GHOST, E_HOPPER, E_THROWER, E_KNIGHT, E_ARCHER, E_SKULL, E_MAGE, E_WOLF)
        10 -> intArrayOf(E_GHOST, E_MAGE, E_SKULL, E_BOMBER, E_ARCHER, E_KNIGHT, E_TURRET, E_MEDUSA, E_GOLEM)
        else -> intArrayOf(E_WALKER, E_BAT, E_THROWER, E_HOPPER, E_TURRET, E_CHARGER, E_MEDUSA, E_KNIGHT, E_GHOST, E_SPIDER,
            E_ARCHER, E_SLIME, E_BOMBER, E_MAGE, E_WOLF, E_SKULL, E_GOLEM)
    }

    /** Multiplicador de rapidez de los enemigos: crece con el mundo. */
    private val wSpd = 1f + world * 0.035f
    private fun spd(e: AdvEnemy): Float = wSpd * (if (e.elite) 1.15f else 1f)

    /** Probabilidad (en %) de que un enemigo salga élite. */
    private fun eliteChance(): Int = if (world < 6) 0 else 6 + (world - 6) * 3

    private fun canBeElite(type: Int) = type != E_POP && type != E_CEIL && type != E_SPIDER

    fun makeElite(e: AdvEnemy) {
        if (e.elite || !canBeElite(e.type)) return
        e.elite = true
        e.hp = e.hp * 2 + 1
    }

    /** Enemigos que caminan por el suelo y pueden retroceder al recibir un golpe. */
    fun groundBody(type: Int) = type == E_WALKER || type == E_KNIGHT || type == E_HOPPER || type == E_CHARGER || type == E_THROWER ||
        type == E_ARCHER || type == E_SLIME || type == E_MAGE || type == E_WOLF || (type == E_SPIDER)

    fun ranged(type: Int) = type == E_TURRET || type == E_THROWER || type == E_ARCHER || type == E_MAGE || type == E_BOMBER

    fun flyer(type: Int) = type == E_BAT || type == E_MEDUSA || type == E_GHOST || type == E_BOMBER || type == E_SKULL

    private fun addEnemy(type: Int, x: Float, y: Float, x0: Float, x1: Float): AdvEnemy {
        val hp = ehp[type] + world / 4
        val ey = when (type) {
            E_BAT -> y - 70f - rnd.nextInt(30)
            E_MEDUSA -> y - 55f - rnd.nextInt(30)
            E_GHOST -> y - 40f - rnd.nextInt(20)
            E_SPIDER -> y - 105f
            E_BOMBER -> y - 96f - rnd.nextInt(14) // al alcance de un salto con látigo
            E_SKULL -> y - 75f - rnd.nextInt(30)
            else -> y
        }
        val e = AdvEnemy(type, x, ey, x0, x1, hp)
        e.face = if (rnd.nextBoolean()) 1 else -1
        e.baseY = e.y
        e.floor = y
        e.t = rnd.nextFloat() * 2f
        e.tx = x
        e.cd = 0.5f + rnd.nextFloat()
        if (canBeElite(type) && rnd.nextInt(100) < eliteChance()) makeElite(e)
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
            val g = al.filter { !flyer(it) && it != E_SPIDER && it != E_TURRET && it != E_THROWER && it != E_ARCHER && it != E_MAGE }.toIntArray()
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
        val w = IntArray(13)
        if (world >= 7) w[S_GAUNTLET] = 4 + (world - 7) * 3
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
        if (last == S_GAUNTLET || amb >= 2 && target <= 560f) { w[S_GAUNTLET] = 0 }
        var tot = 0
        for (v in w) tot += v
        var r = rnd.nextInt(max(1, tot))
        for (i in w.indices) { if (r < w[i]) return i; r -= w[i] }
        return S_FLAT
    }

    /** Añade un tramo y devuelve la nueva x. */
    private fun addSeg(k: Int, cx0: Float, diff: Float): Float {
        var cx = cx0
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
            S_GAUNTLET -> {
                // desafío de élite: dos cornisas con tiradores y un guardián élite abajo (mundos finales)
                val len = 460f
                ground(cx, cx + len)
                val g = AdvCfg.GROUND
                ledge(cx + 40f, cx + 130f, g - 80f)
                ledge(cx + len - 130f, cx + len - 40f, g - 80f)
                val shooters = intArrayOf(E_ARCHER, E_THROWER, E_MAGE)
                addEnemy(shooters[rnd.nextInt(shooters.size)], cx + 85f, g - 80f, cx + 50f, cx + 120f)
                if (world >= 9) addEnemy(shooters[rnd.nextInt(shooters.size)], cx + len - 85f, g - 80f, cx + len - 120f, cx + len - 50f)
                else addCandle(cx + len - 85f, g - 80f)
                val guards = intArrayOf(E_GOLEM, E_KNIGHT, E_WOLF)
                val gd = addEnemy(guards[rnd.nextInt(guards.size)], cx + len / 2f, g, cx + 150f, cx + len - 150f)
                makeElite(gd)
                hangCandle(cx + len / 2f, g - 120f)
                cx += len
            }
            else -> { // S_AMBUSH
                val len = viewW + 60f
                ground(cx, cx + len)
                if (!rooms[cur].cleared) ambushes.add(AdvAmbush(cx, cx + viewW))
                cx += len
            }
        }
        return cx
    }

    // ------------------------------------------------------------------ mapa del mundo
    private fun cellOf(x: Int, y: Int): AdvRoom? { for (r in rooms) if (r.gx == x && r.gy == y) return r; return null }

    private fun link(a: AdvRoom, d: Int, b: AdvRoom) { a.nb[d] = b.id; b.nb[d xor 1] = a.id }

    private fun bfsDepth(): IntArray {
        val par = IntArray(rooms.size) { -2 }
        val q = ArrayList<Int>()
        q.add(0); par[0] = -1; rooms[0].depth = 0
        var h = 0
        while (h < q.size) {
            val a = rooms[q[h++]]
            for (d in 0..3) {
                val n = a.nb[d]
                if (n >= 0 && par[n] == -2) { par[n] = a.id; rooms[n].depth = a.depth + 1; q.add(n) }
            }
        }
        return par
    }

    private fun deg(r: AdvRoom): Int { var n = 0; for (d in 0..3) if (r.nb[d] >= 0) n++; return n }

    private fun buildWorld() {
        val gr = Random(world * 104729L + 17L)
        val n = 14 + world
        rooms.clear()
        val s = AdvRoom(0, 1, 1 + gr.nextInt(rows - 2))
        s.kind = R_START
        rooms.add(s)
        val dx = intArrayOf(-1, 1, 0, 0)
        val dy = intArrayOf(0, 0, -1, 1)
        var tries = 0
        while (rooms.size < n - 1 && tries < 6000) {
            tries++
            val a = rooms[gr.nextInt(rooms.size)]
            val d = if (gr.nextInt(100) < 62) gr.nextInt(2) else 2 + gr.nextInt(2)
            val gx = a.gx + dx[d]
            val gy = a.gy + dy[d]
            if (gx < 0 || gx >= cols || gy < 0 || gy >= rows || cellOf(gx, gy) != null) continue
            val b = AdvRoom(rooms.size, gx, gy)
            rooms.add(b)
            link(a, d, b)
        }
        var par = bfsDepth()
        // jefe: al este de la sala más lejana que tenga el hueco libre
        val cand = rooms.sortedByDescending { it.depth }
        var boss: AdvRoom? = null
        for (a in cand) {
            if (a.nb[D_E] == -1 && a.gx + 1 < cols && cellOf(a.gx + 1, a.gy) == null) {
                val b = AdvRoom(rooms.size, a.gx + 1, a.gy)
                b.kind = R_BOSS
                rooms.add(b)
                link(a, D_E, b)
                boss = b
                break
            }
        }
        if (boss == null) { // no debería pasar: ampliar la rejilla
            cols++
            val last = rooms.filter { it.nb[D_E] == -1 }.maxByOrNull { it.gx }!!
            val b2 = AdvRoom(rooms.size, last.gx + 1, last.gy)
            b2.kind = R_BOSS
            rooms.add(b2)
            link(last, D_E, b2)
            cols = max(cols, last.gx + 2)
            boss = b2
        }
        bossId = boss.id
        par = bfsDepth()
        // camino principal inicio -> jefe
        val path = ArrayList<AdvRoom>()
        var c = boss.id
        while (c != -1) { path.add(0, rooms[c]); c = par[c] }
        // 1) la sala previa al jefe es de guardado
        if (path.size >= 3) path[path.size - 2].kind = R_SAVE
        // 2) puertas con llave en tramos horizontales del camino
        val horiz = ArrayList<Int>()
        for (i in 1 until path.size - 1) if (path[i].nb[D_E] == path[i + 1].id || path[i].nb[D_W] == path[i + 1].id) horiz.add(i)
        val wantLocks = min(horiz.size, 1 + world / 4)
        val lockIdx = ArrayList<Int>()
        for (j in 1..wantLocks) {
            val tgt = (path.size * j / (wantLocks + 1)).coerceAtLeast(1)
            val pick = horiz.filter { it !in lockIdx }.minByOrNull { abs(it - tgt) } ?: break
            lockIdx.add(pick)
        }
        lockIdx.sort()
        for (i in lockIdx) {
            val a = path[i]; val b = path[i + 1]
            val d = if (a.nb[D_E] == b.id) D_E else D_W
            a.lock[d] = true; b.lock[d xor 1] = true
        }
        // regiones (tras cruzar cada puerta con llave)
        val reg = IntArray(rooms.size) { -1 }
        run {
            val q = ArrayList<Int>(); q.add(0); reg[0] = 0
            var h = 0
            while (h < q.size) {
                val a = rooms[q[h++]]
                for (d in 0..3) {
                    val nn = a.nb[d]
                    if (nn >= 0 && reg[nn] == -1) { reg[nn] = reg[a.id] + (if (a.lock[d]) 1 else 0); q.add(nn) }
                }
            }
        }
        for (r in rooms) r.region = max(0, reg[r.id])
        // 3) llaves: una por puerta, en una sala de la región anterior
        fun free(r: AdvRoom) = r.kind == R_NORMAL && r.prize < 0
        val lockCount = lockIdx.size
        for (i in 1..lockCount) {
            var cs = rooms.filter { free(it) && it.region == i - 1 }
            var leaves = cs.filter { deg(it) == 1 }
            if (leaves.isNotEmpty()) cs = leaves
            if (cs.isEmpty()) cs = rooms.filter { free(it) && it.region < i }
            if (cs.isEmpty()) { // sin sitio: quitar esa puerta
                val ix = lockIdx[i - 1]
                val a = path[ix]; val b = path[ix + 1]
                val d = if (a.nb[D_E] == b.id) D_E else D_W
                a.lock[d] = false; b.lock[d xor 1] = false
                continue
            }
            val kr = cs[gr.nextInt(cs.size)]
            kr.kind = R_KEY; kr.prize = IT_KEY; kr.guarded = true
        }
        // 4) tesoros: vida máxima x3, botas, látigo
        val treas = intArrayOf(IT_HPUP, IT_HPUP, IT_HPUP, IT_BOOTS, IT_WHIP)
        for (t in treas) {
            var cs = rooms.filter { free(it) && deg(it) == 1 }
            if (cs.isEmpty()) cs = rooms.filter { free(it) }
            if (cs.isEmpty()) break
            val tr = cs[gr.nextInt(cs.size)]
            tr.kind = R_TREAS; tr.prize = t; tr.guarded = true
        }
        // 5) estatuas de guardado cada ~4 salas del camino
        var i = 4
        while (i < path.size - 2) { if (path[i].kind == R_NORMAL) path[i].kind = R_SAVE; i += 4 }
        // más estatuas: que ninguna sala quede a más de 4 pasos de una
        for (iter in 0 until 8) {
            val dist = IntArray(rooms.size) { -1 }
            val q = ArrayList<Int>()
            for (r in rooms) if (r.kind == R_START || r.kind == R_SAVE) { dist[r.id] = 0; q.add(r.id) }
            var h = 0
            while (h < q.size) {
                val a = rooms[q[h++]]
                for (d in 0..3) { val nn = a.nb[d]; if (nn >= 0 && dist[nn] == -1) { dist[nn] = dist[a.id] + 1; q.add(nn) } }
            }
            val far = rooms.filter { it.kind != R_BOSS && dist[it.id] >= 5 }.maxByOrNull { dist[it.id] } ?: break
            // cadena hacia la estatua más cercana: elegir una sala normal a distancia <= 3
            var c = far
            var pick: AdvRoom? = null
            while (true) {
                if (dist[c.id] <= 3 && c.kind == R_NORMAL && dist[c.id] >= 1) { pick = c; break }
                var nx: AdvRoom? = null
                for (d in 0..3) { val nn = c.nb[d]; if (nn >= 0 && dist[nn] == dist[c.id] - 1) { nx = rooms[nn]; break } }
                if (nx == null || dist[c.id] <= 1) break
                c = nx
            }
            if (pick == null) break
            pick.kind = R_SAVE
        }
        for (r in rooms) if (r.kind == R_NORMAL && world >= 1 && gr.nextInt(100) < 20) r.guarded = true
        // 6) lazos: conexiones extra dentro de una misma región
        for (a in rooms) {
            if (a.kind == R_BOSS) continue
            for (d in intArrayOf(D_E, D_DN)) {
                if (a.nb[d] != -1) continue
                val b = cellOf(a.gx + (if (d == D_E) 1 else 0), a.gy + (if (d == D_DN) 1 else 0)) ?: continue
                if (b.kind == R_BOSS || b.region != a.region || b.nb[d xor 1] != -1) continue
                if (gr.nextInt(100) < 45) link(a, d, b)
            }
        }
        for (r in rooms) r.seed = world * 1000003L + r.id * 7717L + 11L
        rooms[0].seen = true
        rooms[0].visited = true
    }

    // ------------------------------------------------------------------ estructuras (planos de casillas)
    private fun groundedTypes(): IntArray {
        val g = allowed().filter { it == E_WALKER || it == E_HOPPER || it == E_CHARGER || it == E_KNIGHT || it == E_THROWER || it == E_TURRET ||
            it == E_ARCHER || it == E_SLIME || it == E_MAGE || it == E_WOLF || it == E_GOLEM }
        return if (g.isEmpty()) intArrayOf(E_WALKER) else g.toIntArray()
    }

    private fun flyerTypes(): IntArray = allowed().filter { flyer(it) }.toIntArray()

    /** Tramo horizontal libre alrededor de x sobre el suelo fy, limitado por bloques y por la estructura. */
    private fun patrol(x: Float, fy: Float, lim0: Float, lim1: Float): FloatArray {
        var lo = x
        var hi = x
        for (p in plats) if (abs(p.y - fy) < 1f && x >= p.x0 - 0.5f && x <= p.x1 + 0.5f) { lo = p.x0; hi = p.x1 }
        var ch = true
        while (ch) {
            ch = false
            for (p in plats) if (abs(p.y - fy) < 1f) {
                if (p.x1 >= lo - 1f && p.x0 < lo - 0.5f) { lo = p.x0; ch = true }
                if (p.x0 <= hi + 1f && p.x1 > hi + 0.5f) { hi = p.x1; ch = true }
            }
        }
        for (b in blocks) if (b.y1 > fy - 28f && b.y0 < fy - 1f) {
            if (b.x1 <= x && b.x1 > lo) lo = b.x1
            if (b.x0 >= x && b.x0 < hi) hi = b.x0
        }
        lo = max(lo, lim0); hi = min(hi, lim1)
        if (hi - lo < 36f) return floatArrayOf(x - 14f, x + 14f)
        return floatArrayOf(lo + 8f, hi - 8f)
    }

    /** Coloca un plano en x = cx0 y devuelve la x final. */
    private fun placeBp(bp: AdvStructs.Bp, cx: Float): Float {
        val rowsN = bp.rows.size
        val w = bp.w
        fun row(j: Int): String = bp.rows[rowsN - 1 - j]
        fun top(j: Int) = AdvCfg.GROUND - 20f * j
        val secretDone = rooms[cur].secret
        val g = row(0)
        var x = 0
        while (x < w) {
            if (g[x] == '_' || g[x] == 'S') {
                var e = x
                while (e + 1 < w && (g[e + 1] == '_' || g[e + 1] == 'S')) e++
                ground(cx + x * 20f, cx + (e + 1) * 20f)
                for (q in x..e) if (g[q] == 'S') springs.add(AdvSpring(cx + q * 20f, cx + (q + 1) * 20f, AdvCfg.GROUND))
                x = e + 1
            } else x++
        }
        var mFirst = -1
        var mLast = -1
        var mFast = false
        var q = 0
        while (q < w) {
            val ch = g[q]
            if (ch == 'M' || ch == 'N') { if (mFirst < 0) mFirst = q; mLast = q; mFast = ch == 'N' }
            if (ch == 'k') {
                var e = q
                if (e + 1 < w && g[e + 1] == 'k') e++
                crumbles.add(AdvCrumble(cx + q * 20f + 1f, cx + (e + 1) * 20f - 1f, AdvCfg.GROUND))
                q = e
            }
            q++
        }
        if (mFirst >= 0) movers.add(AdvMover(cx + (mFirst + 0.5f) * 20f, cx + (mLast + 0.5f) * 20f, AdvCfg.GROUND, AdvCfg.GROUND, 56f, if (mFast) 1.3f else 0.95f, rnd.nextFloat() * 6f))
        // geometría
        for (j in 1 until rowsN) {
            val r = row(j)
            val up = if (j + 1 < rowsN) row(j + 1) else ".".repeat(w)
            var c = 0
            while (c < w) {
                val ch = r[c]
                if (ch == '#' || ch == 'X' || ch == '=' || ch == 'k') {
                    var e = c
                    while (e + 1 < w && r[e + 1] == ch) e++
                    val x0 = cx + c * 20f
                    val x1 = cx + (e + 1) * 20f
                    val y0 = top(j)
                    when (ch) {
                        '#' -> {
                            blocks.add(AdvBlock(x0, x1, y0, y0 + 20f, false))
                            var sI = c
                            while (sI <= e) {
                                if (up[sI] != '#' && up[sI] != 'X') {
                                    var t = sI
                                    while (t + 1 <= e && up[t + 1] != '#' && up[t + 1] != 'X') t++
                                    plats.add(AdvPlat(cx + sI * 20f, cx + (t + 1) * 20f, y0))
                                    sI = t + 1
                                } else sI++
                            }
                        }
                        'X' -> if (!secretDone) {
                            val b = AdvBlock(x0, x1, y0, y0 + 20f, true)
                            val p = AdvPlat(x0, x1, y0)
                            b.plat = p
                            blocks.add(b)
                            plats.add(p)
                        }
                        '=' -> plats.add(AdvPlat(x0, x1, y0))
                        else -> {
                            var sI = c
                            while (sI <= e) {
                                val t = min(e, sI + 1)
                                crumbles.add(AdvCrumble(cx + sI * 20f + 1f, cx + (t + 1) * 20f - 1f, y0))
                                sI = t + 1
                            }
                        }
                    }
                    c = e + 1
                } else c++
            }
        }
        // objetos y enemigos
        val lim0 = cx + 40f
        val lim1 = cx + (w - 2) * 20f
        for (j in 1 until rowsN) {
            val r = row(j)
            for (c in 0 until w) {
                val ch = r[c]
                val xx = cx + (c + 0.5f) * 20f
                val fy = top(j) + 20f
                when (ch) {
                    'c' -> addCandle(xx, fy)
                    '^' -> if (c == 0 || r[c - 1] != '^') {
                        var e = c
                        while (e + 1 < w && r[e + 1] == '^') e++
                        spikes.add(floatArrayOf(cx + c * 20f + 1f, cx + (e + 1) * 20f - 1f))
                    }
                    'j' -> jets.add(AdvJet(xx, AdvCfg.GROUND, 2.7f, rnd.nextFloat() * 2.7f))
                    'p' -> { val len = AdvCfg.GROUND - 20f - top(j); if (len >= 60f) pends.add(AdvPend(xx, top(j), len, 1.4f, rnd.nextFloat() * 6f)) }
                    'r' -> rocks.add(AdvRock(xx, top(j)))
                    'R' -> if (bp.hasX) { if (!secretDone) secretSpots.add(floatArrayOf(xx, fy - 6f)) } else {
                        val rr = rnd.nextInt(100)
                        var arg = 0
                        val kind = when { rr < 40 -> IT_BIG; rr < 60 -> IT_MEAT; rr < 92 -> { arg = 1 + rnd.nextInt(AdvCfg.SUBS); IT_SUB }; else -> IT_POTION }
                        items.add(AdvItem(xx, fy - 6f, kind, arg))
                    }
                    '?' -> {
                        val al = groundedTypes()
                        val pr = patrol(xx, fy, lim0, lim1)
                        addEnemy(al[rnd.nextInt(al.size)], xx, fy, pr[0], pr[1])
                    }
                    'f' -> {
                        val al = flyerTypes()
                        if (al.isNotEmpty()) addEnemy(al[rnd.nextInt(al.size)], xx, fy, max(lim0 - 20f, xx - 100f), min(lim1 + 20f, xx + 100f))
                    }
                    'P' -> makerStart = xx
                    'E' -> makerGoalX = xx.also { makerGoalY = fy }
                    'v' -> items.add(AdvItem(xx, fy - 6f, IT_HEART, 0))
                    'V' -> items.add(AdvItem(xx, fy - 6f, IT_MEAT, 0))
                    else -> {
                        // enemigos con letra propia (editor "Taller"): se colocan justo en esa casilla
                        val et = AdvMk.ENEMIES.indexOf(ch)
                        if (et >= 0) {
                            val type = AdvMk.TYPES[et]
                            val e = if (type == E_POP) popAt(xx).also { it.floor = fy; it.y = fy + 26f }
                            else if (type == E_CEIL) ceilAt(xx, xx - 90f, xx + 90f).also { it.y = top(j) + 10f; it.baseY = it.y }
                            else if (flyer(type)) addEnemy(type, xx, fy, xx - 110f, xx + 110f).also { it.y = fy - 6f; it.baseY = it.y }
                            else if (type == E_SPIDER) addEnemy(type, xx, fy, max(lim0 - 30f, xx - 80f), min(lim1 + 30f, xx + 80f))
                            else {
                                val pr = patrol(xx, fy, lim0 - 30f, lim1 + 30f)
                                addEnemy(type, xx, fy, pr[0], pr[1])
                            }
                            e.elite = false
                            if (makerElite) makeElite(e)
                        }
                    }
                }
            }
        }
        return cx + w * 20f
    }

    private val minWorld = intArrayOf(0, 0, 0, 0, 1, 2, 1, 2, 1, 0, 0, 1, 1, 3, 4, 5, 0, 0, 1, 3, 2, 3, 0, 3, 2, 4)
    private val boost = arrayOf(
        intArrayOf(0, 5), intArrayOf(0, 4), intArrayOf(0, 4, 8), intArrayOf(0, 2, 7, 9), intArrayOf(1, 2, 6, 9, 10),
        intArrayOf(2, 7, 8), intArrayOf(2, 6, 10), intArrayOf(3, 4, 7, 9), intArrayOf(3, 4, 8), intArrayOf(1, 5, 8),
        intArrayOf(1, 5, 11), intArrayOf(4, 8, 11), intArrayOf(6, 9, 10), intArrayOf(3, 7, 11), intArrayOf(4, 10, 11),
        intArrayOf(6, 8, 11), intArrayOf(0, 3, 10), intArrayOf(5, 11), intArrayOf(5, 10), intArrayOf(3, 10),
        intArrayOf(2, 9), intArrayOf(4, 7), intArrayOf(0, 5), intArrayOf(10, 11), intArrayOf(3, 8), intArrayOf(10, 11)
    )
    private val safeFams = intArrayOf(AdvStructs.F_STAIRS, AdvStructs.F_WALL, AdvStructs.F_PILLARS, AdvStructs.F_ALTAR, AdvStructs.F_TOWER, AdvStructs.F_CHICANE)

    private fun pickStruct(tierMax: Int, lastFam: Int, safe: Boolean): AdvStructs.Bp {
        val list = AdvStructs.all
        var tot = 0
        val wts = IntArray(list.size)
        for (b in list) {
            if (b.tier > tierMax || world < minWorld[b.fam]) continue
            if (safe && (b.tier > 0 || b.fam !in safeFams)) continue
            if (recent.contains(b.id)) continue
            var w = 10
            if (b.tier == tierMax) w += 8 else if (b.tier == 0 && world >= 3) w = 3
            if (world in boost[b.fam]) w *= 3
            if (b.fam == lastFam) w = max(1, w / 5)
            wts[b.id] = w
            tot += w
        }
        if (tot == 0) return list[0]
        var r = rnd.nextInt(tot)
        for (b in list) { if (r < wts[b.id]) { recent.add(b.id); if (recent.size > 10) recent.removeAt(0); return b }; r -= wts[b.id] }
        return list[0]
    }

    /** Para pruebas: sala con una sola estructura. side 0 = entrar por la izquierda, 1 = por la derecha. */
    fun testStruct(id: Int, side: Int) {
        clearRoom()
        rnd.setSeed(1234L + id)
        cur = 0
        arenaX = 1e9f
        bossStarted = false
        ground(0f, 360f)
        val end = placeBp(AdvStructs.all[id], 360f)
        ground(end, end + 360f)
        roomLen = max(end + 360f, viewW + 80f)
        endX = roomLen
        rooms[0].len = roomLen
        px = if (side == 0) 52f else roomLen - 52f
        py = AdvCfg.GROUND
        pvx = 0f; pvy = 0f
        onGround = true
        face = if (side == 0) 1 else -1
        enemies.removeAll { abs(it.x - px) < 100f }
        camX = (px - viewW * 0.4f).coerceIn(0f, max(0f, endX - viewW)); ocam = camX
        opx = px; opy = py
        pendRoom = -1
        enterCd = 5f
        rooms[0].secret = false
    }


    /**
     * Vitrina para las capturas de prueba (versión de desarrollador): una sala plana con tres enemigos a la vista.
     * k = 0: arquero, slime, bombardero; 1: mago, lobo, calavera; 2: gólem élite, caballero y arquero élite.
     */
    // ------------------------------------------------------------------ Taller (editor estilo Mario Maker)
    /** Plano del Taller cargado (filas de arriba hacia abajo, la última es el suelo) o null en la Aventura normal. */
    var maker: Array<String>? = null
    var makerStart = 40f
    var makerGoalX = -1f
    var makerGoalY = AdvCfg.GROUND
    /** En el Taller todos los enemigos pueden salir élite (opción del editor). */
    var makerElite = false

    /** Arma una sala desde un plano del Taller. El jugador empieza en 'P' y gana al tocar la meta 'E'. */
    fun loadMaker(rows: Array<String>) {
        maker = rows
        noExit = true
        clearRoom()
        cur = 0
        arenaX = 1e9f
        bossStarted = false
        makerStart = 40f
        makerGoalX = -1f
        val bp = AdvStructs.Bp(-1, "taller", 0, 0, rows, false)
        val end = placeBp(bp, 0f)
        roomLen = max(end, viewW + 40f)
        if (roomLen > end) ground(end, roomLen)
        endX = roomLen
        rooms[0].len = roomLen
        rooms[0].secret = false
        px = makerStart; py = AdvCfg.GROUND
        // empezar sobre la superficie más alta bajo la 'P'
        py = groundBelow(px, 0f).let { if (it > AdvCfg.H) AdvCfg.GROUND else it }
        pvx = 0f; pvy = 0f; onGround = true; face = 1
        opx = px; opy = py
        safeX = px; safeY = py
        camX = (px - viewW * 0.38f).coerceIn(0f, max(0f, endX - viewW)); ocam = camX
        pendRoom = -1
        enterCd = 1f
        won = false
        bossStarted = false
    }

    /** En la vitrina no se sale de la sala (un golpe podía empujar al jugador por la puerta). */
    var noExit = false

    fun showcase(k: Int) {
        noExit = true
        clearRoom()
        cur = 0
        arenaX = 1e9f
        bossStarted = false
        roomLen = max(2400f, viewW + 80f)
        endX = roomLen
        rooms[0].len = roomLen
        ground(0f, roomLen)
        val g = AdvCfg.GROUND
        val sets = arrayOf(intArrayOf(E_ARCHER, E_SLIME, E_BOMBER), intArrayOf(E_MAGE, E_WOLF, E_SKULL), intArrayOf(E_GOLEM, E_KNIGHT, E_ARCHER))
        val set = sets[k.coerceIn(0, sets.size - 1)]
        for ((i, t) in set.withIndex()) {
            val x = 260f + i * 170f
            val e = addEnemy(t, x, g, x - 80f, x + 80f)
            e.elite = false
            if (k == 2 && i != 1) makeElite(e)
            e.face = -1
            e.cd = 0.4f + i * 0.5f
        }
        px = 80f; py = g; pvx = 0f; pvy = 0f; onGround = true; face = 1
        opx = px; opy = py
        camX = 0f; ocam = 0f
        pendRoom = -1
        enterCd = 5f
    }

    // ------------------------------------------------------------------ rellenos (trampas, torretas y plataformas en los tramos vacíos)
    private val fMin = intArrayOf(150, 240, 130, 230, 150, 270, 120, 240, 100, 260, 120, 240, 150, 130, 170, 230, 170, 240, 140, 270, 230, 180, 200, 200, 170, 160, 120, 230, 180, 190, 270, 270, 270, 270, 230, 120, 210, 230, 230, 270, 250, 270, 230,
        200, 220, 240, 200, 240, 240, 230, 260)
    private val fTier = intArrayOf(0, 1, 0, 1, 0, 2, 0, 1, 0, 1, 0, 1, 1, 0, 0, 2, 0, 2, 1, 1, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 2, 2, 3, 2, 2, 1, 1, 1, 1, 3, 3, 3, 2,
        1, 1, 1, 0, 2, 2, 2, 3)
    private val fWorld = intArrayOf(1, 3, 1, 3, 2, 5, 2, 4, 3, 5, 1, 4, 3, 4, 2, 5, 1, 6, 2, 5, 0, 0, 1, 0, 0, 3, 0, 4, 2, 0, 6, 7, 8, 5, 4, 3, 3, 2, 2, 7, 9, 8, 7,
        3, 2, 4, 0, 5, 7, 5, 8)
    /** Tramos que no hacen daño en el suelo (permitidos en salas seguras y bajo las puertas). */
    private val fAir = booleanArrayOf(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false,
        true, true, true, true, true, false, true, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false, false,
        false, false, false, false, false, false, false, false)
    private val fRecent = ArrayList<Int>()
    val fillCount: Int get() = fMin.size

    private fun fBoost(i: Int): Boolean = when (world) {
        7 -> i == 25 || i == 33 || i == 15 || i == 30 || i == 48
        9 -> i == 16 || i == 17 || i == 42 || i == 18
        8 -> i == 28 || i == 27 || i == 37 || i == 45 || i == 43
        10, 11 -> i == 40 || i == 41 || i == 32 || i == 39 || i == 47 || i == 50
        3 -> i == 2 || i == 3 || i == 39
        5 -> i == 12 || i == 11 || i == 36
        6 -> i == 4 || i == 5 || i == 37
        4 -> i == 6 || i == 7 || i == 13
        2 -> i == 8 || i == 18 || i == 1
        1 -> i == 10 || i == 14 || i == 0
        else -> i == 0 || i == 10 || i == 20
    }

    /** Rellena un tramo plano [x0,x1] con un elemento. Devuelve su número o -1. air = solo elementos sin daño de suelo. */
    private fun fillSpan(x0: Float, x1: Float, air: Boolean, blocksOk: Boolean = true): Int {
        val w = x1 - x0
        val tierMax = min(3, 1 + world / 3)
        val wts = IntArray(fMin.size)
        var tot = 0
        for (i in fMin.indices) {
            if (fMin[i] > w || fTier[i] > tierMax || world < fWorld[i] || fRecent.contains(i)) continue
            if (air && !fAir[i]) continue
            if (!blocksOk && i == 23) continue
            var q = 10
            if (fTier[i] == tierMax) q += 8
            if (fBoost(i)) q *= 3
            wts[i] = q
            tot += q
        }
        if (tot == 0) return -1
        var r = rnd.nextInt(tot)
        var id = 0
        for (i in wts.indices) { if (r < wts[i]) { id = i; break }; r -= wts[i] }
        fRecent.add(id)
        if (fRecent.size > 8) fRecent.removeAt(0)
        placeFill(id, x0, x1)
        return id
    }

    private fun trap(kind: Int, x: Float, y: Float, a: Float, b: Float, c: Float = 0f): AdvTrap {
        val t = AdvTrap(kind, x, y, a, b, c)
        t.ph = rnd.nextFloat() * 6f
        traps.add(t)
        return t
    }

    private fun hangCandle(x: Float, y: Float) { addCandle(x, y); candles[candles.size - 1].hang = true }
    private fun ledge(x0: Float, x1: Float, y: Float) { plats.add(AdvPlat(x0, x1, y)) }
    private fun popAt(x: Float): AdvEnemy { val e = addEnemy(E_POP, x, AdvCfg.GROUND, x - 20f, x + 20f); e.y = AdvCfg.GROUND + 26f; return e }
    private fun ceilAt(x: Float, a: Float, b: Float): AdvEnemy { val e = addEnemy(E_CEIL, x, AdvCfg.GROUND, a, b); e.y = AdvCfg.GROUND - 135f; e.baseY = e.y; return e }
    private fun blockCol(x0: Float, x1: Float, h: Float) {
        blocks.add(AdvBlock(x0, x1, AdvCfg.GROUND - h, AdvCfg.GROUND, false))
        plats.add(AdvPlat(x0, x1, AdvCfg.GROUND - h))
    }

    private fun placeFill(id: Int, x0: Float, x1: Float) {
        val g = AdvCfg.GROUND
        val w = x1 - x0
        val m = (x0 + x1) / 2f
        fun at(f: Float) = x0 + w * f
        val sp = 1.5f + world * 0.06f
        when (id) {
            0 -> trap(TR_SAW, x0 + 12f, g, x1 - 12f, sp)
            1 -> { trap(TR_SAW, x0 + 12f, g, m - 10f, sp + 0.2f); trap(TR_SAW, m + 10f, g, x1 - 12f, sp + 0.2f) }
            2 -> popAt(m)
            3 -> { popAt(at(0.25f)); popAt(at(0.75f)) }
            4 -> { ceilAt(m, x0 + 20f, x1 - 20f); ledge(m - 30f, m + 30f, g - 85f); addCandle(m, g - 85f) }
            5 -> { ceilAt(at(0.28f), x0 + 20f, m - 10f); ceilAt(at(0.72f), m + 10f, x1 - 20f) }
            6 -> trap(TR_CRUSH, m, 0f, 44f, 3.2f)
            7 -> { val a = trap(TR_CRUSH, m - 55f, 0f, 44f, 3.4f); a.ph = 0f; val b = trap(TR_CRUSH, m + 55f, 0f, 44f, 3.4f); b.ph = 1.7f }
            8 -> trap(TR_LASER, m, 0f, 0f, 2.8f)
            9 -> { val a = trap(TR_LASER, at(0.3f), 0f, 0f, 2.9f); a.ph = 0f; val b = trap(TR_LASER, at(0.7f), 0f, 0f, 2.9f); b.ph = 1.45f }
            10 -> trap(TR_BOB, m, g - 42f, 36f, 2.3f)
            11 -> for (k in 0..2) { val t = trap(TR_BOB, at(0.2f + 0.3f * k), g - 42f, 36f, 2.3f); t.ph = k * 1.1f }
            12 -> { val t = trap(TR_ORBIT, m, g - 72f, 46f, 2.1f, 2f); t.ph = 0f; ledge(m - 22f, m + 22f, g - 130f) }
            13 -> pends.add(AdvPend(m, 50f, 215f, 1.4f, rnd.nextFloat() * 6f))
            14 -> { trap(TR_CONV, x0 + 4f, g, x1 - 4f, if (rnd.nextBoolean()) 70f else -70f); scatter(x0, x1, g, 1, true) }
            15 -> { trap(TR_CONV, x0 + 4f, g, x1 - 4f, if (rnd.nextBoolean()) 65f else -65f); trap(TR_SAW, m - 25f, g, m + 25f, 2.2f) }
            16 -> { trap(TR_ICE, x0, g, x1, 0f); scatter(x0, x1, g, 1, true) }
            17 -> { trap(TR_ICE, x0, g, x1, 0f); spikes.add(floatArrayOf(m - 22f, m + 22f)) }
            18 -> trap(TR_ZAP, m - min(55f, w / 2f - 12f), g, m + min(55f, w / 2f - 12f), 2.6f)
            19 -> { trap(TR_ZAP, x0 + 14f, g, x1 - 14f, 2.8f); ledge(x0 + 8f, x1 - 8f, g - 80f); addCandle(m, g - 80f) }
            20 -> {
                val st = (w - 60f) / 2f
                for (k in 0..2) ledge(x0 + 8f + k * st, x0 + 8f + k * st + 60f, g - 70f - 60f * k)
                addCandle(x0 + 38f + st, g - 130f)
                items.add(AdvItem(x0 + 38f + 2f * st, g - 200f, IT_HEART, 0))
            }
            21 -> { ledge(x0 + 14f, x1 - 14f, g - 80f); addCandle(at(0.3f), g - 80f); addCandle(at(0.7f), g - 80f); scatter(x0, x1, g, 1, true) }
            22 -> { movers.add(AdvMover(x0 + 40f, x1 - 40f, g - 85f, g - 85f, 56f, 0.9f, rnd.nextFloat() * 6f)); hangCandle(at(0.3f), g - 120f); hangCandle(at(0.7f), g - 150f) }
            23 -> {
                val hs = floatArrayOf(20f, 40f, 40f, 20f)
                for (k in 0..3) blockCol(m - 80f + k * 40f, m - 40f + k * 40f, hs[k])
                addCandle(m - 20f, g - 40f)
            }
            24 -> {
                springs.add(AdvSpring(m - 30f, m - 10f, g))
                ledge(m - 50f, m + 90f, g - 150f)
                items.add(AdvItem(m + 30f, g - 160f, IT_BIG, 0)); addCandle(m + 70f, g - 150f)
            }
            25 -> { jets.add(AdvJet(m, g, 2.7f, rnd.nextFloat() * 2.7f)); ledge(m - 40f, m + 40f, g - 92f); addCandle(m, g - 92f) }
            26 -> { hangCandle(at(0.2f), g - 60f); hangCandle(at(0.5f), g - 95f); hangCandle(at(0.8f), g - 75f) }
            27 -> for (k in -1..1) rocks.add(AdvRock(m + k * 60f, 30f))
            28 -> { addEnemy(E_SPIDER, at(0.3f), g, x0, x1); addEnemy(E_SPIDER, at(0.7f), g, x0, x1) }
            29 -> { scatter(x0, x1, g, 2, true); addCandle(m, g); hangCandle(at(0.3f), g - 90f) }
            30 -> { popAt(at(0.18f)); trap(TR_SAW, at(0.4f), g, x1 - 12f, sp) }
            31 -> { pends.add(AdvPend(at(0.3f), 50f, 215f, 1.4f, rnd.nextFloat() * 6f)); popAt(at(0.78f)) }
            32 -> { trap(TR_CRUSH, at(0.25f), 0f, 44f, 3.3f); trap(TR_LASER, at(0.72f), 0f, 0f, 2.9f) }
            33 -> for (k in 0..2) jets.add(AdvJet(at(0.22f + 0.28f * k), g, 2.7f, k * 0.9f))
            34 -> { trap(TR_RAIL, m - 70f, g - 52f, m + 70f, g - 52f, 1.8f); scatter(m - 70f, m + 70f, g, 1, true) }
            35 -> trap(TR_RAIL, m, g - 125f, m, g - 12f, 1.7f)
            36 -> { addEnemy(E_KNIGHT, m, g, x0 + 10f, x1 - 10f); hangCandle(at(0.25f), g - 90f); hangCandle(at(0.75f), g - 90f) }
            37 -> for (k in 0..2) addEnemy(E_BAT, at(0.2f + 0.3f * k), g, x0 - 40f, x1 + 40f)
            38 -> {
                val n = ((w - 20f) / 52f).toInt().coerceAtMost(5)
                for (k in 0 until n) crumbles.add(AdvCrumble(x0 + 10f + k * 52f, x0 + 46f + k * 52f, g - 75f))
                addCandle(x0 + 28f + (n - 1) * 52f, g - 75f)
            }
            39 -> { ceilAt(at(0.3f), x0 + 20f, m); popAt(at(0.75f)) }
            40 -> { trap(TR_ORBIT, at(0.25f), g - 72f, 44f, 2.3f, 2f); trap(TR_SAW, at(0.52f), g, x1 - 12f, sp + 0.2f) }
            41 -> { trap(TR_ZAP, x0 + 14f, g, x0 + 105f, 2.7f); trap(TR_LASER, at(0.75f), 0f, 0f, 2.9f) }
            42 -> { trap(TR_ICE, x0, g, x1, 0f); trap(TR_SAW, x0 + 30f, g, x1 - 30f, 1.3f) }
            // --- rellenos con los enemigos nuevos
            43 -> { ledge(m - 45f, m + 45f, g - 85f); addEnemy(E_ARCHER, m, g - 85f, m - 38f, m + 38f); addCandle(at(0.2f), g) }
            44 -> { trap(TR_CONV, x0 + 4f, g, x1 - 4f, if (rnd.nextBoolean()) 60f else -60f); addEnemy(E_BOMBER, m, g, x0 - 40f, x1 + 40f) }
            45 -> { addEnemy(E_WOLF, at(0.3f), g, x0 + 10f, x1 - 10f); addEnemy(E_WOLF, at(0.75f), g, x0 + 10f, x1 - 10f).cd = 1.2f }
            46 -> for (k in 0..2) addEnemy(E_SLIME, at(0.2f + 0.3f * k), g, x0 + 10f, x1 - 10f)
            47 -> { addEnemy(E_MAGE, at(0.7f), g, x0 + 10f, x1 - 10f); trap(TR_LASER, at(0.3f), 0f, 0f, 3.0f) }
            48 -> { addEnemy(E_GOLEM, m, g, x0 + 20f, x1 - 20f); hangCandle(at(0.2f), g - 95f); hangCandle(at(0.8f), g - 95f) }
            49 -> { addEnemy(E_SKULL, at(0.3f), g, x0, x1); addEnemy(E_SKULL, at(0.7f), g, x0, x1).cd = 1.5f; spikes.add(floatArrayOf(m - 16f, m + 16f)) }
            50 -> { ledge(x1 - 90f, x1 - 20f, g - 80f); addEnemy(E_ARCHER, x1 - 55f, g - 80f, x1 - 85f, x1 - 25f); addEnemy(E_KNIGHT, at(0.35f), g, x0 + 10f, x1 - 100f) }
        }
    }

    private fun clearRoom() {
        plats.clear(); spikes.clear(); candles.clear(); enemies.clear(); items.clear(); projs.clear(); hazs.clear()
        movers.clear(); crumbles.clear(); jets.clear(); pends.clear(); rocks.clear(); traps.clear(); ambushes.clear(); doors.clear()
        blocks.clear(); springs.clear(); secretSpots.clear(); spawnQ.clear()
        statueX = -1f
        riding = null
    }

    private fun hub(cx: Float, r: AdvRoom): Float {
        val len = 340f
        ground(cx, cx + len)
        if (r.nb[D_UP] >= 0) doors.add(AdvDoor(cx + 55f, true, r.nb[D_UP]))
        if (r.nb[D_DN] >= 0) doors.add(AdvDoor(cx + len - 55f, false, r.nb[D_DN]))
        if (r.kind == R_SAVE || r.kind == R_START) statueX = cx + len / 2f
        else if (r.prize >= 0 && !r.taken) {
            val it = AdvItem(cx + len / 2f, AdvCfg.GROUND - 40f, r.prize, r.prizeArg)
            it.prize = true
            items.add(it)
        }
        return cx + len
    }

    private fun buildRoom(r: AdvRoom) {
        clearRoom()
        rnd.setSeed(r.seed)
        val diff = world / 11f
        val boss0 = r.kind == R_BOSS
        val ent = if (boss0) 360f else 440f
        ground(0f, ent)
        var cx = ent
        arenaX = 1e9f
        fRecent.clear()
        if (r.kind == R_BOSS) {
            var last = -1
            while (cx < 560f) { val k = pickSeg(cx, 560f, 2, last); cx = addSeg(k, cx, diff); last = k }
            ground(cx, cx + 140f)
            cx += 140f
            arenaX = cx
            ground(arenaX, arenaX + viewW + 80f)
            endX = arenaX + viewW
            bx = arenaX + viewW * 0.78f
            roomLen = endX
            r.len = roomLen
            return
        }
        val hubNeeded = r.nb[D_UP] >= 0 || r.nb[D_DN] >= 0 || r.kind != R_NORMAL
        val safe = r.kind == R_START || r.kind == R_SAVE
        val target = when (r.kind) {
            R_START -> 700f
            R_SAVE -> 640f
            else -> 1100f + rnd.nextInt(700) + world * 40f
        }
        val tierMax = min(3, world / 2 + 1)
        recent.clear()
        var mid = false
        var last = -1
        var lastFam = -1
        fillSpan(210f, ent - 28f, safe)
        while (cx < target) {
            if (!mid && cx >= target * 0.5f) {
                mid = true
                if (r.guarded && !r.cleared && !safe) cx = addSeg(S_AMBUSH, cx, diff)
                if (hubNeeded) {
                    val hx = cx
                    cx = hub(cx, r)
                    fillSpan(hx + 30f, cx - 30f, true, false)
                }
                continue
            }
            if (!safe && rnd.nextInt(100) < 18) {
                val k = pickSeg(cx, target, 2, last)
                cx = addSeg(k, cx, diff)
                last = k
                lastFam = -1
            } else {
                val bp = pickStruct(tierMax, lastFam, safe)
                cx = placeBp(bp, cx)
                lastFam = bp.fam
                last = -1
            }
            val gap = if (safe) 110f + rnd.nextInt(130) else 190f + rnd.nextInt(150)
            ground(cx, cx + gap)
            var fid = -1
            if (rnd.nextInt(100) < (if (safe) 55 else 90)) fid = fillSpan(cx + 34f, cx + gap - 34f, safe)
            val airF = fid >= 0 && fAir[fid]
            if (fid < 0 && rnd.nextInt(4) == 0) addCandle(cx + gap / 2f, AdvCfg.GROUND)
            if (!safe && (fid < 0 || airF)) {
                val n = (if (rnd.nextInt(100) < 70) 1 else 0) + (if (world >= 3 && rnd.nextInt(100) < 45) 1 else 0) + (if (world >= 7 && rnd.nextInt(100) < 35) 1 else 0) +
                    (if (world >= 10 && gap > 260f && rnd.nextInt(100) < 40) 1 else 0)
                if (n > 0) scatter(cx + 20f, cx + gap - 20f, AdvCfg.GROUND, n, true)
            }
            cx += gap
        }
        if (!mid) {
            if (r.guarded && !r.cleared && !safe) cx = addSeg(S_AMBUSH, cx, diff)
            if (hubNeeded) {
                val hx = cx
                cx = hub(cx, r)
                fillSpan(hx + 30f, cx - 30f, true, false)
            }
        }
        ground(cx, cx + 440f)
        fillSpan(cx + 28f, cx + 250f, safe)
        cx += 440f
        roomLen = max(cx, viewW + 80f)
        if (roomLen > cx) ground(cx, roomLen)
        endX = roomLen
        r.len = roomLen
        if (r.kind == R_START || r.kind == R_SAVE) enemies.clear()
    }

    /** Progreso del mundo como texto (para guardarlo): mapa explorado, llaves, mejoras y última estatua. */
    fun saveState(): String {
        val sb = StringBuilder()
        sb.append("1,").append(rooms.size).append(',').append(saveRoom).append(',').append(keys).append(',').append(maxHp)
            .append(',').append(sub).append(',').append(whip).append(',').append(if (boots) 1 else 0).append(',').append(hearts).append(',')
        for ((i, r) in rooms.withIndex()) {
            var v = 0
            if (r.seen) v = v or 1
            if (r.visited) v = v or 2
            if (r.taken) v = v or 4
            if (r.cleared) v = v or 8
            if (r.lock[D_W]) v = v or 16
            if (r.lock[D_E]) v = v or 32
            if (r.secret) v = v or 64
            if (i > 0) sb.append('.')
            sb.append(v)
        }
        return sb.toString()
    }

    fun loadState(str: String): Boolean {
        try {
            val p = str.split(",")
            if (p.size < 10 || p[0] != "1" || p[1].toInt() != rooms.size) return false
            val flags = p[9].split(".")
            if (flags.size != rooms.size) return false
            for ((i, r) in rooms.withIndex()) {
                val v = flags[i].toInt()
                r.seen = v and 1 != 0; r.visited = v and 2 != 0; r.taken = v and 4 != 0; r.cleared = v and 8 != 0
                r.lock[D_W] = v and 16 != 0; r.lock[D_E] = v and 32 != 0; r.secret = v and 64 != 0
            }
            saveRoom = p[2].toInt().coerceIn(0, rooms.size - 1)
            keys = p[3].toInt().coerceIn(0, 9)
            maxHp = p[4].toInt().coerceIn(AdvCfg.MAXHP, 40)
            sub = p[5].toInt().coerceIn(0, AdvCfg.SUBS)
            whip = p[6].toInt().coerceIn(0, 2)
            boots = p[7].toInt() != 0
            hearts = p[8].toInt().coerceIn(0, AdvCfg.MAXHEARTS)
            hp = maxHp
            enterRoom(saveRoom, 3, 0f)
            return true
        } catch (e: Exception) { return false }
    }

    /** Para pruebas: teletransporta a una sala (0 = entrada oeste, 1 = entrada este). */
    fun warp(id: Int, mode: Int) { enterRoom(id, mode, 0f) }

    /** Entra a una sala. mode: 0 = por el oeste, 1 = por el este, 2 = en la x dada (escalera), 3 = junto a la estatua. */
    private fun enterRoom(id: Int, mode: Int, x: Float) {
        val from = cur
        cur = id
        val r = rooms[id]
        r.visited = true
        r.seen = true
        for (d in 0..3) if (r.nb[d] >= 0) rooms[r.nb[d]].seen = true
        buildRoom(r)
        px = when (mode) {
            0 -> 52f
            1 -> roomLen - 52f
            3 -> if (statueX >= 0f) statueX else 52f
            4, 5 -> doors.firstOrNull { it.to == from }?.x ?: 52f
            else -> x
        }
        py = AdvCfg.GROUND
        if (mode == 0) face = 1 else if (mode == 1) face = -1
        pvx = 0f; pvy = 0f
        onGround = true
        riding = null
        bibleT = 0f
        atkT = 0f
        hitstun = 0f
        bossStarted = false
        if (r.kind == R_BOSS && bstate != BS_DEAD) resetBoss()
        enemies.removeAll { abs(it.x - px) < 170f }
        val maxCam = max(0f, endX - viewW)
        camX = (px - viewW * (0.5f - 0.12f * face)).coerceIn(0f, maxCam)
        ocam = camX
        opx = px; opy = py
        safeX = px; safeY = py
        fade = 1f
        enterCd = 0.3f
        touchedSave = mode == 3
        pendRoom = -1
        ev(EV_ROOM, px, py, id)
        rnd.setSeed(r.seed xor (time * 1000f).toLong() xor 0x5DEECE66DL)
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
        shake = max(shake, 3f)
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
        val mk = maker
        if (mk != null) { // sala del Taller: se vuelve a armar desde el principio
            hp = maxHp
            deadT = 0f
            potionT = 0f
            loadMaker(mk)
            invuln = 1.5f
            fade = 1f
            return
        }
        hp = maxHp
        hearts = max(hearts, 5)
        invuln = 1.5f
        deadT = 0f
        potionT = 0f
        enterRoom(saveRoom, 3, 0f)
        invuln = 1.5f
        fade = 1f
    }

    /** Caída a un foso: daño y vuelta al último suelo firme de la sala. */
    private fun fall() {
        if (!god) hp -= 2
        ev(EV_FALL, px, AdvCfg.H - 10f)
        if (hp <= 0) { hp = 0; die(); return }
        px = safeX; py = safeY
        pvx = 0f; pvy = 0f
        onGround = true
        riding = null
        invuln = 1.3f
        hitstun = 0f
        shake = 3f
        opx = px; opy = py
    }

    private fun resetBoss() {
        bossStarted = false
        bhp = bmax
        bstate = BS_INTRO
        bst = 0f
        bx = arenaX + viewW * 0.78f
        bvx = 0f; bvy = 0f; bz = 0f
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
        alpha = (acc / AdvCfg.STEP).coerceIn(0f, 1f)
    }

    private fun lockedMin(): Float {
        if (noExit) return AdvCfg.PHALF
        val a = lastAmbushActive()
        if (a != null) return a.x0 + AdvCfg.PHALF + 2f
        if (bossStarted) return arenaX + AdvCfg.PHALF + 2f
        val r = rooms[cur]
        return if (r.nb[D_W] >= 0 && !r.lock[D_W]) -30f else if (r.lock[D_W]) 20f else AdvCfg.PHALF
    }

    private fun lockedMax(): Float {
        if (noExit) return roomLen - AdvCfg.PHALF
        val a = lastAmbushActive()
        if (a != null) return a.x1 - AdvCfg.PHALF - 2f
        if (bossStarted) return arenaX + viewW - AdvCfg.PHALF - 2f
        val r = rooms[cur]
        return if (r.nb[D_E] >= 0 && !r.lock[D_E]) roomLen + 30f else if (r.lock[D_E]) roomLen - 20f else roomLen - AdvCfg.PHALF
    }

    private fun snapshot() {
        opx = px; opy = py; ocam = camX; obx = bx
        for (e in enemies) { e.ox = e.x; e.oy = e.y }
        for (p in projs) { p.ox = p.x; p.oy = p.y }
        for (m in movers) { m.ox = m.x; m.oy = m.y }
        for (i in items) { i.ox = i.x; i.oy = i.y }
    }

    private fun tick(dt: Float, inp: AdvInput) {
        time += dt
        if (pendRoom >= 0) enterRoom(pendRoom, pendMode, pendX)
        snapshot()
        if (fade > 0f) fade = max(0f, fade - dt * 3.2f)
        enterCd = max(0f, enterCd - dt)
        lockMsgT = max(0f, lockMsgT - dt)
        lastSaveFlash = max(0f, lastSaveFlash - dt)
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
        val target = if (bossStarted) arenaX else if (a != null) a.x0 else (px - viewW * (0.5f - 0.12f * face))
        val maxCam = max(0f, endX - viewW)
        val t = target.coerceIn(0f, maxCam)
        camX += (t - camX) * min(1f, dt * 6f)
        camX = camX.coerceIn(0f, maxCam)
    }

    // ------------------------------------------------------------------ plataformas
    private fun updateMovers(dt: Float) {
        for (sp in springs) if (sp.t > 0f) sp.t = max(0f, sp.t - dt)
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
        updateFillTraps(dt)
    }

    private fun onIceZone(): Boolean {
        for (t in traps) if (t.kind == TR_ICE && px > t.x && px < t.a) return true
        return false
    }

    /** Posición del orbe k de una trampa orbital. */
    fun orbitPos(t: AdvTrap, k: Int, out: FloatArray) {
        val n = max(1, t.c.toInt())
        val an = time * t.b + t.ph + k * 6.2831855f / n
        out[0] = t.x + cos(an) * t.a
        out[1] = t.y + sin(an) * t.a
    }

    private val orbTmp = FloatArray(2)
    fun rndF(): Float = Math.random().toFloat()

    private fun hitCircle(cx: Float, cy: Float, r: Float, dmg: Int) {
        if (invuln > 0f || deadT > 0f) return
        val dx = max(abs(cx - px) - 8f, 0f)
        val dy = max(abs(cy - (py - 15f)) - 14f, 0f)
        if (dx * dx + dy * dy < r * r) playerHurt(dmg, cx)
    }

    /** Ciclo de una trampa con aviso: devuelve 0 apagada, 1 aviso, 2 activa. */
    private fun cyc(t: AdvTrap): Int {
        val per = t.b
        val c = (time + t.ph) % per
        return if (c >= per - 0.65f) 2 else if (c >= per - 1.3f) 1 else 0
    }

    private fun updateFillTraps(dt: Float) {
        val gy = AdvCfg.GROUND
        for (t in traps) {
            if (abs(t.x - px) > 900f && t.kind != TR_SAW && t.kind != TR_RAIL) continue
            when (t.kind) {
                TR_SAW -> {
                    val u = 0.5f + 0.5f * sin(time * t.b + t.ph)
                    t.cx = t.x + (t.a - t.x) * u; t.cy = gy - 11f
                    hitCircle(t.cx, t.cy, 11f, 2)
                }
                TR_RAIL -> {
                    val u = 0.5f + 0.5f * sin(time * t.c + t.ph)
                    t.cx = t.x + (t.a - t.x) * u; t.cy = t.y + (t.b - t.y) * u
                    hitCircle(t.cx, t.cy, 11f, 2)
                }
                TR_BOB -> {
                    t.cx = t.x; t.cy = t.y + sin(time * t.b + t.ph) * t.a
                    hitCircle(t.cx, t.cy, 9f, 2)
                }
                TR_ORBIT -> {
                    for (k in 0 until max(1, t.c.toInt())) { orbitPos(t, k, orbTmp); hitCircle(orbTmp[0], orbTmp[1], 8f, 2) }
                }
                TR_CRUSH -> {
                    val per = t.b
                    val c = (time + t.ph) % per
                    val prev = t.cy
                    t.warn = c >= per - 1.5f && c < per - 0.9f
                    t.cy = when {
                        c < per - 0.9f -> CRUSH_UP
                        c < per - 0.75f -> CRUSH_UP + (gy - CRUSH_UP) * ((c - (per - 0.9f)) / 0.15f)
                        c < per - 0.3f -> gy
                        else -> gy - (gy - CRUSH_UP) * ((c - (per - 0.3f)) / 0.3f)
                    }
                    t.on = t.cy > gy - 40f
                    if (prev < gy - 1f && t.cy >= gy - 1f && abs(t.x - px) < 360f) { shake = max(shake, 5f); ev(EV_ROCK, t.x, gy) }
                    if (t.cy > py - 29f && invuln <= 0f && deadT <= 0f && abs(px - t.x) < t.a / 2f + 8f) playerHurt(3, t.x)
                }
                TR_LASER -> {
                    val s = cyc(t)
                    t.warn = s == 1; t.on = s == 2
                    if (t.on && invuln <= 0f && deadT <= 0f && abs(px - t.x) < 11f) playerHurt(2, t.x)
                }
                TR_ZAP -> {
                    val s = cyc(t)
                    t.warn = s == 1; t.on = s == 2
                    if (t.on && invuln <= 0f && deadT <= 0f && onGround && py >= gy - 2f && px > t.x && px < t.a) playerHurt(2, px)
                }
                TR_CONV -> if (onGround && py >= gy - 2f && px > t.x && px < t.a && deadT <= 0f && hitstun <= 0f) {
                    px = (px + t.b * dt).coerceIn(lockedMin(), lockedMax())
                }
            }
        }
    }

    private fun updateAmbush(dt: Float) {
        for (a in ambushes) {
            if (a.st == 0 && px >= a.x0 + 70f && px < a.x1 - 10f && deadT <= 0f) {
                a.st = 1
                a.wave = 0
                ev(EV_AMBUSH, a.x0 + viewW / 2f, AdvCfg.GROUND)
                spawnWave(a, 2 + (world / 6))
            } else if (a.st == 1) {
                a.t += dt
                if (a.t > 50f) for (e in a.mine) if (!e.dead) { e.dead = true; ev(EV_EDIE, e.x, e.y - 10f) } // seguro contra bloqueos
                a.mine.removeAll { it.dead }
                if (a.wave == 1 && a.mine.size <= 1) {
                    a.wave = 2
                    spawnWave(a, 3 + (world / 4))
                    // en los mundos difíciles, un respiro entre oleadas: carne para curarse
                    if (world >= 7) items.add(AdvItem((a.x0 + a.x1) / 2f, AdvCfg.GROUND - 70f, IT_MEAT, 0))
                }
                else if (a.wave == 2 && a.mine.isEmpty() && world >= 8) {
                    // mundos finales: tercera oleada con un élite al frente
                    a.wave = 3
                    items.add(AdvItem((a.x0 + a.x1) / 2f, AdvCfg.GROUND - 70f, IT_MEAT, 0))
                    spawnWave(a, 2)
                    a.mine.firstOrNull()?.let { makeElite(it) }
                    ev(EV_AMBUSH, a.x0 + viewW / 2f, AdvCfg.GROUND)
                }
                else if (a.wave >= 2 && a.mine.isEmpty()) {
                    a.st = 2
                    rooms[cur].cleared = true
                    ev(EV_CLEAR, px, py - 20f)
                    items.add(AdvItem((a.x0 + a.x1) / 2f, AdvCfg.GROUND - 60f, IT_BIG, 0))
                    items.add(AdvItem((a.x0 + a.x1) / 2f + 24f, AdvCfg.GROUND - 60f, if (rnd.nextBoolean()) IT_SUB else IT_MEAT, 1 + rnd.nextInt(AdvCfg.SUBS)))
                }
            }
        }
    }

    private fun spawnWave(a: AdvAmbush, n: Int) {
        if (a.wave < 2) a.wave++
        val al = allowed().filter { it != E_SPIDER }.toIntArray()
        val melee = al.filter { !ranged(it) }.toIntArray()
        var shooters = 0
        for (i in 0 until n) {
            var t = al[rnd.nextInt(al.size)]
            // como mucho 2 tiradores por oleada: que se pueda pelear sin quedar acorralado por disparos
            if (ranged(t)) { if (shooters >= 2 && melee.isNotEmpty()) t = melee[rnd.nextInt(melee.size)] else shooters++ }
            var x = a.x0 + 60f + rnd.nextFloat() * (viewW - 120f)
            if (abs(x - px) < 140f) x = if (px < a.x0 + viewW / 2f) a.x1 - 40f - rnd.nextFloat() * 80f else a.x0 + 40f + rnd.nextFloat() * 80f
            val e = addEnemy(t, x, AdvCfg.GROUND, a.x0 + 10f, a.x1 - 10f)
            e.aggro = true
            e.stun = 0.25f + i * 0.3f // aparecen escalonados, no todos a la vez
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
            val k = if (onGround) (if (ice || onIceZone()) 9f else 22f) else 12f
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
        val oldPx = px
        px = (px + pvx * dt).coerceIn(lockedMin(), lockedMax())
        if (blocks.isNotEmpty()) collideX(oldPx)
        val prev = py
        py += pvy * dt
        if (pvy < 0f && blocks.isNotEmpty()) {
            for (b in blocks) if (px + 8f > b.x0 && px - 8f < b.x1 && prev - 30f >= b.y1 - 1f && py - 30f < b.y1) { py = b.y1 + 30f; pvy = 0f; jumping = false }
        }
        var landed = false
        if (pvy >= 0f) {
            val y = landing(prev, py)
            if (!y.isNaN()) { py = y; landed = true }
        }
        if (landed) {
            val wasAir = !onGround
            val fall = pvy
            if (wasAir) ev(EV_LAND, px, py)
            pvy = 0f
            onGround = true
            if (wasAir && fall > 100f) {
                for (sp in springs) if (px + 6f > sp.x0 && px - 6f < sp.x1 && abs(py - sp.y) < 2f) {
                    pvy = -830f; onGround = false; jumping = false; coyote = 0f; sp.t = 0.35f
                    ev(EV_SPRING, (sp.x0 + sp.x1) / 2f, sp.y)
                    break
                }
            }
        } else { onGround = false; riding = null }
        if (py > AdvCfg.H + 40f) { fall(); return }
        if (onGround && riding == null) {
            for (p in plats) if (abs(p.y - py) < 1f && px > p.x0 + 16f && px < p.x1 - 16f) {
                var ok = true
                for (s in spikes) if (px + 36f > s[0] && px - 36f < s[1]) ok = false
                if (ok) { safeX = px; safeY = py }
                break
            }
        }
        if (onGround && invuln <= 0f) {
            for (s in spikes) if (px + 6f > s[0] && px - 6f < s[1] && py >= AdvCfg.GROUND - 2f) playerHurt(2, (s[0] + s[1]) / 2f)
        }
        roomLogic(inp)
        if (maker != null && makerGoalX >= 0f && !won && abs(px - makerGoalX) < 18f && abs(py - makerGoalY) < 40f) {
            won = true
            ev(EV_WIN, px, py - 20f)
        }
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

    /** Estatuas, puertas, salidas y puertas con llave. */
    private fun roomLogic(inp: AdvInput) {
        val r = rooms[cur]
        if (deadT > 0f || won) return
        // estatua de guardado
        if (statueX >= 0f) {
            if (abs(px - statueX) < 20f && onGround && !touchedSave && !bossStarted) {
                touchedSave = true
                saveRoom = cur
                hp = maxHp
                hearts = max(hearts, 10)
                lastSaveFlash = 1.2f
                ev(EV_CHECK, statueX, AdvCfg.GROUND)
            } else if (abs(px - statueX) > 70f) touchedSave = false
        }
        // puertas con llave en los bordes
        if (!bossStarted && lastAmbushActive() == null) {
            if (r.lock[D_E] && px >= roomLen - 24f) unlockEdge(r, D_E)
            if (r.lock[D_W] && px <= 24f) unlockEdge(r, D_W)
        }
        // salidas laterales
        if (enterCd <= 0f && pendRoom < 0 && !bossStarted && lastAmbushActive() == null) {
            if (px <= 3f && r.nb[D_W] >= 0 && !r.lock[D_W]) { pendRoom = r.nb[D_W]; pendMode = 1; pendX = 0f }
            else if (px >= roomLen - 3f && r.nb[D_E] >= 0 && !r.lock[D_E]) { pendRoom = r.nb[D_E]; pendMode = 0; pendX = 0f }
        }
        // escaleras: ATAQUE junto a la puerta
        if (inp.atkPressed && onGround && enterCd <= 0f && pendRoom < 0 && !bossStarted && lastAmbushActive() == null) {
            for (d in doors) if (abs(px - d.x) < 24f) {
                pendRoom = d.to
                pendMode = if (d.up) 4 else 5
                pendX = 0f
                inp.atkPressed = false
                ev(EV_DOOR, d.x, AdvCfg.GROUND - 20f, if (d.up) 1 else 0)
                break
            }
        }
    }

    private fun unlockEdge(r: AdvRoom, d: Int) {
        if (keys > 0) {
            keys--
            r.lock[d] = false
            rooms[r.nb[d]].lock[d xor 1] = false
            ev(EV_UNLOCK, if (d == D_E) roomLen - 12f else 12f, AdvCfg.GROUND - 20f)
        } else if (lockMsgT <= 0f) {
            lockMsgT = 1.6f
            ev(EV_LOCKED, px, py - 30f)
        }
    }

    private fun collideX(oldPx: Float) {
        for (b in blocks) {
            if (py > b.y0 + 0.5f && py - 30f < b.y1 - 0.5f && px + 10f > b.x0 && px - 10f < b.x1) {
                px = if (oldPx <= (b.x0 + b.x1) / 2f) b.x0 - 10f else b.x1 + 10f
                pvx = 0f
            }
        }
    }

    private fun breakBlock(b: AdvBlock) {
        blocks.remove(b)
        b.plat?.let { plats.remove(it) }
        ev(EV_BREAK, (b.x0 + b.x1) / 2f, (b.y0 + b.y1) / 2f)
        shake = max(shake, 2.5f)
        if (!rooms[cur].secret) {
            rooms[cur].secret = true
            for (sp in secretSpots) {
                val kind: Int
                var arg = 0
                val r = rnd.nextInt(100)
                kind = when { r < 40 -> IT_BIG; r < 60 -> IT_MEAT; r < 92 -> { arg = 1 + rnd.nextInt(AdvCfg.SUBS); IT_SUB }; else -> IT_POTION }
                items.add(AdvItem(sp[0], sp[1], kind, arg))
            }
            secretSpots.clear()
        }
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
        if (blocks.isNotEmpty()) for (b in ArrayList(blocks)) {
            if (b.brk && overlap(w[0], w[1], w[2], w[3], b.x0, b.y0, b.x1, b.y1)) breakBlock(b)
        }
        // el látigo desvía flechas, bombas y orbes mágicos (no las balas de las torretas ni las del jefe)
        val pit = projs.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            if (!p.mine && p.kind >= 7 && overlap(w[0] - 4f, w[1] - 6f, w[2] + 4f, w[3] + 6f, p.x - p.r, p.y - p.r, p.x + p.r, p.y + p.r)) {
                ev(EV_BLOCK, p.x, p.y)
                pit.remove()
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
    fun ew(e: AdvEnemy): Float {
        val w = when (e.type) {
            E_POP -> 22f; E_CEIL -> 24f; E_BAT -> 18f; E_TURRET -> 22f; E_CHARGER -> 28f; E_MEDUSA -> 18f; E_KNIGHT -> 26f
            E_GHOST -> 22f; E_SPIDER -> 18f; E_ARCHER -> 20f; E_SLIME -> if (e.small) 14f else 24f; E_BOMBER -> 26f
            E_MAGE -> 20f; E_WOLF -> 30f; E_SKULL -> 20f; E_GOLEM -> 34f; else -> 22f
        }
        return if (e.elite) w * 1.15f else w
    }

    fun eh(e: AdvEnemy): Float {
        val h = when (e.type) {
            E_POP -> 26f; E_CEIL -> 26f; E_BAT -> 14f; E_TURRET -> 24f; E_CHARGER -> 26f; E_MEDUSA -> 16f; E_KNIGHT -> 38f
            E_GHOST -> 26f; E_SPIDER -> 14f; E_ARCHER -> 30f; E_SLIME -> if (e.small) 11f else 20f; E_BOMBER -> 16f
            E_MAGE -> 34f; E_WOLF -> 20f; E_SKULL -> 18f; E_GOLEM -> 46f; else -> 28f
        }
        return if (e.elite) h * 1.15f else h
    }

    /** Daño por contacto. */
    fun touchDmg(e: AdvEnemy): Int {
        var d = when (e.type) {
            E_BAT, E_MEDUSA -> 1
            E_SLIME -> if (e.small) 1 else 2
            E_GOLEM -> 3
            E_WOLF -> if (e.st == 2) 3 else 2
            else -> 2
        }
        if (e.elite) d++
        return d
    }

    /** ¿Se le puede caer encima para pisarlo? */
    fun stompable(e: AdvEnemy): Boolean = when (e.type) {
        E_POP -> e.st == 2
        E_CEIL, E_TURRET -> false
        E_SPIDER -> e.st == 2
        E_MAGE -> e.vis > 0.5f
        else -> true
    }

    private val spawnQ = ArrayList<AdvEnemy>()
    private var stompCd = 0f

    private fun hurtEnemy(e: AdvEnemy, dmg: Int, srcX: Float, armor: Boolean, kb: Boolean = true) {
        if (e.dead) return
        if (e.type == E_POP && e.st == 0) return
        if (e.type == E_MAGE && e.vis < 0.5f) return
        if (e.type == E_KNIGHT && !armor && (srcX - e.x) * e.face > 0f && e.st != 2) {
            e.flash = 0.05f
            e.kvx = -e.face * 90f
            ev(EV_BLOCK, e.x + e.face * 12f, e.y - 22f)
            return
        }
        e.hp -= dmg
        e.flash = 0.15f
        e.aggro = true
        ev(EV_HIT, e.x, e.y - eh(e) / 2f, dmg)
        if (e.hp > 0 && kb && e.type != E_GOLEM && (flyer(e.type) || (groundBody(e.type) && !(e.type == E_SPIDER && e.st < 2)))) {
            val s = if (e.x >= srcX) 1f else -1f
            e.kvx = s * (if (e.elite) 110f else 185f)
            e.stun = if (e.elite) 0.08f else 0.2f
        }
        if (e.hp <= 0) {
            e.dead = true
            kills++
            hitStop = max(hitStop, 0.03f)
            ev(EV_EDIE, e.x, e.y - eh(e) / 2f)
            if (e.type == E_SLIME && !e.small) splitSlime(e)
            val r = rnd.nextInt(100)
            if (e.elite) items.add(AdvItem(e.x, e.y - 12f, if (r < 60) IT_BIG else IT_MEAT, 0))
            else if (r < 30) items.add(AdvItem(e.x, e.y - 12f, IT_HEART, 0))
            else if (r < 36) items.add(AdvItem(e.x, e.y - 12f, IT_BIG, 0))
            else if (r < 39 && (e.type == E_KNIGHT || e.type == E_GOLEM)) items.add(AdvItem(e.x, e.y - 12f, IT_MEAT, 0))
        }
    }

    private fun splitSlime(e: AdvEnemy) {
        val amb = lastAmbushActive()
        for (k in intArrayOf(-1, 1)) {
            val m = AdvEnemy(E_SLIME, e.x + k * 8f, e.floor, e.x0, e.x1, 1)
            m.small = true
            m.floor = e.floor; m.baseY = e.floor
            m.y = e.floor - 4f
            m.vy = -260f; m.vx = k * 90f
            m.face = k
            m.aggro = true
            m.cd = 0.3f
            m.age = 0.35f
            spawnQ.add(m)
            if (amb != null && amb.mine.contains(e)) amb.mine.add(m)
        }
        ev(EV_SPLIT, e.x, e.y - 10f)
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
                    IT_MEAT -> hp = min(maxHp, hp + 4)
                    IT_SUB -> sub = i.arg.coerceIn(1, AdvCfg.SUBS)
                    IT_WHIP -> whip = min(2, whip + 1)
                    IT_BOOTS -> boots = true
                    IT_POTION -> potionT = 9f
                    IT_KEY -> keys++
                    IT_HPUP -> { maxHp += 2; hp = maxHp }
                    IT_ORB -> { hp = maxHp; won = true; ev(EV_WIN, px, py - 20f) }
                }
                if (i.prize) rooms[cur].taken = true
                ev(EV_PICK, i.x, i.y, i.kind)
                it.remove()
            }
        }
    }

    // ------------------------------------------------------------------ enemigos
    private fun updateEnemies(dt: Float) {
        val frozen = freezeT > 0f
        if (spawnQ.isNotEmpty()) { enemies.addAll(spawnQ); spawnQ.clear() }
        stompCd = max(0f, stompCd - dt)
        val it = enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.dead) { it.remove(); continue }
            e.flash = max(0f, e.flash - dt)
            e.age += dt
            if (abs(e.x - px) > 700f) continue
            // física: retroceso por golpe (se frena con fricción) sin salirse de su tramo
            if (e.kvx != 0f && !frozen) {
                e.x += e.kvx * dt
                if (groundBody(e.type) || e.type == E_GOLEM) e.x = e.x.coerceIn(e.x0, e.x1)
                e.kvx *= max(0f, 1f - dt * 9f)
                if (abs(e.kvx) < 4f) e.kvx = 0f
            }
            if (e.stun > 0f) e.stun -= dt
            else if (!frozen) enemyAi(e, dt)
            if (e.dead) continue
            if (e.type == E_POP && e.st == 0) continue
            if (e.type == E_MAGE && e.vis < 0.5f) continue
            if (deadT > 0f) continue
            val top = e.y - eh(e)
            // pisotón: caer sobre la cabeza daña al enemigo y rebota (otra forma de pasar)
            if (pvy > 60f && !onGround && stompCd <= 0f && stompable(e) && abs(px - e.x) < ew(e) / 2f + 7f && py >= top - 6f && py <= top + 12f) {
                stompCd = 0.12f
                hurtEnemy(e, whipDmg, px, true, false)
                pvy = -Ballistics.jumpV(62f, AdvCfg.GRAV)
                jumping = false
                if (boots) jumpsLeft = 1
                invuln = max(invuln, 0.12f)
                ev(EV_LAND, px, py)
                continue
            }
            if (invuln <= 0f && overlap(px - 9f, py - 29f, px + 9f, py - 1f, e.x - ew(e) / 2f, top, e.x + ew(e) / 2f, e.y)) {
                playerHurt(touchDmg(e), e.x)
            }
        }
    }

    // ---------------- ayudas de IA
    private val aiTmp = FloatArray(2)
    private fun toP(e: AdvEnemy) = if (px >= e.x) 1 else -1
    private fun sameLevel(e: AdvEnemy, tol: Float = 45f) = abs(py - e.floor) < tol
    private fun clampPatrol(e: AdvEnemy, m: Float = 4f) { e.x = e.x.coerceIn(e.x0 + m, e.x1 - m) }

    private fun patrolWalk(e: AdvEnemy, v: Float, dt: Float) {
        e.x += e.face * v * dt
        if (e.x < e.x0 + 10f) { e.x = e.x0 + 10f; e.face = 1 }
        if (e.x > e.x1 - 10f) { e.x = e.x1 - 10f; e.face = -1 }
    }

    private fun approach(e: AdvEnemy, v: Float, dt: Float, keep: Float) {
        e.face = toP(e)
        if (abs(px - e.x) > keep) e.x += e.face * v * dt
        clampPatrol(e)
    }

    /** Física de salto de los enemigos de suelo: gravedad y aterrizaje en su piso. Devuelve true al aterrizar. */
    private fun hopPhysics(e: AdvEnemy, g: Float, dt: Float): Boolean {
        e.vy += g * dt
        e.x += e.vx * dt
        e.y += e.vy * dt
        if (e.x < e.x0 + 6f) { e.x = e.x0 + 6f; e.vx = abs(e.vx) * 0.4f }
        if (e.x > e.x1 - 6f) { e.x = e.x1 - 6f; e.vx = -abs(e.vx) * 0.4f }
        if (e.y >= e.floor && e.vy >= 0f) { e.y = e.floor; e.vy = 0f; e.vx = 0f; return true }
        return false
    }

    private fun shoot(x: Float, y: Float, vx: Float, vy: Float, kind: Int, dmg: Int, r: Float, grav: Float, life: Float) {
        projs.add(AdvProj(x, y, vx, vy, kind, dmg, false, r).also { it.grav = grav; it.life = life })
    }

    private fun shockwave(x: Float, fy: Float, speed: Float, life: Float, dmg: Int) {
        hazs.add(AdvHaz(x - 9f, fy - 16f, x + 9f, fy, speed, 0f, life, dmg, false, 4))
        hazs.add(AdvHaz(x - 9f, fy - 16f, x + 9f, fy, -speed, 0f, life, dmg, false, 4))
    }

    /**
     * Árbol de comportamiento cuerpo a cuerpo (caballero y gólem), al estilo de los demos de gdx-ai / HoriK816:
     * 1) si ya está atacando, seguir el ataque; 2) si el jugador está a su alcance y no hay enfriamiento, empezar;
     * 3) si el jugador está en su rango de aggro, acercarse; 4) si no, patrullar.
     */
    private val meleeTree = BTree<AdvEnemy>(
        BTree.Selector(
            BTree.Sequence(BTree.Cond { it.st != 0 }, BTree.Act { e, dt -> meleeAttack(e, dt) }),
            BTree.Sequence(
                BTree.Cond { it.cd <= 0f && abs(px - it.x) < meleeReach(it) && sameLevel(it) },
                BTree.Act { e, _ -> e.st = 1; e.t = 0f; e.face = toP(e); BTree.St.RUN }
            ),
            BTree.Sequence(
                BTree.Cond { (abs(px - it.x) < meleeAggro(it) || it.aggro && abs(px - it.x) < 360f) && sameLevel(it, 60f) },
                BTree.Act { e, dt -> approach(e, meleeSpeed(e, true) * spd(e), dt, if (e.type == E_GOLEM) 50f else 40f); BTree.St.RUN }
            ),
            BTree.Act { e, dt -> patrolWalk(e, meleeSpeed(e, false) * spd(e), dt); BTree.St.RUN }
        )
    )

    private fun meleeReach(e: AdvEnemy) = if (e.type == E_GOLEM) 92f else 66f
    private fun meleeAggro(e: AdvEnemy) = if (e.type == E_GOLEM) 260f else 175f
    private fun meleeSpeed(e: AdvEnemy, chase: Boolean) = if (e.type == E_GOLEM) (if (chase) 24f else 16f) else (if (chase) 32f else 24f)

    private fun meleeAttack(e: AdvEnemy, dt: Float): BTree.St {
        e.t += dt
        val s = spd(e)
        if (e.type == E_KNIGHT) {
            when (e.st) {
                1 -> if (e.t > 0.4f / s) { e.st = 2; e.t = 0f }                      // aviso: levanta el escudo
                2 -> { e.x += e.face * 215f * s * dt; clampPatrol(e); if (e.t > 0.24f) { e.st = 3; e.t = 0f } } // embestida
                else -> if (e.t > 0.55f) { e.st = 0; e.t = 0f; e.cd = 1.7f / s }
            }
        } else {
            when (e.st) {
                1 -> if (e.t > 0.75f / s) {                                            // levanta los puños
                    e.st = 2; e.t = 0f
                    shockwave(e.x, e.floor, 195f * s, if (e.elite) 1.0f else 0.75f, 2)
                    shake = max(shake, 5f)
                    ev(EV_SLAM, e.x, e.floor)
                }
                2 -> if (e.t > 0.9f) { e.st = 0; e.t = 0f; e.cd = (if (e.elite) 1.5f else 2.2f) / s } // queda expuesto
            }
        }
        return BTree.St.RUN
    }

    private fun enemyAi(e: AdvEnemy, dt: Float) {
        val toP = toP(e)
        val s = spd(e)
        if (e.cd > 0f) e.cd -= dt
        when (e.type) {
            E_WALKER -> {
                // se apura si te ve en su mismo piso (mundos 4+)
                val chase = world >= 4 && abs(px - e.x) < 200f && sameLevel(e) && toP == e.face
                patrolWalk(e, (if (chase) 62f else 40f) * s, dt)
            }
            E_KNIGHT, E_GOLEM -> meleeTree.tick(e, dt)
            E_BAT -> {
                e.t += dt
                if (abs(px - e.x) < 300f) { e.x += toP * 62f * s * dt; e.face = toP }
                // en mundos avanzados baja poco a poco hacia tu altura
                if (world >= 5 && abs(px - e.x) < 220f) e.baseY += ((py - 40f) - e.baseY).coerceIn(-20f * dt, 20f * dt)
                e.y = e.baseY + sin(e.t * 3f) * 26f
            }
            E_MEDUSA -> {
                e.t += dt
                if (e.st == 0 && abs(px - e.x) < 430f) { e.st = 1; e.face = toP }
                if (e.st == 1) {
                    e.x += e.face * 78f * s * dt
                    e.y = e.baseY + sin(e.t * 3.4f) * 36f
                    if (abs(e.x - px) > 520f) e.dead = true
                }
            }
            E_GHOST -> {
                e.t += dt
                val behind = e.aggro || (e.x - px) * face < 0f // persigue si el jugador mira hacia otro lado
                e.st = if (behind) 1 else 0
                if (e.st == 1 && abs(px - e.x) < 380f) {
                    // Arrive: se acerca y frena al llegar (no atraviesa de golpe)
                    Steer.arrive(e.x, e.y, e.vx, e.vy, px, py - 24f, 52f * s, 260f, 60f, 4f, aiTmp)
                    e.vx += aiTmp[0] * dt; e.vy += aiTmp[1] * dt
                    e.x += e.vx * dt; e.y += e.vy * dt
                } else {
                    e.vx *= 0.9f; e.vy *= 0.9f
                    e.y += (e.baseY + sin(e.t * 2f) * 8f - e.y) * min(1f, dt * 2f)
                }
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
                        if (abs(px - e.x) < 260f) e.x += toP * 44f * s * dt
                        clampPatrol(e)
                    }
                }
            }
            E_THROWER -> {
                e.face = toP
                if (abs(px - e.x) < 340f) e.t += dt
                if (e.t > 2.3f / s) {
                    e.t = 0f
                    // tiro parabólico apuntado (con adelanto) en vez de una velocidad fija
                    Ballistics.aim(e.x, e.y - 26f, px, py - 12f, pvx * 0.6f, 330f, 700f, aiTmp)
                    shoot(e.x, e.y - 26f, aiTmp[0], aiTmp[1], 0, 2, 6f, 700f, 3f)
                }
            }
            E_HOPPER -> {
                if (e.y >= e.baseY && e.vy >= 0f) {
                    e.y = e.baseY; e.vy = 0f; e.vx = 0f
                    e.t += dt
                    e.face = toP
                    if (e.t > 1.1f / s && abs(px - e.x) < 320f) {
                        e.t = 0f
                        e.vy = -340f
                        // salta hacia donde va a estar el jugador (vuelo ~0.75 s)
                        e.vx = ((px + pvx * 0.4f - e.x) / 0.75f).coerceIn(-150f * s, 150f * s)
                    }
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
                if (e.t > max(1.3f, 2.0f - world * 0.06f)) {
                    e.t = 0f
                    shoot(e.x, e.y - 14f, (160f + world * 5f) * toP, 0f, 0, 2, 5f, 0f, 3f)
                }
            }
            E_POP -> {
                e.face = toP
                when (e.st) {
                    0 -> { e.t += dt; e.y = e.floor + 26f; if (e.t > 1.5f && abs(px - e.x) < 330f && abs(px - e.x) > 34f) { e.st = 1; e.t = 0f } }
                    1 -> { e.t += dt; e.y = e.floor + 26f * (1f - min(1f, e.t / 0.3f)); if (e.t >= 0.3f) { e.st = 2; e.t = 0f; e.vy = 0f } }
                    2 -> {
                        e.t += dt
                        e.y = e.floor
                        val shots = if (world >= 6) 3 else 2
                        if (e.vy < shots && e.t > 0.35f + 0.6f * e.vy) {
                            e.vy += 1f
                            shoot(e.x, e.floor - 16f, (165f + world * 5f) * toP, 0f, 0, 2, 5f, 0f, 2.6f)
                        }
                        if (e.t > 0.75f + 0.6f * shots) { e.st = 3; e.t = 0f }
                    }
                    else -> { e.t += dt; e.y = e.floor + 26f * min(1f, e.t / 0.3f); if (e.t >= 0.3f) { e.st = 0; e.t = 0f; e.vy = 0f } }
                }
            }
            E_CEIL -> {
                e.face = toP
                when (e.st) {
                    0 -> {
                        if (abs(px - e.x) < 300f) {
                            e.t += dt
                            // apunta adonde vas a estar cuando caiga la gota (~0.55 s)
                            val tx = (px + pvx * 0.5f).coerceIn(e.x0 + 10f, e.x1 - 10f)
                            e.x += (tx - e.x).coerceIn(-1f, 1f) * min(abs(tx - e.x), 55f * s * dt)
                            if (e.t > max(1.5f, 2.3f - world * 0.07f)) { e.st = 1; e.t = 0f }
                        }
                    }
                    else -> {
                        e.t += dt
                        if (e.t > 0.55f) {
                            shoot(e.x, e.y + 4f, 0f, 120f, 0, 2, 5f, 520f, 2.2f)
                            e.st = 0; e.t = 0f
                        }
                    }
                }
            }
            E_CHARGER -> {
                when (e.st) {
                    0 -> {
                        patrolWalk(e, 35f * s, dt)
                        if (abs(px - e.x) < 210f && abs(py - e.y) < 40f && toP == e.face) { e.st = 1; e.t = 0f }
                    }
                    1 -> { e.t += dt; if (e.t > 0.55f / s) { e.st = 2; e.t = 0f } }
                    2 -> {
                        e.t += dt
                        e.x += e.face * 255f * s * dt
                        if (e.x < e.x0 + 12f) { e.x = e.x0 + 12f; e.st = 3; e.t = 0f }
                        if (e.x > e.x1 - 12f) { e.x = e.x1 - 12f; e.st = 3; e.t = 0f }
                        if (e.t > 0.8f) { e.st = 3; e.t = 0f }
                    }
                    else -> { e.t += dt; if (e.t > 0.7f) { e.st = 0; e.face = toP } }
                }
            }
            E_ARCHER -> {
                e.face = toP
                val dx = abs(px - e.x)
                // mantiene la distancia: huye si estás cerca, se acerca si estás lejos
                if (e.st == 0) {
                    if (dx < 105f) e.x -= toP * 48f * s * dt else if (dx > 270f && dx < 420f) e.x += toP * 34f * s * dt
                    clampPatrol(e)
                }
                if (dx < 380f && abs(py - e.floor) < 170f) e.t += dt
                when (e.st) {
                    0 -> if (e.t > 2.2f / s) { e.st = 1; e.t = 0f }
                    1 -> if (e.t > 0.55f) {                      // tensa el arco (aviso) y dispara
                        Ballistics.aim(e.x + toP * 8f, e.y - 22f, px, py - 15f, pvx, 300f, 420f, aiTmp)
                        shoot(e.x + toP * 8f, e.y - 22f, aiTmp[0], aiTmp[1], 7, 2, 4f, 420f, 3f)
                        if (e.elite) {
                            Ballistics.aim(e.x + toP * 8f, e.y - 22f, px + toP * 40f, py - 15f, 0f, 300f, 420f, aiTmp)
                            shoot(e.x + toP * 8f, e.y - 22f, aiTmp[0], aiTmp[1], 7, 2, 4f, 420f, 3f)
                        }
                        e.st = 0; e.t = 0f
                    }
                }
            }
            E_SLIME -> {
                if (e.y >= e.floor && e.vy >= 0f) {
                    e.y = e.floor
                    e.t += dt
                    val wait = (if (e.small) 0.45f else 0.75f) / s
                    if (e.t > wait) {
                        e.t = 0f
                        val near = abs(px - e.x) < 260f && sameLevel(e, 70f)
                        if (near) e.face = toP else if (e.x < e.x0 + 20f) e.face = 1 else if (e.x > e.x1 - 20f) e.face = -1
                        e.vy = -Ballistics.jumpV(if (e.small) 30f else 46f, 1000f)
                        e.vx = e.face * (if (e.small) 95f else 80f) * s
                    }
                } else hopPhysics(e, 1000f, dt)
            }
            E_BOMBER -> {
                e.t += dt
                // se coloca encima del jugador (Arrive en x) y suelta bombas
                val tx = px.coerceIn(e.x0 - 80f, e.x1 + 80f)
                Steer.arrive(e.x, 0f, e.vx, 0f, tx, 0f, 85f * s, 220f, 70f, 3f, aiTmp)
                e.vx += aiTmp[0] * dt
                e.x += e.vx * dt
                e.y = e.baseY + sin(e.t * 2.2f) * 6f
                e.face = if (e.vx >= 0f) 1 else -1
                if (e.cd <= 0f && abs(px - e.x) < 26f && py > e.y) {
                    e.cd = 1.9f / s
                    shoot(e.x, e.y + 6f, e.vx * 0.3f, 40f, 8, 2, 6f, 650f, 3f)
                }
            }
            E_MAGE -> {
                e.t += dt
                e.face = toP
                when (e.st) {
                    0 -> { // flota quieto; al acercarte prepara el salto
                        e.vis = min(1f, e.vis + dt * 4f)
                        e.y = e.floor - 3f + sin(e.t * 3f) * 2f
                        if (abs(px - e.x) < 320f && sameLevel(e, 120f) && e.cd <= 0f) { e.st = 1; e.t = 0f }
                    }
                    1 -> { // se desvanece
                        e.vis = max(0f, 1f - e.t / 0.4f)
                        if (e.t > 0.4f) {
                            val side = if (rnd.nextBoolean()) 1 else -1
                            var nx = px + side * (120f + rnd.nextFloat() * 50f)
                            if (nx < e.x0 + 6f || nx > e.x1 - 6f) nx = px - side * (120f + rnd.nextFloat() * 50f)
                            nx = nx.coerceIn(e.x0 + 6f, e.x1 - 6f)
                            if (abs(nx - px) < 70f) nx = if (px < (e.x0 + e.x1) / 2f) e.x1 - 6f else e.x0 + 6f
                            e.x = nx; e.ox = nx
                            e.st = 2; e.t = 0f
                            ev(EV_TELEPORT, e.x, e.floor - 18f)
                        }
                    }
                    2 -> { e.vis = min(1f, e.t / 0.35f); if (e.t > 0.35f) { e.st = 3; e.t = 0f } }
                    3 -> if (e.t > 0.5f / s) { // carga y lanza orbes
                        val a0 = atan2((py - 15f) - (e.y - 22f), px - e.x)
                        val n = if (e.elite || world >= 10) 5 else 3
                        for (i in 0 until n) {
                            val an = a0 + (i - (n - 1) / 2f) * 0.26f
                            shoot(e.x, e.y - 22f, cos(an) * 150f * s, sin(an) * 150f * s, 9, 2, 6f, 0f, 3.2f)
                        }
                        e.st = 4; e.t = 0f
                    }
                    else -> if (e.t > 1.3f) { e.st = 0; e.t = 0f; e.cd = 1.1f / s } // cansado: buen momento para golpearlo
                }
            }
            E_WOLF -> {
                when (e.st) {
                    0 -> {
                        patrolWalk(e, 50f * s, dt)
                        if (e.cd <= 0f && abs(px - e.x) < 230f && sameLevel(e, 40f)) { e.st = 1; e.t = 0f; e.face = toP }
                    }
                    1 -> { e.t += dt; e.face = toP; if (e.t > 0.42f / s) { // se agacha (aviso) y salta hacia donde vas
                        val air = 2f * 380f / 1100f
                        val tx = px + pvx * air * 0.7f
                        e.vx = ((tx - e.x) / air).coerceIn(-330f * s, 330f * s)
                        e.vy = -380f
                        e.y = e.floor - 1f
                        e.st = 2; e.t = 0f
                    } }
                    2 -> if (hopPhysics(e, 1100f, dt)) { e.st = 3; e.t = 0f; ev(EV_LAND, e.x, e.floor) }
                    else -> { e.t += dt; if (e.t > 0.6f) { e.st = 0; e.cd = 1.3f / s; e.face = toP } }
                }
            }
            E_SKULL -> {
                e.t += dt
                when (e.st) {
                    0 -> { // ronda en círculos alrededor de su punto (Wander suave) y lo va acercando
                        e.tx += ((px - e.tx).coerceIn(-1f, 1f)) * 26f * s * dt
                        e.tx = e.tx.coerceIn(e.x0 - 120f, e.x1 + 120f)
                        Steer.arrive(e.x, e.y, e.vx, e.vy, e.tx + cos(e.t * 1.7f) * 34f, e.baseY + sin(e.t * 3.4f) * 14f, 120f, 500f, 40f, 1f, aiTmp)
                        e.vx += aiTmp[0] * dt; e.vy += aiTmp[1] * dt
                        e.x += e.vx * dt; e.y += e.vy * dt
                        e.face = toP
                        val d = abs(px - e.x)
                        if (e.cd <= 0f && d < 240f && d > 40f) { e.st = 1; e.t = 0f }
                    }
                    1 -> { // tiembla (aviso) y fija el blanco
                        e.vx *= 0.85f; e.vy *= 0.85f
                        if (e.t > 0.45f / s) {
                            e.st = 2; e.t = 0f
                            e.ty = py - 14f
                            val pt = Steer.predictTime(e.x, e.y, 0f, 0f, px, py, 0.35f)
                            e.tx = px + pvx * pt
                        }
                    }
                    2 -> { // picada: Seek hacia el blanco fijado
                        Steer.seek(e.x, e.y, e.tx, e.ty, 1500f, aiTmp)
                        e.vx += aiTmp[0] * dt; e.vy += aiTmp[1] * dt
                        aiTmp[0] = e.vx; aiTmp[1] = e.vy
                        Steer.limit(aiTmp, 270f * s)
                        e.vx = aiTmp[0]; e.vy = aiTmp[1]
                        e.x += e.vx * dt; e.y = min(e.y + e.vy * dt, AdvCfg.GROUND - 8f)
                        e.face = if (e.vx >= 0f) 1 else -1
                        val arrived = abs(e.x - e.tx) < 10f && abs(e.y - e.ty) < 10f
                        if (arrived || e.t > 0.95f || e.y > AdvCfg.GROUND - 6f) { e.st = 3; e.t = 0f; e.tx = e.x }
                    }
                    else -> { // vuelve a su altura (Arrive)
                        Steer.arrive(e.x, e.y, e.vx, e.vy, e.tx, e.baseY, 120f, 500f, 50f, 6f, aiTmp)
                        e.vx += aiTmp[0] * dt; e.vy += aiTmp[1] * dt
                        e.x += e.vx * dt; e.y = min(e.y + e.vy * dt, AdvCfg.GROUND - 8f)
                        if (abs(e.y - e.baseY) < 8f || e.t > 2f) { e.st = 0; e.t = 0f; e.cd = 1.7f / s }
                    }
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
                // flechas que se clavan en el suelo y bombas que explotan al tocarlo
                if ((p.kind == 7 || p.kind == 8) && p.vy > 0f) {
                    val g = groundBelow(p.x, p.y - 8f)
                    if (p.y >= g - 3f) {
                        if (p.kind == 8) {
                            hazs.add(AdvHaz(p.x - 26f, g - 34f, p.x + 26f, g, 0f, 0f, 0.22f, 2, false, 5))
                            shake = max(shake, 3f)
                            ev(EV_BOOM, p.x, g)
                        }
                        it.remove()
                        continue
                    }
                }
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
            bteleMax = bst
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
                    bteleMax = bst
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

    /** Fase del jefe según su vida (como en 4bush/Game): 1 > 70 %, 2 hasta 35 %, 3 por debajo. */
    fun bossPhase(): Int = if (bhp * 100 > bmax * 70) 1 else if (bhp * 100 > bmax * 35) 2 else 3

    private fun endAct(cd: Float) {
        bstate = BS_WALK
        bcd = cd * (if (benraged) 0.65f else 1f)
        if (bossPhase() == 2 && world >= 6) bcd *= 0.9f
        // fase 3 de los mundos finales: a veces encadena otro ataque casi sin pausa
        if (bossPhase() == 3 && world >= 8 && rnd.nextInt(100) < 25 + (world - 8) * 8) bcd = 0.3f
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
                // invoca enemigos propios de su mundo (más en la fase 3)
                val live = enemies.count { !it.dead && it.x > arenaX }
                val cap = if (bossPhase() >= 3 && world >= 9) 4 else 3
                val pool = allowed().filter { it == E_WALKER || it == E_BAT || it == E_SLIME || it == E_WOLF || it == E_SKULL || it == E_HOPPER || it == E_GHOST }
                val n = if (bossPhase() >= 3 && world >= 9) 3 else 2
                if (live < cap) for (i in 0 until n) {
                    val t = if (pool.isEmpty()) (if (world % 2 == 0) E_WALKER else E_BAT) else pool[rnd.nextInt(pool.size)]
                    var x = arenaX + 80f + rnd.nextFloat() * (viewW - 160f)
                    if (abs(x - px) < 90f) x = if (px < arenaX + viewW / 2f) arenaX + viewW - 90f else arenaX + 90f
                    val e = addEnemy(t, x, AdvCfg.GROUND, arenaX + 10f, arenaX + viewW - 10f)
                    e.aggro = true
                    ev(EV_POTION, e.x, e.y - 10f, 1)
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
                bz = max(0f, bz - bvy * dt)
                bvy += 1500f * dt
                bx = (bx + bvx * dt).coerceIn(arenaX + bdef.w / 2f + 4f, arenaX + viewW - bdef.w / 2f - 4f)
                bt2 += dt
                bsub = if (bsub == 0 && bvy > 0f && bt2 > 0.3f) 1 else bsub
                if (bt2 > 0.9f || (bsub == 1 && bt2 > 0.8f)) {
                    bvx = 0f; bvy = 0f; bz = 0f
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

    init { buildWorld(); enterRoom(0, 0, 0f); saveRoom = 0 }
}

/** Taller (editor de salas estilo Mario Maker): letras de los planos y utilidades. */
object AdvMk {
    /** Letras de los enemigos en los planos, en el mismo orden que TYPES. */
    const val ENEMIES = "wbthugmKGdALBZWOHTC"
    val TYPES = intArrayOf(
        Adv.E_WALKER, Adv.E_BAT, Adv.E_THROWER, Adv.E_HOPPER, Adv.E_TURRET, Adv.E_CHARGER, Adv.E_MEDUSA, Adv.E_KNIGHT,
        Adv.E_GHOST, Adv.E_SPIDER, Adv.E_ARCHER, Adv.E_SLIME, Adv.E_BOMBER, Adv.E_MAGE, Adv.E_WOLF, Adv.E_SKULL,
        Adv.E_GOLEM, Adv.E_POP, Adv.E_CEIL
    )
    /** Filas del plano: 0 = suelo (abajo) y 14 encima. */
    const val ROWS = 15
    const val MAX_W = 240
    const val MIN_W = 40

    /** Sala nueva: suelo de punta a punta, inicio a la izquierda y meta a la derecha. */
    fun blank(w: Int = 100): Array<String> {
        val g = Array(ROWS) { CharArray(w) { '.' } }
        for (x in 0 until w) g[ROWS - 1][x] = '_'
        g[ROWS - 2][2] = 'P'
        g[ROWS - 2][w - 4] = 'E'
        return Array(ROWS) { String(g[it]) }
    }

    /** Texto para guardar: "1|tema|élites|fila/fila/...". */
    fun encode(rows: Array<String>, theme: Int, elite: Boolean): String =
        "1|" + theme + "|" + (if (elite) 1 else 0) + "|" + rows.joinToString("/")

    class Saved(val rows: Array<String>, val theme: Int, val elite: Boolean)

    fun decode(s: String): Saved? {
        return try {
            val p = s.split("|")
            if (p.size < 4 || p[0] != "1") return null
            val rows = p[3].split("/").toTypedArray()
            if (rows.size != ROWS) return null
            val w = rows[0].length
            if (w < MIN_W || w > MAX_W || rows.any { it.length != w }) return null
            Saved(rows, p[1].toInt().coerceIn(0, AdvCfg.WORLDS - 1), p[2] == "1")
        } catch (e: Exception) { null }
    }

    /** ¿Qué letras se pueden poner en la fila del suelo? */
    fun groundOnly(ch: Char) = ch == '_' || ch == 'S' || ch == 'M' || ch == 'N'
}
