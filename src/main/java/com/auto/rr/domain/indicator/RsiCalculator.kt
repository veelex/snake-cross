package com.auto.rr.domain.indicator

/**
 * Wilder's Relative Strength Index.
 *
 * Warm-up values (indices 0 until period) are NaN. From index [period] onward
 * the array is filled using Wilder's smoothing of average gain and average loss.
 * Returns an all-NaN array when there are fewer than period + 1 samples.
 */
object RsiCalculator {

    fun rsi(values: DoubleArray, period: Int = 14): DoubleArray {
        val n = values.size
        val out = DoubleArray(n) { Double.NaN }
        if (period <= 0 || n < period + 1) return out

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = values[i] - values[i - 1]
            if (change >= 0.0) gainSum += change else lossSum += -change
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        out[period] = rsiFromAverages(avgGain, avgLoss)

        for (i in (period + 1) until n) {
            val change = values[i] - values[i - 1]
            val gain = if (change > 0.0) change else 0.0
            val loss = if (change < 0.0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = rsiFromAverages(avgGain, avgLoss)
        }
        return out
    }

    private fun rsiFromAverages(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return if (avgGain == 0.0) 50.0 else 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }
}
