package com.auto.rr.ui.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.auto.rr.ui.theme.rememberChartColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import com.auto.rr.domain.indicator.EmaConfig
import com.auto.rr.domain.indicator.EmaSweepConfig
import com.auto.rr.domain.indicator.FvgConfig
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.LogSpiral
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.indicator.SpiralConfig
import com.auto.rr.domain.indicator.SpiralDirection
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.domain.pattern.ElliottPreset
import com.auto.rr.domain.pattern.ElliottStats
import com.auto.rr.domain.pattern.PatternConfig
import com.auto.rr.domain.strategy.WaveEntryTiming
import com.auto.rr.domain.strategy.WaveRetrace
import com.auto.rr.domain.strategy.WaveThreeConfig
import java.util.Locale
import kotlin.math.abs

private val TokyoAccent = Color(0xFFFF9800)
private val LondonAccent = Color(0xFF2196F3)
private val NewYorkAccent = Color(0xFF4CAF50)

/**
 * Indicator controls, hosted inside [ChartBottomSheet] so the chart stays visible while tuning.
 *
 * One collapsible card per indicator. Each card's header carries the on/off switch and a one-line
 * description; options inside are grouped, and every option has a hint saying what it does and
 * which direction to turn it.
 */
@Composable
fun IndicatorTuningContent(
    timeframe: Timeframe,
    emaConfig: EmaConfig,
    fvgConfig: FvgConfig,
    pivotConfig: PivotConfig,
    sessionConfig: SessionConfig,
    patternConfig: PatternConfig,
    spiralConfig: SpiralConfig,
    sweepConfig: EmaSweepConfig,
    waveConfig: WaveThreeConfig,
    waveSetups: Int,
    elliottStats: ElliottStats?,
    elliottShown: Int,
    lineStyles: ChartLineStyles,
    onEmaChange: (EmaConfig) -> Unit,
    onFvgChange: (FvgConfig) -> Unit,
    onPivotChange: (PivotConfig) -> Unit,
    onSessionChange: (SessionConfig) -> Unit,
    onPatternChange: (PatternConfig) -> Unit,
    onSpiralChange: (SpiralConfig) -> Unit,
    onSweepChange: (EmaSweepConfig) -> Unit,
    onWaveChange: (WaveThreeConfig) -> Unit,
    onLineStylesChange: (ChartLineStyles) -> Unit
) {
    // Only the options shown in the UI take part in "modified?" / "reset" - hidden fields are left alone.
    val fvgDef = FvgConfig.Default
    val fvgReset = fvgConfig.copy(
        showMitigated = fvgDef.showMitigated,
        minGapFraction = fvgDef.minGapFraction,
        extensionBars = fvgDef.extensionBars
    )
    val pivotDef = PivotConfig.Default
    val pivotReset = pivotConfig.copy(strength = pivotDef.strength, prominenceAtr = pivotDef.prominenceAtr)

    val patDef = PatternConfig.defaultsFor(timeframe)
    val patternReset = patternConfig.copy(
        showDoubles = patDef.showDoubles,
        showTriples = patDef.showTriples,
        legToleranceAtr = patDef.legToleranceAtr,
        minNeckDepthAtr = patDef.minNeckDepthAtr,
        minBarsBetweenPivots = patDef.minBarsBetweenPivots,
        maxPatternBars = patDef.maxPatternBars,
        confirmWithinBars = patDef.confirmWithinBars,
        requireNecklineBreak = patDef.requireNecklineBreak,
        invalidateOnLegBreak = patDef.invalidateOnLegBreak
    )

    val elliottReset = patternConfig.withElliottPreset(ElliottPreset.STRICT, timeframe).copy(
        showElliott = patDef.showElliott,
        elliottInStrategy = patDef.elliottInStrategy
    )

    val swDef = EmaSweepConfig.Default
    val sweepReset = sweepConfig.copy(
        tradeSweeps = swDef.tradeSweeps,
        sweepBars = swDef.sweepBars,
        nearAtr = swDef.nearAtr,
        nearBars = swDef.nearBars
    )

    val waveReset = WaveThreeConfig.Default.copy(enabled = waveConfig.enabled)

    val spDef = SpiralConfig.Default
    val spiralReset = spiralConfig.copy(
        growthPerQuarterTurn = spDef.growthPerQuarterTurn,
        direction = spDef.direction,
        swingOffset = spDef.swingOffset,
        manualThroughTime = spDef.manualThroughTime,
        outwardTurns = spDef.outwardTurns,
        inwardTurns = spDef.inwardTurns,
        mirror = spDef.mirror,
        showAnchors = spDef.showAnchors,
        showQuarterMarks = spDef.showQuarterMarks,
        lineWidth = spDef.lineWidth,
        opacity = spDef.opacity,
        color = spDef.color
    )

    val cc = rememberChartColors(isSystemInDarkTheme())
    val lsDef = ChartLineStyles.Default
    val emaStylesDirty = lineStyles.ema != lsDef.ema || lineStyles.emaColors.isNotEmpty()
    val patternStylesDirty = lineStyles.doubles != lsDef.doubles || lineStyles.triples != lsDef.triples ||
        lineStyles.doublesNeck != lsDef.doublesNeck || lineStyles.triplesNeck != lsDef.triplesNeck
    val elliottStylesDirty = lineStyles.elliott != lsDef.elliott || lineStyles.elliottNeck != lsDef.elliottNeck ||
        lineStyles.elliottLabels != lsDef.elliottLabels
    val boxStylesDirty = lineStyles.riskReward != lsDef.riskReward || lineStyles.tradePath != lsDef.tradePath
    val furnitureStylesDirty = lineStyles.grid != lsDef.grid || lineStyles.lastPrice != lsDef.lastPrice

    val sesDef = SessionConfig.Default
    val sessionReset = sessionConfig.copy(
        filterEntries = sesDef.filterEntries,
        boxMode = sesDef.boxMode, showLabels = sesDef.showLabels, showBorder = sesDef.showBorder,
        fillOpacity = sesDef.fillOpacity, borderOpacity = sesDef.borderOpacity,
        tokyoStartHour = sesDef.tokyoStartHour, tokyoEndHour = sesDef.tokyoEndHour,
        londonStartHour = sesDef.londonStartHour, londonEndHour = sesDef.londonEndHour,
        nyStartHour = sesDef.nyStartHour, nyEndHour = sesDef.nyEndHour
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ---------------------------------------------------------------- EMA
        TuningCard(
            title = "EMA ribbon",
            description = "Five moving averages that time the entry (Snake Cross).",
            enabled = emaConfig.enabled,
            onEnabledChange = { onEmaChange(emaConfig.copy(enabled = it)) },
            onReset = if (emaStylesDirty) {
                { onLineStylesChange(lineStyles.copy(ema = lsDef.ema, emaColors = emptyMap())) }
            } else null,
            initiallyExpanded = false
        ) {
            SettingNote("Fixed periods: 1, 5, 8, 13 and 55. The strategy waits for the fast EMAs to " +
                "cross and bunch up around EMA 55 after a pattern confirms.")
            SettingNote("Turning this off also hides all signals and trades, because entries need the EMAs.")
            SettingGroup("Line style (all EMAs)")
            SettingSlider(
                label = "Width",
                hint = "Line thickness.",
                valueText = fmt2(lineStyles.ema.width.toDouble()) + " px",
                value = lineStyles.ema.width.toDouble(),
                min = 0.5,
                max = 5.0,
                lowHint = "thin",
                highHint = "thick",
                onChange = { onLineStylesChange(lineStyles.copy(ema = lineStyles.ema.copy(width = (Math.round(it * 4.0) / 4.0).toFloat()))) }
            )
            SettingSlider(
                label = "Opacity",
                hint = "Lower it to let candles show through.",
                valueText = (lineStyles.ema.opacity * 100).toInt().toString() + "%",
                value = lineStyles.ema.opacity.toDouble(),
                min = 0.1,
                max = 1.0,
                lowHint = "faint",
                highHint = "solid",
                onChange = { onLineStylesChange(lineStyles.copy(ema = lineStyles.ema.copy(opacity = (Math.round(it * 20.0) / 20.0).toFloat()))) }
            )
            SettingGroup("Colours")
            val emaAuto = listOf(1 to cc.ema1, 5 to cc.ema5, 8 to cc.ema8, 13 to cc.ema13, 55 to cc.ema55)
            for ((period, auto) in emaAuto) {
                SettingColor(
                    label = "EMA " + period,
                    selected = lineStyles.emaColors[period],
                    autoColor = auto,
                    onChange = { c ->
                        val m = lineStyles.emaColors.toMutableMap()
                        if (c == null) m.remove(period) else m[period] = c
                        onLineStylesChange(lineStyles.copy(emaColors = m))
                    }
                )
            }
        }

        // ---------------------------------------------------------------- EMA sweep
        TuningCard(
            title = "EMA sweep",
            description = "Price cuts through all four EMAs at once near a double or triple top or bottom.",
            enabled = sweepConfig.enabled,
            onEnabledChange = { onSweepChange(sweepConfig.copy(enabled = it)) },
            onReset = if (sweepConfig != sweepReset) { { onSweepChange(sweepReset) } } else null
        ) {
            SettingNote("A buy needs price to close above EMA 5, 8, 13 and 55 after crossing each of them " +
                "upward; a sell is the mirror image. It only counts near a double bottom, triple bottom " +
                "(buy) or double top, triple top (sell). Stop and target use the Stop & target settings.")
            SettingNote("Needs the EMA ribbon and pattern detection to be on. Elliott waves are not used.")
            SettingGroup("Strategy")
            SettingSwitch(
                label = "Trade sweeps",
                hint = "Use each sweep as a backtest entry. Off keeps them as chart markers only.",
                checked = sweepConfig.tradeSweeps,
                onChange = { onSweepChange(sweepConfig.copy(tradeSweeps = it)) }
            )
            SettingGroup("Same time")
            SettingIntSlider(
                label = "Crossing window",
                hint = "All four EMAs must be crossed within this many bars, ending on the entry bar.",
                value = sweepConfig.sweepBars,
                min = 1,
                max = 5,
                valueText = if (sweepConfig.sweepBars == 1) "1 bar" else sweepConfig.sweepBars.toString() + " bars",
                lowHint = "one candle only",
                highHint = "looser",
                onChange = { onSweepChange(sweepConfig.copy(sweepBars = it)) }
            )
            SettingGroup("Near a pattern")
            SettingSlider(
                label = "Distance",
                hint = "Entry price must be within this many ATR of the neckline or the last leg.",
                valueText = fmt2(sweepConfig.nearAtr) + " ATR",
                value = sweepConfig.nearAtr,
                min = 0.5,
                max = 5.0,
                lowHint = "right at the pattern",
                highHint = "further away",
                onChange = { onSweepChange(sweepConfig.copy(nearAtr = (Math.round(it * 4.0) / 4.0))) }
            )
            SettingIntSlider(
                label = "Time after pattern",
                hint = "How many bars after the pattern completes a sweep still counts.",
                value = sweepConfig.nearBars,
                min = 1,
                max = 30,
                valueText = sweepConfig.nearBars.toString() + " bars",
                lowHint = "fresh patterns",
                highHint = "older patterns",
                onChange = { onSweepChange(sweepConfig.copy(nearBars = it)) }
            )
        }

        // ---------------------------------------------------------------- Elliott wave 3 entry
        TuningCard(
            title = "Elliott wave 3 entry",
            description = "Waits for wave 1 and wave 2, then rests a limit order to ride wave 3.",
            enabled = waveConfig.enabled,
            onEnabledChange = { onWaveChange(waveConfig.copy(enabled = it)) },
            onReset = if (waveConfig != waveReset) { { onWaveChange(waveReset) } } else null
        ) {
            SettingNote("A strong first move (wave 1) is followed by a pullback of about 38.2% or 61.8% of it " +
                "(wave 2). The order rests at the end of wave 2: a buy limit after an up wave 1, a sell limit " +
                "after a down wave 1. Stop loss is the start of wave 1; the profit target (PTL) is the limit " +
                "price plus or minus the target multiple times wave 1.")
            SettingNote("Needs \"Swing pivots\" to be on. Stop and target here replace the Stop & target " +
                "settings for these trades. Setups on the chart: " + waveSetups + ".")
            SettingGroup("Strategy")
            SettingSwitch(
                label = "Trade wave 3 setups",
                hint = "Use each setup as a backtest trade. Off keeps them as chart drawings only.",
                checked = waveConfig.tradeSetups,
                onChange = { onWaveChange(waveConfig.copy(tradeSetups = it)) }
            )
            SettingChoice(
                label = "Place the order",
                hint = "After wave 2: wait for the pullback to finish, then buy/sell the retest. Safer, but " +
                    "price often leaves without a retest. After wave 1: the limit is already waiting while " +
                    "wave 2 falls, so it buys into wave C of the pullback, which is trading against the " +
                    "short-term trend.",
                options = WaveEntryTiming.values().map { it to it.label },
                selected = waveConfig.timing,
                onChange = { onWaveChange(waveConfig.copy(timing = it)) }
            )
            SettingGroup("Wave 2 retracement")
            SettingChoice(
                label = "Retracement",
                hint = "Which pullback of wave 1 to trade. Auto picks the closer of 38.2% and 61.8% once " +
                    "wave 2 has formed; with \"After wave 1\" it uses 38.2%.",
                options = WaveRetrace.values().map { it to it.label },
                selected = waveConfig.retrace,
                onChange = { onWaveChange(waveConfig.copy(retrace = it)) }
            )
            SettingSlider(
                label = "Tolerance",
                hint = "How far wave 2 may sit from the Fibonacci level and still count, as a share of wave 1.",
                valueText = "\u00B1" + Math.round(waveConfig.retraceTolerance * 100.0) + "%",
                value = waveConfig.retraceTolerance,
                min = 0.02,
                max = 0.15,
                lowHint = "exact levels only",
                highHint = "more setups",
                onChange = { onWaveChange(waveConfig.copy(retraceTolerance = Math.round(it * 100.0) / 100.0)) }
            )
            SettingGroup("Target and stop")
            SettingChoice(
                label = "Target (PTL)",
                hint = "Length of wave 3 as a multiple of wave 1, measured from the limit price. 1.618 is the " +
                    "textbook wave 3.",
                options = listOf(1.272, 1.618, 2.0, 2.618).map { it to fmtExt(it) },
                selected = waveConfig.targetExtension,
                onChange = { onWaveChange(waveConfig.copy(targetExtension = it)) }
            )
            SettingSlider(
                label = "Stop buffer",
                hint = "Extra room beyond the start of wave 1. 0 puts the stop exactly on it.",
                valueText = fmt2(waveConfig.stopBufferAtr) + " ATR",
                value = waveConfig.stopBufferAtr,
                min = 0.0,
                max = 2.0,
                lowHint = "exact",
                highHint = "more room",
                onChange = { onWaveChange(waveConfig.copy(stopBufferAtr = Math.round(it * 10.0) / 10.0)) }
            )
            SettingGroup("Wave 1")
            SettingSlider(
                label = "Minimum wave 1",
                hint = "Smallest size of wave 1, in ATR. Raise it to skip small swings.",
                valueText = fmt2(waveConfig.minWave1Atr) + " ATR",
                value = waveConfig.minWave1Atr,
                min = 0.5,
                max = 6.0,
                lowHint = "more setups",
                highHint = "bigger moves only",
                onChange = { onWaveChange(waveConfig.copy(minWave1Atr = Math.round(it * 4.0) / 4.0)) }
            )
            SettingSwitch(
                label = "Wave 1 must break structure",
                hint = "Wave 1 has to pass the swing before it, so it starts a new move instead of being a swing inside a range.",
                checked = waveConfig.requireStructureBreak,
                onChange = { onWaveChange(waveConfig.copy(requireStructureBreak = it)) }
            )
            SettingIntSlider(
                label = "Swing size",
                hint = "Size of the swing points the waves are built from. Raise it for bigger, slower waves.",
                value = waveConfig.pivotStrength,
                min = 0,
                max = 10,
                valueText = if (waveConfig.pivotStrength == 0) "as pivots" else waveConfig.pivotStrength.toString() + " bars",
                lowHint = "same as Swing pivots",
                highHint = "bigger swings",
                onChange = { onWaveChange(waveConfig.copy(pivotStrength = it)) }
            )
            SettingIntSlider(
                label = "Minimum bars per wave",
                hint = "Fewest bars between two wave points.",
                value = waveConfig.minBarsPerWave,
                min = 1,
                max = 10,
                valueText = waveConfig.minBarsPerWave.toString() + " bars",
                lowHint = "more setups",
                highHint = "slower waves",
                onChange = { onWaveChange(waveConfig.copy(minBarsPerWave = it)) }
            )
            SettingGroup("Order lifetime")
            SettingIntSlider(
                label = "Expires after",
                hint = "How many bars the limit order may wait for a fill.",
                value = waveConfig.expiryBars,
                min = 5,
                max = 100,
                valueText = waveConfig.expiryBars.toString() + " bars",
                lowHint = "short-lived",
                highHint = "long-lived",
                onChange = { onWaveChange(waveConfig.copy(expiryBars = it)) }
            )
            SettingSwitch(
                label = "Cancel if price runs away",
                hint = "Drop the order when price passes the end of wave 1 before it fills, because wave 3 has already started without it.",
                checked = waveConfig.cancelOnRunaway,
                onChange = { onWaveChange(waveConfig.copy(cancelOnRunaway = it)) }
            )
        }

        // ---------------------------------------------------------------- FVG
        TuningCard(
            title = "Fair value gaps",
            description = "Shaded zones where price jumped and left an imbalance.",
            enabled = fvgConfig.enabled,
            onEnabledChange = { onFvgChange(fvgConfig.copy(enabled = it)) },
            onReset = if (fvgConfig != fvgReset) { { onFvgChange(fvgReset) } } else null
        ) {
            SettingGroup("What to show")
            SettingSwitch(
                label = "Show mitigated gaps",
                hint = "Keep drawing a gap (dimmed) after price has traded back into it.",
                checked = fvgConfig.showMitigated,
                onChange = { onFvgChange(fvgConfig.copy(showMitigated = it)) }
            )
            SettingGroup("Size & length")
            val bps = fvgConfig.minGapFraction * 10_000.0
            SettingSlider(
                label = "Minimum gap",
                hint = "Smallest gap that counts, as a share of price. 1 bp \u2248 1 pip on EUR/USD.",
                valueText = fmt2(bps) + " bp",
                value = bps,
                min = 0.05,
                max = 2.0,
                lowHint = "more, smaller zones",
                highHint = "fewer, bigger zones",
                onChange = { onFvgChange(fvgConfig.copy(minGapFraction = it / 10_000.0)) }
            )
            SettingIntSlider(
                label = "Zone width",
                hint = "How many bars each zone is drawn to the right of where it formed.",
                value = fvgConfig.extensionBars,
                min = 4,
                max = 40,
                valueText = fvgConfig.extensionBars.toString() + " bars",
                lowHint = "short",
                highHint = "long",
                onChange = { onFvgChange(fvgConfig.copy(extensionBars = it)) }
            )
        }

        // ------------------------------------------------------------- Pivots
        TuningCard(
            title = "Swing pivots",
            description = "Local highs and lows. Patterns are built from these.",
            enabled = pivotConfig.enabled,
            onEnabledChange = { onPivotChange(pivotConfig.copy(enabled = it)) },
            onReset = if (pivotConfig != pivotReset) { { onPivotChange(pivotReset) } } else null
        ) {
            SettingIntSlider(
                label = "Strength",
                hint = "Bars on each side that must be lower (high) or higher (low). " +
                    "A pivot is only confirmed this many bars after it forms.",
                value = pivotConfig.strength,
                min = 1,
                max = 10,
                valueText = pivotConfig.strength.toString() + " bars",
                lowHint = "more, noisier pivots",
                highHint = "fewer, stronger pivots",
                onChange = { onPivotChange(pivotConfig.copy(strength = it)) }
            )
            SettingSlider(
                label = "Minimum swing size",
                hint = "A pivot must stand out from its neighbours by this many ATR. Lower it to catch smaller swings; " +
                    "patterns such as Elliott waves need many pivots in a row.",
                valueText = if (pivotConfig.prominenceAtr <= 0.0) "off" else fmt2(pivotConfig.prominenceAtr) + " ATR",
                value = pivotConfig.prominenceAtr,
                min = 0.0,
                max = 2.0,
                lowHint = "off, every fractal",
                highHint = "only big swings",
                onChange = { onPivotChange(pivotConfig.copy(prominenceAtr = Math.round(it * 20.0) / 20.0)) }
            )
            SettingNote("Turning pivots off also removes patterns and signals.")
        }

        // ----------------------------------------------------------- Patterns
        TuningCard(
            title = "Chart patterns",
            description = "Double / triple tops and bottoms, confirmed by a neckline break.",
            enabled = patternConfig.enabled,
            onEnabledChange = { onPatternChange(patternConfig.copy(enabled = it)) },
            onReset = if (patternConfig != patternReset || patternStylesDirty) {
                {
                    onPatternChange(patternReset)
                    onLineStylesChange(
                        lineStyles.copy(
                            doubles = lsDef.doubles, triples = lsDef.triples,
                            doublesNeck = lsDef.doublesNeck, triplesNeck = lsDef.triplesNeck
                        )
                    )
                }
            } else null
        ) {
            SettingGroup("What to detect")
            SettingSwitch(
                label = "Doubles",
                hint = "Two equal highs or lows (double top / bottom).",
                checked = patternConfig.showDoubles,
                onChange = { onPatternChange(patternConfig.copy(showDoubles = it)) }
            )
            SettingSwitch(
                label = "Triples",
                hint = "Three equal highs or lows (triple top / bottom).",
                checked = patternConfig.showTriples,
                onChange = { onPatternChange(patternConfig.copy(showTriples = it)) }
            )

            SettingGroup("Shape")
            SettingSlider(
                label = "Leg tolerance",
                hint = "How far apart the legs may be, in ATR. Smaller means the legs must line up more exactly.",
                valueText = fmt2(patternConfig.legToleranceAtr) + " ATR",
                value = patternConfig.legToleranceAtr,
                min = 0.1,
                max = 2.0,
                lowHint = "strict, cleaner",
                highHint = "loose, more patterns",
                onChange = { onPatternChange(patternConfig.copy(legToleranceAtr = it)) }
            )
            SettingSlider(
                label = "Neckline depth",
                hint = "How far the neckline must sit beyond the legs, in ATR. Filters out shallow patterns.",
                valueText = fmt2(patternConfig.minNeckDepthAtr) + " ATR",
                value = patternConfig.minNeckDepthAtr,
                min = 0.5,
                max = 6.0,
                lowHint = "shallow ok",
                highHint = "only deep patterns",
                onChange = { onPatternChange(patternConfig.copy(minNeckDepthAtr = it)) }
            )

            SettingGroup("Timing")
            SettingIntSlider(
                label = "Spacing between legs",
                hint = "Minimum bars between one leg and the next.",
                value = patternConfig.minBarsBetweenPivots,
                min = 3,
                max = 40,
                valueText = patternConfig.minBarsBetweenPivots.toString() + " bars",
                lowHint = "tight",
                highHint = "wide apart",
                onChange = { onPatternChange(patternConfig.copy(minBarsBetweenPivots = it)) }
            )
            SettingIntSlider(
                label = "Maximum width",
                hint = "Longest allowed distance from the first leg to the last.",
                value = patternConfig.maxPatternBars,
                min = 30,
                max = 300,
                valueText = patternConfig.maxPatternBars.toString() + " bars",
                lowHint = "compact only",
                highHint = "allow long patterns",
                onChange = { onPatternChange(patternConfig.copy(maxPatternBars = it)) }
            )
            SettingIntSlider(
                label = "Break window",
                hint = "After the last leg, how many bars a close beyond the neckline still confirms the pattern.",
                value = patternConfig.confirmWithinBars,
                min = 5,
                max = 100,
                valueText = patternConfig.confirmWithinBars.toString() + " bars",
                lowHint = "must break quickly",
                highHint = "can break late",
                onChange = { onPatternChange(patternConfig.copy(confirmWithinBars = it)) }
            )

            SettingGroup("Confirmation")
            SettingSwitch(
                label = "Require neckline break",
                hint = "On: only confirmed patterns. Off: also show patterns still forming (dimmed). Elliott waves have their own setting.",
                checked = patternConfig.requireNecklineBreak,
                onChange = { onPatternChange(patternConfig.copy(requireNecklineBreak = it)) }
            )
            SettingSwitch(
                label = "Drop if legs break first",
                hint = "Discard a pattern when price closes past its legs before the neckline breaks.",
                checked = patternConfig.invalidateOnLegBreak,
                onChange = { onPatternChange(patternConfig.copy(invalidateOnLegBreak = it)) }
            )

            SettingNote("Line styles below: Auto colour follows the pattern direction (bullish / bearish).")
            LineStyleControls("Double top / bottom: outline", lineStyles.doubles, cc.bullish) {
                onLineStylesChange(lineStyles.copy(doubles = it))
            }
            LineStyleControls("Double top / bottom: neckline", lineStyles.doublesNeck, cc.bullish) {
                onLineStylesChange(lineStyles.copy(doublesNeck = it))
            }
            LineStyleControls("Triple top / bottom: outline", lineStyles.triples, cc.bullish) {
                onLineStylesChange(lineStyles.copy(triples = it))
            }
            LineStyleControls("Triple top / bottom: neckline", lineStyles.triplesNeck, cc.bullish) {
                onLineStylesChange(lineStyles.copy(triplesNeck = it))
            }
        }

        // ------------------------------------------------------ Elliott waves
        val ew = lineStyles.elliottLabels
        val setLabels = { l: ElliottLabelStyle -> onLineStylesChange(lineStyles.copy(elliottLabels = l)) }
        TuningCard(
            title = "Elliott waves",
            description = "Completed 5-wave impulses (1-2-3-4-5), flagged once price closes back through the end of wave 4.",
            enabled = patternConfig.showElliott,
            onEnabledChange = { onPatternChange(patternConfig.copy(showElliott = it)) },
            onReset = if (patternConfig != elliottReset || elliottStylesDirty) {
                {
                    onPatternChange(elliottReset)
                    onLineStylesChange(
                        lineStyles.copy(
                            elliott = lsDef.elliott,
                            elliottNeck = lsDef.elliottNeck,
                            elliottLabels = lsDef.elliottLabels
                        )
                    )
                }
            } else null
        ) {
            SettingNote("Needs \"Chart patterns\" and \"Swing pivots\" to be on as well.")
            SettingGroup("Trading")
            SettingSwitch(
                label = "Trade Elliott waves",
                hint = "Off: Elliott waves are only drawn on the chart. On: a confirmed impulse can also trigger buy/sell signals and backtest trades.",
                checked = patternConfig.elliottInStrategy,
                onChange = { onPatternChange(patternConfig.copy(elliottInStrategy = it)) }
            )
            SettingGroup("Sensitivity")
            SettingChoice(
                label = "Preset",
                hint = "One tap to loosen or tighten every detection option below. Strict is the original behaviour; " +
                    "Loose finds the most waves but also the roughest.",
                options = listOf(ElliottPreset.STRICT, ElliottPreset.BALANCED, ElliottPreset.LOOSE).map { it to it.label },
                selected = patternConfig.elliottPreset(timeframe),
                onChange = { onPatternChange(patternConfig.withElliottPreset(it, timeframe)) }
            )
            if (patternConfig.elliottPreset(timeframe) == ElliottPreset.CUSTOM) {
                SettingNote("Custom: options below differ from every preset.")
            }
            SettingIntSlider(
                label = "Wave degree (swing size)",
                hint = "Size of the swing points the waves are built from. The default swing size mostly finds tiny " +
                    "moves, which rarely form a clean 5-wave impulse. Raise it to find bigger, slower waves.",
                value = patternConfig.elliottPivotStrength,
                min = 0,
                max = 20,
                valueText = if (patternConfig.elliottPivotStrength == 0) "as pivots" else patternConfig.elliottPivotStrength.toString() + " bars",
                lowHint = "same as Swing pivots",
                highHint = "bigger swings",
                onChange = { onPatternChange(patternConfig.copy(elliottPivotStrength = it)) }
            )
            SettingIntSlider(
                label = "Sizes to scan",
                hint = "1 = only the size above. 2 = also twice that size. 3 = also four times, so small and large " +
                    "impulses are found together.",
                value = patternConfig.elliottDegrees,
                min = 1,
                max = 3,
                valueText = patternConfig.elliottDegrees.toString(),
                lowHint = "one size",
                highHint = "three sizes",
                onChange = { onPatternChange(patternConfig.copy(elliottDegrees = it)) }
            )
            ElliottFunnel(elliottStats, elliottShown, patternConfig.showElliott)

            SettingGroup("Wave size")
            SettingSlider(
                label = "Minimum impulse wave",
                hint = "Smallest size of waves 1, 3 and 5, in ATR.",
                valueText = fmt2(patternConfig.elliottMinWaveAtr) + " ATR",
                value = patternConfig.elliottMinWaveAtr,
                min = 0.1,
                max = 3.0,
                lowHint = "small waves ok",
                highHint = "only large waves",
                onChange = { onPatternChange(patternConfig.copy(elliottMinWaveAtr = Math.round(it * 20.0) / 20.0)) }
            )
            SettingSlider(
                label = "Minimum correction wave",
                hint = "Smallest size of waves 2 and 4 (the pullbacks), in ATR. Pullbacks are usually smaller than the impulse waves.",
                valueText = fmt2(patternConfig.elliottMinCorrectionAtr) + " ATR",
                value = patternConfig.elliottMinCorrectionAtr,
                min = 0.0,
                max = 3.0,
                lowHint = "tiny pullbacks ok",
                highHint = "deep pullbacks only",
                onChange = { onPatternChange(patternConfig.copy(elliottMinCorrectionAtr = Math.round(it * 20.0) / 20.0)) }
            )
            SettingSlider(
                label = "Minimum total move",
                hint = "Whole impulse, start of wave 1 to end of wave 5, in pips (EUR/USD scale: 1 pip = 0.0001).",
                valueText = fmt2(patternConfig.elliottMinTotalPips) + " pips",
                value = patternConfig.elliottMinTotalPips,
                min = 0.0,
                max = 100.0,
                lowHint = "any size",
                highHint = "big moves only",
                onChange = { onPatternChange(patternConfig.copy(elliottMinTotalPips = Math.round(it).toDouble())) }
            )

            SettingGroup("Wave rules")
            SettingSlider(
                label = "Wave 2 max retrace",
                hint = "How much of wave 1 wave 2 may give back. Textbook is 50-62%; it can never pass 100%.",
                valueText = (patternConfig.elliottMaxWave2Retrace * 100).toInt().toString() + "%",
                value = patternConfig.elliottMaxWave2Retrace,
                min = 0.3,
                max = 0.99,
                lowHint = "shallow only",
                highHint = "deep ok",
                onChange = { onPatternChange(patternConfig.copy(elliottMaxWave2Retrace = Math.round(it * 100.0) / 100.0)) }
            )
            SettingSlider(
                label = "Wave 4 max retrace",
                hint = "How much of wave 3 wave 4 may give back. Textbook is about 38%; 62% is already deep.",
                valueText = (patternConfig.elliottMaxWave4Retrace * 100).toInt().toString() + "%",
                value = patternConfig.elliottMaxWave4Retrace,
                min = 0.2,
                max = 0.99,
                lowHint = "shallow only",
                highHint = "deep ok",
                onChange = { onPatternChange(patternConfig.copy(elliottMaxWave4Retrace = Math.round(it * 100.0) / 100.0)) }
            )
            SettingSwitch(
                label = "Allow truncated 5th wave",
                hint = "Accept a wave 5 that fails to pass the end of wave 3. Real impulses do this after a very strong wave 3.",
                checked = patternConfig.elliottAllowTruncated,
                onChange = { onPatternChange(patternConfig.copy(elliottAllowTruncated = it)) }
            )
            SettingSwitch(
                label = "Allow wave 4 overlap",
                hint = "Accept wave 4 dipping into wave 1's price range (diagonal-style moves). Off is the textbook rule.",
                checked = patternConfig.elliottAllowOverlap,
                onChange = { onPatternChange(patternConfig.copy(elliottAllowOverlap = it)) }
            )

            SettingGroup("Timing")
            SettingIntSlider(
                label = "Spacing between waves",
                hint = "Minimum bars from one wave end to the next.",
                value = patternConfig.elliottMinBarsPerWave,
                min = 1,
                max = 20,
                valueText = patternConfig.elliottMinBarsPerWave.toString() + " bars",
                lowHint = "tight",
                highHint = "wide apart",
                onChange = { onPatternChange(patternConfig.copy(elliottMinBarsPerWave = it)) }
            )
            SettingIntSlider(
                label = "Maximum width",
                hint = "Longest allowed distance from the start of wave 1 to the end of wave 5.",
                value = patternConfig.elliottMaxPatternBars,
                min = 30,
                max = 800,
                valueText = patternConfig.elliottMaxPatternBars.toString() + " bars",
                lowHint = "compact only",
                highHint = "allow long impulses",
                onChange = { onPatternChange(patternConfig.copy(elliottMaxPatternBars = it)) }
            )

            SettingGroup("Confirmation")
            SettingSwitch(
                label = "Require confirmation",
                hint = "On: only impulses whose reversal has started. Off: also show finished impulses still waiting (dimmed).",
                checked = patternConfig.elliottRequireBreak,
                onChange = { onPatternChange(patternConfig.copy(elliottRequireBreak = it)) }
            )
            SettingSlider(
                label = "Confirmation level",
                hint = "How much of wave 5 price must give back to confirm. 100% means all of it (a close back through the end of " +
                    "wave 4), which is rare. 38-62% is the usual first sign of a reversal.",
                valueText = (patternConfig.elliottConfirmRetrace * 100).toInt().toString() + "%",
                value = patternConfig.elliottConfirmRetrace,
                min = 0.2,
                max = 1.0,
                lowHint = "early, more waves",
                highHint = "late, fewer waves",
                onChange = { onPatternChange(patternConfig.copy(elliottConfirmRetrace = Math.round(it * 100.0) / 100.0)) }
            )
            SettingIntSlider(
                label = "Confirmation window",
                hint = "Bars after wave 5 in which the confirming close still counts.",
                value = patternConfig.elliottConfirmWithinBars,
                min = 5,
                max = 200,
                valueText = patternConfig.elliottConfirmWithinBars.toString() + " bars",
                lowHint = "must come quickly",
                highHint = "can come late",
                onChange = { onPatternChange(patternConfig.copy(elliottConfirmWithinBars = it)) }
            )
            SettingSlider(
                label = "Close beyond level by",
                hint = "The confirming close must clear the level by this many ATR, not just touch it.",
                valueText = fmt2(patternConfig.elliottConfirmOvershootAtr) + " ATR",
                value = patternConfig.elliottConfirmOvershootAtr,
                min = 0.0,
                max = 0.5,
                lowHint = "any close",
                highHint = "clear break",
                onChange = { onPatternChange(patternConfig.copy(elliottConfirmOvershootAtr = Math.round(it * 100.0) / 100.0)) }
            )
            SettingSwitch(
                label = "Drop if wave 5 keeps going",
                hint = "Discard the impulse when price closes beyond the end of wave 5 before it is confirmed.",
                checked = patternConfig.elliottInvalidateOnBreak,
                onChange = { onPatternChange(patternConfig.copy(elliottInvalidateOnBreak = it)) }
            )
            SettingSlider(
                label = "Drop distance",
                hint = "How far beyond the end of wave 5 a close must go to discard the impulse.",
                valueText = fmt2(patternConfig.elliottInvalidationAtr) + " ATR",
                value = patternConfig.elliottInvalidationAtr,
                min = 0.1,
                max = 3.0,
                lowHint = "drop quickly",
                highHint = "tolerate more",
                onChange = { onPatternChange(patternConfig.copy(elliottInvalidationAtr = Math.round(it * 10.0) / 10.0)) }
            )

            SettingGroup("Other patterns")
            SettingSwitch(
                label = "Share swing points with doubles / triples",
                hint = "Off: an Elliott wave is dropped when a double or triple already uses one of its swing points, " +
                    "which hides many waves. On: they are drawn independently.",
                checked = patternConfig.elliottOverlapOthers,
                onChange = { onPatternChange(patternConfig.copy(elliottOverlapOthers = it)) }
            )

            SettingGroup("Wave labels")
            SettingSwitch(
                label = "Show wave labels",
                hint = "Number each wave end on the chart.",
                checked = ew.show,
                onChange = { setLabels(ew.copy(show = it)) }
            )
            SettingChoice(
                label = "Label style",
                hint = "How the waves are numbered.",
                options = ElliottLabelFormat.values().map { it to it.label },
                selected = ew.format,
                onChange = { setLabels(ew.copy(format = it)) }
            )
            SettingColor(
                label = "Label colour",
                hint = "Auto follows the Elliott outline colour.",
                selected = ew.color,
                autoColor = lineStyles.elliott.color?.let { Color(it) } ?: cc.bullish,
                onChange = { setLabels(ew.copy(color = it)) }
            )
            SettingIntSlider(
                label = "Label size",
                hint = "Text size of the wave numbers.",
                value = ew.sizeSp.toInt(),
                min = 7,
                max = 20,
                valueText = ew.sizeSp.toInt().toString() + " sp",
                lowHint = "small",
                highHint = "large",
                onChange = { setLabels(ew.copy(sizeSp = it.toFloat())) }
            )
            SettingSlider(
                label = "Label opacity",
                hint = "Lower it to keep the labels from competing with the candles.",
                valueText = (ew.opacity * 100).toInt().toString() + "%",
                value = ew.opacity.toDouble(),
                min = 0.1,
                max = 1.0,
                lowHint = "faint",
                highHint = "solid",
                onChange = { setLabels(ew.copy(opacity = (Math.round(it * 20.0) / 20.0).toFloat())) }
            )
            SettingSlider(
                label = "Distance from pivot",
                hint = "Gap between the wave end and its label.",
                valueText = fmt2(ew.distance.toDouble()) + " px",
                value = ew.distance.toDouble(),
                min = 0.0,
                max = 24.0,
                lowHint = "touching",
                highHint = "far",
                onChange = { setLabels(ew.copy(distance = Math.round(it).toFloat())) }
            )
            SettingSwitch(
                label = "Bold",
                hint = "Draw the wave numbers in bold.",
                checked = ew.bold,
                onChange = { setLabels(ew.copy(bold = it)) }
            )
            SettingSwitch(
                label = "Mark unconfirmed with ?",
                hint = "Add a \"?\" to wave 5 until the neckline break confirms the impulse.",
                checked = ew.markUnconfirmed,
                onChange = { setLabels(ew.copy(markUnconfirmed = it)) }
            )

            LineStyleControls("Wave outline", lineStyles.elliott, cc.bullish) {
                onLineStylesChange(lineStyles.copy(elliott = it))
            }
            LineStyleControls("Neckline (end of wave 4)", lineStyles.elliottNeck, cc.bullish) {
                onLineStylesChange(lineStyles.copy(elliottNeck = it))
            }
            SettingNote("Auto line colour follows the direction of the impulse (bullish / bearish).")
        }

        // -------------------------------------------------------- Risk / reward box
        TuningCard(
            title = "Risk/reward box",
            description = "How each backtest trade is drawn: reward zone, risk zone and entry line.",
            onReset = if (boxStylesDirty) {
                { onLineStylesChange(lineStyles.copy(riskReward = lsDef.riskReward, tradePath = lsDef.tradePath)) }
            } else null
        ) {
            val box = lineStyles.riskReward
            SettingGroup("Box")
            SettingSwitch(
                label = "Outline",
                hint = "Draw a border around the reward and risk zones. The trade picked in the Trades sheet is always outlined.",
                checked = box.showOutline,
                onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(showOutline = it))) }
            )
            if (box.showOutline) {
                SettingSlider(
                    label = "Outline width",
                    hint = "Border thickness.",
                    valueText = fmt2(box.outlineWidth.toDouble()) + " px",
                    value = box.outlineWidth.toDouble(),
                    min = 0.5,
                    max = 4.0,
                    lowHint = "thin",
                    highHint = "thick",
                    onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(outlineWidth = (Math.round(it * 4.0) / 4.0).toFloat()))) }
                )
                SettingSlider(
                    label = "Outline opacity",
                    hint = "Border strength.",
                    valueText = (box.outlineOpacity * 100).toInt().toString() + "%",
                    value = box.outlineOpacity.toDouble(),
                    min = 0.1,
                    max = 1.0,
                    lowHint = "faint",
                    highHint = "solid",
                    onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(outlineOpacity = (Math.round(it * 20.0) / 20.0).toFloat()))) }
                )
            }
            SettingSlider(
                label = "Fill opacity",
                hint = "How strongly the two zones are tinted.",
                valueText = (box.fillOpacity * 100).toInt().toString() + "%",
                value = box.fillOpacity.toDouble(),
                min = 0.02,
                max = 0.5,
                lowHint = "see-through",
                highHint = "strong",
                onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(fillOpacity = (Math.round(it * 100.0) / 100.0).toFloat()))) }
            )
            SettingSwitch(
                label = "Result and exit marker",
                hint = "Show the realised R inside the box and a dot where the trade exited.",
                checked = box.showResult,
                onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(showResult = it))) }
            )
            SettingGroup("Colours")
            SettingColor(
                label = "Reward zone",
                selected = box.rewardColor,
                autoColor = cc.bullish,
                onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(rewardColor = it))) }
            )
            SettingColor(
                label = "Risk zone",
                selected = box.riskColor,
                autoColor = cc.bearish,
                onChange = { onLineStylesChange(lineStyles.copy(riskReward = box.copy(riskColor = it))) }
            )
            LineStyleControls("Entry line", lineStyles.tradePath, cc.axisLabel) {
                onLineStylesChange(lineStyles.copy(tradePath = it))
            }
        }

        // -------------------------------------------------------- Chart lines
        TuningCard(
            title = "Grid & price line",
            description = "The background grid and the dashed line at the last price.",
            onReset = if (furnitureStylesDirty) {
                { onLineStylesChange(lineStyles.copy(grid = lsDef.grid, lastPrice = lsDef.lastPrice)) }
            } else null
        ) {
            LineStyleControls("Grid", lineStyles.grid, cc.gridLine) {
                onLineStylesChange(lineStyles.copy(grid = it))
            }
            LineStyleControls("Last-price line", lineStyles.lastPrice, cc.bullish) {
                onLineStylesChange(lineStyles.copy(lastPrice = it))
            }
        }

        // ------------------------------------------------------------- Spiral
        TuningCard(
            title = "Logarithmic spiral",
            description = "Golden spiral wound around the latest swing, to spot where price may turn.",
            enabled = spiralConfig.enabled,
            onEnabledChange = { onSpiralChange(spiralConfig.copy(enabled = it)) },
            onReset = if (spiralConfig != spiralReset) { { onSpiralChange(spiralReset) } } else null
        ) {
            SettingNote("The spiral winds around the older swing point and passes through the newer one. " +
                "Needs swing pivots to be on. Tap any swing pivot on the chart to snap the spiral onto it.")
            SettingGroup("Shape")
            SettingSlider(
                label = "Growth per quarter turn",
                hint = "How much wider the spiral gets every 90\u00B0. 1.62 is the golden ratio (phi).",
                valueText = fmt2(spiralConfig.growthPerQuarterTurn) +
                    if (abs(spiralConfig.growthPerQuarterTurn - LogSpiral.GOLDEN) < 0.005) " \u03C6" else "",
                value = spiralConfig.growthPerQuarterTurn,
                min = 1.1,
                max = 3.0,
                lowHint = "tight, many turns",
                highHint = "wide, fast opening",
                onChange = {
                    // Snap to phi when the slider is close, so the golden spiral is easy to hit exactly.
                    val g = if (abs(it - LogSpiral.GOLDEN) < 0.03) LogSpiral.GOLDEN else it
                    onSpiralChange(spiralConfig.copy(growthPerQuarterTurn = g))
                }
            )
            SettingChoice(
                label = "Direction",
                hint = "Auto turns counter-clockwise after an up-swing and clockwise after a down-swing. \"Counter\" = counter-clockwise.",
                options = SpiralDirection.values().map { it to it.label },
                selected = spiralConfig.direction,
                onChange = { onSpiralChange(spiralConfig.copy(direction = it)) }
            )
            SettingSwitch(
                label = "Mirror spiral",
                hint = "Also draw the opposite-winding spiral through the same two points.",
                checked = spiralConfig.mirror,
                onChange = { onSpiralChange(spiralConfig.copy(mirror = it)) }
            )

            SettingGroup("Anchor swing")
            if (spiralConfig.manualThroughTime != 0L) {
                SettingNote("Snapped to a pivot you tapped on the chart. Tap another pivot to move it.")
                TextButton(onClick = { onSpiralChange(spiralConfig.copy(manualThroughTime = 0L)) }) {
                    Text("Follow the latest swing again")
                }
            }
            SettingIntSlider(
                label = "Swing back",
                hint = "Which swing to use. 0 is the latest one, 1 the one before it, and so on. Moving this clears a tapped pivot.",
                value = spiralConfig.swingOffset,
                min = 0,
                max = 10,
                valueText = when {
                    spiralConfig.manualThroughTime != 0L -> "tapped"
                    spiralConfig.swingOffset == 0 -> "latest"
                    else -> spiralConfig.swingOffset.toString() + " back"
                },
                lowHint = "latest swing",
                highHint = "older swing",
                onChange = { onSpiralChange(spiralConfig.copy(swingOffset = it, manualThroughTime = 0L)) }
            )

            SettingGroup("Length")
            SettingSlider(
                label = "Turns outward",
                hint = "How far the spiral runs on past the newer point, towards the future.",
                valueText = fmt2(spiralConfig.outwardTurns) + " turns",
                value = spiralConfig.outwardTurns,
                min = 0.0,
                max = 4.0,
                lowHint = "none",
                highHint = "long",
                onChange = { onSpiralChange(spiralConfig.copy(outwardTurns = it)) }
            )
            SettingSlider(
                label = "Turns inward",
                hint = "How far the spiral winds in towards the older point.",
                valueText = fmt2(spiralConfig.inwardTurns) + " turns",
                value = spiralConfig.inwardTurns,
                min = 0.0,
                max = 4.0,
                lowHint = "none",
                highHint = "long",
                onChange = { onSpiralChange(spiralConfig.copy(inwardTurns = it)) }
            )

            SettingGroup("Look")
            SettingColor(
                label = "Colour",
                selected = spiralConfig.color,
                autoColor = cc.spiral,
                onChange = { onSpiralChange(spiralConfig.copy(color = it)) }
            )
            SettingSwitch(
                label = "Show anchors",
                hint = "Mark the two swing points and the line between them.",
                checked = spiralConfig.showAnchors,
                onChange = { onSpiralChange(spiralConfig.copy(showAnchors = it)) }
            )
            SettingSwitch(
                label = "Quarter-turn marks",
                hint = "Dots every 90\u00B0 along the spiral, where each radius step by the growth factor lands.",
                checked = spiralConfig.showQuarterMarks,
                onChange = { onSpiralChange(spiralConfig.copy(showQuarterMarks = it)) }
            )
            SettingSlider(
                label = "Line width",
                hint = "Thickness of the spiral line.",
                valueText = fmt2(spiralConfig.lineWidth.toDouble()) + " px",
                value = spiralConfig.lineWidth.toDouble(),
                min = 1.0,
                max = 4.0,
                lowHint = "thin",
                highHint = "thick",
                onChange = { onSpiralChange(spiralConfig.copy(lineWidth = it.toFloat())) }
            )
            SettingSlider(
                label = "Opacity",
                hint = "Lower it to keep the candles easy to read through the spiral.",
                valueText = (spiralConfig.opacity * 100).toInt().toString() + "%",
                value = spiralConfig.opacity.toDouble(),
                min = 0.2,
                max = 1.0,
                lowHint = "faint",
                highHint = "solid",
                onChange = { onSpiralChange(spiralConfig.copy(opacity = it.toFloat())) }
            )
        }

        // ----------------------------------------------------------- Sessions
        TuningCard(
            title = "Trading sessions",
            description = "Shades Tokyo, London and New York hours (UTC).",
            enabled = sessionConfig.enabled,
            onEnabledChange = { onSessionChange(sessionConfig.copy(enabled = it)) },
            onReset = if (sessionConfig != sessionReset) { { onSessionChange(sessionReset) } } else null
        ) {
            SessionTimeline(
                tokyo = sessionConfig.tokyoStartHour until sessionConfig.tokyoEndHour,
                london = sessionConfig.londonStartHour until sessionConfig.londonEndHour,
                ny = sessionConfig.nyStartHour until sessionConfig.nyEndHour,
                tokyoColor = TokyoAccent,
                londonColor = LondonAccent,
                nyColor = NewYorkAccent
            )
            SettingGroup("Appearance")
            SettingSwitch(
                label = "Box around each session",
                hint = "On: a box from the session's low to its high, growing live while the session runs. Off: a full-height band.",
                checked = sessionConfig.boxMode,
                onChange = { onSessionChange(sessionConfig.copy(boxMode = it)) }
            )
            SettingSwitch(
                label = "Show session names",
                hint = "Label each box (\"live\" is added while the session is still running).",
                checked = sessionConfig.showLabels,
                onChange = { onSessionChange(sessionConfig.copy(showLabels = it)) }
            )
            SettingSwitch(
                label = "Box outline",
                hint = "Draw a border around each box.",
                checked = sessionConfig.showBorder,
                onChange = { onSessionChange(sessionConfig.copy(showBorder = it)) }
            )
            SettingSlider(
                label = "Box fill",
                hint = "How strongly the inside of the box is tinted.",
                valueText = (sessionConfig.fillOpacity * 100).toInt().toString() + "%",
                value = sessionConfig.fillOpacity.toDouble(),
                min = 0.0,
                max = 0.5,
                lowHint = "clear",
                highHint = "strong",
                onChange = { onSessionChange(sessionConfig.copy(fillOpacity = (Math.round(it * 100.0) / 100.0).toFloat())) }
            )
            SettingSlider(
                label = "Outline strength",
                hint = "Opacity of the box border.",
                valueText = (sessionConfig.borderOpacity * 100).toInt().toString() + "%",
                value = sessionConfig.borderOpacity.toDouble(),
                min = 0.1,
                max = 1.0,
                lowHint = "faint",
                highHint = "solid",
                onChange = { onSessionChange(sessionConfig.copy(borderOpacity = (Math.round(it * 20.0) / 20.0).toFloat())) }
            )
            SettingGroup("Trading")
            SettingSwitch(
                label = "Only trade inside sessions",
                hint = "Skip signals whose entry bar falls outside all three sessions.",
                checked = sessionConfig.filterEntries,
                onChange = { onSessionChange(sessionConfig.copy(filterEntries = it)) }
            )
            SettingGroup("Hours (UTC, start inclusive, end exclusive)")
            SettingHourRange(
                label = "Tokyo",
                hint = "Asian session.",
                start = sessionConfig.tokyoStartHour,
                end = sessionConfig.tokyoEndHour,
                accent = TokyoAccent,
                onChange = { s, e -> onSessionChange(sessionConfig.copy(tokyoStartHour = s, tokyoEndHour = e)) }
            )
            SettingHourRange(
                label = "London",
                hint = "European session, usually the most active for EUR/USD.",
                start = sessionConfig.londonStartHour,
                end = sessionConfig.londonEndHour,
                accent = LondonAccent,
                onChange = { s, e -> onSessionChange(sessionConfig.copy(londonStartHour = s, londonEndHour = e)) }
            )
            SettingHourRange(
                label = "New York",
                hint = "US session. The London overlap is often the most volatile.",
                start = sessionConfig.nyStartHour,
                end = sessionConfig.nyEndHour,
                accent = NewYorkAccent,
                onChange = { s, e -> onSessionChange(sessionConfig.copy(nyStartHour = s, nyEndHour = e)) }
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

/** Target multiples are shown with their Fibonacci precision: 1.272, 1.618, 2.0, 2.618. */
private fun fmtExt(v: Double): String = if (v == Math.rint(v)) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "%.3f", v)

/** Small "why so few?" readout: how many candidate waves each filter removed, biggest first. */
@Composable
private fun ElliottFunnel(stats: ElliottStats?, shown: Int, enabled: Boolean) {
    if (!enabled) return
    if (stats == null || stats.windows == 0) {
        SettingNote("Detection report: not enough swing points yet. Elliott waves need six alternating pivots " +
            "(lower the pivot strength or minimum swing size to get more).")
        return
    }
    val lines = stats.topRejections().take(4)
    val dropped = (stats.accepted - shown).coerceAtLeast(0)
    val sb = StringBuilder()
    sb.append("Detection report: ").append(stats.windows).append(" candidate waves checked, ")
        .append(shown).append(" shown.")
    if (dropped > 0) sb.append("\n  ").append(dropped).append("  hidden behind a double / triple or a better impulse")
    for ((reason, count) in lines) sb.append("\n  ").append(count).append("  ").append(reason.label)
    SettingNote(sb.toString())
}
