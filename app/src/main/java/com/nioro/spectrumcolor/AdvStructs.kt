package com.nioro.spectrumcolor

/**
 * Catálogo de estructuras del modo Aventura. Cada una es un plano de casillas de 20 px:
 * la última fila es el suelo (y = 300) y las de arriba van subiendo.
 *
 * Suelo (fila 0):  _ suelo   . foso   S suelo con resorte   M / N foso con extremo de plataforma móvil (normal / rápida)
 *                  k foso con tramo que se rompe
 * Resto:  # bloque sólido   X muro rompible (secreto)   = repisa fina   k tramo que se rompe
 *         ^ pinchos   j chorro de fuego   p péndulo   r roca que cae   c vela   R tesoro
 *         ? enemigo de suelo   f enemigo volador   S resorte
 * Cada plano deja 2 columnas libres de suelo a cada lado para que las estructuras encajen entre sí.
 */
object AdvStructs {
    class Bp(val id: Int, val name: String, val fam: Int, val tier: Int, val rows: Array<String>, val mirrored: Boolean) {
        val w = rows[0].length
        val h = rows.size
        val hasX = rows.any { it.contains('X') }
    }

    // familias
    const val F_STAIRS = 0
    const val F_WALL = 1
    const val F_PILLARS = 2
    const val F_PIT = 3
    const val F_FLOAT = 4
    const val F_BRIDGE = 5
    const val F_MOVER = 6
    const val F_SPIKES = 7
    const val F_TUNNEL = 8
    const val F_TOWER = 9
    const val F_LEDGES = 10
    const val F_SECRET = 11
    const val F_SPRING = 12
    const val F_JETS = 13
    const val F_PEND = 14
    const val F_ROCKS = 15
    const val F_GAUNT = 16
    const val F_CASTLE = 17
    const val F_TWOFLOOR = 18
    const val F_CHECKER = 19
    const val F_ISLANDS = 20
    const val F_PITWALL = 21
    const val F_ALTAR = 22
    const val F_HALL = 23
    const val F_CHICANE = 24
    const val F_MIXED = 25
    const val FAMS = 26
    val famNames = arrayOf("Escalinata", "Muro", "Pilares", "Foso", "Plataformas flotantes", "Puente de cristal", "Plataforma móvil",
        "Suelo de pinchos", "Túnel", "Torre", "Repisas", "Alcoba secreta", "Resorte", "Fuego", "Péndulos", "Rocas", "Emboscada",
        "Escalera gótica", "Dos plantas", "Tablero", "Islas", "Foso y muro", "Altar", "Gran salón", "Muros en zigzag", "Mixta")

    val all = ArrayList<Bp>()
    private val seen = HashSet<String>()

    /** Lienzo: j = 0 es el suelo, j = 1.. suben. */
    class Cv(val w: Int, val h: Int = 14) {
        val g = Array(h) { CharArray(w) { '.' } }
        init { for (x in 0 until w) g[0][x] = '_' }
        fun set(x: Int, j: Int, c: Char) { if (x in 0 until w && j in 0 until h) g[j][x] = c }
        fun get(x: Int, j: Int): Char = if (x in 0 until w && j in 0 until h) g[j][x] else '.'
        fun pit(x0: Int, x1: Int, c: Char = '.') { for (x in x0..x1) g[0][x] = c }
        fun box(x0: Int, x1: Int, j0: Int, j1: Int, c: Char = '#') { for (x in x0..x1) for (j in j0..j1) set(x, j, c) }
        fun plat(x0: Int, x1: Int, j: Int) { for (x in x0..x1) set(x, j, '=') }
        fun rows(): Array<String> {
            var top = h - 1
            while (top > 1 && g[top].all { it == '.' }) top--
            return Array(top + 1) { k -> String(g[top - k]) }
        }
    }

    private fun add(fam: Int, name: String, tier: Int, c: Cv) {
        val r = c.rows()
        val key = r.joinToString("/")
        if (!seen.add(key)) return
        all.add(Bp(all.size, name, fam, tier, r, false))
        val m = Array(r.size) { r[it].reversed().map { ch -> ch }.joinToString("") }
        val mk = m.joinToString("/")
        if (seen.add(mk)) all.add(Bp(all.size, "$name (espejo)", fam, tier, m, true))
    }

    /** Descripción corta para el verificador. */
    fun check(b: Bp): String? {
        val w = b.w
        if (w < 8) return "muy estrecho"
        if (b.rows.any { it.length != w }) return "filas desiguales"
        val g = b.rows[b.rows.size - 1]
        for (x in intArrayOf(0, 1, w - 2, w - 1)) if (g[x] != '_') return "extremo sin suelo"
        for (j in 0 until b.rows.size - 1) for (x in intArrayOf(0, 1, w - 2, w - 1)) if (b.rows[j][x] != '.') return "extremo ocupado"
        return null
    }

    init {
        // 1) Escalinata (sube y baja de a una casilla)
        for (h in 2..5) for (sw in 1..3) for (pl in intArrayOf(2, 5)) {
            val c = Cv(4 + 2 * h * sw + pl)
            var x = 2
            for (s in 1..h) { c.box(x, x + sw - 1, 1, s); x += sw }
            val p0 = x
            c.box(p0, p0 + pl - 1, 1, h)
            x += pl
            for (s in h downTo 1) { c.box(x, x + sw - 1, 1, s); x += sw }
            if (pl >= 5) { c.set(p0 + 1, h + 1, 'c'); c.set(p0 + pl - 2, h + 1, '?') } else c.set(p0 + 1, h + 1, 'c')
            add(F_STAIRS, "Escalinata ${h}x$sw", if (h >= 4) 1 else 0, c)
        }
        // 2) Muro
        for (h in 1..3) for (tw in 1..3) for (mode in 0..2) {
            val c = Cv(15 + tw)
            c.box(7, 6 + tw, 1, h)
            when (mode) {
                0 -> c.set(7, h + 1, 'c')
                1 -> { c.set(9 + tw, 1, '?') }
                else -> { c.set(9 + tw, 2, 'f'); c.set(4, 1, 'c') }
            }
            add(F_WALL, "Muro h$h g$tw", if (mode == 2 || h == 3) 1 else 0, c)
        }
        // 3) Pilares
        for (n in 2..4) for (ph in 2..3) for (sp in intArrayOf(4, 6)) for (mode in 0..1) {
            val w = 4 + n * (sp + 1) + 1
            val c = Cv(w)
            for (i in 0 until n) {
                val x = 4 + i * (sp + 1)
                c.box(x, x, 1, ph)
                if (mode == 0) { if (i % 2 == 0) c.set(x, ph + 1, 'c') } else if (i < n - 1) c.set(x + 1 + sp / 2, 1, '?')
            }
            add(F_PILLARS, "Pilares $n h$ph s$sp", if (n >= 4 || mode == 1) 1 else 0, c)
        }
        // 4) Foso
        for (g in 3..5) for (mode in 0..2) {
            val c = Cv(12 + g)
            c.pit(5, 4 + g)
            when (mode) { 1 -> c.set(5 + g / 2, 3, 'f'); 2 -> { c.set(2, 1, '?'); c.set(9 + g, 1, '?') } }
            add(F_PIT, "Foso $g", if (mode > 0) 1 else 0, c)
        }
        for (g in 6..9) for (mode in 0..1) {
            val c = Cv(10 + g)
            c.pit(5, 4 + g)
            val mid = 5 + g / 2
            c.plat(mid - 1, mid, 2)
            if (mode == 1) c.set(mid, 3, 'c') else c.set(2, 1, '?')
            add(F_PIT, "Foso con apoyo $g", 1, c)
        }
        // 5) Plataformas flotantes
        for (n in intArrayOf(3, 5)) for (pw in 2..3) for (sg in 2..4) for (pat in 0..2) {
            val pitW = n * pw + (n + 1) * sg
            val c = Cv(pitW + 10)
            c.pit(5, 4 + pitW)
            var x = 5 + sg
            for (i in 0 until n) {
                val j = when (pat) { 0 -> 2; 1 -> if (i % 2 == 0) 2 else 3; else -> 1 + minOf(i, n - 1 - i) }
                c.plat(x, x + pw - 1, j)
                if (i == n / 2) c.set(x + pw / 2, j + 1, 'c')
                x += pw + sg
            }
            add(F_FLOAT, "Flotantes n$n a$pw s$sg p$pat", if (sg >= 4 || n == 5) 1 else 0, c)
        }
        // 6) Puente de cristal
        for (n in 3..8) for (bat in 0..1) {
            val pitW = n * 2
            val c = Cv(pitW + 10)
            for (i in 0 until n) c.pit(5 + i * 2, 6 + i * 2, 'k')
            if (bat == 1) c.set(5 + n, 3, 'f')
            add(F_BRIDGE, "Puente $n", if (n >= 6 || bat == 1) 1 else 0, c)
        }
        // 7) Plataforma móvil
        for (p in intArrayOf(10, 11, 12, 14)) for (fast in 0..1) for (bat in 0..1) {
            val c = Cv(p + 10)
            c.pit(5, 4 + p)
            c.set(5 + 1, 0, if (fast == 1) 'N' else 'M')
            c.set(5 + p - 2, 0, if (fast == 1) 'N' else 'M')
            if (bat == 1) c.set(5 + p / 2, 3, 'f')
            add(F_MOVER, "Móvil $p", if (fast == 1 || bat == 1) 1 else 0, c)
        }
        // 8) Suelo de pinchos
        for (l in 2..4) for (mode in 0..1) {
            val c = Cv(15 + l)
            for (x in 7 until 7 + l) c.set(x, 1, '^')
            if (mode == 1) c.set(12 + l, 1, '?') else c.set(3, 1, 'c')
            add(F_SPIKES, "Pinchos $l", 1, c)
        }
        for (l in 5..9) for (mode in 0..1) {
            val c = Cv(14 + l)
            var x = 7
            while (x < 7 + l) {
                if ((x - 7) % 4 == 3) { c.set(x, 1, '#'); c.set(x, 2, if (mode == 1) 'c' else '.') } else c.set(x, 1, '^')
                x++
            }
            if (c.get(7 + l - 1, 1) == '#') c.set(7 + l - 1, 1, '^')
            add(F_SPIKES, "Pinchos con islas $l", 2, c)
        }
        // 9) Túnel
        for (len in intArrayOf(8, 12, 16)) for (ceil in 4..6) for (mode in 0..2) {
            if (mode == 2 && ceil == 4) continue
            val c = Cv(len + 8)
            for (x in 3 until 3 + len + 2) c.box(x, x, ceil, ceil + 1)
            when (mode) {
                0 -> { c.set(5, 1, '?'); if (len >= 12) c.set(3 + len - 2, 1, '?') }
                1 -> { c.set(4, 1, 'c'); c.set(3 + len / 2, 1, '?'); c.set(3 + len, 1, 'c') }
                else -> { val m = 3 + len / 2; c.pit(m, m + (if (ceil == 5) 1 else 2)); if (len >= 12) c.set(4, 1, '?') }
            }
            add(F_TUNNEL, "Túnel $len t$ceil", if (mode == 2) 2 else if (len >= 12) 1 else 0, c)
        }
        // 10) Torre opcional
        for (n in 3..5) for (right in 0..1) for (mode in 0..1) {
            val c = Cv(16)
            var x = if (right == 0) 4 else 9
            var j = 3
            for (i in 0 until n) {
                c.plat(x, x + 2, j)
                val last = i == n - 1
                if (last) { c.set(x + 1, j + 1, if (mode == 0) 'c' else 'R') } else if (mode == 1 && i == 1) c.set(x + 1, j + 1, '?')
                x += if (i % 2 == 0) (if (right == 0) 4 else -4) else (if (right == 0) -4 else 4)
                x = x.coerceIn(3, 11)
                j += 2
                if (j > 12) break
            }
            add(F_TOWER, "Torre $n", if (n >= 5) 1 else 0, c)
        }
        // 11) Repisas altas
        for (len in intArrayOf(10, 14, 18, 22)) for (mode in 0..1) {
            val c = Cv(len + 6)
            var x = 4
            var i = 0
            while (x + 3 < len + 3) {
                c.plat(x, x + 2, if (mode == 0) 4 else (if (i % 2 == 0) 4 else 5))
                if (i % 2 == 1) c.set(x + 1, (if (mode == 0) 5 else 6), 'c')
                x += 5
                i++
            }
            c.set(4 + len / 2, 1, '?')
            add(F_LEDGES, "Repisas $len", if (mode == 1) 1 else 0, c)
        }
        // 12) Alcoba secreta (muro rompible)
        for (w in intArrayOf(6, 8)) for (cav in 2..3) for (mode in 0..1) {
            val h = 3
            val c = Cv(w + 10)
            val x0 = 5
            c.box(x0, x0 + w - 1, 1, h)
            // cavidad interior
            c.box(x0 + 1, x0 + cav, 1, 2, '.')
            for (q in 1..cav) c.set(x0 + q, 1, 'R')
            c.set(x0, 1, 'X')
            if (mode == 1) c.set(x0 + w - 2, h + 1, 'c') else c.set(x0 + w + 1, 1, '?')
            add(F_SECRET, "Alcoba $w/$cav", if (mode == 1) 0 else 1, c)
        }
        // 13) Resorte hacia un estante alto
        for (j in 6..8) for (off in intArrayOf(3, 5)) for (mode in 0..1) {
            val c = Cv(18)
            c.set(5, 0, 'S')
            c.plat(5 + off, 8 + off, j)
            c.set(6 + off, j + 1, if (mode == 0) 'R' else 'c')
            if (mode == 1) c.set(12, 1, '?')
            add(F_SPRING, "Resorte $j", if (mode == 1) 1 else 0, c)
        }
        // 14) Corredor de fuego
        for (n in 2..4) for (sp in intArrayOf(5, 7)) for (mode in 0..1) {
            val w = 8 + n * sp
            val c = Cv(w)
            for (i in 0 until n) c.set(5 + i * sp, 1, 'j')
            if (mode == 1) { val m = 5 + sp / 2 + sp; if (m + 2 < w - 2 && n >= 3) c.pit(m, m + 2) }
            c.set(3, 1, 'c')
            add(F_JETS, "Fuego $n", if (n >= 3 || mode == 1) 2 else 1, c)
        }
        // 15) Péndulos
        for (n in 1..3) for (sp in intArrayOf(7, 9)) for (mode in 0..1) {
            val w = 8 + n * sp
            val c = Cv(w)
            for (i in 0 until n) {
                val x = 6 + i * sp
                c.set(x, 8, 'p')
                if (mode == 1 && i < n - 1) c.pit(x + sp / 2 - 1, x + sp / 2 + 1)
            }
            c.set(3, 1, 'c')
            add(F_PEND, "Péndulos $n", if (n >= 2 || mode == 1) 2 else 1, c)
        }
        // 16) Rocas
        for (n in 1..3) for (mode in 0..1) {
            val c = Cv(12 + n * 7)
            for (i in 0 until n) c.set(6 + i * 7, 11, 'r')
            if (mode == 1) c.set(8 + n * 7, 1, '?') else c.set(4, 1, 'c')
            add(F_ROCKS, "Rocas $n", if (n >= 2) 2 else 1, c)
        }
        // 17) Emboscada de enemigos
        for (len in intArrayOf(14, 18, 24)) for (k in 2..4) for (ledges in 0..1) {
            val c = Cv(len + 6)
            for (i in 0 until k) c.set(5 + (len - 4) * (i + 1) / (k + 1), 1, if (i % 3 == 2) 'f' else '?')
            if (ledges == 1) { c.plat(5, 8, 4); c.plat(len - 4, len - 1, 4); c.set(6, 5, 'c'); c.set(len - 3, 5, 'c') }
            add(F_GAUNT, "Patrulla $len/$k", if (k >= 3) 2 else 1, c)
        }
        // 18) Escalera gótica (peldaños finos)
        for (n in 4..7) for (mode in 0..1) {
            val w = 6 + n * 4 + 3
            val c = Cv(w)
            var x = 3
            for (i in 1..n) { c.plat(x, x + 1, i + 1); x += 2 }
            c.plat(x, x + 2, n + 1)
            if (mode == 1) c.set(x + 1, n + 2, '?') else c.set(x + 1, n + 2, 'c')
            x += 3
            for (i in n downTo 1) { c.plat(x, x + 1, i + 1); x += 2 }
            add(F_CASTLE, "Escalera gótica $n", if (n >= 6) 1 else 0, c)
        }
        // 19) Dos plantas
        for (len in intArrayOf(16, 22, 28)) for (mode in 0..1) {
            val c = Cv(len + 6)
            var x = 4
            var i = 0
            while (x + 5 < len + 3) {
                if (i % 3 != 2) c.plat(x, x + 4, 5)
                if (i % 3 == 0) c.set(x + 2, 6, '?')
                x += 6
                i++
            }
            c.set(4 + len / 3, 1, '?')
            if (mode == 1) c.set(4 + 2 * len / 3, 1, 'f') else c.set(4 + 2 * len / 3, 1, 'c')
            add(F_TWOFLOOR, "Dos plantas $len", 1 + mode, c)
        }
        // 20) Tablero
        for (n in 3..7) for (mode in 0..1) {
            val w = 8 + n * 5
            val c = Cv(w)
            for (i in 0 until n) {
                val x = 4 + i * 5
                c.box(x, x + 1, 1, 1)
                if (i < n - 1) for (k in 2..4) c.set(x + k, 1, if (mode == 0) '.' else '^')
                if (mode == 1 && i == n / 2) c.set(x, 2, 'c')
            }
            add(F_CHECKER, "Tablero $n", if (mode == 1) 2 else 1, c)
        }
        // 21) Islas entre fosos
        for (pw in 3..4) for (iw in 2..3) for (mode in 0..1) {
            val c = Cv(10 + 3 * pw + 2 * iw)
            var x = 5
            for (k in 0 until 3) {
                c.pit(x, x + pw - 1)
                x += pw
                if (k < 2) { if (mode == 1) c.set(x + iw / 2, 1, '?'); x += iw }
            }
            if (mode == 0) c.set(5 + pw + iw / 2, 1, 'c')
            add(F_ISLANDS, "Islas $pw/$iw", if (pw == 4) 2 else 1, c)
        }
        // 22) Foso y muro
        for (g in 3..4) for (wh in 2..3) for (mode in 0..1) {
            val c = Cv(16 + g)
            c.pit(5, 4 + g)
            c.box(6 + g + 2, 6 + g + 3, 1, wh)
            if (mode == 1) c.set(6 + g + 5, 1, '?') else c.set(6 + g + 2, wh + 1, 'c')
            add(F_PITWALL, "Foso y muro $g/$wh", 2, c)
        }
        // 23) Altar de velas
        for (n in 3..6) for (mode in 0..1) {
            val c = Cv(10 + n * 3)
            for (i in 0 until n) {
                val x = 5 + i * 3
                c.set(x, 1, 'c')
                if (mode == 1 && i % 2 == 1) { c.box(x + 1, x + 1, 1, 1); c.set(x + 1, 2, 'c') }
            }
            if (mode == 0) c.set(6 + n * 3, 1, '?')
            add(F_ALTAR, "Altar $n", 0, c)
        }
        // 24) Muros en zigzag
        for (n in 3..6) for (mode in 0..2) {
            val c = Cv(6 + n * 7)
            for (i in 0 until n) {
                val h = when (mode) { 0 -> 1 + i % 3; 1 -> 3 - i % 3; else -> if (i % 2 == 0) 2 else 1 }
                val x = 5 + i * 7
                c.box(x, x, 1, h)
                if (mode == 2 && i % 2 == 1) c.set(x + 3, 1, '?')
            }
            add(F_CHICANE, "Zigzag $n/$mode", if (mode == 1 || n >= 5) 1 else 0, c)
        }
        // 25) Mixtas: combinaciones de fosos, muros y pinchos con ritmo creciente
        val mixA = arrayOf("p", "wall", "pit", "spk")
        for (a in 0..3) for (b in 0..3) {
            if (a == b || (a == 2 && b == 1)) continue
            val c = Cv(44)
            var x = 4
            for (k in intArrayOf(a, b, a)) {
                when (k) {
                    0 -> { c.pit(x, x + 3); x += 4 + 6 }
                    1 -> { c.box(x, x, 1, 2); x += 1 + 6 }
                    2 -> { c.plat(x, x + 1, 2); c.pit(x - 1, x + 4); x += 6 + 4 }
                    else -> { for (q in x until x + 3) c.set(q, 1, '^'); x += 3 + 4 }
                }
            }
            c.set(40, 1, 'c')
            add(F_MIXED, "Mixta ${mixA[a]}-${mixA[b]}", 2, c)
        }
        // 26) Gran salón (estructura grande)
        for (v in 0..5) {
            val w = 36 + v * 3
            val c = Cv(w)
            val sp = 6 + v % 3
            var x = 4
            var i = 0
            val m = w / 2
            while (x < w - 6) {
                if (x in (m - 3)..(m + 4)) { x += sp; i++; continue }
                c.box(x, x, 1, 3)
                if (i % 2 == 0) c.plat(x + 1, x + 3, 5 + v % 2)
                c.set(x + 2, 1, if (i % 3 == 1) 'f' else '?')
                if (i % 2 == 0) c.set(x + 2, 6 + v % 2, 'c')
                x += sp
                i++
            }
            c.pit(m, m + 3)
            c.plat(m, m + 3, 2)
            add(F_HALL, "Gran salón $v", 2, c)
        }
    }

    fun famCount(f: Int) = all.count { it.fam == f }
}
