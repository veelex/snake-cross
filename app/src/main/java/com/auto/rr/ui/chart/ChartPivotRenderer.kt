package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.PivotPoint
import com.auto.rr.ui.theme.ChartColors

object ChartPivotRenderer {

    fun render(
        scope: DrawScope,
        candles: List<CandleEntity>,
        pivots: List<PivotPoint>,
        transform: ChartTransform,
        colors: ChartColors,
        markerSizePx: Float = 6f
    ) {
        if (candles.isEmpty() || pivots.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val firstVisible = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val lastVisible = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        for (p in pivots) {
            if (p.index < firstVisible || p.index > lastVisible) continue

            val x = transform.screenXFor(p.index.toFloat()) + transform.xScale / 2f
            val yRaw = transform.screenYFor(p.price, viewHeight)
            if (!yRaw.isFinite()) continue

            val y = yRaw.coerceIn(0f, viewHeight)
            val color = if (p.isHigh) colors.bearish else colors.bullish
            val path = Path()

            if (p.isHigh) {
                // downward triangle below the wick
                val tipY = y + markerSizePx
                path.moveTo(x, tipY)
                path.lineTo(x - markerSizePx, y)
                path.lineTo(x + markerSizePx, y)
                path.close()
            } else {
                // upward triangle above the wick
                val tipY = y - markerSizePx
                path.moveTo(x, tipY)
                path.lineTo(x - markerSizePx, y)
                path.lineTo(x + markerSizePx, y)
                path.close()
            }

            scope.drawPath(path = path, color = color)
        }
    }
}
