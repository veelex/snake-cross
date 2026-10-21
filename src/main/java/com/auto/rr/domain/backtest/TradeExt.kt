package com.auto.rr.domain.backtest

import com.auto.rr.domain.strategy.Signal

enum class ExitReason {
    /** Fixed stop from the pattern was hit. */
    STOP,
    /** Target price was hit. */
    TARGET,
    /** Trailing stop was hit at a level better than the fixed stop. */
    TRAIL,
    /** Max holding period reached; closed at the bar's close. */
    TIME,
    /** Data ended; closed at the last bar's close. */
    HOLD
}

data class Trade(
    val signal: Signal,
    val entryIndex: Int,
    val exitIndex: Int,
    val entryTime: Long,
    val exitTime: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val qty: Double,
    val grossPnl: Double,
    val fees: Double,
    val slippage: Double,
    val netPnl: Double,
    val riskAmount: Double,
    val rMultiple: Double,
    val exitReason: ExitReason
) {
    val isWin: Boolean get() = netPnl > 0.0
}
