package com.auto.rr.domain.strategy

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.backtest.BacktestConfig
import com.auto.rr.domain.backtest.BacktestEngine
import com.auto.rr.domain.backtest.ExitReason
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.PivotDetector
import com.auto.rr.domain.indicator.SessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Elliott wave-3 entry: the order-level formulas from the notes, the retracement
 * matching, limit-order fills in the backtest, and one end-to-end run on a synthetic price path.
 *
 * The same scenarios were verified against a Python port of the algorithm (tools/wave3_reference).
 */
class WaveThreeTest {

    private val eps = 1e-9

    // ------------------------------------------------------------------ the notes' formulas

    @Test fun upWave_buyLimit_stop_target() {
        val yl = 1.1000
        val yh = 1.1100
        val lv = WaveMath.levels(start = yl, end = yh, ratio = 0.382, extension = 1.618)
        assertEquals(yh - 0.382 * (yh - yl), lv.entry, eps)   // buy limit = yh - 38.2% * (yh - yl)
        assertEquals(yl, lv.stop, eps)                         // stop = start of wave 1
        assertEquals(lv.entry + 1.618 * (yh - yl), lv.target, eps)
        assertEquals(yh, lv.runaway, eps)
    }

    @Test fun downWave_sellLimit_stop_target() {
        val yl = 1.1000
        val yh = 1.1100
        val lv = WaveMath.levels(start = yh, end = yl, ratio = 0.618, extension = 1.618)
        assertEquals(yl + 0.618 * (yh - yl), lv.entry, eps)   // sell limit = yl + 61.8% * (yh - yl)
        assertEquals(yh, lv.stop, eps)
        assertEquals(lv.entry - 1.618 * (yh - yl), lv.target, eps)
    }

    @Test fun stopBuffer_movesStopAwayFromEntry() {
        assertTrue(WaveMath.levels(1.10, 1.11, 0.382, 1.618, 0.0005).stop < 1.10)
        assertTrue(WaveMath.levels(1.11, 1.10, 0.382, 1.618, 0.0005).stop > 1.11)
    }

    // ------------------------------------------------------------------ retracement matching

    @Test fun pickRatio_bands() {
        assertEquals(0.382, WaveThreeStrategy.pickRatio(0.40, WaveRetrace.AUTO, 0.07)!!, eps)
        assertEquals(0.618, WaveThreeStrategy.pickRatio(0.60, WaveRetrace.AUTO, 0.07)!!, eps)
        assertNull(WaveThreeStrategy.pickRatio(0.50, WaveRetrace.AUTO, 0.07))   // between the levels
        assertNull(WaveThreeStrategy.pickRatio(0.30, WaveRetrace.AUTO, 0.07))   // too shallow
        assertNull(WaveThreeStrategy.pickRatio(0.60, WaveRetrace.FIB_382, 0.07))
        assertNull(WaveThreeStrategy.pickRatio(0.40, WaveRetrace.FIB_618, 0.07))
    }

    // ------------------------------------------------------------------ limit fills

    private fun limitSignal(bullish: Boolean, limit: Double, cancel: Double, expiry: Int = 40) = Signal(
        bullish = bullish, patternType = com.auto.rr.domain.pattern.PatternType.WAVE3_LONG,
        patternIndex = 5, triggerIndex = 10, triggerPrice = limit,
        stopPrice = if (bullish) 1.1000 else 1.1100, targetPrice = if (bullish) 1.1224 else 1.0876,
        atr14AtTrigger = 0.001, source = SignalSource.WAVE_THREE,
        entryType = EntryType.LIMIT, expiryIndex = expiry, cancelPrice = cancel
    )

    private fun bar(o: Double, h: Double, l: Double, c: Double) = CandleEntity(0L, "M15", o, h, l, c, 0.0)
    private fun flat(n: Int) = List(n) { bar(1.0, 1.0, 1.0, 1.0) }

    @Test fun buyLimit_gapBelow_fillsAtOpen() {
        val s = limitSignal(true, 1.1062, 1.1100)
        val c = flat(11) + bar(1.1055, 1.1058, 1.1050, 1.1056)
        val fill = BacktestEngine.resolveLimitFill(c, s)
        assertNotNull(fill)
        assertEquals(11, fill!!.first)
        assertEquals(1.1055, fill.second, eps)
    }

    @Test fun sellLimit_gapAbove_fillsAtOpen() {
        val s = limitSignal(false, 1.1038, 1.1000)
        val c = flat(11) + bar(1.1045, 1.1050, 1.1042, 1.1046)
        val fill = BacktestEngine.resolveLimitFill(c, s)
        assertNotNull(fill)
        assertEquals(1.1045, fill!!.second, eps)
    }

    @Test fun runaway_cancelsOrder() {
        val s = limitSignal(true, 1.1062, 1.1100)
        val c = flat(11) + bar(1.1070, 1.1105, 1.1068, 1.1100) + bar(1.1090, 1.1095, 1.1060, 1.1062)
        assertNull(BacktestEngine.resolveLimitFill(c, s))   // ran past wave 1 first; the later dip is too late
    }

    @Test fun sameBar_openDecidesWhichLevelCameFirst() {
        val s = limitSignal(true, 1.1062, 1.1100)
        val nearLimit = flat(11) + bar(1.1064, 1.1101, 1.1060, 1.1090)
        val nearCancel = flat(11) + bar(1.1096, 1.1101, 1.1060, 1.1070)
        assertNotNull(BacktestEngine.resolveLimitFill(nearLimit, s))
        assertNull(BacktestEngine.resolveLimitFill(nearCancel, s))
    }

    @Test fun order_expires() {
        val s = limitSignal(true, 1.1062, 1.1100, expiry = 20)
        val c = flat(11) + List(30) { bar(1.108, 1.109, 1.1075, 1.108) } + bar(1.1062, 1.1065, 1.1055, 1.1060)
        assertNull(BacktestEngine.resolveLimitFill(c, s))
    }

    // ------------------------------------------------------------------ end to end

    private fun path(points: List<Pair<Int, Double>>, wiggle: Double = 0.0002): List<CandleEntity> {
        val out = ArrayList<CandleEntity>()
        var prev = points[0].second
        var t = 0L
        for (k in 1 until points.size) {
            val n = points[k].first
            val price = points[k].second
            val rising = price > prev
            for (j in 1..n) {
                val pr = prev + (price - prev) * j / n
                val o = if (out.isEmpty()) points[0].second else out.last().close
                var hi = maxOf(o, pr) + wiggle
                var lo = minOf(o, pr) - wiggle
                // The last bar of a swing overshoots, so every swing end is a strict pivot.
                if (j == n) { if (rising) hi = pr + 3 * wiggle else lo = pr - 3 * wiggle }
                out += CandleEntity(t, "M15", o, hi, lo, pr, 0.0)
                t += 900_000L
            }
            prev = price
        }
        return out
    }

    /** Down 40 pips, wave 1 up ~100 pips, wave 2 back to ~38.2%, a retest, then wave 3 up. */
    private fun longScenario(retest: Boolean = true, rallyTo: Double = 1.1250): List<CandleEntity> {
        val pts = ArrayList<Pair<Int, Double>>()
        pts += Pair(0, 1.1040); pts += Pair(10, 1.1000); pts += Pair(12, 1.1100); pts += Pair(10, 1.10618)
        if (retest) { pts += Pair(4, 1.1090); pts += Pair(6, 1.1063) }
        pts += Pair(20, rallyTo)
        return path(pts)
    }

    private fun detect(c: List<CandleEntity>, cfg: WaveThreeConfig = WaveThreeConfig(enabled = true)): List<Signal> {
        val pivotCfg = PivotConfig(enabled = true, strength = 2, prominenceAtr = 0.0)
        val pivots = PivotDetector.detect(c, pivotCfg)
        val atr = DoubleArray(c.size) { 0.001 }
        return WaveThreeStrategy.detect(c, pivots, pivotCfg, atr, SessionConfig.Default, cfg)
    }

    @Test fun endToEnd_long_fillsOnRetest_andHitsPtl() {
        val c = longScenario()
        val sigs = detect(c)
        assertEquals(1, sigs.size)
        val s = sigs[0]
        assertTrue(s.bullish)
        assertEquals(EntryType.LIMIT, s.entryType)
        assertEquals(0.382, s.wave!!.retrace, eps)
        assertEquals(s.wave!!.startPrice, s.stopPrice, eps)                  // stop = start of wave 1
        assertTrue(s.stopPrice < s.triggerPrice && s.triggerPrice < s.wave!!.endPrice)
        assertTrue(s.targetPrice > s.wave!!.endPrice)
        assertEquals(s.wave!!.pullbackIndex!! + 2, s.triggerIndex)           // placed only once wave 2 is confirmed

        val bt = BacktestEngine.run(c, sigs, BacktestConfig.Default)
        assertEquals(1, bt.totalTrades)
        assertEquals(ExitReason.TARGET, bt.trades[0].exitReason)
        assertTrue(bt.trades[0].entryIndex > s.triggerIndex)                 // filled later, on the retest
        assertTrue(bt.trades[0].entryPrice <= s.triggerPrice + eps)
    }

    @Test fun endToEnd_short_isTheMirrorImage() {
        val c = longScenario()
        val k = 2.2
        val m = c.map { it.copy(open = k - it.open, high = k - it.low, low = k - it.high, close = k - it.close) }
        val long = detect(c)[0]
        val sigs = detect(m)
        assertEquals(1, sigs.size)
        val s = sigs[0]
        assertTrue(!s.bullish)
        assertEquals(k - long.triggerPrice, s.triggerPrice, 1e-9)
        assertEquals(k - long.stopPrice, s.stopPrice, 1e-9)
        assertEquals(k - long.targetPrice, s.targetPrice, 1e-9)
        assertTrue(s.stopPrice > s.triggerPrice && s.triggerPrice > s.targetPrice)
        assertEquals(ExitReason.TARGET, BacktestEngine.run(m, sigs, BacktestConfig.Default).trades[0].exitReason)
    }

    @Test fun endToEnd_noRetest_orderIsCancelled_noTrade() {
        val c = longScenario(retest = false, rallyTo = 1.1300)
        val sigs = detect(c)
        assertEquals(0, BacktestEngine.run(c, sigs, BacktestConfig.Default).totalTrades)
    }

    @Test fun disabled_producesNothing() {
        assertTrue(detect(longScenario(), WaveThreeConfig(enabled = false)).isEmpty())
    }
}
