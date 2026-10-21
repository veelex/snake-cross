package com.auto.rr.domain.indicator

import com.auto.rr.data.local.CandleEntity

object PivotDetector {

    /**
     * Finds swing highs and lows.
     *
     * A bar is a pivot when its high (or low) is the strict extreme of the
     * +/- [PivotConfig.strength] window. When [atr] is supplied and non-empty,
     * the pivot must additionally swing at least [PivotConfig.prominenceAtr] * ATR
     * away from the *neighbouring* (non-pivot) bars, which removes micro-pivots
     * that are technically extremes but have no tradable range.
     *
     * When a bar is both a swing high and a swing low (rare, high-volatility bar),
     * both pivots are emitted. PivotNormalizer handles the resulting runs.
     */
    fun detect(
        candles: List<CandleEntity>,
        cfg: PivotConfig,
        atr: DoubleArray = DoubleArray(0)
    ): List<PivotPoint> {
        val n = candles.size
        val s = cfg.strength
        if (n < 2 * s + 1) return emptyList()

        val useProminence = atr.isNotEmpty() && cfg.prominenceAtr > 0.0

        val out = ArrayList<PivotPoint>()
        for (i in s until n - s) {
            val c = candles[i]
            var isHigh = true
            var isLow = true

            for (j in (i - s)..(i + s)) {
                if (j == i) continue
                val other = candles[j]
                if (other.high >= c.high) isHigh = false
                if (other.low <= c.low) isLow = false
                if (!isHigh && !isLow) break
            }

            if (useProminence && (isHigh || isLow)) {
                val a = atr.getOrNull(i) ?: Double.NaN
                if (!a.isFinite() || a <= 0.0) {
                    // ATR not ready at this index; fall through to classic behaviour.
                } else {
                    val minRequired = cfg.prominenceAtr * a

                    if (isHigh) {
                        var minNeighbourHigh = Double.POSITIVE_INFINITY
                        for (j in (i - s)..(i + s)) {
                            if (j == i) continue
                            val h = candles[j].high
                            if (h < minNeighbourHigh) minNeighbourHigh = h
                        }
                        if (c.high - minNeighbourHigh < minRequired) isHigh = false
                    }

                    if (isLow) {
                        var maxNeighbourLow = Double.NEGATIVE_INFINITY
                        for (j in (i - s)..(i + s)) {
                            if (j == i) continue
                            val l = candles[j].low
                            if (l > maxNeighbourLow) maxNeighbourLow = l
                        }
                        if (maxNeighbourLow - c.low < minRequired) isLow = false
                    }
                }
            }

            if (isHigh) out += PivotPoint(i, c.high, true)
            if (isLow)  out += PivotPoint(i, c.low, false)
        }
        return out
    }
}
