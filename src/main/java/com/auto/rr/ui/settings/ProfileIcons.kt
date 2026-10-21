package com.auto.rr.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius

/** Glyphs for the Profile dialog. Same 24-unit grid and stroke as the toolbar glyphs. */
internal enum class ProfileGlyph { DATA_KEY, BROKER, DEVELOPER, WEBSITE, EMAIL, PHONE }

@Composable
internal fun ProfileIcon(glyph: ProfileGlyph, color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) { draw(glyph, color) }
}

private fun DrawScope.draw(glyph: ProfileGlyph, color: Color) {
    val s = size.width / 24f
    val w = 1.8f * s
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, o(x1, y1), o(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

    when (glyph) {
        // Key: round bow, diagonal shaft, two teeth.
        ProfileGlyph.DATA_KEY -> {
            drawCircle(color, radius = 3.6f * s, center = o(7.5f, 16.5f), style = stroke)
            line(10.2f, 13.8f, 20.5f, 3.5f)
            line(17.2f, 6.8f, 19.6f, 9.2f)
            line(14.2f, 9.8f, 16.2f, 11.8f)
        }
        // Plug: two prongs, body, cable.
        ProfileGlyph.BROKER -> {
            line(9f, 3f, 9f, 8f)
            line(15f, 3f, 15f, 8f)
            val body = Path().apply {
                moveTo(6f * s, 8f * s); lineTo(18f * s, 8f * s)
                lineTo(18f * s, 11f * s)
                cubicTo(18f * s, 14.5f * s, 15.5f * s, 16f * s, 12f * s, 16f * s)
                cubicTo(8.5f * s, 16f * s, 6f * s, 14.5f * s, 6f * s, 11f * s)
                close()
            }
            drawPath(body, color, style = stroke)
            line(12f, 16f, 12f, 21f)
        }
        // Person with a spark: the developer.
        ProfileGlyph.DEVELOPER -> {
            drawCircle(color, radius = 3.6f * s, center = o(10f, 8f), style = stroke)
            val shoulders = Path().apply {
                moveTo(3f * s, 20.5f * s)
                cubicTo(3f * s, 15f * s, 6f * s, 13.5f * s, 10f * s, 13.5f * s)
                cubicTo(14f * s, 13.5f * s, 17f * s, 15f * s, 17f * s, 20.5f * s)
            }
            drawPath(shoulders, color, style = stroke)
            line(20f, 4f, 20f, 9f)
            line(17.5f, 6.5f, 22.5f, 6.5f)
        }
        // Globe: circle, meridian ellipse, equator.
        ProfileGlyph.WEBSITE -> {
            drawCircle(color, radius = 9f * s, center = o(12f, 12f), style = stroke)
            drawOval(color, topLeft = o(8f, 3f), size = Size(8f * s, 18f * s), style = stroke)
            line(3f, 12f, 21f, 12f)
        }
        // Envelope.
        ProfileGlyph.EMAIL -> {
            drawRoundRect(
                color, topLeft = o(3f, 5.5f), size = Size(18f * s, 13f * s),
                cornerRadius = CornerRadius(2.5f * s), style = stroke
            )
            val flap = Path().apply {
                moveTo(3.5f * s, 7f * s); lineTo(12f * s, 13.5f * s); lineTo(20.5f * s, 7f * s)
            }
            drawPath(flap, color, style = stroke)
        }
        // Handset.
        ProfileGlyph.PHONE -> {
            val handset = Path().apply {
                moveTo(5f * s, 4f * s)
                lineTo(9f * s, 4f * s); lineTo(10.5f * s, 8.5f * s)
                lineTo(8.5f * s, 10f * s)
                cubicTo(9.5f * s, 12.5f * s, 11.5f * s, 14.5f * s, 14f * s, 15.5f * s)
                lineTo(15.5f * s, 13.5f * s)
                lineTo(20f * s, 15f * s); lineTo(20f * s, 19f * s)
                cubicTo(12f * s, 21f * s, 3f * s, 12f * s, 5f * s, 4f * s)
                close()
            }
            drawPath(handset, color, style = stroke)
        }
    }
}
