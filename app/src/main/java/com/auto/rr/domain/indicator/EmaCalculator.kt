package com.auto.rr.domain.indicator

import com.auto.rr.data.local.CandleEntity

object EmaCalculator {

    fun ema(values: DoubleArray, period: Int): DoubleArray {
        val n = values.size
        val out = DoubleArray(n) { Double.NaN }
        if (period <= 0 || n < period) return out

        var sum = 0.0
        for (i in 0 until period) sum += values[i]
        var prev = sum / period
        out[period - 1] = prev

        val k = 2.0 / (period + 1)
        for (i in period until n) {
            prev = values[i] * k + prev * (1.0 - k)
            out[i] = prev
        }
        return out
    }

    fun emasFor(candles: List<CandleEntity>, periods: List<Int>): Map<Int, DoubleArray> {
        if (candles.isEmpty()) return periods.associateWith { DoubleArray(0) }
        val closes = DoubleArray(candles.size) { candles[it].close }
        return periods.associateWith { ema(closes, it) }
    }
}
