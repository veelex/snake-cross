package com.auto.rr.domain.indicator

/**
 * "EMA sweep": price closes through all four slower EMAs (5, 8, 13, 55) at (almost) the same
 * time, close to a double/triple top or bottom.
 */
data class EmaSweepConfig(
    /** Master switch: detect sweeps and draw them on the chart. */
    val enabled: Boolean = true,
    /** Also turn each sweep into a backtest entry. */
    val tradeSweeps: Boolean = true,
    /**
     * "At the same time" window. All four EMAs must have been crossed within this many bars,
     * ending on the trigger bar. 1 means a single candle must cut through all four.
     */
    val sweepBars: Int = 3,
    /** How close to the pattern, in ATR, price must be (to the neckline or the last leg). */
    val nearAtr: Double = 2.0,
    /** How many bars after the pattern completes a sweep still counts as "near". */
    val nearBars: Int = 10
) {
    companion object {
        val Default = EmaSweepConfig()
        /** EMA periods that price has to cross. EMA 1 is price itself, so it is not included. */
        val CROSS_PERIODS = listOf(5, 8, 13, 55)
    }
}
