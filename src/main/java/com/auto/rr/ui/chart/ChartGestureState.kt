package com.auto.rr.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver

val ChartGestureStateSaver: Saver<ChartGestureState, List<Any>> = Saver(
    save = { listOf(it.transform.xStart, it.transform.xScale, it.transform.yMin, it.transform.yMax) },
    restore = {
        ChartGestureState(
            ChartTransform(
                it[0] as Float,
                it[1] as Float,
                it[2] as Double,
                it[3] as Double
            )
        )
    }
)

@Stable
class ChartGestureState(initial: ChartTransform) {

    var transform: ChartTransform by mutableStateOf(initial)
        private set

    var viewWidth: Float by mutableFloatStateOf(0f)
        internal set

    var viewHeight: Float by mutableFloatStateOf(0f)
        internal set

    var chartLeftPx: Float by mutableFloatStateOf(0f)
        internal set

    fun update(f: (ChartTransform) -> ChartTransform) {
        transform = f(transform)
    }

    fun reset(initial: ChartTransform) {
        transform = initial
    }
}

@Composable
fun rememberChartGestureState(initial: ChartTransform): ChartGestureState =
    remember(initial) { ChartGestureState(initial) }
