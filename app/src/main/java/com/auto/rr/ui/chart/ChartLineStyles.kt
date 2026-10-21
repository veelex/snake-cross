package com.auto.rr.ui.chart

import androidx.compose.ui.graphics.Color
import com.auto.rr.domain.pattern.Pattern
import com.auto.rr.domain.pattern.PatternType

/**
 * Look of one kind of line on the chart.
 *
 * @param width    stroke width in px
 * @param opacity  0..1, multiplied with whatever alpha the colour already carries
 * @param color    ARGB override, or null to use the line's automatic colour (e.g. green/red by direction)
 */
data class LineStyle(
    val width: Float,
    val opacity: Float = 1f,
    val color: Int? = null
) {
    /** Final draw colour. [auto] is used when there is no override; [alphaMul] is for dimming (e.g. forming patterns). */
    fun color(auto: Color, alphaMul: Float = 1f): Color {
        val base = if (color != null) Color(color) else auto
        return base.copy(alpha = (base.alpha * opacity * alphaMul).coerceIn(0f, 1f))
    }
}

/** How the five Elliott waves are named on the chart. */
enum class ElliottLabelFormat(val label: String) {
    NUMBERS("1 2 3"),
    PARENS("(1) (2)"),
    ROMAN("i ii iii");

    fun text(wave: Int): String = when (this) {
        NUMBERS -> wave.toString()
        PARENS -> "($wave)"
        ROMAN -> listOf("i", "ii", "iii", "iv", "v").getOrElse(wave - 1) { wave.toString() }
    }
}

/**
 * Wave labels (1-5) drawn next to the Elliott pivots.
 *
 * @param color ARGB override, or null to follow the Elliott outline colour
 * @param distance gap in px between the pivot and the label
 * @param markUnconfirmed append "?" to the last wave while the pattern is not yet confirmed
 */
data class ElliottLabelStyle(
    val show: Boolean = true,
    val format: ElliottLabelFormat = ElliottLabelFormat.NUMBERS,
    val sizeSp: Float = 10f,
    val bold: Boolean = true,
    val color: Int? = null,
    val opacity: Float = 1f,
    val distance: Float = 8f,
    val markUnconfirmed: Boolean = true
)

/**
 * Look of the risk/reward box drawn for each backtest trade.
 *
 * @param showOutline outline around the reward and risk zones (the selected trade is always outlined)
 * @param outlineWidth outline stroke width in px
 * @param outlineOpacity outline strength, 0..1
 * @param fillOpacity zone fill strength, 0..1
 * @param rewardColor ARGB override for the reward zone, or null for the bullish colour
 * @param riskColor ARGB override for the risk zone, or null for the bearish colour
 * @param showResult print the realised R inside the box and mark the exit with a dot
 */
data class RiskRewardBoxStyle(
    val showOutline: Boolean = false,
    val outlineWidth: Float = 1f,
    val outlineOpacity: Float = 0.6f,
    val fillOpacity: Float = 0.13f,
    val rewardColor: Int? = null,
    val riskColor: Int? = null,
    val showResult: Boolean = true
)

/** Every user-adjustable line on the chart. Defaults reproduce the original look. */
data class ChartLineStyles(
    // EMA ribbon: one width / opacity for all, colour per period (null = default colour)
    val ema: LineStyle = LineStyle(1.5f, 1f),
    val emaColors: Map<Int, Int> = emptyMap(),

    // Pattern outlines (the zig-zag through the pivots)
    val doubles: LineStyle = LineStyle(1.75f, 1f),
    val triples: LineStyle = LineStyle(1.75f, 1f),
    val elliott: LineStyle = LineStyle(1.75f, 1f),

    // Pattern necklines
    val doublesNeck: LineStyle = LineStyle(1.25f, 0.75f),
    val triplesNeck: LineStyle = LineStyle(1.25f, 0.75f),
    val elliottNeck: LineStyle = LineStyle(1.25f, 0.75f),

    // Entry line across the risk/reward box
    val tradePath: LineStyle = LineStyle(1f, 0.55f),

    // Risk/reward box of backtest trades (its entry line uses [tradePath])
    val riskReward: RiskRewardBoxStyle = RiskRewardBoxStyle(),

    // Chart furniture
    val grid: LineStyle = LineStyle(1f, 1f),
    val lastPrice: LineStyle = LineStyle(1f, 1f),

    // Elliott wave text labels
    val elliottLabels: ElliottLabelStyle = ElliottLabelStyle()
) {
    fun shapeFor(p: Pattern): LineStyle = when (p.type) {
        PatternType.DOUBLE_TOP, PatternType.DOUBLE_BOTTOM -> doubles
        PatternType.TRIPLE_TOP, PatternType.TRIPLE_BOTTOM -> triples
        PatternType.ELLIOTT_IMPULSE_UP, PatternType.ELLIOTT_IMPULSE_DOWN,
        PatternType.WAVE3_LONG, PatternType.WAVE3_SHORT -> elliott
    }

    fun neckFor(p: Pattern): LineStyle = when (p.type) {
        PatternType.DOUBLE_TOP, PatternType.DOUBLE_BOTTOM -> doublesNeck
        PatternType.TRIPLE_TOP, PatternType.TRIPLE_BOTTOM -> triplesNeck
        PatternType.ELLIOTT_IMPULSE_UP, PatternType.ELLIOTT_IMPULSE_DOWN,
        PatternType.WAVE3_LONG, PatternType.WAVE3_SHORT -> elliottNeck
    }

    companion object {
        val Default = ChartLineStyles()
    }
}
