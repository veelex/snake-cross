package com.auto.rr.domain.pattern

import com.auto.rr.domain.indicator.PivotPoint

/**
 * Forces strict high/low alternation. When two pivots of the same kind sit back to back,
 * only the more extreme one survives (higher high / lower low), so pattern windows are
 * never skipped just because a minor same-side pivot sits between two legs.
 */
object PivotNormalizer {

    fun alternate(pivots: List<PivotPoint>): List<PivotPoint> {
        val out = ArrayList<PivotPoint>(pivots.size)
        for (p in pivots) {
            val last = out.lastOrNull()
            if (last == null || last.isHigh != p.isHigh) {
                out += p
                continue
            }
            val moreExtreme = if (p.isHigh) p.price > last.price else p.price < last.price
            if (moreExtreme) out[out.size - 1] = p
        }
        return out
    }
}
