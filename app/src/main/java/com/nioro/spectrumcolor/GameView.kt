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
class GameView(context: Context, private val api: HostApi) : View(context), Choreographer.FrameCallback {

    private enum class Scr { HOME, LEVELS, SETTINGS, PLAY, MP_MENU, MP_HOST, MP_JOIN }
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
    private val prefs = Prefs(context)
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
    private var scr = Scr.HOME
    private var mode = Mode.LEVEL
    private var ov = Ov.NONE
    private var level = 1
    private var levelPage = 0
    private val hits = ArrayList<Hit>()
    private var pressedId: String? = null
    private var dragSlider: String? = null
    private var downX = 0f
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
            Scr.LEVELS, Scr.SETTINGS, Scr.MP_MENU -> goHome()
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
        scr = Scr.HOME
        audio.setTrack(0)
    }

    private fun exitToMenu() {
        when (mode) {
            Mode.LEVEL -> {
                levelPage = Levels.world(level)
                scr = Scr.LEVELS
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
        level = l
        deaths = 0
        beginPlay(Course.level(l), Mode.LEVEL)
    }

    private fun startEndless() {
        beginPlay(Course.endless(rnd.nextLong() and 0x7fffffffffffL), Mode.ENDLESS)
    }

    private fun beginPlay(c: Course, m: Mode) {
        mode = m
        course = c
        world = if (c.isLevel) Levels.world(c.level) else (((c.seed % 12L) + 12L) % 12L).toInt()
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
            id == "play" -> {
                levelPage = Levels.world(prefs.unlocked())
                scr = Scr.LEVELS
            }
            id == "endless" -> startEndless()
            id == "mp" -> {
                netStatus = ""
                scr = Scr.MP_MENU
            }
            id == "settings" -> scr = Scr.SETTINGS
            id == "quit" -> api.quit()
            id == "update" -> {
                if (updState == 3) api.openUrl("https://github.com/aedoniororebolledo6-lgtm/Spectrum-Color/releases/latest/download/SpectrumColor.apk")
                else checkUpdate()
            }
            id == "back" -> onBack()
            id == "wprev" -> levelPage = max(0, levelPage - 1)
            id == "wnext" -> levelPage = min(Levels.WORLDS - 1, levelPage + 1)
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
            id == "tname" || id == "mp_name" -> api.prompt("Tu nombre", prefs.name, "Nombre") { prefs.name = it }
            id == "topen" -> prefs.allOpen = !prefs.allOpen
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
                if (level < Levels.PLAYABLE) {
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
            id == "mp_ip" -> api.prompt("IP del anfitrión", "", "Ejemplo: 192.168.1.20") { joinTyped(it) }
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
            MotionEvent.ACTION_DOWN -> {
                downX = x
                val id = hitAt(x, y)
                pressedId = id
                if (id != null && id.startsWith("sl:")) {
                    dragSlider = id.substring(3)
                    setSlider(x)
                } else if (id == null && scr == Scr.PLAY) {
                    gameTap()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (scr == Scr.PLAY) gameTap()
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragSlider != null) setSlider(x)
            }
            MotionEvent.ACTION_UP -> {
                val id = pressedId
                pressedId = null
                if (dragSlider != null) {
                    dragSlider = null
                    audio.play(Sfx.CLICK)
                } else if (scr == Scr.LEVELS && abs(x - downX) > 160f) {
                    levelPage = if (x < downX) min(Levels.WORLDS - 1, levelPage + 1) else max(0, levelPage - 1)
                    audio.play(Sfx.CLICK)
                } else if (id != null && hitAt(x, y) == id) {
                    activate(id)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedId = null
                dragSlider = null
            }
        }
        return true
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
            for (i in lastStarCount until min(sm.starCount, cr.obs.size)) sparkle(Cfg.W / 2f, cr.obs[i].y, Color.WHITE, 14)
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
        if (sm.started && !sm.dead && !sm.finished) trail.add(floatArrayOf(Cfg.W / 2f, sm.by, 0f))
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
            parts.add(Part(Cfg.W / 2f, sm.by, cos(a) * sp, sin(a) * sp - 300f, col, 8f + rnd.nextFloat() * 16f, life, life))
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
        prefs.setStars(level, earnedStars)
        audio.play(Sfx.WIN)
        haptic(HapticFeedbackConstants.LONG_PRESS)
        val pal = Worlds.colors[world]
        for (i in 0 until 60) {
            val a = -1.57f + (rnd.nextFloat() - 0.5f) * 2.6f
            val sp = 500f + rnd.nextFloat() * 1100f
            val life = 1.0f + rnd.nextFloat() * 0.9f
            parts.add(Part(Cfg.W / 2f, sm.by, cos(a) * sp, sin(a) * sp, pal[rnd.nextInt(4)], 8f + rnd.nextFloat() * 12f, life, life))
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
            if (trail[k][2] > 0.35f) trail.removeAt(k)
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

    private fun cur(): IntArray = Worlds.colors[curWorld().coerceIn(0, Worlds.colors.size - 1)]

    private fun drawAll(c: Canvas) {
        when (scr) {
            Scr.PLAY -> {
                drawPlayWorld(c)
                drawPlayUi(c)
            }
            else -> {
                drawBackground(c, 0f)
                when (scr) {
                    Scr.HOME -> drawHome(c)
                    Scr.LEVELS -> drawLevels(c)
                    Scr.SETTINGS -> drawSettings(c)
                    Scr.MP_MENU -> drawMpMenu(c)
                    Scr.MP_HOST -> drawMpHost(c)
                    Scr.MP_JOIN -> drawMpJoin(c)
                    else -> {}
                }
            }
        }
    }

    private fun drawBackground(c: Canvas, camY: Float) {
        val w = curWorld().coerceIn(0, Worlds.bg.size - 1)
        if (bgShader == null || bgShaderWorld != w) {
            bgShader = LinearGradient(0f, 0f, 0f, vh, Worlds.bg[w], floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            bgShaderWorld = w
        }
        val pp = Gl.p()
        pp.shader = bgShader
        c.drawRect(0f, 0f, Cfg.W, vh, pp)
        val pal = Worlds.colors[w]
        for (i in 0 until 4) {
            val rad = 520f + i * 60f
            val span = vh + 2f * rad
            val depth = 0.05f + i * 0.03f
            val x = Cfg.W * (0.15f + 0.25f * i) + sin(time * 0.25f + i * 1.7f) * 90f
            val y = (((0.2f + 0.27f * i) * vh - camY * depth) % span + span) % span - rad
            Gl.glow(c, x, y, rad, pal[i], 70)
        }
        for (b in bubbles) {
            val s = b.size
            val span = vh + 2f * s
            val x = b.nx * Cfg.W + sin(time * 0.6f + b.sway) * 14f
            val y = ((b.ny * vh - camY * b.depth - time * 8f * (b.depth * 4f)) % span + span) % span - s
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
        if (accent != 0 && enabled) Gl.glow(c, cx, cy, w * 0.62f, accent, if (pressed) 70 else 45)
        Gl.panel(
            c, cx - hw, cy - hh, cx + hw, cy + hh, hh,
            if (enabled) (if (pressed) 1.25f else 1f) else 0.45f,
            if (accent != 0) accent else cur()[0]
        )
        Gl.textFit(c, label, cx, cy + size * 0.35f, size, w - 60f, Gl.medium, Gl.white(if (enabled) 245 else 110))
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
        c.drawRect(0f, 0f, Cfg.W, vh, pp)
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

        val b0 = topI + 970f
        button(c, "play", "Jugar", 540f, b0, 760f, 130f, pal[0], 56f)
        button(c, "endless", "Modo infinito", 540f, b0 + 175f, 760f, 130f, 0, 50f)
        button(c, "mp", "Multijugador local", 540f, b0 + 350f, 760f, 130f, pal[1], 50f)
        button(c, "settings", "Ajustes", 340f, b0 + 525f, 370f, 120f, 0, 46f)
        button(c, "quit", "Salir", 740f, b0 + 525f, 370f, 120f, 0, 46f)
        val total = Levels.PLAYABLE * 3
        Gl.text(
            c, "Estrellas ${prefs.totalStars()}/$total   ·   Récord infinito ${prefs.endlessBest}",
            540f, vh - 130f, 38f, Gl.regular, Gl.white(170)
        )
    }

    private fun drawTile(c: Canvas, lvl: Int, cx: Float, cy: Float, w: Float, h: Float) {
        val pal = cur()
        val playable = Levels.isPlayable(lvl)
        val unlocked = playable && lvl <= prefs.unlocked()
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
        Gl.text(c, "NIVELES", 540f, top + 128f, 54f, Gl.medium, Gl.white(240), 0.2f)
        starShape(c, 880f, top + 108f, 30f, 13f, 0f, gold, Gl.white(210))
        Gl.text(c, prefs.totalStars().toString(), 950f, top + 126f, 46f, Gl.medium, Gl.white(240))

        val wp = levelPage
        val avail = Levels.worldAvailable(wp)
        val ry = top + 300f
        if (wp > 0) iconButton(c, "wprev", 120f, ry, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, -1) }
        if (wp < Levels.WORLDS - 1) iconButton(c, "wnext", 960f, ry, 56f) { cc, x, y, r -> iconChevron(cc, x, y, r, 1) }
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
            dim(c, 110)
            val cy = gridTop + rowH * 2.5f
            Gl.panel(c, 130f, cy - 230f, 950f, cy + 230f, 80f, 1f, pal[1])
            drawLock(c, 540f, cy - 110f, 2.2f, 220)
            Gl.text(c, "PRÓXIMAMENTE", 540f, cy + 50f, 70f, Gl.medium, Gl.white(245), 0.12f, null, Gl.alpha(pal[0], 150))
            Gl.text(c, "Coming soon", 540f, cy + 118f, 44f, Gl.light, Gl.white(190), 0.1f)
            Gl.text(c, "Más niveles en próximas actualizaciones", 540f, cy + 176f, 32f, Gl.regular, Gl.white(150))
        }
        val dots = Levels.WORLDS
        for (i in 0 until dots) {
            val x = 540f + (i - (dots - 1) / 2f) * 38f
            val pp = Gl.p()
            pp.color = if (i == wp) Gl.white(240) else Gl.white(if (Levels.worldAvailable(i)) 110 else 50)
            c.drawCircle(x, vh - 100f, if (i == wp) 9f else 6f, pp)
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
        Gl.panel(c, 60f, y0 - 90f, 1020f, y0 + 1140f, 70f, 0.8f, pal[1])

        settingRow(c, "Música", y0)
        toggle(c, "tmusic", 870f, y0, prefs.musicOn)
        slider(c, "music", y0 + 95f, prefs.musicVol, prefs.musicOn)

        settingRow(c, "Efectos", y0 + 200f)
        toggle(c, "tsfx", 870f, y0 + 200f, prefs.sfxOn)
        slider(c, "sfx", y0 + 295f, prefs.sfxVol, prefs.sfxOn)

        settingRow(c, "Vibración", y0 + 420f)
        toggle(c, "thaptic", 870f, y0 + 420f, prefs.haptics)

        settingRow(c, "Dificultad", y0 + 550f)
        button(c, "teasy", if (prefs.easy) "Fácil" else "Normal", 800f, y0 + 550f, 300f, 100f, 0, 44f)

        settingRow(c, "Jugador", y0 + 680f)
        button(c, "tname", prefs.name, 760f, y0 + 680f, 380f, 100f, 0, 40f)

        settingRow(c, "Niveles", y0 + 810f)
        button(c, "topen", if (prefs.allOpen) "Todos abiertos" else "Abrir todos", 760f, y0 + 810f, 380f, 100f, 0, 40f)

        settingRow(c, "Progreso", y0 + 940f)
        val armed = time - resetArmedAt < 3f
        button(c, "treset", if (armed) "¿Seguro? Toca otra vez" else "Borrar progreso", 760f, y0 + 940f, 380f, 100f, if (armed) 0xFFFF4F6B.toInt() else 0, 36f)

        val ul = when (updState) {
            1 -> "Buscando…"
            2 -> "Estás al día"
            3 -> "Descargar nueva"
            4 -> "Sin conexión"
            else -> "Buscar"
        }
        settingRow(c, "Actualización", y0 + 1070f)
        button(c, "update", ul, 760f, y0 + 1070f, 380f, 100f, if (updState == 3) pal[2] else 0, 38f)

        Gl.text(c, "Spectrum Color  ·  v2", 540f, vh - 90f, 34f, Gl.regular, Gl.white(120))
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
            m.phase == Match.Phase.CLOSED -> "El jugador se fue"
            m.connected -> "¡${m.oppName} se unió!"
            else -> "Esperando jugador" + dots()
        }
        val cy = top + 480f
        Gl.panel(c, 110f, cy - 150f, 970f, cy + 150f, 70f, 1f, pal[0])
        Gl.textFit(c, status, 540f, cy + 14f, 60f, 800f, Gl.medium, Gl.white(245))
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
            Gl.text(c, "El anfitrión se fue", 540f, top + 560f, 52f, Gl.medium, Gl.white(220))
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

        if (cr.isLevel) drawFinish(c, cr.finishY, pal)
        for (i in sm.swCount until cr.sws.size) {
            val s = cr.sws[i]
            if (s.y < camY - 100f) break
            if (s.y > camY + vh + 100f) continue
            drawSwitcher(c, s, cr, pal)
        }
        for (o in cr.obs) {
            if (o.y + o.reach < camY - 40f || o.y - o.reach > camY + vh + 40f) continue
            drawObstacle(c, o, sm.t, pal)
        }
        for (i in sm.starCount until min(cr.obs.size, sm.starCount + 6)) {
            val o = cr.obs[i]
            if (o.y < camY - 60f || o.y > camY + vh + 60f) continue
            drawStar(c, Cfg.W / 2f, o.y)
        }
        drawTrail(c, pal[sm.color % 4])
        if (!sm.dead) drawBall(c, sm, pal[sm.color % 4])
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

    private fun drawObstacle(c: Canvas, o: Ob, t: Float, pal: IntArray) {
        when (o.type) {
            T.RING -> drawRing(c, o, o.size, Geo.rot(o, t), o.shift, pal)
            T.DOUBLE -> {
                drawRing(c, o, o.size, Geo.rot(o, t), o.shift, pal)
                drawRing(c, o, o.inner, Geo.rotInner(o, t), (o.shift + 1) % o.n, pal)
            }
            T.BARS -> drawBars(c, o, t, pal)
            else -> drawPoly(c, o, t, pal)
        }
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
        val half = Cfg.BAR_H
        for (lane in 0 until Cfg.BAR_LANES) {
            val y = o.y + if (lane == 0) -Cfg.BAR_DY else Cfg.BAR_DY
            val off = o.phase * 3f + o.speed * t
            val j0 = floor((0f - off) / Cfg.BAR_SEG).toInt() - 1
            val j1 = floor((Cfg.W - off) / Cfg.BAR_SEG).toInt() + 1
            for (j in j0..j1) {
                val x0 = off + j * Cfg.BAR_SEG + 4f
                val x1 = off + (j + 1) * Cfg.BAR_SEG - 4f
                val col = pal[(((j % o.n) + o.n) % o.n + o.shift) % o.n]
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
                Gl.rect2.set(x0 + half * 0.7f, y - half * 0.72f, x1 - half * 0.7f, y - half * 0.42f)
                c.drawRoundRect(Gl.rect2, half * 0.15f, half * 0.15f, pp)
            }
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

    private fun drawSwitcher(c: Canvas, s: Sw, cr: Course, pal: IntArray) {
        val x = Cfg.W / 2f
        val r = Cfg.SW_R
        val n = if (cr.isLevel) Levels.colors(cr.level) else 4
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
        for (t in trail) {
            val frac = 1f - t[2] / 0.35f
            if (frac <= 0f) continue
            val pp = Gl.p()
            pp.color = Gl.alpha(col, (frac * 70f).toInt())
            c.drawCircle(t[0], t[1], Cfg.BALL_R * (0.3f + 0.55f * frac), pp)
        }
    }

    private fun drawBall(c: Canvas, sm: Sim, col: Int) {
        val r = Cfg.BALL_R
        val x = Cfg.W / 2f
        val y = if (sm.started) sm.by else sm.by + sin(time * 3f) * 9f
        Gl.glow(c, x, y, r * 2.8f, col, 110)
        var pp = Gl.p()
        pp.shader = android.graphics.RadialGradient(
            x - r * 0.35f, y - r * 0.4f, r * 1.5f,
            intArrayOf(Gl.white(235), Gl.alpha(col, 235), Gl.alpha(Gl.darken(col, 0.5f), 245)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, pp)
        pp = Gl.p()
        pp.style = Paint.Style.STROKE
        pp.strokeWidth = 2.4f
        pp.color = Gl.white(160)
        c.drawCircle(x, y, r, pp)
        Gl.arc(c, x, y, r * 0.72f, 40f, 100f, r * 0.16f, Gl.white(70))
        pp = Gl.p()
        pp.color = Gl.white(215)
        Gl.rect.set(x - r * 0.64f, y - r * 0.74f, x - r * 0.06f, y - r * 0.3f)
        c.drawOval(Gl.rect, pp)
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
            if (mode == Mode.LEVEL && level <= 2) {
                Gl.text(c, "Atraviesa solo el color de tu pelota", 540f, vh * 0.62f + 76f, 38f, Gl.regular, Gl.white(210))
            }
        }

        if (mode == Mode.MP) drawMpOverlays(c, sm, pal) else when (ov) {
            Ov.PAUSE -> drawPause(c, pal)
            Ov.DEAD -> drawDead(c, sm, cr, pal)
            Ov.WIN -> drawWin(c, pal)
            Ov.NONE -> {}
        }
        if (mode == Mode.MP && ov == Ov.PAUSE) drawPause(c, pal)
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
        Gl.panel(c, 100f, cy - 520f, 980f, cy + 520f, 70f, 1f, pal[0])
        Gl.textFit(c, "¡NIVEL COMPLETADO!", 540f, cy - 380f, 64f, 780f, Gl.medium, Gl.white(245), 0.08f)
        Gl.text(c, "Nivel $level", 540f, cy - 305f, 42f, Gl.regular, Gl.white(180))
        for (k in 0 until 3) {
            val on = k < earnedStars
            val sx = 540f + (k - 1) * 190f
            val sy = cy - 150f - (if (k == 1) 30f else 0f)
            if (on) Gl.glow(c, sx, sy, 130f, gold, 70)
            starShape(c, sx, sy, 80f, 34f, 0f, if (on) gold else Gl.white(28), if (on) Gl.white(220) else Gl.white(80))
        }
        if (last) {
            Gl.text(c, "¡Terminaste los niveles disponibles!", 540f, cy + 20f, 38f, Gl.medium, Gl.white(220))
            Gl.text(c, "Próximamente más mundos", 540f, cy + 70f, 34f, Gl.regular, Gl.white(160))
        }
        var y = cy + 170f
        if (!last) {
            button(c, "next", "Siguiente nivel", 540f, y, 700f, 130f, pal[0], 54f)
            y += 150f
        }
        button(c, "retry", "Repetir", 540f, y, 700f, 120f, 0, 48f)
        y += 140f
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
