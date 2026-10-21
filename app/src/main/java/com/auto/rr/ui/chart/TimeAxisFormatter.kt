package com.auto.rr.ui.chart

import com.auto.rr.domain.model.Timeframe

/**
 * Allocation-free UTC date/time helpers for the chart's time axis.
 * (No SimpleDateFormat / java.time, so it is cheap to call every frame and works on any API level.)
 */
object TimeAxisFormatter {

    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /** Nominal duration of one bar. */
    fun barMillis(tf: Timeframe): Long = when (tf) {
        Timeframe.M1 -> MINUTE_MS
        Timeframe.M5 -> 5 * MINUTE_MS
        Timeframe.M15 -> 15 * MINUTE_MS
        Timeframe.H1 -> HOUR_MS
        Timeframe.H4 -> 4 * HOUR_MS
        Timeframe.D1 -> DAY_MS
    }

    /** Compact label for a single timestamp (UTC). */
    fun format(timestampMs: Long, timeframe: Timeframe): String = when (timeframe) {
        Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1 -> hhmm(timestampMs)
        Timeframe.H4 -> dayMonth(timestampMs) + " " + hhmm(timestampMs)
        Timeframe.D1 -> dayMonth(timestampMs)
    }

    fun hhmm(ms: Long): String {
        val minuteOfDay = Math.floorMod(ms, DAY_MS) / MINUTE_MS
        val h = (minuteOfDay / 60).toInt()
        val m = (minuteOfDay % 60).toInt()
        return pad2(h) + ":" + pad2(m)
    }

    fun dayMonth(ms: Long): String {
        val ymd = ymd(ms)
        return pad2(day(ymd)) + " " + monthName(month(ymd))
    }

    fun monthName(month: Int): String = MONTHS[(month - 1).coerceIn(0, 11)]

    private fun pad2(v: Int): String = if (v < 10) "0$v" else v.toString()

    // ---- civil calendar maths (proleptic Gregorian, UTC) ----

    /** Packs the UTC date of [ms] as yyyyMMdd. */
    fun ymd(ms: Long): Int {
        val z = Math.floorDiv(ms, DAY_MS) + 719_468L
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        var y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        if (m <= 2) y += 1
        return (y * 10_000 + m * 100 + d).toInt()
    }

    fun year(ymd: Int): Int = ymd / 10_000
    fun month(ymd: Int): Int = (ymd / 100) % 100
    fun day(ymd: Int): Int = ymd % 100

    /** Epoch milliseconds of 00:00 UTC on the given civil date. */
    fun epochMsOf(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return (era * 146_097L + doe - 719_468L) * DAY_MS
    }
}
