package com.auto.rr.ui.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auto.rr.domain.backtest.BacktestConfig
import com.auto.rr.domain.strategy.StrategyConfig
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tuning controls, hosted inside [ChartBottomSheet] so the chart stays visible while tuning.
 * Changes re-run the backtest live; the stats line and equity curve above update as you drag.
 */
@Composable
fun BacktestTuningContent(
    backtestConfig: BacktestConfig,
    strategyConfig: StrategyConfig,
    onBacktestChange: (BacktestConfig) -> Unit,
    onStrategyChange: (StrategyConfig) -> Unit
) {
    val btDef = BacktestConfig.Default
    val stDef = StrategyConfig.Default

    val accountReset = backtestConfig.copy(
        initialEquity = btDef.initialEquity,
        riskFraction = btDef.riskFraction
    )
    val costsReset = backtestConfig.copy(
        feeFraction = btDef.feeFraction,
        slippageFraction = btDef.slippageFraction
    )
    val exitsReset = backtestConfig.copy(
        trailingAtrMultiplier = btDef.trailingAtrMultiplier,
        maxHoldingBars = btDef.maxHoldingBars
    )
    val strategyReset = strategyConfig.copy(
        atrPeriod = stDef.atrPeriod,
        stopAtrMultiplier = stDef.stopAtrMultiplier,
        targetRMultiple = stDef.targetRMultiple
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ------------------------------------------------------ Entry & exit
        TuningCard(
            title = "Stop & target",
            description = "Where each trade is protected and where it takes profit.",
            initiallyExpanded = true,
            onReset = if (strategyConfig != strategyReset) { { onStrategyChange(strategyReset) } } else null
        ) {
            SettingIntSlider(
                label = "ATR period",
                hint = "Look-back for ATR (average bar range), the volatility unit used for stops. " +
                    "Also scales the pivot and pattern thresholds.",
                value = strategyConfig.atrPeriod,
                min = 5,
                max = 50,
                valueText = strategyConfig.atrPeriod.toString() + " bars",
                lowHint = "reacts fast",
                highHint = "smoother",
                onChange = { onStrategyChange(strategyConfig.copy(atrPeriod = it)) }
            )
            SettingSlider(
                label = "Stop distance",
                hint = "Stop sits this many ATR beyond the pattern extreme.",
                valueText = fmt2(strategyConfig.stopAtrMultiplier) + " ATR",
                value = strategyConfig.stopAtrMultiplier,
                min = 0.5,
                max = 3.0,
                lowHint = "tight, stopped out often",
                highHint = "wide, smaller position",
                onChange = { onStrategyChange(strategyConfig.copy(stopAtrMultiplier = it)) }
            )
            SettingSlider(
                label = "Target",
                hint = "Take-profit distance as a multiple of the risk. 2.0R wins 2 for every 1 risked.",
                valueText = fmt2(strategyConfig.targetRMultiple) + " R",
                value = strategyConfig.targetRMultiple,
                min = 0.5,
                max = 5.0,
                lowHint = "hit more often",
                highHint = "bigger wins, hit less",
                onChange = { onStrategyChange(strategyConfig.copy(targetRMultiple = it)) }
            )
        }

        TuningCard(
            title = "Trade management",
            description = "Optional extra exits on top of the stop and target.",
            onReset = if (backtestConfig != exitsReset) { { onBacktestChange(exitsReset) } } else null
        ) {
            SettingSlider(
                label = "Trailing stop",
                hint = "Drags the stop behind price at this many ATR. Off keeps the original stop.",
                valueText = if (backtestConfig.trailingAtrMultiplier <= 0.0) "off"
                else fmt2(backtestConfig.trailingAtrMultiplier) + " ATR",
                value = backtestConfig.trailingAtrMultiplier,
                min = 0.0,
                max = 3.0,
                lowHint = "off / tight",
                highHint = "loose",
                onChange = { onBacktestChange(backtestConfig.copy(trailingAtrMultiplier = it)) }
            )
            SettingIntSlider(
                label = "Time limit",
                hint = "Close the trade at market after this many bars if neither stop nor target was hit. 0 means no limit.",
                value = backtestConfig.maxHoldingBars,
                min = 0,
                max = 500,
                valueText = if (backtestConfig.maxHoldingBars <= 0) "off"
                else backtestConfig.maxHoldingBars.toString() + " bars",
                lowHint = "off / short",
                highHint = "long",
                onChange = { onBacktestChange(backtestConfig.copy(maxHoldingBars = it)) }
            )
        }

        // -------------------------------------------------- Account & costs
        TuningCard(
            title = "Account & risk",
            description = "Starting balance and how much each trade risks.",
            onReset = if (backtestConfig != accountReset) { { onBacktestChange(accountReset) } } else null
        ) {
            SettingSlider(
                label = "Starting equity",
                hint = "Account balance at the start of the test. Position size scales with it.",
                valueText = fmtMoney(backtestConfig.initialEquity),
                value = backtestConfig.initialEquity,
                min = 1_000.0,
                max = 100_000.0,
                onChange = { onBacktestChange(backtestConfig.copy(initialEquity = (it / 500.0).roundToInt() * 500.0)) }
            )
            SettingSlider(
                label = "Risk per trade",
                hint = "Share of equity lost if the stop is hit. Lot size is derived from this and the stop distance.",
                valueText = fmt2(backtestConfig.riskFraction * 100.0) + " %",
                value = backtestConfig.riskFraction,
                min = 0.001,
                max = 0.05,
                lowHint = "cautious",
                highHint = "aggressive",
                onChange = { onBacktestChange(backtestConfig.copy(riskFraction = it)) }
            )
        }

        TuningCard(
            title = "Trading costs",
            description = "Charged on both entry and exit. Keep realistic to avoid flattering results.",
            onReset = if (backtestConfig != costsReset) { { onBacktestChange(costsReset) } } else null
        ) {
            SettingSlider(
                label = "Fee per side",
                hint = "Commission as a share of trade value. 1 bp = 0.01%.",
                valueText = fmt1(backtestConfig.feeFraction * 10_000.0) + " bp",
                value = backtestConfig.feeFraction,
                min = 0.0,
                max = 0.005,
                lowHint = "free",
                highHint = "expensive",
                onChange = { onBacktestChange(backtestConfig.copy(feeFraction = it)) }
            )
            SettingSlider(
                label = "Slippage per side",
                hint = "How much worse than the signal price you assume to get filled.",
                valueText = fmt1(backtestConfig.slippageFraction * 10_000.0) + " bp",
                value = backtestConfig.slippageFraction,
                min = 0.0,
                max = 0.005,
                lowHint = "perfect fills",
                highHint = "poor fills",
                onChange = { onBacktestChange(backtestConfig.copy(slippageFraction = it)) }
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmtMoney(v: Double): String = String.format(Locale.US, "%,.0f", v)
