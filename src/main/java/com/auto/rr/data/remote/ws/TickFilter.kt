package com.auto.rr.data.remote.ws

import kotlin.math.abs

/**
 * Drops bad ticks before they reach the M1 aggregator.
 *
 * A tick that jumps more than [maxJumpFraction] away from the last accepted price
 * is held back; it is only accepted if the *next* [confirmTicks]-1 ticks agree with
 * it (a real level shift). A lone stray print is therefore discarded and can never
 * become a candle high/low.
 */
class TickFilter(
    private val maxJumpFraction: Double = 3.0e-4,
    private val confirmTicks: Int = 2,
    private val maxFutureSkewMs: Long = 120_000L
) {
    private var last = Double.NaN
    private var pending = Double.NaN
    private var pendingCount = 0

    var rejected: Long = 0L
        private set

    /** Seed with a trusted price (e.g. the last stored close) so the very first tick is also checked. */
    fun seed(price: Double) {
        if (price.isFinite() && price > 0.0) {
            last = price
            clearPending()
        }
    }

    fun accept(tick: Tick, nowMs: Long): Boolean {
        val p = tick.price
        if (!p.isFinite() || p <= 0.0 || tick.timestamp > nowMs + maxFutureSkewMs) return reject()

        if (!last.isFinite()) {
            last = p
            return true
        }
        if (isNear(p, last)) {
            last = p
            clearPending()
            return true
        }
        if (pending.isFinite() && isNear(p, pending)) {
            pendingCount++
            if (pendingCount >= confirmTicks) {
                last = p
                clearPending()
                return true
            }
            return reject()
        }
        pending = p
        pendingCount = 1
        if (confirmTicks <= 1) {
            last = p
            clearPending()
            return true
        }
        return reject()
    }

    private fun isNear(a: Double, b: Double) = abs(a - b) <= b * maxJumpFraction

    private fun clearPending() {
        pending = Double.NaN
        pendingCount = 0
    }

    private fun reject(): Boolean {
        rejected++
        return false
    }
}
