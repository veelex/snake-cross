package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.SessionBox
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.indicator.SessionKind
import com.auto.rr.ui.theme.ChartColors

object ChartSessionRenderer {

    fun render(
        scope: DrawScope,
        candles: List<CandleEntity>,
        transform: ChartTransform,
        colors: ChartColors,
        cfg: SessionConfig,
        boxes: List<SessionBox> = emptyList(),
        measurer: TextMeasurer? = null
    ) {
        if (!cfg.enabled || candles.isEmpty() || scope.size.width <= 0f) return
        if (cfg.boxMode) {
            renderBoxes(scope, boxes, transform, colors, cfg, measurer)
            return
        }

        val viewWidth = scope.size.width
        val viewHeight = scope.size.height
        val first = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val last = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        for (i in first..last) {
            val hour = ((candles[i].timestamp / 3_600_000L) % 24L).toInt()
            val left = transform.screenXFor(i.toFloat())
            val width = transform.xScale

            if (hour in cfg.tokyoStartHour until cfg.tokyoEndHour) {
                scope.drawRect(
                    color = colors.sessionTokyo,
                    topLeft = Offset(left, 0f),
                    size = Size(width, viewHeight)
                )
            }
            if (hour in cfg.londonStartHour until cfg.londonEndHour) {
                scope.drawRect(
                    color = colors.sessionLondon,
                    topLeft = Offset(left, 0f),
                    size = Size(width, viewHeight)
                )
            }
            if (hour in cfg.nyStartHour until cfg.nyEndHour) {
                scope.drawRect(
                    color = colors.sessionNewYork,
                    topLeft = Offset(left, 0f),
                    size = Size(width, viewHeight)
                )
            }
        }
    }

    /** One box per session: left/right = first/last candle, top/bottom = the session's high/low. */
    private fun renderBoxes(
        scope: DrawScope,
        boxes: List<SessionBox>,
        transform: ChartTransform,
        colors: ChartColors,
        cfg: SessionConfig,
        measurer: TextMeasurer?
    ) {
        if (boxes.isEmpty()) return
        val w = scope.size.width
        val h = scope.size.height
        val firstVisible = transform.xStart - 1f
        val lastVisible = transform.xStart + w / transform.xScale + 1f

        for (b in boxes) {
            if (b.endIndex + 1 < firstVisible || b.startIndex > lastVisible) continue

            val left = transform.screenXFor(b.startIndex.toFloat())
            val right = transform.screenXFor(b.endIndex.toFloat()) + transform.xScale
            val yTop = transform.screenYFor(b.high, h)
            val yBottom = transform.screenYFor(b.low, h)
            if (!(yTop.isFinite() && yBottom.isFinite())) continue
            // A one-bar session still gets a visible height.
            val top = minOf(yTop, yBottom)
            val height = maxOf(kotlin.math.abs(yBottom - yTop), 2f)

            val base = baseColor(b.kind, colors)
            scope.drawRect(
                color = base.copy(alpha = cfg.fillOpacity),
                topLeft = Offset(left, top),
                size = Size(right - left, height)
            )
            if (cfg.showBorder) {
                scope.drawRect(
                    color = base.copy(alpha = cfg.borderOpacity),
                    topLeft = Offset(left, top),
                    size = Size(right - left, height),
                    style = Stroke(width = 1.25f)
                )
            }
            if (cfg.showLabels && measurer != null) {
                val text = b.kind.label + if (b.live) " \u00B7 live" else ""
                val layout = measurer.measure(
                    text,
                    TextStyle(
                        color = base.copy(alpha = 0.95f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                val visibleLeft = left.coerceAtLeast(0f)
                // Skip the label when the box is too narrow to hold it.
                if (right - visibleLeft >= layout.size.width + 8f) {
                    val ly = (top + 2f).coerceIn(0f, (h - layout.size.height).coerceAtLeast(0f))
                    scope.drawText(layout, topLeft = Offset(visibleLeft + 4f, ly))
                }
            }
        }
    }

    /** Opaque accent for a session: the theme's session colour with its (faint) alpha removed. */
    private fun baseColor(kind: SessionKind, colors: ChartColors): Color {
        val c = when (kind) {
            SessionKind.TOKYO -> colors.sessionTokyo
            SessionKind.LONDON -> colors.sessionLondon
            SessionKind.NEW_YORK -> colors.sessionNewYork
        }
        return c.copy(alpha = 1f)
    }
}
