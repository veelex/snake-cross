package com.auto.rr.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.data.remote.ApiConfig
import com.auto.rr.domain.backtest.BacktestResult
import com.auto.rr.ui.theme.ChartColors
import com.auto.rr.ui.theme.NumberStyle
import java.util.Locale

/** Instrument, live price and change versus the previous bar. */
@Composable
internal fun PriceHeader(
    candles: List<CandleEntity>,
    colors: ChartColors,
    modifier: Modifier = Modifier
) {
    val last = candles.lastOrNull()
    val prev = if (candles.size >= 2) candles[candles.size - 2] else null
    Column(modifier = modifier) {
        Text(
            text = ApiConfig.SYMBOL,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (last == null) {
            Text(
                text = "Loading candles",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val delta = if (prev != null && prev.close != 0.0) (last.close - prev.close) / prev.close else 0.0
            val tone = when {
                delta > 0.0 -> colors.bullish
                delta < 0.0 -> colors.bearish
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.5f", last.close),
                    style = NumberStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = (if (delta >= 0.0) "+" else "") + String.format(Locale.US, "%.3f%%", delta * 100.0),
                    style = NumberStyle,
                    color = tone,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

/** Toggle-style tool button: quiet when idle, brass-tinted when its sheet is open. */
@Composable
internal fun ToolChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(bg)
            .toggleable(value = active, enabled = enabled, role = Role.Button, onValueChange = { onClick() })
            .defaultMinSize(minHeight = 40.dp, minWidth = 44.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    )
}

/** One-line backtest summary that scrolls sideways instead of wrapping or truncating. */
@Composable
internal fun BacktestSummary(
    result: BacktestResult,
    patternCount: Int,
    colors: ChartColors,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (result.totalTrades > 0) {
            val netTone = if (result.netProfitPct >= 0.0) colors.bullish else colors.bearish
            Stat("Net", fmtSignedPct(result.netProfitPct), netTone)
            Stat("Trades", result.totalTrades.toString())
            Stat("Win", String.format(Locale.US, "%.1f%%", result.winRate * 100.0))
            Stat("PF", fmtPf(result.profitFactor))
            Stat("Avg R", String.format(Locale.US, "%.2f", result.avgR))
            Stat("Max DD", String.format(Locale.US, "%.1f%%", result.maxDrawdownPct * 100.0), colors.bearish)
            Stat("Patterns", patternCount.toString())
        } else {
            Text(
                text = "No backtest trades yet  \u00B7  $patternCount patterns on chart",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, tone: Color? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = NumberStyle,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            color = tone ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmtPf(pf: Double): String = when {
    pf.isInfinite() -> "\u221E"
    pf.isNaN() -> "0.00"
    else -> String.format(Locale.US, "%.2f", pf)
}

private fun fmtSignedPct(p: Double): String =
    (if (p >= 0.0) "+" else "") + String.format(Locale.US, "%.1f%%", p * 100.0)
