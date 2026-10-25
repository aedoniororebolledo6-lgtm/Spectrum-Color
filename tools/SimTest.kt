package com.nioro.spectrumcolor

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/*
 * Pruebas en JVM (no forman parte del APK):
 *   - un bot "humano" juega los niveles para comprobar que son superables y que la dificultad sube
 *   - se valida la generación (colores presentes, separación, determinismo)
 *   - se prueba el protocolo del multijugador sobre sockets reales en 127.0.0.1
 * Compilar: ver tools/run_tests.sh
 */

class BotCfg(val slots: Int, val slotSteps: Int, val jitter: Int, val skip: Float)

/**
 * Bot "humano": decide cada slotSteps pasos (~117 ms) buscando una secuencia de toques que sobreviva
 * incluso si el toque llega con retraso (planifica con varios retrasos a la vez, así deja margen).
 * Al ejecutar, cada toque se retrasa al azar hasta `jitter` pasos y a veces se salta una decisión.
 */
class RBot(private val course: Course, private val visH: Float, private val cfg: BotCfg) {
    private val delays = intArrayOf(0, cfg.jitter / 2, cfg.jitter)
    private val sims = Array(cfg.slots + 1) { Array(delays.size) { Sim(course, visH) } }
    private val tmp = Sim(course, visH)

    fun decide(base: Sim): Boolean {
        var bestTap = false
        var best = Float.NEGATIVE_INFINITY
        for (first in 0..1) {
            for (v in delays.indices) sims[0][v].copyFrom(base)
            val r = eval(0, first == 1)
            if (r > best) {
                best = r
                bestTap = first == 1
            }
        }
        return bestTap
    }

    private fun eval(d: Int, tap: Boolean): Float {
        var anyDead = false
        var deadAt = Int.MAX_VALUE
        var allFin = true
        for (v in delays.indices) {
            val s = sims[d + 1][v]
            s.copyFrom(sims[d][v])
            var steps = 0
            while (steps < cfg.slotSteps) {
                if (tap && steps == delays[v]) s.tap()
                s.step()
                steps++
                if (s.dead) {
                    anyDead = true
                    deadAt = minOf(deadAt, d * cfg.slotSteps + steps)
                    break
                }
                if (s.finished) break
            }
            if (!s.finished) allFin = false
        }
        if (anyDead) return -100000f + deadAt
        if (allFin) return 100000f - (d * cfg.slotSteps)
        if (d + 1 >= cfg.slots) {
            var worst = Float.POSITIVE_INFINITY
            for (v in delays.indices) {
                val s = sims[d + 1][v]
                val reserve = s.camY + s.visH - s.by
                var sc = -s.by + (if (reserve < 250f) -300f else 0f)
                // ¿se puede seguir vivo un rato más "flotando"? (evita callejones sin salida)
                tmp.copyFrom(s)
                var i = 0
                var tapIn = -1
                while (i < 150 && !tmp.dead && !tmp.finished) {
                    if (tapIn == 0) { tmp.tap(); tapIn = -1 } else if (tapIn > 0) tapIn--
                    if (tapIn < 0 && tmp.bvy > 500f) tapIn = delays[v]
                    tmp.step()
                    i++
                }
                if (tmp.dead) sc -= 50000f - i
                if (sc < worst) worst = sc
            }
            return worst
        }
        val a = eval(d + 1, false)
        val b = eval(d + 1, true)
        return max(a, b)
    }
}

/** Juega un nivel. Devuelve (terminó, estrellas, segundos). */
fun playBot(course: Course, visH: Float, cfg: BotCfg, rnd: Random, maxSeconds: Float): Triple<Boolean, Int, Float> {
    val sim = Sim(course, visH)
    val bot = RBot(course, visH, cfg)
    val wait = (rnd.nextFloat() * 2.5f / Cfg.STEP).toInt()
    for (i in 0 until wait) sim.step()
    var pendingTap = -1
    var stepCount = 0
    val maxSteps = (maxSeconds / Cfg.STEP).toInt()
    while (!sim.dead && !sim.finished && stepCount < maxSteps) {
        if (rnd.nextFloat() >= cfg.skip) {
            if (bot.decide(sim)) pendingTap = if (cfg.jitter > 0) rnd.nextInt(cfg.jitter + 1) else 0
        }
        for (k in 0 until cfg.slotSteps) {
            if (pendingTap == 0) {
                sim.tap()
                pendingTap = -1
            } else if (pendingTap > 0) {
                pendingTap--
            }
            sim.step()
            stepCount++
            if (sim.dead || sim.finished) break
        }
    }
    return Triple(sim.finished, sim.starCount, sim.t)
}

fun validateCourse(c: Course, label: String): List<String> {
    val errs = ArrayList<String>()
    if (c.obs.isEmpty()) errs.add("$label: sin obstáculos")
    var prevTop = 0f
    for ((i, o) in c.obs.withIndex()) {
        val bottom = o.y + o.reach
        val gap = prevTop - bottom
        if (gap < 380f) errs.add("$label: hueco pequeño ($gap) antes del obstáculo $i")
        prevTop = o.y - o.reach
        if (o.present == 0) errs.add("$label: obstáculo $i sin colores")
    }
    // el color con que se llega a cada obstáculo debe existir en él
    var color = c.startColor
    var si = 0
    for ((i, o) in c.obs.withIndex()) {
        while (si < c.sws.size && c.sws[si].y > o.y + o.reach) {
            color = c.sws[si].toColor
            si++
        }
        if ((o.present and (1 shl color)) == 0) errs.add("$label: color $color imposible en obstáculo $i (tipo ${o.type})")
    }
    // los obstáculos normales no pueden estar dentro de una zona de deslizar (ahí la bola no está en el centro)
    for ((i, o) in c.obs.withIndex()) {
        if (T.isSlide(o.type)) {
            if (!c.inZone(o.y)) errs.add("$label: obstáculo de deslizar $i fuera de zona")
        } else for (z in c.zones) {
            if (o.y + o.reach >= z.top && o.y - o.reach <= z.bottom) errs.add("$label: obstáculo normal $i dentro de una zona")
        }
    }
    return errs
}

fun experiment(trials: Int, humanish: BotCfg = BotCfg(slots = 7, slotSteps = 14, jitter = 10, skip = 0.05f), lvls: List<Int> = listOf(2, 6, 12, 22, 42, 82, 120)) {
    val pool = Executors.newFixedThreadPool(max(1, Runtime.getRuntime().availableProcessors()))
    val res = DoubleArray(lvls.size)
    for ((idx, lv) in lvls.withIndex()) {
        pool.submit {
            var ok = 0
            for (k in 0 until trials) {
                val (fin, _, _) = playBot(Course.level(lv), 2400f, humanish, Random(lv * 1000L + k), 300f)
                if (fin) ok++
            }
            res[idx] = ok.toDouble() / trials
        }
    }
    pool.shutdown()
    pool.awaitTermination(60, TimeUnit.MINUTES)
    println(lvls.indices.joinToString("  ") { "L${lvls[it]}=%3.0f%%".format(res[it] * 100) } + "   media=%.0f%%".format(res.average() * 100))
}

/**
 * Bot "con cabeza": en vez de buscar toques uno a uno, compara dos conductas (subir sin parar o
 * quedarse flotando a la misma altura) y elige la que sobrevive mejor y llega más arriba.
 * Así sabe "esperar dentro del anillo" como haría una persona.
 */
class PBot(private val course: Course, private val visH: Float, private val jitter: Int, private val horizon: Int = 240) {
    private val delays = intArrayOf(0, jitter / 2, jitter)
    private val tmp = Sim(course, visH)
    private var target = Float.NaN

    /** Sube hasta `yt` y ahí se queda flotando (como esperar dentro de un anillo). */
    private fun rollout(base: Sim, firstTap: Boolean, yt: Float, delay: Int): Float {
        tmp.copyFrom(base)
        var pending = if (firstTap) delay else -1
        var i = 0
        while (i < horizon) {
            if (pending == 0) {
                tmp.tap(); pending = -1
            } else if (pending > 0) {
                pending--
            } else if (i > delay + 2) {
                if (tmp.by > yt + 130f) {
                    if (tmp.bvy > 150f) tmp.tap()
                } else if (tmp.bvy > 250f && tmp.by > yt + 100f) {
                    tmp.tap()
                }
            }
            tmp.step()
            if (tmp.dead) return -1e6f + i
            if (tmp.finished) return 1e6f - i
            i++
        }
        return -tmp.by
    }

    fun decide(base: Sim): Boolean {
        val targets = ArrayList<Float>()
        targets.add(if (target.isNaN()) base.by else target)  // seguir esperando donde estaba
        targets.add(base.by)                                   // quedarse aquí
        var added = 0
        for (o in course.obs) {
            if (o.y < base.by - 60f && added < 3) { targets.add(o.y); added++ }
        }
        if (course.isLevel && added < 3) targets.add(course.finishY - 100f)
        var best = -Float.MAX_VALUE
        var bestTap = false
        var bestT = targets[0]
        for (yt in targets) {
            for (first in 0..1) {
                var worst = Float.MAX_VALUE
                for (d in delays) {
                    val r = rollout(base, first == 1, yt, d)
                    if (r < worst) worst = r
                }
                if (worst > best) { best = worst; bestTap = first == 1; bestT = yt }
            }
        }
        target = bestT
        return bestTap
    }
}

fun playP(course: Course, visH: Float, jitter: Int, skip: Float, rnd: Random, maxSeconds: Float): Triple<Boolean, Int, Float> {
    val sim = Sim(course, visH)
    val bot = PBot(course, visH, jitter)
    val wait = (rnd.nextFloat() * 2.0f / Cfg.STEP).toInt()
    for (i in 0 until wait) sim.step()
    var pending = -1
    var steps = 0
    val maxSteps = (maxSeconds / Cfg.STEP).toInt()
    while (!sim.dead && !sim.finished && steps < maxSteps) {
        if (rnd.nextFloat() >= skip && bot.decide(sim)) pending = if (jitter > 0) rnd.nextInt(jitter + 1) else 0
        for (k in 0 until 10) {
            if (pending == 0) { sim.tap(); pending = -1 } else if (pending > 0) pending--
            sim.step(); steps++
            if (sim.dead || sim.finished) break
        }
    }
    return Triple(sim.finished, sim.starCount, sim.t)
}

fun pexp(trials: Int, jitter: Int, skip: Float, levels: List<Int>) {
    val pool = Executors.newFixedThreadPool(max(1, Runtime.getRuntime().availableProcessors()))
    val res = Array(levels.size) { DoubleArray(3) }
    for ((idx, lv) in levels.withIndex()) {
        pool.submit {
            var ok = 0; var st = 0; var secs = 0f
            for (k in 0 until trials) {
                val (fin, stars, t) = playP(Course.level(lv), 2400f, jitter, skip, Random(lv * 1000L + k), 200f)
                if (fin) ok++
                st += stars; secs += t
            }
            res[idx][0] = ok.toDouble() / trials
            res[idx][1] = st.toDouble() / trials
            res[idx][2] = (secs / trials).toDouble()
        }
    }
    pool.shutdown()
    pool.awaitTermination(60, TimeUnit.MINUTES)
    for ((idx, lv) in levels.withIndex()) {
        println("nivel %3d  obstáculos %2d  éxito %3.0f%%  estrellas %5.1f  tiempo %5.1fs".format(lv, Levels.gates(lv), res[idx][0] * 100, res[idx][1], res[idx][2]))
    }
}

/**
 * Búsqueda exhaustiva (por capas de tiempo) de una secuencia de toques que complete el nivel.
 * Sirve para saber si un nivel es POSIBLE y con cuánto margen: cada nodo lleva varias copias del juego
 * con distintos retrasos en el toque (0, 4 y 8 pasos, ~0/33/66 ms) y solo sobrevive si TODAS sobreviven.
 */
fun tname(t: Int): String = arrayOf("anillo", "doble", "barras", "cuadrado", "triángulo", "pinchos", "péndulo", "cruz", "puertas", "órbitas", "opuestas", "arco", "pulso", "hexágono", "rayo", "compuerta", "franja", "sierras", "pilar", "pentágono", "estrella", "ola", "mandíbulas", "ocho").getOrElse(t) { "?" }

val bestStarsTL = ThreadLocal.withInitial { 0 }

class FNode(val sims: Array<Sim>, val pend: IntArray)

fun feasible(course: Course, visH: Float, delays: IntArray, grain: Int = 6, maxSeconds: Float = 150f, beam: Int = (System.getenv("BEAM") ?: "6000").toInt()): Pair<Boolean, Float> {
    var frontier = ArrayList<FNode>()
    val slideV = (System.getenv("SLIDEV") ?: "1300").toFloat()
    frontier.add(FNode(Array(delays.size) { Sim(course, visH).also { s -> s.vmax = slideV } }, IntArray(delays.size) { -1 }))
    val layers = (maxSeconds / (Cfg.STEP * grain)).toInt()
    val rnd = Random(5)
    for (layer in 0 until layers) {
        val seen = HashSet<Long>()
        val next = ArrayList<FNode>()
        for (node in frontier) {
            val moves = if (course.zones.isNotEmpty() && course.inZone(node.sims[0].by)) intArrayOf(-1, 0, 1) else intArrayOf(0)
            for (tap in 0..1) for (mv in moves) {
                val sims = Array(delays.size) { node.sims[it].copy() }
                val pend = node.pend.copyOf()
                var ok = true
                var fin = true
                for (i in delays.indices) {
                    if (tap == 1 && pend[i] < 0) pend[i] = delays[i]
                    val sm = sims[i]
                    for (k in 0 until grain) {
                        if (pend[i] == 0) { sm.tap(); pend[i] = -1 } else if (pend[i] > 0) pend[i]--
                        sm.targetX = if (mv == 0) sm.bx else sm.bx + mv * 5000f
                        sm.step()
                        if (sm.dead) { ok = false; break }
                        if (sm.finished) break
                    }
                    if (!ok) break
                    if (!sm.finished) fin = false
                }
                if (!ok) continue
                if (fin) return Pair(true, (layer + 1) * grain * Cfg.STEP)
                val a = sims[0]
                val key = ((a.by / 7f).toInt().toLong() shl 40) xor ((a.bvy / 40f).toInt().toLong() shl 24) xor (a.starCount.toLong() shl 8) xor (a.swCount.toLong() shl 2) xor (if (pend[0] >= 0) 1L else 0L)
                val key2 = key * 31L + (a.bx / 36f).toInt().toLong()
                if (seen.add(key2)) next.add(FNode(sims, pend))
            }
        }
        for (nd in next) if (nd.sims[0].starCount > bestStarsTL.get()) bestStarsTL.set(nd.sims[0].starCount)
        if (next.isEmpty()) return Pair(false, layer * grain * Cfg.STEP)
        if (next.size > beam) {
            // la mitad de la franja se llena con los estados que más avanzaron (más estrellas y más arriba);
            // la otra mitad al azar, para no perder variedad
            next.shuffle(rnd)
            val keep = beam / 2
            val sorted = ArrayList(next)
            sorted.sortWith(compareByDescending<FNode> { it.sims[0].starCount }.thenBy { it.sims[0].by })
            val chosen = LinkedHashSet<FNode>()
            for (k in 0 until keep) chosen.add(sorted[k])
            var k = 0
            while (chosen.size < beam && k < next.size) { chosen.add(next[k]); k++ }
            frontier = ArrayList(chosen)
        } else {
            frontier = next
        }
    }
    return Pair(false, maxSeconds)
}

/** Diagnóstico: ¿dónde y por qué muere el bot? Uso: diag <nivel> */
fun diag(level: Int) {
    val cfg = BotCfg(slots = 7, slotSteps = 14, jitter = 10, skip = 0.05f)
    val names = Array(T.COUNT) { tname(it) }
    val c0 = Course.level(level)
    println("nivel $level: colores=${Levels.colors(level)} obstáculos=" + c0.obs.joinToString(",") { names[it.type] })
    val deathsAt = IntArray(c0.obs.size + 2)
    val causeFall = IntArray(1)
    for (k in 0 until 6) {
        val course = Course.level(level)
        val sim = Sim(course, 2400f)
        val bot = PBot(course, 2400f, 10)
        val rnd = Random(level * 1000L + k)
        var pend = -1
        var steps = 0
        while (!sim.dead && !sim.finished && steps < 24000) {
            if (rnd.nextFloat() >= cfg.skip) if (bot.decide(sim)) pend = if (cfg.jitter > 0) rnd.nextInt(cfg.jitter + 1) else 0
            for (i in 0 until 10) {
                if (pend == 0) { sim.tap(); pend = -1 } else if (pend > 0) pend--
                sim.step(); steps++
                if (sim.dead || sim.finished) break
            }
        }
        if (sim.finished) deathsAt[c0.obs.size]++
        else if (sim.deathObs >= 0) {
            deathsAt[sim.deathObs]++
            val o = course.obs[sim.deathObs]
            if (o.type != T.BARS) {
                                println("  muerte en ${names[o.type]} #${sim.deathObs}: by-y=%.0f  color pelota=%d  vel=%.0f n=%d t=%.1f desde-obstaculo-anterior".format(
                    sim.by - o.y, sim.color, o.speed, o.n, sim.t))
            }
            if (o.type == T.BARS) {
                println("  muerte en barras #${sim.deathObs}: by-y=%.0f  color pelota=%d  carril0(arriba)=%d carril1(abajo)=%d  vel=%.0f n=%d t=%.1f".format(
                    sim.by - o.y, sim.color, Geo.barColor(o, 0, 540f, sim.t), Geo.barColor(o, 1, 540f, sim.t), o.speed, o.n, sim.t))
            }
        } else causeFall[0]++
    }
    println("muertes por obstáculo (índice:tipo:cantidad): " + c0.obs.indices.filter { deathsAt[it] > 0 }.joinToString("  ") { "$it:${names[c0.obs[it].type]}:${deathsAt[it]}" })
    println("cayó por abajo: ${causeFall[0]}   completó: ${deathsAt[c0.obs.size]}")
}

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "feas") {
        val dl = (args.getOrNull(1) ?: "0,4,8").split(",").map { it.toInt() }.toIntArray()
        val lv = if (args.size > 2) args.drop(2).map { it.toInt() } else (1..Levels.PLAYABLE).toList()
        println("¿es posible el nivel? (retrasos de toque ${dl.joinToString("/")} pasos)")
        val pool = Executors.newFixedThreadPool(max(1, Runtime.getRuntime().availableProcessors()))
        val out = arrayOfNulls<String>(lv.size)
        for ((idx, l) in lv.withIndex()) pool.submit {
            val t0 = System.currentTimeMillis()
            bestStarsTL.set(0)
            val (ok, secs) = feasible(Course.level(l), 2400f, dl)
            out[idx] = "nivel %3d  colores %d  obstáculos %2d  %s  (%.1f s de juego, %.1f s de cálculo)%s".format(l, Levels.colors(l), Levels.gates(l), if (ok) "POSIBLE" else "NO ENCONTRADO", secs, (System.currentTimeMillis() - t0) / 1000f, if (ok) "" else "  mejor: ${bestStarsTL.get()} estrellas, se atasca en ${tname(Course.level(l).obs.getOrNull(bestStarsTL.get())?.type ?: -1)} #${bestStarsTL.get()}")
            synchronized(out) { println(out[idx]) }
        }
        pool.shutdown()
        pool.awaitTermination(600, TimeUnit.MINUTES)
        return
    }
    if (args.size >= 3 && args[0] == "salt") {
        // salt <maxSalt> <nivel>...: ¿qué variantes de cada nivel son posibles con retrasos 0/4/8?
        val maxSalt = args[1].toInt()
        val dl = (System.getenv("DELAYS") ?: "0,4").split(",").map { it.toInt() }.toIntArray()
        val pool = Executors.newFixedThreadPool(max(1, Runtime.getRuntime().availableProcessors()))
        val jobs = ArrayList<Pair<Int, Int>>()
        for (l in args.drop(2).map { it.toInt() }) for (sa in 0..maxSalt) jobs.add(Pair(l, sa))
        val out = arrayOfNulls<String>(jobs.size)
        for ((idx, j) in jobs.withIndex()) pool.submit {
            val (ok, secs) = feasible(Course.level(j.first, j.second), 2400f, dl)
            out[idx] = "nivel %3d  variante %d  %s  (%.1f s de juego)".format(j.first, j.second, if (ok) "POSIBLE" else "no encontrado", secs)
            synchronized(out) { println(out[idx]) }
        }
        pool.shutdown()
        pool.awaitTermination(600, TimeUnit.MINUTES)
        return
    }
    if (args.isNotEmpty() && args[0] == "pbot") {
        val trials = args.getOrNull(1)?.toInt() ?: 6
        val jit = args.getOrNull(2)?.toInt() ?: 10
        val skip = args.getOrNull(3)?.toFloat() ?: 0.05f
        println("bot con cabeza: retraso hasta $jit pasos, descuidos ${(skip * 100).toInt()}%, $trials intentos por nivel")
        pexp(trials, jit, skip, if (args.size > 4) args.drop(4).map { it.toInt() } else (1..Levels.PLAYABLE).toList())
        return
    }
    if (args.size >= 2 && args[0] == "diag") { for (l in args.drop(1)) diag(l.toInt()); return }
    if (args.contains("exp")) { experiment(10); return }
    if (args.contains("perfect")) {
        println("bot perfecto (sin retraso ni descuidos):")
        experiment(6, BotCfg(slots = 7, slotSteps = 14, jitter = 0, skip = 0f), listOf(1, 5, 10, 15, 20))
        println("bot con retraso corto (jitter 4 pasos = 33 ms):")
        experiment(6, BotCfg(slots = 7, slotSteps = 14, jitter = 4, skip = 0f), listOf(1, 5, 10, 15, 20))
        return
    }
    var failures = 0
    println("== Validación de recorridos ==")
    val typeCount = IntArray(T.COUNT)
    for (lv in 1..Levels.PLAYABLE) {
        val c = Course.level(lv)
        val e = validateCourse(c, "nivel $lv")
        e.forEach { println("ERROR $it") }
        failures += e.size
        for (o in c.obs) typeCount[o.type]++
        val c2 = Course.level(lv)
        if (c.obs.size != c2.obs.size || c.obs.zip(c2.obs).any { (a, b) -> a.y != b.y || a.phase != b.phase || a.type != b.type }) {
            println("ERROR nivel $lv no es determinista"); failures++
        }
    }
    println("obstáculos por tipo en los niveles jugables: ${typeCount.toList()}")
    // editor: guardar y volver a cargar un nivel lo deja idéntico (obstáculos, cambiadores, zonas)
    for (lv in intArrayOf(1, 7, 25, 61, 99, 120)) {
        val c = Course.level(lv)
        val d = LevelData.parse(c.toData().serialize())
        if (d == null) { println("ERROR editor: no se pudo leer el nivel $lv"); failures++; continue }
        val c2 = Course.custom(lv, d)
        fun near(a: Float, b: Float) = abs(a - b) < 0.011f
        val same = c.obs.size == c2.obs.size && c.sws.size == c2.sws.size && c.zones.size == c2.zones.size &&
            c.startColor == c2.startColor && near(c.finishY, c2.finishY) &&
            c.obs.zip(c2.obs).all { (x, y) -> x.type == y.type && near(x.y, y.y) && x.n == y.n && x.shift == y.shift && near(x.speed, y.speed) &&
                near(x.phase, y.phase) && near(x.size, y.size) && near(x.inner, y.inner) && near(x.aux, y.aux) && near(x.drift, y.drift) && x.fade == y.fade && near(x.wob, y.wob) } &&
            c.sws.zip(c2.sws).all { (x, y) -> near(x.y, y.y) && x.toColor == y.toColor }
        // y guardar dos veces da exactamente el mismo texto (así se detecta que un nivel ya es oficial)
        if (d.serialize() != LevelData.parse(d.serialize())!!.serialize()) { println("ERROR editor: el texto del nivel $lv no es estable"); failures++ }
        if (!same) { println("ERROR editor: el nivel $lv cambia al guardarlo"); failures++ }
        val e = validateCourse(c2, "editado $lv")
        e.forEach { println("ERROR $it") }
        failures += e.size
    }
    run {
        val b = LevelData.blank(121)
        val e = validateCourse(Course.custom(121, b), "nivel nuevo")
        e.forEach { println("ERROR $it") }
        failures += e.size
        Levels.setCount(125)
        if (Levels.COUNT != 125 || Levels.WORLDS != 13 || !Levels.isPlayable(125)) { println("ERROR niveles extra: cuenta mal"); failures++ }
        Levels.setCount(120)
    }
    // y un nivel guardado reemplaza al generado
    run {
        val d = Course.level(3).toData()
        Custom.map[3] = d
        val same = Course.level(3).obs.size == d.obs.size
        Custom.map.remove(3)
        if (!same) { println("ERROR editor: el nivel guardado no se usa"); failures++ }
    }
    for (seed in 1L..20L) {
        val c = Course.endless(seed, 0)
        c.ensure(-60000f)
        val e = validateCourse(c, "infinito $seed")
        e.forEach { println("ERROR $it") }
        failures += e.size
    }

    val quick = args.contains("quick")
    val trials = if (quick) 3 else 8
    println("\n== Bot (nivel: éxito de $trials intentos, estrellas medias, segundos medios) ==")
    val humanish = BotCfg(slots = 7, slotSteps = 14, jitter = 10, skip = 0.05f)
    val pool = Executors.newFixedThreadPool(max(1, Runtime.getRuntime().availableProcessors()))
    val results = Array(Levels.PLAYABLE + 1) { DoubleArray(3) }
    val levelsToTest = if (quick) listOf(1, 2, 3, 5, 8, 10, 11, 15, 18, 20) else (1..Levels.PLAYABLE).toList()
    for (lv in levelsToTest) {
        pool.submit {
            var ok = 0
            var st = 0
            var secs = 0f
            for (k in 0 until trials) {
                val course = Course.level(lv)
                val rnd = Random(lv * 1000L + k)
                val (fin, stars, t) = playBot(course, 2400f, humanish, rnd, 400f)
                if (fin) ok++
                st += stars
                secs += t
            }
            results[lv][0] = ok.toDouble() / trials
            results[lv][1] = st.toDouble() / trials
            results[lv][2] = (secs / trials).toDouble()
        }
    }
    pool.shutdown()
    pool.awaitTermination(60, TimeUnit.MINUTES)
    var totalSecs = 0.0
    for (lv in levelsToTest) {
        val r = results[lv]
        println("nivel %3d  gates %2d  éxito %3.0f%%  estrellas %5.1f  tiempo %5.1fs".format(lv, Levels.gates(lv), r[0] * 100, r[1], r[2]))
        totalSecs += r[2]
        if (r[0] == 0.0) {
            println("AVISO nivel $lv: el bot simple no lo completó (informativo; la prueba que cuenta es `feas`)")
        }
    }
    println("tiempo medio por intento exitoso (suma de los niveles probados): %.0f s".format(totalSecs))

    println("\n== Alturas de pantalla distintas (nivel 12, 8 intentos) ==")
    for (h in floatArrayOf(1600f, 1920f, 2160f, 2400f)) {
        var ok = 0
        for (k in 0 until 8) {
            val (fin, _, _) = playBot(Course.level(12), h, humanish, Random(k), 400f)
            if (fin) ok++
        }
        println("visH=$h -> $ok/8")
    }

    println("\n== Infinito: estrellas del bot (promedio de 6 semillas) ==")
    run {
        var sum = 0
        for (s in 1L..6L) {
            val (_, stars, _) = playBot(Course.endless(s, 0), 2400f, humanish, Random(s), 200f)
            sum += stars
        }
        println("estrellas medias: ${sum / 6.0}")
    }

    println("\n== Multijugador (sockets reales en 127.0.0.1) ==")
    failures += testNet()
    println(if (failures == 0) "\nTODO OK" else "\nFALLOS: $failures")
    System.exit(if (failures == 0) 0 else 1)
}

fun waitFor(q: ConcurrentLinkedQueue<String>, prefix: String, ms: Long): String? {
    val end = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < end) {
        val it = q.iterator()
        while (it.hasNext()) {
            val m = it.next()
            if (m.startsWith(prefix)) {
                it.remove()
                return m
            }
        }
        Thread.sleep(10)
    }
    return null
}

fun testNet(): Int {
    var fails = 0
    fun check(ok: Boolean, what: String) {
        println((if (ok) "  ok   " else "  FALLO ") + what)
        if (!ok) fails++
    }
    val hostQ = ConcurrentLinkedQueue<String>()
    val guestQ = ConcurrentLinkedQueue<String>()
    val scanQ = ConcurrentLinkedQueue<String>()
    val tcp = 47911
    val udp = 47912
    val host = NetHost("Anfitrión|raro", hostQ, listOf("127.0.0.1"), tcp, udp)
    check(host.start(), "el anfitrión abre el puerto")
    val scanner = NetScanner(scanQ, udp)
    check(scanner.start(), "el escáner abre el puerto UDP")
    val room = waitFor(scanQ, "!ROOM", 4000)
    check(room != null && room.contains("Anfitrión raro"), "se descubre la sala por UDP: $room")

    val guest = NetGuest(guestQ)
    guest.connect("127.0.0.1", tcp)
    check(waitFor(hostQ, "!CONNECTED", 4000) != null, "el anfitrión ve la conexión")
    check(waitFor(guestQ, "!CONNECTED", 4000) != null, "el invitado ve la conexión")
    scanner.stop()

    var clock = 0L
    val hostConn = host.conn!!
    val guestConn = guest.conn!!
    val mh = Match(true, "Ana", { hostConn.send(it) }, { clock })
    val mg = Match(false, "Beto", { guestConn.send(it) }, { clock })
    mh.rounds = 3
    mg.onConnected()
    fun pump() {
        var m = hostQ.poll()
        while (m != null) { if (!m.startsWith("!")) mh.onMessage(m) else if (m == "!CLOSED") mh.onClosed(); m = hostQ.poll() }
        m = guestQ.poll()
        while (m != null) { if (!m.startsWith("!")) mg.onMessage(m) else if (m == "!CLOSED") mg.onClosed(); m = guestQ.poll() }
    }
    fun until(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { pump(); if (cond()) return true; Thread.sleep(5) }
        pump()
        return cond()
    }
    check(until(3000) { mh.connected && mg.connected }, "saludo HELLO/WELCOME: ${mh.oppName} / ${mg.oppName}")
    check(mg.rounds == 3, "el invitado conoce las rondas")

    mh.hostStart()
    check(until(3000) { mg.phase == Match.Phase.COUNTDOWN && mg.seed == mh.seed && mg.round == 1 }, "START llega con la misma semilla")
    mh.beginRun(); mg.beginRun()
    mh.localLive(2); mg.localLive(5)
    check(until(3000) { mh.oppLive == 5 && mg.oppLive == 2 }, "puntajes en vivo se cruzan")
    mh.localFinish(4)
    pump()
    check(mh.phase == Match.Phase.RUN, "una ronda no termina hasta que se eliminan los dos")
    mg.localFinish(7)
    check(until(3000) { mh.phase == Match.Phase.ROUND_END && mg.phase == Match.Phase.ROUND_END }, "ronda 1 termina en ambos")
    check(mh.myTotal == 4 && mh.oppTotal == 7 && mg.myTotal == 7 && mg.oppTotal == 4, "totales coherentes en los dos lados")

    clock += Match.NEXT_ROUND_DELAY_MS + 1
    mh.tick()
    check(until(3000) { mg.phase == Match.Phase.COUNTDOWN && mg.round == 2 && mg.seed == mh.seed }, "el anfitrión lanza la ronda 2 solo")
    mh.beginRun(); mg.beginRun()
    mg.localFinish(3); mh.localFinish(3)
    check(until(3000) { mh.phase == Match.Phase.ROUND_END && mg.phase == Match.Phase.ROUND_END }, "ronda 2 termina")
    clock += Match.NEXT_ROUND_DELAY_MS + 1
    mh.tick()
    check(until(3000) { mg.round == 3 && mg.phase == Match.Phase.COUNTDOWN }, "ronda 3 empieza")
    mh.beginRun(); mg.beginRun()
    mh.localFinish(10); mg.localFinish(1)
    check(until(3000) { mh.phase == Match.Phase.MATCH_END && mg.phase == Match.Phase.MATCH_END }, "partida termina tras 3 rondas")
    check(mh.myTotal == 17 && mh.oppTotal == 11 && mh.result() == 1 && mg.result() == -1, "ganador correcto (17 a 11)")

    mg.rematch()
    check(until(3000) { mh.oppRematch }, "el anfitrión ve la revancha pedida")
    mh.rematch()
    check(until(3000) { mg.phase == Match.Phase.COUNTDOWN && mg.round == 1 && mg.myTotal == 0 && mh.phase == Match.Phase.COUNTDOWN }, "revancha: ambos en ronda 1 con totales en cero")

    // caída de la conexión
    hostConn.close()
    check(until(3000) { mg.phase == Match.Phase.CLOSED }, "el invitado se entera si el anfitrión se va")
    guest.stop(); host.stop()

    // línea gigante no rompe nada
    val hq2 = ConcurrentLinkedQueue<String>()
    val gq2 = ConcurrentLinkedQueue<String>()
    val h2 = NetHost("X", hq2, emptyList(), tcp + 10, udp + 10)
    h2.start()
    val g2 = NetGuest(gq2)
    g2.connect("127.0.0.1", tcp + 10)
    waitFor(gq2, "!CONNECTED", 3000)
    g2.conn!!.send("A".repeat(5000))
    g2.conn!!.send("SCORE|9")
    check(waitFor(hq2, "SCORE|9", 3000) != null, "una línea gigante se descarta y el siguiente mensaje llega")
    g2.stop(); h2.stop()

    // conectar a una IP sin anfitrión
    val gq3 = ConcurrentLinkedQueue<String>()
    NetGuest(gq3).connect("127.0.0.1", tcp + 50)
    check(waitFor(gq3, "!ERROR", 6000) != null, "conectar sin sala da error en vez de colgarse")
    return fails
}
