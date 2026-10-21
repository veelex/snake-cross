package com.auto.rr.domain.strategy

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.PivotDetector
import com.auto.rr.domain.indicator.PivotPoint
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.pattern.PatternType
import com.auto.rr.domain.pattern.PivotNormalizer
import kotlin.math.abs

/** Order levels of one wave-3 setup. */
data class WaveLevels(
    /** Limit price: the expected end of wave 2. */
    val entry: Double,
    /** Stop loss: the start of wave 1 (plus any buffer). */
    val stop: Double,
    /** Profit target (PTL): the expected top of wave 3. */
    val target: Double,
    /** End of wave 1. Price trading through it before the limit fills means wave 3 ran without us. */
    val runaway: Double
)

object WaveMath {

    /**
     * The formulas of the notes, for either direction.
     *
     * @param start price at the start of wave 1 (y_l for an up wave, y_h for a down wave)
     * @param end price at the end of wave 1 (y_h for an up wave, y_l for a down wave)
     * @param ratio wave 2 retracement of wave 1, e.g. 0.382
     * @param extension wave 3 length as a multiple of wave 1
     * @param stopBuffer extra distance beyond [start] for the stop, in price
     *
     * Up wave:   entry = y_h - ratio * (y_h - y_l)      stop = y_l      target = entry + extension * (y_h - y_l)
     * Down wave: entry = y_l + ratio * (y_h - y_l)      stop = y_h      target = entry - extension * (y_h - y_l)
     */
    fun levels(start: Double, end: Double, ratio: Double, extension: Double, stopBuffer: Double = 0.0): WaveLevels {
        val dir = if (end > start) 1.0 else -1.0
        val size = abs(end - start)
        val entry = end - dir * ratio * size
        return WaveLevels(
            entry = entry,
            stop = start - dir * stopBuffer,
            target = entry + dir * extension * size,
            runaway = end
        )
    }
}

/**
 * Finds wave 1 / wave 2 pairs and turns each into a limit-order [Signal] for wave 3.
 *
 * Wave points are swing pivots. A pivot is only known [strength] bars after it happened, so the
 * order is placed at that later bar and no future data is used.
 *
 * For each pair of consecutive swings p0 -> p1 (wave 1):
 *  - wave 1 must be at least [WaveThreeConfig.minWave1Atr] ATR long and, optionally, break the swing before it
 *  - [WaveEntryTiming.AFTER_WAVE2]: the next swing p2 is wave 2; it must retrace about 38.2% or 61.8% of
 *    wave 1 (never past p0). The order is placed once p2 is confirmed.
 *  - [WaveEntryTiming.ANTICIPATE]: the order is placed once p1 is confirmed, before wave 2 shows itself
 *  - a setup is skipped when price has already run past the level it needs (the chance is gone)
 */
object WaveThreeStrategy {

    private const val FALLBACK_SIZE_FRACTION = 0.0015

    fun detect(
        candles: List<CandleEntity>,
        basePivots: List<PivotPoint>,
        pivotConfig: PivotConfig,
        atr: DoubleArray,
        sessionConfig: SessionConfig,
        cfg: WaveThreeConfig
    ): List<Signal> {
        if (!cfg.enabled || candles.size < 3) return emptyList()

        val strength = (if (cfg.pivotStrength > 0) cfg.pivotStrength else pivotConfig.strength).coerceAtLeast(1)
        val raw = if (strength == pivotConfig.strength) basePivots
                  else PivotDetector.detect(candles, pivotConfig.copy(strength = strength), atr)
        val pv = PivotNormalizer.alternate(raw)
        if (pv.size < 2) return emptyList()

        val n = candles.size
        val filterOn = sessionConfig.enabled && sessionConfig.filterEntries
        val out = ArrayList<Signal>()

        for (k in 0 until pv.size - 1) {
            val p0 = pv[k]
            val p1 = pv[k + 1]
            val up = !p0.isHigh
            val size = if (up) p1.price - p0.price else p0.price - p1.price
            if (size <= 0.0) continue
            if (p1.index - p0.index < cfg.minBarsPerWave) continue

            // Wave 1 must be a real swing.
            val a1 = atr.getOrNull(p1.index) ?: Double.NaN
            val minSize = if (a1.isFinite() && a1 > 0.0) cfg.minWave1Atr * a1
                          else FALLBACK_SIZE_FRACTION * abs(p1.price)
            if (size < minSize) continue

            // Wave 1 should start a new move: it has to break the swing before it.
            if (cfg.requireStructureBreak && k >= 1) {
                val before = pv[k - 1].price
                if (if (up) p1.price <= before else p1.price >= before) continue
            }

            // Pick the wave-2 point (when it is needed) and the retracement to trade.
            val p2: PivotPoint?
            val ratio: Double
            val activeIdx: Int
            if (cfg.timing == WaveEntryTiming.AFTER_WAVE2) {
                val w2 = pv.getOrNull(k + 2) ?: continue
                if (w2.index - p1.index < cfg.minBarsPerWave) continue
                val depth = (if (up) p1.price - w2.price else w2.price - p1.price) / size
                if (depth <= 0.0 || depth >= 1.0) continue          // wave 2 never goes past the start of wave 1
                ratio = pickRatio(depth, cfg.retrace, cfg.retraceTolerance) ?: continue
                p2 = w2
                activeIdx = w2.index + strength
            } else {
                p2 = null
                // Without wave 2 there is nothing to match against, so Auto falls back to the shallow level.
                ratio = if (cfg.retrace == WaveRetrace.FIB_618) WaveThreeConfig.FIB_618 else WaveThreeConfig.FIB_382
                activeIdx = p1.index + strength
            }
            if (activeIdx >= n) continue

            val atrAt = atr.getOrNull(activeIdx) ?: Double.NaN
            val buffer = if (atrAt.isFinite() && atrAt > 0.0) cfg.stopBufferAtr * atrAt else 0.0
            val lv = WaveMath.levels(p0.price, p1.price, ratio, cfg.targetExtension, buffer)

            // Chance already gone while the pivot was still being confirmed?
            val from = (if (p2 != null) p2.index else p1.index) + 1
            if (goneBeforeActive(candles, from, activeIdx, up, lv, p2 != null)) continue

            if (filterOn) {
                val hour = ((candles[activeIdx].timestamp / 3_600_000L) % 24L).toInt()
                if (!sessionConfig.isHourInSession(hour)) continue
            }

            out += Signal(
                bullish = up,
                patternType = if (up) PatternType.WAVE3_LONG else PatternType.WAVE3_SHORT,
                patternIndex = (p2 ?: p1).index,
                triggerIndex = activeIdx,
                triggerPrice = lv.entry,
                stopPrice = lv.stop,
                targetPrice = lv.target,
                atr14AtTrigger = if (atrAt.isFinite() && atrAt > 0.0) atrAt else 0.0,
                source = SignalSource.WAVE_THREE,
                entryType = EntryType.LIMIT,
                expiryIndex = activeIdx + cfg.expiryBars,
                cancelPrice = if (cfg.cancelOnRunaway) lv.runaway else Double.NaN,
                wave = WaveSetup(
                    startIndex = p0.index, startPrice = p0.price,
                    endIndex = p1.index, endPrice = p1.price,
                    pullbackIndex = p2?.index, pullbackPrice = p2?.price,
                    retrace = ratio, waveSize = size
                )
            )
        }
        return out
    }

    /** The Fibonacci ratio the wave-2 depth [depth] matches, or null when it matches none within [tol]. */
    internal fun pickRatio(depth: Double, mode: WaveRetrace, tol: Double): Double? {
        val a = WaveThreeConfig.FIB_382
        val b = WaveThreeConfig.FIB_618
        return when (mode) {
            WaveRetrace.FIB_382 -> a.takeIf { abs(depth - a) <= tol }
            WaveRetrace.FIB_618 -> b.takeIf { abs(depth - b) <= tol }
            WaveRetrace.AUTO -> {
                val da = abs(depth - a)
                val db = abs(depth - b)
                if (da <= db) a.takeIf { da <= tol } else b.takeIf { db <= tol }
            }
        }
    }

    /**
     * True when, between the wave point and the bar where the order would be placed, price already
     * made the order pointless:
     *  - after wave 2: price already traded past the end of wave 1 (wave 3 is under way)
     *  - after wave 1: price already pulled back through the limit level
     */
    private fun goneBeforeActive(
        candles: List<CandleEntity>,
        from: Int,
        to: Int,
        up: Boolean,
        lv: WaveLevels,
        afterWave2: Boolean
    ): Boolean {
        for (i in from..minOf(to, candles.size - 1)) {
            val c = candles[i]
            if (afterWave2) {
                if (if (up) c.high >= lv.runaway else c.low <= lv.runaway) return true
            } else {
                if (if (up) c.low <= lv.entry else c.high >= lv.entry) return true
            }
        }
        return false
    }
}
