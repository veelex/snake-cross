package com.auto.rr.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.ui.theme.ChartColors

object ChartEmasRenderer {

    fun render(
        scope: DrawScope,
        candles: List<CandleEntity>,
        emas: Map<Int, DoubleArray>,
        transform: ChartTransform,
        colors: ChartColors,
        styles: ChartLineStyles = ChartLineStyles.Default
    ) {
        if (candles.isEmpty() || emas.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val first = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val last = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)
        if (last < first) return

        for ((period, values) in emas) {
            val auto = colorFor(period, colors) ?: continue
            val color = styles.ema.copy(color = styles.emaColors[period]).color(auto)
            val path = Path()
            var started = false
            for (i in first..last) {
                val v = values.getOrNull(i) ?: continue
                if (!v.isFinite()) continue
                val x = transform.screenXFor(i.toFloat()) + transform.xScale / 2f
                val y = transform.screenYFor(v, viewHeight)
                if (!y.isFinite()) continue
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (started) {
                scope.drawPath(path = path, color = color, style = Stroke(width = styles.ema.width))
            }
        }
    }

    private fun colorFor(period: Int, colors: ChartColors): Color? = when (period) {
        1 -> colors.ema1
        5 -> colors.ema5
        8 -> colors.ema8
        13 -> colors.ema13
        55 -> colors.ema55
        else -> null
    }
}
