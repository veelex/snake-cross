package com.auto.rr.data.preset

import com.auto.rr.domain.backtest.BacktestConfig
import com.auto.rr.domain.indicator.EmaConfig
import com.auto.rr.domain.indicator.FvgConfig
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.SessionConfig
import com.auto.rr.domain.indicator.SpiralConfig
import com.auto.rr.domain.indicator.SpiralDirection
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.domain.pattern.PatternConfig
import com.auto.rr.domain.strategy.StrategyConfig
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext

/** The two kinds of configuration the app can save, load, import and export. */
enum class PresetKind(val id: String, val label: String, val fileTag: String) {
    INDICATOR("indicators", "Indicators", "indicators"),
    TUNE("tune", "Tune", "tune")
}

/** Everything the Indicators sheet edits. */
data class IndicatorSnapshot(
    val ema: EmaConfig,
    val fvg: FvgConfig,
    val pivot: PivotConfig,
    val session: SessionConfig,
    val pattern: PatternConfig,
    val spiral: SpiralConfig
)

/** Everything the Tune sheet edits. */
data class TuneSnapshot(
    val strategy: StrategyConfig,
    val backtest: BacktestConfig
)

sealed interface PresetParse<out T> {
    data class Ok<T>(val value: T, val name: String?, val sourceTimeframe: String?) : PresetParse<T>
    data class Error(val message: String) : PresetParse<Nothing>
}

/**
 * JSON (de)serialisation of configurations.
 *
 * File layout (version 1):
 * ```
 * { "app": "AutoRR", "type": "indicators" | "tune", "version": 1,
 *   "name": "...", "timeframe": "M15", "savedAt": 1760000000000,
 *   "config": { ... } }
 * ```
 * Decoding is forgiving: unknown keys are ignored, missing keys fall back to defaults and every
 * value is clamped to a sane range, so a hand-edited or older file can never crash the chart.
 */
object ConfigJson {

    const val VERSION = 1
    private const val APP = "AutoRR"

    // ------------------------------------------------------------------ encode

    fun encodeIndicators(tf: Timeframe, name: String, s: IndicatorSnapshot, savedAt: Long = System.currentTimeMillis()): String {
        val cfg = JSONObject()
        cfg.put("ema", JSONObject().put("enabled", s.ema.enabled))
        cfg.put("fvg", fvgToJson(s.fvg))
        cfg.put("pivot", pivotToJson(s.pivot))
        cfg.put("session", sessionToJson(s.session))
        cfg.put("pattern", patternToJson(s.pattern))
        cfg.put("spiral", spiralToJson(s.spiral))
        return envelope(PresetKind.INDICATOR, tf, name, savedAt, cfg)
    }

    fun encodeTune(tf: Timeframe, name: String, s: TuneSnapshot, savedAt: Long = System.currentTimeMillis()): String {
        val cfg = JSONObject()
        cfg.put("strategy", strategyToJson(s.strategy))
        cfg.put("backtest", backtestToJson(s.backtest))
        return envelope(PresetKind.TUNE, tf, name, savedAt, cfg)
    }

    private fun envelope(kind: PresetKind, tf: Timeframe, name: String, savedAt: Long, cfg: JSONObject): String =
        JSONObject()
            .put("app", APP)
            .put("type", kind.id)
            .put("version", VERSION)
            .put("name", name)
            .put("timeframe", tf.wire)
            .put("savedAt", savedAt)
            .put("config", cfg)
            .toString(2)

    // ------------------------------------------------------------------ decode

    /** Decodes an Indicators file. Timeframe-scaled pattern defaults come from [tf], the timeframe it is applied to. */
    fun decodeIndicators(text: String, tf: Timeframe): PresetParse<IndicatorSnapshot> {
        val env = openEnvelope(text, PresetKind.INDICATOR)
        if (env is EnvelopeResult.Failure) return PresetParse.Error(env.message)
        env as EnvelopeResult.Success
        return try {
            val c = env.config
            val value = IndicatorSnapshot(
                ema = EmaConfig(enabled = c.optJSONObject("ema").bool("enabled", EmaConfig.Default.enabled)),
                fvg = fvgFromJson(c.optJSONObject("fvg")),
                pivot = pivotFromJson(c.optJSONObject("pivot")),
                session = sessionFromJson(c.optJSONObject("session")),
                pattern = patternFromJson(c.optJSONObject("pattern"), tf),
                spiral = spiralFromJson(c.optJSONObject("spiral"))
            )
            PresetParse.Ok(value, env.name, env.timeframe)
        } catch (e: Exception) {
            PresetParse.Error("Could not read the indicator settings in this file.")
        }
    }

    fun decodeTune(text: String): PresetParse<TuneSnapshot> {
        val env = openEnvelope(text, PresetKind.TUNE)
        if (env is EnvelopeResult.Failure) return PresetParse.Error(env.message)
        env as EnvelopeResult.Success
        return try {
            val c = env.config
            val value = TuneSnapshot(
                strategy = strategyFromJson(c.optJSONObject("strategy")),
                backtest = backtestFromJson(c.optJSONObject("backtest"))
            )
            PresetParse.Ok(value, env.name, env.timeframe)
        } catch (e: Exception) {
            PresetParse.Error("Could not read the tune settings in this file.")
        }
    }

    /** Header-only read, used by the preset list to show when and for which timeframe a preset was saved. */
    fun peek(text: String): Pair<Long, String?>? = try {
        val o = JSONObject(text)
        o.optLong("savedAt", 0L) to o.optString("timeframe", "").ifBlank { null }
    } catch (e: JSONException) {
        null
    }

    private sealed interface EnvelopeResult {
        data class Success(val config: JSONObject, val name: String?, val timeframe: String?) : EnvelopeResult
        data class Failure(val message: String) : EnvelopeResult
    }

    private fun openEnvelope(text: String, expected: PresetKind): EnvelopeResult {
        val root = try {
            JSONObject(text.trim().removePrefix("\uFEFF"))
        } catch (e: JSONException) {
            return EnvelopeResult.Failure("This is not a valid configuration file (invalid JSON).")
        }
        if (root.optString("app") != APP) {
            return EnvelopeResult.Failure("This is not an AutoRR configuration file.")
        }
        val type = root.optString("type")
        if (type != expected.id) {
            val other = PresetKind.values().firstOrNull { it.id == type }
            return EnvelopeResult.Failure(
                if (other != null) "This file holds ${other.label} settings, not ${expected.label}."
                else "Unknown configuration type \"$type\"."
            )
        }
        if (root.optInt("version", 1) > VERSION) {
            return EnvelopeResult.Failure("This file was made by a newer version of the app. Please update AutoRR.")
        }
        val config = root.optJSONObject("config")
            ?: return EnvelopeResult.Failure("The file has no \"config\" section.")
        return EnvelopeResult.Success(
            config = config,
            name = root.optString("name", "").trim().ifBlank { null },
            timeframe = root.optString("timeframe", "").ifBlank { null }
        )
    }

    // ------------------------------------------------------------------ Tune

    private fun strategyToJson(s: StrategyConfig) = JSONObject()
        .put("enabled", s.enabled)
        .put("crossWindow", s.crossWindow)
        .putD("convergenceFraction", s.convergenceFraction)
        .put("atrPeriod", s.atrPeriod)
        .putD("stopAtrMultiplier", s.stopAtrMultiplier)
        .putD("targetRMultiple", s.targetRMultiple)

    private fun strategyFromJson(o: JSONObject?): StrategyConfig {
        val d = StrategyConfig.Default
        return StrategyConfig(
            enabled = o.bool("enabled", d.enabled),
            crossWindow = o.int("crossWindow", d.crossWindow, 1, 100),
            convergenceFraction = o.dbl("convergenceFraction", d.convergenceFraction, 0.0, 0.1),
            atrPeriod = o.int("atrPeriod", d.atrPeriod, 2, 200),
            stopAtrMultiplier = o.dbl("stopAtrMultiplier", d.stopAtrMultiplier, 0.1, 10.0),
            targetRMultiple = o.dbl("targetRMultiple", d.targetRMultiple, 0.1, 20.0)
        )
    }

    private fun backtestToJson(b: BacktestConfig) = JSONObject()
        .putD("initialEquity", b.initialEquity)
        .putD("riskFraction", b.riskFraction)
        .putD("feeFraction", b.feeFraction)
        .putD("slippageFraction", b.slippageFraction)
        .putD("trailingAtrMultiplier", b.trailingAtrMultiplier)
        .put("maxHoldingBars", b.maxHoldingBars)

    private fun backtestFromJson(o: JSONObject?): BacktestConfig {
        val d = BacktestConfig.Default
        return BacktestConfig(
            initialEquity = o.dbl("initialEquity", d.initialEquity, 100.0, 1e9),
            riskFraction = o.dbl("riskFraction", d.riskFraction, 0.0001, 0.5),
            feeFraction = o.dbl("feeFraction", d.feeFraction, 0.0, 0.05),
            slippageFraction = o.dbl("slippageFraction", d.slippageFraction, 0.0, 0.05),
            trailingAtrMultiplier = o.dbl("trailingAtrMultiplier", d.trailingAtrMultiplier, 0.0, 10.0),
            maxHoldingBars = o.int("maxHoldingBars", d.maxHoldingBars, 0, 5000)
        )
    }

    // ------------------------------------------------------------------ Indicators

    private fun fvgToJson(f: FvgConfig) = JSONObject()
        .put("enabled", f.enabled)
        .putD("minGapFraction", f.minGapFraction)
        .put("showMitigated", f.showMitigated)
        .put("extensionBars", f.extensionBars)
        .putF("mitigatedAlpha", f.mitigatedAlpha)

    private fun fvgFromJson(o: JSONObject?): FvgConfig {
        val d = FvgConfig.Default
        return FvgConfig(
            enabled = o.bool("enabled", d.enabled),
            minGapFraction = o.dbl("minGapFraction", d.minGapFraction, 0.0, 0.01),
            showMitigated = o.bool("showMitigated", d.showMitigated),
            extensionBars = o.int("extensionBars", d.extensionBars, 1, 500),
            mitigatedAlpha = o.flt("mitigatedAlpha", d.mitigatedAlpha, 0f, 1f)
        )
    }

    private fun pivotToJson(p: PivotConfig) = JSONObject()
        .put("enabled", p.enabled)
        .put("strength", p.strength)
        .putD("prominenceAtr", p.prominenceAtr)

    private fun pivotFromJson(o: JSONObject?): PivotConfig {
        val d = PivotConfig.Default
        return PivotConfig(
            enabled = o.bool("enabled", d.enabled),
            strength = o.int("strength", d.strength, 1, 20),
            prominenceAtr = o.dbl("prominenceAtr", d.prominenceAtr, 0.0, 10.0)
        )
    }

    private fun sessionToJson(s: SessionConfig) = JSONObject()
        .put("enabled", s.enabled)
        .put("tokyoStart", s.tokyoStartHour).put("tokyoEnd", s.tokyoEndHour)
        .put("londonStart", s.londonStartHour).put("londonEnd", s.londonEndHour)
        .put("nyStart", s.nyStartHour).put("nyEnd", s.nyEndHour)
        .put("filterEntries", s.filterEntries)
        .put("boxMode", s.boxMode)
        .put("showLabels", s.showLabels)
        .put("showBorder", s.showBorder)
        .putF("fillOpacity", s.fillOpacity)
        .putF("borderOpacity", s.borderOpacity)

    private fun sessionFromJson(o: JSONObject?): SessionConfig {
        val d = SessionConfig.Default
        return SessionConfig(
            enabled = o.bool("enabled", d.enabled),
            tokyoStartHour = o.int("tokyoStart", d.tokyoStartHour, 0, 24),
            tokyoEndHour = o.int("tokyoEnd", d.tokyoEndHour, 0, 24),
            londonStartHour = o.int("londonStart", d.londonStartHour, 0, 24),
            londonEndHour = o.int("londonEnd", d.londonEndHour, 0, 24),
            nyStartHour = o.int("nyStart", d.nyStartHour, 0, 24),
            nyEndHour = o.int("nyEnd", d.nyEndHour, 0, 24),
            filterEntries = o.bool("filterEntries", d.filterEntries),
            boxMode = o.bool("boxMode", d.boxMode),
            showLabels = o.bool("showLabels", d.showLabels),
            showBorder = o.bool("showBorder", d.showBorder),
            fillOpacity = o.flt("fillOpacity", d.fillOpacity, 0f, 1f),
            borderOpacity = o.flt("borderOpacity", d.borderOpacity, 0f, 1f)
        )
    }

    private fun spiralToJson(s: SpiralConfig): JSONObject {
        val o = JSONObject()
            .put("enabled", s.enabled)
            .putD("growth", s.growthPerQuarterTurn)
            .put("direction", s.direction.name)
            .put("swingOffset", s.swingOffset)
            .putD("outwardTurns", s.outwardTurns)
            .putD("inwardTurns", s.inwardTurns)
            .put("mirror", s.mirror)
            .put("showAnchors", s.showAnchors)
            .put("showQuarterMarks", s.showQuarterMarks)
            .putF("lineWidth", s.lineWidth)
            .putF("opacity", s.opacity)
        // Absent key = "use the theme colour".
        if (s.color != null) o.put("color", s.color)
        // Absent key = follow the latest swing (no tapped pivot).
        if (s.manualThroughTime != 0L) o.put("manualThroughTime", s.manualThroughTime)
        return o
    }

    private fun spiralFromJson(o: JSONObject?): SpiralConfig {
        val d = SpiralConfig.Default
        val dir = runCatching { SpiralDirection.valueOf(o?.optString("direction", d.direction.name) ?: d.direction.name) }
            .getOrDefault(d.direction)
        return SpiralConfig(
            enabled = o.bool("enabled", d.enabled),
            growthPerQuarterTurn = o.dbl("growth", d.growthPerQuarterTurn, 1.01, 10.0),
            direction = dir,
            swingOffset = o.int("swingOffset", d.swingOffset, 0, 50),
            manualThroughTime = o?.optLong("manualThroughTime", d.manualThroughTime) ?: d.manualThroughTime,
            outwardTurns = o.dbl("outwardTurns", d.outwardTurns, 0.0, 10.0),
            inwardTurns = o.dbl("inwardTurns", d.inwardTurns, 0.0, 10.0),
            mirror = o.bool("mirror", d.mirror),
            showAnchors = o.bool("showAnchors", d.showAnchors),
            showQuarterMarks = o.bool("showQuarterMarks", d.showQuarterMarks),
            lineWidth = o.flt("lineWidth", d.lineWidth, 0.5f, 10f),
            opacity = o.flt("opacity", d.opacity, 0f, 1f),
            color = if (o != null && o.has("color") && !o.isNull("color")) o.optInt("color", 0) else null
        )
    }

    /**
     * Only the options the app lets you edit (and persists) are written; every other
     * pattern field stays at its per-timeframe default, exactly like [com.auto.rr.data.IndicatorConfigStore].
     */
    private fun patternToJson(p: PatternConfig) = JSONObject()
        .put("enabled", p.enabled)
        .put("showDoubles", p.showDoubles)
        .put("showTriples", p.showTriples)
        .put("showElliott", p.showElliott)
        .put("elliottInStrategy", p.elliottInStrategy)
        .putD("legToleranceAtr", p.legToleranceAtr)
        .putD("minNeckDepthAtr", p.minNeckDepthAtr)
        .put("minBarsBetweenPivots", p.minBarsBetweenPivots)
        .put("maxPatternBars", p.maxPatternBars)
        .put("confirmWithinBars", p.confirmWithinBars)
        .put("requireNecklineBreak", p.requireNecklineBreak)
        .put("invalidateOnLegBreak", p.invalidateOnLegBreak)
        .put("elliott", JSONObject()
            .putD("minWaveAtr", p.elliottMinWaveAtr)
            .putD("maxWave2Retrace", p.elliottMaxWave2Retrace)
            .putD("maxWave4Retrace", p.elliottMaxWave4Retrace)
            .put("minBarsPerWave", p.elliottMinBarsPerWave)
            .put("maxPatternBars", p.elliottMaxPatternBars)
            .putD("minCorrectionAtr", p.elliottMinCorrectionAtr)
            .putD("minTotalPips", p.elliottMinTotalPips)
            .put("allowTruncated", p.elliottAllowTruncated)
            .put("allowOverlap", p.elliottAllowOverlap)
            .putD("confirmRetrace", p.elliottConfirmRetrace)
            .put("requireBreak", p.elliottRequireBreak)
            .put("confirmWithinBars", p.elliottConfirmWithinBars)
            .putD("confirmOvershootAtr", p.elliottConfirmOvershootAtr)
            .put("invalidateOnBreak", p.elliottInvalidateOnBreak)
            .putD("invalidationAtr", p.elliottInvalidationAtr)
            .put("overlapOthers", p.elliottOverlapOthers)
            .put("pivotStrength", p.elliottPivotStrength)
            .put("degrees", p.elliottDegrees))

    private fun patternFromJson(o: JSONObject?, tf: Timeframe): PatternConfig {
        val d = PatternConfig.defaultsFor(tf)
        val e = o?.optJSONObject("elliott")
        return d.copy(
            enabled = o.bool("enabled", d.enabled),
            showDoubles = o.bool("showDoubles", d.showDoubles),
            showTriples = o.bool("showTriples", d.showTriples),
            showElliott = o.bool("showElliott", d.showElliott),
            elliottInStrategy = o.bool("elliottInStrategy", d.elliottInStrategy),
            legToleranceAtr = o.dbl("legToleranceAtr", d.legToleranceAtr, 0.0, 10.0),
            minNeckDepthAtr = o.dbl("minNeckDepthAtr", d.minNeckDepthAtr, 0.0, 20.0),
            minBarsBetweenPivots = o.int("minBarsBetweenPivots", d.minBarsBetweenPivots, 1, 1000),
            maxPatternBars = o.int("maxPatternBars", d.maxPatternBars, 5, 5000),
            confirmWithinBars = o.int("confirmWithinBars", d.confirmWithinBars, 1, 2000),
            requireNecklineBreak = o.bool("requireNecklineBreak", d.requireNecklineBreak),
            invalidateOnLegBreak = o.bool("invalidateOnLegBreak", d.invalidateOnLegBreak),
            elliottMinWaveAtr = e.dbl("minWaveAtr", d.elliottMinWaveAtr, 0.0, 20.0),
            elliottMaxWave2Retrace = e.dbl("maxWave2Retrace", d.elliottMaxWave2Retrace, 0.0, 1.5),
            elliottMaxWave4Retrace = e.dbl("maxWave4Retrace", d.elliottMaxWave4Retrace, 0.0, 1.5),
            elliottMinBarsPerWave = e.int("minBarsPerWave", d.elliottMinBarsPerWave, 1, 500),
            elliottMaxPatternBars = e.int("maxPatternBars", d.elliottMaxPatternBars, 10, 10_000),
            elliottMinCorrectionAtr = e.dbl("minCorrectionAtr", d.elliottMinCorrectionAtr, 0.0, 20.0),
            elliottMinTotalPips = e.dbl("minTotalPips", d.elliottMinTotalPips, 0.0, 10_000.0),
            elliottAllowTruncated = e.bool("allowTruncated", d.elliottAllowTruncated),
            elliottAllowOverlap = e.bool("allowOverlap", d.elliottAllowOverlap),
            elliottConfirmRetrace = e.dbl("confirmRetrace", d.elliottConfirmRetrace, 0.0, 1.5),
            elliottRequireBreak = e.bool("requireBreak", d.elliottRequireBreak),
            elliottConfirmWithinBars = e.int("confirmWithinBars", d.elliottConfirmWithinBars, 1, 2000),
            elliottConfirmOvershootAtr = e.dbl("confirmOvershootAtr", d.elliottConfirmOvershootAtr, 0.0, 10.0),
            elliottInvalidateOnBreak = e.bool("invalidateOnBreak", d.elliottInvalidateOnBreak),
            elliottInvalidationAtr = e.dbl("invalidationAtr", d.elliottInvalidationAtr, 0.0, 20.0),
            elliottOverlapOthers = e.bool("overlapOthers", d.elliottOverlapOthers),
            elliottPivotStrength = e.int("pivotStrength", d.elliottPivotStrength, 0, 50),
            elliottDegrees = e.int("degrees", d.elliottDegrees, 1, 3)
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun JSONObject?.bool(key: String, def: Boolean): Boolean =
        if (this == null || !has(key) || isNull(key)) def else optBoolean(key, def)

    private fun JSONObject?.int(key: String, def: Int, min: Int, max: Int): Int =
        if (this == null || !has(key) || isNull(key)) def else optInt(key, def).coerceIn(min, max)

    private fun JSONObject?.dbl(key: String, def: Double, min: Double, max: Double): Double {
        if (this == null || !has(key) || isNull(key)) return def
        val v = optDouble(key, def)
        return if (v.isFinite()) v.coerceIn(min, max) else def
    }

    private fun JSONObject?.flt(key: String, def: Float, min: Float, max: Float): Float =
        dbl(key, def.toDouble(), min.toDouble(), max.toDouble()).toFloat()

    /** Doubles are written with 8 significant digits so float-rounded values don't show up as 0.6000000238. */
    private fun JSONObject.putD(key: String, v: Double): JSONObject {
        if (v.isFinite()) put(key, BigDecimal(v).round(MathContext(8)).toDouble())
        return this
    }

    private fun JSONObject.putF(key: String, v: Float): JSONObject {
        if (v.isFinite()) put(key, v.toString().toDouble())
        return this
    }
}
