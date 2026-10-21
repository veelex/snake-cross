package com.auto.rr.domain.indicator

data class PivotConfig(
    val enabled: Boolean = true,
    /** Bars to the left and right that must be strictly weaker. Classic Williams = 2. */
    val strength: Int = 2,
    /**
     * Minimum swing size beyond the neighbouring high/low, as a multiple of ATR.
     * Filters out micro-pivots that are local extremes but not significant swings.
     * Set to 0 to disable (behaves like the classic fractal detector).
     */
    val prominenceAtr: Double = 0.5
) {
    companion object {
        val Default = PivotConfig()
    }
}
