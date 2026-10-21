package com.auto.rr.ui.chart

import android.content.Context
import android.content.Intent
import com.auto.rr.domain.backtest.BacktestResult
import com.auto.rr.ui.status.StatusBus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun exportTrades(context: Context, result: BacktestResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Snake Cross backtest trades")
        putExtra(Intent.EXTRA_TEXT, buildCsv(result))
    }
    StatusBus.info("Exported trade list", detail = result.trades.size.toString() + " trades")
    context.startActivity(Intent.createChooser(intent, "Export trades"))
}

private fun buildCsv(result: BacktestResult): String {
    val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val sb = StringBuilder()
    sb.append("reason,entry_time_utc,exit_time_utc,entry_idx,exit_idx,")
    sb.append("entry_price,exit_price,qty,net_pnl,r_multiple,direction,pattern")
    sb.append('\n')
    for (t in result.trades) {
        sb.append(t.exitReason.name).append(',')
        sb.append(iso.format(Date(t.entryTime))).append(',')
        sb.append(iso.format(Date(t.exitTime))).append(',')
        sb.append(t.entryIndex).append(',')
        sb.append(t.exitIndex).append(',')
        sb.append(fmtPrice(t.entryPrice)).append(',')
        sb.append(fmtPrice(t.exitPrice)).append(',')
        sb.append(fmtPrice(t.qty)).append(',')
        sb.append(fmtMoney(t.netPnl)).append(',')
        sb.append(fmtR(t.rMultiple)).append(',')
        sb.append(if (t.signal.bullish) "LONG" else "SHORT").append(',')
        sb.append(t.signal.patternType.name).append('\n')
    }
    return sb.toString()
}

private fun fmtPrice(v: Double): String = String.format(Locale.US, "%.4f", v)
private fun fmtMoney(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmtR(v: Double): String = String.format(Locale.US, "%.2f", v)

