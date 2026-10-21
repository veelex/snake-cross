package com.auto.rr.domain.indicator

data class SessionConfig(
    val enabled: Boolean = false,
    // All hours are UTC. [start, end) — start inclusive, end exclusive.
    val tokyoStartHour: Int = 0,
    val tokyoEndHour: Int = 9,
    val londonStartHour: Int = 8,
    val londonEndHour: Int = 17,
    val nyStartHour: Int = 13,
    val nyEndHour: Int = 22,
    /** If true, signals whose trigger bar is outside any session are skipped. */
    val filterEntries: Boolean = false,
    /** Draw each session as a box from its own low to its high (on) or as a full-height band (off). */
    val boxMode: Boolean = true,
    /** Name each box ("Tokyo", "London", "New York"; plus "live" while it is still growing). */
    val showLabels: Boolean = true,
    val showBorder: Boolean = true,
    /** Box fill strength, 0..1. */
    val fillOpacity: Float = 0.12f,
    /** Box outline strength, 0..1. */
    val borderOpacity: Float = 0.6f
) {
    companion object {
        val Default = SessionConfig()
    }

    fun isHourInSession(hour: Int): Boolean {
        if (hour in tokyoStartHour until tokyoEndHour) return true
        if (hour in londonStartHour until londonEndHour) return true
        if (hour in nyStartHour until nyEndHour) return true
        return false
    }
}
