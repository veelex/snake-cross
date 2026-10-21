package com.auto.rr.domain.indicator

/**
 * A confirmed swing high or low.
 *
 * @param index index into the candle list of the pivot bar itself
 * @param price the pivot's high (for [isHigh]) or low (for !isHigh)
 * @param isHigh true for a swing high, false for a swing low
 */
data class PivotPoint(
    val index: Int,
    val price: Double,
    val isHigh: Boolean
)
