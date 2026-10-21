package com.auto.rr.domain.indicator

/** Which way the spiral winds, as seen on screen. */
enum class SpiralDirection(val label: String) {
    /** Follows the swing: counter-clockwise after an up-swing, clockwise after a down-swing. */
    AUTO("Auto"),
    CLOCKWISE("Clockwise"),
    COUNTER_CLOCKWISE("Counter")
}

/**
 * Options for the logarithmic (Fibonacci / golden) spiral.
 *
 * The spiral is anchored on two swing pivots: it winds around the older one (the pole) and passes
 * through the newer one. See [LogSpiral].
 */
data class SpiralConfig(
    val enabled: Boolean = false,
    /** Radius multiplier per quarter turn. 1.618 (phi) is the golden spiral. */
    val growthPerQuarterTurn: Double = LogSpiral.GOLDEN,
    val direction: SpiralDirection = SpiralDirection.AUTO,
    /** 0 = anchor on the latest swing, 1 = the swing before it, and so on. */
    val swingOffset: Int = 0,
    /**
     * Candle timestamp of a pivot the user tapped on the chart, or 0 to follow [swingOffset].
     * Stored as a time rather than an index so it survives new history being loaded.
     */
    val manualThroughTime: Long = 0L,
    /** Full turns drawn outward from the second anchor (towards the future). */
    val outwardTurns: Double = 2.0,
    /** Full turns drawn inward from the second anchor, towards the pole. */
    val inwardTurns: Double = 1.5,
    /** Also draw the mirror-image spiral (opposite winding) through the same anchors. */
    val mirror: Boolean = false,
    val showAnchors: Boolean = true,
    val showQuarterMarks: Boolean = false,
    val lineWidth: Float = 1.5f,
    val opacity: Float = 0.85f,
    /** ARGB override, or null for the theme's default spiral colour. */
    val color: Int? = null
) {
    companion object {
        val Default = SpiralConfig()
    }
}
