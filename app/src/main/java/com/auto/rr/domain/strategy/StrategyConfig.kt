package com.auto.rr.domain.strategy

data class StrategyConfig(
    val enabled: Boolean = true,
    /** How many bars after pattern confirmation we look for EMA alignment. */
    val crossWindow: Int = 5,
    /** Max EMA1-EMA55 spread, as a fraction of EMA55, for the cross to count as "snake". */
    val convergenceFraction: Double = 0.005,
    /** ATR period for stop sizing. */
    val atrPeriod: Int = 14,
    /** Stop = pattern extreme +/- stopAtrMultiplier * ATR. */
    val stopAtrMultiplier: Double = 1.0,
    /** Target = entry +/- targetRMultiple * risk. */
    val targetRMultiple: Double = 2.0
) {
    companion object {
        val Default = StrategyConfig()
    }
}
