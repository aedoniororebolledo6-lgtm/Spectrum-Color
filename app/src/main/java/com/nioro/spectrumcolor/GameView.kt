package com.nioro.spectrumcolor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Lo que el juego necesita de la actividad (diálogos de texto y cerrar la app). */
interface HostApi {
    fun prompt(title: String, initial: String, hint: String, onOk: (String) -> Unit)
    fun quit()
    fun openUrl(url: String)
}

/**
 * Spectrum Color: pantalla única dibujada con Canvas. Todo se dibuja en "unidades de diseño"
 * (el ancho de la pantalla siempre vale 1080), igual que el motor de juego (Engine.kt).
 */
class GameView(context: Context, private val api: HostApi) : View(context), Choreographer.FrameCallback, android.hardware.SensorEventListener {

    private enum class Scr { HOME, LEVELS, SETTINGS, PLAY, MP_MENU, MP_HOST, MP_JOIN, SKINS, THEMES, EDITOR }
    private enum class Mode { LEVEL, ENDLESS, MP }
    private enum class Ov { NONE, PAUSE, DEAD, WIN }

    private class Hit(val id: String, val l: Float, val t: Float, val r: Float, val b: Float)

    private class Part(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val color: Int, val size: Float, var life: Float, val maxLife: Float
    )

    private class Bubble(val nx: Float, val ny: Float, val size: Float, val depth: Float, val sway: Float)

    private class Room(val name: String, val ip: String, val port: Int, var seen: Long)

    private val gold = 0xFFFFD84D.toInt()
    private val prefs = Prefs(context).also { I18n.lang = it.lang }
    private val audio = Audio(context, prefs)
    private val rnd = Random(System.nanoTime())
    private val handler = Handler(Looper.getMainLooper())
    // ---- actualización (0 = sin comprobar, 1 = buscando, 2 = al día, 3 = hay versión nueva, 4 = sin conexión) ----
    @Volatile private var updState = 0
    @Volatile private var updNew = 0

    private fun installedBuild(): Int = try {
        val vn = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        vn.substringAfterLast('.').toIntOrNull() ?: 0
    } catch (e: Exception) {
        0
    }

    private fun checkUpdate() {
        if (updState == 1) return
        updState = 1
        Thread {
            try {
                val u = java.net.URL("https://api.github.com/repos/aedoniororebolledo6-lgtm/Spectrum-Color/releases/tags/latest")
                val cn = u.openConnection() as java.net.HttpURLConnection
                cn.connectTimeout = 8000
                cn.readTimeout = 8000
                cn.setRequestProperty("Accept", "application/vnd.github+json")
                val txt = cn.inputStream.bufferedReader().use { it.readText() }
                cn.disconnect()
                val m = Regex("Compilaci.{1,8}#(\\d+)").find(txt)
                val n = m?.groupValues?.get(1)?.toIntOrNull()
                if (n == null) {
                    updState = 4
                } else {
                    updNew = n
                    updState = if (n > installedBuild()) 3 else 2
                }
            } catch (e: Exception) {
                updState = 4
            }
        }.start()
    }

    // ---- pantalla y tiempo ----
    private var sc = 1f
    private var vh = 2400f
    private var topI = 60f
    private var running = false
    private var lastNanos = 0L
    private var time = 0f

    // ---- navegación ----
    private var scrT0 = -10f
    private var scrDir = 1
    private var ovT0 = -10f
    private var uiIdx = 0
    private fun depthOf(s: Scr): Int = when (s) {
        Scr.HOME -> 0
        Scr.LEVELS, Scr.SETTINGS, Scr.MP_MENU, Scr.SKINS -> 1
        Scr.THEMES, Scr.MP_HOST, Scr.MP_JOIN, Scr.EDITOR -> 2
        Scr.PLAY -> 3
    }
    private var scr = Scr.HOME
        set(v) {
            if (field != v) {
                scrDir = if (depthOf(v) >= depthOf(field)) 1 else -1
                field = v
                scrT0 = time
            }
        }
    private var mode = Mode.LEVEL
    private var ov = Ov.NONE
        set(v) {
            if (field != v) {
                field = v
                ovT0 = time
            }
        }
    private var level = 1
    private var levelPage = 0
    private val hits = ArrayList<Hit>()
    private var pressedId: String? = null
    private var dragSlider: String? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragged = false
    private var skinScroll = 0f
    private var skinTab = 0
    private var skinMax = 0f
    // ---- tema Gatitos: los gatitos se mueven al inclinar el celular y se apartan del dedo ----
    private val catOx = FloatArray(32)
    private val catOy = FloatArray(32)
    private val catVx = FloatArray(32)
    private val catVy = FloatArray(32)
    private val catPx = FloatArray(32)
    private val catPy = FloatArray(32)
    private var tiltX = 0f
    private var tiltY = 0.7f
    private var touchX = -1000f
    private var touchY = -1000f
    private var touching = false
    private var sensorOn = false
    private var skinVel = 0f
    // ---- editor de niveles (solo versión de desarrollador) ----
    private class EdRow(val label: String, val value: String, val minus: (() -> Unit)?, val plus: (() -> Unit)?)
    private var edMode = false
    private var edTesting = false
    private var edLevel = 0
    private var edData: LevelData? = null
    private var edKind = -1
    private var edIdx = -1
    private var edCam = 0f
    private var edDirty = false
    private var edScroll = 0f
    private var edStep = 1
    private var edDrag = 0
    private var edDragY0 = 0f
    private var edMsg = ""
    private var edMsgT = -10f
    private var edConfirm = false
    private var edPanelOpen = true
    private var edOverview = false
    private var edMenu = 0
    private var edDragPushed = false
    private var edOvS = 0.2f
    private var edOvY0 = 0f
    private var edSpeedAcc = 1f
    private val edUndo = ArrayList<String>()
    private val edRedo = ArrayList<String>()
    private val edRows = ArrayList<EdRow>()
    private var lastMoveT = 0L
    private var levelOff = 0f
    private var levelDrag = false
    private var levelVel = 0f
    private var slidePtr = -1
    private var slideRefX = 0f
    private var slideRefBx = Cfg.W / 2f
    private var resetArmedAt = -10f

    // ---- partida ----
    private var course: Course? = null
    private var sim: Sim? = null
    private var world = 0
    private var acc = 0f
    private var deaths = 0
    private var endTimer = 0f
    private var endHandled = false
    private var newBest = false
    private var earnedStars = 0
    private var lastStarCount = 0
    private var lastSwCount = 0
    private var shake = 0f
    private val parts = ArrayList<Part>()
    private val trail = ArrayList<FloatArray>()
    private val bubbles = ArrayList<Bubble>()
    private var bgShader: Shader? = null
    private var bgShaderWorld = -1

    // ---- multijugador ----
    // Cada sala/partida usa una cola nueva: los mensajes tardíos de una conexión vieja nunca afectan a la nueva.
    private var events = ConcurrentLinkedQueue<String>()
    private var host: NetHost? = null
    private var guest: NetGuest? = null
    private var scanner: NetScanner? = null
    private var match: Match? = null
    private var lastPhase = Match.Phase.LOBBY
    private val rooms = LinkedHashMap<String, Room>()
    private var netStatus = ""
    private var connecting = false
    private var myIps: List<String> = emptyList()
    private var mcLock: WifiManager.MulticastLock? = null
    private var cdTime = 0f
    private var lastShown = 4
    private var goFlash = 0f
    private var runTime = 0f
    private var mpFrozen = false
    private var rEndTimer = 0f
    private var musicStarted = false

    // ======================================================================
    // Ciclo de vida
    // ======================================================================

    fun resume() {
        if (!musicStarted) {
            musicStarted = true
            audio.setTrack(0)
        }
        audio.onResume()
        if (!running) {
            running = true
            lastNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        audio.onPause()
        if (sensorOn) stopSensor()
        pressedId = null
        dragSlider = null
        if (scr == Scr.PLAY && mode != Mode.MP && ov == Ov.NONE) {
            val sm = sim
            if (sm != null && !sm.dead && !sm.finished) ov = Ov.PAUSE
        }
    }

    fun destroy() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        handler.removeCallbacksAndMessages(null)
        closeNet()
        audio.release()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastNanos == 0L) 0.016f else min(0.05f, (frameTimeNanos - lastNanos) / 1_000_000_000f)
        lastNanos = frameTimeNanos
        try {
            update(dt)
        } catch (e: Exception) {
            Log.e("SpectrumColor", "update", e)
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        if (nw <= 0 || nh <= 0) return
        sc = nw / Cfg.W
        vh = nh / sc
        bgShaderWorld = -1
        val r = Random(7)
        bubbles.clear()
        for (i in 0 until 14) {
            bubbles.add(
                Bubble(
                    r.nextFloat(), r.nextFloat(), 26f + r.nextFloat() * 90f,
                    0.04f + r.nextFloat() * 0.22f, r.nextFloat() * 6.28f
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var top = insets.systemWindowInsetTop.toFloat()
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            if (cut != null) top = max(top, cut.safeInsetTop.toFloat())
        }
        topI = max(40f, top / sc)
        return super.onApplyWindowInsets(insets)
    }

    // ======================================================================
    // Navegación y acciones
    // ======================================================================

    /** true si el juego manejó el botón "atrás"; false = que la app se cierre. */
    fun onBack(): Boolean {
        when (scr) {
            Scr.HOME -> return false
            Scr.LEVELS, Scr.SETTINGS, Scr.MP_MENU, Scr.SKINS -> goHome()
            Scr.THEMES -> scr = Scr.SETTINGS
            Scr.EDITOR -> edBack()
            Scr.MP_HOST, Scr.MP_JOIN -> {
                leaveMp()
                scr = Scr.MP_MENU
            }
            Scr.PLAY -> {
                if (mode == Mode.MP) {
                    val ph = match?.phase
                    if (ph == null || ph == Match.Phase.MATCH_END || ph == Match.Phase.CLOSED) {
                        exitToMenu()
                    } else {
                        ov = if (ov == Ov.PAUSE) Ov.NONE else Ov.PAUSE
                    }
                } else {
                    when (ov) {
                        Ov.NONE -> ov = Ov.PAUSE
                        Ov.PAUSE -> ov = Ov.NONE
                        Ov.DEAD, Ov.WIN -> exitToMenu()
                    }
                }
            }
        }
        audio.play(Sfx.BACK)
        return true
    }

    private fun goHome() {
        edTesting = false
        edMode = false
        scr = Scr.HOME
        audio.setTrack(0)
    }

    private fun exitToMenu() {
        when (mode) {
            Mode.LEVEL -> {
                if (edTesting) {
                    edTesting = false
                    scr = Scr.EDITOR
                } else {
                    levelPage = Levels.world(level)
                    scr = Scr.LEVELS
                }
            }
            Mode.ENDLESS -> scr = Scr.HOME
            Mode.MP -> {
                leaveMp()
                scr = Scr.MP_MENU
            }
        }
        ov = Ov.NONE
        sim = null
        audio.setTrack(0)
    }

    private fun startLevel(l: Int) {
        if (!Levels.isPlayable(l) || l > prefs.unlocked()) return
        edTesting = false
        level = l
        deaths = 0
        beginPlay(Course.level(l), Mode.LEVEL)
    }

    private fun startEndless() {
        edTesting = false
        beginPlay(Course.endless(rnd.nextLong() and 0x7fffffffffffL), Mode.ENDLESS)
    }

    private fun beginPlay(c: Course, m: Mode) {
        mode = m
        course = c
        world = if (c.isLevel) (if (c.palette >= 0) c.palette.coerceIn(0, Worlds.colors.size - 1) else Levels.world(c.level)) else (((c.seed % 12L) + 12L) % 12L).toInt()
        resetSim()
        scr = Scr.PLAY
        ov = Ov.NONE
        audio.setTrack(1)
    }

    private fun resetSim() {
        val c = course ?: return
        val s = Sim(c, vh)
        s.timeScale = if (mode != Mode.MP && prefs.easy) 0.78f else 1f
        sim = s
        acc = 0f
        endTimer = 0f
        endHandled = false
        newBest = false
        shake = 0f
        lastStarCount = 0
        lastSwCount = 0
        parts.clear()
        trail.clear()
    }

    private fun restartRun() {
        if (mode == Mode.ENDLESS) {
            course = Course.endless(rnd.nextLong() and 0x7fffffffffffL)
            world = (((course!!.seed % 12L) + 12L) % 12L).toInt()
        }
        resetSim()
        ov = Ov.NONE
    }

    private fun haptic(kind: Int) {
        if (prefs.haptics) performHapticFeedback(kind)
    }

    private fun activate(id: String) {
        audio.play(Sfx.CLICK)
        when {
            id == "edit" -> if (BuildConfig.DEV) {
                edMode = true
                levelPage = Levels.world(prefs.unlocked())
                scr = Scr.LEVELS
            }
            id == "play" -> {
                edMode = false
                levelPage = Levels.world(prefs.unlocked())
                scr = Scr.LEVELS
            }
            id == "endless" -> startEndless()
            id == "mp" -> {
                netStatus = ""
                scr = Scr.MP_MENU
            }
            id == "settings" -> scr = Scr.SETTINGS
            id == "skins" -> {
                scr = Scr.SKINS
                skinTab = if (Skins.isAnim(effSkin())) 1 else 0
                val row = (effSkin() - (if (skinTab == 1) Skins.ANIM_FIRST else 0)) / 4
                skinScroll = (row * 280f - 300f).coerceAtLeast(0f)
            }
            id == "tab0" || id == "tab1" || id == "tab2" -> {
                skinTab = id.substring(3).toInt()
                skinScroll = 0f
                skinVel = 0f
            }
            id.startsWith("trail:") -> {
                val k = id.substring(6).toIntOrNull() ?: return
                if (k in 0 until Trails.COUNT && trailOpen(k)) prefs.trail = k
            }
            id.startsWith("skin:") -> {
                val k = id.substring(5).toIntOrNull() ?: return
                if (k in Skins.names.indices && skinOpen(k)) prefs.skin = k
            }
            id == "ttheme" -> scr = Scr.THEMES
            id.startsWith("theme:") -> {
                val k = id.substring(6).toIntOrNull() ?: return
                if (k in 0 until Themes.COUNT) prefs.theme = k
            }
            id == "tlang" -> {
                prefs.lang = (prefs.lang + 1) % 3
                I18n.lang = prefs.lang
            }
            id == "update" -> {
                if (updState == 3) api.openUrl("https://github.com/aedoniororebolledo6-lgtm/Spectrum-Color/releases/latest/download/" + (if (BuildConfig.DEV) "SpectrumColorDev.apk" else "SpectrumColor.apk"))
                else checkUpdate()
            }
            id == "back" -> onBack()
            id == "wprev" -> if (levelPage > 0) {
                levelPage--
                levelOff -= Cfg.W
            }
            id == "wnext" -> if (levelPage < Levels.WORLDS - 1) {
                levelPage++
                levelOff += Cfg.W
            }
            id.startsWith("lvl:") && BuildConfig.DEV && edMode -> openEditor(id.substring(4).toIntOrNull() ?: return)
            id.startsWith("ed") && scr == Scr.EDITOR -> edActivate(id)
            id.startsWith("lvl:") -> {
                val l = id.substring(4).toIntOrNull() ?: return
                startLevel(l)
            }
            // ajustes
            id == "tmusic" -> {
                prefs.musicOn = !prefs.musicOn
                audio.refresh()
            }
            id == "tsfx" -> prefs.sfxOn = !prefs.sfxOn
            id == "thaptic" -> {
                prefs.haptics = !prefs.haptics
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            id == "teasy" -> prefs.easy = !prefs.easy
            id == "tname" || id == "mp_name" -> api.prompt(I18n.t("Tu nombre"), prefs.name, I18n.t("Nombre")) { prefs.name = it }
            id == "topen" -> if (BuildConfig.DEV) prefs.allOpen = !prefs.allOpen
            id == "treset" -> {
                if (time - resetArmedAt < 3f) {
                    prefs.resetProgress()
                    resetArmedAt = -10f
                } else {
                    resetArmedAt = time
                }
            }
            // partida
            id == "pause" -> ov = Ov.PAUSE
            id == "resume" -> ov = Ov.NONE
            id == "restart" -> restartRun()
            id == "retry" -> restartRun()
            id == "exit" -> exitToMenu()
            id == "next" -> {
                if (edTesting) {
                    exitToMenu()
                } else if (level < Levels.PLAYABLE) {
                    deaths = 0
                    startLevel(level + 1)
                } else {
                    exitToMenu()
                }
            }
            // multijugador
            id == "mp_host" -> openHost()
            id == "mp_join" -> openJoin()
            id == "mp_rounds" -> {
                val m = match
                if (m != null) {
                    val opts = Match.ROUND_OPTIONS
                    val i = opts.indexOf(m.rounds)
                    m.rounds = opts[(i + 1) % opts.size]
                    prefs.mpRounds = m.rounds
                }
            }
            id == "mp_start" -> match?.hostStart()
            id == "mp_ip" -> api.prompt(I18n.t("IP del anfitrión"), "", I18n.t("Ejemplo: 192.168.1.20")) { joinTyped(it) }
            id.startsWith("room:") -> {
                val parts2 = id.substring(5).split(":")
                if (parts2.size == 2) joinRoom(parts2[0], parts2[1].toIntOrNull() ?: NetCfg.TCP_PORT)
            }
            id == "rematch" -> match?.rematch()
        }
    }

    // ======================================================================
    // Entrada
    // ======================================================================

    private fun hitAt(x: Float, y: Float): String? {
        for (i in hits.indices.reversed()) {
            val h = hits[i]
            if (x >= h.l && x <= h.r && y >= h.t && y <= h.b) return h.id
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x / sc
        val y = e.y / sc
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> if (scr != Scr.PLAY) {
                touchX = x
                touchY = y
                touching = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
            else -> {}
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastY = y
                dragged = false
                skinVel = 0f
                levelVel = 0f
                levelDrag = false
                lastMoveT = e.eventTime
                val id = hitAt(x, y)
                pressedId = id
                if (scr == Scr.EDITOR) edTouchDown(y)
                if (id != null && id.startsWith("sl:")) {
                    dragSlider = id.substring(3)
                    setSlider(x)
                } else if (id == null && scr == Scr.PLAY) {
                    gameTap()
                    beginSlide(e, 0)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (scr == Scr.PLAY) {
                    gameTap()
                    beginSlide(e, e.actionIndex)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (scr == Scr.PLAY && e.getPointerId(e.actionIndex) == slidePtr) {
                    var other = -1
                    for (k in 0 until e.pointerCount) if (k != e.actionIndex) other = k
                    if (other >= 0) beginSlide(e, other) else slidePtr = -1
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (scr == Scr.PLAY) moveSlide(e)
                if (dragSlider != null) setSlider(x)
                val dtm = (e.eventTime - lastMoveT) / 1000f
                if (scr == Scr.SKINS) {
                    if (!dragged && abs(y - downY) > 22f) {
                        dragged = true
                        pressedId = null
                    }
                    if (dragged) {
                        // con tope elástico: más allá de los extremos cuesta más y al soltar vuelve
                        var d = -(y - lastY)
                        if ((skinScroll < 0f && d < 0f) || (skinScroll > skinMax && d > 0f)) d *= 0.4f
                        skinScroll = (skinScroll + d).coerceIn(-170f, skinMax + 170f)
                        if (dtm > 0.001f) skinVel = skinVel * 0.55f + (d / dtm) * 0.45f
                    }
                    lastY = y
                } else if (scr == Scr.EDITOR) {
                    if (!dragged && abs(y - downY) > 22f) {
                        dragged = true
                        pressedId = null
                    }
                    if (dragged) edTouchDrag(y)
                    lastY = y
                } else if (scr == Scr.LEVELS) {
                    val dx = x - downX
                    if (!levelDrag && abs(dx) > 26f && abs(dx) > abs(y - downY)) {
                        levelDrag = true
                        pressedId = null
                    }
                    if (levelDrag) {
                        var o = dx
                        if ((levelPage == 0 && o > 0f) || (levelPage == Levels.WORLDS - 1 && o < 0f)) o *= 0.35f
                        if (dtm > 0.001f) levelVel = levelVel * 0.55f + ((o - levelOff) / dtm) * 0.45f
                        levelOff = o
                    }
                }
                lastMoveT = e.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val id = pressedId
                pressedId = null
                slidePtr = -1
                if (dragSlider != null) {
                    dragSlider = null
                    audio.play(Sfx.CLICK)
                } else if (scr == Scr.SKINS && dragged) {
                    // era un deslizamiento, no un toque: sigue con inercia (ver update)
                    if (abs(skinVel) < 120f) skinVel = 0f
                } else if (scr == Scr.EDITOR && (dragged || (id == null && edDrag in 1..2) || (id == null && edDrag == 4))) {
                    if (!dragged && id == null) {
                        if (edDrag == 4) {
                            val wy = edOvY0 + (y - edViewTop()) / edOvS
                            edPick(wy, edOvS)
                            edCam = wy - 600f
                        } else {
                            edPick(edCam + (y - edViewTop()))
                        }
                    }
                    edDrag = 0
                } else if (scr == Scr.LEVELS && levelDrag) {
                    levelDrag = false
                    val fling = levelVel
                    val want = if (levelOff < -150f || fling < -900f) 1 else if (levelOff > 150f || fling > 900f) -1 else 0
                    val np = levelPage + want
                    if (want != 0 && np in 0 until Levels.WORLDS) {
                        levelPage = np
                        levelOff += want * Cfg.W
                        audio.play(Sfx.CLICK)
                    }
                } else if (id != null && hitAt(x, y) == id) {
                    activate(id)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                levelDrag = false
                slidePtr = -1
                pressedId = null
                dragSlider = null
            }
        }
        return true
    }

    /** Empieza a seguir un dedo para deslizar la bola (movimiento relativo: no salta al punto tocado). */
    private fun beginSlide(e: MotionEvent, idx: Int) {
        slidePtr = e.getPointerId(idx)
        slideRefX = e.getX(idx) / sc
        slideRefBx = sim?.bx ?: (Cfg.W / 2f)
    }

    private fun moveSlide(e: MotionEvent) {
        val sm = sim ?: return
        val cr = course ?: return
        if (slidePtr < 0) return
        val pi = e.findPointerIndex(slidePtr)
        if (pi < 0) return
        val px = e.getX(pi) / sc
        if (ov == Ov.NONE && !sm.dead && !sm.finished && sm.started && cr.inZone(sm.by)) {
            sm.targetX = slideRefBx + (px - slideRefX) * 1.25f
        } else {
            slideRefX = px
            slideRefBx = sm.bx
        }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    override fun onSensorChanged(ev: android.hardware.SensorEvent) {
        if (ev.sensor.type != android.hardware.Sensor.TYPE_ACCELEROMETER || ev.values.size < 2) return
        val gx = (-ev.values[0] / 9.81f).coerceIn(-1.2f, 1.2f)
        val gy = (ev.values[1] / 9.81f).coerceIn(-1.2f, 1.2f)
        tiltX += (gx - tiltX) * 0.2f
        tiltY += (gy - tiltY) * 0.2f
    }

    private fun startSensor() {
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val s = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(this, s, android.hardware.SensorManager.SENSOR_DELAY_GAME)
            sensorOn = true
        } catch (e: Exception) {
            sensorOn = false
        }
    }

    private fun stopSensor() {
        try {
            (context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager).unregisterListener(this)
        } catch (e: Exception) {
        }
        sensorOn = false
    }

    /** Inercia y rebote del menú de apariencias y asentamiento de las páginas de niveles. */
    private fun updateScroll(dt: Float) {
        if (!levelDrag && levelOff != 0f) {
            levelOff *= exp(-12f * dt)
            if (abs(levelOff) < 0.5f) levelOff = 0f
        }
        val touchingSkins = touching && scr == Scr.SKINS && dragged
        if (!touchingSkins) {
            if (skinScroll < 0f || skinScroll > skinMax) {
                skinVel = 0f
                val bound = if (skinScroll < 0f) 0f else skinMax
                skinScroll += (bound - skinScroll) * (1f - exp(-14f * dt))
                if (abs(skinScroll - bound) < 0.5f) skinScroll = bound
            } else if (skinVel != 0f) {
                skinScroll += skinVel * dt
                skinVel *= exp(-3.4f * dt)
                if (abs(skinVel) < 12f) skinVel = 0f
            }
        }
    }

    private fun updateCats(dt: Float) {
        val on = prefs.theme == Themes.CATS
        if (on && !sensorOn) startSensor() else if (!on && sensorOn) stopSensor()
        if (!on) return
        val n = min(bubbles.size, 32)
        for (i in 0 until n) {
            val m = 0.7f + (i % 5) * 0.12f
            val tx = tiltX * 230f * m
            val ty = (tiltY - 0.7f) * 150f * m
            var ax = (tx - catOx[i]) * 22f - catVx[i] * 4.5f
            var ay = (ty - catOy[i]) * 22f - catVy[i] * 4.5f
            if (touching) {
                val dx = catPx[i] - touchX
                val dy = catPy[i] - touchY
                val d = sqrt(dx * dx + dy * dy)
                if (d in 1f..320f) {
                    val f = (1f - d / 320f) * 9000f
                    ax += dx / d * f
                    ay += dy / d * f
                }
            }
            catVx[i] += ax * dt
            catVy[i] += ay * dt
            catOx[i] = (catOx[i] + catVx[i] * dt).coerceIn(-320f, 320f)
            catOy[i] = (catOy[i] + catVy[i] * dt).coerceIn(-320f, 320f)
        }
    }

    private fun setSlider(x: Float) {
        val v = ((x - SL_X0) / (SL_X1 - SL_X0)).coerceIn(0f, 1f)
        when (dragSlider) {
            "music" -> {
                prefs.musicVol = v
                audio.refresh()
            }
            "sfx" -> prefs.sfxVol = v
        }
    }

    private fun gameTap() {
        val sm = sim ?: return
        if (scr != Scr.PLAY || sm.dead || sm.finished) return
        if (mode == Mode.MP) {
            val m = match ?: return
            if (m.phase != Match.Phase.RUN || m.myDone || mpFrozen || ov == Ov.PAUSE) return
        } else if (ov != Ov.NONE) {
            return
        }
        sm.tap()
        audio.play(Sfx.TAP)
    }

    // ======================================================================
    // Lógica
    // ======================================================================

    private fun update(dt: Float) {
        time += dt
        updateCats(dt)
        updateScroll(dt)
        pollNet()
        val m = match
        if (m != null) {
            m.tick()
            if (m.phase != lastPhase) onPhaseChange(m)
            if (m.phase == Match.Phase.CLOSED) {
                if (scr == Scr.MP_HOST) {
                    openHost() // el invitado se fue antes de empezar: la sala vuelve a quedar abierta
                    netStatus = "El jugador se fue. La sala sigue abierta."
                } else if (scr == Scr.MP_JOIN) {
                    openJoin()
                    netStatus = "El anfitrión se fue."
                }
            }
        }
        updateParts(dt)
        if (shake > 0f) shake = max(0f, shake - dt * 70f)
        if (scr == Scr.MP_JOIN) {
            val now = System.currentTimeMillis()
            val it = rooms.values.iterator()
            while (it.hasNext()) if (now - it.next().seen > 4000L) it.remove()
        }
        if (scr == Scr.PLAY) updatePlay(dt)
    }

    private fun stepSim(sm: Sim, dt: Float) {
        acc += dt
        if (acc > 0.1f) acc = 0.1f
        var n = 0
        while (acc >= Cfg.STEP && n < 14) {
            sm.step()
            acc -= Cfg.STEP
            n++
        }
    }

    private fun updatePlay(dt: Float) {
        val sm = sim ?: return
        if (mode == Mode.MP) {
            updateMp(dt, sm)
            return
        }
        if (ov == Ov.PAUSE) return
        stepSim(sm, dt)
        afterStep(sm)
        if (sm.dead || sm.finished) {
            endTimer += dt
            if (ov == Ov.NONE && endTimer > (if (sm.dead) 0.8f else 1.0f)) {
                ov = if (sm.dead) Ov.DEAD else Ov.WIN
            }
        }
    }

    /** Detecta lo que pasó durante los pasos: estrellas, cambios de color, muerte, meta. */
    private fun afterStep(sm: Sim) {
        val cr = course ?: return
        val pal = Worlds.colors[world]
        if (sm.starCount > lastStarCount) {
            for (i in lastStarCount until min(sm.starCount, cr.obs.size)) sparkle(sm.bx, cr.obs[i].y, Color.WHITE, 14)
            lastStarCount = sm.starCount
            audio.play(Sfx.STAR)
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        if (sm.swCount > lastSwCount) {
            for (i in lastSwCount until min(sm.swCount, cr.sws.size)) {
                sparkle(Cfg.W / 2f, cr.sws[i].y, pal[cr.sws[i].toColor % 4], 18)
            }
            lastSwCount = sm.swCount
            audio.play(Sfx.SWITCH)
        }
        if (sm.started && !sm.dead && !sm.finished) trail.add(floatArrayOf(sm.bx, sm.by, 0f))
        if (sm.dead && !endHandled) {
            endHandled = true
            onDeath(sm)
        } else if (sm.finished && !endHandled) {
            endHandled = true
            onFinish(sm)
        }
    }

    private fun onDeath(sm: Sim) {
        val pal = Worlds.colors[world]
        audio.play(Sfx.DIE)
        haptic(HapticFeedbackConstants.LONG_PRESS)
        shake = 18f
        for (i in 0 until 34) {
            val a = rnd.nextFloat() * 6.2832f
            val sp = 250f + rnd.nextFloat() * 900f
            val life = 0.8f + rnd.nextFloat() * 0.7f
            val col = if (i % 2 == 0) pal[sm.color % 4] else pal[rnd.nextInt(4)]
            parts.add(Part(sm.bx, sm.by, cos(a) * sp, sin(a) * sp - 300f, col, 8f + rnd.nextFloat() * 16f, life, life))
        }
        when (mode) {
            Mode.LEVEL -> deaths++
            Mode.ENDLESS -> {
                if (sm.starCount > prefs.endlessBest) {
                    prefs.endlessBest = sm.starCount
                    newBest = true
                }
            }
            Mode.MP -> {}
        }
    }

    private fun onFinish(sm: Sim) {
        earnedStars = if (deaths == 0) 3 else if (deaths <= 2) 2 else 1
        if (!edTesting) prefs.setStars(level, earnedStars)
        audio.play(Sfx.WIN)
        haptic(HapticFeedbackConstants.LONG_PRESS)
        val pal = Worlds.colors[world]
        for (i in 0 until 60) {
            val a = -1.57f + (rnd.nextFloat() - 0.5f) * 2.6f
            val sp = 500f + rnd.nextFloat() * 1100f
            val life = 1.0f + rnd.nextFloat() * 0.9f
            parts.add(Part(sm.bx, sm.by, cos(a) * sp, sin(a) * sp, pal[rnd.nextInt(4)], 8f + rnd.nextFloat() * 12f, life, life))
        }
    }

    private fun sparkle(x: Float, y: Float, color: Int, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f
            val sp = 160f + rnd.nextFloat() * 520f
            val life = 0.45f + rnd.nextFloat() * 0.4f
            parts.add(Part(x, y, cos(a) * sp, sin(a) * sp, color, 5f + rnd.nextFloat() * 9f, life, life))
        }
    }

    private fun updateParts(dt: Float) {
        var i = parts.size - 1
        while (i >= 0) {
            val p = parts[i]
            p.life -= dt
            if (p.life <= 0f) {
                parts.removeAt(i)
            } else {
                p.vy += 2200f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
            i--
        }
        var k = trail.size - 1
        while (k >= 0) {
            trail[k][2] += dt
            if (trail[k][2] > Trails.MAX_LIFE) trail.removeAt(k)
            k--
        }
    }

    // ======================================================================
    // Multijugador
    // ======================================================================

    private fun updateMp(dt: Float, sm: Sim) {
        val m = match ?: return
        if (goFlash > 0f) goFlash -= dt
        when (m.phase) {
            Match.Phase.COUNTDOWN -> {
                cdTime -= dt
                val shown = ceil(cdTime).toInt()
                if (shown != lastShown && shown in 1..3) {
                    lastShown = shown
                    audio.play(Sfx.COUNT)
                }
                stepSim(sm, dt)
                if (cdTime <= 0f) {
                    m.beginRun()
                    goFlash = 0.8f
                    runTime = 0f
                    audio.play(Sfx.GO)
                }
            }
            Match.Phase.RUN -> {
                if (!m.myDone && !mpFrozen) {
                    runTime += dt
                    stepSim(sm, dt)
                    afterStep(sm)
                    m.localLive(sm.starCount)
                    if (sm.dead) {
                        m.localFinish(sm.starCount)
                    } else if (runTime >= Cfg.ROUND_TIME) {
                        mpFrozen = true
                        m.localFinish(sm.starCount)
                    }
                } else if (!mpFrozen) {
                    stepSim(sm, dt)
                    afterStep(sm)
                }
            }
            Match.Phase.ROUND_END -> {
                rEndTimer = max(0f, rEndTimer - dt)
                if (!mpFrozen) stepSim(sm, dt)
            }
            Match.Phase.MATCH_END -> {
                if (!mpFrozen) stepSim(sm, dt)
            }
            else -> {}
        }
    }

    private fun onPhaseChange(m: Match) {
        lastPhase = m.phase
        when (m.phase) {
            Match.Phase.COUNTDOWN -> {
                beginPlay(Course.endless(m.seed, 0), Mode.MP)
                cdTime = 3.0f
                lastShown = 4
                runTime = 0f
                mpFrozen = false
                goFlash = 0f
            }
            Match.Phase.ROUND_END -> {
                rEndTimer = Match.NEXT_ROUND_DELAY_MS / 1000f
                val mine = m.myRounds.lastOrNull() ?: 0
                val theirs = m.oppRounds.lastOrNull() ?: 0
                audio.play(if (mine >= theirs) Sfx.WIN else Sfx.BACK)
            }
            Match.Phase.MATCH_END -> audio.play(if (m.result() >= 0) Sfx.WIN else Sfx.BACK)
            else -> {}
        }
    }

    private fun lockMulticast(on: Boolean) {
        try {
            if (on) {
                if (mcLock == null) {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    mcLock = wm?.createMulticastLock("spectrum")
                    mcLock?.setReferenceCounted(false)
                }
                mcLock?.acquire()
            } else {
                mcLock?.let { if (it.isHeld) it.release() }
            }
        } catch (e: Exception) {
            // sin permiso o sin Wi-Fi: la IP manual sigue funcionando
        }
    }

    private fun openHost() {
        closeNet()
        val h = NetHost(prefs.name, events)
        if (!h.start()) {
            netStatus = "No se pudo crear la sala. Cierra y abre el juego."
            return
        }
        host = h
        myIps = NetUtil.localIps()
        val m = Match(true, prefs.name, { line -> host?.conn?.send(line) })
        m.rounds = prefs.mpRounds
        match = m
        lastPhase = m.phase
        netStatus = ""
        scr = Scr.MP_HOST
    }

    private fun openJoin() {
        closeNet()
        val m = Match(false, prefs.name, { line -> guest?.conn?.send(line) })
        match = m
        lastPhase = m.phase
        rooms.clear()
        connecting = false
        netStatus = ""
        lockMulticast(true)
        val s = NetScanner(events)
        if (!s.start()) netStatus = "No se pudo buscar salas. Usa \"Escribir IP\"."
        scanner = s
        scr = Scr.MP_JOIN
    }

    private fun joinTyped(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val parts2 = t.split(":")
        val ip = parts2[0].trim()
        val port = if (parts2.size > 1) parts2[1].trim().toIntOrNull() ?: NetCfg.TCP_PORT else NetCfg.TCP_PORT
        joinRoom(ip, port)
    }

    private fun joinRoom(ip: String, port: Int) {
        if (connecting) return
        scanner?.stop()
        scanner = null
        val g = NetGuest(events)
        guest?.stop()
        guest = g
        connecting = true
        netStatus = "Conectando…"
        g.connect(ip, port)
    }

    private fun pollNet() {
        var line = events.poll()
        var guard = 0
        while (line != null && guard < 400) {
            guard++
            handleNet(line)
            line = events.poll()
        }
    }

    private fun handleNet(line: String) {
        if (!line.startsWith("!")) {
            match?.onMessage(line)
            return
        }
        val p = line.split("|")
        when (p[0]) {
            "!CONNECTED" -> {
                connecting = false
                netStatus = ""
                match?.onConnected()
                audio.play(Sfx.SWITCH)
            }
            "!CLOSED" -> {
                match?.onClosed()
                connecting = false
            }
            "!ERROR" -> {
                connecting = false
                netStatus = if (p.getOrNull(1) == "connect") {
                    "No se pudo conectar. ¿Están en el mismo Wi-Fi?"
                } else {
                    "Error de red"
                }
                if (scr == Scr.MP_JOIN && scanner == null) {
                    val s = NetScanner(events)
                    if (s.start()) scanner = s
                }
            }
            "!ROOM" -> {
                val port = p.getOrNull(3)?.toIntOrNull() ?: NetCfg.TCP_PORT
                val ip = p.getOrNull(2) ?: return
                val key = "$ip:$port"
                val r = rooms[key]
                if (r != null) r.seen = System.currentTimeMillis()
                else rooms[key] = Room(p.getOrNull(1) ?: "Sala", ip, port, System.currentTimeMillis())
            }
        }
    }

    /** Sale de la partida / sala: avisa al rival y cierra la red un instante después. */
    private fun leaveMp() {
        match?.leave()
        val h = host
        val g = guest
        val s = scanner
        host = null
        guest = null
        scanner = null
        match = null
        connecting = false
        rooms.clear()
        netStatus = ""
        lockMulticast(false)
        s?.stop()
        handler.postDelayed({
            h?.stop()
            g?.stop()
        }, 250L)
        events = ConcurrentLinkedQueue()
        lastPhase = Match.Phase.LOBBY
    }

    private fun closeNet() {
        host?.stop()
        guest?.stop()
        scanner?.stop()
        host = null
        guest = null
        scanner = null
        match = null
        connecting = false
        rooms.clear()
        lockMulticast(false)
        events = ConcurrentLinkedQueue()
        lastPhase = Match.Phase.LOBBY
    }

    // ======================================================================
    // Dibujo: base
    // ======================================================================

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        hits.clear()
        canvas.save()
        canvas.scale(sc, sc)
        try {
            drawAll(canvas)
        } catch (e: Exception) {
            Log.e("SpectrumColor", "draw", e)
        }
        canvas.restore()
    }

    private fun curWorld(): Int = when (scr) {
        Scr.PLAY -> world
        Scr.LEVELS -> levelPage
        else -> 0
    }

    private fun cur(): IntArray {
        val th = prefs.theme
        if (th != 0 && scr != Scr.PLAY && scr != Scr.LEVELS) return Themes.glow[th]
        return Worlds.colors[curWorld().coerceIn(0, Worlds.colors.size - 1)]
    }

    private fun easeOut(a: Float): Float {
        val u = 1f - a.coerceIn(0f, 1f)
        return 1f - u * u * u
    }

    /** Resorte suave (como las ventanas de iOS): pasa un poco de 1 y se asienta. */
    private fun spring(a: Float): Float {
        val x = a.coerceIn(0f, 1f)
        val k = 1.9f
        val u = x - 1f
        return 1f + (k + 1f) * u * u * u + k * u * u
    }

    private fun drawAll(c: Canvas) {
        uiIdx = 0
        val a = ((time - scrT0) / 0.40f).coerceIn(0f, 1f)
        val e = easeOut(a)
        if (scr == Scr.PLAY) {
            if (a < 1f) {
                c.saveLayerAlpha(0f, 0f, Cfg.W, vh, (255 * e).toInt())
                val z = 1.05f - 0.05f * e
                c.scale(z, z, Cfg.W / 2f, vh / 2f)
            }
            drawPlayWorld(c)
            drawPlayUi(c)
            if (a < 1f) c.restore()
            return
        }
        drawBackground(c, 0f)
        if (a < 1f) {
            c.saveLayerAlpha(0f, 0f, Cfg.W, vh, (255 * e).toInt())
            val z = 0.94f + 0.06f * e
            c.translate(scrDir * (1f - e) * 110f, 0f)
            c.scale(z, z, Cfg.W / 2f, vh / 2f)
        }
        when (scr) {
            Scr.HOME -> drawHome(c)
            Scr.LEVELS -> drawLevels(c)
            Scr.SETTINGS -> drawSettings(c)
            Scr.SKINS -> drawSkins(c)
            Scr.THEMES -> drawThemes(c)
            Scr.MP_MENU -> drawMpMenu(c)
            Scr.MP_HOST -> drawMpHost(c)
            Scr.MP_JOIN -> drawMpJoin(c)
            Scr.EDITOR -> drawEditor(c)
            else -> {}
        }
        if (a < 1f) c.restore()
    }

    private fun drawBackground(c: Canvas, camY: Float) {
        val w = curWorld().coerceIn(0, Worlds.bg.size - 1)
        val th = prefs.theme
        val key = w + th * 100
        if (bgShader == null || bgShaderWorld != key) {
            val cols = if (th == 0) Worlds.bg[w] else Themes.bg[th]
            bgShader = LinearGradient(0f, 0f, 0f, vh, cols, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            bgShaderWorld = key
        }
        val pp = Gl.p()
        pp.shader = bgShader
        c.drawRect(0f, 0f, Cfg.W, vh, pp)
        val pal = if (th == 0) Worlds.colors[w] else Themes.glow[th]
        for (i in 0 until 4) {
            val rad = 520f + i * 60f
            val span = vh + 2f * rad
            val depth = 0.05f + i * 0.03f
            val x = Cfg.W * (0.15f + 0.25f * i) + sin(time * 0.25f + i * 1.7f) * 90f
            val y = (((0.2f + 0.27f * i) * vh - camY * depth) % span + span) % span - rad
            Gl.glow(c, x, y, rad, pal[i], 70)
        }
        val hw = th == Themes.HALLOWEEN
        val cats = th == Themes.CATS
        for ((bi, b) in bubbles.withIndex()) {
            val s = b.size
            val span = vh + 2f * s
            val x = b.nx * Cfg.W + sin(time * 0.6f + b.sway) * 14f
            val y = ((b.ny * vh - camY * b.depth - time * 8f * (b.depth * 4f)) % span + span) % span - s
            if (cats && bi < 32) {
                val kx = x + catOx[bi]
                val ky = y + catOy[bi]
                catPx[bi] = kx
                catPy[bi] = ky
                val kr = s * 0.9f + 8f
                val ex = kr * 1.7f
                if (scr == Scr.PLAY) c.saveLayerAlpha(kx - ex, ky - ex, kx + ex, ky + ex, 120)
                Cats.kitten(c, kx, ky, kr, bi % 4, time, tiltX.coerceIn(-1f, 1f), (tiltY - 0.7f).coerceIn(-1f, 1f))
                if (scr == Scr.PLAY) c.restore()
                continue
            }
            if (hw) {
                if (bi % 2 == 0) Spooky.pumpkin(c, x, y, s * 0.95f + 8f, s > 26f, time)
                else {
                    val q0 = Gl.p()
                    q0.color = Gl.white(14)
                    c.drawCircle(x, y, s * 1.05f + 6f, q0)
                    Spooky.web(c, x, y, s * 1.05f + 6f, 120, 0f, 360f, 3)
                }
                continue
            }
            var q = Gl.p()
            q.shader = android.graphics.RadialGradient(
                x - s * 0.25f, y - s * 0.3f, s * 1.1f,
                intArrayOf(Gl.white(6), Gl.white(10), Gl.white(52)), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(x, y, s, q)
            q = Gl.p()
            q.style = Paint.Style.STROKE
            q.strokeWidth = 1.6f
            q.color = Gl.white(48)
            c.drawCircle(x, y, s, q)
            Gl.arc(c, x, y, s * 0.72f, 205f, 50f, 2.4f, Gl.white(90))
        }
    }

    // ---- elementos de interfaz ----

    private fun button(
        c: Canvas, id: String, label: String, cx: Float, cy: Float, w: Float, h: Float,
        accent: Int = 0, size: Float = 50f, enabled: Boolean = true
    ) {
        val pressed = enabled && pressedId == id
        val s = if (pressed) 0.96f else 1f
        val hw = w / 2f * s
        val hh = h / 2f * s
        val pa = ((time - scrT0 - uiIdx * 0.04f) / 0.42f).coerceIn(0f, 1f)
        uiIdx++
        val popping = pa < 1f
        if (popping) {
            // el área de la capa debe cubrir el resplandor y la sombra del botón, si no se ve un marco rectangular
            val ext = max(w, h) * 0.9f
            c.saveLayerAlpha(cx - ext, cy - ext, cx + ext, cy + ext, (255 * easeOut(pa * 1.4f)).toInt())
            val z = 0.80f + 0.20f * spring(pa)
            c.scale(z, z, cx, cy)
        }
        if (accent != 0 && enabled) Gl.glow(c, cx, cy, w * 0.62f, accent, if (pressed) 70 else 45)
        Gl.panel(
            c, cx - hw, cy - hh, cx + hw, cy + hh, hh,
            if (enabled) (if (pressed) 1.25f else 1f) else 0.45f,
            if (accent != 0) accent else cur()[0]
        )
        Gl.textFit(c, label, cx, cy + size * 0.35f, size, w - 60f, Gl.medium, Gl.white(if (enabled) 245 else 110))
        if (prefs.theme == Themes.HALLOWEEN && w >= 330f && h >= 100f) {
            Spooky.web(c, cx - hw + hh * 0.45f, cy - hh + 2f, hh * 0.75f, 150, 0f, 90f, 2)
            Spooky.pumpkin(c, cx + hw - hh * 0.8f, cy - hh + 4f, hh * 0.36f, false, time)
        }
        if (popping) c.restore()
        if (enabled) hits.add(Hit(id, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f))
    }

    private fun iconButton(c: Canvas, id: String, cx: Float, cy: Float, r: Float, icon: (Canvas, Float, Float, Float) -> Unit) {
        val pressed = pressedId == id
        val rr = if (pressed) r * 0.94f else r
        Gl.panel(c, cx - rr, cy - rr, cx + rr, cy + rr, rr, if (pressed) 1.25f else 1f, cur()[0])
        icon(c, cx, cy, rr)
        hits.add(Hit(id, cx - r - 12f, cy - r - 12f, cx + r + 12f, cy + r + 12f))
    }

    private fun iconPause(c: Canvas, x: Float, y: Float, r: Float) {
        val k = r / 54f
        val pp = Gl.p()
        pp.color = Gl.white(235)
        Gl.rect.set(x - 17f * k, y - 20f * k, x - 5f * k, y + 20f * k)
        c.drawRoundRect(Gl.rect, 5f * k, 5f * k, pp)
        Gl.rect.set(x + 5f * k, y - 20f * k, x + 17f * k, y + 20f * k)
        c.drawRoundRect(Gl.rect, 5f * k, 5f * k, pp)
    }

    private fun iconGear(c: Canvas, x: Float, y: Float, r: Float) {
        val k = r / 54f
        for (i in 0 until 8) {
            val a = i * 0.7853982f
            Gl.line(c, x + cos(a) * 22f * k, y + sin(a) * 22f * k, x + cos(a) * 31f * k, y + sin(a) * 31f * k, 10f * k, Gl.white(235))
        }
        Gl.arc(c, x, y, 18f * k, 0f, 360f, 9f * k, Gl.white(240))
    }

    private fun iconChevron(c: Canvas, x: Float, y: Float, r: Float, dir: Int) {
        val k = r / 54f
        val a = 10f * k * dir
        Gl.line(c, x - a, y - 22f * k, x + a, y, 8f * k, Gl.white(240))
        Gl.line(c, x + a, y, x - a, y + 22f * k, 8f * k, Gl.white(240))
    }

    private fun drawLock(c: Canvas, x: Float, y: Float, s: Float, a: Int) {
        Gl.arc(c, x, y - 6f * s, 15f * s, 180f, 180f, 6f * s, Gl.white(a))
        val pp = Gl.p()
        pp.color = Gl.white(a)
        Gl.rect.set(x - 22f * s, y - 6f * s, x + 22f * s, y + 24f * s)
        c.drawRoundRect(Gl.rect, 6f * s, 6f * s, pp)
    }

    private fun starShape(c: Canvas, x: Float, y: Float, outer: Float, inner: Float, rot: Float, fill: Int, stroke: Int) {
        val path = Gl.path
        path.reset()
        for (k in 0 until 10) {
            val ang = -3.14159f / 2f + k * 3.14159f / 5f + rot
            val rr = if (k % 2 == 0) outer else inner
            val px = x + cos(ang) * rr
            val py = y + sin(ang) * rr
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        var pp = Gl.p()
        pp.color = fill
        c.drawPath(path, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2f
        pp.strokeJoin = Paint.Join.ROUND
        pp.color = stroke
        c.drawPath(path, pp)
    }

    private fun toggle(c: Canvas, id: String, cx: Float, cy: Float, on: Boolean) {
        val w = 150f
        val h = 76f
        val pal = cur()
        Gl.panel(c, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h / 2f, 1f, if (on) pal[0] else pal[1])
        if (on) {
            val pp = Gl.p()
            pp.color = Gl.alpha(pal[0], 110)
            Gl.rect.set(cx - w / 2f + 4f, cy - h / 2f + 4f, cx + w / 2f - 4f, cy + h / 2f - 4f)
            c.drawRoundRect(Gl.rect, h / 2f, h / 2f, pp)
        }
        val kx = cx + if (on) (w / 2f - h / 2f) else -(w / 2f - h / 2f)
        val pp = Gl.p()
        pp.color = Gl.white(if (on) 245 else 170)
        c.drawCircle(kx, cy, h / 2f - 9f, pp)
        hits.add(Hit(id, cx - w / 2f - 20f, cy - h / 2f - 20f, cx + w / 2f + 20f, cy + h / 2f + 20f))
    }

    private fun slider(c: Canvas, id: String, cy: Float, v: Float, enabled: Boolean) {
        val pal = cur()
        val a = if (enabled) 1f else 0.4f
        var pp = Gl.p()
        pp.color = Gl.white((55 * a).toInt())
        Gl.rect.set(SL_X0, cy - 8f, SL_X1, cy + 8f)
        c.drawRoundRect(Gl.rect, 8f, 8f, pp)
        val kx = SL_X0 + (SL_X1 - SL_X0) * v
        pp = Gl.p()
        pp.color = Gl.alpha(pal[0], (210 * a).toInt())
        Gl.rect.set(SL_X0, cy - 8f, kx, cy + 8f)
        c.drawRoundRect(Gl.rect, 8f, 8f, pp)
        Gl.glow(c, kx, cy, 60f, pal[0], (80 * a).toInt())
        pp = Gl.p()
        pp.shader = android.graphics.RadialGradient(
            kx - 8f, cy - 10f, 40f, Gl.white(255), Gl.white((150 * a).toInt()), Shader.TileMode.CLAMP
        )
        c.drawCircle(kx, cy, 28f, pp)
        hits.add(Hit("sl:$id", SL_X0 - 40f, cy - 55f, SL_X1 + 40f, cy + 55f))
    }

    private fun chip(c: Canvas, text: String, cx: Float, cy: Float, w: Float, h: Float, size: Float, color: Int = Gl.white(240)) {
        Gl.panel(c, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h / 2f, 0.9f, cur()[0])
        Gl.textFit(c, text, cx, cy + size * 0.35f, size, w - 40f, Gl.medium, color)
    }

    private fun dim(c: Canvas, a: Int) {
        val pp = Gl.p()
        pp.color = Color.argb(a, 2, 4, 14)
        c.drawRect(-Cfg.W, -vh, Cfg.W * 2f, vh * 2f, pp)
    }

    private fun title(c: Canvas, text: String, y: Float, size: Float = 60f) {
        val pal = cur()
        val shader = LinearGradient(300f, 0f, 780f, 0f, pal[0], pal[1], Shader.TileMode.CLAMP)
        Gl.text(c, text, Cfg.W / 2f, y, size, Gl.light, Color.WHITE, 0.12f, shader)
    }

    // ======================================================================
    // Pantallas de menú
    // ======================================================================

    private fun drawHome(c: Canvas) {
        val pal = cur()
        val ty = topI + 260f
        Gl.glow(c, 540f, ty + 20f, 470f, pal[1], 60)
        Gl.glow(c, 540f, ty + 60f, 390f, pal[0], 50)
        Gl.text(c, "SPECTRUM", 540f, ty, 112f, Gl.thin, Gl.white(245), 0.14f)
        val shader = LinearGradient(270f, 0f, 810f, 0f, intArrayOf(pal[0], pal[3], pal[1]), null, Shader.TileMode.CLAMP)
        Gl.text(c, "COLOR", 540f, ty + 130f, 124f, Gl.light, Color.WHITE, 0.3f, shader)

        if (prefs.theme == Themes.CATS) {
            // orejas sobre "SPECTRUM" y bigotes a los lados de "COLOR"
            val tw = Gl.textWidth("SPECTRUM", 112f, Gl.thin, 0.14f)
            val cw = Gl.textWidth("COLOR", 124f, Gl.light, 0.3f)
            val wig = sin(time * 1.5f) * 4f
            for (sgn in intArrayOf(-1, 1)) {
                val ex = 540f + sgn * (tw / 2f - 10f)
                Gl.glow(c, ex - sgn * 40f, ty - 120f, 110f, pal[0], 50)
                Cats.tri(c, ex - sgn * 18f, ty - 74f, ex - sgn * 130f, ty - 82f, ex + sgn * 12f, ty - 190f + wig * sgn, 0xFFFFA040.toInt())
                Cats.tri(c, ex - sgn * 38f, ty - 84f, ex - sgn * 100f, ty - 88f, ex - sgn * 4f, ty - 160f + wig * sgn, 0xFFFFB0BE.toInt())
            }
            for (sgn in intArrayOf(-1, 1)) {
                Cats.whiskers(c, 540f + sgn * (cw / 2f + 30f), ty + 92f, 190f, sgn, 5f, 210)
            }
            Cats.tri(c, 540f - 20f, ty + 188f, 540f + 20f, ty + 188f, 540f, ty + 214f, 0xFFFF7F9A.toInt())
        }
        if (prefs.theme == Themes.HALLOWEEN) {
            Spooky.web(c, 0f, 0f, 470f, 130, 0f, 90f, 4)
            Spooky.web(c, Cfg.W, 0f, 470f, 130, 90f, 90f, 4)
            Spooky.spider(c, 215f, 0f, topI + 330f + sin(time * 1.3f) * 40f, 30f, time)
            Spooky.spider(c, 900f, 0f, topI + 200f + sin(time * 1.1f + 1f) * 36f, 24f, time)
            val py = topI + 760f
            Gl.glow(c, 540f, py, 330f, pal[0], 60)
            Spooky.pumpkin(c, 330f, py + 70f, 80f, true, time)
            Spooky.pumpkin(c, 750f, py + 70f, 80f, true, time)
            Spooky.pumpkin(c, 540f, py, 125f, true, time)
            val bx = ((time * 90f) % (Cfg.W + 400f)) - 200f
            Spooky.bat(c, bx, topI + 560f + sin(time * 2f) * 30f, 46f, time)
            Spooky.bat(c, Cfg.W - ((time * 70f + 400f) % (Cfg.W + 400f)) + 200f, topI + 640f + sin(time * 1.7f) * 26f, 36f, time + 1f)
        }

        val bob = sin(time * 1.6f) * 6f
        Gl.glow(c, 950f, vh - 260f + bob, 120f, pal[1], 70)
        iconButton(c, "skins", 950f, vh - 260f + bob, 66f) { cc, x, y, r ->
            drawBallAt(cc, x, y, r * 0.58f, pal[(time * 0.8f).toInt() % 4], effSkin(), time, false)
        }
        Gl.glow(c, 130f, vh - 260f - bob, 120f, pal[0], 60)
        iconButton(c, "settings", 130f, vh - 260f - bob, 66f) { cc, x, y, r -> iconGear(cc, x, y, r) }

        val b0 = topI + 1040f
        button(c, "play", "Jugar", 540f, b0, 760f, 130f, pal[0], 56f)
        button(c, "endless", "Modo infinito", 540f, b0 + 175f, 760f, 130f, 0, 50f)
        button(c, "mp", "Multijugador local", 540f, b0 + 350f, 760f, 130f, pal[1], 50f)
        if (BuildConfig.DEV) button(c, "edit", "Editar", 540f, b0 + 515f, 440f, 100f, pal[2], 44f)
        val total = Levels.PLAYABLE * 3
        Gl.text(
            c, "Estrellas ${prefs.totalStars()}/$total   ·   Récord infinito ${prefs.endlessBest}",
            540f, vh - 130f, 38f, Gl.regular, Gl.white(170)
        )
    }

    private fun drawTile(c: Canvas, lvl: Int, cx: Float, cy: Float, w: Float, h: Float) {
        val pal = cur()
        val playable = Levels.isPlayable(lvl)
        val unlocked = playable && (lvl <= prefs.unlocked() || (BuildConfig.DEV && edMode))
        val st = prefs.stars(lvl)
        val id = "lvl:$lvl"
        val pressed = unlocked && pressedId == id
        val s = if (pressed) 0.96f else 1f
        val l = cx - w / 2f * s
        val r = cx + w / 2f * s
        val t = cy - h / 2f * s
        val b = cy + h / 2f * s
        if (unlocked && st == 0) Gl.glow(c, cx, cy, w * 0.7f, pal[lvl % 4], (40 + 25 * sin(time * 3f)).toInt())
        Gl.panel(c, l, t, r, b, 52f, if (unlocked) (if (pressed) 1.25f else 1f) else 0.38f, pal[lvl % 4])
        val nx = cx - w / 2f + 105f
        if (unlocked) {
            Gl.text(c, lvl.toString(), nx, cy + 30f, 86f, Gl.medium, Gl.white(245), 0f, null, Gl.alpha(pal[lvl % 4], 150))
            for (k in 0 until 3) {
                val sx = cx + 28f + k * 66f
                val on = k < st
                starShape(c, sx, cy + 2f, 26f, 11f, 0f, if (on) gold else Gl.white(28), if (on) Gl.white(210) else Gl.white(70))
            }
            if (BuildConfig.DEV && Custom.map.containsKey(lvl)) Gl.text(c, "EDIT", cx + w / 2f - 60f, cy - h / 2f + 36f, 24f, Gl.medium, Gl.white(200), 0.1f)
            hits.add(Hit(id, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f))
        } else {
            Gl.text(c, lvl.toString(), nx, cy + 26f, 74f, Gl.medium, Gl.white(95))
            drawLock(c, cx + 100f, cy - 2f, 1.5f, 130)
        }
    }

    private fun drawLevels(c: Canvas) {
        val pal = cur()
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        Gl.text(c, if (BuildConfig.DEV && edMode) "EDITAR" else "NIVELES", 540f, top + 128f, 54f, Gl.medium, Gl.white(240), 0.2f)
        starShape(c, 880f, top + 108f, 30f, 13f, 0f, gold, Gl.white(210))
        Gl.text(c, prefs.totalStars().toString(), 950f, top + 126f, 46f, Gl.medium, Gl.white(240))

        if (BuildConfig.DEV && edMode) Gl.text(c, "Toca un nivel para editarlo", 540f, top + 226f, 34f, Gl.regular, Gl.white(190))
        val wp = levelPage
        val ry = top + 300f
        if (wp > 0) iconButton(c, "wprev", 120f, ry, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        if (wp < Levels.WORLDS - 1) iconButton(c, "wnext", 960f, ry, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, 1) }

        // la página actual y sus vecinas siguen al dedo; al soltar se asientan con suavidad
        for (k in -1..1) {
            val wq = wp + k
            if (wq < 0 || wq >= Levels.WORLDS) continue
            val off = levelOff + k * Cfg.W
            if (abs(off) >= Cfg.W) continue
            val h1 = hits.size
            c.save()
            c.translate(off, 0f)
            drawWorldPage(c, wq, pal, ry)
            c.restore()
            if (k != 0 || abs(levelOff) > 4f) while (hits.size > h1) hits.removeAt(hits.size - 1)
        }
        val dots = Levels.WORLDS
        val pos = wp - levelOff / Cfg.W
        for (i in 0 until dots) {
            val x = 540f + (i - (dots - 1) / 2f) * 38f
            val near = (1f - abs(i - pos)).coerceIn(0f, 1f)
            val pp = Gl.p()
            pp.color = Gl.white((if (Levels.worldAvailable(i)) 110 else 50) + (130 * near).toInt())
            c.drawCircle(x, vh - 100f, 6f + 3f * near, pp)
        }
    }

    private fun drawWorldPage(c: Canvas, wp: Int, pal: IntArray, ry: Float) {
        val top = topI
        val avail = Levels.worldAvailable(wp)
        Gl.text(c, "MUNDO ${wp + 1}", 540f, ry - 34f, 34f, Gl.medium, Gl.white(150), 0.3f)
        val shader = LinearGradient(300f, 0f, 780f, 0f, pal[0], pal[1], Shader.TileMode.CLAMP)
        Gl.text(c, Worlds.names[wp], 540f, ry + 52f, 72f, Gl.light, Color.WHITE, 0.06f, shader)

        val gridTop = top + 440f
        val availH = vh - gridTop - 170f
        val rowH = min(300f, availH / 5f)
        val tileH = rowH - 36f
        for (i in 0 until Levels.PER_WORLD) {
            val lvl = Levels.worldFirst(wp) + i
            val cx = if (i % 2 == 0) 300f else 780f
            val cy = gridTop + rowH * (i / 2) + rowH / 2f
            drawTile(c, lvl, cx, cy, 440f, tileH)
        }
        if (!avail) {
            val dp = Gl.p()
            dp.color = Color.argb(110, 2, 4, 14)
            c.drawRect(0f, gridTop - 10f, Cfg.W, vh - 160f, dp)
            val cy = gridTop + rowH * 2.5f
            Gl.panel(c, 130f, cy - 230f, 950f, cy + 230f, 80f, 1f, pal[1])
            drawLock(c, 540f, cy - 110f, 2.2f, 220)
            Gl.text(c, "PRÓXIMAMENTE", 540f, cy + 50f, 70f, Gl.medium, Gl.white(245), 0.12f, null, Gl.alpha(pal[0], 150))
            Gl.text(c, "Coming soon", 540f, cy + 118f, 44f, Gl.light, Gl.white(190), 0.1f)
            Gl.text(c, "Más niveles en próximas actualizaciones", 540f, cy + 176f, 32f, Gl.regular, Gl.white(150))
        }
    }

    private fun settingRow(c: Canvas, label: String, y: Float) {
        Gl.text(c, label, 120f, y + 16f, 48f, Gl.regular, Gl.white(240), 0f, null, 0, Paint.Align.LEFT)
    }

    private fun drawSettings(c: Canvas) {
        val pal = cur()
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        title(c, "AJUSTES", top + 130f)
        val y0 = top + 330f
        val dev = BuildConfig.DEV
        // en pantallas más bajas las filas se juntan un poco para que todo quepa
        val rs = min(1f, (vh - 120f - y0) / (if (dev) 1350f else 1220f))
        Gl.panel(c, 60f, y0 - 90f, 1020f, y0 + (if (dev) 1350f else 1220f) * rs, 70f, 0.8f, pal[1])

        settingRow(c, "Música", y0)
        toggle(c, "tmusic", 870f, y0, prefs.musicOn)
        slider(c, "music", y0 + 95f * rs, prefs.musicVol, prefs.musicOn)

        settingRow(c, "Efectos", y0 + 200f * rs)
        toggle(c, "tsfx", 870f, y0 + 200f * rs, prefs.sfxOn)
        slider(c, "sfx", y0 + 295f * rs, prefs.sfxVol, prefs.sfxOn)

        settingRow(c, "Vibración", y0 + 420f * rs)
        toggle(c, "thaptic", 870f, y0 + 420f * rs, prefs.haptics)

        settingRow(c, "Dificultad", y0 + 545f * rs)
        button(c, "teasy", if (prefs.easy) "Fácil" else "Normal", 800f, y0 + 545f * rs, 300f, 100f, 0, 44f)

        settingRow(c, "Jugador", y0 + 665f * rs)
        button(c, "tname", prefs.name, 760f, y0 + 665f * rs, 380f, 100f, 0, 40f)

        settingRow(c, "Tema", y0 + 785f * rs)
        button(c, "ttheme", Themes.names[prefs.theme], 760f, y0 + 785f * rs, 380f, 100f, Themes.glow[prefs.theme][0], 40f)

        settingRow(c, "Idioma", y0 + 905f * rs)
        button(c, "tlang", I18n.langNames[prefs.lang], 760f, y0 + 905f * rs, 380f, 100f, 0, 40f)

        settingRow(c, "Progreso", y0 + 1025f * rs)
        val armed = time - resetArmedAt < 3f
        button(c, "treset", if (armed) "¿Seguro? Toca otra vez" else "Borrar progreso", 760f, y0 + 1025f * rs, 380f, 100f, if (armed) 0xFFFF4F6B.toInt() else 0, 36f)

        val ul = when (updState) {
            1 -> "Buscando…"
            2 -> "Estás al día"
            3 -> "Descargar nueva"
            4 -> "Sin conexión"
            else -> "Buscar"
        }
        settingRow(c, "Actualización", y0 + 1145f * rs)
        button(c, "update", ul, 760f, y0 + 1145f * rs, 380f, 100f, if (updState == 3) pal[2] else 0, 38f)

        if (dev) {
            settingRow(c, "Niveles", y0 + 1265f * rs)
            button(c, "topen", if (prefs.allOpen) "Todos abiertos" else "Abrir todos", 760f, y0 + 1265f * rs, 380f, 100f, 0, 40f)
        }

        Gl.text(c, "Spectrum Color  ·  v2", 540f, vh - 90f, 34f, Gl.regular, Gl.white(120))
    }

    private fun drawThemes(c: Canvas) {
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        title(c, "TEMAS", top + 130f)
        Gl.text(c, "Elige el color de fondo", 540f, top + 235f, 36f, Gl.regular, Gl.white(160))
        val gy = top + 290f
        val rows = (Themes.COUNT + 1) / 2
        // las tarjetas se achican para que quepan todas en pantalla
        val k = min(1f, (vh - gy - 50f) / (rows * 690f - 40f))
        for (t in 0 until Themes.COUNT) {
            val rcx = 540f + (if (t % 2 == 0) -250f else 250f) * k
            val rcy = gy + 320f * k + (t / 2) * 690f * k
            val id = "theme:$t"
            val sel = prefs.theme == t
            val pressed = pressedId == id
            val sc2 = if (pressed) 0.97f else 1f
            val acc = Themes.glow[t][0]
            c.save()
            c.translate(rcx, rcy)
            c.scale(k, k)
            val cx = 0f
            val cy = 0f
            val hw = 230f * sc2
            val hh = 320f * sc2
            if (sel) Gl.glow(c, cx, cy, 340f, acc, 70)
            Gl.panel(c, cx - hw, cy - hh, cx + hw, cy + hh, 60f, if (sel) 1.3f else 0.9f, acc)
            if (sel) {
                val pb = Gl.p()
                pb.style = Paint.Style.STROKE
                pb.strokeWidth = 5f
                pb.color = Gl.white(235)
                Gl.rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
                c.drawRoundRect(Gl.rect, 60f, 60f, pb)
            }
            // vista previa pequeña: una "pantalla" con el degradado, resplandores, bola y botones
            val pl = cx - 170f * sc2
            val pr = cx + 170f * sc2
            val pt = cy - 270f * sc2
            val pb2 = cy + 160f * sc2
            val cols = if (t == 0) Worlds.bg[0] else Themes.bg[t]
            val pp = Gl.p()
            pp.shader = LinearGradient(0f, pt, 0f, pb2, cols, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            Gl.rect.set(pl, pt, pr, pb2)
            c.drawRoundRect(Gl.rect, 36f, 36f, pp)
            c.save()
            Gl.rect.set(pl, pt, pr, pb2)
            val clip = android.graphics.Path()
            clip.addRoundRect(Gl.rect, 36f, 36f, android.graphics.Path.Direction.CW)
            c.clipPath(clip)
            val g = Themes.glow[t]
            Gl.glow(c, pl + 60f, pt + 90f, 150f, g[1], 70)
            Gl.glow(c, pr - 40f, pb2 - 70f, 170f, g[0], 70)
            Gl.text(c, "SPECTRUM", cx, pt + 70f, 34f, Gl.thin, Gl.white(235), 0.12f)
            Gl.text(c, "COLOR", cx, pt + 112f, 38f, Gl.light, g[0], 0.25f)
            if (t == Themes.HALLOWEEN) {
                Spooky.web(c, pl, pt, 130f, 140, 0f, 90f, 3)
                Spooky.web(c, pr, pt, 130f, 140, 90f, 90f, 3)
                Spooky.pumpkin(c, cx, pt + 205f + sin(time * 2f) * 4f, 40f, true, time)
            } else if (t == Themes.CATS) {
                Cats.kitten(c, cx, pt + 215f + sin(time * 2f) * 4f, 46f, ((time / 2f).toInt()) % 4, time, 0f, 0f)
            } else {
                drawBallAt(c, cx, pt + 205f + sin(time * 2f + t) * 5f, 34f, g[(time * 0.8f).toInt() % 4], effSkin(), time, true)
            }
            for (j in 0 until 3) {
                val by = pt + 290f + j * 46f
                Gl.panel(c, cx - 100f, by - 17f, cx + 100f, by + 17f, 17f, 0.9f, if (j == 0) g[0] else g[1])
            }
            c.restore()
            Gl.text(c, Themes.names[t], cx, cy + 232f, 44f, Gl.medium, Gl.white(if (sel) 255 else 225), 0.05f)
            c.restore()
            hits.add(Hit(id, rcx - 230f * k, rcy - 320f * k, rcx + 230f * k, rcy + 320f * k))
        }
    }

    private fun drawMpMenu(c: Canvas) {
        val pal = cur()
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        title(c, "MULTIJUGADOR", top + 130f, 56f)
        Gl.text(c, "Dos celulares en el mismo Wi-Fi", 540f, top + 300f, 44f, Gl.light, Gl.white(220))
        Gl.text(c, "Mismo recorrido para los dos:", 540f, top + 365f, 38f, Gl.regular, Gl.white(170))
        Gl.text(c, "gana quien sume más puntos", 540f, top + 420f, 38f, Gl.regular, Gl.white(170))

        val y0 = top + 640f
        Gl.text(c, "Tu nombre", 540f, y0 - 60f, 34f, Gl.regular, Gl.white(150), 0.1f)
        button(c, "mp_name", prefs.name, 540f, y0 + 30f, 620f, 120f, 0, 50f)
        button(c, "mp_host", "Crear sala", 540f, y0 + 230f, 760f, 140f, pal[0], 56f)
        button(c, "mp_join", "Unirse a una sala", 540f, y0 + 410f, 760f, 140f, pal[1], 56f)
        if (netStatus.isNotEmpty()) Gl.textFit(c, netStatus, 540f, y0 + 560f, 38f, 900f, Gl.regular, Gl.white(210))
        Gl.text(c, "Tip: uno puede crear un punto de acceso", 540f, vh - 175f, 32f, Gl.regular, Gl.white(120))
        Gl.text(c, "Wi-Fi con el celular y el otro conectarse", 540f, vh - 128f, 32f, Gl.regular, Gl.white(120))
    }

    private fun dots(): String {
        val n = ((time * 2f).toInt() % 3) + 1
        return ".".repeat(n)
    }

    private fun drawMpHost(c: Canvas) {
        val pal = cur()
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        title(c, "TU SALA", top + 130f)
        val m = match
        val status = when {
            m == null -> ""
            m.badVersion -> "Versiones distintas"
            m.phase == Match.Phase.CLOSED -> "El jugador se fue"
            m.connected -> "¡${m.oppName} se unió!"
            else -> "Esperando jugador" + dots()
        }
        val cy = top + 480f
        Gl.panel(c, 110f, cy - 150f, 970f, cy + 150f, 70f, 1f, pal[0])
        Gl.textFit(c, status, 540f, cy + 14f, 60f, 800f, Gl.medium, Gl.white(245))
        if (m != null && m.badVersion) {
            Gl.text(c, "Actualiza la app en los dos celulares", 540f, cy + 90f, 34f, Gl.regular, Gl.white(170))
        }
        if (m != null && !m.connected && m.phase != Match.Phase.CLOSED) {
            Gl.text(c, "Dile a tu amigo que toque \"Unirse\"", 540f, cy + 90f, 34f, Gl.regular, Gl.white(170))
        }
        if (myIps.isNotEmpty()) {
            Gl.text(c, "Tu IP: " + myIps.joinToString("  /  "), 540f, cy + 270f, 38f, Gl.regular, Gl.white(190))
            Gl.text(c, "(por si hay que escribirla a mano)", 540f, cy + 322f, 30f, Gl.regular, Gl.white(120))
        } else {
            Gl.text(c, "Conéctate a un Wi-Fi para jugar", 540f, cy + 270f, 38f, Gl.regular, Gl.white(190))
        }
        val by = cy + 520f
        if (m != null) {
            button(c, "mp_rounds", "Rondas: ${m.rounds}", 540f, by, 560f, 120f, 0, 48f)
            button(c, "mp_start", "¡Empezar!", 540f, by + 180f, 760f, 140f, pal[0], 58f, m.connected && m.phase == Match.Phase.LOBBY)
        }
        if (netStatus.isNotEmpty()) Gl.textFit(c, netStatus, 540f, by + 360f, 36f, 900f, Gl.regular, Gl.white(210))
    }

    private fun drawMpJoin(c: Canvas) {
        val pal = cur()
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        title(c, "UNIRSE", top + 130f)
        val m = match
        if (m != null && m.connected) {
            val cy = top + 560f
            Gl.panel(c, 110f, cy - 170f, 970f, cy + 170f, 70f, 1f, pal[0])
            Gl.textFit(c, "Conectado con ${m.oppName}", 540f, cy - 10f, 56f, 800f, Gl.medium, Gl.white(245))
            Gl.text(c, "Esperando que empiece" + dots(), 540f, cy + 80f, 40f, Gl.regular, Gl.white(190))
            return
        }
        if (m != null && m.phase == Match.Phase.CLOSED) {
            if (m.badVersion) {
                Gl.text(c, "Versiones distintas del juego", 540f, top + 540f, 48f, Gl.medium, Gl.white(220))
                Gl.text(c, "Actualiza Spectrum Color en los dos celulares", 540f, top + 610f, 32f, Gl.regular, Gl.white(170))
            } else {
                Gl.text(c, "El anfitrión se fue", 540f, top + 560f, 52f, Gl.medium, Gl.white(220))
            }
            return
        }
        val status = if (connecting) netStatus else if (netStatus.isNotEmpty()) netStatus else "Buscando salas en tu Wi-Fi" + dots()
        Gl.textFit(c, status, 540f, top + 330f, 42f, 900f, Gl.regular, Gl.white(200))
        val list = rooms.values.toList()
        var y = top + 470f
        for (i in 0 until min(4, list.size)) {
            val r = list[i]
            button(c, "room:${r.ip}:${r.port}", "${r.name}  ·  ${r.ip}", 540f, y, 860f, 130f, pal[0], 42f, !connecting)
            y += 160f
        }
        if (list.isEmpty()) {
            Gl.text(c, "Aún no aparece ninguna sala", 540f, top + 520f, 36f, Gl.regular, Gl.white(130))
        }
        button(c, "mp_ip", "Escribir IP", 540f, vh - 300f, 560f, 120f, 0, 46f, !connecting)
    }

    // ======================================================================
    // Dibujo de la partida
    // ======================================================================

    private fun drawPlayWorld(c: Canvas) {
        val sm = sim ?: return
        val cr = course ?: return
        val camY = sm.camY
        drawBackground(c, camY)
        val pal = Worlds.colors[world]
        c.save()
        val sx = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake else 0f
        val sy = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake else 0f
        c.translate(sx, -camY + sy)

        if (cr.isLevel && !(ov == Ov.WIN && sm.finished)) drawFinish(c, cr.finishY, pal)
        for (i in sm.swCount until cr.sws.size) {
            val s = cr.sws[i]
            if (s.y < camY - 100f) break
            if (s.y > camY + vh + 100f) continue
            drawSwitcher(c, s, cr, pal)
        }
        for (z in cr.zones) {
            if (z.bottom < camY - 40f || z.top > camY + vh + 40f) continue
            drawZone(c, z, pal)
        }
        for (o in cr.obs) {
            if (o.y + o.reach < camY - 40f || o.y - o.reach > camY + vh + 40f) continue
            drawObstacle(c, o, sm.t, pal)
        }
        for (i in sm.starCount until min(cr.obs.size, sm.starCount + 6)) {
            val o = cr.obs[i]
            if (T.isSlide(o.type)) continue
            if (o.y < camY - 60f || o.y > camY + vh + 60f) continue
            drawStar(c, Cfg.W / 2f, o.y)
        }
        drawTrail(c, pal[sm.color % 4])
        if (!sm.dead) drawBall(c, sm, pal[sm.color % 4])
        if (!sm.dead && !sm.finished && sm.started && cr.inZone(sm.by)) {
            // flechas junto a la bola: aquí se puede deslizar
            val a = (110 + 90 * sin(time * 5f)).toInt()
            val off = 66f + 6f * sin(time * 5f)
            for (sgn in intArrayOf(-1, 1)) {
                val ax = sm.bx + sgn * off
                Gl.line(c, ax, sm.by - 18f, ax + sgn * 16f, sm.by, 6f, Gl.white(a))
                Gl.line(c, ax + sgn * 16f, sm.by, ax, sm.by + 18f, 6f, Gl.white(a))
            }
        }
        drawParts(c)
        c.restore()
    }

    private fun drawFinish(c: Canvas, y: Float, pal: IntArray) {
        val n = 14
        val sw = 920f / n
        for (i in 0 until n) {
            val x0 = 80f + i * sw
            var pp = Gl.p()
            pp.color = Gl.alpha(pal[i % 4], 150)
            Gl.rect.set(x0 + 2f, y - 22f, x0 + sw - 2f, y + 22f)
            c.drawRoundRect(Gl.rect, 10f, 10f, pp)
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 2f
            pp.color = Gl.white(120)
            c.drawRoundRect(Gl.rect, 10f, 10f, pp)
        }
        Gl.text(c, "META", 540f, y - 60f, 60f, Gl.medium, Gl.white(235), 0.4f, null, Gl.alpha(pal[0], 160))
    }

    private val danger = 0xFFFF2D55.toInt()

    private fun polar(rad: Float, deg: Float, o: Ob, horiz: Boolean): Float {
        val a = deg * 3.14159265f / 180f
        return if (horiz) Cfg.W / 2f + cos(a) * rad else o.y + sin(a) * rad
    }

    private fun drawObstacle(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        if (o.drift != 0f) drawWind(c, o, t)
        val fa = Geo.fadeAlpha(o, t)
        val layer = fa < 0.99f
        if (layer) c.saveLayerAlpha(0f, o.y - o.reach - 20f, Cfg.W, o.y + o.reach + 20f, (fa * 255f).toInt())
        when (o.type) {
            T.RING -> drawRing(c, o, o.size, Geo.rot(o, t), o.shift, pal)
            T.DOUBLE -> {
                drawRing(c, o, o.size, Geo.rot(o, t), o.shift, pal)
                drawRing(c, o, o.inner, Geo.rotInner(o, t), (o.shift + 1) % o.n, pal)
            }
            T.BARS -> drawBars(c, o, t, pal)
            T.SPIKES -> drawSpikes(c, o, t, pal)
            T.PEND -> drawPend(c, o, t, pal)
            T.CROSS -> drawCross(c, o, t, pal)
            T.GATE -> drawGate(c, o, t)
            T.ORBIT -> drawOrbit(c, o, t, pal)
            T.ARC -> drawArcOb(c, o, t, pal)
            T.PULSE -> drawPulse(c, o, t, pal)
            T.LASER -> drawLaser(c, o, t)
            T.SGATE -> drawSGate(c, o, t)
            T.CBAND -> drawCBand(c, o, t, pal)
            T.SAW -> drawSaws(c, o, t)
            T.PILLAR -> drawPillar(c, o, t)
            T.COUNTER -> {
                drawBarLane(c, o, o.y - o.aux, o.phase * 3f + o.speed * t, pal)
                drawBarLane(c, o, o.y + o.aux, o.phase * 3f - o.speed * Cfg.COUNTER_RATIO * t, pal)
            }
            else -> drawPoly(c, o, t, pal)
        }
        if (layer) c.restore()
    }

    // ---- zonas de deslizar y sus obstáculos ----

    /** Tramo del recorrido donde la bola se mueve de lado: franja de cristal con rieles laterales. */
    private fun drawZone(c: Canvas, z: Zone, pal: IntArray) {
        val h = z.bottom - z.top
        val pp = Gl.p()
        pp.shader = LinearGradient(0f, z.top, 0f, z.bottom, intArrayOf(Gl.alpha(pal[0], 0), Gl.alpha(pal[0], 30), Gl.alpha(pal[1], 30), Gl.alpha(pal[1], 0)), floatArrayOf(0f, 0.12f, 0.88f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, z.top, Cfg.W, z.bottom, pp)
        // rieles laterales con luz que sube
        for (sgn in intArrayOf(0, 1)) {
            val x = if (sgn == 0) 30f else Cfg.W - 30f
            Gl.line(c, x, z.top, x, z.bottom, 8f, Gl.white(40))
            var yy = z.bottom - ((time * 140f) % 160f)
            while (yy > z.top) {
                Gl.line(c, x, yy, x, max(z.top, yy - 60f), 8f, Gl.alpha(pal[sgn], 190))
                yy -= 160f
            }
            // flechas hacia adentro, indican el movimiento lateral
            var ay = z.bottom - 150f
            while (ay > z.top + 80f) {
                val d = if (sgn == 0) 1f else -1f
                val a = (70 + 60 * sin(time * 3f + ay * 0.01f)).toInt()
                Gl.line(c, x + d * 22f, ay - 16f, x + d * 40f, ay, 5f, Gl.white(a))
                Gl.line(c, x + d * 40f, ay, x + d * 22f, ay + 16f, 5f, Gl.white(a))
                ay -= 300f
            }
        }
        // cartel de entrada
        if (h > 300f) {
            val a = (170 + 70 * sin(time * 3f)).toInt()
            val ty = z.bottom - 60f
            Gl.text(c, "DESLIZA", Cfg.W / 2f, ty + 14f, 40f, Gl.medium, Gl.white(a), 0.3f, null, Gl.alpha(pal[0], 150))
            for (sgn in intArrayOf(-1, 1)) {
                val ax = Cfg.W / 2f + sgn * 190f
                Gl.line(c, ax, ty - 14f, ax + sgn * 18f, ty, 6f, Gl.white(a))
                Gl.line(c, ax + sgn * 18f, ty, ax, ty + 14f, 6f, Gl.white(a))
            }
        }
    }

    private fun drawSGate(c: Canvas, o: Ob, t: Float) {
        val gx = Geo.sgateX(o, t)
        val half = Cfg.BAR_H
        val l1 = gx - o.aux
        val r0 = gx + o.aux
        Gl.glow(c, gx, o.y, o.aux * 1.1f, 0xFF4DFF8E.toInt(), 38)
        if (l1 > -120f) capsule(c, -120f, l1, o.y, half, danger)
        if (r0 < Cfg.W + 120f) capsule(c, r0, Cfg.W + 120f, o.y, half, danger)
    }

    private fun drawCBand(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val half = Cfg.BAR_H
        val off = o.phase * 3f + o.speed * t
        val j0 = floor((0f - off) / o.size).toInt() - 1
        val j1 = floor((Cfg.W - off) / o.size).toInt() + 1
        for (j in j0..j1) {
            val x0 = off + j * o.size + 4f
            val x1 = off + (j + 1) * o.size - 4f
            capsule(c, x0, x1, o.y, half, pal[(((j % o.n) + o.n) % o.n + o.shift) % o.n])
        }
    }

    private fun drawSaws(c: Canvas, o: Ob, t: Float) {
        Gl.line(c, Cfg.W / 2f - o.size, o.y, Cfg.W / 2f + o.size, o.y, 3f, Gl.white(22))
        for (k in 0 until max(1, o.inner.toInt())) {
            val x = Geo.sawX(o, t, k)
            val r = o.aux
            Gl.glow(c, x, o.y, r * 1.9f, danger, 60)
            val path = Gl.path
            path.reset()
            val spin = t * (if (o.speed >= 0f) 5.5f else -5.5f) + k
            for (i in 0 until 20) {
                val rr = if (i % 2 == 0) r else r * 0.8f
                val a = spin + i * 3.14159265f / 10f
                val px = x + cos(a) * rr
                val py = o.y + sin(a) * rr
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            var pp = Gl.p()
            pp.color = Gl.alpha(danger, 205)
            c.drawPath(path, pp)
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 2.4f
            pp.strokeJoin = Paint.Join.ROUND
            pp.color = Gl.white(150)
            c.drawPath(path, pp)
            pp = Gl.p()
            pp.color = Gl.white(60)
            c.drawCircle(x, o.y, r * 0.5f, pp)
            pp = Gl.p()
            pp.color = Gl.white(190)
            c.drawCircle(x, o.y, r * 0.16f, pp)
        }
    }

    private fun drawPillar(c: Canvas, o: Ob, t: Float) {
        val px = Geo.swingX(o, t)
        val hh = Cfg.PILLAR_HH
        Gl.glow(c, px, o.y, o.aux * 1.3f, danger, 40)
        Gl.panel(c, px - o.aux, o.y - hh, px + o.aux, o.y + hh, 26f, 1.25f, danger)
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 3f
        pp.color = Gl.alpha(danger, 220)
        Gl.rect.set(px - o.aux, o.y - hh, px + o.aux, o.y + hh)
        c.drawRoundRect(Gl.rect, 26f, 26f, pp)
        // franjas de peligro
        c.save()
        c.clipRect(px - o.aux + 6f, o.y - hh + 6f, px + o.aux - 6f, o.y + hh - 6f)
        var sx = px - o.aux - 40f
        while (sx < px + o.aux + 40f) {
            Gl.line(c, sx, o.y + hh, sx + 2f * hh, o.y - hh, 8f, Gl.alpha(danger, 120))
            sx += 44f
        }
        c.restore()
    }

    private fun drawArcOb(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val rot = Geo.rot(o, t)
        val th = Cfg.THICK
        val cx = Cfg.W / 2f
        val sweep = 360f - o.aux
        val span = sweep / o.n
        for (i in 0 until o.n) {
            val col = pal[(i + o.shift) % o.n]
            val st = rot + o.aux + i * span + 1.5f
            val sw = span - 3f
            Gl.arc(c, cx, o.y, o.size, st, sw, th * 1.9f, Gl.alpha(col, 34))
            Gl.arc(c, cx, o.y, o.size, st, sw, th, Gl.alpha(col, 170))
            Gl.arc(c, cx, o.y, o.size - th * 0.22f, st, sw, th * 0.4f, Gl.alpha(Gl.darken(col, 0.6f), 110))
            Gl.arc(c, cx, o.y, o.size + th * 0.5f, st, sw, 2.2f, Gl.white(125))
            Gl.arc(c, cx, o.y, o.size - th * 0.5f, st, sw, 2.2f, Gl.white(70))
            Gl.arc(c, cx, o.y, o.size + th * 0.2f, st + 5f, max(6f, sw - 22f), th * 0.16f, Gl.white(150))
        }
        // puntas redondeadas del hueco
        for (k in 0..1) {
            val a = if (k == 0) rot + 1.5f else rot + 360f - 1.5f
            val col = if (k == 0) pal[o.shift % o.n] else pal[(o.shift + o.n - 1) % o.n]
            val ax = polar(o.size, a, o, true)
            val ay = polar(o.size, a, o, false)
            val pp = Gl.p()
            pp.color = Gl.alpha(col, 120)
            c.drawCircle(ax, ay, th * 0.5f, pp)
        }
    }

    private fun drawPulse(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val idx = Geo.pulseIndex(o, t)
        val fr = Geo.pulseFrac(o, t)
        val next = Geo.pulseIndex(o, t + (1f - fr) / max(0.01f, o.speed) + 0.01f)
        capsule(c, -120f, Cfg.W + 120f, o.y, Cfg.BAR_H, pal[idx])
        val k = ((fr - 0.7f) / 0.3f).coerceIn(0f, 1f)
        for (side in 0..1) {
            val x = if (side == 0) 62f else Cfg.W - 62f
            var pp = Gl.p()
            pp.color = Gl.alpha(pal[next], (70 + 185 * k).toInt())
            c.drawCircle(x, o.y, 15f + 6f * k, pp)
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 2.4f
            pp.color = Gl.white(170)
            c.drawCircle(x, o.y, 15f + 6f * k, pp)
        }
    }

    private fun drawLaser(c: Canvas, o: Ob, t: Float) {
        val on = Geo.laserOn(o, t)
        val warn = Geo.laserWarn(o, t)
        val half = Cfg.BAR_H * 0.75f
        if (on) {
            var pp = Gl.p()
            pp.color = Gl.alpha(danger, 60)
            Gl.rect2.set(0f, o.y - half * 2.4f, Cfg.W, o.y + half * 2.4f)
            c.drawRect(Gl.rect2, pp)
            pp = Gl.p()
            pp.color = Gl.alpha(danger, 235)
            Gl.rect.set(0f, o.y - half, Cfg.W, o.y + half)
            c.drawRect(Gl.rect, pp)
            pp = Gl.p()
            pp.color = Gl.white(230)
            Gl.rect.set(0f, o.y - half * 0.28f, Cfg.W, o.y + half * 0.28f)
            c.drawRect(Gl.rect, pp)
        } else {
            val blink = if (warn) (if (((t * 14f).toInt() and 1) == 0) 150 else 40) else 28
            Gl.line(c, 0f, o.y, Cfg.W, o.y, if (warn) 6f else 3f, Gl.alpha(danger, blink))
        }
        for (side in 0..1) {
            val x = if (side == 0) 34f else Cfg.W - 34f
            var pp = Gl.p()
            pp.color = Gl.alpha(danger, if (on) 255 else 150)
            c.drawCircle(x, o.y, 26f, pp)
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 3f
            pp.color = Gl.white(200)
            c.drawCircle(x, o.y, 26f, pp)
        }
    }

    private fun drawWind(c: Canvas, o: Ob, t: Float) {
        val top = o.y - o.reach - 60f
        val bot = o.y + o.reach + 60f
        val up = o.drift < 0f
        var pp = Gl.p()
        pp.color = Gl.white(9)
        c.drawRect(0f, top, Cfg.W, bot, pp)
        val h = bot - top
        for (col in 0 until 6) {
            val x = 110f + col * 172f
            for (k in 0 until 3) {
                val u = (((t * 0.55f + k / 3f + col * 0.13f) % 1f) + 1f) % 1f
                val yy = if (up) bot - u * h else top + u * h
                val a = (sin(u * 3.14159265f) * 70f).toInt()
                val d = if (up) 1f else -1f
                Gl.line(c, x - 22f, yy + 12f * d, x, yy - 12f * d, 5f, Gl.white(a))
                Gl.line(c, x + 22f, yy + 12f * d, x, yy - 12f * d, 5f, Gl.white(a))
            }
        }
    }

    private fun drawSpikes(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val rot = Geo.rot(o, t)
        drawRing(c, o, o.size, rot, o.shift, pal)
        val segs = Geo.ringSegs(o.n)
        val span = 360f / segs
        val th = Cfg.THICK
        val hw = max(2f, o.aux)
        val ro = o.size + th / 2f
        val ri = o.size - th / 2f
        val hgt = Cfg.SPIKE_H + 4f
        for (i in 0 until segs) {
            val ang = rot + i * span
            for (side in 0..1) {
                val base = if (side == 0) ro else ri
                val tip = if (side == 0) ro + hgt else ri - hgt
                Gl.path.reset()
                Gl.path.moveTo(polar(base, ang - hw, o, true), polar(base, ang - hw, o, false))
                Gl.path.lineTo(polar(tip, ang, o, true), polar(tip, ang, o, false))
                Gl.path.lineTo(polar(base, ang + hw, o, true), polar(base, ang + hw, o, false))
                Gl.path.close()
                var pp = Gl.p()
                pp.color = Gl.alpha(danger, 235)
                c.drawPath(Gl.path, pp)
                pp = Gl.p()
                pp.style = Paint.Style.STROKE
                pp.strokeWidth = 2.4f
                pp.strokeJoin = Paint.Join.ROUND
                pp.color = Gl.white(210)
                c.drawPath(Gl.path, pp)
            }
        }
    }

    private fun capsule(c: Canvas, x0: Float, x1: Float, y: Float, half: Float, col: Int) {
        var pp = Gl.p()
        pp.color = Gl.alpha(col, 34)
        Gl.rect2.set(x0 - 10f, y - half - 10f, x1 + 10f, y + half + 10f)
        c.drawRoundRect(Gl.rect2, half + 10f, half + 10f, pp)
        Gl.rect.set(x0, y - half, x1, y + half)
        pp = Gl.p()
        pp.color = Gl.alpha(col, 170)
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

    private fun drawPend(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val cx = Geo.swingX(o, t)
        val half = Cfg.BAR_H
        val left = pal[o.shift % o.n]
        val right = pal[(o.shift + 1) % o.n]
        // hilo y guía de la oscilación
        Gl.line(c, Cfg.W / 2f - o.size, o.y, Cfg.W / 2f + o.size, o.y, 3f, Gl.white(22))
        capsule(c, cx - o.aux, cx - 3f, o.y, half, left)
        capsule(c, cx + 3f, cx + o.aux, o.y, half, right)
    }

    private fun drawCross(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val th = Cfg.THICK
        for (k in 0 until 4) {
            val a = Geo.crossAng(o, t, k)
            val ca = cos(a)
            val sa = sin(a)
            val x0 = Cfg.W / 2f + ca * o.aux
            val y0 = o.y + sa * o.aux
            val x1 = Cfg.W / 2f + ca * o.size
            val y1 = o.y + sa * o.size
            val col = pal[(k + o.shift) % o.n]
            Gl.line(c, x0, y0, x1, y1, th * 1.9f, Gl.alpha(col, 34))
            Gl.line(c, x0, y0, x1, y1, th, Gl.alpha(col, 170))
            Gl.line(c, x0 - sa * th * 0.2f, y0 + ca * th * 0.2f, x1 - sa * th * 0.2f, y1 + ca * th * 0.2f, th * 0.14f, Gl.white(150))
        }
        val pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2f
        pp.color = Gl.white(35)
        c.drawCircle(Cfg.W / 2f, o.y, o.aux - th * 0.6f, pp)
    }

    private fun drawGate(c: Canvas, o: Ob, t: Float) {
        val cx = Geo.swingX(o, t)
        val half = Cfg.BAR_H
        val l1 = cx - o.aux
        val r0 = cx + o.aux
        Gl.line(c, Cfg.W / 2f - o.size, o.y, Cfg.W / 2f + o.size, o.y, 3f, Gl.white(18))
        if (l1 > -120f) capsule(c, -120f, l1, o.y, half, danger)
        if (r0 < Cfg.W + 120f) capsule(c, r0, Cfg.W + 120f, o.y, half, danger)
    }

    private fun drawOrbit(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        var pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2f
        pp.color = Gl.white(28)
        c.drawCircle(Cfg.W / 2f, o.y, o.size, pp)
        c.drawCircle(Cfg.W / 2f, o.y, o.inner, pp)
        for (k in 0 until 3) orb(c, Geo.orbX(o, t, k, false), Geo.orbY(o, t, k, false), pal[(k + o.shift) % o.n])
        for (k in 0 until 2) orb(c, Geo.orbX(o, t, k, true), Geo.orbY(o, t, k, true), pal[(k + o.shift + 1) % o.n])
    }

    private fun orb(c: Canvas, x: Float, y: Float, col: Int) {
        val r = Cfg.ORB_R
        Gl.glow(c, x, y, r * 1.9f, col, 80)
        var pp = Gl.p()
        pp.shader = android.graphics.RadialGradient(
            x - r * 0.3f, y - r * 0.35f, r * 1.4f,
            intArrayOf(Gl.white(220), Gl.alpha(col, 225), Gl.alpha(Gl.darken(col, 0.55f), 235)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.2f
        pp.color = Gl.white(150)
        c.drawCircle(x, y, r, pp)
    }

    private fun drawRing(c: Canvas, o: Ob, rad: Float, rot: Float, shift: Int, pal: IntArray) {
        val segs = Geo.ringSegs(o.n)
        val span = 360f / segs
        val th = Cfg.THICK
        val cx = Cfg.W / 2f
        for (i in 0 until segs) {
            val col = pal[(i + shift) % o.n]
            val st = rot + i * span + 1.5f
            val sw = span - 3f
            Gl.arc(c, cx, o.y, rad, st, sw, th * 1.9f, Gl.alpha(col, 34))
            Gl.arc(c, cx, o.y, rad, st, sw, th, Gl.alpha(col, 170))
            Gl.arc(c, cx, o.y, rad - th * 0.22f, st, sw, th * 0.4f, Gl.alpha(Gl.darken(col, 0.6f), 110))
            Gl.arc(c, cx, o.y, rad + th * 0.5f, st, sw, 2.2f, Gl.white(125))
            Gl.arc(c, cx, o.y, rad - th * 0.5f, st, sw, 2.2f, Gl.white(70))
            Gl.arc(c, cx, o.y, rad + th * 0.2f, st + 5f, max(6f, sw - 22f), th * 0.16f, Gl.white(150))
        }
    }

    private fun drawBars(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        for (lane in 0 until Cfg.BAR_LANES) {
            drawBarLane(c, o, o.y + if (lane == 0) -Cfg.BAR_DY else Cfg.BAR_DY, o.phase * 3f + o.speed * t, pal)
        }
    }

    private fun drawBarLane(c: Canvas, o: Ob, y: Float, off: Float, pal: IntArray) {
        val half = Cfg.BAR_H
        val j0 = floor((0f - off) / Cfg.BAR_SEG).toInt() - 1
        val j1 = floor((Cfg.W - off) / Cfg.BAR_SEG).toInt() + 1
        for (j in j0..j1) {
            val x0 = off + j * Cfg.BAR_SEG + 4f
            val x1 = off + (j + 1) * Cfg.BAR_SEG - 4f
            capsule(c, x0, x1, y, half, pal[(((j % o.n) + o.n) % o.n + o.shift) % o.n])
        }
    }

    private fun drawPoly(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        val k = Geo.polySides(o)
        val th = Cfg.THICK
        for (i in 0 until k) {
            val j = (i + 1) % k
            val x0 = Geo.vx(o, t, i)
            val y0 = Geo.vy(o, t, i)
            val x1 = Geo.vx(o, t, j)
            val y1 = Geo.vy(o, t, j)
            val col = pal[(i + o.shift) % o.n]
            Gl.line(c, x0, y0, x1, y1, th * 1.9f, Gl.alpha(col, 34))
            Gl.line(c, x0, y0, x1, y1, th, Gl.alpha(col, 170))
            val dx = x1 - x0
            val dy = y1 - y0
            val len = sqrt(dx * dx + dy * dy)
            if (len > 1f) {
                val ux = dx / len
                val uy = dy / len
                var nx = uy
                var ny = -ux
                val mx = (x0 + x1) / 2f - Cfg.W / 2f
                val my = (y0 + y1) / 2f - o.y
                if (mx * nx + my * ny < 0f) {
                    nx = -nx
                    ny = -ny
                }
                val inset = th * 0.45f
                val off = th * 0.2f
                Gl.line(
                    c, x0 + ux * inset + nx * off, y0 + uy * inset + ny * off,
                    x1 - ux * inset + nx * off, y1 - uy * inset + ny * off, th * 0.14f, Gl.white(150)
                )
            }
        }
    }

    private fun drawStar(c: Canvas, x: Float, y: Float) {
        Gl.glow(c, x, y, 70f, Color.WHITE, 55)
        val rot = time * 0.8f
        val pal = cur()
        val pp = Gl.p()
        pp.shader = LinearGradient(x - 26f, y - 26f, x + 26f, y + 26f, Gl.white(245), Gl.alpha(pal[0], 200), Shader.TileMode.CLAMP)
        val path = Gl.path
        path.reset()
        for (k in 0 until 10) {
            val ang = -3.14159f / 2f + k * 3.14159f / 5f + rot
            val rr = if (k % 2 == 0) 26f else 11f
            val px = x + cos(ang) * rr
            val py = y + sin(ang) * rr
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, pp)
        val q = Gl.p()
        q.style = Paint.Style.STROKE
        q.strokeWidth = 2f
        q.strokeJoin = Paint.Join.ROUND
        q.color = Gl.white(200)
        c.drawPath(path, q)
    }

    private fun drawSwitcher(c: Canvas, s: Sw, cr: Course, pal: IntArray) =
        drawSwitcherN(c, s, if (cr.isLevel) Levels.colors(cr.level) else 4, pal)

    private fun drawSwitcherN(c: Canvas, s: Sw, n: Int, pal: IntArray) {
        val x = Cfg.W / 2f
        val r = Cfg.SW_R
        Gl.glow(c, x, s.y, r * 2.8f, Color.WHITE, 45)
        Gl.rect.set(x - r, s.y - r, x + r, s.y + r)
        val rot = time * 90f
        val span = 360f / n
        for (i in 0 until n) {
            val pp = Gl.p()
            pp.color = Gl.alpha(pal[i % 4], 235)
            c.drawArc(Gl.rect, rot + i * span, span, true, pp)
        }
        var pp = Gl.p()
        pp.color = Gl.alpha(pal[s.toColor % 4], 255)
        c.drawCircle(x, s.y, r * 0.42f, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.6f
        pp.color = Gl.white(190)
        c.drawCircle(x, s.y, r * 0.42f, pp)
        pp = Gl.p()
        pp.shader = android.graphics.RadialGradient(
            x - r * 0.3f, s.y - r * 0.35f, r * 1.2f, Gl.white(130), Gl.white(0), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, s.y, r, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.6f
        pp.color = Gl.white(170)
        c.drawCircle(x, s.y, r, pp)
    }

    private fun drawTrail(c: Canvas, col: Int) {
        Trails.draw(c, effTrail(), trail, col, Worlds.colors[world], Cfg.BALL_R, time)
    }

    private fun trailOpen(k: Int): Boolean = prefs.allOpen || prefs.totalStars() >= Trails.need[k]

    private fun effTrail(): Int {
        val k = prefs.trail
        return if (k in 0 until Trails.COUNT && trailOpen(k)) k else 0
    }

    private val prevTrail = ArrayList<FloatArray>().also { l -> for (i in 0 until 34) l.add(FloatArray(3)) }

    /** Rastro de muestra: la bola recorre una onda hacia la derecha y deja su estela detrás. */
    private fun drawTrailDemo(c: Canvas, st: Int, x0: Float, x1: Float, cy: Float, amp: Float, r: Float, col: Int, pal: IntArray, tm: Float, ballR: Float) {
        val ph = (tm * 0.7f + st * 0.37f) % 1f
        val span = x1 - x0
        val n = prevTrail.size
        val len = 0.42f // fracción del recorrido que ocupa el rastro
        for (i in 0 until n) {
            val u = i / (n - 1f)                 // 0 = más viejo
            val age = (1f - u) * Trails.life[st] * 0.95f
            val q = (ph - (1f - u) * len).let { it - floor(it) }
            val t = prevTrail[i]
            t[0] = x0 + span * q
            t[1] = cy + sin(q * 6.2832f * 1.5f) * amp
            t[2] = age
        }
        val ph2 = ph
        val bxx = x0 + span * ph2
        val byy = cy + sin(ph2 * 6.2832f * 1.5f) * amp
        // en el borde de la vuelta los puntos saltan: se recortan los que quedan más allá del inicio
        val keep = ArrayList<FloatArray>(n)
        for (i in 0 until n) { if (prevTrail[i][0] <= bxx + 0.5f) keep.add(prevTrail[i]) }
        Trails.draw(c, st, keep, col, pal, r, tm)
        drawBallAt(c, bxx, byy, ballR, col, effSkin(), tm, false)
    }

    private val ballClip = Path()

    private fun skinOpen(k: Int): Boolean {
        if (prefs.allOpen) return true
        if (Skins.special(k)) return prefs.endlessBest >= Skins.endNeed(k) || prefs.worldsDone() >= Skins.worldNeed(k)
        return prefs.totalStars() >= Skins.need[k]
    }

    private fun effSkin(): Int {
        val k = prefs.skin
        return if (k in Skins.names.indices && skinOpen(k)) k else 0
    }

    private fun drawBall(c: Canvas, sm: Sim, col: Int) {
        val r = Cfg.BALL_R
        val x = sm.bx
        val y = if (sm.started) sm.by else sm.by + sin(time * 3f) * 9f
        drawBallAt(c, x, y, r, col, effSkin(), time, true)
    }

    private fun starPath(x: Float, y: Float, ro: Float, ri: Float) {
        Gl.path.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) ro else ri
            val a = (-90.0 + i * 36.0) * Math.PI / 180.0
            val px = x + (rr * cos(a)).toFloat()
            val py = y + (rr * sin(a)).toFloat()
            if (i == 0) Gl.path.moveTo(px, py) else Gl.path.lineTo(px, py)
        }
        Gl.path.close()
    }

    /** La bola siempre conserva el color de juego; la apariencia solo cambia el diseño. */
    private fun drawBallAt(c: Canvas, x: Float, y: Float, r: Float, col: Int, skin: Int, tm: Float, glow: Boolean) {
        if (glow) Gl.glow(c, x, y, r * 2.8f, col, 110)
        var pp = Gl.p()
        pp.shader = android.graphics.RadialGradient(
            x - r * 0.35f, y - r * 0.4f, r * 1.5f,
            intArrayOf(Gl.white(235), Gl.alpha(col, 235), Gl.alpha(Gl.darken(col, 0.5f), 245)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, pp)

        // diseño interior (recortado a la bola)
        c.save()
        ballClip.reset()
        ballClip.addCircle(x, y, r, Path.Direction.CW)
        c.clipPath(ballClip)
        when (skin) {
            2 -> {
                c.rotate(35f, x, y)
                for (k in -1..1) {
                    pp = Gl.p()
                    pp.color = Gl.white(95)
                    Gl.rect.set(x - r * 1.3f, y + k * r * 0.62f - r * 0.14f, x + r * 1.3f, y + k * r * 0.62f + r * 0.14f)
                    c.drawRect(Gl.rect, pp)
                }
            }
            3 -> {
                val dx = floatArrayOf(-0.45f, 0.4f, 0.05f, -0.1f, 0.55f, -0.6f)
                val dy = floatArrayOf(-0.35f, -0.4f, 0.05f, 0.6f, 0.35f, 0.3f)
                val dr = floatArrayOf(0.17f, 0.12f, 0.2f, 0.14f, 0.1f, 0.1f)
                for (i in 0 until 6) {
                    pp = Gl.p()
                    pp.color = Gl.white(100)
                    c.drawCircle(x + dx[i] * r, y + dy[i] * r, dr[i] * r, pp)
                }
            }
            4 -> {
                starPath(x, y + r * 0.04f, r * 0.55f, r * 0.24f)
                pp = Gl.p()
                pp.color = Gl.white(205)
                c.drawPath(Gl.path, pp)
            }
            6 -> {
                c.save()
                c.rotate(45f, x, y)
                pp = Gl.p()
                pp.style = Paint.Style.STROKE
                pp.strokeWidth = r * 0.11f
                pp.color = Gl.white(190)
                Gl.rect.set(x - r * 0.36f, y - r * 0.36f, x + r * 0.36f, y + r * 0.36f)
                c.drawRoundRect(Gl.rect, r * 0.06f, r * 0.06f, pp)
                c.restore()
            }
            7 -> {
                Gl.arc(c, x, y, r * 0.55f, 0f, 360f, r * 0.1f, Gl.white(150))
                pp = Gl.p()
                pp.color = Gl.white(200)
                c.drawCircle(x, y, r * 0.2f, pp)
            }
            12 -> {
                Gl.path.reset()
                Gl.path.moveTo(x + r * 0.12f, y - r * 0.62f)
                Gl.path.lineTo(x - r * 0.34f, y + r * 0.08f)
                Gl.path.lineTo(x - r * 0.02f, y + r * 0.08f)
                Gl.path.lineTo(x - r * 0.14f, y + r * 0.62f)
                Gl.path.lineTo(x + r * 0.36f, y - r * 0.12f)
                Gl.path.lineTo(x + r * 0.04f, y - r * 0.12f)
                Gl.path.close()
                pp = Gl.p()
                pp.color = Gl.white(215)
                c.drawPath(Gl.path, pp)
            }
            13 -> {
                val hy = y + r * 0.02f
                Gl.path.reset()
                Gl.path.moveTo(x, hy + r * 0.46f)
                Gl.path.cubicTo(x - r * 0.75f, hy - r * 0.02f, x - r * 0.42f, hy - r * 0.62f, x, hy - r * 0.2f)
                Gl.path.cubicTo(x + r * 0.42f, hy - r * 0.62f, x + r * 0.75f, hy - r * 0.02f, x, hy + r * 0.46f)
                Gl.path.close()
                pp = Gl.p()
                pp.color = Gl.white(210)
                c.drawPath(Gl.path, pp)
            }
            14 -> {
                Gl.rect.set(x - r * 0.62f, y - r * 0.34f, x + r * 0.62f, y + r * 0.34f)
                pp = Gl.p()
                pp.color = Gl.white(225)
                c.drawOval(Gl.rect, pp)
                pp = Gl.p()
                pp.color = Gl.darken(col, 0.3f)
                c.drawCircle(x, y, r * 0.25f, pp)
                pp = Gl.p()
                pp.color = Gl.white(240)
                c.drawCircle(x - r * 0.07f, y - r * 0.08f, r * 0.07f, pp)
            }
            15 -> {
                val q = r * 0.5f
                for (i in -2..1) for (j in -2..1) {
                    if ((i + j) and 1 == 0) continue
                    pp = Gl.p()
                    pp.color = Gl.white(80)
                    c.drawRect(x + i * q, y + j * q, x + (i + 1) * q, y + (j + 1) * q, pp)
                }
            }
            16 -> {
                val bx = floatArrayOf(-0.4f, 0.35f, 0.0f, 0.5f, -0.1f)
                val by = floatArrayOf(0.35f, 0.4f, -0.15f, -0.2f, -0.55f)
                val br = floatArrayOf(0.2f, 0.14f, 0.12f, 0.09f, 0.08f)
                for (i in 0 until 5) {
                    val rr = br[i] * r
                    val ox = x + bx[i] * r
                    val oy = y + by[i] * r - ((tm * 0.25f + i * 0.2f) % 1f) * r * 0.15f
                    pp = Gl.p()
                    pp.style = Paint.Style.STROKE
                    pp.strokeWidth = r * 0.04f
                    pp.color = Gl.white(190)
                    c.drawCircle(ox, oy, rr, pp)
                    pp = Gl.p()
                    pp.color = Gl.white(110)
                    c.drawCircle(ox - rr * 0.3f, oy - rr * 0.3f, rr * 0.28f, pp)
                }
            }
            17 -> {
                for (k in -1..1) {
                    Gl.path.reset()
                    val base = y + k * r * 0.5f
                    Gl.path.moveTo(x - r, base)
                    var t = -1f
                    while (t <= 1.001f) {
                        Gl.path.lineTo(x + t * r, base + sin((t * 3.2f + tm * 1.2f)) * r * 0.14f)
                        t += 0.1f
                    }
                    pp = Gl.p()
                    pp.style = Paint.Style.STROKE
                    pp.strokeWidth = r * 0.12f
                    pp.color = Gl.white(120)
                    c.drawPath(Gl.path, pp)
                }
            }
            18 -> {
                for (i in 0 until 12) {
                    val a = (i * 30.0 + tm * 20.0) * Math.PI / 180.0
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    Gl.line(c, x + ca * r * 0.34f, y + sa * r * 0.34f, x + ca * r * 0.78f, y + sa * r * 0.78f, r * 0.08f, Gl.white(150))
                }
                pp = Gl.p()
                pp.color = Gl.white(215)
                c.drawCircle(x, y, r * 0.24f, pp)
            }
            19 -> {
                val q = r * 0.3f
                val px = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
                val py = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
                for (i in 0 until 8) {
                    pp = Gl.p()
                    pp.color = Gl.white(if ((i + (tm * 2f).toInt()) % 3 == 0) 230 else 110)
                    c.drawRect(x + px[i] * q * 1.2f - q / 2f, y + py[i] * q * 1.2f - q / 2f, x + px[i] * q * 1.2f + q / 2f, y + py[i] * q * 1.2f + q / 2f, pp)
                }
            }
            in 20..39 -> drawSkinIcon(c, x, y, r, skin, col, tm)
            in Skins.ANIM_FIRST..59 -> drawAnimIcon(c, x, y, r, skin, col, tm)
            in 60..76 -> drawAnimIcon2(c, x, y, r, skin, tm)
            9 -> {
                pp = Gl.p()
                pp.color = Gl.alpha(Gl.darken(col, 0.25f), 110)
                c.drawCircle(x, y, r, pp)
            }
            11 -> {
                pp = Gl.p()
                pp.color = Gl.alpha(Gl.darken(col, 0.3f), 120)
                c.drawCircle(x, y, r, pp)
                val sx = floatArrayOf(-0.5f, 0.3f, 0.55f, -0.15f, 0.1f, -0.6f, 0.45f)
                val sy = floatArrayOf(-0.3f, -0.55f, 0.1f, 0.45f, -0.05f, 0.35f, 0.55f)
                for (i in 0 until 7) {
                    val a = (0.5f + 0.5f * sin(tm * 3f + i * 1.7f))
                    pp = Gl.p()
                    pp.color = Gl.white((70 + 170 * a).toInt())
                    c.drawCircle(x + sx[i] * r, y + sy[i] * r, r * (0.05f + 0.04f * a), pp)
                }
            }
        }
        c.restore()

        // borde
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        when (skin) {
            1, 10 -> {
                val cols = if (skin == 1)
                    intArrayOf(0xFF5CF2FF.toInt(), 0xFFB46CFF.toInt(), 0xFFFF6FD8.toInt(), 0xFF5CF2FF.toInt())
                else
                    intArrayOf(0xFFFF4F6B.toInt(), 0xFFFFB84F.toInt(), 0xFFFFF04F.toInt(), 0xFF4FFF8A.toInt(),
                        0xFF4FB8FF.toInt(), 0xFFB04FFF.toInt(), 0xFFFF4F6B.toInt())
                val sh = android.graphics.SweepGradient(x, y, cols, null)
                val m = android.graphics.Matrix()
                m.postRotate(tm * 90f, x, y)
                sh.setLocalMatrix(m)
                pp.shader = sh
                pp.strokeWidth = r * 0.15f
            }
            8 -> {
                pp.strokeWidth = r * 0.16f
                pp.color = 0xFFFFD25E.toInt()
            }
            9 -> {
                val acc = 0xFFFF4FD8.toInt()
                val g = Gl.p()
                g.style = Paint.Style.STROKE
                g.strokeWidth = r * 0.42f
                g.color = Gl.alpha(acc, 60)
                c.drawCircle(x, y, r, g)
                pp.strokeWidth = r * 0.14f
                pp.color = acc
            }
            else -> {
                pp.strokeWidth = 2.4f
                pp.color = Gl.white(160)
            }
        }
        c.drawCircle(x, y, r, pp)
        if (skin == 5) {
            c.save()
            c.rotate(-22f, x, y)
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = r * 0.13f
            pp.color = Gl.white(190)
            Gl.rect.set(x - r * 1.5f, y - r * 0.38f, x + r * 1.5f, y + r * 0.38f)
            c.drawOval(Gl.rect, pp)
            c.restore()
        }
        if (skin != 9) Gl.arc(c, x, y, r * 0.72f, 40f, 100f, r * 0.16f, Gl.white(70))
        pp = Gl.p()
        pp.color = Gl.white(215)
        Gl.rect.set(x - r * 0.64f, y - r * 0.74f, x - r * 0.06f, y - r * 0.3f)
        c.drawOval(Gl.rect, pp)
    }

    private fun pw(a: Int): Paint {
        val p = Gl.p()
        p.color = Gl.white(a)
        return p
    }

    private fun pk(color: Int): Paint {
        val p = Gl.p()
        p.color = color
        return p
    }

    private fun ps(a: Int, w: Float): Paint {
        val p = Gl.p()
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Gl.white(a)
        return p
    }

    /** Dibujos de las apariencias 20..39 (dentro de la bola, ya recortada). */
    private fun drawSkinIcon(c: Canvas, x: Float, y: Float, r: Float, skin: Int, col: Int, tm: Float) {
        val dark = Color.argb(185, 10, 14, 30)
        val path = Gl.path
        when (skin) {
            20 -> { // voleibol
                Gl.arc(c, x - r * 0.95f, y - r * 0.15f, r * 1.0f, -42f, 84f, r * 0.09f, Gl.white(200))
                Gl.arc(c, x + r * 0.2f, y + r * 1.05f, r * 1.05f, 205f, 80f, r * 0.09f, Gl.white(200))
                Gl.arc(c, x + r * 0.55f, y - r * 0.95f, r * 1.05f, 100f, 75f, r * 0.09f, Gl.white(200))
            }
            21 -> { // cápsula
                Gl.rect.set(x - r, y - r * 0.11f, x + r, y + r * 0.11f)
                c.drawRect(Gl.rect, pk(dark))
                c.drawCircle(x, y, r * 0.28f, pk(Gl.darken(col, 0.35f)))
                c.drawCircle(x, y, r * 0.28f, ps(230, r * 0.07f))
                c.drawCircle(x, y, r * 0.12f, pw(230))
            }
            22 -> { // flechas hacia afuera
                for (k in 0 until 4) {
                    val a = (45.0 + k * 90.0) * Math.PI / 180.0
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    val x0 = x + ca * r * 0.16f
                    val y0 = y + sa * r * 0.16f
                    val x1 = x + ca * r * 0.6f
                    val y1 = y + sa * r * 0.6f
                    Gl.line(c, x0, y0, x1, y1, r * 0.09f, Gl.white(220))
                    for (sgn in intArrayOf(-1, 1)) {
                        val b = a + Math.PI + sgn * 0.7
                        Gl.line(c, x1, y1, x1 + (cos(b) * r * 0.22f).toFloat(), y1 + (sin(b) * r * 0.22f).toFloat(), r * 0.09f, Gl.white(220))
                    }
                }
            }
            23 -> { // calabaza
                for (sgn in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(x + sgn * r * 0.34f, y - r * 0.32f)
                    path.lineTo(x + sgn * r * 0.14f, y - r * 0.02f)
                    path.lineTo(x + sgn * r * 0.5f, y - r * 0.02f)
                    path.close()
                    c.drawPath(path, pk(dark))
                }
                path.reset()
                path.moveTo(x - r * 0.5f, y + r * 0.2f)
                var up = false
                var px = x - r * 0.5f
                while (px < x + r * 0.5f) {
                    px += r * 0.125f
                    path.lineTo(px, y + r * (if (up) 0.2f else 0.4f))
                    up = !up
                }
                c.drawPath(path, ps(255, r * 0.09f).also { it.color = dark })
            }
            24 -> { // claqueta
                Gl.rect.set(x - r * 0.5f, y - r * 0.05f, x + r * 0.5f, y + r * 0.45f)
                c.drawRoundRect(Gl.rect, r * 0.06f, r * 0.06f, pw(205))
                Gl.rect.set(x - r * 0.52f, y - r * 0.42f, x + r * 0.52f, y - r * 0.12f)
                c.drawRoundRect(Gl.rect, r * 0.05f, r * 0.05f, pw(235))
                for (k in 0 until 3) {
                    val sx = x - r * 0.34f + k * r * 0.3f
                    Gl.line(c, sx, y - r * 0.12f, sx + r * 0.14f, y - r * 0.42f, r * 0.07f, dark)
                }
            }
            25 -> { // bomba
                c.drawCircle(x - r * 0.05f, y + r * 0.14f, r * 0.42f, pk(dark))
                Gl.line(c, x + r * 0.2f, y - r * 0.2f, x + r * 0.38f, y - r * 0.5f, r * 0.07f, Gl.white(220))
                val k = 0.5f + 0.5f * sin(tm * 8f)
                c.drawCircle(x + r * 0.42f, y - r * 0.56f, r * (0.08f + 0.05f * k), pw(250))
                Gl.arc(c, x - r * 0.05f, y + r * 0.14f, r * 0.28f, 200f, 60f, r * 0.05f, Gl.white(150))
            }
            26 -> { // hongo
                Gl.rect.set(x - r * 0.55f, y - r * 0.5f, x + r * 0.55f, y + r * 0.3f)
                c.drawArc(Gl.rect, 180f, 180f, true, pw(215))
                Gl.rect.set(x - r * 0.2f, y - r * 0.02f, x + r * 0.2f, y + r * 0.5f)
                c.drawRoundRect(Gl.rect, r * 0.08f, r * 0.08f, pw(235))
                c.drawCircle(x - r * 0.25f, y - r * 0.2f, r * 0.09f, pk(Gl.darken(col, 0.4f)))
                c.drawCircle(x + r * 0.22f, y - r * 0.25f, r * 0.07f, pk(Gl.darken(col, 0.4f)))
                c.drawCircle(x + r * 0.02f, y - r * 0.08f, r * 0.06f, pk(Gl.darken(col, 0.4f)))
            }
            27 -> { // pergamino
                Gl.rect.set(x - r * 0.34f, y - r * 0.48f, x + r * 0.34f, y + r * 0.48f)
                c.drawRoundRect(Gl.rect, r * 0.08f, r * 0.08f, pw(215))
                for (k in 0 until 4) Gl.line(c, x - r * 0.2f, y - r * 0.28f + k * r * 0.19f, x + r * 0.2f, y - r * 0.28f + k * r * 0.19f, r * 0.05f, dark)
                c.drawCircle(x - r * 0.34f, y - r * 0.48f, r * 0.1f, pw(240))
                c.drawCircle(x + r * 0.34f, y + r * 0.48f, r * 0.1f, pw(240))
            }
            28 -> { // enfoque
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    val cx = x + sx * r * 0.52f
                    val cy = y + sy * r * 0.52f
                    Gl.line(c, cx, cy, cx, cy - sy * r * 0.26f, r * 0.08f, Gl.white(225))
                    Gl.line(c, cx, cy, cx - sx * r * 0.26f, cy, r * 0.08f, Gl.white(225))
                }
                c.drawCircle(x, y, r * 0.17f, ps(230, r * 0.07f))
            }
            29 -> { // flor
                for (k in 0 until 5) {
                    val a = (-90.0 + k * 72.0) * Math.PI / 180.0
                    c.drawCircle(x + (cos(a) * r * 0.3f).toFloat(), y + (sin(a) * r * 0.3f).toFloat(), r * 0.22f, pw(205))
                }
                c.drawCircle(x, y, r * 0.15f, pk(Gl.darken(col, 0.45f)))
            }
            30 -> { // escudo
                path.reset()
                path.moveTo(x - r * 0.42f, y - r * 0.46f)
                path.lineTo(x + r * 0.42f, y - r * 0.46f)
                path.lineTo(x + r * 0.42f, y + r * 0.04f)
                path.quadTo(x + r * 0.42f, y + r * 0.38f, x, y + r * 0.56f)
                path.quadTo(x - r * 0.42f, y + r * 0.38f, x - r * 0.42f, y + r * 0.04f)
                path.close()
                c.drawPath(path, pw(90))
                c.drawPath(path, ps(230, r * 0.07f))
                path.reset()
                path.moveTo(x, y - r * 0.46f)
                path.lineTo(x + r * 0.42f, y - r * 0.46f)
                path.lineTo(x + r * 0.42f, y + r * 0.04f)
                path.quadTo(x + r * 0.42f, y + r * 0.38f, x, y + r * 0.56f)
                path.close()
                c.drawPath(path, pk(dark))
            }
            31 -> { // trofeo
                path.reset()
                path.moveTo(x - r * 0.4f, y - r * 0.46f)
                path.lineTo(x + r * 0.4f, y - r * 0.46f)
                path.lineTo(x + r * 0.24f, y + r * 0.08f)
                path.lineTo(x - r * 0.24f, y + r * 0.08f)
                path.close()
                c.drawPath(path, pw(225))
                Gl.rect.set(x - r * 0.07f, y + r * 0.06f, x + r * 0.07f, y + r * 0.3f)
                c.drawRect(Gl.rect, pw(225))
                Gl.rect.set(x - r * 0.3f, y + r * 0.28f, x + r * 0.3f, y + r * 0.44f)
                c.drawRoundRect(Gl.rect, r * 0.05f, r * 0.05f, pw(235))
                Gl.arc(c, x - r * 0.42f, y - r * 0.28f, r * 0.17f, 90f, 180f, r * 0.06f, Gl.white(210))
                Gl.arc(c, x + r * 0.42f, y - r * 0.28f, r * 0.17f, 270f, 180f, r * 0.06f, Gl.white(210))
            }
            32 -> { // mariposa
                for (sgn in intArrayOf(-1, 1)) {
                    c.save()
                    c.rotate(sgn * 24f, x + sgn * r * 0.3f, y - r * 0.15f)
                    Gl.rect.set(x + sgn * r * 0.3f - r * 0.26f, y - r * 0.15f - r * 0.3f, x + sgn * r * 0.3f + r * 0.26f, y - r * 0.15f + r * 0.3f)
                    c.drawOval(Gl.rect, pw(210))
                    c.restore()
                    Gl.rect.set(x + sgn * r * 0.22f - r * 0.18f, y + r * 0.2f - r * 0.2f, x + sgn * r * 0.22f + r * 0.18f, y + r * 0.2f + r * 0.2f)
                    c.drawOval(Gl.rect, pw(170))
                }
                Gl.line(c, x, y - r * 0.4f, x, y + r * 0.42f, r * 0.08f, dark)
            }
            33 -> { // alerta
                path.reset()
                path.moveTo(x, y - r * 0.5f)
                path.lineTo(x + r * 0.5f, y + r * 0.4f)
                path.lineTo(x - r * 0.5f, y + r * 0.4f)
                path.close()
                c.drawPath(path, pw(225))
                Gl.line(c, x, y - r * 0.12f, x, y + r * 0.14f, r * 0.09f, dark)
                c.drawCircle(x, y + r * 0.27f, r * 0.06f, pk(dark))
            }
            34 -> { // corona
                path.reset()
                path.moveTo(x - r * 0.46f, y + r * 0.3f)
                path.lineTo(x - r * 0.46f, y - r * 0.28f)
                path.lineTo(x - r * 0.22f, y - r * 0.02f)
                path.lineTo(x, y - r * 0.4f)
                path.lineTo(x + r * 0.22f, y - r * 0.02f)
                path.lineTo(x + r * 0.46f, y - r * 0.28f)
                path.lineTo(x + r * 0.46f, y + r * 0.3f)
                path.close()
                c.drawPath(path, pw(225))
                Gl.rect.set(x - r * 0.46f, y + r * 0.16f, x + r * 0.46f, y + r * 0.3f)
                c.drawRect(Gl.rect, pk(dark))
            }
            35 -> { // equilibrio (yin-yang)
                Gl.rect.set(x - r * 0.6f, y - r * 0.6f, x + r * 0.6f, y + r * 0.6f)
                c.drawArc(Gl.rect, 90f, 180f, true, pw(215))
                c.drawCircle(x, y - r * 0.3f, r * 0.3f, pw(215))
                c.drawCircle(x, y + r * 0.3f, r * 0.3f, pk(Gl.darken(col, 0.45f)))
                c.drawCircle(x, y - r * 0.3f, r * 0.08f, pk(Gl.darken(col, 0.45f)))
                c.drawCircle(x, y + r * 0.3f, r * 0.08f, pw(230))
            }
            36 -> { // hoja
                path.reset()
                path.moveTo(x - r * 0.42f, y + r * 0.42f)
                path.quadTo(x - r * 0.5f, y - r * 0.5f, x + r * 0.46f, y - r * 0.46f)
                path.quadTo(x + r * 0.5f, y + r * 0.5f, x - r * 0.42f, y + r * 0.42f)
                path.close()
                c.drawPath(path, pw(210))
                Gl.line(c, x - r * 0.4f, y + r * 0.4f, x + r * 0.3f, y - r * 0.3f, r * 0.06f, dark)
            }
            37 -> { // copo de nieve
                for (k in 0 until 3) {
                    val a = (k * 60.0) * Math.PI / 180.0
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    Gl.line(c, x - ca * r * 0.62f, y - sa * r * 0.62f, x + ca * r * 0.62f, y + sa * r * 0.62f, r * 0.07f, Gl.white(225))
                    for (e in intArrayOf(-1, 1)) {
                        val ex = x + e * ca * r * 0.4f
                        val ey = y + e * sa * r * 0.4f
                        for (sg in intArrayOf(-1, 1)) {
                            val b = a + sg * 0.9 + (if (e < 0) Math.PI else 0.0)
                            Gl.line(c, ex, ey, ex + (cos(b) * r * 0.16f).toFloat(), ey + (sin(b) * r * 0.16f).toFloat(), r * 0.05f, Gl.white(200))
                        }
                    }
                }
            }
            38 -> { // gota
                path.reset()
                path.moveTo(x, y - r * 0.52f)
                path.cubicTo(x + r * 0.52f, y + r * 0.08f, x + r * 0.34f, y + r * 0.52f, x, y + r * 0.52f)
                path.cubicTo(x - r * 0.34f, y + r * 0.52f, x - r * 0.52f, y + r * 0.08f, x, y - r * 0.52f)
                path.close()
                c.drawPath(path, pw(210))
                Gl.arc(c, x, y + r * 0.12f, r * 0.26f, 120f, 60f, r * 0.05f, dark)
            }
            39 -> { // luna creciente
                val a = Path()
                a.addCircle(x, y, r * 0.48f, Path.Direction.CW)
                val b = Path()
                b.addCircle(x + r * 0.24f, y - r * 0.1f, r * 0.4f, Path.Direction.CW)
                a.op(b, Path.Op.DIFFERENCE)
                c.drawPath(a, pw(225))
                c.drawCircle(x + r * 0.4f, y + r * 0.28f, r * 0.05f, pw(220))
            }
        }
    }

    private val uSpace = Color.argb(255, 5, 8, 25)

    private fun dot(c: Canvas, x: Float, y: Float, rad: Float, color: Int, a: Int) {
        val p = Gl.p()
        p.color = Gl.alpha(color, a)
        c.drawCircle(x, y, max(0.5f, rad), p)
    }

    private fun frac(v: Float): Float = v - floor(v)

    /** Apariencias animadas 40..59: elementos naturales y espaciales dentro de la bola (ya recortada). */
    private fun drawAnimIcon(c: Canvas, x: Float, y: Float, r: Float, skin: Int, col: Int, tm: Float) {
        val under = intArrayOf(90, 60, 0, 110, 130, 0, 40, 60, 140, 150, 130, 170, 120, 150, 150, 150, 130, 150, 160, 90)
        val ua = under[skin - Skins.ANIM_FIRST]
        if (ua > 0) c.drawCircle(x, y, r, pk(Gl.alpha(uSpace, ua)))
        val path = Gl.path
        when (skin) {
            40 -> { // fuego
                for (k in 0 until 9) {
                    val u = frac(tm * 0.85f + k / 9f)
                    val px = x + sin(k * 2.3f + tm * 3f) * r * 0.22f * (1f - u * 0.4f)
                    val py = y + r * (0.62f - u * 1.15f)
                    val cc = if (u < 0.3f) 0xFFFFE082.toInt() else if (u < 0.65f) 0xFFFF8F00.toInt() else 0xFFE53935.toInt()
                    dot(c, px, py, r * (0.30f * (1f - u) + 0.05f), cc, (230 * (1f - u * 0.85f)).toInt())
                }
                dot(c, x, y + r * 0.7f, r * 0.45f, 0xFFFFB300.toInt(), 120)
            }
            41 -> { // humo
                for (k in 0 until 8) {
                    val u = frac(tm * 0.35f + k / 8f)
                    val px = x + sin(k * 1.9f + tm * 1.4f) * r * 0.25f
                    val py = y + r * (0.6f - u * 1.3f)
                    dot(c, px, py, r * (0.14f + 0.34f * u), if (k % 2 == 0) 0xFFCFD8DC.toInt() else 0xFF90A4AE.toInt(), (150 * (1f - u)).toInt())
                }
            }
            42 -> { // agua
                for (k in 0 until 3) {
                    path.reset()
                    val by = y + (k - 1) * r * 0.42f
                    path.moveTo(x - r, by)
                    var px = -1f
                    while (px <= 1.001f) {
                        path.lineTo(x + px * r, by + sin(px * 4.2f + tm * 2.2f + k * 1.7f) * r * 0.13f)
                        px += 0.1f
                    }
                    c.drawPath(path, ps(if (k == 1) 230 else 160, r * 0.11f).also { it.color = Gl.alpha(if (k == 1) 0xFFB3E5FC.toInt() else Color.WHITE, if (k == 1) 230 else 160) })
                }
                for (k in 0 until 4) {
                    val u = frac(tm * 0.4f + k * 0.27f)
                    dot(c, x + sin(k * 3.1f) * r * 0.5f, y + r * (0.8f - u * 1.5f), r * 0.07f, Color.WHITE, (200 * (1f - u)).toInt())
                }
            }
            43 -> { // tormenta
                for (k in 0 until 4) dot(c, x + (k - 1.5f) * r * 0.42f + sin(tm * 0.8f + k) * r * 0.08f, y - r * 0.45f + (k % 2) * r * 0.1f, r * 0.34f, 0xFF607D8B.toInt(), 230)
                val ph = tm % 2.6f
                val flash = if (ph < 0.12f || (ph > 0.22f && ph < 0.3f)) 1f else 0f
                if (flash > 0f) {
                    c.drawCircle(x, y, r, pk(Gl.white(60)))
                    path.reset()
                    path.moveTo(x + r * 0.08f, y - r * 0.2f)
                    path.lineTo(x - r * 0.12f, y + r * 0.08f)
                    path.lineTo(x + r * 0.1f, y + r * 0.1f)
                    path.lineTo(x - r * 0.16f, y + r * 0.55f)
                    c.drawPath(path, ps(255, r * 0.1f).also { it.color = 0xFFFFF59D.toInt() })
                }
                for (k in 0 until 6) {
                    val u = frac(tm * 1.6f + k * 0.17f)
                    Gl.line(c, x + (k - 2.5f) * r * 0.3f, y + r * (0.0f + u * 0.9f), x + (k - 2.5f) * r * 0.3f - r * 0.05f, y + r * (0.14f + u * 0.9f), r * 0.04f, Gl.white(150))
                }
            }
            44 -> { // lava
                for (k in 0 until 5) {
                    val px = x + sin(tm * 0.7f + k * 1.9f) * r * 0.4f
                    val py = y + sin(tm * 0.9f + k * 2.7f) * r * 0.6f
                    dot(c, px, py, r * (0.26f + 0.08f * sin(tm + k)), 0xFFFF7043.toInt(), 215)
                    dot(c, px, py, r * 0.14f, 0xFFFFEB3B.toInt(), 200)
                }
            }
            45 -> { // escarcha
                for (k in 0 until 10) {
                    val u = frac(tm * 0.3f + k * 0.137f)
                    dot(c, x + sin(k * 2.7f + tm) * r * 0.7f, y - r + u * 2f * r, r * (0.05f + 0.03f * (k % 3)), Color.WHITE, 220)
                }
                for (k in 0 until 3) {
                    val a = k * 60.0 * Math.PI / 180.0
                    Gl.line(c, x - (cos(a) * r * 0.45f).toFloat(), y - (sin(a) * r * 0.45f).toFloat(), x + (cos(a) * r * 0.45f).toFloat(), y + (sin(a) * r * 0.45f).toFloat(), r * 0.04f, Gl.white(90))
                }
            }
            46 -> { // remolino
                for (arm in 0 until 3) for (i in 0 until 9) {
                    val ang = tm * 2.4f + arm * 2.094f + i * 0.5f
                    val rad = r * (0.08f + i * 0.095f)
                    dot(c, x + cos(ang) * rad, y + sin(ang) * rad, r * (0.06f + 0.012f * i), Color.WHITE, 230 - i * 20)
                }
            }
            47 -> { // lluvia
                for (k in 0 until 9) {
                    val u = frac(tm * 1.2f + k * 0.113f)
                    val px = x - r + (k * 0.27f % 1f) * 2f * r
                    val py = y - r + u * 2.2f * r
                    Gl.line(c, px, py, px - r * 0.07f, py + r * 0.22f, r * 0.045f, Gl.alpha(0xFFB3E5FC.toInt(), 220))
                }
            }
            48 -> { // luciérnagas
                for (k in 0 until 7) {
                    val px = x + sin(tm * 0.6f + k * 2.1f) * r * 0.6f
                    val py = y + cos(tm * 0.5f + k * 1.3f) * r * 0.6f
                    val a = (0.5f + 0.5f * sin(tm * 2.2f + k * 1.7f))
                    dot(c, px, py, r * 0.2f, 0xFFCDDC39.toInt(), (60 * a).toInt())
                    dot(c, px, py, r * 0.07f, 0xFFF9FBE7.toInt(), (80 + 175 * a).toInt())
                }
            }
            49 -> { // brasas
                for (k in 0 until 10) {
                    val u = frac(tm * 0.6f + k * 0.1f)
                    val px = x + sin(k * 2.9f + tm * 2f) * r * 0.5f
                    dot(c, px, y + r * (0.8f - u * 1.6f), r * (0.08f + 0.05f * (k % 3)), if (k % 2 == 0) 0xFFFF9800.toInt() else 0xFFFF5722.toInt(), (240 * (1f - u)).toInt())
                }
                dot(c, x, y + r * 0.85f, r * 0.5f, 0xFFD84315.toInt(), 130)
            }
            50 -> { // nebulosa
                val cols = intArrayOf(0xFFAB47BC.toInt(), 0xFFEC407A.toInt(), 0xFF26C6DA.toInt())
                for (k in 0 until 3) {
                    val px = x + sin(tm * 0.35f + k * 2.1f) * r * 0.35f
                    val py = y + cos(tm * 0.3f + k * 1.7f) * r * 0.35f
                    Gl.glow(c, px, py, r * 0.8f, cols[k], 170)
                }
                for (k in 0 until 8) {
                    val a = 0.5f + 0.5f * sin(tm * 2f + k * 1.9f)
                    dot(c, x + sin(k * 4.1f) * r * 0.8f, y + cos(k * 2.3f) * r * 0.8f, r * 0.04f, Color.WHITE, (90 + 160 * a).toInt())
                }
            }
            51 -> { // agujero negro
                for (i in 0 until 14) {
                    val ang = tm * (1.6f + (i % 4) * 0.25f) + i * 0.9f
                    val rad = r * (0.3f + (i % 5) * 0.11f)
                    dot(c, x + cos(ang) * rad, y + sin(ang) * rad * 0.4f, r * 0.05f, if (i % 2 == 0) 0xFFFFB74D.toInt() else Color.WHITE, 220)
                }
                c.save()
                c.rotate(-20f, x, y)
                Gl.rect.set(x - r * 0.75f, y - r * 0.2f, x + r * 0.75f, y + r * 0.2f)
                c.drawOval(Gl.rect, ps(210, r * 0.06f).also { it.color = 0xFFFFA726.toInt() })
                c.restore()
                c.drawCircle(x, y, r * 0.24f, pk(Color.BLACK))
            }
            52 -> { // supernova
                val u = frac(tm * 0.5f)
                dot(c, x, y, r * (0.15f + 0.25f * sin(tm * 6f) * 0.2f + 0.2f), 0xFFFFF8E1.toInt(), 255)
                Gl.glow(c, x, y, r * 0.9f, 0xFFFFCA28.toInt(), 150)
                c.drawCircle(x, y, r * (0.2f + 0.8f * u), ps((220 * (1f - u)).toInt(), r * 0.08f).also { it.color = Gl.alpha(0xFFFFF176.toInt(), (220 * (1f - u)).toInt()) })
                for (k in 0 until 8) {
                    val a = k * 45.0 * Math.PI / 180.0 + tm * 0.4f
                    Gl.line(c, x + (cos(a) * r * 0.2f).toFloat(), y + (sin(a) * r * 0.2f).toFloat(), x + (cos(a) * r * (0.45f + 0.3f * u)).toFloat(), y + (sin(a) * r * (0.45f + 0.3f * u)).toFloat(), r * 0.04f, Gl.alpha(0xFFFFF59D.toInt(), 200))
                }
            }
            53 -> { // cometa
                for (k in 0 until 6) dot(c, x + sin(k * 5.3f) * r * 0.8f, y + cos(k * 3.7f) * r * 0.8f, r * 0.035f, Color.WHITE, 170)
                val u = frac(tm * 0.45f)
                val hx = x - r * 0.9f + u * r * 1.8f
                val hy = y - r * 0.6f + u * r * 1.2f
                for (k in 1..10) {
                    val t2 = k / 10f
                    dot(c, hx - t2 * r * 0.7f, hy - t2 * r * 0.47f, r * (0.12f * (1f - t2) + 0.02f), 0xFF80DEEA.toInt(), (200 * (1f - t2)).toInt())
                }
                dot(c, hx, hy, r * 0.13f, Color.WHITE, 255)
            }
            54 -> { // sistema solar
                c.drawCircle(x, y, r * 0.36f, ps(40, r * 0.02f))
                c.drawCircle(x, y, r * 0.62f, ps(40, r * 0.02f))
                dot(c, x, y, r * 0.17f, 0xFFFFD54F.toInt(), 255)
                Gl.glow(c, x, y, r * 0.5f, 0xFFFFB300.toInt(), 120)
                dot(c, x + cos(tm * 2.2f) * r * 0.36f, y + sin(tm * 2.2f) * r * 0.36f, r * 0.08f, 0xFF4FC3F7.toInt(), 255)
                dot(c, x + cos(tm * 1.2f + 2f) * r * 0.62f, y + sin(tm * 1.2f + 2f) * r * 0.62f, r * 0.11f, 0xFFEF5350.toInt(), 255)
            }
            55 -> { // pulsar
                c.save()
                c.rotate(tm * 120f, x, y)
                for (sgn in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(x, y)
                    path.lineTo(x + sgn * r * 1.1f, y - r * 0.12f)
                    path.lineTo(x + sgn * r * 1.1f, y + r * 0.12f)
                    path.close()
                    c.drawPath(path, pk(Gl.white(140)))
                }
                c.restore()
                Gl.glow(c, x, y, r * 0.5f, 0xFF80D8FF.toInt(), 200)
                dot(c, x, y, r * 0.11f, Color.WHITE, 255)
            }
            56 -> { // aurora boreal
                val cols = intArrayOf(0xFF69F0AE.toInt(), 0xFF40C4FF.toInt(), 0xFFB388FF.toInt())
                for (k in 0 until 3) {
                    path.reset()
                    var first = true
                    var px = -1f
                    while (px <= 1.001f) {
                        val py = y - r * 0.15f + sin(px * 3f + tm * 1.3f + k * 1.4f) * r * 0.25f + (k - 1) * r * 0.25f
                        if (first) { path.moveTo(x + px * r, py); first = false } else path.lineTo(x + px * r, py)
                        px += 0.1f
                    }
                    c.drawPath(path, ps(150, r * 0.22f).also { it.color = Gl.alpha(cols[k], (110 + 70 * sin(tm + k)).toInt()) })
                }
                for (k in 0 until 6) dot(c, x + sin(k * 4.1f) * r * 0.8f, y - r * 0.5f + cos(k * 2.3f) * r * 0.4f, r * 0.035f, Color.WHITE, 190)
            }
            57 -> { // espiral
                Gl.glow(c, x, y, r * 0.55f, 0xFFFFE082.toInt(), 190)
                for (arm in 0 until 2) for (i in 0 until 14) {
                    val ang = tm * 0.9f + arm * 3.1416f + i * 0.42f
                    val rad = r * (0.1f + i * 0.058f)
                    dot(c, x + cos(ang) * rad, y + sin(ang) * rad, r * (0.075f - i * 0.003f), if (i < 5) 0xFFFFF8E1.toInt() else 0xFF9FA8DA.toInt(), 230 - i * 10)
                }
            }
            58 -> { // eclipse
                val pulse = 0.5f + 0.5f * sin(tm * 2f)
                Gl.glow(c, x, y, r * 0.95f, 0xFFFFE0B2.toInt(), (110 + 90 * pulse).toInt())
                c.drawCircle(x, y, r * 0.42f, ps(230, r * 0.05f).also { it.color = Gl.alpha(0xFFFFF3E0.toInt(), 230) })
                c.drawCircle(x, y, r * 0.4f, pk(Color.BLACK))
                val a = tm * 0.6f
                dot(c, x + cos(a) * r * 0.42f, y + sin(a) * r * 0.42f, r * (0.07f + 0.04f * pulse), Color.WHITE, 255)
            }
            59 -> { // plasma
                val cols = intArrayOf(0xFFFF4081.toInt(), 0xFF40C4FF.toInt(), 0xFFFFEA00.toInt(), 0xFFB388FF.toInt())
                for (k in 0 until 4) {
                    val px = x + sin(tm * (0.9f + k * 0.3f) + k * 1.7f) * r * 0.5f
                    val py = y + cos(tm * (0.7f + k * 0.25f) + k * 2.3f) * r * 0.5f
                    Gl.glow(c, px, py, r * 0.65f, cols[k], 190)
                }
                for (k in 0 until 3) {
                    val a = tm * 1.5f + k * 2.1f
                    Gl.line(c, x, y, x + cos(a) * r * 0.85f, y + sin(a) * r * 0.85f, r * 0.03f, Gl.white(110))
                }
                dot(c, x, y, r * 0.1f, Color.WHITE, 220)
            }
        }
    }

    private fun hf(n: Float): Float = frac(sin(n * 12.9898f) * 43758.547f)

    /** Apariencias animadas 60..76: fenómenos, ciencia y magia (también recortadas a la bola). */
    private fun drawAnimIcon2(c: Canvas, x: Float, y: Float, r: Float, skin: Int, tm: Float) {
        val under = intArrayOf(90, 0, 120, 0, 200, 90, 120, 140, 200, 150, 150, 160, 150, 160, 150, 90, 100)
        val ua = under[skin - 60]
        if (ua > 0) c.drawCircle(x, y, r, pk(Gl.alpha(uSpace, ua)))
        val path = Gl.path
        val pi = Math.PI.toFloat()
        when (skin) {
            60 -> { // tornado
                for (i in 0 until 8) {
                    val u = i / 7f
                    val yy = y - r * 0.75f + u * r * 1.45f
                    val rx = r * (0.78f - 0.62f * u)
                    val sx = x + sin(tm * 2.6f + u * 5f) * r * 0.08f * (1f - u * 0.5f)
                    Gl.rect.set(sx - rx, yy - r * 0.07f, sx + rx, yy + r * 0.07f)
                    c.drawOval(Gl.rect, ps(if (i % 2 == 0) 230 else 160, r * 0.06f))
                }
                for (k in 0 until 6) {
                    val a = tm * 3.2f + k * 1.05f
                    val u = frac(tm * 0.5f + k * 0.17f)
                    dot(c, x + cos(a) * r * (0.2f + 0.5f * (1f - u)), y + r * (0.8f - 1.5f * u), r * 0.045f, 0xFFB0BEC5.toInt(), 220)
                }
            }
            61 -> { // terremoto
                c.drawCircle(x, y, r, pk(Gl.alpha(0xFF8D6E63.toInt(), 200)))
                val sh = sin(tm * 38f) * r * 0.025f
                val pulse = 0.5f + 0.5f * sin(tm * 3f)
                c.save()
                c.translate(sh, -sh * 0.6f)
                for (k in 0 until 4) {
                    val a = k * 1.57f + 0.5f
                    path.reset()
                    path.moveTo(x, y)
                    for (s in 1..5) {
                        val rad = r * 0.2f * s
                        val jit = (hf(k * 7f + s * 3.1f) - 0.5f) * r * 0.35f
                        path.lineTo(x + cos(a) * rad - sin(a) * jit, y + sin(a) * rad + cos(a) * jit)
                    }
                    c.drawPath(path, ps(255, r * 0.1f).also { it.color = Gl.alpha(0xFFFF8F00.toInt(), (90 + 100 * pulse).toInt()) })
                    c.drawPath(path, ps(255, r * 0.045f).also { it.color = 0xFF2B1A12.toInt() })
                }
                c.restore()
                for (k in 0 until 6) {
                    val u = frac(tm * 0.7f + k * 0.17f)
                    dot(c, x + (hf(k * 2.1f) - 0.5f) * r * 1.4f, y + r * 0.8f - u * r * 0.9f, r * 0.06f, 0xFFD7CCC8.toInt(), (170 * (1f - u)).toInt())
                }
            }
            62 -> { // volcán
                path.reset()
                path.moveTo(x - r * 1.1f, y + r)
                path.lineTo(x - r * 0.22f, y + r * 0.2f)
                path.lineTo(x + r * 0.22f, y + r * 0.2f)
                path.lineTo(x + r * 1.1f, y + r)
                path.close()
                c.drawPath(path, pk(0xFF4E342E.toInt()))
                Gl.line(c, x + r * 0.05f, y + r * 0.25f, x + r * 0.4f, y + r * 0.95f, r * 0.07f, Gl.alpha(0xFFFF6D00.toInt(), 200))
                Gl.glow(c, x, y + r * 0.2f, r * 0.55f, 0xFFFF6D00.toInt(), 190)
                for (k in 0 until 9) {
                    val u = frac(tm * 0.65f + k / 9f)
                    val dir = (hf(k * 3.3f) - 0.5f) * 1.6f
                    val h = 4f * u * (1f - u) * (0.6f + 0.5f * hf(k * 1.7f))
                    dot(c, x + dir * r * 0.7f * u, y + r * 0.2f - r * 1.2f * h, r * 0.07f, if (k % 2 == 0) 0xFFFFD54F.toInt() else 0xFFFF7043.toInt(), (240 * (1f - u * 0.4f)).toInt())
                }
                for (k in 0 until 4) {
                    val u = frac(tm * 0.3f + k * 0.25f)
                    dot(c, x + sin(k * 2.3f + tm) * r * 0.2f, y - r * 0.1f - u * r * 0.8f, r * (0.1f + 0.2f * u), 0xFF90A4AE.toInt(), (120 * (1f - u)).toInt())
                }
            }
            63 -> { // avalancha
                c.drawCircle(x, y, r, pk(Gl.alpha(0xFFB3E5FC.toInt(), 110)))
                for (k in 0 until 12) {
                    val u = frac(tm * 0.45f + k * 0.083f)
                    val px = x - r * 0.9f + u * r * 1.8f + (hf(k * 1.9f) - 0.5f) * r * 0.4f
                    val py = y - r * 0.8f + u * r * 1.6f + sin(k * 2f) * r * 0.15f
                    dot(c, px, py, r * (0.08f + 0.1f * hf(k * 2.2f)), if (k % 3 == 0) 0xFFE1F5FE.toInt() else Color.WHITE, 235)
                }
                Gl.rect.set(x - r, y + r * 0.55f, x + r, y + r * 1.3f)
                c.drawOval(Gl.rect, pk(Gl.alpha(Color.WHITE, 235)))
            }
            64 -> { // matriz digital
                for (cn in 0 until 6) {
                    val cx = x + (cn - 2.5f) * r * 0.32f
                    val sp = 0.5f + hf(cn * 5.1f) * 0.7f
                    for (k in 0 until 5) {
                        val u = frac(tm * sp + cn * 0.31f - k * 0.09f)
                        val py = y - r + u * 2.2f * r
                        val a = (235 * (1f - k / 5f)).toInt()
                        c.drawRect(cx - r * 0.05f, py - r * 0.07f, cx + r * 0.05f, py + r * 0.07f, pk(Gl.alpha(if (k == 0) 0xFFE8FFE8.toInt() else 0xFF00E676.toInt(), a)))
                    }
                }
            }
            65 -> { // holograma
                val cc = 0xFF4DD0E1.toInt()
                c.drawCircle(x, y, r, pk(Gl.alpha(cc, 35)))
                val rr = r * 0.85f
                for (k in -2..2) {
                    val ly = y + k * r * 0.33f
                    val rx = sqrt(max(0f, rr * rr - (k * r * 0.33f) * (k * r * 0.33f)))
                    Gl.rect.set(x - rx, ly - rx * 0.18f, x + rx, ly + rx * 0.18f)
                    c.drawOval(Gl.rect, ps(160, r * 0.03f).also { it.color = Gl.alpha(cc, 170) })
                }
                for (k in 0 until 3) {
                    val rxl = abs(cos(tm * 1.2f + k * 1.047f)) * rr
                    Gl.rect.set(x - rxl, y - rr, x + rxl, y + rr)
                    c.drawOval(Gl.rect, ps(160, r * 0.03f).also { it.color = Gl.alpha(cc, 150) })
                }
                val sy = y - r + frac(tm * 0.6f) * 2f * r
                Gl.line(c, x - rr, sy, x + rr, sy, r * 0.05f, Gl.alpha(0xFFB2EBF2.toInt(), 160))
            }
            66 -> { // teseracto
                val a = tm * 0.9f
                val ox = FloatArray(4)
                val oy = FloatArray(4)
                val ix = FloatArray(4)
                val iy = FloatArray(4)
                for (k in 0 until 4) {
                    val ao = a + k * 1.5708f + 0.7854f
                    ox[k] = x + cos(ao) * r * 0.66f
                    oy[k] = y + sin(ao) * r * 0.66f
                    val ai = -a * 1.3f + k * 1.5708f + 0.7854f
                    ix[k] = x + cos(ai) * r * 0.33f
                    iy[k] = y + sin(ai) * r * 0.33f
                }
                val ca = 0xFF80DEEA.toInt()
                val cb = 0xFFEA80FC.toInt()
                Gl.glow(c, x, y, r * 0.4f, cb, 70)
                for (k in 0 until 4) {
                    val n = (k + 1) % 4
                    Gl.line(c, ox[k], oy[k], ox[n], oy[n], r * 0.05f, Gl.alpha(ca, 230))
                    Gl.line(c, ix[k], iy[k], ix[n], iy[n], r * 0.05f, Gl.alpha(cb, 230))
                    Gl.line(c, ox[k], oy[k], ix[k], iy[k], r * 0.03f, Gl.white(130))
                }
            }
            67 -> { // caos cuántico
                val cols = intArrayOf(0xFFFF4081.toInt(), 0xFF40C4FF.toInt(), 0xFFFFEA00.toInt(), 0xFFB388FF.toInt(), 0xFF69F0AE.toInt())
                c.drawCircle(x, y, r * 0.8f, ps(40, r * 0.02f))
                for (k in 0 until 16) {
                    val st = floor(tm * 3f + k * 0.37f)
                    val f = frac(tm * 3f + k * 0.37f)
                    val ang = hf(st * 1.3f + k) * 6.2832f
                    val rad = sqrt(hf(st * 2.7f + k * 3f)) * r * 0.85f
                    dot(c, x + cos(ang) * rad, y + sin(ang) * rad, r * 0.06f, cols[k % 5], (60 + 195 * sin(f * pi)).toInt())
                }
            }
            68 -> { // materia oscura
                Gl.glow(c, x, y, r * 0.9f, 0xFF4A148C.toInt(), 150)
                for (k in 0 until 7) {
                    val a = tm * 0.7f + k * 0.9f
                    val rad = r * (0.35f + 0.3f * sin(tm * 0.5f + k))
                    Gl.glow(c, x + cos(a) * rad, y + sin(a) * rad * 0.8f, r * 0.45f, if (k % 2 == 0) 0xFF7B1FA2.toInt() else 0xFF311B92.toInt(), 130)
                }
                c.drawCircle(x, y, r * 0.3f, pk(0xFF06000F.toInt()))
                c.drawCircle(x, y, r * 0.3f, ps(120, r * 0.03f).also { it.color = Gl.alpha(0xFFB388FF.toInt(), 120) })
                for (k in 0 until 8) {
                    val tw = 0.5f + 0.5f * sin(tm * 1.5f + k * 2.1f)
                    dot(c, x + sin(k * 4.1f) * r * 0.8f, y + cos(k * 2.3f) * r * 0.8f, r * 0.03f, 0xFFE1BEE7.toInt(), (40 + 120 * tw).toInt())
                }
            }
            69 -> { // agujero de gusano
                for (k in 0 until 7) {
                    val u = frac(tm * 0.45f + k / 7f)
                    val rad = r * (1f - 0.92f * u)
                    val a = (60 + 190 * u).toInt()
                    val colr = if (k % 2 == 0) 0xFF40C4FF.toInt() else 0xFFB388FF.toInt()
                    c.drawCircle(x, y, rad, ps(a, r * 0.05f).also { it.color = Gl.alpha(colr, a) })
                }
                Gl.glow(c, x, y, r * 0.35f, Color.WHITE, 200)
                dot(c, x, y, r * 0.06f, Color.WHITE, 255)
            }
            70 -> { // ecos del big bang
                val cols = intArrayOf(0xFFFF80AB.toInt(), 0xFF82B1FF.toInt(), 0xFFFFE57F.toInt())
                for (k in 0 until 5) {
                    val u = frac(tm * 0.4f + k * 0.2f)
                    val a = (230 * (1f - u)).toInt()
                    c.drawCircle(x, y, r * 1.1f * u, ps(a, r * (0.02f + 0.07f * (1f - u))).also { it.color = Gl.alpha(cols[k % 3], a) })
                }
                val fl = 0.6f + 0.4f * sin(tm * 5f)
                Gl.glow(c, x, y, r * 0.45f, 0xFFFFF3E0.toInt(), (170 * fl).toInt())
                dot(c, x, y, r * 0.09f, Color.WHITE, 255)
                for (k in 0 until 8) {
                    val u = frac(tm * 0.4f + k * 0.125f)
                    val a = k * 0.785f
                    dot(c, x + cos(a) * r * u, y + sin(a) * r * u, r * 0.035f, Color.WHITE, (220 * (1f - u)).toInt())
                }
            }
            71 -> { // bioluminiscencia
                for (k in 0 until 7) {
                    val px = x + sin(tm * 0.4f + k * 2.3f) * r * 0.6f
                    val py = y + cos(tm * 0.33f + k * 1.7f) * r * 0.6f
                    val p = 0.5f + 0.5f * sin(tm * 1.8f + k * 1.3f)
                    val cc = if (k % 2 == 0) 0xFF1DE9B6.toInt() else 0xFF18FFFF.toInt()
                    Gl.glow(c, px, py, r * 0.32f, cc, (60 + 110 * p).toInt())
                    val ang = tm * 0.5f + k
                    Gl.line(c, px - cos(ang) * r * 0.09f, py - sin(ang) * r * 0.09f, px + cos(ang) * r * 0.09f, py + sin(ang) * r * 0.09f, r * 0.07f, Gl.alpha(cc, (150 + 100 * p).toInt()))
                }
                for (k in 0 until 8) {
                    val u = frac(tm * 0.2f + k * 0.125f)
                    dot(c, x + sin(k * 3.7f) * r * 0.8f, y + r - u * 2f * r, r * 0.025f, 0xFFB9F6CA.toInt(), 160)
                }
            }
            72 -> { // adn giratorio
                for (i in 0 until 12) {
                    val yy = y - r * 0.82f + i * r * 0.149f
                    val ph = tm * 2.2f + i * 0.55f
                    val sx = sin(ph) * r * 0.4f
                    val d1 = cos(ph)
                    Gl.line(c, x - sx, yy, x + sx, yy, r * 0.03f, Gl.white(90))
                    dot(c, x + sx, yy, r * (0.07f + 0.03f * d1), 0xFF40C4FF.toInt(), (170 + 80 * d1).toInt())
                    dot(c, x - sx, yy, r * (0.07f - 0.03f * d1), 0xFFFF4081.toInt(), (170 - 80 * d1).toInt())
                }
            }
            73 -> { // telaraña estelar
                Gl.glow(c, x, y, r * 0.8f, 0xFF7C4DFF.toInt(), 100)
                c.save()
                c.rotate(tm * 8f, x, y)
                Spooky.web(c, x, y, r * 0.92f, 190, 0f, 360f, 4)
                c.restore()
                for (k in 0 until 8) {
                    val j = 1 + (k * 3) % 4
                    val ang = Math.toRadians((tm * 8f + k * 45f).toDouble())
                    val rad = r * 0.92f * j / 4f
                    val tw = 0.5f + 0.5f * sin(tm * 3f + k * 1.7f)
                    val px = x + cos(ang).toFloat() * rad
                    val py = y + sin(ang).toFloat() * rad
                    Gl.glow(c, px, py, r * 0.14f, 0xFFE3F2FD.toInt(), (60 + 100 * tw).toInt())
                    dot(c, px, py, r * 0.045f, 0xFFE3F2FD.toInt(), (110 + 145 * tw).toInt())
                }
            }
            74 -> { // runas antiguas
                val cc = 0xFFB388FF.toInt()
                val c2 = Gl.alpha(0xFFE1BEE7.toInt(), 240)
                val pulse = 0.5f + 0.5f * sin(tm * 2f)
                Gl.glow(c, x, y, r * 0.9f, cc, (70 + 60 * pulse).toInt())
                c.drawCircle(x, y, r * 0.82f, ps(200, r * 0.03f).also { it.color = Gl.alpha(cc, 200) })
                c.drawCircle(x, y, r * 0.52f, ps(150, r * 0.02f).also { it.color = Gl.alpha(cc, 150) })
                c.save()
                c.rotate(tm * 25f, x, y)
                for (k in 0 until 6) {
                    val a = k * 1.0472f
                    val gx = x + cos(a) * r * 0.67f
                    val gy = y + sin(a) * r * 0.67f
                    c.save()
                    c.rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, gx, gy)
                    val w = r * 0.07f
                    when (k % 3) {
                        0 -> {
                            Gl.line(c, gx, gy - w * 1.6f, gx, gy + w * 1.6f, r * 0.035f, c2)
                            Gl.line(c, gx, gy - w * 0.8f, gx + w * 1.2f, gy - w * 1.6f, r * 0.035f, c2)
                        }
                        1 -> {
                            Gl.line(c, gx - w, gy + w * 1.4f, gx, gy - w * 1.4f, r * 0.035f, c2)
                            Gl.line(c, gx, gy - w * 1.4f, gx + w, gy + w * 1.4f, r * 0.035f, c2)
                            Gl.line(c, gx - w * 0.6f, gy + w * 0.2f, gx + w * 0.6f, gy + w * 0.2f, r * 0.035f, c2)
                        }
                        else -> {
                            Gl.line(c, gx - w, gy - w, gx + w, gy + w, r * 0.035f, c2)
                            Gl.line(c, gx - w, gy + w, gx + w, gy - w, r * 0.035f, c2)
                        }
                    }
                    c.restore()
                }
                c.restore()
                Gl.line(c, x, y - r * 0.2f, x, y + r * 0.2f, r * 0.04f, Gl.white(220))
                Gl.line(c, x - r * 0.14f, y - r * 0.1f, x + r * 0.14f, y + r * 0.1f, r * 0.04f, Gl.white(220))
            }
            75 -> { // alquimia
                val gold = 0xFFFFD54F.toInt()
                c.drawCircle(x, y, r * 0.78f, ps(210, r * 0.04f).also { it.color = Gl.alpha(gold, 210) })
                for (h in 0 until 2) {
                    path.reset()
                    for (i in 0 until 3) {
                        val a = tm * (if (h == 0) 0.8f else -0.8f) + i * 2.0944f + (if (h == 0) -1.5708f else 1.5708f)
                        val px = x + cos(a) * r * 0.66f
                        val py = y + sin(a) * r * 0.66f
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    c.drawPath(path, ps(170, r * 0.03f).also { it.color = Gl.alpha(gold, 170) })
                }
                val orb = intArrayOf(0xFFCFD8DC.toInt(), gold, 0xFFFF8A50.toInt())
                for (k in 0 until 3) {
                    val a = tm * 1.4f + k * 2.0944f
                    val px = x + cos(a) * r * 0.45f
                    val py = y + sin(a) * r * 0.45f
                    Gl.glow(c, px, py, r * 0.25f, orb[k], 110)
                    dot(c, px, py, r * 0.1f, orb[k], 255)
                }
                for (k in 0 until 4) {
                    val u = frac(tm * 0.35f + k * 0.25f)
                    dot(c, x + sin(k * 2.9f) * r * 0.3f, y + r * 0.7f - u * r * 1.4f, r * 0.04f, 0xFFFFF8E1.toInt(), (200 * (1f - u)).toInt())
                }
            }
            76 -> { // gema del tiempo
                val gc = 0xFF4DD0E1.toInt()
                val p = 0.5f + 0.5f * sin(tm * 2f)
                Gl.glow(c, x, y, r * 0.85f, gc, (80 + 60 * p).toInt())
                path.reset()
                path.moveTo(x, y - r * 0.8f)
                path.lineTo(x + r * 0.5f, y)
                path.lineTo(x, y + r * 0.8f)
                path.lineTo(x - r * 0.5f, y)
                path.close()
                c.drawPath(path, pk(Gl.alpha(gc, 90)))
                c.drawPath(path, ps(230, r * 0.045f).also { it.color = Gl.alpha(0xFFB2EBF2.toInt(), 230) })
                Gl.line(c, x - r * 0.5f, y, x + r * 0.5f, y, r * 0.025f, Gl.white(110))
                Gl.line(c, x, y - r * 0.8f, x, y + r * 0.8f, r * 0.025f, Gl.white(70))
                for (k in 0 until 5) {
                    val u = frac(tm * 0.8f + k * 0.2f)
                    dot(c, x + sin(k * 3f) * r * 0.08f, y - r * 0.6f + u * r * 1.2f, r * 0.025f, 0xFFFFF59D.toInt(), 220)
                }
                val a = tm * 2.5f
                Gl.line(c, x, y, x + sin(a) * r * 0.3f, y - cos(a) * r * 0.3f, r * 0.05f, Color.WHITE)
                val a2 = tm * 0.4f
                Gl.line(c, x, y, x + sin(a2) * r * 0.2f, y - cos(a2) * r * 0.2f, r * 0.06f, Gl.alpha(0xFFFFF59D.toInt(), 230))
                dot(c, x, y, r * 0.05f, Color.WHITE, 255)
            }
        }
    }

    // ---- menú de apariencias ----

    private fun drawSkins(c: Canvas) {
        val pal = cur()
        val top = topI
        iconButton(c, "back", 110f, top + 110f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        title(c, "APARIENCIAS", top + 130f, 56f)
        val k0 = effSkin()
        val colNow = pal[(time * 0.8f).toInt() % 4]
        Gl.glow(c, 540f, top + 340f, 300f, colNow, 80)
        val t0 = effTrail()
        if (skinTab == 2) {
            drawTrailDemo(c, t0, 150f, 930f, top + 340f, 80f, 40f, colNow, pal, time, 46f)
            Gl.text(c, Trails.names[t0], 540f, top + 540f, 54f, Gl.light, Gl.white(245), 0.08f)
            Gl.text(c, "El rastro que deja la bola al subir", 540f, top + 595f, 30f, Gl.regular, Gl.white(140))
        } else {
            drawBallAt(c, 540f, top + 340f + sin(time * 2f) * 8f, 100f, colNow, k0, time, true)
            Gl.text(c, Skins.names[k0], 540f, top + 540f, 54f, Gl.light, Gl.white(245), 0.08f)
            Gl.text(c, "La bola conserva su color de juego", 540f, top + 595f, 30f, Gl.regular, Gl.white(140))
        }

        // pestañas: clásicas / animadas
        button(c, "tab0", "Clásicas", 195f, top + 690f, 330f, 84f, if (skinTab == 0) pal[0] else 0, 36f)
        button(c, "tab1", "Animadas", 540f, top + 690f, 330f, 84f, if (skinTab == 1) pal[1] else 0, 36f)
        button(c, "tab2", "Estelas", 885f, top + 690f, 330f, 84f, if (skinTab == 2) pal[2] else 0, 36f)

        val w = 240f
        val h = 262f
        val gy = top + 770f
        val viewBot = vh - 150f
        val tr = skinTab == 2
        val first = if (skinTab == 1) Skins.ANIM_FIRST else 0
        val count = if (tr) Trails.COUNT else if (skinTab == 1) Skins.names.size - Skins.ANIM_FIRST else Skins.ANIM_FIRST
        val rows = (count + 3) / 4
        skinMax = max(0f, rows * (h + 18f) - 18f - (viewBot - gy))
        skinScroll = skinScroll.coerceIn(-170f, skinMax + 170f)
        c.save()
        c.clipRect(0f, gy - 12f, Cfg.W, viewBot)
        for (j in 0 until count) {
            val i = first + j
            val cx = 135f + (j % 4) * 270f
            val cy = gy + h / 2f + (j / 4) * (h + 18f) - skinScroll
            if (cy + h / 2f < gy - 12f || cy - h / 2f > viewBot) continue
            val open = if (tr) trailOpen(i) else skinOpen(i)
            val id = if (tr) "trail:$i" else "skin:$i"
            val pressed = open && pressedId == id
            val sel = if (tr) i == t0 else i == k0
            val s = if (pressed) 0.96f else 1f
            Gl.panel(
                c, cx - w / 2f * s, cy - h / 2f * s, cx + w / 2f * s, cy + h / 2f * s, 48f,
                if (!open) 0.38f else if (sel) 1.35f else 0.9f, if (sel) pal[0] else pal[1]
            )
            if (sel) {
                val pb = Gl.p()
                pb.style = Paint.Style.STROKE
                pb.strokeWidth = 5f
                pb.color = Gl.white(230)
                Gl.rect.set(cx - w / 2f * s, cy - h / 2f * s, cx + w / 2f * s, cy + h / 2f * s)
                c.drawRoundRect(Gl.rect, 48f, 48f, pb)
            }
            val bc = pal[(i + (time * 0.8f).toInt()) % 4]
            if (tr) {
                c.save()
                c.clipRect(cx - w / 2f + 6f, cy - h / 2f + 6f, cx + w / 2f - 6f, cy + h / 2f - 6f)
                drawTrailDemo(c, i, cx - 92f, cx + 92f, cy - 50f, 24f, 22f, bc, pal, time, 19f)
                c.restore()
                Gl.textFit(c, Trails.names[i], cx, cy + if (open) 62f else 32f, 32f, w - 24f, Gl.medium, Gl.white(240))
                if (open) {
                    val ht = max(gy, cy - h / 2f)
                    val hb = min(viewBot, cy + h / 2f)
                    if (hb > ht) hits.add(Hit(id, cx - w / 2f, ht, cx + w / 2f, hb))
                } else {
                    val gp = Gl.p()
                    gp.color = Color.argb(150, 92, 98, 116)
                    Gl.rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                    c.drawRoundRect(Gl.rect, 48f, 48f, gp)
                    drawLock(c, cx, cy + 74f, 0.95f, 235)
                    Gl.text(c, "${Trails.need[i]} estrellas", cx, cy + 118f, 25f, Gl.regular, Gl.white(215))
                }
            } else if (open) {
                drawBallAt(c, cx, cy - 32f, 56f, bc, i, time, false)
                Gl.textFit(c, Skins.names[i], cx, cy + 82f, 34f, w - 24f, Gl.medium, Gl.white(240))
                val ht = max(gy, cy - h / 2f)
                val hb = min(viewBot, cy + h / 2f)
                if (hb > ht) hits.add(Hit(id, cx - w / 2f, ht, cx + w / 2f, hb))
            } else {
                // se ve completa, pero con una capa gris translúcida y un candado: no se puede elegir
                drawBallAt(c, cx, cy - 50f, 52f, bc, i, time, false)
                Gl.textFit(c, Skins.names[i], cx, cy + 36f, 32f, w - 24f, Gl.medium, Gl.white(230))
                val gp = Gl.p()
                gp.color = Color.argb(150, 92, 98, 116)
                Gl.rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                c.drawRoundRect(Gl.rect, 48f, 48f, gp)
                if (Skins.special(i)) {
                    drawLock(c, cx, cy + 62f, 0.8f, 235)
                    Gl.text(c, "${Skins.endNeed(i)} pts infinito", cx, cy + 98f, 23f, Gl.regular, Gl.white(220))
                    Gl.text(c, "o ${Skins.worldNeed(i)} mundos", cx, cy + 123f, 23f, Gl.regular, Gl.white(220))
                } else {
                    drawLock(c, cx, cy + 74f, 0.95f, 235)
                    Gl.text(c, "${Skins.need[i]} estrellas", cx, cy + 118f, 25f, Gl.regular, Gl.white(215))
                }
            }
        }
        c.restore()
        // indicador de desplazamiento
        if (skinMax > 0f) {
            val trackH = viewBot - gy
            val barH = max(60f, trackH * trackH / (trackH + skinMax))
            val by0 = gy + (trackH - barH) * (skinScroll / skinMax).coerceIn(0f, 1f)
            Gl.rect.set(Cfg.W - 10f, by0, Cfg.W - 4f, by0 + barH)
            c.drawRoundRect(Gl.rect, 3f, 3f, pw(70))
        }
        Gl.text(
            c,
            if (skinTab == 1) "Infinito ${prefs.endlessBest}  ·  Mundos ${prefs.worldsDone()}/${Levels.WORLDS}" else "Estrellas ${prefs.totalStars()}/${Levels.PLAYABLE * 3}",
            540f, vh - 90f,
            34f, Gl.regular, Gl.white(150)
        )
    }


    // ======================================================================
    // Editor de niveles (solo versión de desarrollador)
    // ======================================================================

    private val edTypeNames = arrayOf(
        "Anillo", "Doble", "Barras", "Cuadrado", "Triángulo", "Pinchos", "Péndulo", "Cruz", "Compuerta", "Órbita",
        "Contrabarras", "Arco", "Pulso", "Hexágono", "Láser", "Puerta lateral", "Franja", "Sierras", "Pilar"
    )
    // etiquetas de tamaño / interior / auxiliar según el tipo (null = no se usa)
    private val edLbl: Array<Array<String?>> = arrayOf(
        arrayOf("Radio", null, null), arrayOf("Radio ext.", "Radio int.", null), arrayOf(null, null, null),
        arrayOf("Tamaño", null, null), arrayOf("Tamaño", null, null), arrayOf("Radio", null, "Pinchos"),
        arrayOf("Amplitud", null, "Largo"), arrayOf("Radio", null, "Radio centro"), arrayOf("Amplitud", null, "Hueco"),
        arrayOf("Radio ext.", "Radio int.", null), arrayOf(null, null, "Separación"), arrayOf("Radio", null, "Hueco °"),
        arrayOf(null, null, null), arrayOf("Tamaño", null, null), arrayOf(null, null, "Encendido"),
        arrayOf("Amplitud", "Centro", "Hueco"), arrayOf("Largo", null, null), arrayOf("Recorrido", "Cantidad", "Radio"),
        arrayOf("Amplitud", null, "Ancho")
    )
    private val edTplNames = arrayOf(
        "Tres anillos", "Barras", "Pinchos y cruz", "Formas", "Órbitas y arco", "Rayos", "Compuertas", "Zona de deslizar", "Viento y sombras"
    )

    private fun edPanelOpenEff(): Boolean = edPanelOpen && !edOverview
    private fun edViewTop(): Float = topI + 150f
    private fun edPanelTop(): Float = if (edPanelOpenEff()) vh - 860f else vh - 190f
    private fun edViewH(): Float = edPanelTop() - edViewTop()
    private fun edMaxCam(): Float = 300f - edViewH()
    private fun edMinCam(): Float = min(edMaxCam(), (edData?.finishY ?: -3000f) - 400f)
    private fun edListTop(): Float = edPanelTop() + 205f
    private fun edListBot(): Float = vh - 190f

    private fun edToast(s: String) {
        edMsg = s
        edMsgT = time
    }

    private fun edPush() {
        val d = edData ?: return
        edUndo.add(d.serialize())
        if (edUndo.size > 60) edUndo.removeAt(0)
        edRedo.clear()
    }

    private fun edRestore(s: String) {
        LevelData.parse(s)?.let { edData = it }
        edKind = -1
        edIdx = -1
        edDirty = true
    }

    /** Valores iniciales razonables para cada tipo de obstáculo. */
    private fun edDefault(type: Int, y: Float, n0: Int): Ob {
        var n = n0
        var speed = 55f
        var size = 300f
        var inner = 0f
        var aux = 0f
        when (type) {
            T.RING -> { size = 300f }
            T.DOUBLE -> { size = 345f; inner = 195f }
            T.BARS -> { speed = 200f; size = 0f }
            T.SQUARE -> { size = 240f }
            T.TRI -> { size = 370f }
            T.SPIKES -> { size = 330f; aux = 6f }
            T.PEND -> { speed = 95f; size = 330f; aux = 225f }
            T.CROSS -> { size = 290f; aux = 95f }
            T.GATE -> { speed = 90f; size = 330f; aux = 155f }
            T.ORBIT -> { size = 270f; inner = 130f }
            T.COUNTER -> { speed = 200f; size = 0f; aux = 120f }
            T.ARC -> { size = 300f; aux = 110f }
            T.PULSE -> { speed = 0.7f; size = 0f }
            T.HEX -> { size = 295f }
            T.LASER -> { speed = 0.4f; size = 0f; aux = 0.47f }
            T.SGATE -> { speed = 60f; size = 100f; aux = 155f; inner = 290f; n = 4 }
            T.CBAND -> { speed = 170f; size = 320f; n = 4 }
            T.SAW -> { speed = 100f; size = 360f; aux = 60f; inner = 2f; n = 4 }
            T.PILLAR -> { speed = 60f; size = 100f; aux = 145f; n = 4 }
        }
        return Ob(type, y, n, 0, speed, 0f, size, inner, aux)
    }

    private fun openEditor(l: Int) {
        if (!BuildConfig.DEV || !Levels.isPlayable(l)) return
        edLevel = l
        level = l
        world = Levels.world(l)
        edData = Course.level(l).toData()
        edKind = -1
        edIdx = -1
        edDirty = false
        edConfirm = false
        edScroll = 0f
        edMenu = 0
        edOverview = false
        edUndo.clear()
        edRedo.clear()
        edSpeedAcc = 1f
        edCam = edMaxCam()
        scr = Scr.EDITOR
    }

    private fun edBack() {
        if (edMenu != 0) { edMenu = 0; return }
        if (edOverview) { edOverview = false; return }
        if (edDirty && !edConfirm) {
            edConfirm = true
            edToast("Cambios sin guardar. Pulsa atrás otra vez para salir")
            return
        }
        edConfirm = false
        levelPage = Levels.world(edLevel)
        scr = Scr.LEVELS
    }

    private fun edSetOb(n: Ob) {
        val d = edData ?: return
        if (edKind != 0 || edIdx !in d.obs.indices) return
        d.obs[edIdx] = n
        d.obs.sortByDescending { it.y }
        edIdx = d.obs.indexOfFirst { it === n }
        edDirty = true
        edConfirm = false
    }

    private fun edSetSw(n: Sw) {
        val d = edData ?: return
        if (edKind != 1 || edIdx !in d.sws.indices) return
        d.sws[edIdx] = n
        d.sws.sortByDescending { it.y }
        edIdx = d.sws.indexOfFirst { it === n }
        edDirty = true
        edConfirm = false
    }

    private fun edSetZone(n: Zone) {
        val d = edData ?: return
        if (edKind != 2 || edIdx !in d.zones.indices) return
        val nz = if (n.top > n.bottom - 100f) Zone(n.bottom, n.bottom - 100f) else n
        d.zones[edIdx] = nz
        d.zones.sortByDescending { it.bottom }
        edIdx = d.zones.indexOfFirst { it === nz }
        edDirty = true
        edConfirm = false
    }

    private fun edSnap(v: Float): Float = Math.round(v / 10f) * 10f

    private fun edPick(wy: Float, scale: Float = 1f) {
        val d = edData ?: return
        edKind = -1
        edIdx = -1
        var bd = 1e9f
        for (i in d.sws.indices) {
            val dd = abs(wy - d.sws[i].y)
            if (dd <= 50f / scale && dd < bd) { bd = dd; edKind = 1; edIdx = i }
        }
        if (edKind == 1) return
        for (i in d.obs.indices) {
            val o = d.obs[i]
            val dd = abs(wy - o.y)
            if (dd <= max(o.reach, 60f / scale) && dd < bd) { bd = dd; edKind = 0; edIdx = i }
        }
        if (edKind == 0) return
        for (i in d.zones.indices) {
            val z = d.zones[i]
            if (wy <= z.bottom && wy >= z.top) { edKind = 2; edIdx = i; return }
        }
    }

    private fun edTouchDown(y: Float) {
        edDrag = 0
        edDragPushed = false
        if (edMenu != 0) return
        val vt = edViewTop()
        val pt = edPanelTop()
        if (y in vt..pt) {
            if (edOverview) { edDrag = 4; return }
            edDrag = 1
            val d = edData ?: return
            if (edKind == 0 && edIdx in d.obs.indices) {
                val o = d.obs[edIdx]
                if (abs(edCam + (y - vt) - o.y) <= max(o.reach, 50f)) {
                    edDrag = 2
                    edDragY0 = o.y
                }
            }
        } else if (edPanelOpenEff() && y >= edListTop() && y <= edListBot()) {
            edDrag = 3
        }
    }

    private fun edTouchDrag(y: Float) {
        when (edDrag) {
            1 -> edCam -= (y - lastY)
            2 -> {
                val d = edData ?: return
                if (edKind == 0 && edIdx in d.obs.indices) {
                    if (!edDragPushed) {
                        // el estado anterior al arrastre (la posición original) queda en el historial
                        val old = d.obs[edIdx].copy(y = edDragY0)
                        val tmp = d.copy()
                        tmp.obs[edIdx] = old
                        edUndo.add(tmp.serialize())
                        if (edUndo.size > 60) edUndo.removeAt(0)
                        edRedo.clear()
                        edDragPushed = true
                    }
                    edSetOb(d.obs[edIdx].copy(y = edSnap(edDragY0 + (y - downY))))
                }
            }
            3 -> edScroll -= (y - lastY)
            4 -> edCam = edOvY0 + (y - edViewTop()) / edOvS - 600f
        }
    }

    /** Pone en `d` el primer color presente en la máscara. */
    private fun edFirstBit(mask: Int): Int {
        for (k in 0 until 4) if ((mask and (1 shl k)) != 0) return k
        return 0
    }

    /** Recalcula los cambiadores de color para que cada obstáculo tenga siempre un color cruzable. */
    private fun edAutoColors() {
        val d = edData ?: return
        d.sortAll()
        d.sws.clear()
        if (d.obs.isEmpty()) return
        var cur = d.startColor
        if ((d.obs[0].present and (1 shl cur)) == 0) cur = edFirstBit(d.obs[0].present)
        d.startColor = cur
        var prevTop = -Cfg.BALL_R
        for ((i, o) in d.obs.withIndex()) {
            val bottom = o.y + o.reach
            if (i > 0 && (o.present and (1 shl cur)) == 0) {
                val target = edFirstBit(o.present)
                d.sws.add(Sw((prevTop + bottom) / 2f, target))
                cur = target
            }
            prevTop = o.y - o.reach
        }
        d.sws.sortByDescending { it.y }
        edDirty = true
    }

    private fun edTemplate(k: Int): List<Ob> {
        fun o(t: Int, n: Int = 3, f: (Ob) -> Ob = { it }): Ob = f(edDefault(t, 0f, n))
        return when (k) {
            0 -> listOf(o(T.RING), o(T.RING) { it.copy(speed = -it.speed) }, o(T.RING, 4))
            1 -> listOf(o(T.BARS), o(T.COUNTER), o(T.BARS) { it.copy(speed = -it.speed * 1.1f) })
            2 -> listOf(o(T.SPIKES), o(T.CROSS), o(T.PEND))
            3 -> listOf(o(T.SQUARE), o(T.TRI), o(T.HEX))
            4 -> listOf(o(T.ORBIT), o(T.ARC), o(T.RING) { it.copy(wob = 30f) })
            5 -> listOf(o(T.LASER), o(T.PULSE), o(T.LASER) { it.copy(speed = 0.45f) })
            6 -> listOf(o(T.GATE), o(T.PEND), o(T.GATE) { it.copy(speed = -it.speed) })
            7 -> listOf(o(T.SGATE), o(T.CBAND), o(T.SAW), o(T.PILLAR))
            else -> listOf(o(T.RING) { it.copy(drift = 1400f) }, o(T.SQUARE) { it.copy(drift = -1400f) }, o(T.HEX) { it.copy(fade = true) })
        }
    }

    /** Inserta una plantilla en el centro de la vista; empuja hacia arriba lo que haya encima para hacerle espacio. */
    private fun edInsertTemplate(k: Int) {
        val d = edData ?: return
        edPush()
        val y0 = edSnap(edCam + edViewH() / 2f)
        val specs = edTemplate(k)
        val slide = specs.any { T.isSlide(it.type) }
        val placed = ArrayList<Ob>()
        val newSw = ArrayList<Sw>()
        var prevTop = y0 + 120f
        for (s in specs) {
            val oy = edSnap(prevTop - 420f - s.reach)
            val ob = s.copy(y = oy)
            if (!slide) newSw.add(Sw(edSnap((prevTop + oy + ob.reach) / 2f), edFirstBit(ob.present)))
            placed.add(ob)
            prevTop = oy - ob.reach
        }
        var zone: Zone? = null
        if (slide) zone = Zone(placed.first().y + placed.first().reach + 130f, placed.last().y - placed.last().reach - 140f)
        val topEdge = if (zone != null) zone.top else prevTop
        val shift = (y0 - topEdge) + 450f
        // lo que estaba por encima del punto de inserción sube para hacer espacio
        for (i in d.obs.indices) if (d.obs[i].y < y0 - 5f) d.obs[i] = d.obs[i].copy(y = d.obs[i].y - shift)
        for (i in d.sws.indices) if (d.sws[i].y < y0 - 5f) d.sws[i] = Sw(d.sws[i].y - shift, d.sws[i].toColor)
        for (i in d.zones.indices) if (d.zones[i].bottom < y0 - 5f) d.zones[i] = Zone(d.zones[i].bottom - shift, d.zones[i].top - shift)
        d.finishY -= shift
        d.obs.addAll(placed)
        d.sws.addAll(newSw)
        if (zone != null) d.zones.add(zone)
        d.sortAll()
        edKind = 0
        edIdx = d.obs.indexOfFirst { it === placed.last() }
        edDirty = true
        edCam = edSnap(y0 - edViewH() * 0.6f)
        edToast("Plantilla añadida: ${edTplNames[k]}")
    }

    private fun edActivate(id: String) {
        val d = edData ?: return
        when {
            id == "edmenu" -> edMenu = if (edMenu == 0) 1 else 0
            id == "edmenux" -> edMenu = 0
            id == "edcont" -> { edMenu = 0; edOverview = false }
            id == "edmap" -> { edMenu = 0; edOverview = !edOverview }
            id == "edtpl" || id == "edtplb" -> edMenu = 2
            id == "edtpb" -> edMenu = 1
            id.startsWith("edtp:") -> { edMenu = 0; edInsertTemplate(id.substring(5).toIntOrNull() ?: return) }
            id == "edpanel" -> edPanelOpen = !edPanelOpen
            id == "edstart" -> { edMenu = 0; edOverview = false; edCam = edMaxCam() }
            id == "edgoal" -> { edMenu = 0; edOverview = false; edCam = d.finishY - 300f }
            id == "edundo" -> {
                edMenu = 0
                if (edUndo.isEmpty()) { edToast("Nada que deshacer"); return }
                edRedo.add(d.serialize())
                edRestore(edUndo.removeAt(edUndo.size - 1))
                edToast("Deshecho")
            }
            id == "edredo" -> {
                edMenu = 0
                if (edRedo.isEmpty()) { edToast("Nada que rehacer"); return }
                edUndo.add(d.serialize())
                edRestore(edRedo.removeAt(edRedo.size - 1))
                edToast("Rehecho")
            }
            id == "edauto" -> { edMenu = 0; edPush(); edAutoColors(); edToast("Cambiadores de color recalculados") }
            id == "edexit" -> { edMenu = 0; edBack() }
            id == "edaddo" -> {
                edPush()
                val y = edSnap(edCam + edViewH() / 2f)
                val o = edDefault(T.RING, y, 3)
                d.obs.add(o)
                d.obs.sortByDescending { it.y }
                edKind = 0
                edIdx = d.obs.indexOfFirst { it === o }
                edDirty = true
                edScroll = 0f
            }
            id == "edadds" -> {
                edPush()
                val y = edSnap(edCam + edViewH() / 2f)
                val s = Sw(y, (d.startColor + 1) % 4)
                d.sws.add(s)
                d.sws.sortByDescending { it.y }
                edKind = 1
                edIdx = d.sws.indexOfFirst { it === s }
                edDirty = true
                edScroll = 0f
            }
            id == "edaddz" -> {
                edPush()
                val y = edSnap(edCam + edViewH() / 2f)
                val z = Zone(y + 300f, y - 300f)
                d.zones.add(z)
                d.zones.sortByDescending { it.bottom }
                edKind = 2
                edIdx = d.zones.indexOfFirst { it === z }
                edDirty = true
                edScroll = 0f
            }
            id == "eddup" -> {
                if (edKind < 0) return
                edPush()
                if (edKind == 0 && edIdx in d.obs.indices) {
                    val o = d.obs[edIdx]
                    val n = o.copy(y = edSnap(o.y - o.reach * 2f - 80f))
                    d.obs.add(n)
                    d.obs.sortByDescending { it.y }
                    edIdx = d.obs.indexOfFirst { it === n }
                } else if (edKind == 1 && edIdx in d.sws.indices) {
                    val s = d.sws[edIdx]
                    val n = Sw(edSnap(s.y - 160f), (s.toColor + 1) % 4)
                    d.sws.add(n)
                    d.sws.sortByDescending { it.y }
                    edIdx = d.sws.indexOfFirst { it === n }
                } else if (edKind == 2 && edIdx in d.zones.indices) {
                    val z = d.zones[edIdx]
                    val n = Zone(z.top - 100f, z.top - 100f - (z.bottom - z.top))
                    d.zones.add(n)
                    d.zones.sortByDescending { it.bottom }
                    edIdx = d.zones.indexOfFirst { it === n }
                }
                edDirty = true
            }
            id == "eddel" -> {
                if (edKind < 0) return
                if (edKind == 0 && d.obs.size <= 1) { edToast("Debe quedar al menos un obstáculo"); return }
                edPush()
                if (edKind == 0 && edIdx in d.obs.indices) d.obs.removeAt(edIdx)
                else if (edKind == 1 && edIdx in d.sws.indices) d.sws.removeAt(edIdx)
                else if (edKind == 2 && edIdx in d.zones.indices) d.zones.removeAt(edIdx)
                edDirty = true
                edKind = -1
                edIdx = -1
            }
            id == "edsave" -> {
                edMenu = 0
                d.sortAll()
                val s = d.serialize()
                prefs.setCustom(edLevel, s)
                LevelData.parse(s)?.let { Custom.map[edLevel] = it }
                edDirty = false
                edConfirm = false
                edToast("Nivel $edLevel guardado")
            }
            id == "edorig" -> {
                edMenu = 0
                edPush()
                prefs.setCustom(edLevel, null)
                Custom.map.remove(edLevel)
                edData = Course.level(edLevel).toData()
                edKind = -1
                edIdx = -1
                edDirty = false
                edOverview = false
                edCam = edMaxCam()
                edToast("Nivel original restaurado")
            }
            id == "edtest" -> {
                edMenu = 0
                d.sortAll()
                if (d.obs.isEmpty()) return
                level = edLevel
                deaths = 0
                beginPlay(Course.custom(edLevel, d), Mode.LEVEL)
                edTesting = true
            }
            id.startsWith("edm:") || id.startsWith("edp:") -> {
                val k = id.substring(4).toIntOrNull() ?: return
                if (k !in edRows.indices) return
                val r = edRows[k]
                if (k > 0) edPush()
                if (id[2] == 'm') r.minus?.invoke() else r.plus?.invoke()
                if (k > 0) edDirty = true
            }
        }
    }

    private fun edFmt(v: Float): String = if (abs(v) < 10f && v != Math.floor(v.toDouble()).toFloat()) String.format(java.util.Locale.US, "%.2f", v) else v.toInt().toString()

    private fun edBuildRows() {
        edRows.clear()
        val d = edData ?: return
        val st = edStep.toFloat()
        edRows.add(EdRow("Paso", "×$edStep", { edStep = if (edStep == 1) 5 else 1 }, { edStep = if (edStep == 1) 5 else 1 }))
        if (edKind == 0 && edIdx in d.obs.indices) {
            val o = d.obs[edIdx]
            val allowed = (0 until T.COUNT).filter { it != T.DOUBLE }
            fun cycle(dir: Int) {
                val i = allowed.indexOf(o.type).let { if (it < 0) 0 else it }
                val t = allowed[(i + dir + allowed.size) % allowed.size]
                edSetOb(edDefault(t, o.y, o.n))
            }
            edRows.add(EdRow("Tipo", edTypeNames[o.type], { cycle(-1) }, { cycle(1) }))
            edRows.add(EdRow("Altura", edFmt(-o.y), { edSetOb(o.copy(y = o.y + 10f * st)) }, { edSetOb(o.copy(y = o.y - 10f * st)) }))
            edRows.add(EdRow("Colores", o.n.toString(),
                { val n = max(2, o.n - 1); edSetOb(o.copy(n = n, shift = o.shift % n)) },
                { val n = min(4, o.n + 1); edSetOb(o.copy(n = n, shift = o.shift % n)) }))
            edRows.add(EdRow("Desfase color", o.shift.toString(),
                { edSetOb(o.copy(shift = (o.shift + o.n - 1) % o.n)) }, { edSetOb(o.copy(shift = (o.shift + 1) % o.n)) }))
            val small = o.type == T.PULSE || o.type == T.LASER
            val sp = if (small) 0.02f * st else 5f * st
            edRows.add(EdRow("Velocidad", edFmt(o.speed), { edSetOb(o.copy(speed = o.speed - sp)) }, { edSetOb(o.copy(speed = o.speed + sp)) }))
            edRows.add(EdRow("Fase °", edFmt(o.phase), { edSetOb(o.copy(phase = (o.phase - 15f * st + 3600f) % 360f)) }, { edSetOb(o.copy(phase = (o.phase + 15f * st) % 360f)) }))
            val lb = edLbl[o.type]
            lb[0]?.let { l -> edRows.add(EdRow(l, edFmt(o.size), { edSetOb(o.copy(size = max(0f, o.size - 10f * st))) }, { edSetOb(o.copy(size = o.size + 10f * st)) })) }
            lb[1]?.let { l ->
                val s2 = if (o.type == T.SAW) 1f else 10f
                edRows.add(EdRow(l, edFmt(o.inner), { edSetOb(o.copy(inner = o.inner - s2 * st)) }, { edSetOb(o.copy(inner = o.inner + s2 * st)) }))
            }
            lb[2]?.let { l ->
                val s3 = if (o.type == T.LASER) 0.02f else if (o.type == T.SPIKES) 1f else 5f
                edRows.add(EdRow(l, edFmt(o.aux), { edSetOb(o.copy(aux = max(0f, o.aux - s3 * st))) }, { edSetOb(o.copy(aux = o.aux + s3 * st)) }))
            }
            edRows.add(EdRow("Corriente aire", edFmt(o.drift), { edSetOb(o.copy(drift = o.drift - 100f * st)) }, { edSetOb(o.copy(drift = o.drift + 100f * st)) }))
            edRows.add(EdRow("Desvanece", if (o.fade) "Sí" else "No", { edSetOb(o.copy(fade = !o.fade)) }, { edSetOb(o.copy(fade = !o.fade)) }))
            edRows.add(EdRow("Vaivén °", edFmt(o.wob), { edSetOb(o.copy(wob = max(0f, o.wob - 5f * st))) }, { edSetOb(o.copy(wob = o.wob + 5f * st)) }))
            val hasZone = d.zones.any { o.y <= it.bottom && o.y >= it.top }
            fun zoneToggle() {
                if (hasZone) d.zones.removeAll { o.y <= it.bottom && o.y >= it.top }
                else d.zones.add(Zone(o.y + o.reach + 130f, o.y - o.reach - 130f))
                d.zones.sortByDescending { it.bottom }
            }
            edRows.add(EdRow("Zona deslizar", if (hasZone) "Sí" else "No", { zoneToggle() }, { zoneToggle() }))
        } else if (edKind == 1 && edIdx in d.sws.indices) {
            val s = d.sws[edIdx]
            edRows.add(EdRow("Cambiador", "Color ${s.toColor + 1}", { edSetSw(Sw(s.y, (s.toColor + 3) % 4)) }, { edSetSw(Sw(s.y, (s.toColor + 1) % 4)) }))
            edRows.add(EdRow("Altura", edFmt(-s.y), { edSetSw(Sw(s.y + 10f * st, s.toColor)) }, { edSetSw(Sw(s.y - 10f * st, s.toColor)) }))
        } else if (edKind == 2 && edIdx in d.zones.indices) {
            val z = d.zones[edIdx]
            edRows.add(EdRow("Zona: abajo", edFmt(-z.bottom), { edSetZone(Zone(z.bottom + 10f * st, z.top)) }, { edSetZone(Zone(z.bottom - 10f * st, z.top)) }))
            edRows.add(EdRow("Zona: arriba", edFmt(-z.top), { edSetZone(Zone(z.bottom, z.top + 10f * st)) }, { edSetZone(Zone(z.bottom, z.top - 10f * st)) }))
        } else {
            edRows.add(EdRow("Toca un elemento", "", null, null))
        }
        edRows.add(EdRow("— Nivel —", "", null, null))
        edRows.add(EdRow("Color inicial", "Color ${d.startColor + 1}", { d.startColor = (d.startColor + 3) % 4 }, { d.startColor = (d.startColor + 1) % 4 }))
        edRows.add(EdRow("Largo (meta)", edFmt(-d.finishY), { d.finishY += 40f * st }, { d.finishY -= 40f * st }))
        val nPal = Worlds.colors.size
        edRows.add(EdRow("Colores mundo", if (d.palette < 0) "Auto" else "${d.palette + 1}",
            { d.palette = if (d.palette < 0) nPal - 1 else d.palette - 1 }, { d.palette = if (d.palette >= nPal - 1) -1 else d.palette + 1 }))
        fun scaleSpeed(f: Float) {
            for (i in d.obs.indices) d.obs[i] = d.obs[i].copy(speed = d.obs[i].speed * f)
            edSpeedAcc *= f
        }
        edRows.add(EdRow("Velocidad global", "×" + String.format(java.util.Locale.US, "%.2f", edSpeedAcc), { scaleSpeed(1f / 1.1f) }, { scaleSpeed(1.1f) }))
        edRows.add(EdRow("Auto cambiadores", "Aplicar", { edAutoColors() }, { edAutoColors() }))
    }

    private fun drawEdWorld(c: Canvas, d: LevelData, pal: IntArray, y0: Float, y1: Float, detail: Boolean) {
        drawFinish(c, d.finishY, pal)
        Gl.line(c, 140f, 0f, 940f, 0f, 3f, Gl.white(120))
        if (detail) Gl.text(c, "INICIO", 540f, 40f, 30f, Gl.medium, Gl.white(150), 0.2f)
        edDot(c, Cfg.W / 2f, 0f, Cfg.BALL_R, pal[d.startColor % 4])
        for ((i, z) in d.zones.withIndex()) {
            if (z.bottom < y0 - 40f || z.top > y1 + 40f) continue
            drawZone(c, z, pal)
            if (edKind == 2 && edIdx == i) {
                val pp = Gl.p()
                pp.style = Paint.Style.STROKE
                pp.strokeWidth = 5f
                pp.color = Gl.white(220)
                pp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(26f, 16f), (time * 40f) % 42f)
                c.drawRect(8f, z.top, Cfg.W - 8f, z.bottom, pp)
            }
        }
        val nColors = if (d.palette >= 0 || true) Levels.colors(edLevel) else 4
        for (s in d.sws) {
            if (s.y < y0 - 100f || s.y > y1 + 100f) continue
            drawSwitcherN(c, s, nColors, pal)
        }
        for ((i, o) in d.obs.withIndex()) {
            if (o.y + o.reach < y0 - 40f || o.y - o.reach > y1 + 40f) continue
            drawObstacle(c, o, time, pal)
            if (!T.isSlide(o.type)) drawStar(c, Cfg.W / 2f, o.y)
            if (detail) Gl.text(c, "${i + 1}", 36f, o.y + 10f, 30f, Gl.medium, Gl.white(150))
        }
        if (edKind == 0 && edIdx in d.obs.indices) {
            val o = d.obs[edIdx]
            val pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 4f
            pp.color = Gl.white(210)
            pp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(22f, 14f), (time * 40f) % 36f)
            c.drawRect(8f, o.y - o.reach, Cfg.W - 8f, o.y + o.reach, pp)
            if (detail) Gl.text(c, "${edIdx + 1} · ${edTypeNames[o.type]}", 24f, o.y - o.reach - 14f, 32f, Gl.medium, Color.WHITE, 0f, null, 0, Paint.Align.LEFT)
        } else if (edKind == 1 && edIdx in d.sws.indices) {
            val s = d.sws[edIdx]
            val pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 5f
            pp.color = Gl.white(230)
            c.drawCircle(Cfg.W / 2f, s.y, Cfg.SW_R + 18f, pp)
        }
    }

    private fun drawEditor(c: Canvas) {
        val d = edData ?: return
        val wIdx = if (d.palette >= 0) d.palette else Levels.world(edLevel)
        val pal = Worlds.colors[wIdx.coerceIn(0, Worlds.colors.size - 1)]
        val vt = edViewTop()
        val pt = edPanelTop()
        val vH = pt - vt
        if (d.finishY > -400f) d.finishY = -400f
        edCam = edCam.coerceIn(edMinCam(), edMaxCam())

        // ---- mundo ----
        c.save()
        c.clipRect(0f, vt, Cfg.W, pt)
        val bgp = Gl.p()
        bgp.color = Color.argb(110, 0, 0, 0)
        c.drawRect(0f, vt, Cfg.W, pt, bgp)
        if (edOverview) {
            val y0 = d.finishY - 300f
            val y1 = 260f
            val s = min(0.5f, vH / (y1 - y0))
            edOvS = s
            edOvY0 = y0
            c.translate(Cfg.W / 2f, vt)
            c.scale(s, s)
            c.translate(-Cfg.W / 2f, -y0)
            drawEdWorld(c, d, pal, y0, y1, false)
            // ventana que se ve al volver a editar
            val normalH = (vh - 860f) - vt
            val pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 6f / s
            pp.color = Gl.white(230)
            c.drawRect(4f, edCam, Cfg.W - 4f, edCam + normalH, pp)
            val fp = Gl.p()
            fp.color = Gl.white(28)
            c.drawRect(4f, edCam, Cfg.W - 4f, edCam + normalH, fp)
        } else {
            c.translate(0f, vt - edCam)
            drawEdWorld(c, d, pal, edCam, edCam + vH, true)
        }
        c.restore()

        // ---- cabecera ----
        iconButton(c, "back", 110f, topI + 90f, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        Gl.textFit(c, "EDITOR · NIVEL $edLevel" + if (edDirty) " *" else "", 400f, topI + 108f, 40f, 400f, Gl.medium, Gl.white(240), 0.08f)
        button(c, "edmenu", if (edMenu != 0) "Menú ▴" else "Menú ▾", 870f, topI + 90f, 300f, 76f, pal[1], 34f)
        if (edOverview) {
            Gl.text(c, "Mapa completo: toca o arrastra para elegir", 540f, vt + 50f, 30f, Gl.medium, Gl.white(200))
            button(c, "edcont", "Seguir editando aquí", 540f, pt - 70f, 560f, 84f, pal[0], 36f)
        }
        if (time - edMsgT < 2.2f) {
            val a = (255 * (1f - ((time - edMsgT - 1.6f) / 0.6f).coerceIn(0f, 1f))).toInt()
            Gl.panel(c, 90f, vt + 70f, 990f, vt + 156f, 40f, 1f, pal[1])
            Gl.textFit(c, edMsg, 540f, vt + 126f, 34f, 840f, Gl.medium, Gl.white(a))
        }

        // ---- panel ----
        Gl.panel(c, 24f, pt, Cfg.W - 24f, vh - 24f, 56f, 0.9f, pal[0])
        if (edPanelOpenEff()) {
            val hasSel = edKind >= 0
            button(c, "edaddo", "+ Obstáculo", 169f, pt + 58f, 235f, 80f, 0, 28f)
            button(c, "edadds", "+ Cambiador", 416f, pt + 58f, 235f, 80f, 0, 28f)
            button(c, "edaddz", "+ Zona", 663f, pt + 58f, 235f, 80f, 0, 28f)
            button(c, "edtplb", "Plantillas", 910f, pt + 58f, 235f, 80f, pal[2], 28f)
            button(c, "eddup", "Duplicar", 169f, pt + 146f, 235f, 80f, 0, 28f, hasSel)
            button(c, "eddel", "Borrar", 416f, pt + 146f, 235f, 80f, 0, 28f, hasSel)
            button(c, "edundo", "Deshacer", 663f, pt + 146f, 235f, 80f, 0, 28f, edUndo.isNotEmpty())
            button(c, "edredo", "Rehacer", 910f, pt + 146f, 235f, 80f, 0, 28f, edRedo.isNotEmpty())

            edBuildRows()
            val lt = edListTop()
            val lb = edListBot()
            val rh = 86f
            val maxScroll = max(0f, edRows.size * rh - (lb - lt))
            edScroll = edScroll.coerceIn(0f, maxScroll)
            c.save()
            c.clipRect(40f, lt, Cfg.W - 40f, lb)
            for ((i, r) in edRows.withIndex()) {
                val ry = lt + i * rh + rh / 2f - edScroll
                if (ry - rh / 2f < lt - 1f || ry + rh / 2f > lb + 1f) continue
                if (r.minus == null && r.plus == null) {
                    Gl.textFit(c, r.label, 540f, ry + 12f, 32f, 700f, Gl.medium, Gl.white(170), 0.05f)
                    continue
                }
                Gl.textFit(c, r.label, 70f, ry + 12f, 34f, 330f, Gl.regular, Gl.white(215), 0f, Paint.Align.LEFT)
                Gl.textFit(c, r.value, 600f, ry + 12f, 36f, 250f, Gl.medium, Gl.white(245))
                button(c, "edm:$i", "−", 790f, ry, 120f, 70f, 0, 44f)
                button(c, "edp:$i", "+", 940f, ry, 120f, 70f, 0, 44f)
            }
            c.restore()
            if (maxScroll > 0f) {
                val th = lb - lt
                val bh = max(50f, th * th / (th + maxScroll))
                val by0 = lt + (th - bh) * (edScroll / maxScroll)
                Gl.rect.set(Cfg.W - 38f, by0, Cfg.W - 32f, by0 + bh)
                c.drawRoundRect(Gl.rect, 3f, 3f, pw(80))
            }
        }
        button(c, "edpanel", if (edPanelOpenEff()) "Ocultar" else "Herramientas", 190f, vh - 105f, 300f, 84f, 0, 32f, !edOverview)
        button(c, "edtest", "Probar", 540f, vh - 105f, 300f, 84f, pal[2], 34f)
        button(c, "edsave", "Guardar", 890f, vh - 105f, 300f, 84f, pal[1], 34f)

        if (edMenu != 0) drawEdMenu(c, pal)
    }

    private fun drawEdMenu(c: Canvas, pal: IntArray) {
        val dim = Gl.p()
        dim.color = Color.argb(150, 0, 0, 0)
        c.drawRect(0f, 0f, Cfg.W, vh, dim)
        hits.add(Hit("edmenux", 0f, 0f, Cfg.W, vh))
        val items = ArrayList<Pair<String, String>>()
        if (edMenu == 1) {
            items.add("edcont" to "Seguir editando")
            items.add("edmap" to (if (edOverview) "Vista normal" else "Ver mapa completo"))
            items.add("edtpl" to "Plantillas")
            items.add("edauto" to "Auto cambiadores de color")
            items.add("edstart" to "Ir al inicio")
            items.add("edgoal" to "Ir a la meta")
            items.add("edundo" to "Deshacer")
            items.add("edredo" to "Rehacer")
            items.add("edtest" to "Probar nivel")
            items.add("edsave" to "Guardar")
            items.add("edorig" to "Restaurar original")
            items.add("edexit" to "Salir del editor")
        } else {
            for (i in edTplNames.indices) items.add("edtp:$i" to edTplNames[i])
            items.add("edtpb" to "Volver")
        }
        val y0 = topI + 200f
        val step = 92f
        Gl.panel(c, 320f, y0 - 40f, 1040f, y0 + items.size * step + 4f, 56f, 1f, pal[1])
        for ((i, it) in items.withIndex()) {
            val enabled = when (it.first) {
                "edundo" -> edUndo.isNotEmpty()
                "edredo" -> edRedo.isNotEmpty()
                else -> true
            }
            button(c, it.first, it.second, 680f, y0 + i * step + 30f, 660f, 80f, 0, 34f, enabled)
        }
    }

    private fun edDot(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        val pp = Gl.p()
        pp.color = Gl.alpha(col, 230)
        c.drawCircle(x, y, r, pp)
        val p2 = Gl.p()
        p2.style = Paint.Style.STROKE
        p2.strokeWidth = 3f
        p2.color = Gl.white(190)
        c.drawCircle(x, y, r, p2)
    }

    private fun drawParts(c: Canvas) {
        for (s in parts) {
            val a = (s.life / s.maxLife).coerceIn(0f, 1f)
            var pp = Gl.p()
            pp.color = Gl.alpha(s.color, (a * 230f).toInt())
            c.drawCircle(s.x, s.y, s.size * (0.5f + 0.5f * a), pp)
            pp = Gl.p()
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 1.5f
            pp.color = Gl.white((a * 160f).toInt())
            c.drawCircle(s.x, s.y, s.size * (0.5f + 0.5f * a), pp)
        }
    }

    // ======================================================================
    // Interfaz de la partida
    // ======================================================================

    private fun clock(sec: Float): String {
        val s = max(0, ceil(sec).toInt())
        val mm = s / 60
        val ss = s % 60
        return mm.toString() + ":" + (if (ss < 10) "0" else "") + ss
    }

    private fun drawPlayUi(c: Canvas) {
        val sm = sim ?: return
        val cr = course ?: return
        val pal = cur()
        val m = match
        val top = topI + 30f
        val cy = top + 76f

        val canPause = if (mode == Mode.MP) {
            m != null && (m.phase == Match.Phase.RUN || m.phase == Match.Phase.COUNTDOWN) && ov == Ov.NONE
        } else {
            ov == Ov.NONE && !sm.dead && !sm.finished
        }
        if (canPause) iconButton(c, "pause", 100f, cy, 54f) { cc, x, y, r -> iconPause(cc, x, y, r) }

        val score = sm.starCount
        val label = score.toString()
        val pw = max(230f, Gl.textWidth(label, 84f, Gl.medium) + 130f)
        Gl.panel(c, 540f - pw / 2f, cy - 70f, 540f + pw / 2f, cy + 70f, 70f, 1f, pal[0])
        Gl.text(c, label, 540f, cy + 29f, 84f, Gl.medium, Gl.white(245), 0f, null, pal[0])

        when (mode) {
            Mode.LEVEL -> {
                chip(c, "Nivel $level", 930f, cy, 230f, 90f, 40f)
                val total = max(1, cr.totalGates)
                val frac = (score / total.toFloat()).coerceIn(0f, 1f)
                var pp = Gl.p()
                pp.color = Gl.white(50)
                Gl.rect.set(340f, cy + 96f, 740f, cy + 110f)
                c.drawRoundRect(Gl.rect, 7f, 7f, pp)
                pp = Gl.p()
                pp.color = Gl.alpha(pal[0], 220)
                Gl.rect.set(340f, cy + 96f, 340f + 400f * frac, cy + 110f)
                c.drawRoundRect(Gl.rect, 7f, 7f, pp)
            }
            Mode.ENDLESS -> chip(c, "Récord ${max(prefs.endlessBest, score)}", 930f, cy, 230f, 90f, 36f)
            Mode.MP -> if (m != null) {
                val left = if (m.phase == Match.Phase.RUN && !m.myDone) Cfg.ROUND_TIME - runTime else if (m.phase == Match.Phase.COUNTDOWN) Cfg.ROUND_TIME else 0f
                chip(c, clock(left), 930f, cy, 230f, 90f, 44f)
                Gl.text(c, "Ronda ${m.round}/${m.rounds}", 930f, cy + 90f, 30f, Gl.regular, Gl.white(170))
                chip(c, "${m.oppName}  ${m.oppLive}", 540f, cy + 118f, 420f, 70f, 34f, Gl.alpha(pal[1], 255))
            }
        }

        // pistas al empezar
        if (ov == Ov.NONE && !sm.started && !sm.dead && !sm.finished &&
            (mode != Mode.MP || (m != null && m.phase == Match.Phase.RUN))
        ) {
            val a = (160 + 90 * sin(time * 4f)).toInt()
            Gl.text(c, "Toca para saltar", 540f, vh * 0.62f, 60f, Gl.medium, Gl.white(a), 0.05f, null, pal[0])
            if (mode == Mode.LEVEL && cr.zones.isNotEmpty() && level <= 14 && !sm.started) {
                Gl.text(c, "Habrá tramos donde deslizas el dedo", 540f, vh * 0.62f + 76f, 38f, Gl.regular, Gl.white(210))
            }
            if (mode == Mode.LEVEL && level <= 2) {
                Gl.text(c, "Atraviesa solo el color de tu pelota", 540f, vh * 0.62f + 76f, 38f, Gl.regular, Gl.white(210))
            }
        }

        if (mode == Mode.LEVEL && ov == Ov.NONE && sm.started && !sm.dead && !sm.finished && level <= 14) {
            val zb = cr.zones.firstOrNull { sm.by <= it.bottom + 700f && sm.by >= it.top }
            if (zb != null) {
                val a = (200 + 55 * sin(time * 4f)).toInt()
                Gl.text(c, "¡Desliza el dedo para mover la bola!", 540f, vh * 0.30f, 44f, Gl.medium, Gl.white(a), 0.02f, null, pal[0])
            }
        }
        val oa = ((time - ovT0) / 0.42f).coerceIn(0f, 1f)
        if (mode == Mode.MP) {
            drawMpOverlays(c, sm, pal)
            if (ov == Ov.PAUSE) popOverlay(c, oa) { drawPause(c, pal) }
        } else when (ov) {
            Ov.PAUSE -> popOverlay(c, oa) { drawPause(c, pal) }
            Ov.DEAD -> popOverlay(c, oa) { drawDead(c, sm, cr, pal) }
            Ov.WIN -> popOverlay(c, oa) { drawWin(c, pal) }
            Ov.NONE -> {}
        }
    }

    /** Ventanas emergentes: aparecen con un resorte suave y se desvanecen, como en iOS. */
    private fun popOverlay(c: Canvas, a: Float, draw: () -> Unit) {
        if (a >= 1f) {
            draw()
            return
        }
        c.saveLayerAlpha(0f, 0f, Cfg.W, vh, (255 * easeOut(a * 1.6f)).toInt())
        val z = 0.86f + 0.14f * spring(a)
        c.scale(z, z, Cfg.W / 2f, vh / 2f)
        draw()
        c.restore()
    }

    private fun drawPause(c: Canvas, pal: IntArray) {
        dim(c, 150)
        val mp = mode == Mode.MP
        val rows = if (mp) 3 else 4
        val ph = 250f + rows * 150f + 10f
        val cy = vh / 2f
        val t = cy - ph / 2f
        Gl.panel(c, 120f, t, 960f, t + ph, 70f, 1f, pal[0])
        Gl.text(c, "PAUSA", 540f, t + 130f, 70f, Gl.light, Gl.white(245), 0.25f)
        var y = t + 250f
        button(c, "resume", "Continuar", 540f, y, 680f, 120f, pal[0], 52f)
        y += 150f
        if (!mp) {
            button(c, "restart", "Reiniciar", 540f, y, 680f, 120f, 0, 50f)
            y += 150f
        }
        button(c, "tmusic", if (prefs.musicOn) "Música: Sí" else "Música: No", 330f, y, 330f, 110f, 0, 38f)
        button(c, "tsfx", if (prefs.sfxOn) "Efectos: Sí" else "Efectos: No", 750f, y, 330f, 110f, 0, 38f)
        y += 150f
        button(c, "exit", if (mp) "Abandonar partida" else "Salir al menú", 540f, y, 680f, 120f, 0, 48f)
    }

    private fun drawDead(c: Canvas, sm: Sim, cr: Course, pal: IntArray) {
        dim(c, 140)
        val cy = vh / 2f
        if (mode == Mode.ENDLESS) {
            Gl.panel(c, 110f, cy - 430f, 970f, cy + 430f, 70f, 1f, pal[0])
            Gl.text(c, "FIN DEL JUEGO", 540f, cy - 300f, 54f, Gl.medium, Gl.white(225), 0.25f)
            val shader = LinearGradient(300f, 0f, 780f, 0f, intArrayOf(pal[0], pal[3], pal[1]), null, Shader.TileMode.CLAMP)
            Gl.text(c, sm.starCount.toString(), 540f, cy - 70f, 240f, Gl.thin, Color.WHITE, 0f, shader)
            if (newBest) {
                Gl.text(c, "¡NUEVO RÉCORD!", 540f, cy + 20f, 54f, Gl.medium, gold, 0.12f)
            } else {
                Gl.text(c, "Récord ${prefs.endlessBest}", 540f, cy + 20f, 50f, Gl.medium, Gl.white(200))
            }
            button(c, "retry", "Jugar de nuevo", 540f, cy + 180f, 680f, 130f, pal[0], 52f)
            button(c, "exit", "Menú", 540f, cy + 340f, 680f, 120f, 0, 48f)
        } else {
            Gl.panel(c, 110f, cy - 400f, 970f, cy + 400f, 70f, 1f, pal[1])
            Gl.text(c, "¡CASI!", 540f, cy - 250f, 78f, Gl.medium, Gl.white(245), 0.2f)
            Gl.text(c, "Nivel $level", 540f, cy - 170f, 44f, Gl.regular, Gl.white(180))
            Gl.text(c, "Obstáculos ${sm.starCount} / ${cr.totalGates}", 540f, cy - 70f, 52f, Gl.medium, Gl.white(225))
            button(c, "retry", "Reintentar", 540f, cy + 90f, 680f, 130f, pal[0], 54f)
            button(c, "exit", "Niveles", 540f, cy + 250f, 680f, 120f, 0, 48f)
        }
    }

    private fun drawWin(c: Canvas, pal: IntArray) {
        dim(c, 140)
        val cy = vh / 2f
        val last = level >= Levels.PLAYABLE
        // panel centrado: los botones quedan dentro con el mismo margen arriba y abajo
        Gl.panel(c, 100f, cy - 480f, 980f, cy + 490f, 70f, 1f, pal[0])
        Gl.textFit(c, "¡NIVEL COMPLETADO!", 540f, cy - 355f, 64f, 780f, Gl.medium, Gl.white(245), 0.08f)
        Gl.text(c, "Nivel $level", 540f, cy - 285f, 42f, Gl.regular, Gl.white(180))
        for (k in 0 until 3) {
            val on = k < earnedStars
            val sx = 540f + (k - 1) * 190f
            val sy = cy - 130f - (if (k == 1) 30f else 0f)
            if (on) Gl.glow(c, sx, sy, 130f, gold, 70)
            starShape(c, sx, sy, 80f, 34f, 0f, if (on) gold else Gl.white(28), if (on) Gl.white(220) else Gl.white(80))
        }
        var y = cy + 70f
        if (last) {
            Gl.text(c, "¡Completaste todos los niveles!", 540f, cy - 10f, 38f, Gl.medium, Gl.white(220))
            y = cy + 100f
        } else {
            button(c, "next", "Siguiente nivel", 540f, y, 700f, 130f, pal[0], 54f)
            y += 155f
        }
        button(c, "retry", "Repetir", 540f, y, 700f, 120f, 0, 48f)
        y += 150f
        button(c, "exit", "Niveles", 540f, y, 700f, 120f, 0, 48f)
    }

    private fun drawMpOverlays(c: Canvas, sm: Sim, pal: IntArray) {
        val m = match ?: return
        val cy = vh / 2f
        if (m.phase == Match.Phase.CLOSED) {
            dim(c, 170)
            Gl.panel(c, 110f, cy - 260f, 970f, cy + 260f, 70f, 1f, pal[1])
            Gl.text(c, "El rival se desconectó", 540f, cy - 60f, 56f, Gl.medium, Gl.white(245))
            button(c, "exit", "Salir", 540f, cy + 120f, 600f, 130f, pal[0], 52f)
            return
        }
        when (m.phase) {
            Match.Phase.COUNTDOWN -> {
                val shown = max(1, ceil(cdTime).toInt())
                val frac = cdTime - floor(cdTime)
                val size = 300f + 80f * frac
                Gl.text(c, shown.toString(), 540f, cy + 90f, size, Gl.thin, Gl.white(245), 0f, null, pal[0])
                Gl.text(c, "Prepárate · vs ${m.oppName}", 540f, cy + 250f, 44f, Gl.regular, Gl.white(200))
            }
            Match.Phase.RUN -> {
                if (goFlash > 0f) {
                    val a = (goFlash / 0.8f * 255f).toInt()
                    Gl.text(c, "¡YA!", 540f, cy, 200f, Gl.medium, Gl.white(a), 0f, null, pal[0])
                }
                if (m.myDone && ov != Ov.PAUSE) {
                    Gl.panel(c, 130f, cy - 130f, 950f, cy + 130f, 60f, 1f, pal[1])
                    Gl.text(c, if (mpFrozen) "¡Se acabó el tiempo!" else "Eliminado", 540f, cy - 20f, 56f, Gl.medium, Gl.white(245))
                    Gl.text(c, "Esperando al rival" + dots(), 540f, cy + 60f, 38f, Gl.regular, Gl.white(190))
                } else if (m.oppDone && !m.myDone) {
                    chip(c, "Rival eliminado · ¡sigue sumando!", 540f, vh - 200f, 820f, 90f, 36f)
                }
            }
            Match.Phase.ROUND_END -> {
                dim(c, 130)
                Gl.panel(c, 110f, cy - 380f, 970f, cy + 380f, 70f, 1f, pal[0])
                Gl.text(c, "RONDA ${m.round} DE ${m.rounds}", 540f, cy - 260f, 54f, Gl.medium, Gl.white(235), 0.15f)
                val mine = m.myRounds.lastOrNull() ?: 0
                val theirs = m.oppRounds.lastOrNull() ?: 0
                scoreLine(c, "Tú", mine, cy - 120f, mine >= theirs, pal)
                scoreLine(c, m.oppName, theirs, cy, theirs > mine, pal)
                Gl.text(c, "Total  ${m.myTotal} – ${m.oppTotal}", 540f, cy + 150f, 50f, Gl.medium, Gl.white(220))
                Gl.text(c, "Siguiente ronda en ${ceil(rEndTimer).toInt()}", 540f, cy + 260f, 40f, Gl.regular, Gl.white(170))
            }
            Match.Phase.MATCH_END -> {
                dim(c, 150)
                val res = m.result()
                Gl.panel(c, 100f, cy - 560f, 980f, cy + 560f, 70f, 1f, if (res >= 0) gold else pal[1])
                val word = if (res > 0) "¡GANASTE!" else if (res < 0) "PERDISTE" else "EMPATE"
                val shader = LinearGradient(250f, 0f, 830f, 0f, intArrayOf(pal[0], pal[3], pal[1]), null, Shader.TileMode.CLAMP)
                Gl.text(c, word, 540f, cy - 380f, 110f, Gl.medium, Color.WHITE, 0.08f, if (res > 0) null else shader, if (res > 0) gold else 0)
                Gl.text(c, "${m.myTotal}  –  ${m.oppTotal}", 540f, cy - 220f, 120f, Gl.thin, Gl.white(245))
                Gl.text(c, "Tú  ·  ${m.oppName}", 540f, cy - 150f, 38f, Gl.regular, Gl.white(170))
                var y = cy - 70f
                for (i in m.myRounds.indices) {
                    val a = m.myRounds[i]
                    val b = m.oppRounds.getOrNull(i) ?: 0
                    Gl.text(c, "Ronda ${i + 1}:   $a  –  $b", 540f, y, 38f, Gl.regular, Gl.white(200))
                    y += 52f
                    if (i >= 4) break
                }
                val rLabel = if (m.oppLeft) "El rival salió" else if (m.myRematch) "Esperando al rival…" else "Revancha"
                button(c, "rematch", rLabel, 540f, cy + 330f, 700f, 130f, pal[0], 52f, !m.myRematch && !m.oppLeft)
                button(c, "exit", "Salir", 540f, cy + 480f, 700f, 120f, 0, 48f)
            }
            else -> {}
        }
    }

    private fun scoreLine(c: Canvas, name: String, score: Int, y: Float, lead: Boolean, pal: IntArray) {
        Gl.textFit(c, name, 160f, y + 18f, 52f, 420f, Gl.regular, Gl.white(if (lead) 245 else 180), 0f, Paint.Align.LEFT)
        Gl.text(c, score.toString(), 920f, y + 30f, 90f, Gl.medium, if (lead) gold else Gl.white(200), 0f, null, 0, Paint.Align.RIGHT)
    }

    companion object {
        private const val SL_X0 = 120f
        private const val SL_X1 = 960f
    }
}
