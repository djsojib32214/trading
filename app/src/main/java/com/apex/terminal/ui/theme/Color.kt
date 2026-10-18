package com.apex.terminal.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pure-OLED palette from the PRD. Black is a true `#000000` so the AMOLED panel
 * on the target device can switch those pixels off entirely.
 */
object ApexColors {
    val BgBlack = Color(0xFF000000)
    val SurfaceBase = Color(0xFF060709)
    val SurfaceCard = Color(0xFF0C0E12)
    val SurfaceRaised = Color(0xFF12151C)
    val BorderDark = Color(0xFF191C24)

    val BlueMain = Color(0xFF2563EB)
    val BlueDim = Color(0xFF1B3E8F)
    val BlueSoft = Color(0x442563EB)

    val RedChg = Color(0xFFF23645)
    val GreenPing = Color(0xFF089981)
    val Amber = Color(0xFFFF9800)
    val Violet = Color(0xFFB388FF)

    val TextPrimary = Color(0xFFEAECEF)
    val TextSecondary = Color(0xFF848E9C)
    val TextTertiary = Color(0xFF5E6673)

    val Ema20 = Color(0xFF2962FF)
    val Ema50 = Color(0xFFFF9800)
    val Ema200 = Color(0xFFE91E63)
    val Rsi = Color(0xFFB388FF)
    val StochK = Color(0xFF26C6DA)
    val StochD = Color(0xFFFFB74D)
}
