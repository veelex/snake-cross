package com.auto.rr.domain.strategy

/** When the resting entry order is placed. */
enum class WaveEntryTiming(val label: String) {
    /** Wait until wave 2 has formed and is confirmed, then rest a limit at the retrace level (a retest entry). */
    AFTER_WAVE2("After wave 2"),

    /** Rest the limit as soon as wave 1 is confirmed, so it fills as wave 2 pulls back (buys into wave C). */
    ANTICIPATE("After wave 1")
}

/** Which wave-2 retracement of wave 1 is traded. */
enum class WaveRetrace(val label: String, val ratio: Double) {
    /** Use whichever of 38.2% / 61.8% the finished wave 2 is closest to. Needs [WaveEntryTiming.AFTER_WAVE2]. */
    AUTO("Auto", 0.0),
    FIB_382("38.2%", WaveThreeConfig.FIB_382),
    FIB_618("61.8%", WaveThreeConfig.FIB_618)
}

/**
 * Elliott "wave 3" entry: wave 1 and wave 2 are in, so a limit order is rested at the end of wave 2
 * to ride wave 3.
 *
 * With wave 1 running from [p0] (start) to [p1] (end), and delta = |p1 - p0| as the unit:
 *  - limit  = p1 -/+ retrace * delta   (buy after an up wave 1, sell after a down wave 1)
 *  - stop   = p0 (start of wave 1)
 *  - target = limit +/- [targetExtension] * delta   (the "PTL", top of wave 3)
 */
data class WaveThreeConfig(
    /** Master switch: detect setups and draw them on the chart. */
    val enabled: Boolean = false,
    /** Also turn each setup into a backtest trade. Off keeps them as chart drawings only. */
    val tradeSetups: Boolean = true,
    val timing: WaveEntryTiming = WaveEntryTiming.AFTER_WAVE2,
    val retrace: WaveRetrace = WaveRetrace.AUTO,
    /** How far the actual wave 2 may sit from 38.2% / 61.8% (as a share of wave 1) and still count. */
    val retraceTolerance: Double = 0.07,
    /** Wave 3 target as a multiple of wave 1, measured from the limit price. 1.618 is the textbook wave 3. */
    val targetExtension: Double = 1.618,
    /** Extra room beyond the start of wave 1 for the stop, in ATR. 0 puts the stop exactly on the start of wave 1. */
    val stopBufferAtr: Double = 0.0,
    /** Wave 1 must be at least this many ATR long. Filters out noise swings. */
    val minWave1Atr: Double = 2.0,
    /** Minimum bars between consecutive wave points. */
    val minBarsPerWave: Int = 3,
    /** Wave 1 must break the previous swing in its direction, i.e. it starts a new move rather than a range swing. */
    val requireStructureBreak: Boolean = true,
    /** Pivot size (bars each side) for the wave points. 0 = same as the Swing pivots setting. */
    val pivotStrength: Int = 0,
    /** Bars after the order is placed during which it may still fill. */
    val expiryBars: Int = 30,
    /** Drop the order if price runs past the end of wave 1 before it fills (wave 3 left without us). */
    val cancelOnRunaway: Boolean = true
) {
    companion object {
        const val FIB_382 = 0.382
        const val FIB_618 = 0.618
        val Default = WaveThreeConfig()
    }
}
