package com.auto.rr.domain.model

import com.auto.rr.data.local.CandleEntity

/**
 * Forex weekly trading hours, used to make candles flow seamlessly across the weekend.
 *
 * The FX market closes on Friday at 17:00 New York time and reopens on Sunday at 17:00 New York
 * time (22:00 UTC in winter, 21:00 UTC during US daylight saving). Nothing real trades in between,
 * but feeds still deliver stale quotes (flat "weekend" bars) and daily bars stamped Sunday that
 * hold only the last couple of hours before Monday.
 *
 * This is a pure, read-time view: stored data is never modified. Everything here works on UTC
 * epoch milliseconds and avoids java.time so it runs on any API level.
 */
object MarketCalendar {

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    private const val CLOSE_MINUTE_OF_DAY = 17 * 60   // 17:00 New York

    fun barMillis(tf: Timeframe): Long = when (tf) {
        Timeframe.M1 -> 60_000L
        Timeframe.M5 -> 5 * 60_000L
        Timeframe.M15 -> 15 * 60_000L
        Timeframe.H1 -> HOUR_MS
        Timeframe.H4 -> 4 * HOUR_MS
        Timeframe.D1 -> DAY_MS
    }

    /** True if the market is closed at instant [utcMs] (Fri 17:00 NY up to, not including, Sun 17:00 NY). */
    fun isClosed(utcMs: Long): Boolean {
        val local = utcMs + newYorkOffsetMs(utcMs)
        val day = Math.floorDiv(local, DAY_MS)
        val minuteOfDay = (Math.floorMod(local, DAY_MS) / 60_000L).toInt()
        return when (dayOfWeek(day)) {
            FRIDAY -> minuteOfDay >= CLOSE_MINUTE_OF_DAY
            SATURDAY -> true
            SUNDAY -> minuteOfDay < CLOSE_MINUTE_OF_DAY
            else -> false
        }
    }

    /** A bar is closed only if it lies entirely inside the closure, so the bar holding the open is kept. */
    fun isClosedBar(startMs: Long, barMs: Long): Boolean =
        isClosed(startMs) && isClosed(startMs + barMs - 1)

    /**
     * A bar that must not take a slot on the chart: closed, or a midnight-stamped daily bar for
     * Sunday (merged into Monday instead of being drawn on its own).
     */
    fun isHiddenBar(startMs: Long, barMs: Long): Boolean =
        isClosedBar(startMs, barMs) || isSundayDailyBar(startMs, barMs)

    private fun isSundayDailyBar(startMs: Long, barMs: Long): Boolean =
        barMs == DAY_MS && Math.floorMod(startMs, DAY_MS) == 0L &&
            dayOfWeek(Math.floorDiv(startMs, DAY_MS)) == SUNDAY

    /** Start of the next bar that is actually shown after the bar starting at [startMs]. */
    fun nextBarStart(startMs: Long, barMs: Long): Long {
        var t = startMs + barMs
        while (isHiddenBar(t, barMs)) t += barMs
        return t
    }

    /** Start of the previous bar that is actually shown before the bar starting at [startMs]. */
    fun prevBarStart(startMs: Long, barMs: Long): Long {
        var t = startMs - barMs
        while (isHiddenBar(t, barMs)) t -= barMs
        return t
    }

    /**
     * Drops bars that fall entirely inside the weekend closure and, for daily charts, folds a
     * Sunday bar into the following Monday (open from Sunday, high/low spanning both, Monday's
     * close). A trailing Sunday bar with nothing after it yet is kept so the live bar still shows.
     */
    fun collapseWeekends(candles: List<CandleEntity>, timeframe: Timeframe): List<CandleEntity> {
        if (candles.isEmpty()) return candles
        val barMs = barMillis(timeframe)
        val out = ArrayList<CandleEntity>(candles.size)
        var pending: CandleEntity? = null

        for (c in candles) {
            if (isSundayDailyBar(c.timestamp, barMs)) {
                pending = merge(pending, c)
                continue
            }
            if (isClosedBar(c.timestamp, barMs)) continue
            val p = pending
            if (p != null) {
                out += c.copy(
                    open = p.open,
                    high = maxOf(p.high, c.high),
                    low = minOf(p.low, c.low),
                    volume = p.volume + c.volume
                )
                pending = null
            } else {
                out += c
            }
        }
        pending?.let { out += it }
        return out
    }

    /** Folds [next] into [first]: open of the first, close of the last, widest high/low. */
    private fun merge(first: CandleEntity?, next: CandleEntity): CandleEntity =
        if (first == null) next
        else next.copy(
            timestamp = first.timestamp,
            open = first.open,
            high = maxOf(first.high, next.high),
            low = minOf(first.low, next.low),
            volume = first.volume + next.volume
        )

    // ---------------------------------------------------------------- calendar maths

    private const val SUNDAY = 0
    private const val FRIDAY = 5
    private const val SATURDAY = 6

    /** Day of week for a day count since 1970-01-01 (a Thursday); 0 = Sunday ... 6 = Saturday. */
    internal fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 4, 7L).toInt()

    /** UTC offset of New York at [utcMs]: -5h, or -4h during US daylight saving. */
    internal fun newYorkOffsetMs(utcMs: Long): Long {
        val year = civilYear(Math.floorDiv(utcMs, DAY_MS))
        // DST: second Sunday of March 02:00 EST (07:00 UTC) until first Sunday of November 02:00 EDT (06:00 UTC).
        val start = (nthSunday(year, 3, 2)) * DAY_MS + 7 * HOUR_MS
        val end = (nthSunday(year, 11, 1)) * DAY_MS + 6 * HOUR_MS
        return if (utcMs in start until end) -4 * HOUR_MS else -5 * HOUR_MS
    }

    /** Epoch day of the [n]th Sunday of [month] in [year]. */
    private fun nthSunday(year: Int, month: Int, n: Int): Long {
        val first = daysFromCivil(year, month, 1)
        val firstSunday = first + Math.floorMod(7 - dayOfWeek(first), 7)
        return firstSunday + 7L * (n - 1)
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    private fun civilYear(epochDay: Long): Int {
        val z = epochDay + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val m = if (mp < 10) mp + 3 else mp - 9
        return (if (m <= 2) y + 1 else y).toInt()
    }
}

/**
 * Timestamps for bar indices beyond the loaded data (the empty space left and right of the
 * candles), counted in *shown* bars so labels skip the weekend exactly like real candles do.
 * Cursors cache their position, so walking indices in order costs O(1) per step.
 */
class BarClock(private val candles: List<CandleEntity>, private val barMs: Long) {
    private var rightIdx = candles.size - 1
    private var rightTs = candles.lastOrNull()?.timestamp ?: 0L
    private var leftIdx = 0
    private var leftTs = candles.firstOrNull()?.timestamp ?: 0L

    fun timeAt(i: Int): Long = when {
        candles.isEmpty() -> 0L
        i < 0 -> {
            while (leftIdx > i) { leftTs = MarketCalendar.prevBarStart(leftTs, barMs); leftIdx-- }
            while (leftIdx < i) { leftTs = MarketCalendar.nextBarStart(leftTs, barMs); leftIdx++ }
            leftTs
        }
        i >= candles.size -> {
            while (rightIdx < i) { rightTs = MarketCalendar.nextBarStart(rightTs, barMs); rightIdx++ }
            while (rightIdx > i) { rightTs = MarketCalendar.prevBarStart(rightTs, barMs); rightIdx-- }
            rightTs
        }
        else -> candles[i].timestamp
    }
}
