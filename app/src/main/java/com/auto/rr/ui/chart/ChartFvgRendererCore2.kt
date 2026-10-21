package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.FvgConfig
import com.auto.rr.domain.indicator.FvgZone
import com.auto.rr.ui.theme.ChartColors

object ChartFvgRenderer {

    fun render(
        scope: DrawScope,
        candles: List<CandleEntity>,
        zones: List<FvgZone>,
        transform: ChartTransform,
        colors: ChartColors,
        cfg: FvgConfig
    ) {
        if (candles.isEmpty() || zones.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val firstVisible = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val lastVisible = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        for (zone in zones) {
            // fixed-width window: [startIndex, startIndex + extensionBars]
            val naturalEnd = zone.startIndex + cfg.extensionBars
            val endIdx = zone.mitigationIndex?.let { minOf(it, naturalEnd) } ?: naturalEnd

            if (zone.startIndex > lastVisible) continue
            if (endIdx < firstVisible) continue

            val xLeftRaw = transform.screenXFor(zone.startIndex.toFloat()) + transform.xScale / 2f
            val xRightRaw = transform.screenXFor(endIdx.toFloat()) + transform.xScale / 2f

            val xLeft = xLeftRaw.coerceAtLeast(0f)
            val xRight = xRightRaw.coerceAtMost(viewWidth)
            if (xRight - xLeft < 0.5f) continue

            val yTopRaw = transform.screenYFor(zone.upperBound, viewHeight)
            val yBotRaw = transform.screenYFor(zone.lowerBound, viewHeight)
            if (!yTopRaw.isFinite() || !yBotRaw.isFinite()) continue

            val top = minOf(yTopRaw, yBotRaw)
            val bottom = maxOf(yTopRaw, yBotRaw)
            if (bottom < 0f || top > viewHeight) continue

            val clippedTop = top.coerceAtLeast(0f)
            val clippedBottom = bottom.coerceAtMost(viewHeight)
            if (clippedBottom - clippedTop < 0.5f) continue

            val baseColor = if (zone.bullish) colors.fvgBullish else colors.fvgBearish
            val alpha = if (zone.mitigationIndex != null) cfg.mitigatedAlpha else 1.0f

            scope.drawRect(
                color = baseColor,
                topLeft = Offset(xLeft, clippedTop),
                size = Size(xRight - xLeft, clippedBottom - clippedTop),
                alpha = alpha
            )
        }
    }
}
