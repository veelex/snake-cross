package com.auto.rr.domain.pattern

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.PivotDetector
import com.auto.rr.domain.indicator.PivotPoint

/** Why a candidate set of six pivots was turned down. Used for the "why so few?" funnel in the UI. */
enum class ElliottReject(val label: String) {
    WIDTH("Wider than the maximum width"),
    SPACING("Two waves too close together"),
    SHAPE("Swings do not trend in one direction"),
    RULE_W2("Wave 2 went past the start of wave 1"),
    RULE_W3("Wave 3 did not pass the end of wave 1"),
    RULE_SHORTEST("Wave 3 was the shortest impulse wave"),
    RULE_OVERLAP("Wave 4 overlapped wave 1"),
    RULE_TRUNCATED("Wave 5 fell short of wave 3"),
    RETRACE("Wave 2 or 4 retraced too deep"),
    SIZE("A wave was too small"),
    TOTAL("Whole move too small"),
    NO_CONFIRM("Never confirmed (expired or invalidated)"),
    FORMING("Still forming (hidden while confirmation is required)")
}

/** Counts of candidate windows and how many each filter removed, in the order the filters run. */
class ElliottStats {
    var windows = 0
    var accepted = 0
    val rejected = IntArray(ElliottReject.values().size)

    internal fun reject(r: ElliottReject) { rejected[r.ordinal]++ }

    /** Non-zero rejection reasons, biggest first. */
    fun topRejections(): List<Pair<ElliottReject, Int>> =
        ElliottReject.values().map { it to rejected[it.ordinal] }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
}

class ElliottResult(val patterns: List<Pattern>, val stats: ElliottStats)

/**
 * Detects completed five-wave Elliott impulses (0-1-2-3-4-5) from swing pivots.
 *
 * The pivot list is treated as a sliding window of six alternating pivots. Working in
 * "impulse direction" space (prices negated for a down-impulse) the rules are:
 *  1. Wave 2 never retraces beyond the start of wave 1.                      (always)
 *  2. Wave 3 ends beyond the end of wave 1.                                  (always)
 *  3. Wave 3 is never the shortest of waves 1, 3 and 5.                      (always)
 *  4. Wave 4 does not overlap wave 1 territory.        (off with [PatternConfig.elliottAllowOverlap])
 *  5. Wave 5 ends beyond the end of wave 3.            (off with [PatternConfig.elliottAllowTruncated])
 * Softer, configurable limits cover the wave 2 / wave 4 retracement depth and minimum wave sizes.
 *
 * A finished impulse marks the exhaustion of a trend, so the emitted [Pattern] is a
 * *reversal*: [Pattern.bullish] is the expected trade direction (true after a down-impulse).
 * It maps onto the existing neckline machinery:
 *  - neckline = confirmation level: [PatternConfig.elliottConfirmRetrace] of wave 5 given back
 *    (1.0 = the end of wave 4)
 *  - invalidation = a close beyond the end of wave 5 (the impulse is still running)
 *
 * Pivot layout of the returned pattern: [p0, p1, p2, p3, p4, p5] (6 pivots).
 */
object ElliottWaveDetector {

    fun detect(
        candles: List<CandleEntity>,
        pivots: List<PivotPoint>,
        cfg: PatternConfig,
        atr: DoubleArray = DoubleArray(0),
        pivotStrength: Int = 2,
        rsi: DoubleArray = DoubleArray(0)
    ): List<Pattern> = detectWithStats(candles, pivots, cfg, atr, pivotStrength, rsi).patterns

    /**
     * Scans one or several pivot sizes ("wave degrees") and merges the results.
     *
     * The base size is [PatternConfig.elliottPivotStrength] (or the Swing pivots strength when 0);
     * with [PatternConfig.elliottDegrees] > 1 it also tries 2x and 4x that size, so both small and
     * large impulses are found. [basePivots] are the chart's pivots, reused when the size matches.
     * Identical impulses found at several sizes are kept once; stats are summed over all sizes.
     */
    fun detectScaled(
        candles: List<CandleEntity>,
        basePivots: List<PivotPoint>,
        cfg: PatternConfig,
        atr: DoubleArray,
        pivotConfig: PivotConfig,
        rsi: DoubleArray = DoubleArray(0)
    ): ElliottResult {
        val base = if (cfg.elliottPivotStrength > 0) cfg.elliottPivotStrength else pivotConfig.strength
        val sizes = (0 until cfg.elliottDegrees.coerceIn(1, 3)).map { (base.coerceAtLeast(1)) shl it }

        val stats = ElliottStats()
        val seen = HashSet<List<Int>>()
        val out = ArrayList<Pattern>()
        for (size in sizes) {
            val pv = if (size == pivotConfig.strength) basePivots
                     else PivotDetector.detect(candles, pivotConfig.copy(strength = size), atr)
            val r = detectWithStats(candles, pv, cfg, atr, size, rsi)
            stats.windows += r.stats.windows
            for (i in r.stats.rejected.indices) stats.rejected[i] += r.stats.rejected[i]
            for (p in r.patterns) if (seen.add(p.pivotIndices)) { out += p; stats.accepted++ }
        }
        return ElliottResult(out.sortedBy { it.leftIndex }, stats)
    }

    fun detectWithStats(
        candles: List<CandleEntity>,
        pivots: List<PivotPoint>,
        cfg: PatternConfig,
        atr: DoubleArray = DoubleArray(0),
        pivotStrength: Int = 2,
        rsi: DoubleArray = DoubleArray(0)
    ): ElliottResult {
        val stats = ElliottStats()
        if (candles.isEmpty()) return ElliottResult(emptyList(), stats)
        val pv = PivotNormalizer.alternate(pivots)
        if (pv.size < 6) return ElliottResult(emptyList(), stats)

        // Elliott has its own confirmation window / invalidation switch; resolve() reads them from cfg.
        val resolveCfg = cfg.copy(
            confirmWithinBars = cfg.elliottConfirmWithinBars,
            invalidateOnLegBreak = cfg.elliottInvalidateOnBreak
        )
        val retrace = cfg.elliottConfirmRetrace.coerceIn(0.05, 1.0)

        val out = ArrayList<Pattern>()
        for (k in 0..pv.size - 6) {
            stats.windows++
            val p = pv.subList(k, k + 6)

            // Spacing and total width
            if (p[5].index - p[0].index > cfg.elliottMaxPatternBars) { stats.reject(ElliottReject.WIDTH); continue }
            var spacedOk = true
            for (i in 0 until 5) {
                if (p[i + 1].index - p[i].index < cfg.elliottMinBarsPerWave) { spacedOk = false; break }
            }
            if (!spacedOk) { stats.reject(ElliottReject.SPACING); continue }

            // Impulse direction: starts at a low -> up-impulse, starts at a high -> down-impulse.
            val up = !p[0].isHigh
            val x = DoubleArray(6) { if (up) p[it].price else -p[it].price }

            val w1 = x[1] - x[0]
            val w2 = x[1] - x[2]
            val w3 = x[3] - x[2]
            val w4 = x[3] - x[4]
            val w5 = x[5] - x[4]
            if (w1 <= 0.0 || w2 <= 0.0 || w3 <= 0.0 || w4 <= 0.0 || w5 <= 0.0) { stats.reject(ElliottReject.SHAPE); continue }

            if (x[2] <= x[0]) { stats.reject(ElliottReject.RULE_W2); continue }
            if (x[3] <= x[1]) { stats.reject(ElliottReject.RULE_W3); continue }
            if (w3 < w1 && w3 < w5) { stats.reject(ElliottReject.RULE_SHORTEST); continue }
            if (!cfg.elliottAllowOverlap && x[4] <= x[1]) { stats.reject(ElliottReject.RULE_OVERLAP); continue }
            if (!cfg.elliottAllowTruncated && x[5] <= x[3]) { stats.reject(ElliottReject.RULE_TRUNCATED); continue }

            val r2 = w2 / w1
            val r4 = w4 / w3
            if (r2 > cfg.elliottMaxWave2Retrace || r4 >= 1.0 || r4 > cfg.elliottMaxWave4Retrace) {
                stats.reject(ElliottReject.RETRACE); continue
            }

            // Minimum wave size: every wave must be a real swing, not noise.
            // Waves 1, 3, 5 are impulse waves; waves 2 and 4 are corrections and may be smaller.
            val waves = doubleArrayOf(w1, w2, w3, w4, w5)
            var sizeOk = true
            for (i in 0 until 5) {
                val end = p[i + 1]
                val a = atr.getOrNull(end.index) ?: Double.NaN
                val minAtr = if (i % 2 == 0) cfg.elliottMinWaveAtr else cfg.elliottMinCorrectionAtr
                val minSize = if (a.isFinite() && a > 0.0) minAtr * a
                              else cfg.minNeckDepthFraction * kotlin.math.abs(end.price)
                if (waves[i] < minSize) { sizeOk = false; break }
            }
            if (!sizeOk) { stats.reject(ElliottReject.SIZE); continue }

            val totalMove = x[5] - x[0]
            if (totalMove * 10_000.0 < cfg.elliottMinTotalPips) { stats.reject(ElliottReject.TOTAL); continue }

            // Reversal trade is against the impulse.
            val bullish = !up
            // Confirmation level: give back `retrace` of wave 5 (1.0 lands exactly on the end of wave 4).
            val neckline = p[5].price + (p[4].price - p[5].price) * retrace
            val legExtreme = p[5].price
            val atr5 = atr.getOrNull(p[5].index) ?: Double.NaN
            val hasAtr = atr5.isFinite() && atr5 > 0.0
            val invalidBuf = if (hasAtr) cfg.elliottInvalidationAtr * atr5 else cfg.invalidationFraction * legExtreme
            val overshootBuf = if (hasAtr) cfg.elliottConfirmOvershootAtr * atr5 else cfg.confirmOvershootFraction * legExtreme

            val res = DoublePatternDetector.resolve(
                candles, p[5].index, pivotStrength, neckline, bullish, legExtreme,
                invalidBuf, overshootBuf, resolveCfg
            )
            if (res == null) { stats.reject(ElliottReject.NO_CONFIRM); continue }
            if (cfg.elliottRequireBreak && res.confirmation == null) { stats.reject(ElliottReject.FORMING); continue }

            stats.accepted++
            out += Pattern(
                type = if (up) PatternType.ELLIOTT_IMPULSE_UP else PatternType.ELLIOTT_IMPULSE_DOWN,
                bullish = bullish,
                pivotIndices = p.map { it.index },
                pivotPrices = p.map { it.price },
                neckline = neckline,
                confirmationIndex = res.confirmation,
                quality = quality(
                    r2, w3 / w1, r4,
                    DoublePatternDetector.rsiDivergence(rsi, p[3].index, p[5].index, bullish)
                )
            )
        }
        return ElliottResult(out, stats)
    }

    /**
     * 0..1 score of how textbook the impulse looks (guidelines, not rules):
     * wave 2 retraces ~50-62% of wave 1, wave 3 extends towards 1.618x wave 1,
     * wave 4 retraces ~38% of wave 3, and RSI diverges between waves 3 and 5.
     */
    internal fun quality(r2: Double, w3ToW1: Double, r4: Double, divergence: Double): Double {
        val s2 = (1.0 - kotlin.math.abs(r2 - 0.55) / 0.55).coerceIn(0.0, 1.0)
        val s3 = (w3ToW1 / 1.618).coerceIn(0.0, 1.0)
        val s4 = (1.0 - kotlin.math.abs(r4 - 0.382) / 0.382).coerceIn(0.0, 1.0)
        val sd = if (divergence.isNaN()) 0.5 else (divergence / 10.0).coerceIn(0.0, 1.0)
        return 0.25 * s2 + 0.30 * s3 + 0.25 * s4 + 0.20 * sd
    }
}
