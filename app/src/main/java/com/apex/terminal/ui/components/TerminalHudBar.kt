package com.apex.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apex.terminal.ui.chart.TerminalUiState
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.util.Formatters

/**
 * 24dp HUD strip: live OHLC of the hovered/forming bar plus the active EMA
 * readings, in Roboto Mono, horizontally scrollable on narrow viewports.
 */
@Composable
fun TerminalHudBar(
    state: TerminalUiState,
    modifier: Modifier = Modifier,
) {
    val candle = state.displayCandle
    val toggles = state.indicatorToggles
    val reference = state.referenceCandle

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(ApexColors.SurfaceBase)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Field("O", candle?.open, reference?.close, forceNeutral = true)
        Field("H", candle?.high, candle?.open)
        Field("L", candle?.low, candle?.open)
        Field("C", candle?.close, candle?.open)
        Field("V", candle?.volume, reference?.volume, volume = true)

        if (toggles.ema20) EmaField("EMA20", state.ema20, ApexColors.Ema20)
        if (toggles.ema50) EmaField("EMA50", state.ema50, ApexColors.Ema50)
        if (toggles.ema200) EmaField("EMA200", state.ema200, ApexColors.Ema200)
        if (toggles.rsi14) EmaField("RSI14", state.rsi, ApexColors.Rsi)
        if (toggles.stochastic) {
            EmaField("K", state.stochK, ApexColors.StochK)
            EmaField("D", state.stochD, ApexColors.StochD)
        }

        if (state.isHovering) {
            Text(
                text = "  ⟵ ${Formatters.clock(state.hoverCandle?.time ?: 0L)}",
                style = MonoMetric,
                color = ApexColors.TextTertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: Double?,
    reference: Double?,
    volume: Boolean = false,
    forceNeutral: Boolean = false,
) {
    val color = when {
        forceNeutral || value == null || reference == null -> ApexColors.TextPrimary
        value > reference -> ApexColors.GreenPing
        value < reference -> ApexColors.RedChg
        else -> ApexColors.TextPrimary
    }
    Text(
        text = "$label ",
        style = MonoMetric,
        color = ApexColors.TextTertiary,
    )
    Text(
        text = if (volume) Formatters.compact(value) else Formatters.price(value),
        style = MonoMetric,
        color = color,
    )
    Spacer(Modifier.width(10.dp))
}

@Composable
private fun EmaField(label: String, value: Double?, accent: Color) {
    Text(
        text = "$label ",
        style = MonoMetric,
        color = accent,
    )
    Text(
        text = value?.let { Formatters.price(it) } ?: "--",
        style = MonoMetric,
        color = ApexColors.TextSecondary,
    )
    Spacer(Modifier.width(10.dp))
}
