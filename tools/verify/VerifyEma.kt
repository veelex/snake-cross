// Standalone EMA checks. Compile with:
//   kotlinc src/main/java/com/auto/rr/data/local/CandleEntity.kt src/main/java/com/auto/rr/domain/indicator/EmaCalculator.kt tools/verify/VerifyEma.kt -include-runtime -d verify_ema.jar
import com.auto.rr.domain.indicator.EmaCalculator
import kotlin.math.abs

private var emaFailures = 0
private fun ec(name: String, ok: Boolean) {
    println((if (ok) "PASS " else "FAIL ") + name)
    if (!ok) emaFailures++
}

fun main() {
    val closes = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
    val e3 = EmaCalculator.ema(closes, 3)
    ec("warmup is NaN", e3[0].isNaN() && e3[1].isNaN())
    ec("seeds on period-th value", abs(e3[2] - 2.0) < 1e-9)
    val k = 2.0 / 4.0
    ec("step matches formula", abs(e3[3] - (4.0 * k + 2.0 * (1 - k))) < 1e-9)
    ec("length matches input", e3.size == closes.size)
    val e1 = EmaCalculator.ema(closes, 1)
    ec("period=1 equals input", e1.indices.all { abs(e1[it] - closes[it]) < 1e-9 })
    val e9 = EmaCalculator.ema(closes, 9)
    ec("period>size all NaN", e9.all { it.isNaN() })
    ec("empty input empty output", EmaCalculator.ema(DoubleArray(0), 5).isEmpty())
    println(if (emaFailures == 0) "EMA ALL PASSED" else "EMA " + emaFailures + " FAILED")
    if (emaFailures > 0) kotlin.system.exitProcess(1)
}
