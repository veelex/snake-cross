package com.auto.rr.ui.chart

import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.backtest.Trade
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Computes "normal" chart framing: how wide a candle is, where the view starts, and what price
 * range is shown. Used for the initial view and for double-tap-to-reset on each axis.
 *
 * Normalised framing means:
 *  - time: about [TARGET_VISIBLE_BARS] candles fill the plot regardless of screen size, with a few
 *    bars of empty space after the newest candle so it does not touch the price axis;
 *  - price: fitted to the candles actually on screen, but never squeezed into a sliver. In a flat
 *    market the range is widened to at least [MIN_SPAN_IN_CANDLES] typical candle heights, so
 *    small moves are not blown up into huge swings.
 */
object ChartViewport {

    /** Candles that should fit across the plot at the default zoom. */
    const val TARGET_VISIBLE_BARS = 90
    /** Empty bars kept to the right of the newest candle at the default zoom. */
    const val RIGHT_MARGIN_BARS = 8

    private const val MIN_X_SCALE_DEFAULT = 3f
    private const val MAX_X_SCALE_DEFAULT = 24f

    private const val PAD_FRACTION = 0.10
    private const val MIN_PAD = 0.00010
    /** Minimum displayed price span, in multiples of the median candle height. */
    private const val MIN_SPAN_IN_CANDLES = 10.0
    /** Absolute minimum displayed span as a fraction of price (~8 pips on EUR/USD). */
    private const val MIN_SPAN_FRACTION = 0.0007

    /** Pixels per candle at the default zoom for a plot [viewWidthPx] wide. */
    fun defaultXScale(viewWidthPx: Float): Float {
        if (viewWidthPx <= 0f) return ChartTransform.DEFAULT_X_SCALE
        val s = viewWidthPx / (TARGET_VISIBLE_BARS + RIGHT_MARGIN_BARS)
        return s.coerceIn(MIN_X_SCALE_DEFAULT, MAX_X_SCALE_DEFAULT)
    }

    /** Full default framing: normal candle width, newest candle at the right, price fitted. */
    fun init(candles: List<CandleEntity>, viewWidthPx: Float = 0f): ChartTransform {
        if (candles.isEmpty()) {
            return ChartTransform(0f, defaultXScale(viewWidthPx), 0.0, 1.0)
        }
        val scale = defaultXScale(viewWidthPx)
        val visibleBars = if (viewWidthPx > 0f) viewWidthPx / scale else ChartTransform.DEFAULT_VISIBLE_CANDLES.toFloat()
        val total = candles.size
        val xStart = max(0f, total + RIGHT_MARGIN_BARS - visibleBars)
        return fitPrice(candles, ChartTransform(xStart, scale, 0.0, 1.0), viewWidthPx)
    }

    /** Trade framing: context bars kept on each side of the trade (at least), and the fewest bars shown. */
    private const val FOCUS_MIN_PAD_BARS = 10
    private const val FOCUS_MIN_VISIBLE_BARS = 40
    private const val FOCUS_MAX_X_SCALE = 30f

    /**
     * Frames one trade: entry to exit centred, with context either side (half the trade's length,
     * at least [FOCUS_MIN_PAD_BARS] bars, and never fewer than [FOCUS_MIN_VISIBLE_BARS] bars in
     * view). Price is fitted to the candles on screen plus the entry and exit, and the signal's stop
     * and target when they do not squash the view (they are skipped if they would more than double
     * the visible range).
     */
    fun focusTrade(candles: List<CandleEntity>, trade: Trade, viewWidthPx: Float): ChartTransform {
        if (candles.isEmpty()) return ChartTransform(0f, defaultXScale(viewWidthPx), 0.0, 1.0)
        val lastIdx = candles.size - 1
        val entry = trade.entryIndex.coerceIn(0, lastIdx)
        val exit = trade.exitIndex.coerceIn(entry, lastIdx)
        val span = exit - entry

        val width = if (viewWidthPx > 0f) viewWidthPx else ChartTransform.DEFAULT_X_SCALE * ChartTransform.DEFAULT_VISIBLE_CANDLES
        val pad = max(FOCUS_MIN_PAD_BARS, span / 2)
        val bars = max(span + 2 * pad, FOCUS_MIN_VISIBLE_BARS)
        val scale = (width / bars).coerceIn(ChartTransform.MIN_X_SCALE, FOCUS_MAX_X_SCALE)
        val visible = width / scale
        val xStart = (entry + exit) / 2f + 0.5f - visible / 2f

        val first = floor(xStart).toInt().coerceIn(0, lastIdx)
        val lastVisible = ceil(xStart + visible).toInt().coerceIn(first, lastIdx)
        val slice = candles.subList(first, lastVisible + 1)

        val core = listOf(trade.entryPrice, trade.exitPrice).filter { it.isFinite() }
        val (baseLo, baseHi) = priceRange(slice, core)
        val baseSpan = baseHi - baseLo
        val optional = listOf(trade.signal.stopPrice, trade.signal.targetPrice)
            .filter { it.isFinite() && it > baseLo - baseSpan * 0.5 && it < baseHi + baseSpan * 0.5 }
        val (lo, hi) = if (optional.isEmpty()) baseLo to baseHi else priceRange(slice, core + optional)
        return ChartTransform(xStart, scale, lo, hi)
    }

    /** Double-tap on the price axis: refit price to the candles currently on screen. */
    fun fitPrice(
        candles: List<CandleEntity>,
        current: ChartTransform,
        viewWidthPx: Float
    ): ChartTransform {
        if (candles.isEmpty()) return current
        val width = if (viewWidthPx > 0f) viewWidthPx else current.xScale * ChartTransform.DEFAULT_VISIBLE_CANDLES
        val first = current.xStart.toInt().coerceIn(0, candles.size - 1)
        val last = ((current.xStart + width / current.xScale).toInt() + 1)
            .coerceIn(first + 1, candles.size)
        val (lo, hi) = priceRange(candles.subList(first, last))
        return current.withPriceRange(lo, hi)
    }

    /**
     * Double-tap on the time axis: back to the default candle width. The newest bar currently
     * visible on the right stays where it is, so the user does not lose their place.
     */
    fun resetTime(current: ChartTransform, viewWidthPx: Float): ChartTransform {
        if (viewWidthPx <= 0f) return current
        val rightEdge = current.xStart + viewWidthPx / current.xScale
        val scale = defaultXScale(viewWidthPx)
        return current.copy(xStart = rightEdge - viewWidthPx / scale, xScale = scale)
    }

    @Deprecated("Use fitPrice", ReplaceWith("fitPrice(candles, current, viewWidthPx)"))
    fun refitPrice(candles: List<CandleEntity>, current: ChartTransform, viewWidthPx: Float) =
        fitPrice(candles, current, viewWidthPx)

    /** Candles are sanitised upstream, so the range is min/max plus padding, floored for flat markets. */
    private fun priceRange(slice: List<CandleEntity>, extras: List<Double> = emptyList()): Pair<Double, Double> {
        if (slice.isEmpty()) return 0.0 to 1.0
        var hi = Double.NEGATIVE_INFINITY
        var lo = Double.POSITIVE_INFINITY
        for (c in slice) { hi = max(hi, c.high); lo = min(lo, c.low) }
        for (x in extras) { hi = max(hi, x); lo = min(lo, x) }

        val heights = DoubleArray(slice.size) { slice[it].high - slice[it].low }.also { it.sort() }
        val medianHeight = heights[heights.size / 2]
        val mid = (hi + lo) / 2.0
        val floor = max(medianHeight * MIN_SPAN_IN_CANDLES, mid * MIN_SPAN_FRACTION)

        val rawSpan = (hi - lo).coerceAtLeast(0.0)
        val span = max(rawSpan * (1.0 + 2 * PAD_FRACTION), floor)
        if (span <= 0.0) return (lo - MIN_PAD) to (hi + MIN_PAD)
        return (mid - span / 2.0) to (mid + span / 2.0)
    }
}
