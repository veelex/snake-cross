package com.auto.rr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Brass300,
    onPrimary = Color(0xFF1E1606),
    primaryContainer = BrassContainerDark,
    onPrimaryContainer = Color(0xFFF1DFB0),
    secondary = Ink200,
    onSecondary = Ink900,
    secondaryContainer = Ink600,
    onSecondaryContainer = Ink100,
    tertiary = Brass300,
    background = Ink900,
    onBackground = Ink100,
    surface = Ink800,
    onSurface = Ink100,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink200,
    surfaceTint = Color.Transparent,
    outline = Ink300,
    outlineVariant = Ink500,
    error = StatusErrDark,
    onError = Ink900
)

private val LightColors = lightColorScheme(
    primary = Brass700,
    onPrimary = Color.White,
    primaryContainer = BrassContainerLight,
    onPrimaryContainer = Color(0xFF2B1F00),
    secondary = Paper700,
    onSecondary = Color.White,
    secondaryContainer = Paper200,
    onSecondaryContainer = Paper900,
    tertiary = Brass700,
    background = Paper100,
    onBackground = Paper900,
    surface = Paper50,
    onSurface = Paper900,
    surfaceVariant = Paper200,
    onSurfaceVariant = Paper700,
    surfaceTint = Color.Transparent,
    outline = Paper400,
    outlineVariant = Paper300,
    error = StatusErrLight,
    onError = Color.White
)

/** Container radii: tight on inner controls, softer on the surfaces that hold them. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Immutable
data class StatusPalette(
    val ok: Color,
    val sync: Color,
    val warn: Color,
    val err: Color,
    val test: Color
)

private val DarkStatus = StatusPalette(StatusOkDark, StatusSyncDark, StatusWarnDark, StatusErrDark, StatusTestDark)
private val LightStatus = StatusPalette(StatusOkLight, StatusSyncLight, StatusWarnLight, StatusErrLight, StatusTestLight)

val LocalStatusPalette = staticCompositionLocalOf { DarkStatus }

/** Semantic status colours for the current theme. */
object AppStatus {
    val colors: StatusPalette
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current
}

/** Plot area background. Slightly deeper than the app background so the chart reads as the focus. */
fun chartBackgroundFor(isDark: Boolean): Color = if (isDark) Color(0xFF0D0D0C) else Color(0xFFFCFBF8)

@Composable
fun AutoRrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalStatusPalette provides (if (darkTheme) DarkStatus else LightStatus),
            content = content
        )
    }
}
