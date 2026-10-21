package com.auto.rr.domain.indicator

data class FvgConfig(
    val enabled: Boolean = true,
    /** Minimum gap size, as a fraction of price (~0.16 pip at EUR/USD 1.09). */
    val minGapFraction: Double = 1.5e-5,
    /** Keep zones visible after their mitigation index. */
    val showMitigated: Boolean = true,
    /** Fixed width in bars: every zone is drawn exactly this wide from formation. */
    val extensionBars: Int = 12,
    /** Alpha applied to zones past their mitigation index (0..1). */
    val mitigatedAlpha: Float = 0.55f
) {
    companion object {
        val Default = FvgConfig()
    }
}
