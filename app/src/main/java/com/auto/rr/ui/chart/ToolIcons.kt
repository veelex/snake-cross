package com.auto.rr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Glyphs for the chart toolbar. Drawn on a 24-unit grid with one shared 1.8 stroke. */
internal enum class ToolGlyph { IND, TUNE, TRADES, PROFILE }

private const val GRID = 24f
private const val STROKE = 1.8f

/**
 * Toolbar button that shows a glyph instead of a text label. Same look as [ToolChip]
 * (quiet when idle, brass-tinted when its sheet is open) with a 44dp touch target.
 * [label] is kept for TalkBack.
 */
@Composable
internal fun ToolIconChip(
    glyph: ToolGlyph,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(bg)
            .semantics { contentDescription = label }
            .toggleable(value = active, enabled = enabled, role = Role.Button, onValueChange = { onClick() })
            .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
    ) {
        Canvas(modifier = Modifier.size(22.dp)) { drawGlyph(glyph, fg) }
    }
}

private fun DrawScope.drawGlyph(glyph: ToolGlyph, color: Color) {
    val s = size.width / GRID
    val stroke = Stroke(width = STROKE * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, o(x1, y1), o(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)

    when (glyph) {
        // Two moving averages crossing.
        ToolGlyph.IND -> {
            val slow = Path().apply {
                moveTo(3f * s, 14f * s)
                cubicTo(8f * s, 14f * s, 13f * s, 13f * s, 21f * s, 11f * s)
            }
            val fast = Path().apply {
                moveTo(3f * s, 19f * s)
                cubicTo(9f * s, 19f * s, 12f * s, 8f * s, 21f * s, 5f * s)
            }
            drawPath(slow, color, alpha = 0.55f, style = stroke)
            drawPath(fast, color, style = stroke)
        }
        // Three sliders with offset knobs.
        ToolGlyph.TUNE -> {
            val rows = listOf(6f to 15f, 12f to 8f, 18f to 13f)
            val r = 2.2f
            for ((y, kx) in rows) {
                line(3f, y, kx - r - 0.4f, y)
                line(kx + r + 0.4f, y, 21f, y)
                drawCircle(color, radius = r * s, center = o(kx, y), style = stroke)
            }
        }
        // Up and down arrows: entries and exits.
        ToolGlyph.TRADES -> {
            line(8f, 20f, 8f, 5f)
            line(4.5f, 8.5f, 8f, 5f)
            line(8f, 5f, 11.5f, 8.5f)
            line(16f, 4f, 16f, 19f)
            line(12.5f, 15.5f, 16f, 19f)
            line(16f, 19f, 19.5f, 15.5f)
        }
        // Profile: head and shoulders.
        ToolGlyph.PROFILE -> {
            drawCircle(color, radius = 4f * s, center = o(12f, 8f), style = stroke)
            val shoulders = Path().apply {
                moveTo(4f * s, 20.5f * s)
                cubicTo(4f * s, 15f * s, 8f * s, 13.5f * s, 12f * s, 13.5f * s)
                cubicTo(16f * s, 13.5f * s, 20f * s, 15f * s, 20f * s, 20.5f * s)
            }
            drawPath(shoulders, color, style = stroke)
        }
    }
}
