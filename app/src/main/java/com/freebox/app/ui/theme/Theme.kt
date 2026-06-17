package com.freebox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ProfitGreen,
    onPrimary = Color.White,
    primaryContainer = GreenChipContainer,
    onPrimaryContainer = GreenChipContent,
    secondary = ProfitGreenDeep,
    onSecondary = Color.White,
    secondaryContainer = GreenChipContainer,
    onSecondaryContainer = GreenChipContent,
    tertiary = SlateMuted,
    onTertiary = Color.White,
    tertiaryContainer = SlateBorderFaint,
    onTertiaryContainer = InkSlate,
    background = SurfaceWhite,
    onBackground = InkSlate,
    surface = SurfaceWhite,
    onSurface = InkSlate,
    surfaceVariant = SlateBorderFaint,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SoftSlate,
    surfaceContainer = SoftSlate,
    surfaceContainerHigh = SlateBorderFaint,
    surfaceContainerHighest = SlateBorder,
    outline = SlateOutline,
    outlineVariant = SlateBorder,
    error = ErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed
)

// The Luminous Precision design system is light-only; every screen is
// designed against white surfaces, so the system dark setting is ignored
// rather than shipping an untested inverted theme.
@Composable
fun FreeboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
@Deprecated("Use FreeboxTheme instead", ReplaceWith("FreeboxTheme(darkTheme, dynamicColor, content)"))
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    FreeboxTheme(darkTheme, dynamicColor, content)
}
