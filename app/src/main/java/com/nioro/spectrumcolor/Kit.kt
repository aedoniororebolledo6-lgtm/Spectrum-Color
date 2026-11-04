package com.nioro.spectrumcolor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Obstáculos de catálogo (tipo T.KIT): 200 diseños armados con piezas de vidrio de colores.
 * Sin dependencias de Android: la colisión se prueba en la JVM (tools/SimTest.kt, modo "kits").
 *
 * Cada diseño es una lista de piezas (aro, polígono, estrella, aspas, satélites o cinta). Las medidas están
 * pensadas para size = 300 y se escalan con o.size / 300. El giro de cada pieza es el giro del obstáculo
 * (Geo.rot, que ya incluye el vaivén de los mundos difíciles) multiplicado por `spd`.
 */

/** Pieza de un obstáculo de catálogo. */
class KPart(
    val kind: Int,
    val dx: Float = 0f,
    val dy: Float = 0f,
    /** Radio (aro, polígono, estrella, aspas y órbita de satélites). En la cinta no se usa. */
    val r: Float = 300f,
    /** Estrella: radio interior relativo (0..1). Aspas: radio del hueco central. Satélites: radio de cada bolita. Cinta: largo de cada tramo. */
    val r2: Float = 0f,
    /** Cantidad de tramos/lados/puntas/brazos/bolitas. Si es <= 0 es un múltiplo de los colores: 0 → 2n, -1 → n, -3 → 3n. */
    val cnt: Int = 0,
    /** Aro: hueco libre en grados. */
    val gap: Float = 0f,
    /** Multiplicador del giro (negativo = al revés). En la cinta, de la velocidad. */
    val spd: Float = 1f,
    /** Desfase del giro en grados. */
    val off: Float = 0f,
    /** Desfase de los colores. */
    val coff: Int = 0,
    /** El radio late: fracción de su tamaño. */
    val breathe: Float = 0f,
    /** Vaivén horizontal (unidades de diseño). */
    val sway: Float = 0f,
    /** Estiramiento vertical (elipse): 1 = redondo. */
    val ey: Float = 1f
) {
    companion object {
        const val ARC = 0
        const val POLY = 1
        const val STAR = 2
        const val SPOKE = 3
        const val DOTS = 4
        const val BAR = 5
    }
}

class KDesign(val id: Int, val family: Int, val variant: Int, val parts: Array<KPart>) {
    val name: String get() = Kit.familyNames[family] + " " + Kit.roman[variant]
}

object Kit {
    const val COUNT = 200
    const val FAMILIES = 20

    val familyNames = arrayOf(
        "Aro roto", "Abanicos", "Engranaje", "Estrella", "Molino", "Satélites", "Doble órbita", "Planeta",
        "Cintas cruzadas", "Escalera", "Aros gemelos", "Ocho", "Joya", "Péndulo de aro", "Latido",
        "Polígono vivo", "Elipse", "Hélice", "Rombo", "Corona"
    )
    val roman = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

    /** Los 200 diseños. El índice sube con la dificultad: variante 0 de las 20 familias, luego la 1, etc. */
    val designs: Array<KDesign> = Array(COUNT) { i ->
        val v = i / FAMILIES
        val f = i % FAMILIES
        KDesign(i, f, v, build(f, v))
    }

    /** Nivel en que se estrena el diseño i: del 11 (mundo 2) al 120, unos dos por nivel. */
    fun unlockLevel(i: Int): Int = 11 + (i * 109) / (COUNT - 1)

    /** En el modo infinito: a partir de qué obstáculo aparece el diseño i. */
    fun unlockGate(i: Int): Int = 14 + i / 3

    fun segs(p: KPart, n: Int): Int = if (p.cnt > 0) p.cnt else n * (if (p.cnt == 0) 2 else -p.cnt)

    private fun build(f: Int, v: Int): Array<KPart> {
        val k = v / 9f // 0..1 a lo largo de las variantes
        fun s(lo: Float, hi: Float) = lo + (hi - lo) * k
        return when (f) {
            0 -> arrayOf(KPart(KPart.ARC, r = 310f, cnt = 0, gap = s(150f, 70f), spd = s(1f, 1.2f)))
            1 -> arrayOf(
                KPart(KPart.ARC, r = 300f, cnt = -1, gap = s(205f, 165f), spd = 1f),
                KPart(KPart.ARC, r = 300f, cnt = -1, gap = s(205f, 165f), spd = 1f, off = 180f, coff = 1)
            )
            // lados pares y múltiplos de los colores: el lado de arriba y el de abajo son del mismo color
            2 -> arrayOf(KPart(KPart.POLY, r = 305f, cnt = intArrayOf(12, 8, 10, 12, 8, 10, 12, 8, 10, 12)[v], spd = s(0.85f, 1.05f)))
            3 -> arrayOf(KPart(KPart.STAR, r = 325f, r2 = s(0.68f, 0.6f), cnt = 5 + v % 4, spd = s(0.8f, 1.0f)))
            4 -> arrayOf(KPart(KPart.SPOKE, r = 300f, r2 = 100f, cnt = 3 + v % 5, spd = s(0.8f, 1.05f)))
            5 -> arrayOf(KPart(KPart.DOTS, r = 285f, r2 = 36f, cnt = 4 + v % 5, spd = s(0.9f, 1.15f)))
            6 -> arrayOf(
                KPart(KPart.DOTS, r = 290f, r2 = 34f, cnt = 3 + v / 3, spd = 1f),
                KPart(KPart.DOTS, r = 145f, r2 = 32f, cnt = 2 + v / 4, spd = s(-1.1f, -1.35f), coff = 1)
            )
            7 -> arrayOf(
                KPart(KPart.ARC, r = 325f, cnt = 0, spd = 1f),
                KPart(KPart.DOTS, r = 165f, r2 = 30f, cnt = 2 + v / 3, spd = s(-1.0f, -1.3f), coff = 1)
            )
            8 -> arrayOf(
                KPart(KPart.BAR, dy = -118f, r2 = s(560f, 460f), spd = 1f),
                KPart(KPart.BAR, dy = 118f, r2 = s(560f, 460f), spd = s(-0.7f, -0.95f), coff = 1)
            )
            9 -> if (v < 5) arrayOf(
                KPart(KPart.BAR, dy = -170f, r2 = 520f, spd = s(0.9f, 1.1f)),
                KPart(KPart.BAR, dy = 170f, r2 = 520f, spd = s(-0.9f, -1.1f), coff = 1)
            ) else arrayOf(
                KPart(KPart.BAR, dy = -250f, r2 = 520f, spd = 1f),
                KPart(KPart.BAR, dy = 0f, r2 = 480f, spd = -0.85f, coff = 1),
                KPart(KPart.BAR, dy = 250f, r2 = 520f, spd = 1.1f, coff = 2)
            )
            // dos aros concéntricos alineados: en la línea de la bola siempre muestran el mismo color
            10 -> arrayOf(
                KPart(KPart.ARC, r = 335f, cnt = 0, spd = s(0.85f, 1.05f)),
                KPart(KPart.ARC, r = s(205f, 225f), cnt = 0, spd = s(0.85f, 1.05f))
            )
            // dos aros apilados que giran juntos: donde se tocan tienen el mismo color
            11 -> arrayOf(
                KPart(KPart.ARC, dy = -225f, r = 210f, cnt = 0, spd = s(0.9f, 1.1f)),
                KPart(KPart.ARC, dy = 225f, r = 210f, cnt = 0, spd = s(0.9f, 1.1f))
            )
            12 -> arrayOf(
                KPart(KPart.POLY, r = 325f, cnt = 6, spd = s(0.85f, 1.05f)),
                KPart(KPart.POLY, r = 195f, cnt = 6, spd = s(0.85f, 1.05f))
            )
            13 -> arrayOf(KPart(KPart.ARC, r = 285f, cnt = 0, spd = s(0.9f, 1.05f), sway = s(55f, 150f)))
            14 -> arrayOf(KPart(KPart.ARC, r = 300f, cnt = 0, spd = s(0.9f, 1.05f), breathe = s(0.1f, 0.24f)))
            15 -> arrayOf(KPart(KPart.POLY, r = 300f, cnt = intArrayOf(6, 8, 12)[v % 3], spd = s(0.85f, 1.05f), breathe = s(0.08f, 0.16f)))
            16 -> arrayOf(KPart(KPart.ARC, r = 330f, cnt = 0, spd = s(0.9f, 1.1f), ey = if (v % 2 == 0) s(0.78f, 0.62f) else s(1.12f, 1.25f)))
            17 -> arrayOf(
                KPart(KPart.SPOKE, r = 320f, r2 = 110f, cnt = 2, spd = s(0.8f, 1.0f)),
                KPart(KPart.DOTS, r = 320f, r2 = 34f, cnt = 2, spd = s(0.8f, 1.0f), off = 90f, coff = 1)
            )
            18 -> arrayOf(KPart(KPart.POLY, r = 290f, cnt = 4, spd = s(0.7f, 0.9f), ey = if (v % 2 == 0) s(1.12f, 1.28f) else s(0.82f, 0.72f)))
            else -> arrayOf(
                KPart(KPart.STAR, r = 330f, r2 = 0.82f, cnt = 6 + v % 3, spd = s(0.8f, 1.0f)),
                KPart(KPart.DOTS, r = 150f, r2 = 26f, cnt = 2, spd = s(-0.9f, -1.15f), coff = 1)
            )
        }
    }

    // ------------------------------------------------------------------ geometría
    fun design(o: Ob): KDesign = designs[o.design.coerceIn(0, COUNT - 1)]

    fun scale(o: Ob): Float = o.size / 300f

    fun ang(o: Ob, p: KPart, t: Float): Float = Geo.rot(o, t) * p.spd + p.off

    fun cx(o: Ob, p: KPart, t: Float): Float {
        val sw = if (p.sway != 0f) p.sway * sin(t * 1.3f + o.phase * 0.02f) else 0f
        return Cfg.W / 2f + (p.dx + sw) * scale(o)
    }

    fun cy(o: Ob, p: KPart): Float = o.y + p.dy * scale(o)

    fun rad(o: Ob, p: KPart, t: Float): Float {
        val b = if (p.breathe != 0f) 1f + p.breathe * sin(t * 2.1f + o.phase * 0.03f) else 1f
        return p.r * scale(o) * b
    }

    /** Velocidad de una cinta (unidades/s): sale del giro del obstáculo. */
    fun barSpeed(o: Ob, p: KPart): Float = o.speed * 3.2f * p.spd

    fun barColor(o: Ob, p: KPart, x: Float, t: Float): Int {
        val off = o.phase * 3f + barSpeed(o, p) * t
        val len = if (p.r2 > 0f) p.r2 else Cfg.BAR_SEG
        val j = floor((x - off) / len).toInt()
        return ((((j % o.n) + o.n) % o.n) + o.shift + p.coff) % o.n
    }

    fun reach(o: Ob): Float {
        val s = o.size / 300f
        var m = 0f
        for (p in designs[o.design.coerceIn(0, COUNT - 1)].parts) {
            val ext = when (p.kind) {
                KPart.BAR -> Cfg.BAR_H
                KPart.DOTS -> p.r * s * (1f + p.breathe) * p.ey + p.r2
                else -> p.r * s * (1f + p.breathe) * p.ey + Cfg.THICK / 2f
            }
            m = max(m, abs(p.dy) * s + ext)
        }
        return m + 6f
    }

    /** Colores que aparecen en el obstáculo (máscara de bits). */
    fun present(o: Ob): Int {
        var m = 0
        for (p in designs[o.design.coerceIn(0, COUNT - 1)].parts) {
            val k = if (p.kind == KPart.BAR) o.n else segs(p, o.n)
            for (i in 0 until k) m = m or (1 shl ((i + o.shift + p.coff) % o.n))
        }
        return m
    }

    /** Vértice i de un polígono o estrella (en coordenadas de pantalla). */
    fun vert(o: Ob, p: KPart, t: Float, i: Int, out: FloatArray) {
        out[0] = vertX(o, p, t, i)
        out[1] = vertY(o, p, t, i)
    }

    private fun vertAng(o: Ob, p: KPart, t: Float, i: Int): Float {
        val k = segs(p, o.n)
        return (ang(o, p, t) + i * (if (p.kind == KPart.STAR) 180f else 360f) / k) * DEG
    }

    private fun vertR(o: Ob, p: KPart, t: Float, i: Int): Float {
        val r = rad(o, p, t)
        return if (p.kind == KPart.STAR && i % 2 == 1) r * p.r2 else r
    }

    // sin búferes compartidos: la colisión se usa desde varios hilos en las pruebas
    fun vertX(o: Ob, p: KPart, t: Float, i: Int): Float = cx(o, p, t) + cos(vertAng(o, p, t, i)) * vertR(o, p, t, i)
    fun vertY(o: Ob, p: KPart, t: Float, i: Int): Float = cy(o, p) + sin(vertAng(o, p, t, i)) * vertR(o, p, t, i) * p.ey

    private const val DEG = 3.14159265f / 180f

    /** true si la bola (de ese color) choca con el obstáculo de catálogo en el instante t. */
    fun hits(o: Ob, bx: Float, by: Float, color: Int, t: Float): Boolean {
        val graze = Cfg.BALL_R * Cfg.GRAZE
        val band = Cfg.THICK / 2f + graze
        for (p in designs[o.design.coerceIn(0, COUNT - 1)].parts) {
            when (p.kind) {
                KPart.ARC -> {
                    val dx = bx - cx(o, p, t)
                    val dy = (by - cy(o, p)) / p.ey
                    val dist = sqrt(dx * dx + dy * dy)
                    if (abs(dist - rad(o, p, t)) > band) continue
                    var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - ang(o, p, t)
                    a = ((a % 360f) + 360f) % 360f
                    if (a < p.gap) continue
                    val k = segs(p, o.n)
                    val seg = min(k - 1, ((a - p.gap) / ((360f - p.gap) / k)).toInt())
                    if ((seg + o.shift + p.coff) % o.n != color) return true
                }
                KPart.POLY, KPart.STAR -> {
                    val k = segs(p, o.n) * (if (p.kind == KPart.STAR) 2 else 1)
                    for (i in 0 until k) {
                        val ci = if (p.kind == KPart.STAR) i / 2 else i
                        if ((ci + o.shift + p.coff) % o.n == color) continue
                        val j = (i + 1) % k
                        if (Geo.segDist(bx, by, vertX(o, p, t, i), vertY(o, p, t, i), vertX(o, p, t, j), vertY(o, p, t, j)) < band) return true
                    }
                }
                KPart.SPOKE -> {
                    val k = segs(p, o.n)
                    val r = rad(o, p, t)
                    val r0 = p.r2 * scale(o)
                    val x0 = cx(o, p, t)
                    val y0 = cy(o, p)
                    for (i in 0 until k) {
                        if ((i + o.shift + p.coff) % o.n == color) continue
                        val a = (ang(o, p, t) + i * 360f / k) * DEG
                        val ca = cos(a)
                        val sa = sin(a) * p.ey
                        if (Geo.segDist(bx, by, x0 + ca * r0, y0 + sa * r0, x0 + ca * r, y0 + sa * r) < band) return true
                    }
                }
                KPart.DOTS -> {
                    val k = segs(p, o.n)
                    val r = rad(o, p, t)
                    val rr = p.r2 + Cfg.BALL_R * 0.85f
                    val x0 = cx(o, p, t)
                    val y0 = cy(o, p)
                    for (i in 0 until k) {
                        if ((i + o.shift + p.coff) % o.n == color) continue
                        val a = (ang(o, p, t) + i * 360f / k) * DEG
                        val dx = bx - (x0 + cos(a) * r)
                        val dy = by - (y0 + sin(a) * r * p.ey)
                        if (dx * dx + dy * dy < rr * rr) return true
                    }
                }
                KPart.BAR -> {
                    if (abs(by - cy(o, p)) > Cfg.BAR_H + graze) continue
                    if (barColor(o, p, bx, t) != color) return true
                }
            }
        }
        return false
    }
}
