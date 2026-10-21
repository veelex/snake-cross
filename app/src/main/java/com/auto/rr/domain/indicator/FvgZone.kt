package com.auto.rr.domain.indicator

/**
 * A Fair Value Gap: the untraded range between candle 1's high/low and
 * candle 3's low/high in a three-candle pattern.
 *
 * @param bullish true for a bullish (bullish direction) gap
 * @param startIndex index of the third candle of the pattern (where the gap is confirmed)
 * @param upperBound upper price of the zone
 * @param lowerBound lower price of the zone
 * @param mitigationIndex index of the first bar that fully crossed the zone, or null if still live
 */
data class FvgZone(
    val bullish: Boolean,
    val startIndex: Int,
    val upperBound: Double,
    val lowerBound: Double,
    val mitigationIndex: Int?
)
