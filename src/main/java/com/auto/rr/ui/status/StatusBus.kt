package com.auto.rr.ui.status

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * App-wide status and activity log.
 *
 * Every call takes the technical message first (kept for support / sharing) plus optional
 * user-facing wording:
 * ```
 * StatusBus.warn("ws disconnected", title = "Live price feed disconnected",
 *     hint = "Reconnecting automatically.", category = StatusCategory.CONNECTION)
 * ```
 * Without a [title] the message itself is shown, so older call sites keep working.
 */
object StatusBus {

    private const val LOG_CAP = 300

    private val nextId = AtomicLong(1)

    private val _state = MutableStateFlow<StatusState>(StatusState.Ok)
    val state: StateFlow<StatusState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<StatusEvent>>(emptyList())
    val log: StateFlow<List<StatusEvent>> = _log.asStateFlow()

    fun ok(
        msg: String = "OK",
        title: String? = null,
        category: StatusCategory = StatusCategory.APP,
        hint: String? = null
    ) {
        _state.value = StatusState.Ok
        push(StatusLevel.OK, msg, title, category, hint)
    }

    fun syncing(
        msg: String,
        title: String? = null,
        category: StatusCategory = StatusCategory.APP,
        hint: String? = null
    ) {
        _state.value = StatusState.Syncing
        push(StatusLevel.SYNC, msg, title, category, hint)
    }

    fun warn(
        msg: String,
        title: String? = null,
        category: StatusCategory = StatusCategory.APP,
        hint: String? = null
    ) {
        _state.value = StatusState.Warning(title ?: msg, hint)
        push(StatusLevel.WARN, msg, title, category, hint)
    }

    fun error(
        msg: String,
        title: String? = null,
        category: StatusCategory = StatusCategory.APP,
        hint: String? = null
    ) {
        _state.value = StatusState.Error(title ?: msg, hint)
        push(StatusLevel.ERR, msg, title, category, hint)
    }

    fun testing(name: String) {
        _state.value = StatusState.Testing(name)
        push(StatusLevel.TEST, name, null, StatusCategory.APP, null)
    }

    /** Something the user did. Logged only: it never overwrites the current status (e.g. a standing error). */
    fun info(
        title: String,
        detail: String = "",
        category: StatusCategory = StatusCategory.YOU
    ) {
        push(StatusLevel.INFO, detail.ifEmpty { title }, title, category, null)
    }

    fun clear() {
        _log.value = emptyList()
    }

    private fun push(
        level: StatusLevel,
        msg: String,
        title: String?,
        category: StatusCategory,
        hint: String?
    ) {
        val now = System.currentTimeMillis()
        val shownTitle = title ?: msg
        _log.update { current ->
            val last = current.lastOrNull()
            // Merge identical back-to-back entries ("ws disconnected" x12) instead of flooding the list.
            if (last != null && last.level == level && last.category == category &&
                last.title == shownTitle && last.msg == msg && last.hint == hint
            ) {
                current.dropLast(1) + last.copy(at = now, count = last.count + 1)
            } else {
                (current + StatusEvent(
                    at = now, level = level, msg = msg, id = nextId.getAndIncrement(),
                    category = category, title = shownTitle, hint = hint
                )).takeLast(LOG_CAP)
            }
        }
    }
}
