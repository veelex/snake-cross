package com.auto.rr.domain.strategy

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.EmaSweepConfig
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.pattern.Pattern
import kotlin.math.abs
import kotlin.math.min

/**
 * Entry when price cuts through all four slower EMAs (5, 8, 13, 55) at the same time, near a
 * double bottom / triple bottom (buy) or double top / triple top (sell).
 *
 * - Bullish sweep: every EMA was crossed upward within [EmaSweepConfig.sweepBars] bars, the
 *   latest of those crossings is the trigger bar, and the trigger bar closes above all four.
 * - "Near" means both: the trigger is within [EmaSweepConfig.nearBars] bars of the pattern's
 *   completion, and its close is within [EmaSweepConfig.nearAtr] ATR of the neckline or of the
 *   pattern's last leg.
 * - Stop and target reuse the Stop & target settings in [StrategyConfig].
 *
 * Elliott waves are ignored on purpose: this strategy is defined for DB / TB / DT / TT only.
 */
object EmaSweepStrategy {

    fun detect(
        candles: List<CandleEntity>,
        patterns: List<Pattern>,
        emas: Map<Int, DoubleArray>,
        atr: DoubleArray,
        sessionConfig: SessionConfig,
        strategy: StrategyConfig,
        cfg: EmaSweepConfig
    ): List<Signal> {
        if (!cfg.enabled || candles.size < 2 || patterns.isEmpty() || atr.isEmpty()) return emptyList()
        val lines = ArrayList<DoubleArray>(EmaSweepConfig.CROSS_PERIODS.size)
        for (p in EmaSweepConfig.CROSS_PERIODS) lines += emas[p]?.takeIf { it.size == candles.size } ?: return emptyList()

        val closes = DoubleArray(candles.size) { candles[it].close }
        val filterOn = sessionConfig.enabled && sessionConfig.filterEntries
        val out = ArrayList<Signal>()
        val seen = HashSet<Long>() // one signal per trigger bar and direction

        for (pat in patterns) {
            if (pat.isElliott) continue
            val bullish = pat.bullish
            val completion = pat.confirmationIndex ?: pat.rightIndex
            val from = pat.rightIndex.coerceAtLeast(1)
            val to = (completion + cfg.nearBars).coerceAtMost(candles.size - 1)
            if (from > to) continue

            val lastLeg = pat.pivotPrices.lastOrNull() ?: continue
            val patternLow = pat.pivotPrices.minOrNull() ?: continue
            val patternHigh = pat.pivotPrices.maxOrNull() ?: continue

            for (i in from..to) {
                val atrAt = atr.getOrNull(i) ?: continue
                if (!atrAt.isFinite() || atrAt <= 0.0) continue
                if (!sweptAll(closes, lines, i, bullish, cfg.sweepBars)) continue

                val close = closes[i]
                val distance = min(abs(close - pat.neckline), abs(close - lastLeg))
                if (distance > cfg.nearAtr * atrAt) continue

                if (filterOn) {
                    val hour = ((candles[i].timestamp / 3_600_000L) % 24L).toInt()
                    if (!sessionConfig.isHourInSession(hour)) continue
                }

                val stop = if (bullish) patternLow - strategy.stopAtrMultiplier * atrAt
                else patternHigh + strategy.stopAtrMultiplier * atrAt
                val risk = abs(close - stop)
                if (risk <= 0.0) continue
                val target = if (bullish) close + strategy.targetRMultiple * risk
                else close - strategy.targetRMultiple * risk

                val key = i.toLong() * 2 + if (bullish) 1 else 0
                if (!seen.add(key)) break // another pattern already fired here

                out += Signal(
                    bullish = bullish,
                    patternType = pat.type,
                    patternIndex = pat.rightIndex,
                    triggerIndex = i,
                    triggerPrice = close,
                    stopPrice = stop,
                    targetPrice = target,
                    atr14AtTrigger = atrAt,
                    source = SignalSource.EMA_SWEEP
                )
                break // first sweep per pattern
            }
        }
        return out
    }

    /** True when bar [i] closes beyond all four EMAs and each one was crossed within the window. */
    private fun sweptAll(
        closes: DoubleArray,
        lines: List<DoubleArray>,
        i: Int,
        bullish: Boolean,
        window: Int
    ): Boolean {
        // Must end on a fresh crossing, so the same sweep is not reported again on later bars.
        var crossedNow = false
        for (ema in lines) {
            val e = ema[i]
            if (!e.isFinite()) return false
            if (if (bullish) closes[i] <= e else closes[i] >= e) return false
            if (crossedAt(closes, ema, i, bullish)) crossedNow = true
        }
        if (!crossedNow) return false

        val start = (i - window + 1).coerceAtLeast(1)
        for (ema in lines) {
            var found = false
            for (j in start..i) {
                if (crossedAt(closes, ema, j, bullish)) { found = true; break }
            }
            if (!found) return false
        }
        return true
    }

    private fun crossedAt(closes: DoubleArray, ema: DoubleArray, j: Int, bullish: Boolean): Boolean {
        if (j < 1) return false
        val e0 = ema[j - 1]
        val e1 = ema[j]
        if (!e0.isFinite() || !e1.isFinite()) return false
        return if (bullish) closes[j - 1] <= e0 && closes[j] > e1
        else closes[j - 1] >= e0 && closes[j] < e1
    }
}
