package com.apex.terminal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apex.terminal.R

/**
 * `Roboto Mono` for every number the trader reads — bundled locally so the
 * terminal keeps its metrics aligned on a restricted network with no font CDN
 * reachable.
 */
val RobotoMono: FontFamily = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
)

/** Dense terminal typography: nothing taller than 13sp in the chrome. */
val ApexTypography = Typography(
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.4.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/** The one style every price/latency/metric uses. */
val MonoMetric = TextStyle(
    fontFamily = RobotoMono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.sp,
)

val MonoMetricBold = MonoMetric.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp)
