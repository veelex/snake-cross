package com.auto.rr.domain.pattern

enum class PatternType {
    DOUBLE_TOP,
    DOUBLE_BOTTOM,
    TRIPLE_TOP,
    TRIPLE_BOTTOM,
    /** Completed 5-wave Elliott impulse that rose; expected reversal is down. */
    ELLIOTT_IMPULSE_UP,
    /** Completed 5-wave Elliott impulse that fell; expected reversal is up. */
    ELLIOTT_IMPULSE_DOWN,
    /** Wave 1 + wave 2 pullback seen; a buy limit waits for wave 3 up. Produced by WaveThreeStrategy only. */
    WAVE3_LONG,
    /** Wave 1 + wave 2 pullback seen; a sell limit waits for wave 3 down. Produced by WaveThreeStrategy only. */
    WAVE3_SHORT
}
