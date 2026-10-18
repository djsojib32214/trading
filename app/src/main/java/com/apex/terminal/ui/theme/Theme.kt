package com.apex.terminal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ApexDarkScheme = darkColorScheme(
    primary = ApexColors.BlueMain,
    onPrimary = Color.White,
    primaryContainer = ApexColors.BlueDim,
    onPrimaryContainer = ApexColors.TextPrimary,

    secondary = ApexColors.GreenPing,
    onSecondary = Color.Black,
    tertiary = ApexColors.Amber,

    background = ApexColors.BgBlack,
    onBackground = ApexColors.TextPrimary,
    surface = ApexColors.SurfaceBase,
    onSurface = ApexColors.TextPrimary,
    surfaceVariant = ApexColors.SurfaceCard,
    onSurfaceVariant = ApexColors.TextSecondary,
    surfaceContainer = ApexColors.SurfaceCard,
    surfaceContainerHigh = ApexColors.SurfaceRaised,
    surfaceContainerHighest = ApexColors.SurfaceRaised,
    surfaceContainerLow = ApexColors.SurfaceBase,
    surfaceContainerLowest = ApexColors.BgBlack,

    outline = ApexColors.BorderDark,
    outlineVariant = ApexColors.BorderDark,
    error = ApexColors.RedChg,
    onError = Color.White,
    scrim = Color(0xCC000000),
)

/** ApexTerminal always renders the OLED-dark scheme; there is no light mode. */
@Composable
fun ApexTerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ApexDarkScheme,
        typography = ApexTypography,
        content = content,
    )
}
