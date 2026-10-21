package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.ui.theme.ChartColors

object ChartRenderer {

    fun renderChartBackground(scope: DrawScope, bg: Color) {
        scope.drawRect(color = bg, size = Size(scope.size.width, scope.size.height))
    }

    /**
     * Horizontal lines sit on the price-axis ticks; vertical lines sit on the time-axis ticks
     * (through the centre of their bar), with day/month/year boundaries drawn stronger.
     */
    fun renderGrid(
        scope: DrawScope,
        colors: ChartColors,
        transform: ChartTransform,
        timeTicks: List<TimeTick>,
        style: LineStyle = LineStyle(1f, 1f)
    ) {
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return
        for (t in AxisTicks.niceTicks(transform.yMin, transform.yMax, 6)) {
            val y = transform.screenYFor(t, h)
            if (y.isFinite() && y in 0f..h) scope.drawLine(style.color(colors.gridLine), Offset(0f, y), Offset(w, y), style.width)
        }
        for (tick in timeTicks) {
            val x = transform.screenXFor(tick.index.toFloat()) + transform.xScale / 2f
            if (x < 0f || x > w) continue
            scope.drawLine(
                color = style.color(colors.gridLine, if (tick.major) 1f else 0.6f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = if (tick.major) style.width * 1.5f else style.width
            )
        }
    }

    fun renderCandles(
        scope: DrawScope,
        candles: List<CandleEntity>,
        transform: ChartTransform,
        colors: ChartColors,
        minBodyHeightPx: Float = 2f
    ) {
        if (candles.isEmpty() || scope.size.width <= 0f || scope.size.height <= 0f) return

        val slotWidth = transform.xScale
        val bodyWidth = (slotWidth * 0.72f).coerceIn(1f, 32f)
        val wickWidth = (bodyWidth * 0.15f).coerceAtLeast(1f)
        val halfBody = bodyWidth / 2f
        val viewHeight = scope.size.height
        val viewWidth = scope.size.width

        val first = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val last = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        for (i in first..last) {
            val c = candles[i]
            val centerX = transform.screenXFor(i.toFloat()) + transform.xScale / 2f
            val color = if (c.close >= c.open) colors.bullish else colors.bearish

            val yHighRaw = transform.screenYFor(c.high, viewHeight)
            val yLowRaw = transform.screenYFor(c.low, viewHeight)
            if (yHighRaw.isFinite() && yLowRaw.isFinite()) {
                val yHigh = yHighRaw.coerceIn(0f, viewHeight)
                val yLow = yLowRaw.coerceIn(0f, viewHeight)
                if (yHigh != yLow) {
                    scope.drawLine(
                        color = color,
                        start = Offset(centerX, yHigh),
                        end = Offset(centerX, yLow),
                        strokeWidth = wickWidth
                    )
                }
            }

            val yOpen = transform.screenYFor(c.open, viewHeight)
            val yClose = transform.screenYFor(c.close, viewHeight)
            if (!yOpen.isFinite() || !yClose.isFinite()) continue

            var top = minOf(yOpen, yClose).coerceIn(0f, viewHeight)
            var bottom = maxOf(yOpen, yClose).coerceIn(0f, viewHeight)

            // v2 rule: a body under the minimum height is grown symmetrically
            // around its centre so a doji is still visible.
            if (bottom - top < minBodyHeightPx) {
                val mid = (top + bottom) * 0.5f
                top = (mid - minBodyHeightPx * 0.5f).coerceAtLeast(0f)
                bottom = (mid + minBodyHeightPx * 0.5f).coerceAtMost(viewHeight)
            }

            scope.drawRect(
                color = color,
                topLeft = Offset(centerX - halfBody, top),
                size = Size(bodyWidth, bottom - top)
            )
        }
    }
}
