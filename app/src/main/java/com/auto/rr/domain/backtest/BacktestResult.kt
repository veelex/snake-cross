package com.auto.rr.domain.backtest

data class EquityPoint(val time: Long, val equity: Double)

data class BacktestResult(
    val initialEquity: Double,
    val finalEquity: Double,
    val trades: List<Trade>,
    val equityCurve: List<EquityPoint>,
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val netProfit: Double,
    val netProfitPct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val avgR: Double
) {
    companion object {
        fun empty(cfg: BacktestConfig): BacktestResult = BacktestResult(
            initialEquity = cfg.initialEquity,
            finalEquity = cfg.initialEquity,
            trades = emptyList(),
            equityCurve = emptyList(),
            totalTrades = 0,
            wins = 0,
            losses = 0,
            winRate = 0.0,
            netProfit = 0.0,
            netProfitPct = 0.0,
            profitFactor = 0.0,
            maxDrawdownPct = 0.0,
            avgR = 0.0
        )
    }
}
