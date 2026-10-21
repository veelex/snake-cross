package com.auto.rr.domain.strategy

import com.auto.rr.domain.pattern.PatternType

enum class SignalSource { SNAKE_CROSS, EMA_SWEEP, WAVE_THREE }

/** How a [Signal] gets into the market. */
enum class EntryType {
    /** Enter at the open of the bar after [Signal.triggerIndex]. All the original strategies. */
    MARKET,

    /**
     * A resting limit order at [Signal.triggerPrice], placed at the close of [Signal.triggerIndex].
     * It fills the first time price touches the level, and is dropped when it expires or when
     * price reaches [Signal.cancelPrice] first.
     */
    LIMIT
}

/**
 * Wave anchors behind a wave-3 entry, kept so the chart can draw them.
 *
 * @param startIndex / startPrice  start of wave 1 (the stop level)
 * @param endIndex / endPrice      end of wave 1 (the extreme that price must not run past)
 * @param pullbackIndex / pullbackPrice  end of wave 2, or null while only wave 1 is known
 * @param retrace the Fibonacci ratio the limit price was built from (0.382 or 0.618)
 * @param waveSize wave 1 length in price (the "1.0" unit of the notes)
 */
data class WaveSetup(
    val startIndex: Int,
    val startPrice: Double,
    val endIndex: Int,
    val endPrice: Double,
    val pullbackIndex: Int?,
    val pullbackPrice: Double?,
    val retrace: Double,
    val waveSize: Double
)

/**
 * An actionable buy/sell trigger.
 *
 * @param bullish true for a buy, false for a sell
 * @param patternType the pattern that preceded the signal
 * @param patternIndex bar index where the pattern was confirmed
 * @param triggerIndex bar index where the EMA alignment fired (for [EntryType.LIMIT]: the bar at
 *        whose close the order is placed)
 * @param triggerPrice candle close at [triggerIndex] (for [EntryType.LIMIT]: the limit price)
 */
data class Signal(
    val bullish: Boolean,
    val patternType: PatternType,
    val patternIndex: Int,
    val triggerIndex: Int,
    val triggerPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val atr14AtTrigger: Double,
    /** Which strategy produced this signal. */
    val source: SignalSource = SignalSource.SNAKE_CROSS,
    /** Market at the next open, or a resting limit order. */
    val entryType: EntryType = EntryType.MARKET,
    /** LIMIT only: last bar index at which the order may still fill. -1 = until the data ends. */
    val expiryIndex: Int = -1,
    /** LIMIT only: the order is dropped if price trades through this level before it fills. NaN = never. */
    val cancelPrice: Double = Double.NaN,
    /** Wave anchors for chart drawing; set by [WaveThreeStrategy]. */
    val wave: WaveSetup? = null
) {
    val riskPerUnit: Double get() = triggerPrice - stopPrice

    val rMultiple: Double
        get() = if (riskPerUnit > 0.0) (targetPrice - triggerPrice) / riskPerUnit else 0.0
}
