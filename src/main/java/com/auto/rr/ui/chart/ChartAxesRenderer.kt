package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auto.rr.ui.theme.ChartColors

/**
 * Axis layout: the price (Y) axis is a strip on the RIGHT edge of the canvas
 * (x in [axisX, axisX + axisWidth]); the time (X) axis is a strip along the bottom.
 */
object ChartAxesRenderer {

    private const val TICK_LEN = 4f
    private const val LABEL_GAP = 8f

    fun renderPriceAxis(
        scope: DrawScope,
        measurer: TextMeasurer,
        chartTop: Float,
        chartBottom: Float,
        axisX: Float,
        transform: ChartTransform,
        colors: ChartColors
    ) {
        val chartH = chartBottom - chartTop
        if (chartH <= 0f) return
        val range = transform.yMax - transform.yMin
        val ticks = AxisTicks.niceTicks(transform.yMin, transform.yMax, 6)
        val style = TextStyle(color = colors.axisLabel, fontSize = 10.sp)

        // axis spine
        scope.drawRect(
            color = colors.gridLine,
            topLeft = Offset(axisX, chartTop),
            size = Size(1f, chartH)
        )

        for (t in ticks) {
            val y = chartTop + transform.screenYFor(t, chartH)
            if (y < chartTop - 2f || y > chartBottom + 2f) continue

            scope.drawLine(
                color = colors.axisLabel,
                start = Offset(axisX + 1f, y),
                end = Offset(axisX + 1f + TICK_LEN, y),
                strokeWidth = 1f
            )

            val layout = measurer.measure(AxisTicks.formatPrice(t, range), style)
            scope.drawText(
                textLayoutResult = layout,
                topLeft = Offset(axisX + LABEL_GAP, y - layout.size.height / 2f)
            )
        }
    }

    /** Filled price tag on the axis for the latest close. */
    fun renderLastPriceTag(
        scope: DrawScope,
        measurer: TextMeasurer,
        chartTop: Float,
        chartBottom: Float,
        axisX: Float,
        axisWidth: Float,
        price: Double,
        tagColor: Color,
        transform: ChartTransform
    ) {
        val chartH = chartBottom - chartTop
        if (chartH <= 0f) return
        val y = chartTop + transform.screenYFor(price, chartH)
        if (!y.isFinite()) return
        val label = AxisTicks.formatPrice(price, transform.yMax - transform.yMin)
        val layout = measurer.measure(label, TextStyle(color = Color.White, fontSize = 10.sp))
        val h = layout.size.height + 4f
        val top = (y - h / 2f).coerceIn(chartTop, chartBottom - h)
        scope.drawRect(
            color = tagColor,
            topLeft = Offset(axisX + 1f, top),
            size = Size(axisWidth - 1f, h)
        )
        scope.drawText(
            textLayoutResult = layout,
            topLeft = Offset(axisX + LABEL_GAP, top + 2f)
        )
    }

    fun renderTimeAxis(
        scope: DrawScope,
        measurer: TextMeasurer,
        chartLeft: Float,
        chartRight: Float,
        chartBottom: Float,
        transform: ChartTransform,
        ticks: List<TimeTick>,
        colors: ChartColors
    ) {
        val chartW = chartRight - chartLeft
        if (chartW <= 0f) return
        val minor = TextStyle(color = colors.axisLabel, fontSize = 10.sp)
        val major = TextStyle(color = colors.axisLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)

        // axis spine
        scope.drawRect(
            color = colors.gridLine,
            topLeft = Offset(chartLeft, chartBottom),
            size = Size(chartW, 1f)
        )

        var lastLabelRight = chartLeft - 1f
        for (tick in ticks) {
            // tick sits on the centre of its bar
            val x = chartLeft + transform.screenXFor(tick.index.toFloat()) + transform.xScale / 2f
            if (x < chartLeft || x > chartRight) continue

            scope.drawLine(
                color = colors.axisLabel,
                start = Offset(x, chartBottom + 1f),
                end = Offset(x, chartBottom + 1f + (if (tick.major) TICK_LEN + 2f else TICK_LEN)),
                strokeWidth = if (tick.major) 1.5f else 1f
            )

            val layout = measurer.measure(tick.label, if (tick.major) major else minor)
            val left = x - layout.size.width / 2f
            val right = left + layout.size.width
            // never clip at the edges and never overlap the previous label
            if (left < chartLeft || right > chartRight || left < lastLabelRight + 6f) continue
            scope.drawText(
                textLayoutResult = layout,
                topLeft = Offset(left, chartBottom + 6f)
            )
            lastLabelRight = right
        }
    }

    /** Bottom-right corner where the two axes meet; shows the time zone of the time axis; tapping it opens the picker. */
    fun renderCorner(
        scope: DrawScope,
        measurer: TextMeasurer,
        axisX: Float,
        chartBottom: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        label: String,
        colors: ChartColors
    ) {
        scope.drawRect(
            color = colors.gridLine,
            topLeft = Offset(axisX, chartBottom),
            size = Size(canvasWidth - axisX, canvasHeight - chartBottom)
        )
        val layout = measurer.measure(label, TextStyle(color = colors.axisLabel, fontSize = 9.sp))
        scope.drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                axisX + (canvasWidth - axisX - layout.size.width) / 2f,
                chartBottom + (canvasHeight - chartBottom - layout.size.height) / 2f
            )
        )
    }
}
