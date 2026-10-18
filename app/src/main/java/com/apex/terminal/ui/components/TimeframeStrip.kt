package com.apex.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.apex.terminal.core.Timeframes
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold

/**
 * 32dp timeframe strip: one chip per interval, plus the `fx` indicator sheet
 * trigger and the settings affordance.
 */
@Composable
fun TimeframeStrip(
    selected: String,
    onSelect: (String) -> Unit,
    onOpenIndicators: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorsActive: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(ApexColors.SurfaceBase)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Timeframes.ALL.forEach { tf ->
            TimeframeChip(
                label = Timeframes.label(tf),
                active = tf == selected,
                onClick = { onSelect(tf) },
            )
        }

        Spacer(Modifier.width(4.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (indicatorsActive) ApexColors.BlueSoft else ApexColors.SurfaceRaised)
                .border(
                    1.dp,
                    if (indicatorsActive) ApexColors.BlueMain else ApexColors.BorderDark,
                    RoundedCornerShape(4.dp),
                )
                .clickable(onClick = onOpenIndicators)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = if (indicatorsActive) ApexColors.BlueMain else ApexColors.TextSecondary,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "fx Indicators",
                style = MonoMetric,
                color = if (indicatorsActive) ApexColors.BlueMain else ApexColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(2.dp))

        IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Terminal settings",
                tint = ApexColors.TextSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun TimeframeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) ApexColors.BlueMain else ApexColors.SurfaceRaised)
            .border(
                1.dp,
                if (active) ApexColors.BlueMain else ApexColors.BorderDark,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (active) MonoMetricBold else MonoMetric,
            color = if (active) androidx.compose.ui.graphics.Color.White else ApexColors.TextSecondary,
        )
    }
}
