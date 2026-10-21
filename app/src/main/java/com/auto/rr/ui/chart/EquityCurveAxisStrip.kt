package com.auto.rr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.auto.rr.domain.backtest.BacktestResult
import com.auto.rr.ui.theme.ChartColors

@Composable
fun EquityCurveStrip(
    result: BacktestResult,
    colors: ChartColors,
    modifier: Modifier = Modifier
) {
    if (result.totalTrades == 0 || result.equityCurve.size < 2) return

    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        EquityCurveRenderer.render(
            scope = this,
            measurer = measurer,
            result = result,
            colors = colors
        )
    }
}
