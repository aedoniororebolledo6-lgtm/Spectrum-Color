import com.nioro.spectrumcolor.*

fun structure(a: Adv): String {
    // simulación abstracta: ¿se llega al jefe recogiendo llaves y abriendo puertas?
    val rooms = a.rooms
    val seen = BooleanArray(rooms.size)
    val lk = Array(rooms.size) { rooms[it].lock.copyOf() }
    val taken = BooleanArray(rooms.size)
    var keys = 0
    var changed = true
    seen[0] = true
    while (changed) {
        changed = false
        for (r in rooms) if (seen[r.id]) {
            if (r.kind == Adv.R_KEY && !taken[r.id]) { taken[r.id] = true; keys++; changed = true }
            for (d in 0..3) {
                val n = r.nb[d]
                if (n < 0 || seen[n]) continue
                if (lk[r.id][d]) { if (keys > 0) { keys--; lk[r.id][d] = false; lk[n][d xor 1] = false } else continue }
                seen[n] = true; changed = true
            }
        }
    }
    val allSeen = seen.all { it }
    val locks = rooms.sumOf { r -> r.lock.count { it } } / 2
    val keyRooms = rooms.count { it.kind == Adv.R_KEY }
    val nb = rooms.count { it.kind == Adv.R_BOSS }
    return "salas=${rooms.size} llaves=$keyRooms puertas=$locks guardado=${rooms.count { it.kind == Adv.R_SAVE }} tesoros=${rooms.count { it.kind == Adv.R_TREAS }} jefe=$nb alcanzable=${seen[a.bossId]} todas=$allSeen"
}

fun main(args: Array<String>) {
    val god = args.isNotEmpty() && args[0] == "god"
    val vwl = if (args.size > 1) args[1].toFloat() else 640f
    val mode = if (args.size > 2) args[2] else "full"
    var okAll = true
    for (w in 0 until AdvCfg.WORLDS) {
        val a0 = Adv(w, vwl, god)
        val st = structure(a0)
        if (!st.contains("alcanzable=true") || !st.contains("todas=true")) okAll = false
        // guardar / cargar progreso
        run {
            a0.rooms[1 % a0.rooms.size].visited = true; a0.keys = 1; a0.maxHp = 16
            val b = Adv(w, vwl, false)
            if (!b.loadState(a0.saveState()) || b.keys != 1 || b.maxHp != 16 || !b.rooms[1 % b.rooms.size].visited) { println("  guardado/carga FALLA"); okAll = false }
            a0.keys = 0; a0.maxHp = 12; a0.rooms[1 % a0.rooms.size].visited = false
        }
        if (mode == "structs") {
            if (w > 0) continue
            var bad = 0
            val byFam = IntArray(AdvStructs.FAMS)
            for (bp in AdvStructs.all) for (side in 0..1) for (wd in intArrayOf(5, 8, 11)) {
                val a = Adv(wd, vwl, true)
                a.whip = 2
                a.testStruct(bp.id, side)
                val inp = AdvInput()
                var t = 0
                var done = false
                val g = Goal(if (side == 0) 1e6f else -1e6f, false)
                try {
                    while (t < 120 * 90 && !done) {
                        bot(a, inp, t / 4, g)
                        a.step(1f / 30f, inp)
                        a.events.clear(); inp.clearPressed()
                        t += 4
                        if (side == 0 && a.px > a.roomLen - 300f) done = true
                        if (side == 1 && a.px < 300f) done = true
                    }
                } catch (e: Exception) { e.printStackTrace(); okAll = false }
                if (!done) { bad++; byFam[bp.fam]++; if (bad <= 80) println("  ${bp.id} ${bp.name} lado=$side mundo=$wd NO: px=${a.px.toInt()} t=${"%.0f".format(a.time)}") }
            }
            println("estructuras=${AdvStructs.all.size} malas(en alguna prueba)=$bad")
            println(byFam.withIndex().filter { it.value > 0 }.joinToString { "${AdvStructs.famNames[it.index]}=${it.value}" })
            if (bad > 0) okAll = false
            continue
        }
        if (mode == "rooms") {
            // cada sala, en los dos sentidos
            var bad = 0
            for (r in a0.rooms) for (side in 0..1) {
                if (r.kind == Adv.R_BOSS && side == 1) continue
                val a = Adv(w, vwl, true)
                a.whip = 2
                a.warp(r.id, side)
                val inp = AdvInput()
                var t = 0
                val goalX = if (side == 0) a.roomLen - 20f else 20f
                val g = Goal(if (r.kind == Adv.R_BOSS) 1e6f else if (side == 0) a.roomLen + 60f else -60f, false)
                var done = false
                try {
                    while (t < 120 * 150 && !done) {
                        bot(a, inp, t / 4, g)
                        a.step(1f / 30f, inp)
                        a.events.clear(); inp.clearPressed()
                        t += 4
                        if (System.getProperty("trace") == "${w}:${r.id}:$side" && t % 60 == 0) println("   t=${t} px=${a.px.toInt()} py=${a.py.toInt()} gr=${a.onGround} tm=${a.time} pvy=${a.pvy} pvx=${a.pvx} hp=${a.hp} cur=${a.cur}")
                        if (r.kind == Adv.R_BOSS) { if (a.won) done = true }
                        else if (a.cur != r.id || (side == 0 && a.px > a.roomLen - 25f) || (side == 1 && a.px < 25f)) done = true
                    }
                } catch (e: Exception) { e.printStackTrace(); okAll = false }
                if (!done) { bad++; println("  sala ${r.id} (${r.kind}) lado=$side NO se atraviesa: px=${a.px.toInt()} len=${a.roomLen.toInt()} t=${"%.0f".format(a.time)}") }
            }
            println("mundo $w: $st malas=$bad")
            if (bad > 0) okAll = false
            continue
        }
        // sin modo dios: hasta 3 intentos con distinto ritmo de reacción (como jugadores distintos);
        // el mundo "se puede pasar" si alguno gana. Se informa el primero que gana.
        val tries = if (god) 1 else 3
        var a = a0
        var tryN = 0
        var worstRoomT = 0f
        for (k in 0 until tries) {
            tryN = k + 1
            a = if (k == 0) a0 else Adv(w, vwl, god)
            if (god) a.whip = 2
            val inp = AdvInput()
            var t = 0
            val max = 120 * 3000
            val phase = intArrayOf(0, 7, 13)[k]
            worstRoomT = 0f
            var roomT = 0f
            var lastRoom = a.cur
            try {
                while (!a.won && t < max) {
                    val g = planGoal(a)
                    bot(a, inp, t / 4 + phase, g)
                    a.step(1f / 30f, inp)
                    a.events.clear()
                    inp.clearPressed()
                    t += 4
                    roomT += 4f / 120f
                    if (a.cur != lastRoom) { lastRoom = a.cur; if (roomT > worstRoomT) worstRoomT = roomT; roomT = 0f }
                }
            } catch (e: Exception) { e.printStackTrace(); okAll = false }
            if (a.won) break
        }
        val visited = a.rooms.count { it.visited }
        println("mundo $w ${AdvCfg.bosses[w].name}: won=${a.won} intento=$tryN muertes=${a.deaths} t=${"%.0f".format(a.time)}s salasVisitadas=$visited/${a.rooms.size} llaves=${a.keys} maxHp=${a.maxHp} peorSala=${"%.0f".format(worstRoomT)}s | $st")
        if (!a.won) okAll = false
    }
    println(if (okAll) "TODO OK" else "FALLOS")
}
