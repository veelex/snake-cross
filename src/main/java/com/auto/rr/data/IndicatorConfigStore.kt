package com.auto.rr.data

import android.content.Context
import com.auto.rr.domain.indicator.EmaConfig
import com.auto.rr.domain.indicator.EmaSweepConfig
import com.auto.rr.domain.indicator.FvgConfig
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.indicator.SpiralConfig
import com.auto.rr.domain.indicator.SpiralDirection
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.domain.pattern.PatternConfig
import com.auto.rr.domain.strategy.WaveEntryTiming
import com.auto.rr.domain.strategy.WaveRetrace
import com.auto.rr.domain.strategy.WaveThreeConfig

object IndicatorConfigStore {

    private const val PREFS_PREFIX = "auto_rr.indicators."

    private fun prefsName(tf: Timeframe) = PREFS_PREFIX + tf.wire

    fun loadEma(ctx: Context, tf: Timeframe): EmaConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = EmaConfig.Default
        return EmaConfig(
            periods = d.periods,
            enabled = p.getBoolean("ema.enabled", d.enabled)
        )
    }

    fun loadSweep(ctx: Context, tf: Timeframe): EmaSweepConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = EmaSweepConfig.Default
        return EmaSweepConfig(
            enabled = p.getBoolean("sweep.enabled", d.enabled),
            tradeSweeps = p.getBoolean("sweep.trade", d.tradeSweeps),
            sweepBars = p.getInt("sweep.bars", d.sweepBars),
            nearAtr = p.getFloat("sweep.nearAtr", d.nearAtr.toFloat()).toDouble(),
            nearBars = p.getInt("sweep.nearBars", d.nearBars)
        )
    }

    fun saveSweep(ctx: Context, tf: Timeframe, c: EmaSweepConfig) {
        ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE).edit()
            .putBoolean("sweep.enabled", c.enabled)
            .putBoolean("sweep.trade", c.tradeSweeps)
            .putInt("sweep.bars", c.sweepBars)
            .putFloat("sweep.nearAtr", c.nearAtr.toFloat())
            .putInt("sweep.nearBars", c.nearBars)
            .apply()
    }

    fun loadWave(ctx: Context, tf: Timeframe): WaveThreeConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = WaveThreeConfig.Default
        return WaveThreeConfig(
            enabled = p.getBoolean("wave3.enabled", d.enabled),
            tradeSetups = p.getBoolean("wave3.trade", d.tradeSetups),
            timing = runCatching { WaveEntryTiming.valueOf(p.getString("wave3.timing", d.timing.name) ?: d.timing.name) }
                .getOrDefault(d.timing),
            retrace = runCatching { WaveRetrace.valueOf(p.getString("wave3.retrace", d.retrace.name) ?: d.retrace.name) }
                .getOrDefault(d.retrace),
            retraceTolerance = rd(p, "wave3.tolerance", d.retraceTolerance),
            targetExtension = rd(p, "wave3.extension", d.targetExtension),
            stopBufferAtr = rd(p, "wave3.stopBufferAtr", d.stopBufferAtr),
            minWave1Atr = rd(p, "wave3.minWave1Atr", d.minWave1Atr),
            minBarsPerWave = p.getInt("wave3.minBars", d.minBarsPerWave),
            requireStructureBreak = p.getBoolean("wave3.structureBreak", d.requireStructureBreak),
            pivotStrength = p.getInt("wave3.pivotStrength", d.pivotStrength),
            expiryBars = p.getInt("wave3.expiryBars", d.expiryBars),
            cancelOnRunaway = p.getBoolean("wave3.cancelOnRunaway", d.cancelOnRunaway)
        )
    }

    fun saveWave(ctx: Context, tf: Timeframe, c: WaveThreeConfig) {
        ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE).edit()
            .putBoolean("wave3.enabled", c.enabled)
            .putBoolean("wave3.trade", c.tradeSetups)
            .putString("wave3.timing", c.timing.name)
            .putString("wave3.retrace", c.retrace.name)
            .putFloat("wave3.tolerance", c.retraceTolerance.toFloat())
            .putFloat("wave3.extension", c.targetExtension.toFloat())
            .putFloat("wave3.stopBufferAtr", c.stopBufferAtr.toFloat())
            .putFloat("wave3.minWave1Atr", c.minWave1Atr.toFloat())
            .putInt("wave3.minBars", c.minBarsPerWave)
            .putBoolean("wave3.structureBreak", c.requireStructureBreak)
            .putInt("wave3.pivotStrength", c.pivotStrength)
            .putInt("wave3.expiryBars", c.expiryBars)
            .putBoolean("wave3.cancelOnRunaway", c.cancelOnRunaway)
            .apply()
    }

    fun loadFvg(ctx: Context, tf: Timeframe): FvgConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = FvgConfig.Default
        return FvgConfig(
            enabled = p.getBoolean("fvg.enabled", d.enabled),
            minGapFraction = p.getFloat("fvg.minGapFraction", d.minGapFraction.toFloat()).toDouble(),
            showMitigated = p.getBoolean("fvg.showMitigated", d.showMitigated),
            extensionBars = p.getInt("fvg.extensionBars", d.extensionBars),
            mitigatedAlpha = p.getFloat("fvg.mitigatedAlpha", d.mitigatedAlpha)
        )
    }

    fun loadPivot(ctx: Context, tf: Timeframe): PivotConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = PivotConfig.Default
        return PivotConfig(
            enabled = p.getBoolean("pivot.enabled", d.enabled),
            strength = p.getInt("pivot.strength", d.strength),
            prominenceAtr = p.getFloat("pivot.prominenceAtr", d.prominenceAtr.toFloat()).toDouble().let { Math.round(it * 1000.0) / 1000.0 }
        )
    }

    fun loadPattern(ctx: Context, tf: Timeframe): PatternConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = PatternConfig.defaultsFor(tf)
        // copy() keeps every field that is not user-editable (pip floor, trend filter, Elliott
        // limits, ...) at its per-timeframe default instead of the generic class default.
        return d.copy(
            enabled = p.getBoolean("pattern.enabled", d.enabled),
            showDoubles = p.getBoolean("pattern.showDoubles", d.showDoubles),
            showTriples = p.getBoolean("pattern.showTriples", d.showTriples),
            showElliott = p.getBoolean("pattern.showElliott", d.showElliott),
            elliottInStrategy = p.getBoolean("pattern.elliottInStrategy", d.elliottInStrategy),
            elliottMinWaveAtr = rd(p, "pattern.elliottMinWaveAtr", d.elliottMinWaveAtr),
            elliottMaxWave2Retrace = rd(p, "pattern.ew.maxW2", d.elliottMaxWave2Retrace),
            elliottMaxWave4Retrace = rd(p, "pattern.ew.maxW4", d.elliottMaxWave4Retrace),
            elliottMinBarsPerWave = p.getInt("pattern.ew.minBars", d.elliottMinBarsPerWave),
            elliottMaxPatternBars = p.getInt("pattern.ew.maxBars", d.elliottMaxPatternBars),
            elliottMinCorrectionAtr = rd(p, "pattern.ew.minCorrAtr", d.elliottMinCorrectionAtr),
            elliottMinTotalPips = rd(p, "pattern.ew.minPips", d.elliottMinTotalPips),
            elliottAllowTruncated = p.getBoolean("pattern.ew.truncated", d.elliottAllowTruncated),
            elliottAllowOverlap = p.getBoolean("pattern.ew.overlap", d.elliottAllowOverlap),
            elliottConfirmRetrace = rd(p, "pattern.ew.confirmRetrace", d.elliottConfirmRetrace),
            elliottRequireBreak = p.getBoolean("pattern.ew.requireBreak", d.elliottRequireBreak),
            elliottConfirmWithinBars = p.getInt("pattern.ew.confirmBars", d.elliottConfirmWithinBars),
            elliottConfirmOvershootAtr = rd(p, "pattern.ew.overshootAtr", d.elliottConfirmOvershootAtr),
            elliottInvalidateOnBreak = p.getBoolean("pattern.ew.invalidate", d.elliottInvalidateOnBreak),
            elliottInvalidationAtr = rd(p, "pattern.ew.invalidAtr", d.elliottInvalidationAtr),
            elliottOverlapOthers = p.getBoolean("pattern.ew.overlapOthers", d.elliottOverlapOthers),
            elliottPivotStrength = p.getInt("pattern.ew.pivotStrength", d.elliottPivotStrength),
            elliottDegrees = p.getInt("pattern.ew.degrees", d.elliottDegrees),
            legToleranceAtr = rd(p, "pattern.legTolAtr", d.legToleranceAtr),
            minNeckDepthAtr = rd(p, "pattern.neckDepthAtr", d.minNeckDepthAtr),
            minBarsBetweenPivots = p.getInt("pattern.minBars", d.minBarsBetweenPivots),
            maxPatternBars = p.getInt("pattern.maxBars", d.maxPatternBars),
            confirmWithinBars = p.getInt("pattern.confirmBars", d.confirmWithinBars),
            requireNecklineBreak = p.getBoolean("pattern.requireBreak", d.requireNecklineBreak),
            invalidateOnLegBreak = p.getBoolean("pattern.invalidate", d.invalidateOnLegBreak)
        )
    }

    /** Float-stored double, rounded so that "equals the default?" checks survive the round trip. */
    private fun rd(p: android.content.SharedPreferences, key: String, def: Double): Double =
        Math.round(p.getFloat(key, def.toFloat()).toDouble() * 10_000.0) / 10_000.0

    fun loadSession(ctx: Context, tf: Timeframe): SessionConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = SessionConfig.Default
        return SessionConfig(
            enabled = p.getBoolean("session.enabled.v2", d.enabled),
            tokyoStartHour = p.getInt("session.tokyoStart", d.tokyoStartHour),
            tokyoEndHour = p.getInt("session.tokyoEnd", d.tokyoEndHour),
            londonStartHour = p.getInt("session.londonStart", d.londonStartHour),
            londonEndHour = p.getInt("session.londonEnd", d.londonEndHour),
            nyStartHour = p.getInt("session.nyStart", d.nyStartHour),
            nyEndHour = p.getInt("session.nyEnd", d.nyEndHour),
            filterEntries = p.getBoolean("session.filterEntries", d.filterEntries),
            boxMode = p.getBoolean("session.boxMode", d.boxMode),
            showLabels = p.getBoolean("session.showLabels", d.showLabels),
            showBorder = p.getBoolean("session.showBorder", d.showBorder),
            fillOpacity = p.getFloat("session.fillOpacity", d.fillOpacity),
            borderOpacity = p.getFloat("session.borderOpacity", d.borderOpacity)
        )
    }

    fun loadSpiral(ctx: Context, tf: Timeframe): SpiralConfig {
        val p = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE)
        val d = SpiralConfig.Default
        val dir = runCatching { SpiralDirection.valueOf(p.getString("spiral.direction", d.direction.name) ?: d.direction.name) }
            .getOrDefault(d.direction)
        return SpiralConfig(
            enabled = p.getBoolean("spiral.enabled", d.enabled),
            growthPerQuarterTurn = p.getFloat("spiral.growth", d.growthPerQuarterTurn.toFloat()).toDouble()
                // Stored as a float; snap back to the exact default so "modified?" stays accurate.
                .let { if (kotlin.math.abs(it - d.growthPerQuarterTurn) < 1e-4) d.growthPerQuarterTurn else it },
            direction = dir,
            swingOffset = p.getInt("spiral.swingOffset", d.swingOffset),
            manualThroughTime = p.getLong("spiral.manualTime", d.manualThroughTime),
            outwardTurns = p.getFloat("spiral.outTurns", d.outwardTurns.toFloat()).toDouble(),
            inwardTurns = p.getFloat("spiral.inTurns", d.inwardTurns.toFloat()).toDouble(),
            mirror = p.getBoolean("spiral.mirror", d.mirror),
            showAnchors = p.getBoolean("spiral.showAnchors", d.showAnchors),
            showQuarterMarks = p.getBoolean("spiral.quarterMarks", d.showQuarterMarks),
            lineWidth = p.getFloat("spiral.lineWidth", d.lineWidth),
            opacity = p.getFloat("spiral.opacity", d.opacity),
            color = if (p.contains("spiral.color")) p.getInt("spiral.color", 0) else null
        )
    }

    fun save(
        ctx: Context,
        tf: Timeframe,
        ema: EmaConfig,
        fvg: FvgConfig,
        pivot: PivotConfig,
        session: SessionConfig,
        pattern: PatternConfig = PatternConfig.Default,
        spiral: SpiralConfig = SpiralConfig.Default
    ) {
        val e = ctx.getSharedPreferences(prefsName(tf), Context.MODE_PRIVATE).edit()
        e.putBoolean("ema.enabled", ema.enabled)
        e.putBoolean("fvg.enabled", fvg.enabled)
        e.putFloat("fvg.minGapFraction", fvg.minGapFraction.toFloat())
        e.putBoolean("fvg.showMitigated", fvg.showMitigated)
        e.putInt("fvg.extensionBars", fvg.extensionBars)
        e.putFloat("fvg.mitigatedAlpha", fvg.mitigatedAlpha)
        e.putBoolean("pivot.enabled", pivot.enabled)
        e.putInt("pivot.strength", pivot.strength)
        e.putFloat("pivot.prominenceAtr", pivot.prominenceAtr.toFloat())
        e.putBoolean("pattern.enabled", pattern.enabled)
        e.putBoolean("pattern.showDoubles", pattern.showDoubles)
        e.putBoolean("pattern.showTriples", pattern.showTriples)
        e.putBoolean("pattern.showElliott", pattern.showElliott)
        e.putBoolean("pattern.elliottInStrategy", pattern.elliottInStrategy)
        e.putFloat("pattern.elliottMinWaveAtr", pattern.elliottMinWaveAtr.toFloat())
        e.putFloat("pattern.ew.maxW2", pattern.elliottMaxWave2Retrace.toFloat())
        e.putFloat("pattern.ew.maxW4", pattern.elliottMaxWave4Retrace.toFloat())
        e.putInt("pattern.ew.minBars", pattern.elliottMinBarsPerWave)
        e.putInt("pattern.ew.maxBars", pattern.elliottMaxPatternBars)
        e.putFloat("pattern.ew.minCorrAtr", pattern.elliottMinCorrectionAtr.toFloat())
        e.putFloat("pattern.ew.minPips", pattern.elliottMinTotalPips.toFloat())
        e.putBoolean("pattern.ew.truncated", pattern.elliottAllowTruncated)
        e.putBoolean("pattern.ew.overlap", pattern.elliottAllowOverlap)
        e.putFloat("pattern.ew.confirmRetrace", pattern.elliottConfirmRetrace.toFloat())
        e.putBoolean("pattern.ew.requireBreak", pattern.elliottRequireBreak)
        e.putInt("pattern.ew.confirmBars", pattern.elliottConfirmWithinBars)
        e.putFloat("pattern.ew.overshootAtr", pattern.elliottConfirmOvershootAtr.toFloat())
        e.putBoolean("pattern.ew.invalidate", pattern.elliottInvalidateOnBreak)
        e.putFloat("pattern.ew.invalidAtr", pattern.elliottInvalidationAtr.toFloat())
        e.putBoolean("pattern.ew.overlapOthers", pattern.elliottOverlapOthers)
        e.putInt("pattern.ew.pivotStrength", pattern.elliottPivotStrength)
        e.putInt("pattern.ew.degrees", pattern.elliottDegrees)
        e.putFloat("pattern.legTolAtr", pattern.legToleranceAtr.toFloat())
        e.putFloat("pattern.neckDepthAtr", pattern.minNeckDepthAtr.toFloat())
        e.putInt("pattern.minBars", pattern.minBarsBetweenPivots)
        e.putInt("pattern.maxBars", pattern.maxPatternBars)
        e.putInt("pattern.confirmBars", pattern.confirmWithinBars)
        e.putBoolean("pattern.requireBreak", pattern.requireNecklineBreak)
        e.putBoolean("pattern.invalidate", pattern.invalidateOnLegBreak)
        e.putBoolean("spiral.enabled", spiral.enabled)
        e.putFloat("spiral.growth", spiral.growthPerQuarterTurn.toFloat())
        e.putString("spiral.direction", spiral.direction.name)
        e.putInt("spiral.swingOffset", spiral.swingOffset)
        e.putLong("spiral.manualTime", spiral.manualThroughTime)
        e.putFloat("spiral.outTurns", spiral.outwardTurns.toFloat())
        e.putFloat("spiral.inTurns", spiral.inwardTurns.toFloat())
        e.putBoolean("spiral.mirror", spiral.mirror)
        e.putBoolean("spiral.showAnchors", spiral.showAnchors)
        e.putBoolean("spiral.quarterMarks", spiral.showQuarterMarks)
        e.putFloat("spiral.lineWidth", spiral.lineWidth)
        e.putFloat("spiral.opacity", spiral.opacity)
        if (spiral.color != null) e.putInt("spiral.color", spiral.color) else e.remove("spiral.color")
        e.putBoolean("session.enabled.v2", session.enabled)
        e.putInt("session.tokyoStart", session.tokyoStartHour)
        e.putInt("session.tokyoEnd", session.tokyoEndHour)
        e.putInt("session.londonStart", session.londonStartHour)
        e.putInt("session.londonEnd", session.londonEndHour)
        e.putInt("session.nyStart", session.nyStartHour)
        e.putInt("session.nyEnd", session.nyEndHour)
        e.putBoolean("session.filterEntries", session.filterEntries)
        e.putBoolean("session.boxMode", session.boxMode)
        e.putBoolean("session.showLabels", session.showLabels)
        e.putBoolean("session.showBorder", session.showBorder)
        e.putFloat("session.fillOpacity", session.fillOpacity)
        e.putFloat("session.borderOpacity", session.borderOpacity)
        e.apply()
    }
}
