package com.auto.rr.domain.indicator

import com.auto.rr.data.local.CandleEntity

object FvgDetector {

    fun detect(candles: List<CandleEntity>, cfg: FvgConfig): List<FvgZone> {
        val n = candles.size
        if (n < 3) return emptyList()
        val out = ArrayList<FvgZone>()

        for (i in 2 until n) {
            val c1 = candles[i - 2]
            val c3 = candles[i]
            val refPrice = (c1.high + c1.low + c3.high + c3.low) / 4.0
            if (!refPrice.isFinite() || refPrice <= 0.0) continue
            val minGap = cfg.minGapFraction * refPrice

            // Bullish FVG: gap above c1.high, below c3.low
            if (c1.high < c3.low) {
                val gap = c3.low - c1.high
                if (gap >= minGap) {
                    val mit = findMitigation(candles, i + 1, c1.high, bullish = true)
                    if (cfg.showMitigated || mit == null) {
                        out += FvgZone(
                            bullish = true,
                            startIndex = i,
                            upperBound = c3.low,
                            lowerBound = c1.high,
                            mitigationIndex = mit
                        )
                    }
                }
            }

            // Bearish FVG: gap below c1.low, above c3.high
            if (c1.low > c3.high) {
                val gap = c1.low - c3.high
                if (gap >= minGap) {
                    val mit = findMitigation(candles, i + 1, c1.low, bullish = false)
                    if (cfg.showMitigated || mit == null) {
                        out += FvgZone(
                            bullish = false,
                            startIndex = i,
                            upperBound = c1.low,
                            lowerBound = c3.high,
                            mitigationIndex = mit
                        )
                    }
                }
            }
        }
        return out
    }

    /** First bar at or after [from] whose low (bullish) / high (bearish) crosses [level]. */
    private fun findMitigation(
        candles: List<CandleEntity>,
        from: Int,
        level: Double,
        bullish: Boolean
    ): Int? {
        for (i in from until candles.size) {
            val c = candles[i]
            if (bullish && c.low <= level) return i
            if (!bullish && c.high >= level) return i
        }
        return null
    }
}
