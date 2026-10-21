package com.auto.rr.ui.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object AxisTicks {

    fun niceTicks(min: Double, max: Double, target: Int = 6): List<Double> {
        if (!min.isFinite() || !max.isFinite() || min >= max) return emptyList()
        val range = niceNum(max - min, round = false)
        val step = niceNum(range / (target - 1).coerceAtLeast(1), round = true)
        if (step <= 0.0) return emptyList()
        val niceMin = floor(min / step) * step
        val niceMax = ceil(max / step) * step
        val out = ArrayList<Double>()
        var v = niceMin
        var guard = 0
        while (v <= niceMax + step * 0.5 && guard < 200) {
            if (v >= min - step * 0.001 && v <= max + step * 0.001) out += v
            v += step
            guard++
        }
        return out
    }

    fun formatPrice(price: Double, range: Double): String {
        val decimals = when {
            range >= 100.0 -> 1
            range >= 10.0 -> 2
            range >= 1.0 -> 3
            range >= 0.1 -> 4
            else -> 5
        }
        return String.format(java.util.Locale.US, "%." + decimals + "f", price)
    }

    private fun niceNum(range: Double, round: Boolean): Double {
        if (range <= 0.0) return 1.0
        val exponent = floor(log10(range)).toInt()
        val fraction = range / 10.0.pow(exponent)
        val niceFraction = if (round) {
            when {
                fraction < 1.5 -> 1.0
                fraction < 3.0 -> 2.0
                fraction < 7.0 -> 5.0
                else -> 10.0
            }
        } else {
            when {
                fraction <= 1.0 -> 1.0
                fraction <= 2.0 -> 2.0
                fraction <= 5.0 -> 5.0
                else -> 10.0
            }
        }
        return niceFraction * 10.0.pow(exponent)
    }
}
