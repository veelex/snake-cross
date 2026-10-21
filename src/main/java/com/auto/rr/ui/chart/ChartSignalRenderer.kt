package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.strategy.Signal
import com.auto.rr.domain.strategy.SignalSource
import com.auto.rr.ui.theme.ChartColors

object ChartSignalRenderer {

    fun render(
        scope: DrawScope,
        measurer: TextMeasurer,
        candles: List<CandleEntity>,
        signals: List<Signal>,
        transform: ChartTransform,
        colors: ChartColors,
        styles: ChartLineStyles = ChartLineStyles.Default
    ) {
        if (candles.isEmpty() || signals.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val firstVisible = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val lastVisible = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        var lastTagIndex = Int.MIN_VALUE

        for (s in signals) {
            if (s.triggerIndex < firstVisible || s.triggerIndex > lastVisible) continue

            val xStart = transform.screenXFor(s.triggerIndex.toFloat()) + transform.xScale / 2f
            val yEntryRaw = transform.screenYFor(s.triggerPrice, viewHeight)
            if (!yEntryRaw.isFinite()) continue
            val yEntry = yEntryRaw.coerceIn(0f, viewHeight)

            val color = if (s.bullish) colors.bullish else colors.bearish

            // Entry arrow (triangle) — points up for buy, down for sell
            val arrowH = 9f
            val arrowW = 11f
            val arrow = Path()
            if (s.bullish) {
                arrow.moveTo(xStart, yEntry - arrowH * 0.4f)
                arrow.lineTo(xStart - arrowW * 0.5f, yEntry + arrowH * 0.6f)
                arrow.lineTo(xStart + arrowW * 0.5f, yEntry + arrowH * 0.6f)
            } else {
                arrow.moveTo(xStart, yEntry + arrowH * 0.4f)
                arrow.lineTo(xStart - arrowW * 0.5f, yEntry - arrowH * 0.6f)
                arrow.lineTo(xStart + arrowW * 0.5f, yEntry - arrowH * 0.6f)
            }
            arrow.close()
            scope.drawPath(path = arrow, color = color)

            // BUY / SELL tag — one per trigger index even if multiple signals
            if (s.triggerIndex != lastTagIndex) {
                drawTag(scope, measurer, s.bullish, s.source, color, xStart, yEntry, viewWidth, viewHeight)
                lastTagIndex = s.triggerIndex
            }
        }
    }

    private fun drawTag(
        scope: DrawScope,
        measurer: TextMeasurer,
        bullish: Boolean,
        source: SignalSource,
        accentColor: Color,
        centerX: Float,
        arrowTipY: Float,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val base = if (bullish) "BUY" else "SELL"
        val label = if (source == SignalSource.EMA_SWEEP) "$base \u00B7 4EMA" else base
        val style = TextStyle(color = Color.White, fontSize = 9.sp)
        val layout = measurer.measure(label, style)

        val padH = 6f
        val padV = 4f
        val tagW = layout.size.width + padH * 2
        val tagH = layout.size.height + padV * 2

        // Place above for buy, below for sell — matches v2's visual intent.
        val top = if (bullish) arrowTipY - 20f - tagH else arrowTipY + 20f
        val left = (centerX - tagW * 0.5f).coerceIn(0f, viewWidth - tagW)
        val right = left + tagW
        val bottom = top + tagH

        if (top < 0f || bottom > viewHeight) return

        // Background capsule
        scope.drawRoundRect(
            color = accentColor,
            topLeft = Offset(left, top),
            size = Size(right - left, tagH),
            cornerRadius = CornerRadius(3f, 3f),
            alpha = 0.92f
        )
        // Border
        scope.drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, tagH),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1f),
            alpha = 0.4f
        )
        // Text
        scope.drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                left + padH,
                top + (tagH - layout.size.height) * 0.5f
            )
        )
    }
}
