import com.nioro.spectrumcolor.*

fun bot(a: Adv, inp: AdvInput, t: Int) {
    inp.left = false; inp.right = false; inp.jumpHeld = false
    val ahead = a.face
    // objetivo
    var bossFight = a.bossStarted
    if (bossFight && a.bstate == Adv.BS_DEAD) {
        val o = a.items.firstOrNull { it.kind == Adv.IT_ORB }
        val tx = o?.x ?: (a.arenaX + 320f)
        if (tx > a.px) inp.right = true else inp.left = true
        return
    }
    if (bossFight) {
        val dx = a.bx - a.px
        if (Math.abs(dx) > 70f) { if (dx > 0) inp.right = true else inp.left = true } else if (Math.abs(dx) < 40f) { if (dx > 0) inp.left = true else inp.right = true }
        if (t % 20 == 0) { inp.atkPressed = true; if (dx > 0) inp.right = true else inp.left = true }
        if (a.sub != 0 && a.hearts >= AdvCfg.subCost[a.sub] && t % 70 == 0) inp.spPressed = true
        // esquiva simple: saltar cuando hay proyectil cerca
        for (p in a.projs) if (!p.mine && Math.abs(p.x - a.px) < 60f && a.onGround) inp.jumpPressed = true
        inp.jumpHeld = true
        return
    }
    inp.right = true
    // foso delante
    val gx = a.px + 28f
    if (a.onGround && a.groundBelow(gx, a.py - 5f) > a.py + 20f) { inp.jumpPressed = true }
    inp.jumpHeld = true
    for (e in a.enemies) if (!e.dead && Math.abs(e.x - a.px) < 75f && Math.abs(e.y - a.py) < 60f) {
        inp.atkPressed = true
        if (e.x < a.px && !inp.left) { inp.left = false }
    }
    for (c in a.candles) if (c.alive && c.x - a.px in 0f..60f) inp.atkPressed = true
    // pinchos
    for (s in a.spikes) if (s[0] - a.px in 0f..50f && a.onGround) inp.jumpPressed = true
    if (a.pvy < 0f) inp.jumpHeld = true
}

fun main(args: Array<String>) {
    val god = args.isNotEmpty() && args[0] == "god"
    var okAll = true
    for (w in 0 until AdvCfg.WORLDS) {
        val a = Adv(w, 640f, god)
        val inp = AdvInput()
        var t = 0
        val max = 120 * 600
        try {
            while (!a.won && t < max) {
                bot(a, inp, t / 4)
                a.step(1f / 30f, inp)
                inp.clearPressed()
                t += 4
            }
        } catch (e: Exception) { e.printStackTrace(); okAll = false }
        println("mundo $w ${AdvCfg.bosses[w].name}: won=${a.won} muertes=${a.deaths} t=${"%.0f".format(a.time)}s bossHp=${a.bhp}/${a.bmax} px=${a.px.toInt()} arena=${a.arenaX.toInt()}")
        if (!a.won) okAll = false
    }
    println(if (okAll) "TODO OK" else "FALLOS")
}
