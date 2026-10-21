package com.auto.rr.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.auto.rr.R

/**
 * The app's typefaces. Everything in Type.kt reads from here.
 *
 * Files live in src/main/res/font/ (singular), lowercase with underscores:
 *   geist_regular, geist_medium, geist_semibold, geist_bold
 *   outfit_medium, outfit_semibold, outfit_bold
 *   geist_mono_regular, geist_mono_medium
 */
object AppFonts {
    /** Body text, labels and controls. */
    val Sans = FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
        Font(R.font.geist_semibold, FontWeight.SemiBold),
        Font(R.font.geist_bold, FontWeight.Bold)
    )

    /** Headlines, titles and card titles. */
    val Display = FontFamily(
        Font(R.font.outfit_medium, FontWeight.Medium),
        Font(R.font.outfit_semibold, FontWeight.SemiBold),
        Font(R.font.outfit_bold, FontWeight.Bold)
    )

    /** Prices, stats and other aligned numbers. */
    val Mono = FontFamily(
        Font(R.font.geist_mono_regular, FontWeight.Normal),
        Font(R.font.geist_mono_medium, FontWeight.Medium)
    )
}
