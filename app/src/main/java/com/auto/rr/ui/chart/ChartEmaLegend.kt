package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.auto.rr.ui.theme.ChartColors

object ChartEmaLegend {

    fun render(
        scope: DrawScope,
        measurer: TextMeasurer,
        periods: List<Int>,
        colors: ChartColors,
        overrides: Map<Int, Int> = emptyMap(),
        topLeft: Offset = Offset(8f, 8f)
    ) {
        if (periods.isEmpty()) return

        val style = TextStyle(color = colors.axisLabel, fontSize = 10.sp)
        val dotR = 3f
        val gapDotText = 5f
        val gapEntry = 14f

        var x = topLeft.x
        val baselineY = topLeft.y + 10f

        for (period in periods) {
            val color = overrides[period]?.let { Color(it) } ?: colorFor(period, colors) ?: continue

            // dot
            scope.drawCircle(
                color = color,
                radius = dotR,
                center = Offset(x + dotR, baselineY - 3f)
            )

            // label
            val label = "EMA" + period
            val layout = measurer.measure(label, style)
            scope.drawText(
                textLayoutResult = layout,
                topLeft = Offset(x + dotR * 2f + gapDotText, topLeft.y)
            )

            x += dotR * 2f + gapDotText + layout.size.width + gapEntry
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
