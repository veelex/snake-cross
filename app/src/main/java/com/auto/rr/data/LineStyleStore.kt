package com.auto.rr.data

import android.content.Context
import android.content.SharedPreferences
import com.auto.rr.ui.chart.ChartLineStyles
import com.auto.rr.ui.chart.ElliottLabelFormat
import com.auto.rr.ui.chart.ElliottLabelStyle
import com.auto.rr.ui.chart.LineStyle
import com.auto.rr.ui.chart.RiskRewardBoxStyle

/**
 * Line styles are global (not per timeframe): a colour picked once applies on every timeframe.
 */
object LineStyleStore {

    private const val PREFS = "auto_rr.line_styles"
    private val EMA_PERIODS = listOf(1, 5, 8, 13, 55)

    fun load(ctx: Context): ChartLineStyles {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = ChartLineStyles.Default
        val emaColors = HashMap<Int, Int>()
        for (period in EMA_PERIODS) {
            val key = "ema.color.$period"
            if (p.contains(key)) emaColors[period] = p.getInt(key, 0)
        }
        return ChartLineStyles(
            ema = get(p, "ema", d.ema),
            emaColors = emaColors,
            doubles = get(p, "doubles", d.doubles),
            triples = get(p, "triples", d.triples),
            elliott = get(p, "elliott", d.elliott),
            doublesNeck = get(p, "doublesNeck", d.doublesNeck),
            triplesNeck = get(p, "triplesNeck", d.triplesNeck),
            elliottNeck = get(p, "elliottNeck", d.elliottNeck),
            tradePath = get(p, "tradePath", d.tradePath),
            riskReward = getBox(p, d.riskReward),
            grid = get(p, "grid", d.grid),
            lastPrice = get(p, "lastPrice", d.lastPrice),
            elliottLabels = getLabels(p, d.elliottLabels)
        )
    }

    fun save(ctx: Context, s: ChartLineStyles) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        put(e, "ema", s.ema)
        for (period in EMA_PERIODS) {
            val c = s.emaColors[period]
            if (c != null) e.putInt("ema.color.$period", c) else e.remove("ema.color.$period")
        }
        put(e, "doubles", s.doubles)
        put(e, "triples", s.triples)
        put(e, "elliott", s.elliott)
        put(e, "doublesNeck", s.doublesNeck)
        put(e, "triplesNeck", s.triplesNeck)
        put(e, "elliottNeck", s.elliottNeck)
        put(e, "tradePath", s.tradePath)
        putBox(e, s.riskReward)
        put(e, "grid", s.grid)
        put(e, "lastPrice", s.lastPrice)
        putLabels(e, s.elliottLabels)
        e.apply()
    }

    private fun getBox(p: SharedPreferences, d: RiskRewardBoxStyle) = RiskRewardBoxStyle(
        showOutline = p.getBoolean("rrbox.outline", d.showOutline),
        outlineWidth = p.getFloat("rrbox.outlineWidth", d.outlineWidth),
        outlineOpacity = p.getFloat("rrbox.outlineOpacity", d.outlineOpacity),
        fillOpacity = p.getFloat("rrbox.fillOpacity", d.fillOpacity),
        rewardColor = if (p.contains("rrbox.reward")) p.getInt("rrbox.reward", 0) else null,
        riskColor = if (p.contains("rrbox.risk")) p.getInt("rrbox.risk", 0) else null,
        showResult = p.getBoolean("rrbox.result", d.showResult)
    )

    private fun putBox(e: SharedPreferences.Editor, b: RiskRewardBoxStyle) {
        e.putBoolean("rrbox.outline", b.showOutline)
        e.putFloat("rrbox.outlineWidth", b.outlineWidth)
        e.putFloat("rrbox.outlineOpacity", b.outlineOpacity)
        e.putFloat("rrbox.fillOpacity", b.fillOpacity)
        if (b.rewardColor != null) e.putInt("rrbox.reward", b.rewardColor) else e.remove("rrbox.reward")
        if (b.riskColor != null) e.putInt("rrbox.risk", b.riskColor) else e.remove("rrbox.risk")
        e.putBoolean("rrbox.result", b.showResult)
    }

    private fun getLabels(p: SharedPreferences, d: ElliottLabelStyle): ElliottLabelStyle {
        val fmt = runCatching { ElliottLabelFormat.valueOf(p.getString("ewl.format", d.format.name) ?: d.format.name) }
            .getOrDefault(d.format)
        return ElliottLabelStyle(
            show = p.getBoolean("ewl.show", d.show),
            format = fmt,
            sizeSp = p.getFloat("ewl.size", d.sizeSp),
            bold = p.getBoolean("ewl.bold", d.bold),
            color = if (p.contains("ewl.color")) p.getInt("ewl.color", 0) else null,
            opacity = p.getFloat("ewl.opacity", d.opacity),
            distance = p.getFloat("ewl.distance", d.distance),
            markUnconfirmed = p.getBoolean("ewl.markUnconfirmed", d.markUnconfirmed)
        )
    }

    private fun putLabels(e: SharedPreferences.Editor, l: ElliottLabelStyle) {
        e.putBoolean("ewl.show", l.show)
        e.putString("ewl.format", l.format.name)
        e.putFloat("ewl.size", l.sizeSp)
        e.putBoolean("ewl.bold", l.bold)
        if (l.color != null) e.putInt("ewl.color", l.color) else e.remove("ewl.color")
        e.putFloat("ewl.opacity", l.opacity)
        e.putFloat("ewl.distance", l.distance)
        e.putBoolean("ewl.markUnconfirmed", l.markUnconfirmed)
    }

    private fun get(p: SharedPreferences, key: String, d: LineStyle): LineStyle = LineStyle(
        width = p.getFloat("$key.width", d.width),
        opacity = p.getFloat("$key.opacity", d.opacity),
        color = if (p.contains("$key.color")) p.getInt("$key.color", 0) else null
    )

    private fun put(e: SharedPreferences.Editor, key: String, s: LineStyle) {
        e.putFloat("$key.width", s.width)
        e.putFloat("$key.opacity", s.opacity)
        if (s.color != null) e.putInt("$key.color", s.color) else e.remove("$key.color")
    }
}
