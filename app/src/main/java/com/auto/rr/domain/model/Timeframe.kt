package com.auto.rr.domain.model

/**
 * Canonical timeframes used throughout the app.
 *  - [wire] is the app-internal id (also the SQLite column value).
 *  - [providerValue] is the Twelve Data interval string.
 *  - [label] is what the UI shows.
 */
enum class Timeframe(
    val wire: String,
    val providerValue: String,
    val label: String
) {
    M1("M1", "1min", "M1"),
    M5("M5", "5min", "M5"),
    M15("M15", "15min", "M15"),
    H1("H1", "1h", "H1"),
    H4("H4", "4h", "H4"),
    D1("D1", "1day", "D1");

    companion object {
        fun fromWire(w: String): Timeframe? = values().firstOrNull { it.wire == w }
    }
}
