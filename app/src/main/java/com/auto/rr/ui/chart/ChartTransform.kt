package com.auto.rr.ui.chart

import androidx.compose.runtime.Immutable
import com.auto.rr.data.local.CandleEntity

@Immutable
data class ChartTransform(
    val xStart: Float,
    val xScale: Float,
    val yMin: Double,
    val yMax: Double
) {

    fun screenXFor(index: Float): Float = (index - xStart) * xScale

    fun candleIndexFor(screenX: Float): Float = screenX / xScale + xStart

    fun screenYFor(price: Double, viewHeight: Float): Float {
        val span = yMax - yMin
        if (span <= 0.0) return viewHeight / 2f
        return (viewHeight * (yMax - price) / span).toFloat()
    }

    fun priceFor(screenY: Float, viewHeight: Float): Double {
        if (viewHeight <= 0f) return yMax
        return yMax - (screenY.toDouble() / viewHeight.toDouble()) * (yMax - yMin)
    }

    fun visibleIndexRange(viewWidth: Float): Pair<Float, Float> {
        val first = xStart
        val last = xStart + viewWidth / xScale
        return first to last
    }

    fun panByPixels(dxPx: Float): ChartTransform =
        copy(xStart = xStart - dxPx / xScale)

    fun panPriceByPixels(dyPx: Float, viewHeight: Float): ChartTransform {
        val span = yMax - yMin
        if (viewHeight <= 0f) return this
        val delta = dyPx.toDouble() * span / viewHeight.toDouble()
        return copy(yMin = yMin + delta, yMax = yMax + delta)
    }

    fun scaleTimeBy(factor: Float, pivotScreenX: Float): ChartTransform {
        if (factor <= 0f) return this
        val pivotIndex = candleIndexFor(pivotScreenX)
        val newScale = (xScale * factor).coerceIn(MIN_X_SCALE, MAX_X_SCALE)
        val newStart = pivotIndex - pivotScreenX / newScale
        return copy(xStart = newStart, xScale = newScale)
    }

    fun scalePriceBy(factor: Float, pivotScreenY: Float, viewHeight: Float): ChartTransform {
        if (factor <= 0f || viewHeight <= 0f) return this
        val pivotPrice = priceFor(pivotScreenY, viewHeight)
        val ratio = (pivotScreenY / viewHeight).toDouble()
        val newSpan = (yMax - yMin) / factor.toDouble()
        val newMax = pivotPrice + newSpan * ratio
        val newMin = newMax - newSpan
        return copy(yMin = newMin, yMax = newMax)
    }

    fun withPriceRange(newMin: Double, newMax: Double): ChartTransform =
        copy(yMin = newMin, yMax = newMax)

    companion object {
        const val MIN_X_SCALE = 0.5f
        const val MAX_X_SCALE = 80f
        const val DEFAULT_X_SCALE = 8f
        const val DEFAULT_VISIBLE_CANDLES = 120
    }
}
