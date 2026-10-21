package com.auto.rr.ui.status

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-text export of the activity log, newest first. Used by Copy and Share. */
fun formatLog(events: List<StatusEvent>, state: StatusState): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val sb = StringBuilder()
    sb.append("Snake Cross activity log").append('\n')
    sb.append("Exported: ").append(fmt.format(Date())).append('\n')
    sb.append("Status: ").append(statusSummary(state)).append('\n')
    sb.append("Entries: ").append(events.size).append('\n').append('\n')
    for (e in events.asReversed()) {
        sb.append(fmt.format(Date(e.at))).append("  ")
        sb.append(levelWord(e.level).padEnd(7)).append("  ")
        sb.append('[').append(e.category.label).append("]  ")
        sb.append(e.title)
        if (e.count > 1) sb.append("  (x").append(e.count).append(')')
        sb.append('\n')
        if (!e.hint.isNullOrBlank()) sb.append("    What to do: ").append(e.hint).append('\n')
        if (e.msg != e.title) sb.append("    Details: ").append(e.msg).append('\n')
    }
    return sb.toString()
}

fun levelWord(level: StatusLevel): String = when (level) {
    StatusLevel.OK -> "OK"
    StatusLevel.SYNC -> "SYNC"
    StatusLevel.WARN -> "WARNING"
    StatusLevel.ERR -> "ERROR"
    StatusLevel.TEST -> "TEST"
    StatusLevel.INFO -> "INFO"
}

fun statusSummary(state: StatusState): String = when (state) {
    is StatusState.Ok -> "Everything is working"
    is StatusState.Syncing -> "Updating data"
    is StatusState.Warning -> "Needs attention: " + state.msg
    is StatusState.Error -> "Problem: " + state.msg
    is StatusState.Testing -> "Test: " + state.name
}
