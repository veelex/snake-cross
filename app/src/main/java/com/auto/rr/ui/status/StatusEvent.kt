package com.auto.rr.ui.status

/** INFO is for things the user did (saved a preset, switched timeframe): logged, but never changes the status pill. */
enum class StatusLevel { OK, SYNC, WARN, ERR, TEST, INFO }

/** Lets the activity log be filtered by what an entry is about. */
enum class StatusCategory(val label: String) {
    CONNECTION("Connection"),
    DATA("Market data"),
    YOU("Your actions"),
    APP("App")
}

/**
 * One line in the activity log.
 *
 *  - [title] is the plain-language sentence shown to the user.
 *  - [hint] says what to do about it (mostly for warnings and errors).
 *  - [msg] is the raw technical detail, shown only when an entry is expanded and included when sharing.
 *  - [count] > 1 when identical consecutive entries were merged.
 */
data class StatusEvent(
    val at: Long,
    val level: StatusLevel,
    val msg: String,
    val id: Long = 0L,
    val category: StatusCategory = StatusCategory.APP,
    val title: String = msg,
    val hint: String? = null,
    val count: Int = 1
)
