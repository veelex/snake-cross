package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.backtest.Trade
import com.auto.rr.domain.strategy.Signal
import com.auto.rr.domain.strategy.SignalSource
import com.auto.rr.ui.theme.ChartColors
import java.util.Locale
import kotlin.math.min

/**
 * Draws the wave-3 entry setups:
 *  - the wave 1 / wave 2 swings, numbered "1" and "2"
 *  - the order levels as horizontal lines: limit (entry), stop loss (start of wave 1) and the
 *    profit target (PTL, top of wave 3), from the bar where the order is placed to the trade's
 *    exit, or to the order's expiry when it never filled.
 *
 * Expects [scope] to be exactly the chart area (ChartScreen already insets and clips it).
 */
object ChartWaveThreeRenderer {

    private val EntryColor = Color(0xFF42A5F5)
    private val WaveColor = Color(0xFFFFC107)

    fun render(
        scope: DrawScope,
        measurer: TextMeasurer,
        candles: List<CandleEntity>,
        signals: List<Signal>,
        trades: List<Trade>,
        transform: ChartTransform,
        colors: ChartColors
    ) {
        if (candles.isEmpty() || signals.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val firstVisible = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val lastVisible = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)
        val lastIdx = candles.size - 1
        val half = transform.xScale / 2f
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

        val tradeBySignal = HashMap<Signal, Trade>(trades.size)
        for (t in trades) tradeBySignal[t.signal] = t

        for (s in signals) {
            if (s.source != SignalSource.WAVE_THREE) continue
            val w = s.wave ?: continue

            val trade = tradeBySignal[s]
            val endIdx = trade?.exitIndex
                ?: (if (s.expiryIndex >= 0) min(s.expiryIndex, lastIdx) else lastIdx)
            if (endIdx < firstVisible || w.startIndex > lastVisible) continue

            val up = s.bullish
            val xStart = transform.screenXFor(s.triggerIndex.toFloat()) + half
            val xEnd = transform.screenXFor(endIdx.toFloat()) + half

            // ---- waves 1 and 2
            val x0 = transform.screenXFor(w.startIndex.toFloat()) + half
            val y0 = transform.screenYFor(w.startPrice, viewHeight)
            val x1 = transform.screenXFor(w.endIndex.toFloat()) + half
            val y1 = transform.screenYFor(w.endPrice, viewHeight)
            if (y0.isFinite() && y1.isFinite()) {
                val path = Path()
                path.moveTo(x0, y0)
                path.lineTo(x1, y1)
                val pbIdx = w.pullbackIndex
                val pbPrice = w.pullbackPrice
                if (pbIdx != null && pbPrice != null) {
                    val x2 = transform.screenXFor(pbIdx.toFloat()) + half
                    val y2 = transform.screenYFor(pbPrice, viewHeight)
                    if (y2.isFinite()) {
                        path.lineTo(x2, y2)
                        scope.drawCircle(color = WaveColor, radius = 3f, center = Offset(x2, y2))
                        drawWaveLabel(scope, measurer, "2", x2, y2, above = !up)   // wave 2 ends on a low after an up wave 1
                    }
                }
                scope.drawPath(path = path, color = WaveColor.copy(alpha = 0.85f), style = Stroke(width = 1.5f))
                scope.drawCircle(color = WaveColor, radius = 3f, center = Offset(x1, y1))
                drawWaveLabel(scope, measurer, "1", x1, y1, above = up)     // wave 1 ends on a high after an up wave 1
            }

            // ---- order levels
            val ratioText = String.format(Locale.US, "%.1f%%", w.retrace * 100.0)
            val entryText = (if (up) "BUY LMT " else "SELL LMT ") + ratioText
            drawLevel(scope, measurer, s.triggerPrice, entryText, EntryColor, xStart, xEnd, viewHeight, viewWidth, transform, dash)
            drawLevel(scope, measurer, s.stopPrice, "SL", colors.bearish, xStart, xEnd, viewHeight, viewWidth, transform, dash)
            drawLevel(scope, measurer, s.targetPrice, "PTL", colors.bullish, xStart, xEnd, viewHeight, viewWidth, transform, dash)
        }
    }

    private fun drawLevel(
        scope: DrawScope,
        measurer: TextMeasurer,
        price: Double,
        text: String,
        color: Color,
        xStart: Float,
        xEnd: Float,
        viewHeight: Float,
        viewWidth: Float,
        transform: ChartTransform,
        dash: PathEffect
    ) {
        val y = transform.screenYFor(price, viewHeight)
        if (!y.isFinite() || y < 0f || y > viewHeight) return
        scope.drawLine(
            color = color.copy(alpha = 0.85f),
            start = Offset(xStart, y),
            end = Offset(xEnd, y),
            strokeWidth = 1.25f,
            pathEffect = dash
        )
        val layout = measurer.measure(
            text,
            TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        )
        val tx = (xStart + 4f).coerceIn(0f, (viewWidth - layout.size.width).coerceAtLeast(0f))
        val ty = (y - layout.size.height - 1f).coerceAtLeast(0f)
        scope.drawText(textLayoutResult = layout, topLeft = Offset(tx, ty))
    }

    private fun drawWaveLabel(
        scope: DrawScope,
        measurer: TextMeasurer,
        text: String,
        x: Float,
        y: Float,
        above: Boolean
    ) {
        val layout = measurer.measure(
            text,
            TextStyle(color = WaveColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
        val ty = if (above) y - 8f - layout.size.height else y + 8f
        scope.drawText(
            textLayoutResult = layout,
            topLeft = Offset(x - layout.size.width / 2f, ty)
        )
    }
}
