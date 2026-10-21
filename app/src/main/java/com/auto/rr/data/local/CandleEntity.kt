package com.auto.rr.data.local

/**
 * One OHLCV bar for a single timeframe. Primary key = (timestamp, timeframe).
 * [timestamp] is epoch milliseconds, always UTC.
 */
data class CandleEntity(
    val timestamp: Long,
    val timeframe: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)
