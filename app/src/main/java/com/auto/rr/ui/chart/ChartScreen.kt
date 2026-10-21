package com.auto.rr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.data.ConfigStore
import com.auto.rr.data.TimeframeStore
import com.auto.rr.data.IndicatorConfigStore
import com.auto.rr.data.preset.ConfigJson
import com.auto.rr.data.preset.IndicatorSnapshot
import com.auto.rr.data.preset.PresetKind
import com.auto.rr.data.preset.PresetParse
import com.auto.rr.data.preset.TuneSnapshot
import com.auto.rr.data.LineStyleStore
import com.auto.rr.di.ServiceLocator
import com.auto.rr.domain.indicator.AtrCalculator
import com.auto.rr.domain.indicator.EmaCalculator
import com.auto.rr.domain.indicator.EmaConfig
import com.auto.rr.domain.indicator.FvgConfig
import com.auto.rr.domain.indicator.FvgDetector
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.PivotDetector
import com.auto.rr.domain.indicator.RsiCalculator
import com.auto.rr.domain.indicator.LogSpiral
import com.auto.rr.domain.indicator.SessionBoxes
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.domain.pattern.DoublePatternDetector
import com.auto.rr.domain.pattern.ElliottStats
import com.auto.rr.domain.pattern.ElliottWaveDetector
import com.auto.rr.domain.pattern.PatternConfig
import com.auto.rr.domain.pattern.PatternSelector
import com.auto.rr.domain.pattern.TriplePatternDetector
import com.auto.rr.domain.strategy.EmaSweepStrategy
import com.auto.rr.domain.strategy.SnakeCrossStrategy
import com.auto.rr.domain.strategy.StrategyConfig
import com.auto.rr.domain.strategy.WaveThreeStrategy
import com.auto.rr.domain.backtest.BacktestConfig
import com.auto.rr.domain.backtest.BacktestEngine
import com.auto.rr.domain.backtest.BacktestResult
import com.auto.rr.domain.backtest.Trade
import com.auto.rr.ui.settings.ProfileDialog
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusPill
import com.auto.rr.ui.theme.chartBackgroundFor
import com.auto.rr.ui.theme.rememberChartColors

private val PRICE_AXIS_WIDTH = ChartGestureConfig.Default.priceAxisWidth   // right edge
private val TIME_AXIS_HEIGHT = ChartGestureConfig.Default.timeAxisHeight    // bottom edge
private val MIN_TIME_TICK_SPACING = 72.dp

@Composable
fun ChartScreen() {
    val state by StatusBus.state.collectAsStateWithLifecycle()
    val log by StatusBus.log.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(ChartSheet.NONE) }

    val appContext = LocalContext.current
    // Saved across restarts by TimeframeStore; rememberSaveable still covers rotation.
    var selectedTf by rememberSaveable { mutableStateOf(TimeframeStore.load(appContext)) }
    var initializedTf by rememberSaveable { mutableStateOf<Timeframe?>(null) }

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val chartColors = rememberChartColors(isDark)
    val chartBackground = chartBackgroundFor(isDark)
    val textMeasurer = rememberTextMeasurer()
    var timeZone by remember { mutableStateOf(ChartTimeZoneStore.load(context)) }
    var showTzPicker by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    // Which preset dialog (save / load / import / export) is open, if any.
    var presetsFor by rememberSaveable { mutableStateOf<PresetKind?>(null) }

    val density = LocalDensity.current
    val axisRightPx = with(density) { PRICE_AXIS_WIDTH.toPx() }
    val axisBottomPx = with(density) { TIME_AXIS_HEIGHT.toPx() }
    val minTickSpacingPx = with(density) { MIN_TIME_TICK_SPACING.toPx() }

    val candlesFlow = remember(selectedTf) {
        ServiceLocator.candleRepository.observe(selectedTf)
    }
    val loadedCandles by candlesFlow.collectAsStateWithLifecycle(
        initialValue = emptyList<CandleEntity>()
    )
    // After a timeframe switch the state still holds the previous timeframe's candles until the
    // new query emits. Drawing, framing or backtesting on those would be wrong, so drop them.
    val candles = remember(loadedCandles, selectedTf) {
        if (loadedCandles.isNotEmpty() && loadedCandles.first().timeframe != selectedTf.wire) emptyList<CandleEntity>()
        else loadedCandles
    }

    var emaConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadEma(context, selectedTf)) }
    val emas = remember(candles, emaConfig) {
        if (candles.isEmpty() || !emaConfig.enabled) emptyMap<Int, DoubleArray>()
        else EmaCalculator.emasFor(candles, emaConfig.periods)
    }

    var fvgConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadFvg(context, selectedTf)) }
    val fvgs = remember(candles, fvgConfig) {
        if (candles.isEmpty() || !fvgConfig.enabled) emptyList()
        else FvgDetector.detect(candles, fvgConfig)
    }

    var sessionConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadSession(context, selectedTf)) }
    // Rebuilt whenever the candles change, so the running session's box grows with live data.
    val sessionBoxes = remember(candles, sessionConfig) {
        if (candles.isEmpty() || !sessionConfig.enabled || !sessionConfig.boxMode) emptyList()
        else SessionBoxes.compute(candles, sessionConfig)
    }

    var strategyConfig by remember(selectedTf) { mutableStateOf(ConfigStore.loadStrategy(context, selectedTf)) }
    val atr = remember(candles, strategyConfig) {
        if (candles.isEmpty()) DoubleArray(0)
        else AtrCalculator.atr(candles, strategyConfig.atrPeriod)
    }

    var pivotConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadPivot(context, selectedTf)) }
    val rsi = remember(candles) {
        if (candles.isEmpty()) DoubleArray(0)
        else RsiCalculator.rsi(DoubleArray(candles.size) { candles[it].close }, 14)
    }

    val pivots = remember(candles, pivotConfig, atr) {
        if (candles.isEmpty() || !pivotConfig.enabled) emptyList()
        else PivotDetector.detect(candles, pivotConfig, atr)
    }

    var patternConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadPattern(context, selectedTf)) }
    var lineStyles by remember { mutableStateOf(LineStyleStore.load(context)) }
    LaunchedEffect(lineStyles) { LineStyleStore.save(context, lineStyles) }
    var spiralConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadSpiral(context, selectedTf)) }
    // The spiral is anchored on swing pivots, so it needs pivots to be enabled.
    val spiralAnchors = remember(
        pivots, spiralConfig.enabled, spiralConfig.swingOffset, spiralConfig.manualThroughTime
    ) {
        if (!spiralConfig.enabled || pivots.isEmpty()) null
        else {
            // A tapped pivot wins; if it is gone (data or pivot settings changed) fall back to the swing offset.
            val manualIdx = if (spiralConfig.manualThroughTime != 0L)
                pivots.indexOfFirst { candles.getOrNull(it.index)?.timestamp == spiralConfig.manualThroughTime }
            else -1
            (if (manualIdx >= 0) LogSpiral.anchorsAt(pivots, manualIdx) else null)
                ?: LogSpiral.anchors(pivots, spiralConfig.swingOffset)
        }
    }
    LaunchedEffect(emaConfig, fvgConfig, pivotConfig, sessionConfig, patternConfig, spiralConfig, selectedTf) {
        IndicatorConfigStore.save(context, selectedTf, emaConfig, fvgConfig, pivotConfig, sessionConfig, patternConfig, spiralConfig)
    }
    val elliottResult = remember(candles, pivots, atr, rsi, patternConfig, pivotConfig) {
        if (candles.isEmpty() || !pivotConfig.enabled || !patternConfig.enabled || !patternConfig.showElliott) null
        else ElliottWaveDetector.detectScaled(candles, pivots, patternConfig, atr, pivotConfig, rsi)
    }
    val elliottStats: ElliottStats? = elliottResult?.stats
    val patterns = remember(candles, pivots, atr, rsi, patternConfig, pivotConfig, elliottResult) {
        if (candles.isEmpty() || !patternConfig.enabled || (pivots.isEmpty() && elliottResult == null)) emptyList()
        else {
            val doubles = if (patternConfig.showDoubles)
                DoublePatternDetector.detect(candles, pivots, patternConfig, atr, pivotConfig.strength, rsi)
            else emptyList()
            val triples = if (patternConfig.showTriples)
                TriplePatternDetector.detect(candles, pivots, patternConfig, atr, pivotConfig.strength, rsi)
            else emptyList()
            val elliott = elliottResult?.patterns ?: emptyList()
            PatternSelector.select(doubles, triples, elliott, patternConfig.elliottOverlapOthers)
        }
    }
    // Elliott waves are always drawn, but only feed the buy/sell strategy when explicitly enabled.
    val strategyPatterns = remember(patterns, patternConfig.elliottInStrategy) {
        if (patternConfig.elliottInStrategy) patterns else patterns.filter { !it.isElliott }
    }
    val snakeSignals = remember(candles, strategyPatterns, emas, atr, sessionConfig, strategyConfig) {
        if (candles.isEmpty() || strategyPatterns.isEmpty() || emas.isEmpty() || atr.isEmpty() || !strategyConfig.enabled) emptyList()
        else SnakeCrossStrategy.detect(candles, strategyPatterns, emas, atr, sessionConfig, strategyConfig)
    }
    var sweepConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadSweep(context, selectedTf)) }
    LaunchedEffect(sweepConfig, selectedTf) { IndicatorConfigStore.saveSweep(context, selectedTf, sweepConfig) }
    // EMA sweep reads the same patterns minus Elliott (it ignores those itself) and needs the EMAs.
    val sweepSignals = remember(candles, strategyPatterns, emas, atr, sessionConfig, strategyConfig, sweepConfig) {
        if (candles.isEmpty() || strategyPatterns.isEmpty() || emas.isEmpty() || atr.isEmpty() || !sweepConfig.enabled) emptyList()
        else EmaSweepStrategy.detect(candles, strategyPatterns, emas, atr, sessionConfig, strategyConfig, sweepConfig)
    }
    // Elliott wave 3 entries: limit orders rested at the end of wave 2. They need swing pivots only,
    // not the EMAs or the pattern detectors, so they are independent of the strategies above.
    var waveConfig by remember(selectedTf) { mutableStateOf(IndicatorConfigStore.loadWave(context, selectedTf)) }
    LaunchedEffect(waveConfig, selectedTf) { IndicatorConfigStore.saveWave(context, selectedTf, waveConfig) }
    val waveSignals = remember(candles, pivots, pivotConfig, atr, sessionConfig, waveConfig) {
        if (candles.isEmpty() || !pivotConfig.enabled || !waveConfig.enabled) emptyList()
        else WaveThreeStrategy.detect(candles, pivots, pivotConfig, atr, sessionConfig, waveConfig)
    }
    // Everything drawn on the chart. The backtest only takes sweeps when "Trade sweeps" is on,
    // and wave 3 setups when "Trade wave 3 setups" is on. (Wave 3 setups have their own drawing.)
    val signals = remember(snakeSignals, sweepSignals) {
        (snakeSignals + sweepSignals).sortedBy { it.triggerIndex }
    }
    val tradeSignals = remember(snakeSignals, sweepSignals, sweepConfig.tradeSweeps, waveSignals, waveConfig.tradeSetups) {
        val base = if (sweepConfig.tradeSweeps) snakeSignals + sweepSignals else snakeSignals
        (if (waveConfig.tradeSetups) base + waveSignals else base).sortedBy { it.triggerIndex }
    }

    var backtestConfig by remember(selectedTf) { mutableStateOf(ConfigStore.loadBacktest(context, selectedTf)) }

    LaunchedEffect(strategyConfig, backtestConfig, selectedTf) {
        ConfigStore.save(context, selectedTf, strategyConfig, backtestConfig)
    }
    var bt by remember { mutableStateOf(BacktestResult.empty(backtestConfig)) }
    // Trade picked in the Trades sheet: the chart is framed on it and it stays highlighted.
    var focusedTrade by remember(selectedTf) { mutableStateOf<Trade?>(null) }
    LaunchedEffect(tradeSignals, backtestConfig) {
        bt = withContext(Dispatchers.Default) {
            BacktestEngine.run(candles, tradeSignals, backtestConfig)
        }
    }

    val gestureState = rememberSaveable(saver = ChartGestureStateSaver) {
        ChartGestureState(
            ChartTransform(xStart = 0f, xScale = 8f, yMin = 0.0, yMax = 1.0)
        )
    }

    // Wait for the plot to be measured so the default zoom can be sized to the screen.
    if (initializedTf != selectedTf && candles.isNotEmpty() && gestureState.viewWidth > 50f) {
        gestureState.reset(ChartViewport.init(candles, gestureState.viewWidth))
        initializedTf = selectedTf
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriceHeader(
                    candles = candles,
                    colors = chartColors,
                    modifier = Modifier.weight(1f)
                )
                ToolIconChip(ToolGlyph.IND, "Indicators", sheet == ChartSheet.IND) {
                    sheet = if (sheet == ChartSheet.IND) ChartSheet.NONE else ChartSheet.IND
                }
                ToolIconChip(ToolGlyph.TUNE, "Tune", sheet == ChartSheet.TUNE) {
                    sheet = if (sheet == ChartSheet.TUNE) ChartSheet.NONE else ChartSheet.TUNE
                }
                ToolIconChip(ToolGlyph.TRADES, "Trades", sheet == ChartSheet.TRADES, enabled = bt.totalTrades > 0) {
                    sheet = if (sheet == ChartSheet.TRADES) ChartSheet.NONE else ChartSheet.TRADES
                }
                ToolIconChip(ToolGlyph.PROFILE, "Profile", showProfile) { showProfile = true }
            }
            TimeframeStrip(
                selected = selectedTf,
                onSelect = { tf ->
                    if (tf != selectedTf) {
                        selectedTf = tf
                        TimeframeStore.save(appContext, tf)
                        StatusBus.info("Switched chart to " + tf.label)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            BacktestSummary(
                result = bt,
                patternCount = patterns.size,
                colors = chartColors
            )
            if (bt.totalTrades > 0) {
                EquityCurveStrip(
                    result = bt,
                    colors = chartColors,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(chartBackground)
                    .onSizeChanged {
                        gestureState.viewWidth =
                            (it.width.toFloat() - axisRightPx).coerceAtLeast(1f)
                        gestureState.viewHeight =
                            (it.height.toFloat() - axisBottomPx).coerceAtLeast(1f)
                        // The plot starts at the canvas' left edge; the price axis is on the right.
                        gestureState.chartLeftPx = 0f
                    }
                    .chartGestures(
                        gestureState,
                        onCornerTap = { showTzPicker = true },
                        onResetPrice = {
                            gestureState.update { ChartViewport.fitPrice(candles, it, gestureState.viewWidth) }
                        },
                        onResetTime = {
                            gestureState.update { ChartViewport.resetTime(it, gestureState.viewWidth) }
                        },
                        onBodyTap = { tap ->
                            // With the spiral on, tapping a swing pivot snaps the spiral onto it.
                            if (spiralConfig.enabled && pivots.isNotEmpty()) {
                                val idx = pivotListIndexAt(
                                    pivots, gestureState.transform, tap,
                                    gestureState.viewHeight, with(density) { 28.dp.toPx() }
                                )
                                if (idx != null) {
                                    val pv = pivots[idx]
                                    val time = candles.getOrNull(pv.index)?.timestamp
                                    when {
                                        time == null -> Unit
                                        LogSpiral.anchorsAt(pivots, idx) == null ->
                                            StatusBus.info("No earlier swing to wind the spiral around")
                                        else -> {
                                            spiralConfig = spiralConfig.copy(manualThroughTime = time)
                                            StatusBus.info(
                                                "Spiral snapped to swing " + (if (pv.isHigh) "high " else "low ") +
                                                    String.format(java.util.Locale.US, "%.5f", pv.price)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
            ) {
                val chartLeft = 0f
                val chartRight = size.width - axisRightPx
                val chartTop = 0f
                val chartBottom = size.height - axisBottomPx
                val chartW = chartRight - chartLeft
                val chartH = chartBottom - chartTop

                if (chartW <= 0f || chartH <= 0f) return@Canvas

                ChartRenderer.renderChartBackground(this, chartBackground)

                // Draw area == exactly the plotted chart (excludes price/time axes), so the
                // transform's height matches the axis labels and the gesture math.
                val timeTicks = TimeAxisTicks.compute(
                    candles = candles,
                    transform = gestureState.transform,
                    viewWidthPx = chartW,
                    timeframe = selectedTf,
                    minSpacingPx = minTickSpacingPx,
                    zone = timeZone
                )

                inset(left = 0f, top = 0f, right = axisRightPx, bottom = axisBottomPx) {
                  clipRect {
                    ChartRenderer.renderGrid(this, chartColors, gestureState.transform, timeTicks, lineStyles.grid)
                    ChartSessionRenderer.render(
                        this,
                        candles,
                        gestureState.transform,
                        chartColors,
                        sessionConfig,
                        sessionBoxes,
                        textMeasurer
                    )
                    ChartFvgRenderer.render(
                        this,
                        candles,
                        fvgs,
                        gestureState.transform,
                        chartColors,
                        fvgConfig
                    )
                    ChartPatternRendererLive.render(
                        this,
                        textMeasurer,
                        candles,
                        patterns,
                        gestureState.transform,
                        chartColors,
                        lineStyles
                    )
                    ChartSpiralRenderer.render(
                        this,
                        candles,
                        spiralAnchors,
                        gestureState.transform,
                        chartColors,
                        spiralConfig
                    )
                    ChartEmasRenderer.render(
                        this,
                        candles,
                        emas,
                        gestureState.transform,
                        chartColors,
                        lineStyles
                    )
                    ChartRenderer.renderCandles(
                        this,
                        candles,
                        gestureState.transform,
                        chartColors
                    )
                    ChartPivotRenderer.render(
                        this,
                        candles,
                        pivots,
                        gestureState.transform,
                        chartColors
                    )
                    ChartWaveThreeRenderer.render(
                        this,
                        measurer = textMeasurer,
                        candles = candles,
                        signals = waveSignals,
                        trades = bt.trades,
                        transform = gestureState.transform,
                        colors = chartColors
                    )
                    ChartSignalRenderer.render(
                        this,
                        measurer = textMeasurer,
                        candles = candles,
                        signals = signals,
                        transform = gestureState.transform,
                        colors = chartColors,
                        styles = lineStyles
                    )
                    ChartTradeRenderer.render(
                        this,
                        candles = candles,
                        trades = bt.trades,
                        transform = gestureState.transform,
                        colors = chartColors,
                        styles = lineStyles,
                        selected = focusedTrade,
                        measurer = textMeasurer
                    )
                    ChartEmaLegend.render(
                        this,
                        measurer = textMeasurer,
                        periods = emaConfig.periods,
                        colors = chartColors,
                        overrides = lineStyles.emaColors,
                        topLeft = Offset(8f, 4f)
                    )
                  }
                }

                val last = candles.lastOrNull()
                if (last != null) {
                    val yLast = gestureState.transform.screenYFor(last.close, chartH)
                    if (yLast.isFinite() && yLast >= chartTop && yLast <= chartBottom) {
                        val lineColor = lineStyles.lastPrice.color(
                            if (last.close >= last.open) chartColors.bullish
                            else chartColors.bearish
                        )
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        drawLine(
                            color = lineColor,
                            start = Offset(chartLeft, yLast),
                            end = Offset(chartRight, yLast),
                            strokeWidth = lineStyles.lastPrice.width,
                            pathEffect = dash
                        )
                    }
                }

                if (last != null) {
                    ChartAxesRenderer.renderLastPriceTag(
                        scope = this,
                        measurer = textMeasurer,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        axisX = chartRight,
                        axisWidth = axisRightPx,
                        price = last.close,
                        tagColor = if (last.close >= last.open) chartColors.bullish else chartColors.bearish,
                        transform = gestureState.transform
                    )
                }

                ChartAxesRenderer.renderPriceAxis(
                    scope = this,
                    measurer = textMeasurer,
                    chartTop = chartTop,
                    chartBottom = chartBottom,
                    axisX = chartRight,
                    transform = gestureState.transform,
                    colors = chartColors
                )

                ChartAxesRenderer.renderTimeAxis(
                    scope = this,
                    measurer = textMeasurer,
                    chartLeft = chartLeft,
                    chartRight = chartRight,
                    chartBottom = chartBottom,
                    transform = gestureState.transform,
                    ticks = timeTicks,
                    colors = chartColors
                )

                ChartAxesRenderer.renderCorner(
                    scope = this,
                    measurer = textMeasurer,
                    axisX = chartRight,
                    chartBottom = chartBottom,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    label = timeZone.abbreviationAt(
                        candles.getOrNull(
                            (gestureState.transform.xStart + chartW / gestureState.transform.xScale)
                                .toInt().coerceIn(0, (candles.size - 1).coerceAtLeast(0))
                        )?.timestamp ?: System.currentTimeMillis()
                    ),
                    colors = chartColors
                )
            }

            when (sheet) {
                ChartSheet.IND -> ChartBottomSheet(
                    title = "Indicators",
                    onClose = { sheet = ChartSheet.NONE },
                    actions = {
                        TextButton(onClick = { presetsFor = PresetKind.INDICATOR }) { Text("Presets") }
                    }
                ) {
                    IndicatorTuningContent(
                        timeframe = selectedTf,
                        emaConfig = emaConfig,
                        fvgConfig = fvgConfig,
                        pivotConfig = pivotConfig,
                        sessionConfig = sessionConfig,
                        patternConfig = patternConfig,
                        spiralConfig = spiralConfig,
                        sweepConfig = sweepConfig,
                        waveConfig = waveConfig,
                        waveSetups = waveSignals.size,
                        elliottStats = elliottStats,
                        elliottShown = patterns.count { it.isElliott },
                        lineStyles = lineStyles,
                        onEmaChange = { emaConfig = it },
                        onFvgChange = { fvgConfig = it },
                        onPivotChange = { pivotConfig = it },
                        onSessionChange = { sessionConfig = it },
                        onPatternChange = { patternConfig = it },
                        onSpiralChange = { spiralConfig = it },
                        onSweepChange = { sweepConfig = it },
                        onWaveChange = { waveConfig = it },
                        onLineStylesChange = { lineStyles = it }
                    )
                }
                ChartSheet.TUNE -> ChartBottomSheet(
                    title = "Tune backtest",
                    onClose = { sheet = ChartSheet.NONE },
                    actions = {
                        TextButton(onClick = { presetsFor = PresetKind.TUNE }) { Text("Presets") }
                    }
                ) {
                    BacktestTuningContent(
                        backtestConfig = backtestConfig,
                        strategyConfig = strategyConfig,
                        onBacktestChange = { backtestConfig = it },
                        onStrategyChange = { strategyConfig = it }
                    )
                }
                ChartSheet.TRADES -> ChartBottomSheet(
                    title = "Trades (" + bt.totalTrades + ")",
                    onClose = { sheet = ChartSheet.NONE },
                    actions = {
                        if (bt.trades.isNotEmpty()) {
                            TextButton(onClick = { exportTrades(context, bt) }) { Text("Export") }
                        }
                    }
                ) {
                    TradeListContent(
                        result = bt,
                        zone = timeZone,
                        selected = focusedTrade,
                        onTradeClick = { t ->
                            focusedTrade = t
                            gestureState.reset(ChartViewport.focusTrade(candles, t, gestureState.viewWidth))
                            sheet = ChartSheet.NONE
                        }
                    )
                }
                ChartSheet.LOG -> ChartBottomSheet(
                    title = "Activity",
                    onClose = { sheet = ChartSheet.NONE }
                ) {
                    StatusLogContent(state = state, events = log)
                }
                ChartSheet.NONE -> Unit
            }

            StatusBar(
                state = state,
                logSize = log.size,
                onClick = { sheet = if (sheet == ChartSheet.LOG) ChartSheet.NONE else ChartSheet.LOG }
            )
        }
    }

    if (showTzPicker) {
        TimeZonePickerDialog(
            selected = timeZone,
            nowUtcMs = System.currentTimeMillis(),
            onSelect = {
                timeZone = it
                ChartTimeZoneStore.save(context, it)
                StatusBus.info("Chart time zone set to " + it.title, detail = it.zoneId)
                showTzPicker = false
            },
            onDismiss = { showTzPicker = false }
        )
    }

    if (showProfile) {
        ProfileDialog(onDismiss = { showProfile = false })
    }

    when (presetsFor) {
        PresetKind.INDICATOR -> ConfigPresetsDialog(
            kind = PresetKind.INDICATOR,
            timeframe = selectedTf,
            buildJson = { n ->
                ConfigJson.encodeIndicators(
                    selectedTf, n,
                    IndicatorSnapshot(emaConfig, fvgConfig, pivotConfig, sessionConfig, patternConfig, spiralConfig)
                )
            },
            onApply = { json ->
                when (val r = ConfigJson.decodeIndicators(json, selectedTf)) {
                    is PresetParse.Error -> PresetApplyResult(false, r.message)
                    is PresetParse.Ok -> {
                        emaConfig = r.value.ema
                        fvgConfig = r.value.fvg
                        pivotConfig = r.value.pivot
                        sessionConfig = r.value.session
                        patternConfig = r.value.pattern
                        spiralConfig = r.value.spiral
                        PresetApplyResult(true, appliedMessage(r.sourceTimeframe, selectedTf), r.name)
                    }
                }
            },
            onDismiss = { presetsFor = null }
        )
        PresetKind.TUNE -> ConfigPresetsDialog(
            kind = PresetKind.TUNE,
            timeframe = selectedTf,
            buildJson = { n ->
                ConfigJson.encodeTune(selectedTf, n, TuneSnapshot(strategyConfig, backtestConfig))
            },
            onApply = { json ->
                when (val r = ConfigJson.decodeTune(json)) {
                    is PresetParse.Error -> PresetApplyResult(false, r.message)
                    is PresetParse.Ok -> {
                        strategyConfig = r.value.strategy
                        backtestConfig = r.value.backtest
                        PresetApplyResult(true, appliedMessage(r.sourceTimeframe, selectedTf), r.name)
                    }
                }
            },
            onDismiss = { presetsFor = null }
        )
        null -> Unit
    }

}

private enum class ChartSheet { NONE, IND, TUNE, TRADES, LOG }

private fun appliedMessage(source: String?, target: Timeframe): String =
    if (source != null && source != target.wire) "Applied to ${target.label} (file was saved on $source)."
    else "Applied to ${target.label}."
