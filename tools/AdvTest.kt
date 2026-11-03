import com.nioro.spectrumcolor.*

fun supportAt(a: Adv, x: Float): Boolean {
    if (a.groundBelow(x, a.py - 5f) < a.py + 20f) return true
    for (c in a.crumbles) if (c.st != 2 && x >= c.x0 - 2f && x <= c.x1 + 2f && Math.abs(c.y - a.py) < 12f) return true
    for (m in a.movers) if (Math.abs(x - m.x) <= m.w / 2f && Math.abs(m.y - a.py) < 12f) return true
    return false
}

/** Objetivo de navegación: x destino, y si hay que pulsar ATAQUE al llegar (escalera). */
class Goal(val tx: Float, val door: Boolean)

fun passable(a: Adv, r: AdvRoom, d: Int): Boolean = r.nb[d] >= 0 && (!r.lock[d] || a.keys > 0)

fun planGoal(a: Adv): Goal {
    val rooms = a.rooms
    // objetivo: llave más cercana si falta, si no el jefe
    val bfs = IntArray(rooms.size) { -2 }
    val dirTo = IntArray(rooms.size) { -1 } // primer paso desde la sala actual
    val q = ArrayList<Int>()
    q.add(a.cur); bfs[a.cur] = -1
    var h = 0
    var goal = -1
    val needKey = rooms.any { it.kind == Adv.R_KEY && !it.taken }
    // tesoros (vida máxima, botas, látigo) a pocas salas: vale la pena desviarse
    val dist = IntArray(rooms.size) { 99 }
    dist[a.cur] = 0
    while (h < q.size) {
        val rr = rooms[q[h++]]
        val treas = !a.god && rr.kind == Adv.R_TREAS && !rr.taken && dist[rr.id] <= 3
        val isGoal = treas || if (a.keys == 0 && needKey && lockAhead(a)) (rr.kind == Adv.R_KEY && !rr.taken) else (rr.kind == Adv.R_BOSS)
        if (isGoal) { goal = rr.id; break }
        for (d in 0..3) {
            val n = rr.nb[d]
            if (n < 0 || bfs[n] != -2) continue
            if (rr.lock[d] && a.keys == 0) continue
            bfs[n] = rr.id
            dist[n] = dist[rr.id] + 1
            dirTo[n] = if (rr.id == a.cur) d else dirTo[rr.id]
            q.add(n)
        }
    }
    if (goal == -1) return Goal(a.roomLen / 2f, false)
    if (goal == a.cur) {
        val it = a.items.firstOrNull { it.prize }
        return Goal(it?.x ?: (a.roomLen - 30f), false)
    }
    val d = dirTo[goal]
    val r = rooms[a.cur]
    return when (d) {
        Adv.D_W -> Goal(-60f, false)
        Adv.D_E -> Goal(a.roomLen + 60f, false)
        else -> Goal(a.doors.firstOrNull { it.to == r.nb[d] }?.x ?: a.roomLen / 2f, true)
    }
}

/** ¿hay alguna puerta con llave entre aquí y el jefe? */
fun lockAhead(a: Adv): Boolean = a.rooms.any { r -> (0..3).any { r.lock[it] } }

fun bot(a: Adv, inp: AdvInput, t: Int, g: Goal) {
    inp.left = false; inp.right = false; inp.jumpHeld = false
    val d0 = if (g.tx >= a.px) 1 else -1
    fun go(dir: Int) { if (dir > 0) inp.right = true else inp.left = true }
    if (a.bossStarted && a.bstate == Adv.BS_DEAD) {
        val o = a.items.firstOrNull { it.kind == Adv.IT_ORB }
        val tx = o?.x ?: (a.arenaX + 320f)
        go(if (tx > a.px) 1 else -1)
        return
    }
    if (a.bossStarted) {
        val dx = a.bx - a.px
        if (Math.abs(dx) > 70f) go(if (dx > 0) 1 else -1) else if (Math.abs(dx) < 40f) go(if (dx > 0) -1 else 1)
        if (t % 20 == 0) { inp.atkPressed = true; go(if (dx > 0) 1 else -1) }
        if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] && t % 70 == 0) inp.spPressed = true
        for (p in a.projs) if (!p.mine && Math.abs(p.x - a.px) < 60f && a.onGround) inp.jumpPressed = true
        inp.jumpHeld = true
        return
    }
    // emboscada: perseguir y atacar
    val amb = a.ambushes.firstOrNull { it.st == 1 }
    if (amb != null) {
        val tg = amb.mine.filter { !it.dead }.minByOrNull { Math.abs(it.x - a.px) }
        if (tg != null) {
            val dx = tg.x - a.px
            if (!a.god && threats(a, inp, t, if (dx > 0) 1 else -1)) {
                if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] && t % 25 == 0) inp.spPressed = true
                inp.jumpHeld = true; return
            }
            go(if (dx > 0) 1 else -1)
            if (t % 8 == 0 && Math.abs(dx) < 75f) inp.atkPressed = true
            if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] && t % 60 == 0) inp.spPressed = true
            if (Math.abs(a.py - tg.y) > 40f && a.onGround && t % 40 == 0) inp.jumpPressed = true
            inp.jumpHeld = true
            return
        }
    }
    // escalera: al llegar, ATAQUE
    if (g.door && Math.abs(a.px - g.tx) < 8f && a.onGround) { inp.atkPressed = true; return }
    if (!a.god && threats(a, inp, t, d0)) { inp.jumpHeld = true; return }
    // objetivo cerca (tesoro): se detiene al llegar
    // si el premio quedó abajo (estoy sobre una repisa), seguir caminando para bajar
    var go = !(Math.abs(g.tx - a.px) < 6f)
    var d = d0
    if (a.py < AdvCfg.GROUND - 10f && a.onGround && a.riding == null && !g.door) {
        val pl = a.plats.firstOrNull { Math.abs(it.y - a.py) < 1f && a.px >= it.x0 - 12f && a.px <= it.x1 + 12f && g.tx >= it.x0 - 4f && g.tx <= it.x1 + 4f }
        if (pl != null && a.items.any { it.prize && Math.abs(it.x - g.tx) < 2f && it.y > a.py + 10f }) { d = if (a.px - pl.x0 < pl.x1 - a.px) -1 else 1; go = true }
    }
    for (j in a.jets) { val dd = (j.x - a.px) * d; if (dd in 14f..90f && (j.warn || j.on)) go = false }
    for (r in a.rocks) { val dd = (r.x - a.px) * d; if (dd in -15f..110f && (r.st == 1 || r.st == 2)) go = false }
    for (p in a.pends) if (Math.abs(p.bx - a.px) < 45f && p.by > AdvCfg.GROUND - 95f && a.onGround) inp.jumpPressed = true
    // borde de una repisa con suelo firme abajo: bajarse, no saltar
    val pit = !supportAt(a, a.px + 3f * d) && a.onGround && !(a.py < AdvCfg.GROUND - 10f && a.groundBelow(a.px + 14f * d, a.py) <= AdvCfg.GROUND + 1f && a.items.any { it.prize })
    if (pit) {
        val m = a.movers.filter { (it.x + it.w / 2f - a.px) * d > -5f && (it.x - it.w / 2f - a.px) * d < 260f }.minByOrNull { Math.abs(it.x - a.px) }
        if (m != null) {
            val edge = if (d > 0) m.x - m.w / 2f - a.px else a.px - (m.x + m.w / 2f)
            val sameLevel = Math.abs(m.y - a.py) < 5f
            go = edge > 34f || (sameLevel && edge in -2f..34f) || (a.riding != null && !a.riding!!.vertical)
        } else inp.jumpPressed = true
    }
    val r = a.riding
    if (r != null && r.vertical) {
        go = a.plats.any { (if (d > 0) it.x0 - a.px else a.px - it.x1) in -10f..140f && Math.abs(it.y - r.y) < 5f }
    }
    // plataforma móvil sobre suelo firme: basta con bajarse caminando
    val solidBelow = r != null && !r.vertical && a.groundBelow(a.px + 30f * d, r.y + 2f) <= AdvCfg.GROUND + 1f &&
        a.groundBelow(a.px + 60f * d, r.y + 2f) <= AdvCfg.GROUND + 1f
    if (r != null && !r.vertical && !solidBelow) {
        val far = if (d > 0) a.plats.filter { it.x0 > r.x }.minByOrNull { it.x0 } else a.plats.filter { it.x1 < r.x }.maxByOrNull { it.x1 }
        if (far != null) {
            val gap = if (d > 0) far.x0 - (r.x + r.w / 2f) else (r.x - r.w / 2f) - far.x1
            go = gap < 20f && Math.abs(far.y - r.y) < 6f
        }
    }
    // en el aire: si hay suelo justo debajo y estamos cayendo, frenar para aterrizar en plataformas pequeñas
    if (!a.onGround && a.pvy > 0f && a.riding == null) {
        var bp: AdvPlat? = null
        for (q in a.plats) if (a.px >= q.x0 && a.px <= q.x1 && q.y >= a.py - 1f && (bp == null || q.y < bp.y) && a.blocks.none { b -> Math.abs(b.y0 - q.y) < 1f && q.x0 >= b.x0 - 1f && q.x1 <= b.x1 + 1f }) bp = q
        if (bp != null && (bp.y < AdvCfg.GROUND - 8f || bp.x1 - bp.x0 < 110f)) go = false
    }
    if (go) go(d) else if (a.ice && a.pvx * d > 30f && a.onGround) go(-d)
    inp.jumpHeld = true
    for (e in a.enemies) if (!e.dead && Math.abs(e.x - a.px) < 75f && Math.abs(e.y - a.py) < 60f) inp.atkPressed = true
    for (e in a.enemies) if (!e.dead && Math.abs(e.x - a.px) < 90f && e.y < a.py - 40f && e.y > a.py - 130f && a.onGround && a.riding == null) { inp.jumpPressed = true; inp.atkPressed = true }
    if (!(a.onGround && !supportAt(a, a.px + 40f * d))) for (c in a.candles) if (c.alive && (c.x - a.px) * d in 0f..60f) inp.atkPressed = true
    for (b in a.blocks) { val dd = if (d > 0) b.x0 - a.px else a.px - b.x1; if (dd in -2f..40f && b.y0 < a.py - 2f && b.y1 > a.py - 30f && a.onGround) inp.jumpPressed = true }
    for (s in a.spikes) { val dd = if (d > 0) s[0] - a.px else a.px - s[1]; if (dd in 0f..50f && a.onGround) inp.jumpPressed = true }
    if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] + 3 && t % 90 == 0) inp.spPressed = true
}

/**
 * Peligros (solo sin modo dios): devuelve true si ya decidió qué hacer este paso.
 * Esquiva ondas y proyectiles, desvía con el látigo, se detiene a pelear en vez de chocar y pisa a los acorazados.
 */
fun threats(a: Adv, inp: AdvInput, t: Int, d: Int): Boolean {
    fun go(dir: Int) { if (dir > 0) inp.right = true else inp.left = true }
    // ondas por el suelo que vienen hacia mí: saltar a tiempo
    for (h in a.hazs) if (!h.mine && h.vx != 0f && h.warn <= 0f) {
        val hx = (h.x0 + h.x1) / 2f
        val coming = (a.px - hx) * h.vx > 0f
        if (coming && Math.abs(a.px - hx) < 46f && Math.abs(h.y1 - a.py) < 10f && a.onGround) { inp.jumpPressed = true; return false }
    }
    // rayos que avisan: alejarse
    for (h in a.hazs) if (!h.mine && h.vx == 0f && h.warn > 0f && a.px > h.x0 - 14f && a.px < h.x1 + 14f && h.y1 >= a.py - 5f) {
        go(if (a.px < (h.x0 + h.x1) / 2f) -1 else 1); return true
    }
    // proyectiles enemigos cerca
    for (p in a.projs) if (!p.mine) {
        val dx = p.x - a.px
        val dy = p.y - (a.py - 15f)
        val coming = dx * p.vx < 0f || Math.abs(dx) < 16f
        if (!coming || Math.abs(dx) > 70f || Math.abs(dy) > 60f) continue
        if (p.kind >= 7 && dx * a.face > 0f && Math.abs(dy) < 26f && a.atkCd <= 0f) { inp.atkPressed = true; return false }
        if (a.onGround && Math.abs(dy) < 24f && Math.abs(p.vy) < 200f) { inp.jumpPressed = true; return false }
    }
    // volador encima (bombardero, calavera, murciélago): saltar y golpear
    for (f in a.enemies) if (!f.dead && a.flyer(f.type)) {
        val fdx = Math.abs(f.x - a.px)
        val fdy = a.py - f.y
        if (fdx < a.whipReach - 6f && fdy in 50f..140f && a.onGround) {
            val toF = if (f.x >= a.px) 1 else -1
            if (a.face != toF) go(toF)
            inp.jumpPressed = true
            return true
        }
        if (fdx < a.whipReach - 6f && !a.onGround && Math.abs((a.py - 18f) - (f.y - a.eh(f) / 2f)) < 30f && a.atkCd <= 0f) { inp.atkPressed = true; return true }
    }
    // enemigo delante: pelear antes de chocar
    var best: AdvEnemy? = null
    for (e in a.enemies) {
        if (e.dead) continue
        if (e.type == Adv.E_POP && e.st == 0) continue
        val dx = (e.x - a.px) * d
        if (dx < -20f || dx > a.whipReach + 50f) continue
        if (Math.abs((e.y - a.eh(e) / 2f) - (a.py - 18f)) > 55f) continue
        if (best == null || Math.abs(e.x - a.px) < Math.abs(best.x - a.px)) best = e
    }
    val e = best ?: return false
    val dx = Math.abs(e.x - a.px)
    if (e.type == Adv.E_MAGE && e.vis < 0.5f) { go(-d); return true } // esperar a que aparezca
    val shield = e.type == Adv.E_KNIGHT && (a.px - e.x) * e.face > 0f && e.st != 2
    if (shield && a.onGround && dx < 70f && dx > 20f && t % 6 == 0 && a.stompable(e)) {
        inp.jumpPressed = true; inp.atkPressed = dx < a.whipReach; go(d); return true // saltar encima (pisotón)
    }
    if (e.type == Adv.E_GOLEM && e.st == 1 && dx < 120f) { go(-d); return true } // va a golpear el piso: retroceder
    if (dx < a.whipReach - 4f) {
        val toE = if (e.x >= a.px) 1 else -1
        val keep = if (e.type == Adv.E_GOLEM) a.ew(e) / 2f + 22f else 16f
        if (a.face != toE) go(toE) // mirar hacia el enemigo antes de atacar
        else if (dx < keep) go(-toE) // demasiado cerca: retroceder un poco
        if (a.atkCd <= 0f) inp.atkPressed = true
        if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] + 2 && t % 40 == 0) inp.spPressed = true
        return true
    }
    // enemigo que viene hacia mí: esperarlo un instante en vez de chocarlo
    val comingAt = (e.type == Adv.E_WALKER || e.type == Adv.E_CHARGER || e.type == Adv.E_WOLF || e.type == Adv.E_SLIME) && (a.px - e.x) * e.face > 0f
    if (comingAt && dx < a.whipReach + 30f && t % 30 < 22) return true
    return false
}

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
