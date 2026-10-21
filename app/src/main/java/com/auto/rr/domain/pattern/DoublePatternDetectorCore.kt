package com.auto.rr.domain.pattern

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.PivotPoint
import kotlin.math.abs

object DoublePatternDetector {

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
        if (pv.size < 3) return emptyList()

        val out = ArrayList<Pattern>()
        for (k in 0 until pv.size - 2) {
            val leg1 = pv[k]
            val neck = pv[k + 1]
            val leg2 = pv[k + 2]

            val span = leg2.index - leg1.index
            if (span < cfg.minBarsBetweenPivots || span > cfg.maxPatternBars) continue

            val bullish = !leg1.isHigh
            val avgLeg = (leg1.price + leg2.price) / 2.0
            if (avgLeg <= 0.0) continue

            val atrAtLeg1 = atr.getOrNull(leg1.index) ?: Double.NaN
            val atrAtLeg2 = atr.getOrNull(leg2.index) ?: Double.NaN
            val atrAtNeck = atr.getOrNull(neck.index) ?: Double.NaN

            if (!hasTrendContext(candles, leg1.index, bullish, atrAtLeg1, cfg)) continue

            val atrAtLegs = if (atrAtLeg1.isFinite() && atrAtLeg2.isFinite()) maxOf(atrAtLeg1, atrAtLeg2)
                            else if (atrAtLeg1.isFinite()) atrAtLeg1
                            else atrAtLeg2
            val atrAtDepth = if (atrAtNeck.isFinite() && atrAtNeck > 0.0) atrAtNeck else atrAtLegs

            val tol = legTolerance(cfg, atrAtLegs, avgLeg)
            val spread = abs(leg1.price - leg2.price)
            if (spread > tol) continue

            val legExtreme = if (bullish) minOf(leg1.price, leg2.price) else maxOf(leg1.price, leg2.price)
            val legInner = if (bullish) maxOf(leg1.price, leg2.price) else minOf(leg1.price, leg2.price)
            val depth = if (bullish) neck.price - legInner else legInner - neck.price
            if (depth < minDepth(cfg, atrAtDepth, avgLeg)) continue
            if (abs(neck.price - legExtreme) * 10_000.0 < cfg.minPatternHeightPips) continue

            val neckline = neck.price
            val invalidBuf = if (atrAtLegs.isFinite() && atrAtLegs > 0.0) cfg.invalidationAtr * atrAtLegs
                             else cfg.invalidationFraction * avgLeg
            val overshootBuf = if (atrAtLegs.isFinite() && atrAtLegs > 0.0) cfg.confirmOvershootAtr * atrAtLegs
                               else cfg.confirmOvershootFraction * avgLeg
            val res = resolve(
                candles, leg2.index, pivotStrength, neckline, bullish, legExtreme,
                invalidBuf, overshootBuf, cfg
            ) ?: continue
            if (cfg.requireNecklineBreak && res.confirmation == null) continue

            out += Pattern(
                type = if (bullish) PatternType.DOUBLE_BOTTOM else PatternType.DOUBLE_TOP,
                bullish = bullish,
                pivotIndices = listOf(leg1.index, neck.index, leg2.index),
                pivotPrices = listOf(leg1.price, neck.price, leg2.price),
                neckline = neckline,
                confirmationIndex = res.confirmation,
                quality = quality(
                    spread, tol, depth, atrAtDepth,
                    rsiDivergence(rsi, leg1.index, leg2.index, bullish)
                )
            )
        }
        return out
    }

    internal class Resolution(val confirmation: Int?)

    internal fun resolve(
        candles: List<CandleEntity>,
        lastLegIndex: Int,
        pivotStrength: Int,
        neckline: Double,
        bullish: Boolean,
        legExtreme: Double,
        invalidationBuffer: Double,
        overshootBuffer: Double,
        cfg: PatternConfig
    ): Resolution? {
        val from = lastLegIndex + pivotStrength.coerceAtLeast(1)
        val end = from + cfg.confirmWithinBars
        var i = from
        while (i < candles.size && i <= end) {
            val close = candles[i].close
            if (cfg.invalidateOnLegBreak) {
                if (bullish && close < legExtreme - invalidationBuffer) return null
                if (!bullish && close > legExtreme + invalidationBuffer) return null
            }
            if (bullish && close > neckline + overshootBuffer) return Resolution(i)
            if (!bullish && close < neckline - overshootBuffer) return Resolution(i)
            i++
        }
        return if (candles.size - 1 <= end) Resolution(null) else null
    }

    internal fun legTolerance(cfg: PatternConfig, atrAt: Double, price: Double): Double =
        if (atrAt.isFinite() && atrAt > 0.0) cfg.legToleranceAtr * atrAt
        else cfg.legToleranceFraction * price

    internal fun minDepth(cfg: PatternConfig, atrAt: Double, price: Double): Double =
        if (atrAt.isFinite() && atrAt > 0.0) cfg.minNeckDepthAtr * atrAt
        else cfg.minNeckDepthFraction * price

    internal fun quality(
        spread: Double,
        tol: Double,
        depth: Double,
        atrAt: Double,
        divergence: Double
    ): Double {
        val symmetry = if (tol > 0.0) (1.0 - spread / tol).coerceIn(0.0, 1.0) else 0.0
        val depthScore = if (atrAt.isFinite() && atrAt > 0.0) (depth / (3.0 * atrAt)).coerceIn(0.0, 1.0) else 0.5
        val divScore = if (divergence.isNaN()) 0.5 else (divergence / 10.0).coerceIn(0.0, 1.0)
        return 0.4 * symmetry + 0.4 * depthScore + 0.2 * divScore
    }

    internal fun rsiDivergence(rsi: DoubleArray, aIndex: Int, bIndex: Int, bullish: Boolean): Double {
        if (rsi.isEmpty()) return Double.NaN
        val a = rsi.getOrNull(aIndex) ?: return Double.NaN
        val b = rsi.getOrNull(bIndex) ?: return Double.NaN
        if (!a.isFinite() || !b.isFinite()) return Double.NaN
        return if (bullish) b - a else a - b
    }

    internal fun hasTrendContext(
        candles: List<CandleEntity>,
        leg1Index: Int,
        bullish: Boolean,
        atr: Double,
        cfg: PatternConfig
    ): Boolean {
        if (!cfg.requireTrendContext) return true
        if (!atr.isFinite() || atr <= 0.0) return true

        val lookback = cfg.trendLookbackBars
        val start = (leg1Index - lookback).coerceAtLeast(0)
        if (start >= leg1Index) return true

        val before = candles[start].close
        val atLeg = candles[leg1Index].close
        val move = atLeg - before
        val threshold = cfg.trendMinAtr * atr

        return if (bullish) move < -threshold else move > threshold
    }
}
