package com.auto.rr.ui.chart

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auto.rr.domain.backtest.BacktestResult
import com.auto.rr.domain.backtest.ExitReason
import com.auto.rr.domain.backtest.Trade
import com.auto.rr.domain.pattern.PatternType
import com.auto.rr.ui.theme.AppFonts
import com.auto.rr.ui.theme.NumberStyle
import com.auto.rr.ui.theme.rememberChartColors
import java.util.Locale

private enum class ResultFilter(val label: String) { ALL("All"), WINS("Wins"), LOSSES("Losses") }
private enum class SideFilter(val label: String) { ALL("Both"), LONG("Long"), SHORT("Short") }
private enum class SortMode(val label: String) {
    NEWEST("Newest"), OLDEST("Oldest"), BEST("Best R"), WORST("Worst R")
}

/** One line of the list: a day heading (time-sorted views only) or a trade. */
private sealed class ListEntry {
    data class Day(val label: String, val count: Int, val net: Double) : ListEntry()
    data class Item(val trade: Trade) : ListEntry()
}

private const val DAY_MS = 86_400_000L

/** Headline figure at the top of the sheet. */
private val HeroStyle = TextStyle(
    fontFamily = AppFonts.Display,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 38.sp,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum"
)

/**
 * Trades sheet: a result summary, compact filter controls, then a flat ledger of trades.
 * The summary always describes the full backtest; the line under the controls describes
 * the filtered view. When the list is sorted by time, trades are grouped under day headings.
 */
@Composable
fun TradeListContent(
    result: BacktestResult,
    modifier: Modifier = Modifier,
    zone: ChartTimeZone = ChartTimeZone.UTC,
    selected: Trade? = null,
    onTradeClick: (Trade) -> Unit = {}
) {
    if (result.trades.isEmpty()) {
        EmptyState(
            title = "No trades yet",
            body = "Loosen the pattern or session settings in Ind, or widen the stop in Tune.",
            modifier = modifier
        )
        return
    }

    val colors = rememberChartColors(isSystemInDarkTheme())
    val win = colors.bullish
    val loss = colors.bearish

    var resultFilter by rememberSaveable { mutableStateOf(ResultFilter.ALL) }
    var sideFilter by rememberSaveable { mutableStateOf(SideFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(SortMode.NEWEST) }

    val winCount = remember(result) { result.trades.count { it.netPnl > 0.0 } }

    val shown = remember(result, resultFilter, sideFilter, sort) {
        val filtered = result.trades.filter { t ->
            val okResult = when (resultFilter) {
                ResultFilter.ALL -> true
                ResultFilter.WINS -> t.netPnl > 0.0
                ResultFilter.LOSSES -> t.netPnl <= 0.0
            }
            val okSide = when (sideFilter) {
                SideFilter.ALL -> true
                SideFilter.LONG -> t.signal.bullish
                SideFilter.SHORT -> !t.signal.bullish
            }
            okResult && okSide
        }
        when (sort) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.entryTime }
            SortMode.OLDEST -> filtered.sortedBy { it.entryTime }
            SortMode.BEST -> filtered.sortedByDescending { it.rMultiple }
            SortMode.WORST -> filtered.sortedBy { it.rMultiple }
        }
    }
    val shownNet = shown.sumOf { it.netPnl }

    val grouped = sort == SortMode.NEWEST || sort == SortMode.OLDEST
    val entries: List<ListEntry> = remember(shown, zone, grouped) {
        if (!grouped) {
            shown.map { ListEntry.Item(it) }
        } else {
            val out = ArrayList<ListEntry>(shown.size + 16)
            val byDay = shown.groupBy { Math.floorDiv(zone.toLocal(it.entryTime), DAY_MS) }
            for ((_, day) in byDay) {
                out.add(
                    ListEntry.Day(
                        label = TimeAxisFormatter.dayMonth(zone.toLocal(day.first().entryTime)),
                        count = day.size,
                        net = day.sumOf { it.netPnl }
                    )
                )
                for (t in day) out.add(ListEntry.Item(t))
            }
            out
        }
    }

    // Reopening the sheet scrolls to the trade that is currently shown on the chart
    // (summary and controls are the two items before the first entry).
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val idx = if (selected == null) -1
        else entries.indexOfFirst { it is ListEntry.Item && it.trade == selected }
        if (idx >= 0) listState.scrollToItem(idx + 2)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item {
            Summary(result, win, loss, Modifier.padding(top = 2.dp, bottom = 14.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(
                    options = ResultFilter.values().toList(),
                    selected = resultFilter,
                    label = {
                        when (it) {
                            ResultFilter.ALL -> "All " + result.trades.size
                            ResultFilter.WINS -> "Wins $winCount"
                            ResultFilter.LOSSES -> "Losses " + (result.trades.size - winCount)
                        }
                    },
                    fill = true,
                    modifier = Modifier.fillMaxWidth()
                ) { resultFilter = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Segmented(
                        options = SideFilter.values().toList(),
                        selected = sideFilter,
                        label = { it.label },
                        fill = false
                    ) { sideFilter = it }
                    Spacer(Modifier.weight(1f))
                    SortMenu(sort) { sort = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shown.size.toString() + " of " + result.trades.size + " trades",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = fmtSigned(shownNet),
                        style = NumberStyle,
                        fontSize = 13.sp,
                        color = if (shownNet > 0.0) win else loss
                    )
                }
                Text(
                    text = "Tap a trade to show it on the chart.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing matches",
                    body = "No trades fit these filters.",
                    actionLabel = "Clear filters",
                    onAction = {
                        resultFilter = ResultFilter.ALL
                        sideFilter = SideFilter.ALL
                    },
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
        items(entries) { entry ->
            when (entry) {
                is ListEntry.Day -> DayHeader(entry, win, loss)
                is ListEntry.Item -> {
                    val t = entry.trade
                    TradeRow(
                        t = t,
                        accent = if (t.netPnl > 0.0) win else loss,
                        zone = zone,
                        selected = t == selected
                    ) { onTradeClick(t) }
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------------------------

@Composable
private fun Summary(result: BacktestResult, win: Color, loss: Color, modifier: Modifier = Modifier) {
    val netColor = if (result.netProfit >= 0.0) win else loss
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Net result",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(fmtPct(result.netProfitPct), style = HeroStyle, color = netColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Final equity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    fmtMoney(result.finalEquity),
                    style = NumberStyle,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "from " + fmtMoney(result.initialEquity),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        WinLossBar(result.wins, result.losses, win, loss)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .height(IntrinsicSize.Min)
        ) {
            StatCell("Win rate", (result.winRate * 100).toInt().toString() + "%", null, Modifier.weight(1f))
            StatDivider()
            StatCell(
                "Avg R", fmtSigned2(result.avgR),
                if (result.avgR >= 0.0) win else loss, Modifier.weight(1f)
            )
            StatDivider()
            StatCell("Profit factor", fmtPf(result.profitFactor), null, Modifier.weight(1f))
            StatDivider()
            StatCell(
                "Drawdown", (result.maxDrawdownPct * 100).toInt().toString() + "%",
                loss, Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WinLossBar(wins: Int, losses: Int, win: Color, loss: Color) {
    val total = wins + losses
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
                .semantics { contentDescription = "$wins wins, $losses losses" }
        ) {
            if (total > 0) {
                if (wins > 0) Box(Modifier.weight(wins.toFloat()).fillMaxHeight().background(win))
                if (losses > 0) {
                    if (wins > 0) Spacer(Modifier.width(2.dp))
                    Box(Modifier.weight(losses.toFloat()).fillMaxHeight().background(loss))
                }
            }
        }
        Row {
            Text(
                "$wins won",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$losses lost",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Color?, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = NumberStyle,
            fontSize = 15.sp,
            maxLines = 1,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(vertical = 10.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
}

// ---------------------------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------------------------

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    fill: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (o in options) {
            val on = o == selected
            val bg by animateColorAsState(
                targetValue = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(180),
                label = "segmentBg"
            )
            val itemModifier = if (fill) Modifier.weight(1f) else Modifier
            Box(
                modifier = itemModifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(o) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(o),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sort: SortMode, onSelect: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Change sort order") { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Sort",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sort.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in SortMode.values()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            m.label,
                            fontWeight = if (m == sort) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (m == sort) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(m)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun Chevron(tint: Color) {
    Canvas(Modifier.size(width = 10.dp, height = 6.dp)) {
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2f, size.height)
            lineTo(size.width, 0f)
        }
        drawPath(p, tint, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Ledger
// ---------------------------------------------------------------------------------------------

@Composable
private fun DayHeader(day: ListEntry.Day, win: Color, loss: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "  \u00B7  " + day.count + (if (day.count == 1) " trade" else " trades"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = fmtSigned(day.net),
            style = NumberStyle,
            fontSize = 12.sp,
            color = if (day.net > 0.0) win else loss
        )
    }
}

@Composable
private fun TradeRow(t: Trade, accent: Color, zone: ChartTimeZone, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val rowBg by animateColorAsState(
        targetValue = if (selected) primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(200),
        label = "rowBg"
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(rowBg)
                .clickable(onClick = onClick)
                .semantics { this.selected = selected }
                .height(IntrinsicSize.Min)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (selected) primary else Color.Transparent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                SideGlyph(t.signal.bullish)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = patternName(t.signal.patternType),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = fmtDateTime(zone.toLocal(t.entryTime)) + "  \u2192  " +
                            fmtDateTime(zone.toLocal(t.exitTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fmtPrice(t.entryPrice) + " \u2192 " + fmtPrice(t.exitPrice),
                        style = NumberStyle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = exitText(t.exitReason) + "  \u00B7  " + (t.exitIndex - t.entryIndex) +
                            " bars  \u00B7  " + zone.abbreviationAt(t.exitTime) +
                            "  \u00B7  costs " + fmtMoney(t.fees + t.slippage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = fmtSigned2(t.rMultiple) + " R",
                        style = NumberStyle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent
                    )
                    Text(
                        text = fmtSigned(t.netPnl),
                        style = NumberStyle,
                        fontSize = 12.sp,
                        color = accent
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Box(
            Modifier
                .padding(start = 53.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        )
    }
}

/** Direction marker. Neutral on purpose: colour is reserved for the result of the trade. */
@Composable
private fun SideGlyph(long: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = if (long) "Long" else "Short" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(10.dp)) {
            val p = Path().apply {
                if (long) {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                } else {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                }
                close()
            }
            drawPath(p, tint)
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------

private fun patternName(p: PatternType): String = when (p) {
    PatternType.DOUBLE_TOP -> "Double top"
    PatternType.DOUBLE_BOTTOM -> "Double bottom"
    PatternType.TRIPLE_TOP -> "Triple top"
    PatternType.TRIPLE_BOTTOM -> "Triple bottom"
    PatternType.ELLIOTT_IMPULSE_UP -> "Elliott 5-wave up"
    PatternType.ELLIOTT_IMPULSE_DOWN -> "Elliott 5-wave down"
    PatternType.WAVE3_LONG -> "Wave 3 entry (long)"
    PatternType.WAVE3_SHORT -> "Wave 3 entry (short)"
}

private fun exitText(r: ExitReason): String = when (r) {
    ExitReason.STOP -> "Stopped out"
    ExitReason.TARGET -> "Target hit"
    ExitReason.TRAIL -> "Trailing stop"
    ExitReason.TIME -> "Time limit"
    ExitReason.HOLD -> "Open at end of data"
}

/** "14 Oct 09:30" - caller shifts [ms] into the chosen zone. */
private fun fmtDateTime(ms: Long): String =
    TimeAxisFormatter.dayMonth(ms) + " " + TimeAxisFormatter.hhmm(ms)

private fun fmtPrice(v: Double): String = String.format(Locale.US, "%.5f", v)
private fun fmtMoney(v: Double): String = String.format(Locale.US, "%,.2f", v)
private fun fmtSigned(v: Double): String = (if (v >= 0.0) "+" else "") + fmtMoney(v)
private fun fmtSigned2(v: Double): String = (if (v >= 0.0) "+" else "") + String.format(Locale.US, "%.2f", v)

private fun fmtPct(p: Double): String {
    val sign = if (p >= 0.0) "+" else ""
    return sign + String.format(Locale.US, "%.1f%%", p * 100.0)
}

private fun fmtPf(pf: Double): String = when {
    pf.isInfinite() -> "inf"
    pf.isNaN() -> "0.00"
    else -> String.format(Locale.US, "%.2f", pf)
}
