package com.auto.rr.domain.indicator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The two swings a spiral is built from: it winds around [pole] and passes through [through]. */
data class SpiralAnchors(val pole: PivotPoint, val through: PivotPoint) {
    val isUpSwing: Boolean get() = through.price > pole.price
}

/**
 * Logarithmic spiral  r(t) = r0 * g^(t / (PI/2))  where t is the angle travelled from the
 * "through" anchor and g is the growth per quarter turn (phi for the golden spiral).
 *
 * Pure geometry, no Compose or Android types, so it can be unit tested on the JVM.
 */
object LogSpiral {

    const val GOLDEN = 1.6180339887498949
    private const val QUARTER = PI / 2.0

    /**
     * Picks the anchors. [through] is the pivot [swingOffset] places back from the newest one;
     * [pole] is the closest earlier pivot of the opposite kind (high vs low), so the pair is a
     * genuine swing. Returns null when there are not enough pivots.
     */
    fun anchors(pivots: List<PivotPoint>, swingOffset: Int): SpiralAnchors? =
        anchorsAt(pivots, pivots.size - 1 - swingOffset.coerceAtLeast(0))

    /**
     * Anchors with [through] = the pivot at [throughListIndex] in [pivots] (a position in the list,
     * not a candle index) and [pole] = the closest earlier pivot of the opposite kind.
     * Returns null when that pivot has no earlier opposite pivot to wind around.
     */
    fun anchorsAt(pivots: List<PivotPoint>, throughListIndex: Int): SpiralAnchors? {
        val bi = throughListIndex
        if (bi <= 0 || bi >= pivots.size) return null
        val b = pivots[bi]
        for (i in bi - 1 downTo 0) {
            val a = pivots[i]
            if (a.isHigh != b.isHigh && a.index < b.index) return SpiralAnchors(a, b)
        }
        return null
    }

    /** +1 = clockwise on screen (y points down), -1 = counter-clockwise. */
    fun windingSign(direction: SpiralDirection, anchors: SpiralAnchors): Int = when (direction) {
        SpiralDirection.CLOCKWISE -> 1
        SpiralDirection.COUNTER_CLOCKWISE -> -1
        SpiralDirection.AUTO -> if (anchors.isUpSwing) -1 else 1
    }

    /** Radius at angle [t] (radians travelled from the through-anchor, negative = inward). */
    fun radiusAt(r0: Double, growth: Double, t: Double): Double =
        r0 * growth.pow(t / QUARTER)

    /**
     * Polyline as interleaved x,y pairs, from the innermost to the outermost point.
     *
     * @param cx,cy      pole position
     * @param r0         distance from pole to the through-anchor
     * @param theta0     angle of the through-anchor as seen from the pole (atan2(dy, dx))
     * @param sign       winding, see [windingSign]
     * @param maxRadius  the outward part stops once the radius exceeds this, so a high growth
     *                   factor cannot generate a huge off-screen path
     */
    fun polyline(
        cx: Double,
        cy: Double,
        r0: Double,
        theta0: Double,
        growth: Double,
        sign: Int,
        inwardTurns: Double,
        outwardTurns: Double,
        maxRadius: Double,
        stepsPerTurn: Int = 64
    ): FloatArray {
        if (!(r0 > 0.0) || !(growth > 1.0)) return FloatArray(0)
        val steps = stepsPerTurn.coerceAtLeast(8)
        val dt = 2.0 * PI / steps
        val inSteps = (inwardTurns.coerceAtLeast(0.0) * steps).toInt()
        val outSteps = (outwardTurns.coerceAtLeast(0.0) * steps).toInt()

        val out = ArrayList<Float>((inSteps + outSteps + 1) * 2)
        for (k in -inSteps..outSteps) {
            val t = k * dt
            val r = radiusAt(r0, growth, t)
            if (r > maxRadius) break
            val a = theta0 + sign * t
            out.add((cx + r * cos(a)).toFloat())
            out.add((cy + r * sin(a)).toFloat())
        }
        return out.toFloatArray()
    }

    /** Positions of the quarter-turn marks (t = k * PI/2, k != 0) within the drawn range. */
    fun quarterMarks(
        cx: Double,
        cy: Double,
        r0: Double,
        theta0: Double,
        growth: Double,
        sign: Int,
        inwardTurns: Double,
        outwardTurns: Double,
        maxRadius: Double
    ): FloatArray {
        if (!(r0 > 0.0) || !(growth > 1.0)) return FloatArray(0)
        val kIn = (inwardTurns.coerceAtLeast(0.0) * 4.0).toInt()
        val kOut = (outwardTurns.coerceAtLeast(0.0) * 4.0).toInt()
        val out = ArrayList<Float>()
        for (k in -kIn..kOut) {
            if (k == 0) continue
            val r = radiusAt(r0, growth, k * QUARTER)
            if (r > maxRadius) break
            val a = theta0 + sign * k * QUARTER
            out.add((cx + r * cos(a)).toFloat())
            out.add((cy + r * sin(a)).toFloat())
        }
        return out.toFloatArray()
    }

    fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
