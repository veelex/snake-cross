package com.auto.rr.domain.pattern

/**
 * A reversal pattern defined by an ordered list of swing pivots.
 *
 * Double top/bottom: 3 pivots  [leg1, neck, leg2]
 * Triple top/bottom: 5 pivots  [leg1, neck1, leg2, neck2, leg3]
 * Elliott impulse:    6 pivots  [w0, w1, w2, w3, w4, w5]  (neckline = end of wave 4)
 *
 * The pivot list alternates high/low. For doubles/triples it starts with a leg (leg/neck/leg/...).
 *
 * @param pivotIndices index in the candle list for each pivot (left -> right)
 * @param pivotPrices price at each pivot, same length as [pivotIndices]
 * @param neckline trigger level: highest neck for bullish, lowest for bearish
 * @param confirmationIndex first bar after the last leg that closed beyond [neckline], or null
 * @param quality 0..1 score (leg symmetry + neck depth); used to resolve overlaps
 */
data class Pattern(
    val type: PatternType,
    val bullish: Boolean,
    val pivotIndices: List<Int>,
    val pivotPrices: List<Double>,
    val neckline: Double,
    val confirmationIndex: Int?,
    val quality: Double = 0.0
) {
    val isTriple: Boolean get() = type == PatternType.TRIPLE_TOP || type == PatternType.TRIPLE_BOTTOM
    val isElliott: Boolean get() = type == PatternType.ELLIOTT_IMPULSE_UP || type == PatternType.ELLIOTT_IMPULSE_DOWN
    val leftIndex: Int get() = pivotIndices.first()
    val rightIndex: Int get() = pivotIndices.last()
    val isConfirmed: Boolean get() = confirmationIndex != null
}
