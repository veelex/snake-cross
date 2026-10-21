package com.auto.rr.data

import android.content.Context
import com.auto.rr.domain.backtest.BacktestConfig
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.domain.strategy.StrategyConfig

object ConfigStore {

    private const val PREFS_PREFIX = "auto_rr.config."

    private fun prefsName(tf: Timeframe) = PREFS_PREFIX + tf.wire

    fun loadStrategy(ctx: Context, tf: Timeframe): StrategyConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = StrategyConfig.Default
        return StrategyConfig(
            enabled = p.getBoolean("enabled", d.enabled),
            crossWindow = p.getInt("crossWindow", d.crossWindow),
            convergenceFraction = p.getFloat("convergenceFraction", d.convergenceFraction.toFloat()).toDouble(),
            atrPeriod = p.getInt("atrPeriod", d.atrPeriod),
            stopAtrMultiplier = p.getFloat("stopAtrMultiplier", d.stopAtrMultiplier.toFloat()).toDouble(),
            targetRMultiple = p.getFloat("targetRMultiple", d.targetRMultiple.toFloat()).toDouble()
        )
    }

    fun loadBacktest(ctx: Context, tf: Timeframe): BacktestConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = BacktestConfig.Default
        return BacktestConfig(
            initialEquity = p.getFloat("initialEquity", d.initialEquity.toFloat()).toDouble(),
            riskFraction = p.getFloat("riskFraction", d.riskFraction.toFloat()).toDouble(),
            feeFraction = p.getFloat("feeFraction", d.feeFraction.toFloat()).toDouble(),
            slippageFraction = p.getFloat("slippageFraction", d.slippageFraction.toFloat()).toDouble(),
            trailingAtrMultiplier = p.getFloat("trailingAtrMultiplier", d.trailingAtrMultiplier.toFloat()).toDouble(),
            maxHoldingBars = p.getInt("maxHoldingBars", d.maxHoldingBars)
        )
    }

    fun save(ctx: Context, tf: Timeframe, s: StrategyConfig, b: BacktestConfig) {
        val e = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE).edit()
        e.putBoolean("enabled", s.enabled)
        e.putInt("crossWindow", s.crossWindow)
        e.putFloat("convergenceFraction", s.convergenceFraction.toFloat())
        e.putInt("atrPeriod", s.atrPeriod)
        e.putFloat("stopAtrMultiplier", s.stopAtrMultiplier.toFloat())
        e.putFloat("targetRMultiple", s.targetRMultiple.toFloat())
        e.putFloat("initialEquity", b.initialEquity.toFloat())
        e.putFloat("riskFraction", b.riskFraction.toFloat())
        e.putFloat("feeFraction", b.feeFraction.toFloat())
        e.putFloat("slippageFraction", b.slippageFraction.toFloat())
        e.putFloat("trailingAtrMultiplier", b.trailingAtrMultiplier.toFloat())
        e.putInt("maxHoldingBars", b.maxHoldingBars)
        e.apply()
    }
}
