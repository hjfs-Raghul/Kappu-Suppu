package com.kappusuppu.drawing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class Pt(val x: Float, val y: Float)

/**
 * Generates slightly irregular, hand-drawn point sequences. All functions are pure (they only use
 * the supplied [Random]) so a given seed always produces the same pencil line.
 * Units are caller-defined: the character drawings use a 0..1 box, UI borders use pixels.
 */
object Wobble {
    private const val TWO_PI = 2.0 * PI

    fun line(a: Pt, b: Pt, rnd: Random, amp: Float = 0.006f, step: Float = 0.025f): List<Pt> {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val len = hypot(dx, dy)
        if (len < 1e-6) return listOf(a, b)
        val n = max(3, (len / step).toInt())
        val nx = -dy / len
        val ny = dx / len
        val phase = rnd.nextDouble() * TWO_PI
        val freq = 1.0 + rnd.nextDouble() * 2.0
        val drift = (rnd.nextDouble() - 0.5) * amp * 2.0
        return List(n + 1) { i ->
            val t = i.toDouble() / n
            val off = sin(t * freq * PI + phase) * amp * 0.8 + drift * t + (rnd.nextDouble() - 0.5) * amp * 0.35
            Pt((a.x + dx * t + nx * off).toFloat(), (a.y + dy * t + ny * off).toFloat())
        }
    }

    /** Open or closed polyline made of wobbly segments. */
    fun polyline(points: List<Pt>, rnd: Random, closed: Boolean = false, amp: Float = 0.006f, step: Float = 0.025f): List<Pt> {
        require(points.size >= 2)
        val pts = if (closed) points + points.first() else points
        val out = ArrayList<Pt>()
        for (i in 0 until pts.size - 1) {
            val seg = line(pts[i], pts[i + 1], rnd, amp, step)
            if (i == 0) out.addAll(seg) else out.addAll(seg.drop(1))
        }
        return out
    }

    /** A circle/ellipse drawn like a child would: starts anywhere, slight overshoot, uneven radius. */
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rnd: Random, overlap: Double = 0.12, step: Float = 0.02f): List<Pt> {
        val start = rnd.nextDouble() * TWO_PI
        val sweep = TWO_PI * (1.0 + overlap)
        val n = max(18, (TWO_PI * max(rx, ry) / step).toInt())
        val p1 = rnd.nextDouble() * TWO_PI
        val p2 = rnd.nextDouble() * TWO_PI
        val spiral = 0.05 * (if (rnd.nextBoolean()) 1 else -1)
        return List(n + 1) { i ->
            val t = i.toDouble() / n
            val ang = start + sweep * t
            val k = 1.0 + 0.03 * sin(2 * ang + p1) + 0.02 * sin(3 * ang + p2) + spiral * t + (rnd.nextDouble() - 0.5) * 0.006
            Pt((cx + cos(ang) * rx * k).toFloat(), (cy + sin(ang) * ry * k).toFloat())
        }
    }

    /** Quadratic curve a -> b bending towards [control], with pencil wobble. */
    fun curve(a: Pt, control: Pt, b: Pt, rnd: Random, amp: Float = 0.004f): List<Pt> {
        val n = 14
        val raw = List(n + 1) { i ->
            val t = i.toDouble() / n
            val u = 1 - t
            Pt(
                (u * u * a.x + 2 * u * t * control.x + t * t * b.x).toFloat(),
                (u * u * a.y + 2 * u * t * control.y + t * t * b.y).toFloat(),
            )
        }
        return raw.map { Pt(it.x + ((rnd.nextDouble() - 0.5) * amp).toFloat(), it.y + ((rnd.nextDouble() - 0.5) * amp).toFloat()) }
    }
}

object InkMath {
    fun length(points: List<Pt>): Float {
        var total = 0f
        for (i in 1 until points.size) total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return total
    }

    /** The first [fraction] (0..1) of the polyline, measured by length. */
    fun partial(points: List<Pt>, fraction: Float): List<Pt> {
        if (points.isEmpty()) return points
        val f = fraction.coerceIn(0f, 1f)
        if (f >= 1f) return points
        if (f <= 0f) return listOf(points.first())
        val target = length(points) * f
        val out = ArrayList<Pt>()
        out.add(points.first())
        var walked = 0f
        for (i in 1 until points.size) {
            val seg = hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            if (walked + seg >= target) {
                val t = if (seg <= 0f) 0f else (target - walked) / seg
                out.add(
                    Pt(
                        points[i - 1].x + (points[i].x - points[i - 1].x) * t,
                        points[i - 1].y + (points[i].y - points[i - 1].y) * t,
                    ),
                )
                return out
            }
            walked += seg
            out.add(points[i])
        }
        return out
    }
}
