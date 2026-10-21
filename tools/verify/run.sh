#!/usr/bin/env bash
# Compiles the pure-Kotlin data logic + Verify.kt with kotlinc and runs the checks.
# Usage: KOTLINC=/path/to/kotlinc tools/verify/run.sh   (defaults to kotlinc on PATH)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/src/main/java/com/auto/rr"
OUT="$(mktemp -d)"
"${KOTLINC:-kotlinc}" -nowarn \
  "$SRC/data/local/CandleEntity.kt" "$SRC/data/remote/ws/TickModel.kt" "$SRC/data/remote/ws/TickFilter.kt" \
  "$SRC/data/sanitize/CandleSanitizer.kt" "$SRC/data/sync/M1BarBuilder.kt" "$SRC/domain/model/Timeframe.kt" \
  "$ROOT/tools/verify/Verify.kt" -include-runtime -d "$OUT/verify.jar" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -jar "$OUT/verify.jar" 2>&1 | grep -v JAVA_TOOL_OPTIONS
