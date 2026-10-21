package com.auto.rr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ChartColors(
    val bullish: Color,
    val bearish: Color,
    val ema1: Color,
    val ema5: Color,
    val ema8: Color,
    val ema13: Color,
    val ema55: Color,
    val fvgBullish: Color,
    val fvgBearish: Color,
    val sessionTokyo: Color,
    val sessionLondon: Color,
    val sessionNewYork: Color,
    val spiral: Color,
    val gridLine: Color,
    val axisLabel: Color
)

@Composable
fun rememberChartColors(isDark: Boolean): ChartColors = if (isDark) {
    ChartColors(
        bullish = Color(0xFF4DB6AC),
        bearish = Color(0xFFE57373),
        ema1 = Color(0xFF90CAF9),
        ema5 = Color(0xFFA5D6A7),
        ema8 = Color(0xFFFFF59D),
        ema13 = Color(0xFFFFAB91),
        ema55 = Color(0xFFB0BEC5),
        fvgBullish = Color(0x334DB6AC),
        fvgBearish = Color(0x33E57373),
        sessionTokyo = Color(0x1AFF9800),
        sessionLondon = Color(0x1A2196F3),
        sessionNewYork = Color(0x1A4CAF50),
        spiral = Color(0xFFFFC107),
        gridLine = Color(0x14FFFFFF),
        axisLabel = Color(0xFF9A978E)
    )
} else {
    ChartColors(
        bullish = Color(0xFF00897B),
        bearish = Color(0xFFD32F2F),
        ema1 = Color(0xFF1565C0),
        ema5 = Color(0xFF2E7D32),
        ema8 = Color(0xFFF57F17),
        ema13 = Color(0xFFD84315),
        ema55 = Color(0xFF37474F),
        fvgBullish = Color(0x2600897B),
        fvgBearish = Color(0x26D32F2F),
        sessionTokyo = Color(0x14FF9800),
        sessionLondon = Color(0x142196F3),
        sessionNewYork = Color(0x144CAF50),
        spiral = Color(0xFFB26A00),
        gridLine = Color(0x1A3B3520),
        axisLabel = Color(0xFF5E5B52)
    )
}
