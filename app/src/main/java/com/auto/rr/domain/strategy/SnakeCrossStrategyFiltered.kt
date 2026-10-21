package com.auto.rr.domain.strategy

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.pattern.Pattern
import kotlin.math.abs

object SnakeCrossStrategy {

    private val PERIODS = listOf(1, 5, 8, 13, 55)

    fun detect(
        candles: List<CandleEntity>,
        patterns: List<Pattern>,
        emas: Map<Int, DoubleArray>,
        atr: DoubleArray,
        sessionConfig: SessionConfig,
        cfg: StrategyConfig
    ): List<Signal> {
        if (candles.isEmpty() || patterns.isEmpty() || emas.isEmpty() || atr.isEmpty()) return emptyList()

        val arrays = ArrayList<DoubleArray>(PERIODS.size)
        for (p in PERIODS) arrays += emas[p] ?: return emptyList()

        val out = ArrayList<Signal>()
        val filterOn = sessionConfig.enabled && sessionConfig.filterEntries

        for (pat in patterns) {
            val confirmIdx = pat.confirmationIndex ?: continue
            if (confirmIdx < 0 || confirmIdx >= candles.size) continue

            val from = confirmIdx
            val to = (confirmIdx + cfg.crossWindow).coerceAtMost(candles.size - 1)
            if (from > to) continue

            var triggerIdx = -1
            var triggerPrice = 0.0

            for (i in from..to) {
                val e1 = arrays[0].getOrNull(i) ?: continue
                val e5 = arrays[1].getOrNull(i) ?: continue
                val e8 = arrays[2].getOrNull(i) ?: continue
                val e13 = arrays[3].getOrNull(i) ?: continue
                val e55 = arrays[4].getOrNull(i) ?: continue

                if (!e1.isFinite() || !e5.isFinite() || !e8.isFinite() ||
                    !e13.isFinite() || !e55.isFinite() || e55 <= 0.0) continue

                val spread = abs(e1 - e55) / e55
                if (spread > cfg.convergenceFraction) continue

                val aligned = if (pat.bullish) {
                    e1 > e5 && e5 > e8 && e8 > e13 && e13 > e55
                } else {
                    e1 < e5 && e5 < e8 && e8 < e13 && e13 < e55
                }
                if (aligned) {
                    triggerIdx = i
                    triggerPrice = candles[i].close
                    break
                }
            }

            if (triggerIdx < 0) continue

            // Session filter — skip if trigger bar falls outside all sessions.
            if (filterOn) {
                val hour = ((candles[triggerIdx].timestamp / 3_600_000L) % 24L).toInt()
                if (!sessionConfig.isHourInSession(hour)) continue
            }

            val atrAt = atr.getOrNull(triggerIdx) ?: continue
            if (!atrAt.isFinite() || atrAt <= 0.0) continue

            val patternLow = pat.pivotPrices.minOrNull() ?: continue
            val patternHigh = pat.pivotPrices.maxOrNull() ?: continue

            val stop = if (pat.bullish) {
                patternLow - cfg.stopAtrMultiplier * atrAt
            } else {
                patternHigh + cfg.stopAtrMultiplier * atrAt
            }

            val risk = abs(triggerPrice - stop)
            if (risk <= 0.0) continue

            val target = if (pat.bullish) {
                triggerPrice + cfg.targetRMultiple * risk
            } else {
                triggerPrice - cfg.targetRMultiple * risk
            }

            out += Signal(
                bullish = pat.bullish,
                patternType = pat.type,
                patternIndex = pat.rightIndex,
                triggerIndex = triggerIdx,
                triggerPrice = triggerPrice,
                stopPrice = stop,
                targetPrice = target,
                atr14AtTrigger = atrAt
            )
        }
        return out
    }
}
