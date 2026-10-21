#!/usr/bin/env bash
# Compiles the pattern domain logic + VerifyPatterns.kt and runs the checks.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/src/main/java/com/auto/rr"
OUT="$(mktemp -d)"
"${KOTLINC:-kotlinc}" -nowarn \
  "$SRC/data/local/CandleEntity.kt" "$SRC/domain/indicator/PivotPoint.kt" "$SRC/domain/indicator/PivotConfig.kt" \
  "$SRC/domain/indicator/PivotDetector.kt" "$SRC/domain/indicator/AtrCalculator.kt" \
  "$SRC/domain/pattern/PatternConfigCore.kt" "$SRC/domain/pattern/PatternCore.kt" "$SRC/domain/pattern/PatternTypeCore.kt" \
  "$SRC/domain/pattern/PivotNormalizer.kt" "$SRC/domain/pattern/DoublePatternDetectorCore.kt" \
  "$SRC/domain/pattern/TriplePatternDetectorCore.kt" "$SRC/domain/pattern/PatternSelector.kt" \
  "$ROOT/tools/verify/VerifyPatterns.kt" -include-runtime -d "$OUT/vp.jar" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -jar "$OUT/vp.jar" 2>&1 | grep -v JAVA_TOOL_OPTIONS
