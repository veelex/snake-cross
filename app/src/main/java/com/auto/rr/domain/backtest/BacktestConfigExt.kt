package com.auto.rr.domain.backtest

data class BacktestConfig(
    val initialEquity: Double = 10_000.0,
    /** Fraction of equity risked per trade, e.g. 0.01 = 1%. */
    val riskFraction: Double = 0.01,
    /** Per-side fee as a fraction of notional, e.g. 0.0004 = 4 bps. */
    val feeFraction: Double = 0.0004,
    /** Per-side slippage as a fraction of notional. */
    val slippageFraction: Double = 0.0002,
    /** Trailing stop distance as a multiple of ATR at the trigger bar. 0 disables trailing. */
    val trailingAtrMultiplier: Double = 0.0,
    /** Force-close a trade after this many bars if no stop/target hit. 0 disables the limit. */
    val maxHoldingBars: Int = 0
) {
    companion object {
        val Default = BacktestConfig()
    }
}
