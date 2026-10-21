package com.auto.rr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auto.rr.ui.theme.AppFonts
import com.auto.rr.ui.theme.AppStatus
import com.auto.rr.ui.theme.CardTitleStyle
import kotlin.math.roundToInt

/**
 * Shared building blocks for the Indicators and Tune sheets.
 *
 * Layout of every option:  label ........ [value]
 *                          one-line hint (what it does / which way to turn it)
 *                          control
 *                          low-end hint ........ high-end hint
 */

/** Set to false by a [TuningCard] whose master switch is off, so its controls dim and lock. */
internal val LocalTuningEnabled = compositionLocalOf { true }

/**
 * Collapsible card for one indicator / settings group.
 *
 * @param enabled        master switch state, or null when the group has no on/off switch
 * @param onReset        shows a "Reset to defaults" button when non-null (pass null when unchanged)
 */
@Composable
internal fun TuningCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean? = null,
    onEnabledChange: (Boolean) -> Unit = {},
    onReset: (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val active = enabled ?: true

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Tinted header band so the title reads as separate from the options below it.
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = CardTitleStyle,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (enabled != null) {
                            Spacer(Modifier.width(8.dp))
                            StatusDot(on = active)
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (enabled != null) {
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (expanded) "\u2212" else "+",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalTuningEnabled provides active) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                            .alpha(if (active) 1f else 0.45f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        content()
                    }
                }
                if (onReset != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onReset) { Text("Reset to defaults") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(on: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                if (on) AppStatus.colors.ok else MaterialTheme.colorScheme.outline,
                CircleShape
            )
    )
}

/** Small coloured caption that groups related options inside a card. */
@Composable
internal fun SettingGroup(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Plain explanatory paragraph inside a card. */
@Composable
internal fun SettingNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun SettingSwitch(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val on = LocalTuningEnabled.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = on)
    }
}

@Composable
private fun ValueChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = AppFonts.Mono,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SettingHeader(label: String, hint: String, valueText: String, accent: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (accent != null) {
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        ValueChip(valueText)
    }
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EndHints(low: String?, high: String?) {
    if (low == null && high == null) return
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            low.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            high.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val SwatchPalette = listOf(
    0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFF44336.toInt(), 0xFFE91E63.toInt(),
    0xFF9C27B0.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF00BCD4.toInt(),
    0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFFCDDC39.toInt(), 0xFFFFFFFF.toInt(),
    0xFF9E9E9E.toInt(), 0xFF000000.toInt()
)

/**
 * Colour picker: an "Auto" swatch (the line's normal colour, shown as [autoColor]) followed by a
 * palette. [selected] is an ARGB int, or null for Auto.
 */
@Composable
internal fun SettingColor(
    label: String,
    selected: Int?,
    autoColor: Color,
    onChange: (Int?) -> Unit,
    hint: String? = null
) {
    val on = LocalTuningEnabled.current
    val ring = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Swatch(color = autoColor.copy(alpha = 1f), chosen = selected == null, ring = ring, enabled = on, text = "A") {
                onChange(null)
            }
            for (c in SwatchPalette) {
                Swatch(color = Color(c), chosen = selected == c, ring = ring, enabled = on, text = null) {
                    onChange(c)
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, chosen: Boolean, ring: Color, enabled: Boolean, text: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color, CircleShape)
            .border(if (chosen) 2.5.dp else 1.dp, if (chosen) ring else ring.copy(alpha = 0.25f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Colour + width + opacity for one kind of line.
 * [autoColor] previews what "Auto" means for this line.
 */
@Composable
internal fun LineStyleControls(
    title: String,
    style: LineStyle,
    autoColor: Color,
    onChange: (LineStyle) -> Unit
) {
    SettingGroup(title)
    SettingColor(
        label = "Colour",
        selected = style.color,
        autoColor = autoColor,
        onChange = { onChange(style.copy(color = it)) }
    )
    SettingSlider(
        label = "Width",
        hint = "Line thickness.",
        valueText = String.format(java.util.Locale.US, "%.2f px", style.width),
        value = style.width.toDouble(),
        min = 0.5,
        max = 5.0,
        lowHint = "thin",
        highHint = "thick",
        onChange = { onChange(style.copy(width = (Math.round(it * 4.0) / 4.0).toFloat())) }
    )
    SettingSlider(
        label = "Opacity",
        hint = "Lower it to let candles show through.",
        valueText = (style.opacity * 100).toInt().toString() + "%",
        value = style.opacity.toDouble(),
        min = 0.1,
        max = 1.0,
        lowHint = "faint",
        highHint = "solid",
        onChange = { onChange(style.copy(opacity = (Math.round(it * 20.0) / 20.0).toFloat())) }
    )
}

/** Row of mutually exclusive choices (pills) with label and hint. */
@Composable
internal fun <T> SettingChoice(
    label: String,
    hint: String,
    options: List<Pair<T, String>>,
    selected: T,
    onChange: (T) -> Unit
) {
    val on = LocalTuningEnabled.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, text) in options) {
                val isSel = value == selected
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = on) { onChange(value) }
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Continuous slider with label, live value chip, hint and optional end-of-range captions
 * (e.g. low = "more patterns", high = "fewer patterns").
 */
@Composable
internal fun SettingSlider(
    label: String,
    hint: String,
    valueText: String,
    value: Double,
    min: Double,
    max: Double,
    onChange: (Double) -> Unit,
    lowHint: String? = null,
    highHint: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingHeader(label, hint, valueText)
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { onChange(it.toDouble().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = LocalTuningEnabled.current,
            modifier = Modifier.fillMaxWidth()
        )
        EndHints(lowHint, highHint)
    }
}

/** Integer slider. Small ranges snap with tick marks; large ranges are rounded without ticks. */
@Composable
internal fun SettingIntSlider(
    label: String,
    hint: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    valueText: String = value.toString(),
    lowHint: String? = null,
    highHint: String? = null
) {
    val span = max - min
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingHeader(label, hint, valueText)
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = if (span in 2..20) span - 1 else 0,
            enabled = LocalTuningEnabled.current,
            modifier = Modifier.fillMaxWidth()
        )
        EndHints(lowHint, highHint)
    }
}

/** Two-thumb slider for an hour-of-day window [start, end) in UTC. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingHourRange(
    label: String,
    hint: String,
    start: Int,
    end: Int,
    accent: Color,
    onChange: (start: Int, end: Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingHeader(label, hint, hourText(start) + " \u2013 " + hourText(end), accent)
        RangeSlider(
            value = start.toFloat()..end.toFloat(),
            onValueChange = { r ->
                var s = r.start.roundToInt().coerceIn(0, 23)
                var e = r.endInclusive.roundToInt().coerceIn(1, 24)
                if (e <= s) {
                    if (s != start) s = e - 1 else e = s + 1
                }
                onChange(s.coerceIn(0, 23), e.coerceIn(1, 24))
            },
            valueRange = 0f..24f,
            steps = 23,
            enabled = LocalTuningEnabled.current,
            modifier = Modifier.fillMaxWidth()
        )
        EndHints("00:00", "24:00")
    }
}

private fun hourText(h: Int): String = (if (h < 10) "0$h" else "$h") + ":00"

/** Three-lane 24h strip showing where each session sits, so overlaps are obvious at a glance. */
@Composable
internal fun SessionTimeline(
    tokyo: IntRange,
    london: IntRange,
    ny: IntRange,
    tokyoColor: Color,
    londonColor: Color,
    nyColor: Color
) {
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(58.dp)) {
        val laneH = 10.dp.toPx()
        val gap = 3.dp.toPx()
        val w = size.width
        val unit = w / 24f
        val lanes = listOf(
            tokyo to tokyoColor,
            london to londonColor,
            ny to nyColor
        )
        lanes.forEachIndexed { i, (range, color) ->
            val top = i * (laneH + gap)
            drawRoundRect(
                color = track.copy(alpha = 0.35f),
                topLeft = Offset(0f, top),
                size = Size(w, laneH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            // IntRange here is [start, endExclusive) encoded as start..(end-1)
            if (!range.isEmpty()) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(range.first * unit, top),
                    size = Size((range.last + 1 - range.first) * unit, laneH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
        val style = TextStyle(color = labelColor, fontSize = 9.sp)
        val labelTop = 3 * (laneH + gap) + 2.dp.toPx()
        for (h in listOf(0, 6, 12, 18, 24)) {
            val x = h * unit
            drawLine(
                color = labelColor.copy(alpha = 0.5f),
                start = Offset(x, 3 * (laneH + gap) - gap),
                end = Offset(x, 3 * (laneH + gap) - gap + 3.dp.toPx()),
                strokeWidth = 1f
            )
            val layout = measurer.measure((if (h < 10) "0$h" else "$h") + "h", style)
            val left = (x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
            drawText(textLayoutResult = layout, topLeft = Offset(left, labelTop))
        }
    }
}
