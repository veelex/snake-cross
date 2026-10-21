package com.auto.rr.data.remote.dto

import com.auto.rr.data.local.CandleEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class CandleDto(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
) {
    fun toEntity(timeframe: String): CandleEntity = CandleEntity(
        timestamp = timestamp,
        timeframe = timeframe,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 0.0
    )

    companion object {
        private val INTRADAY = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        private val DAILY = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

        /** Returns null for rows that fail structural validation. */
        fun fromJsonObject(o: JSONObject): CandleDto? {
            return try {
                val raw = o.getString("datetime")
                val ts = parseUtc(raw)
                val open = o.getString("open").toDouble()
                val high = o.getString("high").toDouble()
                val low = o.getString("low").toDouble()
                val close = o.getString("close").toDouble()
                if (!isValid(open, high, low, close)) null
                else CandleDto(ts, open, high, low, close)
            } catch (_: Throwable) {
                null
            }
        }

        private fun isValid(o: Double, h: Double, l: Double, c: Double): Boolean {
            if (!o.isFinite() || !h.isFinite() || !l.isFinite() || !c.isFinite()) return false
            if (h < l) return false
            if (h < o || h < c) return false
            if (l > o || l > c) return false
            if (o <= 0.0 || h <= 0.0 || l <= 0.0 || c <= 0.0) return false
            return true
        }

        private fun parseUtc(raw: String): Long {
            synchronized(INTRADAY) {
                try { INTRADAY.parse(raw)?.let { return it.time } } catch (_: Throwable) { }
            }
            synchronized(DAILY) {
                try { DAILY.parse(raw)?.let { return it.time } } catch (_: Throwable) { }
            }
            throw IllegalArgumentException("Unparseable date: " + raw)
        }
    }
}
