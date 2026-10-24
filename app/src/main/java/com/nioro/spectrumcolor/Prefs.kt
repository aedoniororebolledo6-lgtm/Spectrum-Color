package com.nioro.spectrumcolor

import android.content.Context
import android.content.SharedPreferences

/** Ajustes y progreso guardados en el celular. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("spectrum_color", Context.MODE_PRIVATE)
    private val starArr = IntArray(Levels.COUNT + 1)

    init {
        val s = sp.getString("stars", "") ?: ""
        for (i in 0 until minOf(s.length, Levels.COUNT)) {
            val d = s[i] - '0'
            starArr[i + 1] = if (d in 0..3) d else 0
        }
    }

    var musicOn: Boolean
        get() = sp.getBoolean("musicOn", true)
        set(v) { sp.edit().putBoolean("musicOn", v).apply() }

    var musicVol: Float
        get() = sp.getFloat("musicVol", 0.7f).coerceIn(0f, 1f)
        set(v) { sp.edit().putFloat("musicVol", v.coerceIn(0f, 1f)).apply() }

    var sfxOn: Boolean
        get() = sp.getBoolean("sfxOn", true)
        set(v) { sp.edit().putBoolean("sfxOn", v).apply() }

    var sfxVol: Float
        get() = sp.getFloat("sfxVol", 0.8f).coerceIn(0f, 1f)
        set(v) { sp.edit().putFloat("sfxVol", v.coerceIn(0f, 1f)).apply() }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) { sp.edit().putBoolean("haptics", v).apply() }

    /** Modo fácil: los obstáculos giran más lento (solo en un jugador). */
    var easy: Boolean
        get() = sp.getBoolean("easy", false)
        set(v) { sp.edit().putBoolean("easy", v).apply() }

    /** Apariencia elegida de la bola (índice en Skins). */
    var skin: Int
        get() = sp.getInt("skin", 0)
        set(v) { sp.edit().putInt("skin", v).apply() }

    /** Estela (rastro) de la bola (índice en Trails). */
    var trail: Int
        get() = sp.getInt("trail", 0)
        set(v) { sp.edit().putInt("trail", v).apply() }

    /** Tema de fondo (0 = clásico, 1 = rojo, 2 = morado, 3 = rosa). */
    var theme: Int
        get() = sp.getInt("theme", 0).coerceIn(0, Themes.COUNT - 1)
        set(v) { sp.edit().putInt("theme", v).apply() }

    /** Idioma: 0 = español, 1 = inglés, 2 = ruso. */
    var lang: Int
        get() = sp.getInt("lang", I18n.systemDefault()).coerceIn(0, 2)
        set(v) { sp.edit().putInt("lang", v).apply() }

    /** Solo existe en la versión de desarrollador; en la normal siempre es falso. */
    var allOpen: Boolean
        get() = BuildConfig.DEV && sp.getBoolean("allOpen", false)
        set(v) { sp.edit().putBoolean("allOpen", v).apply() }

    var name: String
        get() = sp.getString("name", "Jugador") ?: "Jugador"
        set(v) { sp.edit().putString("name", NetUtil.clean(v)).apply() }

    var endlessBest: Int
        get() = sp.getInt("endlessBest", 0)
        set(v) { sp.edit().putInt("endlessBest", v).apply() }

    var mpRounds: Int
        get() = sp.getInt("mpRounds", 3)
        set(v) { sp.edit().putInt("mpRounds", v).apply() }

    fun stars(level: Int): Int = if (level in 1..Levels.COUNT) starArr[level] else 0

    fun setStars(level: Int, v: Int) {
        if (!Levels.isPlayable(level)) return
        if (v > starArr[level]) {
            starArr[level] = v.coerceAtMost(3)
            save()
        }
    }

    /** Nivel más alto que se puede jugar ahora. */
    fun unlocked(): Int {
        if (allOpen) return Levels.PLAYABLE
        var l = 1
        while (l < Levels.PLAYABLE && starArr[l] > 0) l++
        return l
    }

    /** Mundos con todos sus niveles superados (al menos 1 estrella en cada uno). */
    fun worldsDone(): Int {
        var n = 0
        for (w in 0 until Levels.WORLDS) {
            var ok = true
            for (i in 0 until Levels.PER_WORLD) {
                val l = Levels.worldFirst(w) + i
                if (l > Levels.COUNT || starArr[l] <= 0) { ok = false; break }
            }
            if (ok) n++
        }
        return n
    }

    fun totalStars(): Int {
        var n = 0
        for (i in 1..Levels.COUNT) n += starArr[i]
        return n
    }

    fun resetProgress() {
        for (i in starArr.indices) starArr[i] = 0
        sp.edit().putString("stars", "").putInt("endlessBest", 0).putBoolean("allOpen", false).apply()
    }

    private fun save() {
        val sb = StringBuilder()
        for (i in 1..Levels.COUNT) sb.append(('0' + starArr[i]))
        sp.edit().putString("stars", sb.toString()).apply()
    }
}
