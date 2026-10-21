# Elliott wave 3 entry: upgrade notes

## What was added

A new strategy, **Elliott wave 3 entry**, built from your notes. It waits for wave 1 and wave 2, then rests a
limit order at the end of wave 2 to ride wave 3.

| Your notes | In the code |
|---|---|
| Wait for wave 1 and 2, then buy/sell into wave 3 | `WaveThreeStrategy` finds wave 1 (swing p0 to p1) and wave 2 (next swing p2) |
| Wave 2 retraces 38% or 62% | Retracement: Auto / 38.2% / 61.8%, with a tolerance (default +/-7% of wave 1) |
| Buy limit = y_h - r (y_h - y_l); Sell limit = y_l + r (y_h - y_l) | `WaveMath.levels()` (entry) |
| Stop loss = start of wave 1 | `stop = p0` (+ optional ATR buffer, default 0) |
| PTL = top of wave 3, (0.618) | `entry +/- 1.618 x wave 1` (see "Assumptions") |
| Caveat: trading wave C of (a,b,c) is against the trend | "Place the order" option, with the risk spelled out in the hint |

Wave 1 length (y_h - y_l) is the "1.0" unit, exactly as in your notes.

### Worked example (up wave 1, 100 pips, 38.2% pullback)
Wave 1: 1.1000 to 1.1100. Buy limit = 1.1100 - 0.382 x 0.0100 = **1.10618**. Stop = **1.1000**.
PTL = 1.10618 + 1.618 x 0.0100 = **1.12236**. Risk 61.8 pips for 161.8 pips, which is **2.62R** (4.24R at 61.8%).

## How to use it
Indicators sheet, then **Elliott wave 3 entry** card, then switch it on (needs **Swing pivots** on).
- Wave 1/2 are drawn and numbered; entry / SL / PTL are drawn as dashed lines from the moment the order
  is placed until the trade exits (or the order expires).
- **Trade wave 3 setups** (on by default) feeds them to the backtest; the Trades sheet lists them as
  "Wave 3 entry (long/short)".
- Off by default, so existing backtests are unchanged until you switch it on.

## Behaviour you should know about
- **No look-ahead.** A swing point is only known `strength` bars after it happened; the order is placed
  at that later bar.
- **After wave 2 (default)**: wait for wave 2 to finish, then rest the limit at the Fibonacci level. Safer
  (matches "wait for wave 1 and 2"), but price often leaves without a retest, so expect fewer fills.
- **After wave 1**: the limit is already resting while wave 2 falls, so it buys into wave C of the pullback.
  That is the counter-trend trade your caveat warns about. Auto retracement uses 38.2% in this mode.
- An order is dropped if it expires (default 30 bars) or if price passes the end of wave 1 before filling
  ("runs away"). Switchable.
- Backtest fills: a buy limit fills at the limit, or at the open if the bar gaps below it (mirror for
  sells). If one bar touches both the limit and the runaway level, the bar's open decides which came first.
- One position at a time, as before. The engine now orders trades by **fill bar**, so a late-filling limit
  order cannot block or reorder earlier market trades.

## Assumptions to confirm
1. **"PTL (0.618)"** is read as the standard wave 3 = **1.618 x wave 1**, measured from the limit price.
   Selectable: 1.272 / 1.618 / 2.0 / 2.618. If you meant something else, change one setting
   (`WaveThreeConfig.targetExtension`).
2. 38% / 62% are the exact Fibonacci ratios 0.382 / 0.618 (your formulas use 0.38 / 0.62).
3. Wave 1 must break the previous swing (structure break) so a random swing inside a range is not
   treated as wave 1. Switchable. This is a heuristic, not an Elliott wave count.

## Files
New: `domain/strategy/WaveThreeConfig.kt`, `WaveThreeStrategy.kt`, `ui/chart/ChartWaveThreeRenderer.kt`,
`src/test/.../WaveThreeTest.kt`, `tools/wave3_reference/*`.
Changed: `Signal.kt` (limit-order fields, all defaulted), `BacktestEngineExt.kt` (limit fills),
`PatternTypeCore.kt` (+2 enum values), `IndicatorConfigStore.kt` (persistence per timeframe),
`IndicatorTuningDialog.kt` (settings card), `ChartScreen.kt` (wiring), and three exhaustive `when` blocks
(`ChartLineStyles.kt`, `ChartPatternRenderer.kt`, `TradeListContent.kt`).

## Verification status
- **Not compiled.** The build environment used for this work had no Kotlin/Gradle toolchain. Files were
  checked for balanced syntax and against the existing APIs by hand. Please run a Gradle build first.
- The algorithm was ported line-for-line to Python (`tools/wave3_reference`) and checked on synthetic
  price paths: your formulas in both directions, R multiples, no look-ahead, mirror symmetry, retracement
  bands, runaway cancel, expiry, gap fills, same-bar ambiguity, filters. All pass
  (`python3 tools/wave3_reference/run_tests.py`).
- `WaveThreeTest.kt` repeats those scenarios in Kotlin (JUnit4). It has **not been run**.

## Known limitations / suggested next steps
- Presets (the Presets button) do not include the new settings yet, same as the EMA sweep.
- The app has no broker/order execution; limit levels exist on the chart and in the backtest only.
- No higher-timeframe trend filter; the structure-break rule is the only context check.
- Pre-existing, left untouched: `Signal.rMultiple` returns 0 for sell signals (negative `riskPerUnit`).
  Nothing reads it today.
