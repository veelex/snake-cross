package com.auto.rr.ui.chart

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auto.rr.ui.theme.AppFonts
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory
import com.auto.rr.ui.status.StatusEvent
import com.auto.rr.ui.status.StatusLevel
import com.auto.rr.ui.status.StatusState
import com.auto.rr.ui.status.formatLog
import com.auto.rr.ui.theme.AppStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class LogFilter(val label: String, val matches: (StatusEvent) -> Boolean) {
    ALL("All", { true }),
    PROBLEMS("Problems", { it.level == StatusLevel.WARN || it.level == StatusLevel.ERR }),
    CONNECTION("Connection", { it.category == StatusCategory.CONNECTION }),
    DATA("Data", { it.category == StatusCategory.DATA }),
    YOU("Your actions", { it.category == StatusCategory.YOU })
}

private sealed interface LogItem {
    data class Header(val label: String) : LogItem
    data class Entry(val event: StatusEvent) : LogItem
}

/**
 * Activity log, hosted inside [ChartBottomSheet] like the Indicators / Tune / Trades sheets.
 *
 * Top to bottom: a plain-language health summary, filter chips, Copy / Share / Clear, then the
 * entries newest-first and grouped by day. Warnings and errors always show what to do about them;
 * tap an entry to reveal the raw technical detail.
 */
@Composable
fun StatusLogContent(
    state: StatusState,
    events: List<StatusEvent>
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var confirmClear by remember { mutableStateOf(false) }

    val problems = remember(events) {
        events.count { it.level == StatusLevel.WARN || it.level == StatusLevel.ERR }
    }
    val items = remember(events, filter) {
        groupByDay(events.asReversed().filter(filter.matches))
    }
    val shownCount = remember(items) { items.count { it is LogItem.Entry } }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusSummaryCard(state = state, events = events)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (f in LogFilter.values()) {
                val label = if (f == LogFilter.PROBLEMS && problems > 0) f.label + " (" + problems + ")" else f.label
                ToolChip(label = label, active = filter == f) { filter = f; confirmClear = false }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (filter == LogFilter.ALL) events.size.toString() + " entries"
                else "$shownCount of ${events.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            TextButton(enabled = events.isNotEmpty(), onClick = {
                clipboard.setText(AnnotatedString(formatLog(events, state)))
                StatusBus.info("Copied the activity log to the clipboard", detail = events.size.toString() + " entries")
            }) { Text("Copy") }
            TextButton(enabled = events.isNotEmpty(), onClick = {
                val text = formatLog(events, state)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Snake Cross activity log")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                StatusBus.info("Opened the share sheet for the activity log", detail = events.size.toString() + " entries")
                context.startActivity(Intent.createChooser(intent, "Share activity log"))
            }) { Text("Share") }
            TextButton(enabled = events.isNotEmpty(), onClick = {
                if (confirmClear) {
                    StatusBus.clear()
                    StatusBus.info("Activity log cleared")
                    expanded = emptySet()
                    confirmClear = false
                } else {
                    confirmClear = true
                }
            }) {
                Text(
                    if (confirmClear) "Confirm clear" else "Clear",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (items.isEmpty()) {
            EmptyLog(filter)
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = items,
                    key = { if (it is LogItem.Header) "h:" + it.label else "e:" + (it as LogItem.Entry).event.id }
                ) { item ->
                    when (item) {
                        is LogItem.Header -> Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                        is LogItem.Entry -> LogRow(
                            event = item.event,
                            expanded = item.event.id in expanded,
                            onToggle = {
                                val id = item.event.id
                                expanded = if (id in expanded) expanded - id else expanded + id
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------- summary

@Composable
private fun StatusSummaryCard(state: StatusState, events: List<StatusEvent>) {
    val palette = AppStatus.colors
    val (color, title, detail) = when (state) {
        is StatusState.Ok -> Triple(palette.ok, "Everything is working", "Live prices and saved history look healthy.")
        is StatusState.Syncing -> Triple(palette.sync, "Updating data", "Fetching the latest prices. This only takes a moment.")
        is StatusState.Warning -> Triple(palette.warn, "Needs attention", listOfNotNull(state.msg, state.hint).joinToString("\n"))
        is StatusState.Error -> Triple(palette.err, "Something is wrong", listOfNotNull(state.msg, state.hint).joinToString("\n"))
        is StatusState.Testing -> Triple(palette.test, "Test running", state.name)
    }
    val warnings = events.count { it.level == StatusLevel.WARN }
    val errors = events.count { it.level == StatusLevel.ERR }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (warnings + errors > 0) {
                    Text(
                        text = buildList {
                            if (errors > 0) add(if (errors == 1) "1 error" else "$errors errors")
                            if (warnings > 0) add(if (warnings == 1) "1 warning" else "$warnings warnings")
                        }.joinToString(" · ") + " in this log",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- rows

@Composable
private fun LogRow(event: StatusEvent, expanded: Boolean, onToggle: () -> Unit) {
    val color = levelColor(event.level)
    val problem = event.level == StatusLevel.WARN || event.level == StatusLevel.ERR
    val hasDetail = event.msg != event.title
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (problem) color.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = hasDetail, onClickLabel = if (expanded) "Hide details" else "Show details", onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph(event.level), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeText(event.at) + " · " + event.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.count > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "×" + event.count,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (!event.hint.isNullOrBlank()) {
                Text(
                    text = event.hint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (hasDetail) {
                if (expanded) {
                    Text(
                        text = event.msg,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = AppFonts.Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
                Text(
                    text = if (expanded) "Hide details" else "Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLog(filter: LogFilter) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = when (filter) {
                LogFilter.ALL -> "Nothing here yet. Connection, data and activity messages will show up as you use the app."
                LogFilter.PROBLEMS -> "No problems. Warnings and errors will appear here if something goes wrong."
                LogFilter.CONNECTION -> "No connection events yet."
                LogFilter.DATA -> "No market-data events yet."
                LogFilter.YOU -> "Nothing yet. Saved presets, timeframe changes and exports are listed here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------- helpers

@Composable
private fun levelColor(level: StatusLevel): Color {
    val p = AppStatus.colors
    return when (level) {
        StatusLevel.OK -> p.ok
        StatusLevel.SYNC -> p.sync
        StatusLevel.WARN -> p.warn
        StatusLevel.ERR -> p.err
        StatusLevel.TEST -> p.test
        StatusLevel.INFO -> MaterialTheme.colorScheme.primary
    }
}

private fun glyph(level: StatusLevel): String = when (level) {
    StatusLevel.OK -> "\u2713"
    StatusLevel.SYNC -> "\u21BB"
    StatusLevel.WARN -> "!"
    StatusLevel.ERR -> "\u2715"
    StatusLevel.TEST -> "T"
    StatusLevel.INFO -> "i"
}

private fun timeText(at: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))

/** Inserts "Today" / "Yesterday" / date headers into a newest-first list. */
private fun groupByDay(events: List<StatusEvent>): List<LogItem> {
    if (events.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val out = ArrayList<LogItem>(events.size + 4)
    var lastLabel: String? = null
    for (e in events) {
        val label = dayLabel(e.at, now)
        if (label != lastLabel) {
            out += LogItem.Header(label)
            lastLabel = label
        }
        out += LogItem.Entry(e)
    }
    return out
}

private fun dayLabel(at: Long, now: Long): String {
    val tz = TimeZone.getDefault()
    fun dayIndex(t: Long) = Math.floorDiv(t + tz.getOffset(t), 86_400_000L)
    return when (dayIndex(now) - dayIndex(at)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
    }
}
