// Dependency-free checks for the pure-Kotlin data logic. Run: tools/verify/run.sh
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.data.remote.ws.Tick
import com.auto.rr.data.remote.ws.TickFilter
import com.auto.rr.data.sanitize.CandleSanitizer
import com.auto.rr.data.sync.M1BarBuilder
import kotlin.math.abs
import kotlin.random.Random

private var failures = 0
private fun check(name: String, ok: Boolean, detail: String = "") {
    println((if (ok) "PASS " else "FAIL ") + name + (if (detail.isNotEmpty()) "  [$detail]" else ""))
    if (!ok) failures++
}

private fun series(n: Int, seed: Int, step: Long = 300_000L): MutableList<CandleEntity> {
    val r = Random(seed)
    var p = 1.1020
    return MutableList(n) { i ->
        val o = p
        val c = o + (r.nextDouble() - 0.5) * 0.00010
        val h = maxOf(o, c) + r.nextDouble() * 0.00006
        val l = minOf(o, c) - r.nextDouble() * 0.00006
        p = c
        CandleEntity(i * step, "M5", o, h, l, c, 0.0)
    }
}

private fun candleChecks() {
    val clean = series(300, 7)
    val dirty = clean.toMutableList()
    val bad = mutableSetOf<Int>()
    // lone needles up / down
    for (i in listOf(30, 75, 120, 160, 210)) { dirty[i] = dirty[i].copy(high = dirty[i].high + 0.0040); bad += i }
    for (i in listOf(50, 140, 250)) { dirty[i] = dirty[i].copy(low = dirty[i].low - 0.0035); bad += i }
    // fully bad bars (open/close/high/low all displaced)
    for (i in listOf(90, 180)) { val c = dirty[i]; dirty[i] = c.copy(open = c.open + 0.0050, high = c.high + 0.0052, low = c.low + 0.0049, close = c.close + 0.0050); bad += i }
    // adjacent pair of bad bars (like the paired M15 spikes)
    dirty[200] = dirty[200].copy(high = dirty[200].high + 0.0030); dirty[201] = dirty[201].copy(low = dirty[201].low - 0.0045); bad += 200; bad += 201

    val out = CandleSanitizer.sanitize(dirty)
    check("size preserved", out.size == dirty.size)
    val maxRangeAfter = out.maxOf { it.high - it.low }
    check("no needle remains (max bar range < 8 pips)", maxRangeAfter < 0.0008, "max=%.5f".format(maxRangeAfter))
    val worstDev = bad.maxOf { i -> maxOf(abs(out[i].high - clean[i].high), abs(out[i].low - clean[i].low)) }
    check("bad bars pulled back near true values (< 4 pips)", worstDev < 0.0004, "worst=%.5f".format(worstDev))
    val untouched = (0 until clean.size).filter { it !in bad }
    val changed = untouched.count { out[it] != dirty[it] }
    check("clean bars essentially untouched (<2% changed)", changed.toDouble() / untouched.size < 0.02, "changed=$changed/${untouched.size}")
    check("OHLC invariants hold", out.all { it.high >= maxOf(it.open, it.close) && it.low <= minOf(it.open, it.close) })

    // legitimate level shift must survive
    val step = series(120, 3).toMutableList()
    for (i in 60 until 120) step[i] = step[i].let { it.copy(open = it.open + 0.0030, high = it.high + 0.0030, low = it.low + 0.0030, close = it.close + 0.0030) }
    val so = CandleSanitizer.sanitize(step)
    val shiftKept = (58..62).all { abs(so[it].close - step[it].close) < 0.00005 && abs(so[it].high - step[it].high) < 0.00005 }
    check("genuine 30-pip level shift is preserved", shiftKept)

    // legitimate trend preserved
    val trend = MutableList(100) { i -> val p = 1.10 + i * 0.00020; CandleEntity(i * 60_000L, "M1", p, p + 0.0002, p - 0.00005, p + 0.00015, 0.0) }
    check("steady trend untouched", CandleSanitizer.sanitize(trend) == trend)

    // edge bars and tiny inputs
    check("empty input", CandleSanitizer.sanitize(emptyList()).isEmpty())
    check("single bar", CandleSanitizer.sanitize(listOf(clean[0])).size == 1)
    val edgeDirty = clean.take(50).toMutableList(); edgeDirty[49] = edgeDirty[49].copy(high = edgeDirty[49].high + 0.004); edgeDirty[0] = edgeDirty[0].copy(low = edgeDirty[0].low - 0.004)
    val eo = CandleSanitizer.sanitize(edgeDirty)
    check("needles on first/last bar are clamped", eo[0].high - eo[0].low < 0.0006 && eo[49].high - eo[49].low < 0.0006)
    val nan = clean.take(20).toMutableList(); nan[10] = nan[10].copy(high = Double.NaN)
    check("NaN row does not propagate", CandleSanitizer.sanitize(nan).all { it.high.isFinite() && it.low.isFinite() })
}

private fun tickChecks() {
    fun t(p: Double, ts: Long = 1_000_000L) = Tick("EUR/USD", p, ts)
    val f = TickFilter()
    f.seed(1.1200)
    check("normal tick accepted", f.accept(t(1.12002), 1_000_000L))
    check("lone spike rejected", !f.accept(t(1.1250), 1_000_000L))
    check("price after spike accepted (spike never became last)", f.accept(t(1.12004), 1_000_000L))
    val g = TickFilter(); g.seed(1.1200)
    check("level shift: first tick held", !g.accept(t(1.1210), 1_000_000L))
    check("level shift: confirmed by second tick", g.accept(t(1.12102), 1_000_000L))
    check("level shift: continues normally", g.accept(t(1.12105), 1_000_000L))
    val h = TickFilter(); h.seed(1.1200)
    check("seeded filter rejects bad very first tick", !h.accept(t(1.2000), 1_000_000L))
    check("future-dated tick rejected", !TickFilter().accept(t(1.12, 9_000_000_000L), 1_000_000L))
    check("NaN / zero price rejected", !TickFilter().accept(t(Double.NaN), 0L) && !TickFilter().accept(t(0.0), 0L))
}

private fun builderChecks() {
    val b = M1BarBuilder()
    val m = 60_000L
    val u1 = b.add(10 * m + 5, 1.1000)!!
    check("first tick opens bar", u1.isNewBar && u1.current.open == 1.1000)
    b.add(10 * m + 9000, 1.1004); b.add(10 * m + 20000, 1.0998)
    val cur = b.current!!
    check("high/low/close tracked", cur.high == 1.1004 && cur.low == 1.0998 && cur.close == 1.0998)
    val u2 = b.add(11 * m + 1, 1.1001)!!
    check("rollover returns closed bar", u2.closed?.timestamp == 10 * m && u2.current.timestamp == 11 * m)
    check("late tick ignored", b.add(10 * m + 30000, 1.2000) == null && b.current!!.high == 1.1001)
    b.mergeStored(CandleEntity(11 * m, "M1", 1.0995, 1.1010, 1.0990, 1.1000, 0.0))
    check("stored bar merged (open kept, range widened)", b.current!!.open == 1.0995 && b.current!!.high == 1.1010 && b.current!!.close == 1.1001)
}

fun main() {
    candleChecks(); tickChecks(); builderChecks()
    println(if (failures == 0) "\nALL CHECKS PASSED" else "\n$failures CHECK(S) FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
