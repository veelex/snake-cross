package com.auto.rr.ui.chart

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.TimeZone

/**
 * Display time zones for the chart. Each one is backed by a real IANA zone, so offsets are
 * resolved per timestamp and daylight saving is handled: New York is EST in winter and EDT in
 * summer, and a chart spanning a clock change stays correct on both sides.
 *
 * Stored candles and session hours stay in UTC; this only changes what is displayed.
 */
enum class ChartTimeZone(
    val zoneId: String,
    val title: String,
    /** Abbreviation outside daylight saving. */
    private val standardAbbr: String,
    /** Abbreviation during daylight saving, or null if the zone has none. */
    private val daylightAbbr: String?
) {
    UTC("UTC", "Coordinated Universal Time", "UTC", null),
    EAT("Africa/Nairobi", "East Africa Time (Nairobi)", "EAT", null),
    EST("America/New_York", "US Eastern (New York)", "EST", "EDT");

    private val zone: TimeZone by lazy(LazyThreadSafetyMode.PUBLICATION) { TimeZone.getTimeZone(zoneId) }

    private class Cached(val window: Long, val offsetMs: Long)

    @Volatile private var cached = Cached(Long.MIN_VALUE, 0L)

    /** Offset from UTC, in ms, in effect at the instant [utcMs]. */
    fun offsetAt(utcMs: Long): Long {
        // Clock changes only happen on 15-minute boundaries, so one lookup per window is exact
        // and keeps per-bar axis maths cheap. Immutable holder => safe across threads.
        val window = Math.floorDiv(utcMs, WINDOW_MS)
        val c = cached
        if (c.window == window) return c.offsetMs
        val off = zone.getOffset(window * WINDOW_MS).toLong()
        cached = Cached(window, off)
        return off
    }

    /** Wall-clock milliseconds: [utcMs] shifted so UTC formatting prints this zone's time. */
    fun toLocal(utcMs: Long): Long = utcMs + offsetAt(utcMs)

    private fun inDaylight(utcMs: Long): Boolean = zone.inDaylightTime(java.util.Date(utcMs))

    /** Abbreviation in effect at [utcMs], e.g. "EDT" in July and "EST" in January. */
    fun abbreviationAt(utcMs: Long): String =
        if (daylightAbbr != null && inDaylight(utcMs)) daylightAbbr else standardAbbr

    /** "UTC-4" style offset in effect at [utcMs]. */
    fun offsetLabelAt(utcMs: Long): String {
        val minutes = (offsetAt(utcMs) / 60_000L).toInt()
        if (minutes == 0) return "UTC+0"
        val sign = if (minutes < 0) "\u2212" else "+"
        val abs = Math.abs(minutes)
        val h = abs / 60
        val m = abs % 60
        return "UTC" + sign + h + (if (m != 0) ":" + (if (m < 10) "0$m" else "$m") else "")
    }

    companion object {
        private const val WINDOW_MS = 15 * 60_000L
    }
}

object ChartTimeZoneStore {
    private const val PREFS = "auto_rr.ui"
    private const val KEY = "chart.timezone"

    fun load(ctx: Context): ChartTimeZone {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ChartTimeZone.values().firstOrNull { it.name == name } ?: ChartTimeZone.UTC
    }

    fun save(ctx: Context, tz: ChartTimeZone) {
        // commit(): tiny write, and the choice must not be lost if the app is killed right after.
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tz.name).commit()
    }
}

@Composable
fun TimeZonePickerDialog(
    selected: ChartTimeZone,
    nowUtcMs: Long,
    onSelect: (ChartTimeZone) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chart time zone") },
        text = {
            Column {
                Text(
                    "Changes the time axis and trade times. Stored data and session hours stay in UTC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                for (tz in ChartTimeZone.values()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tz) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = tz == selected, onClick = { onSelect(tz) })
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                tz.abbreviationAt(nowUtcMs) + "  \u00B7  " + tz.offsetLabelAt(nowUtcMs) + " now",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                tz.title + (if (tz == ChartTimeZone.EST) ", follows daylight saving" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
