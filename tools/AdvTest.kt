import com.nioro.spectrumcolor.*

fun supportAt(a: Adv, x: Float): Boolean {
    if (a.groundBelow(x, a.py - 5f) < a.py + 20f) return true
    for (c in a.crumbles) if (c.st != 2 && x >= c.x0 - 2f && x <= c.x1 + 2f && Math.abs(c.y - a.py) < 12f) return true
    for (m in a.movers) if (Math.abs(x - m.x) <= m.w / 2f && Math.abs(m.y - a.py) < 12f) return true
    return false
}

fun bot(a: Adv, inp: AdvInput, t: Int) {
    inp.left = false; inp.right = false; inp.jumpHeld = false
    if (a.bossStarted && a.bstate == Adv.BS_DEAD) {
        val o = a.items.firstOrNull { it.kind == Adv.IT_ORB }
        val tx = o?.x ?: (a.arenaX + 320f)
        if (tx > a.px) inp.right = true else inp.left = true
        return
    }
    if (a.bossStarted) {
        val dx = a.bx - a.px
        if (Math.abs(dx) > 70f) { if (dx > 0) inp.right = true else inp.left = true } else if (Math.abs(dx) < 40f) { if (dx > 0) inp.left = true else inp.right = true }
        if (t % 20 == 0) { inp.atkPressed = true; if (dx > 0) inp.right = true else inp.left = true }
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
            if (Math.abs(dx) > 50f) { if (dx > 0) inp.right = true else inp.left = true }
            else { if (dx > 0) inp.right = true else inp.left = true }
            if (t % 8 == 0 && Math.abs(dx) < 75f) inp.atkPressed = true
            if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] && t % 60 == 0) inp.spPressed = true
            if (Math.abs(a.py - tg.y) > 40f && a.onGround && t % 40 == 0) inp.jumpPressed = true
            inp.jumpHeld = true
            return
        }
    }
    var go = true
    // fuego
    for (j in a.jets) { val d = j.x - a.px; if (d in 14f..90f && (j.warn || j.on || (j.on.not() && false))) go = false }
    // rocas
    for (r in a.rocks) { val d = r.x - a.px; if (d in -15f..110f && (r.st == 1 || r.st == 2)) go = false }
    // péndulos: saltar cuando la cuchilla pasa baja cerca
    for (p in a.pends) if (Math.abs(p.bx - a.px) < 45f && p.by > AdvCfg.GROUND - 95f && a.onGround) inp.jumpPressed = true
    // fosos
    val pit = !supportAt(a, a.px + 26f) && a.onGround
    if (pit) {
        val m = a.movers.filter { it.x + it.w / 2f > a.px - 5f && it.x - it.w / 2f < a.px + 260f }.minByOrNull { Math.abs(it.x - a.px) }
        if (m != null) {
            val edge = m.x - m.w / 2f - a.px
            val sameLevel = Math.abs(m.y - a.py) < 5f
            go = edge > 34f || (sameLevel && edge in -2f..34f) || (a.riding != null && !a.riding!!.vertical)
        } else inp.jumpPressed = true
    }
    // sobre plataforma móvil vertical: esperar a la cornisa
    val r = a.riding
    if (r != null && r.vertical) {
        go = a.plats.any { it.x0 - a.px in -10f..140f && Math.abs(it.y - r.y) < 5f }
    }
    if (r != null && !r.vertical) {
        val far = a.plats.filter { it.x0 > r.x }.minByOrNull { it.x0 }
        if (far != null) go = far.x0 - (r.x + r.w / 2f) < 20f && Math.abs(far.y - r.y) < 6f
    }
    if (go) inp.right = true else if (a.ice && a.pvx > 30f && a.onGround) inp.left = true
    inp.jumpHeld = true
    for (e in a.enemies) if (!e.dead && Math.abs(e.x - a.px) < 75f && Math.abs(e.y - a.py) < 60f) inp.atkPressed = true
    for (e in a.enemies) if (!e.dead && Math.abs(e.x - a.px) < 90f && e.y < a.py - 40f && e.y > a.py - 130f && a.onGround && a.riding == null) { inp.jumpPressed = true; inp.atkPressed = true }
    for (c in a.candles) if (c.alive && c.x - a.px in 0f..60f) inp.atkPressed = true
    for (s in a.spikes) if (s[0] - a.px in 0f..50f && a.onGround) inp.jumpPressed = true
    if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] + 3 && t % 90 == 0) inp.spPressed = true
}

fun main(args: Array<String>) {
    val god = args.isNotEmpty() && args[0] == "god"
    val vwl = if (args.size > 1) args[1].toFloat() else 640f
    var okAll = true
    for (w in 0 until AdvCfg.WORLDS) {
        val a = Adv(w, vwl, god)
        if (god) a.whip = 2
        val inp = AdvInput()
        var t = 0
        val max = 120 * 900
        var stuck = 0f
        var lastX = a.px
        var worst = 0f
        try {
            while (!a.won && t < max) {
                bot(a, inp, t / 4)
                a.step(1f / 30f, inp)
                a.events.clear()
                inp.clearPressed()
                t += 4
                if (t % 480 == 0) { if (Math.abs(a.px - lastX) < 3f && !a.bossStarted) stuck += 4f else stuck = 0f; lastX = a.px; if (stuck > worst) worst = stuck }
            }
        } catch (e: Exception) { e.printStackTrace(); okAll = false }
        println("mundo $w ${AdvCfg.bosses[w].name}: won=${a.won} muertes=${a.deaths} t=${"%.0f".format(a.time)}s bossHp=${a.bhp}/${a.bmax} px=${a.px.toInt()} arena=${a.arenaX.toInt()} amb=${a.ambushes.count{it.st==2}}/${a.ambushes.size} len=${a.arenaX.toInt()} stuckMax=${worst.toInt()}s")
        if (!a.won) okAll = false
    }
    println(if (okAll) "TODO OK" else "FALLOS")
}
