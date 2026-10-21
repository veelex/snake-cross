# AutoRR

Android (Compose) EUR/USD chart: REST candles (Twelve Data) + live WebSocket ticks aggregated into M1.

## Data pipeline
- `ForexRestApi` → `CandleDto` (structural validation) → SQLite (`CandleDao`), raw values kept.
- `ForexWebSocketClient` → `TickFilter` (drops stray prints) → `M1BarBuilder` → `M1Aggregator` (throttled live writes, merges with stored bar).
- `CandleRepository.observe()` runs `CandleSanitizer` so the chart never sees needle candles.

## Tuning
- `CandleSanitizer.Config`: `rangeMultiple` (lower = stricter wicks), `minBandFraction`.
- `TickFilter(maxJumpFraction, confirmTicks)`.

## Checks
`KOTLINC=/path/to/kotlinc tools/verify/run.sh` – compiles the pure-Kotlin logic and runs the checks.

## Patterns (double/triple tops & bottoms)
- Pivots are forced to alternate high/low before detection (`PivotNormalizer`).
- Leg tolerance and neck depth are ATR-based (`PatternConfig`), tunable per timeframe in Indicators → Double / Triple patterns.
- Confirmation is searched only once the last leg pivot is known (no look-ahead), within `confirmWithinBars`; patterns whose legs break first are dropped.
- `PatternSelector` resolves overlaps by confirmed → quality → time.
- `tools/verify/run_patterns.sh` runs the pattern checks.

## Chart UI
- Timeframe is a dropdown button (top-left); Ind / Tune / Trades sit next to it.
- Ind, Tune and Trades open a docked, non-modal bottom sheet below the chart (tap its handle for compact/tall). The chart, stats line and equity curve stay visible and update live while you tune.
