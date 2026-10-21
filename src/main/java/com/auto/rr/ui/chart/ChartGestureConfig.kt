package com.auto.rr.ui.chart

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ChartGestureConfig(
    /** Width of the price (Y) axis strip on the RIGHT edge. Dragging it vertically rescales price. */
    val priceAxisWidth: Dp = 60.dp,
    /** Height of the time (X) axis strip on the bottom edge. Dragging it horizontally rescales time. */
    val timeAxisHeight: Dp = 28.dp,
    val priceAxisSensitivityPx: Float = 220f,
    val timeAxisSensitivityPx: Float = 200f,
    val pinchSensitivity: Float = 1.0f
) {
    companion object {
        val Default = ChartGestureConfig()
    }
}
