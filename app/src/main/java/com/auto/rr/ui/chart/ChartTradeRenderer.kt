package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.backtest.ExitReason
import com.auto.rr.domain.backtest.Trade
import com.auto.rr.ui.theme.ChartColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws each backtest trade as a risk/reward box: the reward zone (entry to target) in the
 * bullish colour, the risk zone (entry to stop) in the bearish colour, a thin entry line between
 * them, spanning entry bar to exit bar. A small marker and the realised R show how it ended.
 */
object ChartTradeRenderer {

    private const val MIN_BOX_WIDTH = 3f
    private const val LABEL_MIN_WIDTH = 34f
    private const val CORNER = 2f

    fun render(
        scope: DrawScope,
        candles: List<CandleEntity>,
        trades: List<Trade>,
        transform: ChartTransform,
        colors: ChartColors,
        styles: ChartLineStyles = ChartLineStyles.Default,
        selected: Trade? = null,
        measurer: TextMeasurer? = null,
        exitRadius: Float = 3f
    ) {
        if (candles.isEmpty() || trades.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val firstVisible = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val lastVisible = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        for (t in trades) {
            if (t.exitIndex < firstVisible) continue
            if (t.entryIndex > lastVisible) continue

            val xEntry = transform.screenXFor(t.entryIndex.toFloat()) + transform.xScale / 2f
            val xExit = transform.screenXFor(t.exitIndex.toFloat()) + transform.xScale / 2f
            val yEntryRaw = transform.screenYFor(t.entryPrice, viewHeight)
            val yExitRaw = transform.screenYFor(t.exitPrice, viewHeight)
            val yStopRaw = transform.screenYFor(t.signal.stopPrice, viewHeight)
            val yTargetRaw = transform.screenYFor(t.signal.targetPrice, viewHeight)
            if (!yEntryRaw.isFinite() || !yExitRaw.isFinite()) continue

            val yEntry = yEntryRaw.coerceIn(0f, viewHeight)
            val yExit = yExitRaw.coerceIn(0f, viewHeight)
            val yStop = if (yStopRaw.isFinite()) yStopRaw.coerceIn(0f, viewHeight) else yEntry
            val yTarget = if (yTargetRaw.isFinite()) yTargetRaw.coerceIn(0f, viewHeight) else yEntry

            val isSelected = selected != null && t == selected
            val exitColor = exitColorFor(t.exitReason, colors)

            val left = min(xEntry, xExit)
            val width = max(abs(xExit - xEntry), MIN_BOX_WIDTH)
            val right = left + width
            val corner = CornerRadius(CORNER, CORNER)
            val box = styles.riskReward
            // The picked trade keeps a strong outline so it is easy to find, even with outlines off.
            val drawOutline = box.showOutline || isSelected
            val outlineW = if (isSelected) maxOf(box.outlineWidth, 2f) else box.outlineWidth
            val outlineA = if (isSelected) 0.95f else box.outlineOpacity
            val rewardColor = box.rewardColor?.let { Color(it) } ?: colors.bullish
            val riskColor = box.riskColor?.let { Color(it) } ?: colors.bearish

            if (isSelected) {
                // Soft band over the whole trade so it is easy to find after jumping to it.
                val bandL = left.coerceAtLeast(0f)
                val bandR = right.coerceAtMost(viewWidth)
                if (bandR > bandL) {
                    scope.drawRect(
                        color = exitColor.copy(alpha = 0.10f),
                        topLeft = Offset(bandL, 0f),
                        size = Size(bandR - bandL, viewHeight)
                    )
                }
            }

            // Reward zone (entry to target) and risk zone (entry to stop).
            drawZone(scope, left, width, yEntry, yTarget, rewardColor, box.fillOpacity, drawOutline, outlineW, outlineA, corner)
            drawZone(scope, left, width, yEntry, yStop, riskColor, box.fillOpacity, drawOutline, outlineW, outlineA, corner)

            // Entry line across the box.
            scope.drawLine(
                color = styles.tradePath.color(Color.White, 0.55f),
                start = Offset(left, yEntry),
                end = Offset(right, yEntry),
                strokeWidth = styles.tradePath.width
            )

            // Exit marker on the right edge, coloured by how the trade ended.
            if (box.showResult && xExit in -exitRadius..viewWidth + exitRadius) {
                val c = Offset(xExit, yExit)
                scope.drawCircle(color = exitColor, radius = exitRadius, center = c)
                scope.drawCircle(
                    color = Color.White, radius = exitRadius, center = c,
                    style = Stroke(width = 1f), alpha = 0.55f
                )
            }

            // Realised R, inside the box when there is room.
            if (box.showResult && measurer != null && width >= LABEL_MIN_WIDTH) {
                val text = String.format(Locale.US, "%+.1fR", t.rMultiple)
                val layout = measurer.measure(
                    text,
                    TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = exitColor)
                )
                val top = min(yTarget, min(yStop, yEntry))
                val tx = left.coerceAtLeast(0f) + 4f
                val ty = top + 2f
                if (tx + layout.size.width < right.coerceAtMost(viewWidth) && ty + layout.size.height < viewHeight) {
                    scope.drawText(layout, topLeft = Offset(tx, ty))
                }
            }
        }
    }

    private fun drawZone(
        scope: DrawScope,
        left: Float,
        width: Float,
        yA: Float,
        yB: Float,
        color: Color,
        fillAlpha: Float,
        drawOutline: Boolean,
        outlineW: Float,
        outlineA: Float,
        corner: CornerRadius
    ) {
        val top = min(yA, yB)
        val h = abs(yA - yB)
        if (h < 0.5f) return
        scope.drawRoundRect(
            color = color.copy(alpha = fillAlpha),
            topLeft = Offset(left, top),
            size = Size(width, h),
            cornerRadius = corner
        )
        if (drawOutline) {
            scope.drawRoundRect(
                color = color.copy(alpha = outlineA),
                topLeft = Offset(left, top),
                size = Size(width, h),
                cornerRadius = corner,
                style = Stroke(width = outlineW)
            )
        }
    }

    private fun exitColorFor(reason: ExitReason, colors: ChartColors): Color = when (reason) {
        ExitReason.STOP -> colors.bearish
        ExitReason.TARGET -> colors.bullish
        ExitReason.TRAIL -> colors.ema8
        ExitReason.TIME -> colors.axisLabel
        ExitReason.HOLD -> colors.axisLabel
    }
}
