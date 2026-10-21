package com.auto.rr.data.sync

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.model.Timeframe

/** Pure tick -> M1 bar state machine (no I/O, easy to test). */
class M1BarBuilder {

    class Update(
        /** The bar that just finished, when this tick opened a new minute. */
        val closed: CandleEntity?,
        val current: CandleEntity,
        val isNewBar: Boolean
    )

    var current: CandleEntity? = null
        private set

    /** Returns null for late ticks that belong to an already-closed minute. */
    fun add(timestampMs: Long, price: Double): Update? {
        val bucket = (timestampMs / MINUTE_MS) * MINUTE_MS
        val bar = current
        if (bar == null) {
            val fresh = newBar(bucket, price)
            current = fresh
            return Update(null, fresh, true)
        }
        if (bucket < bar.timestamp) return null
        if (bucket > bar.timestamp) {
            val fresh = newBar(bucket, price)
            current = fresh
            return Update(bar, fresh, true)
        }
        val updated = bar.copy(
            high = maxOf(bar.high, price),
            low = minOf(bar.low, price),
            close = price
        )
        current = updated
        return Update(null, updated, false)
    }

    /** Merge a bar already stored for the current minute (e.g. from the REST feed or a previous run). */
    fun mergeStored(stored: CandleEntity) {
        val bar = current ?: return
        if (stored.timestamp != bar.timestamp) return
        current = bar.copy(
            open = stored.open,
            high = maxOf(bar.high, stored.high),
            low = minOf(bar.low, stored.low)
        )
    }

    fun reset() {
        current = null
    }

    private fun newBar(bucket: Long, price: Double) = CandleEntity(
        timestamp = bucket,
        timeframe = Timeframe.M1.wire,
        open = price, high = price, low = price, close = price,
        volume = 0.0
    )

    companion object {
        const val MINUTE_MS = 60_000L
    }
}
