package com.auto.rr.data

import android.content.Context
import com.auto.rr.domain.model.Timeframe

/** Remembers the timeframe the chart was last showing so the app reopens where it was left. */
object TimeframeStore {
    private const val PREFS = "auto_rr.ui"
    private const val KEY = "chart.timeframe"

    fun load(ctx: Context): Timeframe {
        val wire = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return wire?.let(Timeframe::fromWire) ?: Timeframe.M1
    }

    fun save(ctx: Context, tf: Timeframe) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tf.wire).apply()
    }
}
