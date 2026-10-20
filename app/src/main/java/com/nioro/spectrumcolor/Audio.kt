package com.nioro.spectrumcolor

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
    private var mp: MediaPlayer? = null
    private var track = -1
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
            m.setVolume(v, v)
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    fun onPause() {
        paused = true
        try {
            mp?.let { if (it.isPlaying) it.pause() }
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
            } else if (!m.isPlaying) {
                m.start()
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

    private fun startPlayer() {
        try {
            val res = if (track == 0) R.raw.music_menu else R.raw.music_game
            val m = MediaPlayer.create(ctx, res) ?: return
            m.isLooping = true
            val v = prefs.musicVol * 0.85f
            m.setVolume(v, v)
            if (!paused) m.start()
            mp = m
        } catch (e: Exception) {
            mp = null
        }
    }

    private fun releasePlayer() {
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
