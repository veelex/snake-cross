// Standalone double/triple pattern checks. Run: tools/verify/run_patterns.sh
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.domain.indicator.AtrCalculator
import com.auto.rr.domain.indicator.PivotConfig
import com.auto.rr.domain.indicator.PivotDetector
import com.auto.rr.domain.indicator.PivotPoint
import com.auto.rr.domain.pattern.DoublePatternDetector
import com.auto.rr.domain.pattern.PatternConfig
import com.auto.rr.domain.pattern.PatternSelector
import com.auto.rr.domain.pattern.PatternType
import com.auto.rr.domain.pattern.PivotNormalizer
import com.auto.rr.domain.pattern.TriplePatternDetector

private var fails = 0
private fun check(name: String, ok: Boolean, detail: String = "") {
    println((if (ok) "PASS " else "FAIL ") + name + (if (detail.isNotEmpty()) "  [$detail]" else ""))
    if (!ok) fails++
}

/** Piecewise-linear close path through (bar, price) waypoints. */
private fun path(vararg wp: Pair<Int, Double>): List<CandleEntity> {
    val out = ArrayList<CandleEntity>()
    var prev = wp[0].second
    for (s in 0 until wp.size - 1) {
        val (b0, p0) = wp[s]; val (b1, p1) = wp[s + 1]
        for (b in b0 until b1) {
            val c = p0 + (p1 - p0) * (b - b0 + 1) / (b1 - b0).toDouble()
            val o = prev
            val turn = if (b == b1 - 1) 0.00005 else 0.0   // sharper extreme on the turning bar
            val hi = maxOf(o, c) + 0.00002 + (if (p1 > p0) turn else 0.0)
            val lo = minOf(o, c) - 0.00002 - (if (p1 < p0) turn else 0.0)
            out += CandleEntity(out.size * 300_000L, "M5", o, hi, lo, c, 0.0)
            prev = c
        }
    }
    return out
}

private const val S = 2
private fun run(candles: List<CandleEntity>, cfg: PatternConfig = PatternConfig.Default): Pair<List<com.auto.rr.domain.pattern.Pattern>, List<com.auto.rr.domain.pattern.Pattern>> {
    val atr = AtrCalculator.atr(candles, 14)
    val piv = PivotDetector.detect(candles, PivotConfig(true, S, prominenceAtr = 0.0), atr)
    return DoublePatternDetector.detect(candles, piv, cfg, atr, S) to TriplePatternDetector.detect(candles, piv, cfg, atr, S)
}

fun main() {
    // lead-in so ATR is warm, then L1 - neck - L2 - breakout
    val db = path(0 to 1.1060, 20 to 1.1000, 40 to 1.0950, 55 to 1.1000, 70 to 1.0951, 85 to 1.1040, 100 to 1.1060)
    val (d, t) = run(db)
    val bottoms = d.filter { it.type == PatternType.DOUBLE_BOTTOM }
    check("double bottom detected", bottoms.size == 1, "n=${bottoms.size}")
    val p = bottoms.firstOrNull()
    check("confirmed after break", p?.confirmationIndex != null)
    check("no look-ahead (conf >= leg2 + strength)", p != null && p.confirmationIndex!! >= p.rightIndex + S)
    check("neckline is neck pivot", p != null && Math.abs(p.neckline - p.pivotPrices[1]) < 1e-12)
    check("no triple on a double", t.isEmpty())

    // legs break first: price collapses below leg low before reclaiming neckline
    val fail = path(0 to 1.1060, 20 to 1.1000, 40 to 1.0950, 55 to 1.1000, 70 to 1.0951, 80 to 1.0900, 100 to 1.1060)
    check("invalidated when legs break", run(fail).first.none { it.type == PatternType.DOUBLE_BOTTOM && it.pivotIndices.size == 3 && it.rightIndex < 75 && it.confirmationIndex != null })

    // forming pattern is only emitted when break is not required
    val forming = path(0 to 1.1060, 20 to 1.1000, 40 to 1.0950, 55 to 1.1000, 70 to 1.0951, 78 to 1.0975)
    check("forming hidden when break required", run(forming).first.isEmpty())
    val f2 = run(forming, PatternConfig(requireNecklineBreak = false)).first
    check("forming shown when break not required", f2.size == 1 && f2[0].confirmationIndex == null)

    // too-uneven legs are rejected (ATR tolerance)
    val uneven = path(0 to 1.1060, 20 to 1.1000, 40 to 1.0950, 55 to 1.1000, 70 to 1.0985, 85 to 1.1040, 100 to 1.1060)
    check("uneven legs rejected", run(uneven).first.none { it.type == PatternType.DOUBLE_BOTTOM })

    // triple bottom beats the doubles inside it
    val tb = path(0 to 1.1080, 20 to 1.1000, 40 to 1.0950, 52 to 1.1000, 64 to 1.0950, 76 to 1.1002, 88 to 1.0951, 103 to 1.1045, 118 to 1.1060)
    val (d3, t3) = run(tb)
    check("triple bottom detected", t3.any { it.type == PatternType.TRIPLE_BOTTOM && it.isConfirmed })
    val sel = PatternSelector.select(d3, t3)
    check("selector keeps the triple only", sel.size == 1 && sel[0].type == PatternType.TRIPLE_BOTTOM, sel.map { it.type }.toString())

    // alternation: two highs in a row collapse to the higher one
    val raw = listOf(PivotPoint(10, 1.0950, false), PivotPoint(20, 1.0990, true), PivotPoint(25, 1.1000, true), PivotPoint(40, 1.0951, false))
    val norm = PivotNormalizer.alternate(raw)
    check("same-side pivots collapse to the extreme", norm.size == 3 && norm[1].price == 1.1000)

    println(if (fails == 0) "\nALL PATTERN CHECKS PASSED" else "\n$fails PATTERN CHECK(S) FAILED")
    if (fails > 0) kotlin.system.exitProcess(1)
}
