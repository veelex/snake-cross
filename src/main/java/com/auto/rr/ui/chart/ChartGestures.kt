package com.auto.rr.ui.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.exp

private enum class Zone { PriceAxis, TimeAxis, Corner, Body }

fun Modifier.chartGestures(
    state: ChartGestureState,
    config: ChartGestureConfig = ChartGestureConfig.Default,
    onCornerTap: () -> Unit = {},
    /** Double-tap on the price axis: reset the price scale to normal. */
    onResetPrice: () -> Unit = {},
    /** Double-tap on the time axis: reset the time scale to normal. */
    onResetTime: () -> Unit = {},
    /** Single-finger tap on the plot area (no drag, no pinch), in canvas pixels. */
    onBodyTap: (Offset) -> Unit = {}
): Modifier = composed {
    val bodyTap by rememberUpdatedState(onBodyTap)
    val cornerTap by rememberUpdatedState(onCornerTap)
    val resetPrice by rememberUpdatedState(onResetPrice)
    val resetTime by rememberUpdatedState(onResetTime)
    val density = LocalDensity.current
    val priceAxisPx = with(density) { config.priceAxisWidth.toPx() }
    val timeAxisPx = with(density) { config.timeAxisHeight.toPx() }
    val priceSensitivity = config.priceAxisSensitivityPx
    val timeSensitivity = config.timeAxisSensitivityPx
    val pinchSensitivity = config.pinchSensitivity

    pointerInput(priceAxisPx, timeAxisPx) {
        // Persist across gestures so two quick taps on the same axis count as a double tap.
        var lastTapZone: Zone? = null
        var lastTapTime = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val sizeH = size.height.toFloat()
            val sizeW = size.width.toFloat()
            val slop = viewConfiguration.touchSlop
            val zone = classifyZone(down.position, sizeW, sizeH, priceAxisPx, timeAxisPx)
            val tapped = when (zone) {
                Zone.PriceAxis -> handlePriceAxis(state, sizeH, priceSensitivity, down.position, slop)
                Zone.TimeAxis -> handleTimeAxis(state, sizeW, timeSensitivity, down.position, slop)
                Zone.Corner -> { handleCorner(down.position, slop) { cornerTap() }; false }
                Zone.Body -> { handleBody(state, pinchSensitivity, down.position, slop) { bodyTap(it) }; false }
            }
            if (tapped) {
                val now = SystemClock.uptimeMillis()
                if (lastTapZone == zone && now - lastTapTime <= viewConfiguration.doubleTapTimeoutMillis) {
                    lastTapZone = null
                    if (zone == Zone.PriceAxis) resetPrice() else resetTime()
                } else {
                    lastTapZone = zone
                    lastTapTime = now
                }
            } else {
                lastTapZone = null
            }
        }
    }
}

private fun classifyZone(
    p: Offset,
    widthPx: Float,
    heightPx: Float,
    priceAxisPx: Float,
    timeAxisPx: Float
): Zone {
    val inPriceStrip = p.x > widthPx - priceAxisPx
    val inTimeStrip = p.y > heightPx - timeAxisPx
    return when {
        inPriceStrip && inTimeStrip -> Zone.Corner
        inPriceStrip -> Zone.PriceAxis
        inTimeStrip -> Zone.TimeAxis
        else -> Zone.Body
    }
}

private suspend fun AwaitPointerEventScope.handleCorner(
    downPosition: Offset,
    touchSlop: Float,
    onTap: () -> Unit
) {
    val up = waitForUpOrCancellation() ?: return
    if ((up.position - downPosition).getDistance() <= touchSlop) {
        up.consume()
        onTap()
    }
}

/** Drags rescale price. Returns true if the gesture was a plain tap (no drag). */
private suspend fun AwaitPointerEventScope.handlePriceAxis(
    state: ChartGestureState,
    viewHeight: Float,
    sensitivity: Float,
    downPosition: Offset,
    touchSlop: Float
): Boolean {
    if (viewHeight <= 0f) return false
    var lastY = 0f
    var primed = false
    var moved = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.pressed } ?: return !moved
        if ((change.position - downPosition).getDistance() > touchSlop) moved = true
        val y = change.position.y
        if (!primed) {
            lastY = y
            primed = true
            continue
        }
        val dy = y - lastY
        lastY = y
        if (dy != 0f && moved) {
            val factor = exp(-dy / sensitivity)
            state.update { it.scalePriceBy(factor, y, state.viewHeight) }
        }
        event.changes.forEach { it.consume() }
    }
}

/** Drags rescale time. Returns true if the gesture was a plain tap (no drag). */
private suspend fun AwaitPointerEventScope.handleTimeAxis(
    state: ChartGestureState,
    viewWidth: Float,
    sensitivity: Float,
    downPosition: Offset,
    touchSlop: Float
): Boolean {
    if (viewWidth <= 0f) return false
    var lastX = 0f
    var primed = false
    var moved = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.pressed } ?: return !moved
        if ((change.position - downPosition).getDistance() > touchSlop) moved = true
        val xCanvas = change.position.x
        val xLocal = xCanvas - state.chartLeftPx
        if (!primed) {
            lastX = xLocal
            primed = true
            continue
        }
        val dx = xLocal - lastX
        lastX = xLocal
        if (dx != 0f && moved) {
            val factor = exp(-dx / sensitivity)
            state.update { it.scaleTimeBy(factor, xLocal) }
        }
        event.changes.forEach { it.consume() }
    }
}

private suspend fun AwaitPointerEventScope.handleBody(
    state: ChartGestureState,
    pinchSensitivity: Float,
    downPosition: Offset,
    touchSlop: Float,
    onTap: (Offset) -> Unit
) {
    var prevCentroid: Offset? = null
    var prevDistance = 0f
    var isTap = true
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) {
            if (isTap) onTap(downPosition)
            return
        }
        if (pressed.size >= 2 ||
            (pressed[0].position - downPosition).getDistance() > touchSlop
        ) isTap = false

        val rawCentroid = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } /
            pressed.size.toFloat()
        val centroid = Offset(rawCentroid.x - state.chartLeftPx, rawCentroid.y)
        val distance = if (pressed.size >= 2) {
            (pressed[0].position - pressed[1].position).getDistance()
        } else 0f

        val prev = prevCentroid
        if (prev != null) {
            val dx = centroid.x - prev.x
            val dy = centroid.y - prev.y
            if (dx != 0f) state.update { it.panByPixels(dx) }
            if (dy != 0f && state.viewHeight > 0f) {
                state.update { it.panPriceByPixels(dy, state.viewHeight) }
            }
            if (pressed.size >= 2 && prevDistance > 0f && distance > 0f) {
                val factor = (distance / prevDistance) * pinchSensitivity
                state.update { it.scaleTimeBy(factor, centroid.x) }
            }
        }
        prevCentroid = centroid
        prevDistance = distance
        event.changes.forEach { it.consume() }
    }
}
