package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.auto.rr.domain.backtest.BacktestResult
import com.auto.rr.ui.theme.ChartColors
import java.util.Locale

object EquityCurveRenderer {

    private const val AXIS_WIDTH_PX = 56f
    private const val PAD_PX = 6f
    private const val TICK_COUNT = 3

    fun render(
        scope: DrawScope,
        measurer: TextMeasurer,
        result: BacktestResult,
        colors: ChartColors
    ) {
        val points = result.equityCurve
        if (points.size < 2 || scope.size.width <= 0f || scope.size.height <= 0f) return

        val plotW = scope.size.width - AXIS_WIDTH_PX
        val plotH = scope.size.height
        if (plotW <= 0f) return

        var lo = result.initialEquity
        var hi = result.initialEquity
        for (p in points) {
            if (p.equity < lo) lo = p.equity
            if (p.equity > hi) hi = p.equity
        }
        if (hi - lo < 1e-9) {
            lo -= 1.0
            hi += 1.0
        }
        val range = hi - lo
        val usableH = plotH - PAD_PX * 2f

        fun xFor(i: Int): Float =
            PAD_PX + (plotW - PAD_PX * 2f) * i / (points.size - 1).toFloat()

        fun yFor(v: Double): Float =
            PAD_PX + usableH * (1.0 - (v - lo) / range).toFloat()

        val positive = result.finalEquity >= result.initialEquity
        val lineColor = if (positive) colors.bullish else colors.bearish

        val areaPath = Path().apply {
            moveTo(xFor(0), plotH)
            for (i in points.indices) lineTo(xFor(i), yFor(points[i].equity))
            lineTo(xFor(points.size - 1), plotH)
            close()
        }
        scope.drawPath(path = areaPath, color = lineColor, alpha = 0.12f)

        val yBase = yFor(result.initialEquity)
        scope.drawLine(
            color = colors.axisLabel,
            start = Offset(0f, yBase),
            end = Offset(plotW, yBase),
            strokeWidth = 1f,
            alpha = 0.35f
        )

        val linePath = Path().apply {
            moveTo(xFor(0), yFor(points[0].equity))
            for (i in 1 until points.size) lineTo(xFor(i), yFor(points[i].equity))
        }
        scope.drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 1.5f)
        )

        val style = TextStyle(color = colors.axisLabel, fontSize = 9.sp)
        val span = hi - lo
        for (k in 0 until TICK_COUNT) {
            val v = lo + span * k / (TICK_COUNT - 1).toDouble()
            val y = yFor(v)
            if (y < 0f || y > plotH) continue

            scope.drawLine(
                color = colors.gridLine,
                start = Offset(0f, y),
                end = Offset(plotW, y),
                strokeWidth = 1f,
                alpha = 0.5f
            )

            val label = formatEquity(v)
            val layout = measurer.measure(label, style)
            val labelX = plotW + 4f
            val labelY = (y - layout.size.height * 0.5f).coerceIn(0f, plotH - layout.size.height)
            scope.drawText(
                textLayoutResult = layout,
                topLeft = Offset(labelX, labelY)
            )
        }
    }

    private fun formatEquity(v: Double): String =
        String.format(Locale.US, "%,.0f", v)
}
