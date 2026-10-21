package com.auto.rr.domain.pattern

import com.auto.rr.domain.model.Timeframe

/** One-tap sensitivity levels for Elliott wave detection. */
enum class ElliottPreset(val label: String) {
    STRICT("Strict"),
    BALANCED("Balanced"),
    LOOSE("Loose"),
    CUSTOM("Custom")
}

data class PatternConfig(
    val enabled: Boolean = true,
    val showDoubles: Boolean = true,
    val showTriples: Boolean = true,
    /** Max distance between legs, in ATR multiples (volatility-aware). */
    val legToleranceAtr: Double = 0.6,
    /** Fallback leg tolerance (fraction of price) while ATR is not yet available. */
    val legToleranceFraction: Double = 0.002,
    /** Neckline must stand at least this many ATR beyond the legs. */
    val minNeckDepthAtr: Double = 1.5,
    /** Fallback neck depth (fraction of price) while ATR is not yet available. */
    val minNeckDepthFraction: Double = 0.0015,
    /** Minimum bars between consecutive legs. */
    val minBarsBetweenPivots: Int = 8,
    /** Maximum bars from first to last leg. */
    val maxPatternBars: Int = 150,
    /** Bars (after the last leg is known) in which a neckline close is still accepted. */
    val confirmWithinBars: Int = 40,
    /** Only emit patterns that have closed beyond the neckline. */
    val requireNecklineBreak: Boolean = true,
    /** Drop a pattern if price closes beyond the legs (wrong side) before the neckline breaks. */
    val invalidateOnLegBreak: Boolean = true,
    /** Invalidation buffer: how far price must close past the leg extreme to kill the pattern. */
    val invalidationAtr: Double = 0.8,
    /** Fallback invalidation buffer (fraction of price) while ATR is not yet available. */
    val invalidationFraction: Double = 0.002,

    // --- Trend context (Step 2) ---
    /** Require a preceding counter-move before leg1 to consider this a reversal, not range-bound noise. */
    val requireTrendContext: Boolean = true,
    /** Bars before leg1 used to measure the preceding move. */
    val trendLookbackBars: Int = 30,
    /** Minimum preceding move, as a multiple of ATR, to satisfy the trend filter. */
    val trendMinAtr: Double = 2.0,

    // --- Absolute size floor (Step 6) ---
    /** Minimum neck-to-leg height in pips (EUR/USD). Below this a pattern is noise regardless of ATR gates. */
    val minPatternHeightPips: Double = 8.0,

    // --- Confirmation quality (Step 7) ---
    /** Confirmation close must clear the neckline by at least this many ATRs, not just a tick. */
    val confirmOvershootAtr: Double = 0.10,
    /** Fallback overshoot (fraction of price) while ATR is not yet available. */
    val confirmOvershootFraction: Double = 0.0002,

    // --- Triple-pattern quality (Step 8) ---
    /** Max difference between the two intermediate peaks of a triple, as a multiple of ATR. */
    val neckToleranceAtr: Double = 0.8,
    /** For triples: require the middle leg to be the extreme (lowest for a bottom, highest for a top). */
    val requireMiddleLegExtreme: Boolean = true,

    // --- Elliott wave (5-wave impulse) ---
    /** Detect completed 5-wave impulses and flag the expected reversal. */
    val showElliott: Boolean = true,
    /** Let confirmed Elliott impulses trigger buy/sell signals. Off: they are drawn on the chart only. */
    val elliottInStrategy: Boolean = false,
    /** Every wave must be at least this many ATRs long. Filters out noisy micro-waves. */
    val elliottMinWaveAtr: Double = 1.0,
    /** Wave 2 may retrace at most this fraction of wave 1 (hard rule is < 1.0). */
    val elliottMaxWave2Retrace: Double = 0.90,
    /** Wave 4 may retrace at most this fraction of wave 3 (no-overlap rule applies on top). */
    val elliottMaxWave4Retrace: Double = 0.618,
    /** Minimum bars between consecutive wave pivots. */
    val elliottMinBarsPerWave: Int = 3,
    /** Maximum bars from the start of wave 1 to the end of wave 5. */
    val elliottMaxPatternBars: Int = 300,
    /** Waves 2 and 4 (the corrections) must be at least this many ATRs long. Often set lower than the impulse waves. */
    val elliottMinCorrectionAtr: Double = 1.0,
    /** Whole move (start of wave 1 to end of wave 5) must be at least this many pips (EUR/USD scale). */
    val elliottMinTotalPips: Double = 8.0,
    /** Accept a "truncated" 5th wave that fails to pass the end of wave 3. */
    val elliottAllowTruncated: Boolean = false,
    /** Accept wave 4 overlapping the price territory of wave 1 (diagonals / corrective-looking moves). */
    val elliottAllowOverlap: Boolean = false,
    /**
     * Confirmation level as a share of wave 5 that price must give back. 1.0 = the whole of wave 5,
     * i.e. a close back through the end of wave 4 (strictest). 0.382 = only a 38.2% retrace.
     */
    val elliottConfirmRetrace: Double = 1.0,
    /** Only show impulses that have closed through the confirmation level. Off: also show forming ones (dimmed). */
    val elliottRequireBreak: Boolean = true,
    /** Bars after wave 5 in which a close through the confirmation level still confirms. */
    val elliottConfirmWithinBars: Int = 40,
    /** The confirming close must clear the level by at least this many ATRs. */
    val elliottConfirmOvershootAtr: Double = 0.10,
    /** Drop the impulse if price closes beyond the end of wave 5 before it is confirmed (the trend is still running). */
    val elliottInvalidateOnBreak: Boolean = true,
    /** How far beyond the end of wave 5 a close must go to drop the impulse, in ATR. */
    val elliottInvalidationAtr: Double = 0.8,
    /** Let Elliott impulses share swing points with double / triple patterns instead of competing with them. */
    val elliottOverlapOthers: Boolean = false,
    /**
     * Pivot size (bars each side) used to find wave ends. 0 = same as the Swing pivots setting.
     * Bigger means bigger, slower waves; the default pivot size mostly finds micro-swings.
     */
    val elliottPivotStrength: Int = 0,
    /** How many pivot sizes to scan: 1 = just the base size, 2 = also double it, 3 = also quadruple it. */
    val elliottDegrees: Int = 1
) {
    /** Applies one of the sensitivity presets to the Elliott options only. */
    fun withElliottPreset(preset: ElliottPreset, tf: Timeframe): PatternConfig {
        val d = defaultsFor(tf)
        return when (preset) {
            ElliottPreset.STRICT, ElliottPreset.CUSTOM -> copy(
                elliottMinWaveAtr = d.elliottMinWaveAtr,
                elliottMinCorrectionAtr = d.elliottMinCorrectionAtr,
                elliottMaxWave2Retrace = d.elliottMaxWave2Retrace,
                elliottMaxWave4Retrace = d.elliottMaxWave4Retrace,
                elliottMinBarsPerWave = d.elliottMinBarsPerWave,
                elliottMaxPatternBars = d.elliottMaxPatternBars,
                elliottMinTotalPips = d.elliottMinTotalPips,
                elliottAllowTruncated = d.elliottAllowTruncated,
                elliottAllowOverlap = d.elliottAllowOverlap,
                elliottConfirmRetrace = d.elliottConfirmRetrace,
                elliottRequireBreak = d.elliottRequireBreak,
                elliottConfirmWithinBars = d.elliottConfirmWithinBars,
                elliottConfirmOvershootAtr = d.elliottConfirmOvershootAtr,
                elliottInvalidateOnBreak = d.elliottInvalidateOnBreak,
                elliottInvalidationAtr = d.elliottInvalidationAtr,
                elliottOverlapOthers = d.elliottOverlapOthers,
                elliottPivotStrength = d.elliottPivotStrength,
                elliottDegrees = d.elliottDegrees
            )
            ElliottPreset.BALANCED -> withElliottPreset(ElliottPreset.STRICT, tf).copy(
                elliottMinWaveAtr = 0.7,
                elliottMinCorrectionAtr = 0.5,
                elliottMaxWave4Retrace = 0.80,
                elliottMinTotalPips = d.elliottMinTotalPips * 0.75,
                elliottAllowTruncated = true,
                elliottConfirmRetrace = 0.618,
                elliottConfirmWithinBars = d.elliottConfirmWithinBars * 3 / 2,
                elliottConfirmOvershootAtr = 0.05,
                elliottInvalidationAtr = 1.2,
                elliottDegrees = 2
            )
            ElliottPreset.LOOSE -> withElliottPreset(ElliottPreset.STRICT, tf).copy(
                elliottMinWaveAtr = 0.4,
                elliottMinCorrectionAtr = 0.25,
                elliottMaxWave2Retrace = 0.95,
                elliottMaxWave4Retrace = 0.95,
                elliottMinBarsPerWave = 2,
                elliottMinTotalPips = d.elliottMinTotalPips * 0.5,
                elliottAllowTruncated = true,
                elliottAllowOverlap = true,
                elliottConfirmRetrace = 0.382,
                elliottConfirmWithinBars = d.elliottConfirmWithinBars * 2,
                elliottConfirmOvershootAtr = 0.0,
                elliottInvalidationAtr = 1.5,
                elliottOverlapOthers = true,
                elliottDegrees = 3
            )
        }
    }

    /** Which preset the current Elliott options match exactly, or CUSTOM. */
    fun elliottPreset(tf: Timeframe): ElliottPreset =
        ElliottPreset.values().firstOrNull { it != ElliottPreset.CUSTOM && withElliottPreset(it, tf) == this }
            ?: ElliottPreset.CUSTOM

    companion object {
        val Default = PatternConfig()

        /**
         * Timeframe-scaled defaults. Bar-count windows are the only fields that
         * differ across timeframes; ATR-relative tolerances are timeframe-independent.
         */
        fun defaultsFor(tf: Timeframe): PatternConfig = when (tf) {
            Timeframe.M1  -> PatternConfig(minBarsBetweenPivots = 15, maxPatternBars = 240, elliottMaxPatternBars = 480, confirmWithinBars = 80, minPatternHeightPips =  4.0, elliottConfirmWithinBars = 80, elliottMinTotalPips = 4.0)
            Timeframe.M5  -> PatternConfig(minBarsBetweenPivots = 10, maxPatternBars = 200, elliottMaxPatternBars = 400, confirmWithinBars = 60, minPatternHeightPips =  6.0, elliottConfirmWithinBars = 60, elliottMinTotalPips = 6.0)
            Timeframe.M15 -> PatternConfig(minBarsBetweenPivots =  8, maxPatternBars = 150, elliottMaxPatternBars = 300, confirmWithinBars = 40, minPatternHeightPips =  8.0, elliottConfirmWithinBars = 40, elliottMinTotalPips = 8.0)
            Timeframe.H1  -> PatternConfig(minBarsBetweenPivots =  6, maxPatternBars = 120, elliottMaxPatternBars = 240, confirmWithinBars = 30, minPatternHeightPips = 12.0, elliottConfirmWithinBars = 30, elliottMinTotalPips = 12.0)
            Timeframe.H4  -> PatternConfig(minBarsBetweenPivots =  5, maxPatternBars =  90, elliottMaxPatternBars = 180, confirmWithinBars = 20, minPatternHeightPips = 20.0, elliottConfirmWithinBars = 20, elliottMinTotalPips = 20.0)
            Timeframe.D1  -> PatternConfig(minBarsBetweenPivots =  4, maxPatternBars =  60, elliottMaxPatternBars = 120, confirmWithinBars = 15, minPatternHeightPips = 40.0, elliottConfirmWithinBars = 15, elliottMinTotalPips = 40.0)
        }
    }
}
