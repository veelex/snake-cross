package com.auto.rr.data.sanitize

import com.auto.rr.data.local.CandleEntity

/**
 * Removes bad prints from a series of candles before they are drawn.
 *
 * Free market-data feeds regularly contain single bars (or pairs of bars) whose
 * high/low – and sometimes open/close – sit dozens of pips away from the market.
 * They show up as the full-height "needle" candles on the lower timeframes.
 *
 * A bar is judged against its *context*, not against the whole series:
 *  - the reference range is spanned by the median close of the bars just before
 *    and just after it (medians, so a cluster of up to [Config.refWindow]/2 bad
 *    neighbours cannot drag the reference away);
 *  - the allowed excursion beyond that range is a multiple of the local median
 *    bar range, with an absolute floor, so quiet and volatile periods are both
 *    handled;
 *  - genuine level shifts (gaps, news steps) are kept because the reference range
 *    stretches from the "before" level to the "after" level.
 *
 * Raw data is never modified in storage – sanitising is a pure read-time pass.
 */
object CandleSanitizer {

    data class Config(
        /** Bars on each side used to build the reference closes. */
        val refWindow: Int = 5,
        /** Bars on each side used to estimate the typical bar range. */
        val scaleWindow: Int = 15,
        /** Allowed excursion beyond the reference range, in typical bar ranges. */
        val rangeMultiple: Double = 3.0,
        /** Absolute floor for the allowed excursion, as a fraction of price (1.5e-4 ≈ 1.7 pips on EURUSD). */
        val minBandFraction: Double = 1.5e-4,
        /** The first/last bar only has one-sided context, so it gets a wider band. */
        val edgeBoost: Double = 1.25
    )

    fun sanitize(candles: List<CandleEntity>, cfg: Config = Config()): List<CandleEntity> {
        val n = candles.size
        if (n == 0) return candles

        val closes = DoubleArray(n) { candles[it].close }
        val ranges = DoubleArray(n) { (candles[it].high - candles[it].low).coerceAtLeast(0.0) }
        val out = ArrayList<CandleEntity>(n)

        for (i in 0 until n) {
            val c = candles[i]
            if (!c.isFinite()) {
                // Unusable row – carry the previous bar forward so indices stay aligned.
                out += flatBar(c.timestamp, c.timeframe, out.lastOrNull()?.close ?: closes.firstFinite())
                continue
            }

            val before = medianOrNull(closes, (i - cfg.refWindow).coerceAtLeast(0), i)
            val after = medianOrNull(closes, i + 1, (i + 1 + cfg.refWindow).coerceAtMost(n))
            if (before == null && after == null) {
                out += c.normalized()
                continue
            }
            val refLo = minOf(before ?: after!!, after ?: before!!)
            val refHi = maxOf(before ?: after!!, after ?: before!!)

            val scale = medianOrNull(
                ranges,
                (i - cfg.scaleWindow).coerceAtLeast(0),
                (i + 1 + cfg.scaleWindow).coerceAtMost(n)
            ) ?: 0.0
            var band = maxOf(cfg.rangeMultiple * scale, cfg.minBandFraction * (refLo + refHi) / 2.0)
            if (before == null || after == null) band *= cfg.edgeBoost

            val lo = refLo - band
            val hi = refHi + band
            val o = c.open.coerceIn(lo, hi)
            val cl = c.close.coerceIn(lo, hi)
            val h = c.high.coerceIn(lo, hi)
            val l = c.low.coerceIn(lo, hi)
            out += c.copy(
                open = o,
                close = cl,
                high = maxOf(h, o, cl),
                low = minOf(l, o, cl)
            )
        }
        return out
    }

    private fun CandleEntity.isFinite(): Boolean =
        open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite() &&
            open > 0.0 && high > 0.0 && low > 0.0 && close > 0.0

    private fun CandleEntity.normalized(): CandleEntity =
        copy(high = maxOf(high, open, close), low = minOf(low, open, close))

    private fun flatBar(ts: Long, tf: String, price: Double) = CandleEntity(ts, tf, price, price, price, price, 0.0)

    private fun DoubleArray.firstFinite(): Double = firstOrNull { it.isFinite() && it > 0.0 } ?: 1.0

    /** Median of [from, to) ignoring non-finite values; null if the slice is empty. */
    private fun medianOrNull(values: DoubleArray, from: Int, to: Int): Double? {
        if (to <= from) return null
        val tmp = ArrayList<Double>(to - from)
        for (k in from until to) if (values[k].isFinite()) tmp += values[k]
        if (tmp.isEmpty()) return null
        tmp.sort()
        val m = tmp.size / 2
        return if (tmp.size % 2 == 1) tmp[m] else (tmp[m - 1] + tmp[m]) / 2.0
    }
}
