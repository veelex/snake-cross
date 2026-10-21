package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.pattern.Pattern
import com.auto.rr.domain.pattern.PatternType
import com.auto.rr.ui.theme.ChartColors

/**
 * Draws double/triple tops and bottoms and Elliott 5-wave impulses.
 *
 * Expects [scope] to be exactly the chart area (see ChartScreen: inset + clip), so no
 * coordinate clamping is needed: shapes that run off-screen are simply clipped.
 *  - confirmed: solid shape, solid neckline, break marker on the neckline
 *  - forming  : dimmed shape, neckline extended to the latest bar
 *  - Elliott  : waves numbered 1-5, neckline starts at the end of wave 4
 */
object ChartPatternRendererLive {

    fun render(
        scope: DrawScope,
        measurer: TextMeasurer,
        candles: List<CandleEntity>,
        patterns: List<Pattern>,
        transform: ChartTransform,
        colors: ChartColors,
        styles: ChartLineStyles = ChartLineStyles.Default
    ) {
        if (candles.isEmpty() || patterns.isEmpty() || scope.size.width <= 0f) return

        val viewHeight = scope.size.height
        val viewWidth = scope.size.width
        val firstVisible = (transform.xStart.toInt() - 1).coerceAtLeast(0)
        val lastVisible = ((transform.xStart + viewWidth / transform.xScale).toInt() + 1)
            .coerceAtMost(candles.size - 1)

        val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
        val dotRadius = 3.5f
        val half = transform.xScale / 2f
        val lastIdx = candles.size - 1

        scope.clipRect(0f, 0f, viewWidth, viewHeight) {
            for (p in patterns) {
                val conf = p.confirmationIndex
                val neckEndIdx = conf ?: lastIdx
                if (neckEndIdx < firstVisible && p.rightIndex < firstVisible) continue
                if (p.leftIndex > lastVisible) continue

                val n = p.pivotIndices.size
                if (n < 3) continue

                val auto = if (p.bullish) colors.bullish else colors.bearish
                val a = if (p.isConfirmed) 1f else 0.45f
                val shapeStyle = styles.shapeFor(p)
                val neckStyle = styles.neckFor(p)
                // Solid colour for the break ring; shape colour (dimmed while forming) for the rest.
                val color = shapeStyle.color(auto)
                val shapeColor = shapeStyle.color(auto, a)

                val xs = FloatArray(n) { i -> transform.screenXFor(p.pivotIndices[i].toFloat()) + half }
                val ys = FloatArray(n) { i -> transform.screenYFor(p.pivotPrices[i], viewHeight) }
                if (ys.any { !it.isFinite() }) continue
                val yNeck = transform.screenYFor(p.neckline, viewHeight)
                if (!yNeck.isFinite()) continue
                val xEnd = transform.screenXFor(neckEndIdx.toFloat()) + half

                // Neckline: first leg -> break bar (or latest bar while forming)
                drawLine(
                    color = neckStyle.color(auto, a),
                    start = Offset(if (p.isElliott) xs[4] else xs.first(), yNeck),
                    end = Offset(xEnd, yNeck),
                    strokeWidth = neckStyle.width,
                    pathEffect = dash
                )

                // Shape through all pivots
                val shape = Path().apply {
                    moveTo(xs[0], ys[0])
                    for (i in 1 until n) lineTo(xs[i], ys[i])
                }
                drawPath(path = shape, color = shapeColor, style = Stroke(width = shapeStyle.width))

                // Leg dots (even indices); Elliott marks every wave end (1..5).
                if (p.isElliott) {
                    for (i in 1 until n) {
                        drawCircle(color = shapeColor, radius = dotRadius, center = Offset(xs[i], ys[i]))
                    }
                } else {
                    for (i in 0 until n step 2) {
                        drawCircle(color = shapeColor, radius = dotRadius, center = Offset(xs[i], ys[i]))
                    }
                }

                // Break marker: ring on the neckline at the confirmation bar
                if (conf != null) {
                    drawCircle(
                        color = color,
                        radius = dotRadius + 1.5f,
                        center = Offset(xEnd, yNeck),
                        style = Stroke(width = (shapeStyle.width * 0.85f).coerceAtLeast(1f))
                    )
                }

                if (p.isElliott) {
                    // Wave labels outside the shape: above swing highs, below swing lows.
                    val lab = styles.elliottLabels
                    if (lab.show) {
                        val up = p.type == PatternType.ELLIOTT_IMPULSE_UP
                        val labelColor = (lab.color?.let { Color(it) } ?: shapeStyle.color(auto))
                            .let { it.copy(alpha = (it.alpha * lab.opacity * a).coerceIn(0f, 1f)) }
                        val style = TextStyle(
                            color = labelColor,
                            fontSize = lab.sizeSp.sp,
                            fontWeight = if (lab.bold) FontWeight.Bold else FontWeight.Normal
                        )
                        for (i in 1 until n) {
                            val isHigh = up == (i % 2 == 1)
                            val text = lab.format.text(i) +
                                if (lab.markUnconfirmed && i == n - 1 && !p.isConfirmed) "?" else ""
                            val wl = measurer.measure(text, style)
                            val wy = if (isHigh) ys[i] - lab.distance - wl.size.height else ys[i] + lab.distance
                            drawText(
                                textLayoutResult = wl,
                                topLeft = Offset(xs[i] - wl.size.width / 2f, wy)
                            )
                        }
                    }
                    continue
                }

                // Triples: label on the middle leg, outside the shape.
                // Doubles: label on the neck, outside the shape.
                val anchor = if (n >= 5) 2 else 1
                val below = if (n >= 5) p.bullish else !p.bullish
                val label = labelFor(p.type) + if (p.isConfirmed) "" else "?"
                val layout = measurer.measure(
                    label,
                    TextStyle(color = shapeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                val ly = if (below) ys[anchor] + 8f else ys[anchor] - 8f - layout.size.height
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(xs[anchor] - layout.size.width / 2f, ly)
                )
            }
        }
    }

    private fun labelFor(t: PatternType): String = when (t) {
        PatternType.DOUBLE_TOP -> "DT"
        PatternType.DOUBLE_BOTTOM -> "DB"
        PatternType.TRIPLE_TOP -> "TT"
        PatternType.TRIPLE_BOTTOM -> "TB"
        PatternType.ELLIOTT_IMPULSE_UP, PatternType.ELLIOTT_IMPULSE_DOWN -> "EW"
        PatternType.WAVE3_LONG, PatternType.WAVE3_SHORT -> "W3"
    }
}
