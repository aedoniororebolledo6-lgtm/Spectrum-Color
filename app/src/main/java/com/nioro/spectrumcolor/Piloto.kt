package com.nioro.spectrumcolor

/**
 * Piloto automático para filmar el anuncio (modo filmación de GameView): busca una jugada que no muera con la misma
 * búsqueda por franjas que tools/SimTest.kt ("feas"), pero con un solo jugador y sin retraso, y la devuelve paso a paso
 * (en qué pasos tocar y hacia dónde mover el dedo en las zonas de deslizar). Sin Android.
 */
object Piloto {
    class Plan(val tap: BooleanArray, val move: IntArray)

    private class Node(val sim: Sim, val parent: Node?, val tap: Boolean, val mv: Int)

    fun plan(start: Sim, seconds: Float, grain: Int = 6, beam: Int = 500): Plan {
        val layers = (seconds / (Cfg.STEP * grain)).toInt()
        var frontier = arrayListOf(Node(start.copy(), null, false, 0))
        var best = frontier[0]
        val rnd = java.util.Random(3)
        for (layer in 0 until layers) {
            val seen = HashSet<Long>()
            val next = ArrayList<Node>()
            for (nd in frontier) {
                val c = nd.sim.course
                val moves = if (c.zones.isNotEmpty() && c.inZone(nd.sim.by)) intArrayOf(0, -1, 1) else intArrayOf(0)
                // primero "no tocar": a igualdad, la jugada con menos toques se ve más natural
                for (tap in 0..1) for (mv in moves) {
                    val s = nd.sim.copy()
                    if (tap == 1) s.tap()
                    var ok = true
                    for (k in 0 until grain) {
                        s.targetX = if (mv == 0) s.bx else s.bx + mv * 5000f
                        s.step()
                        if (s.dead) { ok = false; break }
                        if (s.finished) break
                    }
                    if (!ok) continue
                    val n = Node(s, nd, tap == 1, mv)
                    if (s.finished) return build(n, grain)
                    val key = ((s.by / 7f).toInt().toLong() shl 40) xor ((s.bvy / 40f).toInt().toLong() shl 24) xor
                        (s.starCount.toLong() shl 8) xor (s.swCount.toLong() shl 2)
                    if (seen.add(key * 31L + (s.bx / 36f).toInt().toLong())) next.add(n)
                }
            }
            if (next.isEmpty()) break
            val sorted = ArrayList(next)
            sorted.sortWith(compareByDescending<Node> { it.sim.starCount }.thenBy { it.sim.by })
            best = sorted[0]
            if (next.size > beam) {
                // la mitad con los que más avanzaron y la otra mitad al azar, para no perder variedad
                next.shuffle(rnd)
                val chosen = LinkedHashSet<Node>()
                for (k in 0 until beam / 2) chosen.add(sorted[k])
                var k = 0
                while (chosen.size < beam && k < next.size) { chosen.add(next[k]); k++ }
                frontier = ArrayList(chosen)
            } else {
                frontier = next
            }
        }
        return build(best, grain)
    }

    private fun build(last: Node, grain: Int): Plan {
        val chain = ArrayList<Node>()
        var n: Node? = last
        while (n?.parent != null) { chain.add(n); n = n.parent }
        chain.reverse()
        val tap = BooleanArray(chain.size * grain)
        val move = IntArray(chain.size * grain)
        for ((i, nd) in chain.withIndex()) {
            tap[i * grain] = nd.tap
            for (k in 0 until grain) move[i * grain + k] = nd.mv
        }
        return Plan(tap, move)
    }
}
