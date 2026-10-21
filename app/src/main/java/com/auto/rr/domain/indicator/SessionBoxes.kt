package com.auto.rr.domain.indicator

import com.auto.rr.data.local.CandleEntity

enum class SessionKind(val label: String) {
    TOKYO("Tokyo"),
    LONDON("London"),
    NEW_YORK("New York")
}

/**
 * One trading session on one day, as a box.
 *
 * @param startIndex first candle inside the session
 * @param endIndex   last candle inside the session so far (for a live session: the newest candle)
 * @param high       highest high of those candles
 * @param low        lowest low of those candles
 * @param live       true while the session is still running, i.e. the box is still growing
 */
data class SessionBox(
    val kind: SessionKind,
    val startIndex: Int,
    val endIndex: Int,
    val high: Double,
    val low: Double,
    val live: Boolean
)

/**
 * Groups candles into per-session boxes. Hours are UTC, start inclusive, end exclusive, and a
 * session is one box per UTC day. Because the boxes are rebuilt from the candle list, a box for
 * the running session grows (and its high / low move) as new candles arrive.
 */
object SessionBoxes {

    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L

    private class Builder(val day: Long, val start: Int, var end: Int, var high: Double, var low: Double)

    /**
     * @param nowMs used to decide whether the session at the end of the data is genuinely live
     *   (data recent enough) or just the last session before the data stops (market closed, stale feed).
     */
    fun compute(
        candles: List<CandleEntity>,
        cfg: SessionConfig,
        nowMs: Long = System.currentTimeMillis()
    ): List<SessionBox> {
        if (candles.isEmpty() || !cfg.enabled) return emptyList()

        val kinds = listOf(
            Triple(SessionKind.TOKYO, cfg.tokyoStartHour, cfg.tokyoEndHour),
            Triple(SessionKind.LONDON, cfg.londonStartHour, cfg.londonEndHour),
            Triple(SessionKind.NEW_YORK, cfg.nyStartHour, cfg.nyEndHour)
        ).filter { it.second < it.third }

        val lastIdx = candles.size - 1
        val barMs = if (lastIdx > 0) (candles[lastIdx].timestamp - candles[lastIdx - 1].timestamp)
            .coerceIn(60_000L, DAY_MS) else HOUR_MS
        val fresh = nowMs - candles[lastIdx].timestamp <= 2 * barMs + 60_000L

        val out = ArrayList<SessionBox>()
        val cur = arrayOfNulls<Builder>(kinds.size)

        fun close(k: Int, live: Boolean) {
            val b = cur[k] ?: return
            out += SessionBox(kinds[k].first, b.start, b.end, b.high, b.low, live)
            cur[k] = null
        }

        for (i in candles.indices) {
            val c = candles[i]
            val day = Math.floorDiv(c.timestamp, DAY_MS)
            val hour = Math.floorMod(Math.floorDiv(c.timestamp, HOUR_MS), 24L).toInt()
            for (k in kinds.indices) {
                val inSession = hour >= kinds[k].second && hour < kinds[k].third
                val b = cur[k]
                if (b != null && (!inSession || b.day != day)) close(k, live = false)
                if (inSession) {
                    val open = cur[k]
                    if (open == null) cur[k] = Builder(day, i, i, c.high, c.low)
                    else {
                        open.end = i
                        if (c.high > open.high) open.high = c.high
                        if (c.low < open.low) open.low = c.low
                    }
                }
            }
        }
        // Whatever is still open runs up to the newest candle: that is the live session.
        for (k in kinds.indices) close(k, live = fresh)

        return out.sortedBy { it.startIndex }
    }
}
