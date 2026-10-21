package com.auto.rr.domain.indicator

import com.auto.rr.data.local.CandleEntity
import kotlin.math.abs

object AtrCalculator {

    fun atr(candles: List<CandleEntity>, period: Int): DoubleArray {
        val n = candles.size
        val out = DoubleArray(n) { Double.NaN }
        if (period <= 0 || n < period) return out

        val tr = DoubleArray(n)
        for (i in 0 until n) {
            val c = candles[i]
            val hl = c.high - c.low
            if (i == 0) {
                tr[i] = hl
            } else {
                val prevClose = candles[i - 1].close
                val hc = abs(c.high - prevClose)
                val lc = abs(c.low - prevClose)
                tr[i] = maxOf(hl, hc, lc)
            }
        }

        var sum = 0.0
        for (i in 0 until period) sum += tr[i]
        out[period - 1] = sum / period

        for (i in period until n) {
            out[i] = (out[i - 1] * (period - 1) + tr[i]) / period
        }
        return out
    }
}
