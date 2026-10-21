package com.auto.rr.ui.chart

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.model.BarClock
import com.auto.rr.domain.model.Timeframe
import kotlin.math.ceil
import kotlin.math.floor

/** One labelled position on the time axis. [index] is the bar index the tick sits on. */
data class TimeTick(
    val index: Int,
    val label: String,
    /** Boundary tick (new day / month / year) - drawn bolder than regular ticks. */
    val major: Boolean
)

/**
 * Builds a "proper" time axis: ticks land on round UTC times (full hours, midnights, Mondays,
 * month starts ...) instead of at fixed pixel offsets, the step adapts to the zoom level, and
 * boundaries (new day / month / year) are called out. Because the chart X-axis is bar-index
 * based (weekend gaps are collapsed), a tick is placed on the first bar at/after its time.
 * Areas beyond the loaded data are labelled by extrapolating the nominal bar duration, skipping
 * the weekend closure so labels match where real candles would appear.
 */
object TimeAxisTicks {

    private class Step(val ms: Long, val months: Int = 0, val offsetMs: Long = 0L) {
        /** Approximate duration, used only to choose a step for the current zoom. */
        val approxMs: Long get() = if (months > 0) months * 30L * TimeAxisFormatter.DAY_MS else ms
    }

    private const val D = TimeAxisFormatter.DAY_MS
    private const val H = TimeAxisFormatter.HOUR_MS
    private const val M = TimeAxisFormatter.MINUTE_MS

    private val LADDER = listOf(
        Step(1 * M), Step(2 * M), Step(5 * M), Step(10 * M), Step(15 * M), Step(30 * M),
        Step(1 * H), Step(2 * H), Step(3 * H), Step(4 * H), Step(6 * H), Step(12 * H),
        Step(1 * D),
        Step(7 * D, offsetMs = 3 * D),   // epoch day 0 is a Thursday -> shift so weeks start Monday
        Step(D, months = 1),
        Step(D, months = 3),
        Step(D, months = 12)
    )

    fun compute(
        candles: List<CandleEntity>,
        transform: ChartTransform,
        viewWidthPx: Float,
        timeframe: Timeframe,
        minSpacingPx: Float,
        zone: ChartTimeZone = ChartTimeZone.UTC
    ): List<TimeTick> {
        if (candles.isEmpty() || viewWidthPx <= 0f || transform.xScale <= 0f) return emptyList()

        val barMs = TimeAxisFormatter.barMillis(timeframe)
        val clock = BarClock(candles, barMs)
        val step = chooseStep(barMs, transform.xScale, minSpacingPx)

        val firstIdx = floor(transform.xStart).toInt() - 1
        val lastIdx = ceil(transform.xStart + viewWidthPx / transform.xScale).toInt()
        if (lastIdx <= firstIdx) return emptyList()

        val out = ArrayList<TimeTick>()
        var prevBucket = bucketOf(zone.toLocal(clock.timeAt(firstIdx)), step)
        var prevStart = bucketStart(prevBucket, step)

        for (i in (firstIdx + 1)..lastIdx) {
            val bucket = bucketOf(zone.toLocal(clock.timeAt(i)), step)
            // Only move forward: when clocks go back an hour, local time repeats and would
            // otherwise produce duplicate / out-of-order ticks.
            if (bucket <= prevBucket) continue
            val start = bucketStart(bucket, step)
            out += makeTick(i, start, prevStart, step)
            prevBucket = bucket
            prevStart = start
        }
        return out
    }

    private fun chooseStep(barMs: Long, xScale: Float, minSpacingPx: Float): Step {
        for (s in LADDER) {
            if (s.approxMs.toDouble() / barMs.toDouble() * xScale >= minSpacingPx) return s
        }
        return LADDER.last()
    }

    private fun bucketOf(t: Long, s: Step): Long {
        if (s.months <= 0) return Math.floorDiv(t + s.offsetMs, s.ms)
        val ymd = TimeAxisFormatter.ymd(t)
        val monthIndex = TimeAxisFormatter.year(ymd).toLong() * 12 + (TimeAxisFormatter.month(ymd) - 1)
        return Math.floorDiv(monthIndex, s.months.toLong())
    }

    private fun bucketStart(bucket: Long, s: Step): Long {
        if (s.months <= 0) return bucket * s.ms - s.offsetMs
        val monthIndex = bucket * s.months
        val year = Math.floorDiv(monthIndex, 12L).toInt()
        val month = Math.floorMod(monthIndex, 12L).toInt() + 1
        return TimeAxisFormatter.epochMsOf(year, month, 1)
    }

    private fun makeTick(index: Int, start: Long, prevStart: Long, s: Step): TimeTick {
        val ymd = TimeAxisFormatter.ymd(start)
        val prev = TimeAxisFormatter.ymd(prevStart)
        val newYear = TimeAxisFormatter.year(ymd) != TimeAxisFormatter.year(prev)
        val newMonth = newYear || TimeAxisFormatter.month(ymd) != TimeAxisFormatter.month(prev)
        val newDay = newMonth || TimeAxisFormatter.day(ymd) != TimeAxisFormatter.day(prev)

        return when {
            // Calendar steps: year -> "2026", otherwise month name.
            s.months > 0 ->
                if (newYear || s.months >= 12) TimeTick(index, TimeAxisFormatter.year(ymd).toString(), true)
                else TimeTick(index, TimeAxisFormatter.monthName(TimeAxisFormatter.month(ymd)), false)

            // Day / week steps: day number, month name on a new month, year on a new year.
            s.ms >= D ->
                when {
                    newYear -> TimeTick(index, TimeAxisFormatter.year(ymd).toString(), true)
                    newMonth -> TimeTick(index, TimeAxisFormatter.monthName(TimeAxisFormatter.month(ymd)), true)
                    else -> TimeTick(index, TimeAxisFormatter.day(ymd).toString(), false)
                }

            // Intraday steps: HH:mm, and the date at every UTC midnight rollover.
            else ->
                if (newDay) TimeTick(index, TimeAxisFormatter.dayMonth(start), true)
                else TimeTick(index, TimeAxisFormatter.hhmm(start), false)
        }
    }
}
