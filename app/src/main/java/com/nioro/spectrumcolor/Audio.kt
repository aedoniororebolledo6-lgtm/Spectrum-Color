package com.nioro.spectrumcolor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool

object Sfx {
    const val TAP = 0
    const val STAR = 1
    const val SWITCH = 2
    const val DIE = 3
    const val WIN = 4
    const val CLICK = 5
    const val BACK = 6
    const val COUNT = 7
    const val GO = 8
    const val N = 9
}

/** Efectos (SoundPool) y música (MediaPlayer en bucle). Todo protegido: el audio nunca debe cerrar el juego. */
class Audio(private val ctx: Context, private val prefs: Prefs) {
    private var pool: SoundPool? = null
    private val sid = IntArray(Sfx.N)
    private val ready = HashSet<Int>()
    private var mp: AudioTrack? = null
    private var track = -1
    private val variant = intArrayOf(java.util.Random().nextInt(4), 1) // menú: al abrir el juego empieza una canción al azar
    private val nTracks = intArrayOf(4, 2)
    private var cur = 0
    private var paused = false

    init {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
            p.setOnLoadCompleteListener { _, id, status ->
                if (status == 0) synchronized(ready) { ready.add(id) }
            }
            pool = p
            val res = IntArray(Sfx.N)
            res[Sfx.TAP] = R.raw.sfx_tap
            res[Sfx.STAR] = R.raw.sfx_star
            res[Sfx.SWITCH] = R.raw.sfx_switch
            res[Sfx.DIE] = R.raw.sfx_die
            res[Sfx.WIN] = R.raw.sfx_win
            res[Sfx.CLICK] = R.raw.sfx_click
            res[Sfx.BACK] = R.raw.sfx_back
            res[Sfx.COUNT] = R.raw.sfx_count
            res[Sfx.GO] = R.raw.sfx_go
            for (i in 0 until Sfx.N) sid[i] = p.load(ctx, res[i], 1)
        } catch (e: Exception) {
            pool = null
        }
    }

    fun play(i: Int, scale: Float = 1f) {
        if (!prefs.sfxOn || i < 0 || i >= Sfx.N) return
        val p = pool ?: return
        val id = sid[i]
        val ok = synchronized(ready) { ready.contains(id) }
        if (!ok) return
        val v = (prefs.sfxVol * scale).coerceIn(0f, 1f)
        try {
            p.play(id, v, v, 1, 0, 1f)
        } catch (e: Exception) {
            // sin sonido
        }
    }

    /** 0 = música de menús, 1 = música de partida. */
    fun setTrack(t: Int) {
        if (t == track && (mp != null || !prefs.musicOn)) {
            refresh()
            return
        }
        releasePlayer()
        track = t
        if (t in 0..1) {
            // menú: canción al azar (distinta de la anterior); partida: se alternan
            variant[t] = if (t == 0) (variant[t] + 1 + java.util.Random().nextInt(nTracks[t] - 1)) % nTracks[t] else (variant[t] + 1) % nTracks[t]
            cur = variant[t]
        }
        if (prefs.musicOn) startPlayer()
    }

    /** Aplica cambios de volumen / activar / desactivar música. */
    fun refresh() {
        if (!prefs.musicOn) {
            releasePlayer()
            return
        }
        val m = mp
        if (m == null) {
            if (track >= 0) startPlayer()
            return
        }
        try {
            val v = prefs.musicVol * 0.85f
            m.setVolume(v)
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    fun onPause() {
        paused = true
        try {
            mp?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.pause() }
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    fun onResume() {
        paused = false
        if (!prefs.musicOn) return
        try {
            val m = mp
            if (m == null) {
                if (track >= 0) startPlayer()
            } else if (m.playState != AudioTrack.PLAYSTATE_PLAYING) {
                m.play()
            }
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    fun release() {
        releasePlayer()
        try {
            pool?.release()
        } catch (e: Exception) {
        }
        pool = null
    }

    /** Lee un WAV PCM 16 bit mono/estéreo de res/raw y devuelve (muestras, frecuencia, canales). */
    private fun readWav(res: Int): Triple<ShortArray, Int, Int>? {
        val bytes = ctx.resources.openRawResource(res).use { it.readBytes() }
        var p = 12
        var rate = 22050
        var ch = 1
        while (p + 8 <= bytes.size) {
            val id = String(bytes, p, 4, Charsets.US_ASCII)
            val len = (bytes[p + 4].toInt() and 255) or ((bytes[p + 5].toInt() and 255) shl 8) or
                ((bytes[p + 6].toInt() and 255) shl 16) or ((bytes[p + 7].toInt() and 255) shl 24)
            if (id == "fmt ") {
                ch = (bytes[p + 10].toInt() and 255) or ((bytes[p + 11].toInt() and 255) shl 8)
                rate = (bytes[p + 12].toInt() and 255) or ((bytes[p + 13].toInt() and 255) shl 8) or
                    ((bytes[p + 14].toInt() and 255) shl 16) or ((bytes[p + 15].toInt() and 255) shl 24)
            } else if (id == "data") {
                val n = minOf(len, bytes.size - p - 8) / 2
                val out = ShortArray(n)
                java.nio.ByteBuffer.wrap(bytes, p + 8, n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
                return Triple(out, rate, ch)
            }
            p += 8 + len + (len and 1)
        }
        return null
    }

    // La canción se lee en segundo plano (antes se leía en el hilo del dibujo y el juego se trababa un instante al
    // entrar o salir de un nivel). Las 3 últimas quedan en memoria para que cambiar de pantalla sea instantáneo.
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var gen = 0
    private var loadingRes = -1
    private val pcmCache = LinkedHashMap<Int, Triple<ShortArray, Int, Int>>(4, 0.75f, true)

    /** Bucle sin corte: el WAV completo en memoria y AudioTrack repitiéndolo (MediaPlayer deja un hueco al reiniciar). */
    private fun startPlayer() {
        val res = if (track == 0) (when (cur) { 0 -> R.raw.music_menu; 1 -> R.raw.music_menu2; 2 -> R.raw.music_menu3; else -> R.raw.music_menu4 })
        else (if (cur == 0) R.raw.music_game else R.raw.music_game2)
        val cached = synchronized(pcmCache) { pcmCache[res] }
        if (cached != null) {
            buildTrack(cached)
            return
        }
        if (loadingRes == res) return
        loadingRes = res
        val g = ++gen
        try {
            Thread({
                val w = try { readWav(res) } catch (e: Throwable) { null }
                if (w != null) synchronized(pcmCache) {
                    pcmCache[res] = w
                    while (pcmCache.size > 3) pcmCache.remove(pcmCache.keys.first())
                }
                main.post {
                    if (g != gen) return@post
                    loadingRes = -1
                    if (w != null && mp == null && prefs.musicOn && track >= 0) buildTrack(w)
                }
            }, "audio-load").apply { isDaemon = true }.start()
        } catch (e: Throwable) {
            loadingRes = -1
        }
    }

    private fun buildTrack(w: Triple<ShortArray, Int, Int>) {
        try {
            val (pcm, rate, ch) = w
            val mask = if (ch == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(mask)
                .build()
            val t = AudioTrack(attrs, fmt, pcm.size * 2, AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size / ch, -1)
            val v = prefs.musicVol * 0.85f
            t.setVolume(v)
            if (!paused) t.play()
            mp = t
        } catch (e: Throwable) {
            mp = null
        }
    }

    private fun releasePlayer() {
        gen++ // una canción que se esté leyendo ya no se usará
        loadingRes = -1
        val m = mp ?: return
        mp = null
        try {
            m.stop()
        } catch (e: Exception) {
        }
        try {
            m.release()
        } catch (e: Exception) {
        }
    }
}
