package com.auto.rr.domain.backtest

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.strategy.EntryType
import com.auto.rr.domain.strategy.Signal
import kotlin.math.abs

object BacktestEngine {

    fun run(
        candles: List<CandleEntity>,
        signals: List<Signal>,
        cfg: BacktestConfig
    ): BacktestResult {
        if (candles.isEmpty() || signals.isEmpty()) return BacktestResult.empty(cfg)

        // Work out where each signal actually gets into the market. A limit order fills later than
        // its trigger bar (or never), so order by the fill bar, not by the trigger bar.
        val entries = ArrayList<PendingEntry>(signals.size)
        for (sig in signals) {
            if (sig.entryType == EntryType.LIMIT) {
                val fill = resolveLimitFill(candles, sig) ?: continue
                entries += PendingEntry(sig, fill.first, fill.second)
            } else {
                val idx = sig.triggerIndex + 1
                if (idx >= candles.size) continue
                entries += PendingEntry(sig, idx, candles[idx].open)
            }
        }
        entries.sortWith(compareBy<PendingEntry> { it.index }.thenBy { it.signal.triggerIndex })

        val trades = ArrayList<Trade>()
        val curve = ArrayList<EquityPoint>()
        curve.add(EquityPoint(candles.first().timestamp, cfg.initialEquity))

        var equity = cfg.initialEquity
        var peakEquity = cfg.initialEquity
        var maxDrawdown = 0.0
        var nextFreeIdx = 0

        for (pe in entries) {
            val s = pe.signal
            val entryIdx = pe.index
            if (entryIdx >= candles.size) continue
            if (entryIdx < nextFreeIdx) continue

            val entryBar = candles[entryIdx]
            val entryPrice = pe.price
            val fixedStop = s.stopPrice
            val target = s.targetPrice

            val riskPerUnit = abs(entryPrice - fixedStop)
            if (riskPerUnit <= 0.0 || !riskPerUnit.isFinite()) continue

            val riskAmount = equity * cfg.riskFraction
            if (riskAmount <= 0.0) continue
            val qty = riskAmount / riskPerUnit
            if (!qty.isFinite() || qty <= 0.0) continue

            val trailDistance = if (cfg.trailingAtrMultiplier > 0.0 && s.atr14AtTrigger > 0.0)
                cfg.trailingAtrMultiplier * s.atr14AtTrigger
            else 0.0
            val trailingEnabled = trailDistance > 0.0

            // Favorable extreme starts at the entry bar's extreme.
            var favorableExtreme = if (s.bullish) entryBar.high else entryBar.low
            // Effective stop starts at the fixed stop, only moves in the trade's favour.
            var effectiveStop = fixedStop

            var exitIdx = -1
            var exitPrice = 0.0
            var exitReason = ExitReason.HOLD

            for (i in entryIdx until candles.size) {
                val c = candles[i]

                // Check the current bar against the CURRENT effective stop (from prior extremes).
                if (s.bullish) {
                    if (c.low <= effectiveStop) {
                        exitIdx = i
                        exitPrice = effectiveStop
                        exitReason = if (effectiveStop > fixedStop) ExitReason.TRAIL else ExitReason.STOP
                        break
                    }
                    if (c.high >= target) {
                        exitIdx = i; exitPrice = target; exitReason = ExitReason.TARGET; break
                    }
                } else {
                    if (c.high >= effectiveStop) {
                        exitIdx = i
                        exitPrice = effectiveStop
                        exitReason = if (effectiveStop < fixedStop) ExitReason.TRAIL else ExitReason.STOP
                        break
                    }
                    if (c.low <= target) {
                        exitIdx = i; exitPrice = target; exitReason = ExitReason.TARGET; break
                    }
                }

                // Max holding period: close at this bar's close if the limit is reached.
                if (cfg.maxHoldingBars > 0 && (i - entryIdx) >= cfg.maxHoldingBars) {
                    exitIdx = i
                    exitPrice = c.close
                    exitReason = ExitReason.TIME
                    break
                }

                // Update trailing for the NEXT bar.
                if (s.bullish) {
                    if (c.high > favorableExtreme) favorableExtreme = c.high
                } else {
                    if (c.low < favorableExtreme) favorableExtreme = c.low
                }
                if (trailingEnabled) {
                    effectiveStop = if (s.bullish) {
                        maxOf(fixedStop, favorableExtreme - trailDistance)
                    } else {
                        minOf(fixedStop, favorableExtreme + trailDistance)
                    }
                }
            }

            if (exitIdx < 0) {
                exitIdx = candles.size - 1
                exitPrice = candles[exitIdx].close
                exitReason = ExitReason.HOLD
            }

            val exitBar = candles[exitIdx]
            val grossPnl = if (s.bullish) qty * (exitPrice - entryPrice)
                           else qty * (entryPrice - exitPrice)

            val notional = (entryPrice + exitPrice) * qty
            val fees = notional * cfg.feeFraction
            val slippage = notional * cfg.slippageFraction
            val netPnl = grossPnl - fees - slippage

            equity += netPnl
            if (equity > peakEquity) peakEquity = equity
            if (peakEquity > 0.0) {
                val dd = (peakEquity - equity) / peakEquity
                if (dd > maxDrawdown) maxDrawdown = dd
            }

            val rMult = if (riskAmount > 0.0) netPnl / riskAmount else 0.0

            trades.add(
                Trade(
                    signal = s,
                    entryIndex = entryIdx,
                    exitIndex = exitIdx,
                    entryTime = entryBar.timestamp,
                    exitTime = exitBar.timestamp,
                    entryPrice = entryPrice,
                    exitPrice = exitPrice,
                    qty = qty,
                    grossPnl = grossPnl,
                    fees = fees,
                    slippage = slippage,
                    netPnl = netPnl,
                    riskAmount = riskAmount,
                    rMultiple = rMult,
                    exitReason = exitReason
                )
            )

            curve.add(EquityPoint(exitBar.timestamp, equity))
            nextFreeIdx = exitIdx + 1
        }

        return summarize(cfg, trades, curve, equity, maxDrawdown)
    }

    /** A signal together with the bar and price at which it enters the market. */
    private class PendingEntry(val signal: Signal, val index: Int, val price: Double)

    /**
     * Finds where a resting limit order fills: the first bar after [Signal.triggerIndex] (up to
     * [Signal.expiryIndex]) that trades through the limit price [Signal.triggerPrice].
     *
     * The order is dropped (null) if price trades through [Signal.cancelPrice] first. When a single
     * bar reaches both levels the bar's open decides which came first: whichever level the open is
     * nearer to is assumed to be hit first.
     *
     * A buy limit fills at the limit price, or at the open when the bar gaps below it (a better
     * price); a sell limit mirrors that. Returns (bar index, fill price).
     */
    internal fun resolveLimitFill(candles: List<CandleEntity>, s: Signal): Pair<Int, Double>? {
        val first = s.triggerIndex + 1
        if (first >= candles.size) return null
        val last = if (s.expiryIndex >= 0) minOf(s.expiryIndex, candles.size - 1) else candles.size - 1
        val limit = s.triggerPrice
        val cancel = s.cancelPrice
        val hasCancel = cancel.isFinite()

        for (i in first..last) {
            val c = candles[i]
            val touched = if (s.bullish) c.low <= limit else c.high >= limit
            val ran = hasCancel && (if (s.bullish) c.high >= cancel else c.low <= cancel)

            if (touched && ran && abs(c.open - cancel) < abs(c.open - limit)) return null
            if (touched) {
                val price = if (s.bullish) minOf(c.open, limit) else maxOf(c.open, limit)
                return Pair(i, price)
            }
            if (ran) return null
        }
        return null
    }

    private fun summarize(
        cfg: BacktestConfig,
        trades: List<Trade>,
        curve: List<EquityPoint>,
        finalEquity: Double,
        maxDrawdown: Double
    ): BacktestResult {
        if (trades.isEmpty()) return BacktestResult.empty(cfg)

        var wins = 0
        var losses = 0
        var grossWins = 0.0
        var grossLosses = 0.0
        var sumR = 0.0

        for (t in trades) {
            sumR += t.rMultiple
            if (t.netPnl > 0.0) {
                wins++
                grossWins += t.netPnl
            } else {
                losses++
                grossLosses += -t.netPnl
            }
        }

        val total = trades.size
        val winRate = wins.toDouble() / total
        val netProfit = finalEquity - cfg.initialEquity
        val netProfitPct = if (cfg.initialEquity > 0.0) netProfit / cfg.initialEquity else 0.0
        val profitFactor = if (grossLosses > 0.0) grossWins / grossLosses
                           else if (grossWins > 0.0) Double.POSITIVE_INFINITY
                           else 0.0
        val avgR = sumR / total

        return BacktestResult(
            initialEquity = cfg.initialEquity,
            finalEquity = finalEquity,
            trades = trades,
            equityCurve = curve,
            totalTrades = total,
            wins = wins,
            losses = losses,
            winRate = winRate,
            netProfit = netProfit,
            netProfitPct = netProfitPct,
            profitFactor = profitFactor,
            maxDrawdownPct = maxDrawdown,
            avgR = avgR
        )
    }
}
