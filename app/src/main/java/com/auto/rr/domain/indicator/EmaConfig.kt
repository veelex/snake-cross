package com.auto.rr.domain.indicator

data class EmaConfig(
    val periods: List<Int> = listOf(1, 5, 8, 13, 55),
    val enabled: Boolean = true
) {
    companion object {
        val Default = EmaConfig()
    }
}
