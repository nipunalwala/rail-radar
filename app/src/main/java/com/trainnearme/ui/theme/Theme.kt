package com.trainnearme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.trainnearme.core.model.Line

// Built on the launcher icon's blue. Fixed rather than taken from the
// wallpaper, so status colours always sit on a background they were chosen for.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A4D8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF12407A),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

/** Colours with a meaning of their own, which the Material scheme has no slot for. */
data class StatusColors(
    val onTime: Color,
    val slightlyLate: Color,
    val late: Color,
    val western: Color,
    val central: Color,
    val harbour: Color,
) {
    fun line(line: Line): Color = when (line) {
        Line.WESTERN -> western
        Line.CENTRAL -> central
        Line.HARBOUR -> harbour
    }
}

// The line colours are this app's own choice, not the railway's. A line's name
// is always shown beside its colour.
private val LightStatus = StatusColors(
    onTime = Color(0xFF1B7F3B),
    slightlyLate = Color(0xFF8A5A00),
    late = Color(0xFFBA1A1A),
    western = Color(0xFFD9482B),
    central = Color(0xFF2F6FD0),
    harbour = Color(0xFF23884C),
)

private val DarkStatus = StatusColors(
    onTime = Color(0xFF7BDA94),
    slightlyLate = Color(0xFFF2C14E),
    late = Color(0xFFFFB4AB),
    western = Color(0xFFFF8A6B),
    central = Color(0xFF7FB0FF),
    harbour = Color(0xFF6FD694),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

@Composable
fun TrainNearMeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
