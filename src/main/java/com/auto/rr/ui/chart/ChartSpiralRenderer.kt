package com.auto.rr.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.LogSpiral
import com.auto.rr.domain.indicator.SpiralAnchors
import com.auto.rr.domain.indicator.SpiralConfig
import com.auto.rr.ui.theme.ChartColors
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Draws the logarithmic spiral in screen space: it winds around the older anchor and passes
 * through the newer one, so the shape stays a true spiral at any zoom while both anchors stay
 * glued to their candles.
 */
object ChartSpiralRenderer {

    fun render(
        scope: DrawScope,
        candles: List<CandleEntity>,
        anchors: SpiralAnchors?,
        transform: ChartTransform,
        colors: ChartColors,
        config: SpiralConfig
    ) {
        if (!config.enabled || anchors == null || candles.isEmpty()) return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return

        val ax = (transform.screenXFor(anchors.pole.index.toFloat()) + transform.xScale / 2f).toDouble()
        val ay = transform.screenYFor(anchors.pole.price, h).toDouble()
        val bx = (transform.screenXFor(anchors.through.index.toFloat()) + transform.xScale / 2f).toDouble()
        val by = transform.screenYFor(anchors.through.price, h).toDouble()
        if (!(ax.isFinite() && ay.isFinite() && bx.isFinite() && by.isFinite())) return

        val r0 = LogSpiral.distance(ax, ay, bx, by)
        if (r0 < 2.0) return
        val theta0 = atan2(by - ay, bx - ax)
        val growth = config.growthPerQuarterTurn
        // Anything further than this from the pole is far off screen anyway.
        val maxRadius = (hypot(w.toDouble(), h.toDouble()) + r0) * 4.0

        val base = config.color?.let { androidx.compose.ui.graphics.Color(it) } ?: colors.spiral
        val main = base.copy(alpha = config.opacity)
        val sign = LogSpiral.windingSign(config.direction, anchors)

        drawOne(scope, ax, ay, r0, theta0, growth, sign, config, maxRadius, main, config.lineWidth)
        if (config.mirror) {
            drawOne(
                scope, ax, ay, r0, theta0, growth, -sign, config, maxRadius,
                base.copy(alpha = config.opacity * 0.6f), config.lineWidth
            )
        }

        if (config.showAnchors) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            scope.drawLine(
                color = base.copy(alpha = config.opacity * 0.6f),
                start = Offset(ax.toFloat(), ay.toFloat()),
                end = Offset(bx.toFloat(), by.toFloat()),
                strokeWidth = 1f,
                pathEffect = dash
            )
            scope.drawCircle(
                color = base, radius = 5f, center = Offset(ax.toFloat(), ay.toFloat()),
                style = Stroke(width = 1.5f)
            )
            scope.drawCircle(color = base, radius = 3.5f, center = Offset(bx.toFloat(), by.toFloat()))
        }
    }

    private fun drawOne(
        scope: DrawScope,
        cx: Double,
        cy: Double,
        r0: Double,
        theta0: Double,
        growth: Double,
        sign: Int,
        config: SpiralConfig,
        maxRadius: Double,
        color: androidx.compose.ui.graphics.Color,
        lineWidth: Float
    ) {
        val pts = LogSpiral.polyline(
            cx, cy, r0, theta0, growth, sign,
            config.inwardTurns, config.outwardTurns, maxRadius
        )
        if (pts.size >= 4) {
            val path = Path()
            path.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) {
                path.lineTo(pts[i], pts[i + 1])
                i += 2
            }
            scope.drawPath(
                path = path, color = color,
                style = Stroke(width = lineWidth, cap = StrokeCap.Round)
            )
        }
        if (config.showQuarterMarks) {
            val marks = LogSpiral.quarterMarks(
                cx, cy, r0, theta0, growth, sign,
                config.inwardTurns, config.outwardTurns, maxRadius
            )
            var i = 0
            while (i + 1 < marks.size) {
                scope.drawCircle(color = color, radius = 2.5f, center = Offset(marks[i], marks[i + 1]))
                i += 2
            }
        }
    }
}
