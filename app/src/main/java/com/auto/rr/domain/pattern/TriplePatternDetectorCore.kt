package com.auto.rr.domain.pattern

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.PivotPoint
import kotlin.math.abs

object TriplePatternDetector {

    fun detect(
        candles: List<CandleEntity>,
        pivots: List<PivotPoint>,
        cfg: PatternConfig,
        atr: DoubleArray = DoubleArray(0),
        pivotStrength: Int = 2,
        rsi: DoubleArray = DoubleArray(0)
    ): List<Pattern> {
        if (candles.isEmpty()) return emptyList()
        val pv = PivotNormalizer.alternate(pivots)
        if (pv.size < 5) return emptyList()

        val out = ArrayList<Pattern>()
        for (k in 0 until pv.size - 4) {
            val leg1 = pv[k]
            val neck1 = pv[k + 1]
            val leg2 = pv[k + 2]
            val neck2 = pv[k + 3]
            val leg3 = pv[k + 4]

            if (leg2.index - leg1.index < cfg.minBarsBetweenPivots) continue
            if (leg3.index - leg2.index < cfg.minBarsBetweenPivots) continue
            if (leg3.index - leg1.index > cfg.maxPatternBars) continue

            val bullish = !leg1.isHigh
            val legs = doubleArrayOf(leg1.price, leg2.price, leg3.price)
            val avgLeg = legs.average()
            if (avgLeg <= 0.0) continue

            val atrAtLeg1 = atr.getOrNull(leg1.index) ?: Double.NaN
            val atrAtLeg2 = atr.getOrNull(leg2.index) ?: Double.NaN
            val atrAtLeg3 = atr.getOrNull(leg3.index) ?: Double.NaN
            val atrAtNeck1 = atr.getOrNull(neck1.index) ?: Double.NaN
            val atrAtNeck2 = atr.getOrNull(neck2.index) ?: Double.NaN

            if (!DoublePatternDetector.hasTrendContext(candles, leg1.index, bullish, atrAtLeg1, cfg)) continue

            val finiteLegs = listOf(atrAtLeg1, atrAtLeg2, atrAtLeg3).filter { it.isFinite() }
            val atrAtLegs = if (finiteLegs.isEmpty()) Double.NaN else finiteLegs.max()
            val finiteNecks = listOf(atrAtNeck1, atrAtNeck2).filter { it.isFinite() && it > 0.0 }
            val atrAtDepth = if (finiteNecks.isNotEmpty()) finiteNecks.average() else atrAtLegs

            val tol = DoublePatternDetector.legTolerance(cfg, atrAtLegs, avgLeg)
            val lo = legs.min()
            val hi = legs.max()
            val spread = hi - lo
            if (spread > tol) continue

            if (cfg.requireMiddleLegExtreme) {
                val middleIsExtreme = if (bullish)
                    leg2.price < leg1.price && leg2.price < leg3.price
                else
                    leg2.price > leg1.price && leg2.price > leg3.price
                if (!middleIsExtreme) continue
            }

            val neckTol = if (atrAtLegs.isFinite() && atrAtLegs > 0.0) cfg.neckToleranceAtr * atrAtLegs
                          else cfg.legToleranceFraction * (neck1.price + neck2.price) / 2.0
            if (abs(neck1.price - neck2.price) > neckTol) continue

            val legExtreme = if (bullish) lo else hi
            val legInner = if (bullish) hi else lo
            val minDepth = DoublePatternDetector.minDepth(cfg, atrAtDepth, avgLeg)
            val d1 = if (bullish) neck1.price - legInner else legInner - neck1.price
            val d2 = if (bullish) neck2.price - legInner else legInner - neck2.price
            if (d1 < minDepth || d2 < minDepth) continue

            val neckline = if (bullish) maxOf(neck1.price, neck2.price)
                           else minOf(neck1.price, neck2.price)

            if (abs(neckline - legExtreme) * 10_000.0 < cfg.minPatternHeightPips) continue

            val invalidBuf = if (atrAtLegs.isFinite() && atrAtLegs > 0.0) cfg.invalidationAtr * atrAtLegs
                             else cfg.invalidationFraction * avgLeg
            val overshootBuf = if (atrAtLegs.isFinite() && atrAtLegs > 0.0) cfg.confirmOvershootAtr * atrAtLegs
                               else cfg.confirmOvershootFraction * avgLeg
            val res = DoublePatternDetector.resolve(
                candles, leg3.index, pivotStrength, neckline, bullish, legExtreme,
                invalidBuf, overshootBuf, cfg
            ) ?: continue
            if (cfg.requireNecklineBreak && res.confirmation == null) continue

            out += Pattern(
                type = if (bullish) PatternType.TRIPLE_BOTTOM else PatternType.TRIPLE_TOP,
                bullish = bullish,
                pivotIndices = listOf(leg1.index, neck1.index, leg2.index, neck2.index, leg3.index),
                pivotPrices = listOf(leg1.price, neck1.price, leg2.price, neck2.price, leg3.price),
                neckline = neckline,
                confirmationIndex = res.confirmation,
                quality = DoublePatternDetector.quality(
                    spread, tol, minOf(d1, d2), atrAtDepth,
                    DoublePatternDetector.rsiDivergence(rsi, leg1.index, leg3.index, bullish)
                )
            )
        }
        return out
    }
}
