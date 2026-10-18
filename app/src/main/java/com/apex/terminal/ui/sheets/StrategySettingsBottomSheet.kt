package com.apex.terminal.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.terminal.domain.StrategyConfig
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold

/**
 * Alert matrix for the continuous background monitor. Conditions are always
 * evaluated on closed candles only.
 */
@Composable
fun StrategySettingsBottomSheet(
    sheetState: SheetState,
    strategy: StrategyConfig,
    monitorEnabled: Boolean,
    onMonitorEnabled: (Boolean) -> Unit,
    onStrategy: ((StrategyConfig) -> StrategyConfig) -> Unit,
    onClearCache: () -> Unit,
    onFitContent: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ApexColors.SurfaceCard,
        contentColor = ApexColors.TextPrimary,
        scrimColor = Color(0xCC000000),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            SectionTitle("BACKGROUND MONITOR")

            ToggleRow(
                label = "Continuous monitor",
                hint = "Foreground service · partial wake lock",
                checked = monitorEnabled,
                onCheckedChange = onMonitorEnabled,
            )

            SectionTitle("ALERT CONDITIONS (CLOSED CANDLES ONLY)")

            ToggleRow(
                label = "EMA 20 / 50 cross",
                hint = "Golden & death cross of the EMA ribbon",
                checked = strategy.emaCross,
                onCheckedChange = { value -> onStrategy { it.copy(emaCross = value) } },
            )
            ToggleRow(
                label = "RSI extremes",
                hint = "Cross of ${strategy.rsiUpper.toInt()} / ${strategy.rsiLower.toInt()}",
                checked = strategy.rsiExtremes,
                onCheckedChange = { value -> onStrategy { it.copy(rsiExtremes = value) } },
            )
            ToggleRow(
                label = "Stochastic cross",
                hint = "%K crossing %D in the lower pane",
                checked = strategy.stochasticCross,
                onCheckedChange = { value -> onStrategy { it.copy(stochasticCross = value) } },
            )
            ToggleRow(
                label = "Range breakout",
                hint = "Close beyond the last ${strategy.breakoutLookback}-bar range",
                checked = strategy.rangeBreakout,
                onCheckedChange = { value -> onStrategy { it.copy(rangeBreakout = value) } },
            )

            SliderRow(
                label = "Breakout lookback",
                value = strategy.breakoutLookback.toFloat(),
                range = 5f..100f,
                steps = 18,
                format = { "${it.toInt()} bars" },
                onValueChange = { value -> onStrategy { it.copy(breakoutLookback = value.toInt()) } },
            )
            SliderRow(
                label = "RSI upper bound",
                value = strategy.rsiUpper.toFloat(),
                range = 55f..90f,
                steps = 6,
                format = { "%.0f".format(it) },
                onValueChange = { value -> onStrategy { it.copy(rsiUpper = value.toDouble()) } },
            )
            SliderRow(
                label = "RSI lower bound",
                value = strategy.rsiLower.toFloat(),
                range = 10f..45f,
                steps = 6,
                format = { "%.0f".format(it) },
                onValueChange = { value -> onStrategy { it.copy(rsiLower = value.toDouble()) } },
            )

            SectionTitle("MAINTENANCE")

            ActionRow("Fit chart to data", onFitContent)
            ActionRow("Clear local candles for this market", onClearCache)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MonoMetric.copy(fontSize = 9.sp),
        color = ApexColors.TextTertiary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MonoMetricBold,
                color = if (checked) ApexColors.TextPrimary else ApexColors.TextSecondary,
            )
            Text(text = hint, style = MonoMetric.copy(fontSize = 9.sp), color = ApexColors.TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ApexColors.BlueMain,
                checkedBorderColor = ApexColors.BlueMain,
                uncheckedThumbColor = ApexColors.TextTertiary,
                uncheckedTrackColor = ApexColors.SurfaceBase,
                uncheckedBorderColor = ApexColors.BorderDark,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MonoMetric, color = ApexColors.TextSecondary)
            Text(format(value), style = MonoMetric, color = ApexColors.BlueMain)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ApexColors.BlueMain,
                activeTrackColor = ApexColors.BlueMain,
                inactiveTrackColor = ApexColors.SurfaceRaised,
            ),
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ApexColors.SurfaceRaised)
            .border(1.dp, ApexColors.BorderDark, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MonoMetric, color = ApexColors.TextPrimary)
    }
}
