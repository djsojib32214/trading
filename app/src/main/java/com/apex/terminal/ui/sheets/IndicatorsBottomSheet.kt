package com.apex.terminal.ui.sheets

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
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
import com.apex.terminal.data.prefs.SettingsStore
import com.apex.terminal.ui.chart.IndicatorToggles
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold

/**
 * `fx` sheet — standard indicators only: EMA 20/50/200, RSI(14) and
 * Stochastic (14, 3, 3). Each row toggles a Material 3 switch.
 */
@Composable
fun IndicatorsBottomSheet(
    sheetState: SheetState,
    toggles: IndicatorToggles,
    onToggle: (SettingsStore.IndicatorKey, Boolean) -> Unit,
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
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "INDICATORS",
                style = MonoMetric.copy(fontSize = 9.sp),
                color = ApexColors.TextTertiary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )

            IndicatorRow(
                label = "EMA 20",
                hint = "Overlay · exponential moving average",
                accent = ApexColors.Ema20,
                checked = toggles.ema20,
                onCheckedChange = { onToggle(SettingsStore.IndicatorKey.EMA20, it) },
            )
            IndicatorRow(
                label = "EMA 50",
                hint = "Overlay · exponential moving average",
                accent = ApexColors.Ema50,
                checked = toggles.ema50,
                onCheckedChange = { onToggle(SettingsStore.IndicatorKey.EMA50, it) },
            )
            IndicatorRow(
                label = "EMA 200",
                hint = "Overlay · trend filter",
                accent = ApexColors.Ema200,
                checked = toggles.ema200,
                onCheckedChange = { onToggle(SettingsStore.IndicatorKey.EMA200, it) },
            )
            IndicatorRow(
                label = "RSI (14)",
                hint = "Lower pane · Wilder smoothing",
                accent = ApexColors.Rsi,
                checked = toggles.rsi14,
                onCheckedChange = { onToggle(SettingsStore.IndicatorKey.RSI14, it) },
            )
            IndicatorRow(
                label = "Stochastic (14,3,3)",
                hint = "Lower pane · slow %K / %D",
                accent = ApexColors.StochK,
                checked = toggles.stochastic,
                onCheckedChange = { onToggle(SettingsStore.IndicatorKey.STOCHASTIC, it) },
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Indicator math runs in Kotlin; the WebView only renders the resulting series.",
                style = MonoMetric.copy(fontSize = 9.sp),
                color = ApexColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun IndicatorRow(
    label: String,
    hint: String,
    accent: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (checked) accent else ApexColors.BorderDark),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MonoMetricBold,
                    color = if (checked) ApexColors.TextPrimary else ApexColors.TextSecondary,
                )
                Text(
                    text = hint,
                    style = MonoMetric.copy(fontSize = 9.sp),
                    color = ApexColors.TextTertiary,
                )
            }
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
