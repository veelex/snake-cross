package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import com.auto.rr.domain.indicator.PivotPoint
import kotlin.math.hypot

/**
 * Finds the swing pivot closest to a tap, or null when none is within [radiusPx].
 * Distances are measured in screen space against the pivot marker position, so the hit area
 * stays the same size at every zoom level. Returns the position in [pivots], not a candle index.
 */
internal fun pivotListIndexAt(
    pivots: List<PivotPoint>,
    transform: ChartTransform,
    tap: Offset,
    viewHeight: Float,
    radiusPx: Float
): Int? {
    var best = -1
    var bestDist = radiusPx
    for (i in pivots.indices) {
        val p = pivots[i]
        val x = transform.screenXFor(p.index.toFloat()) + transform.xScale / 2f
        if (x < tap.x - radiusPx || x > tap.x + radiusPx) continue
        val y = transform.screenYFor(p.price, viewHeight)
        if (!y.isFinite()) continue
        val d = hypot((x - tap.x).toDouble(), (y - tap.y).toDouble()).toFloat()
        if (d <= bestDist) {
            bestDist = d
            best = i
        }
    }
    return if (best >= 0) best else null
}
