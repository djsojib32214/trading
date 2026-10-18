package com.apex.terminal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.terminal.domain.model.ConnectionState
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold

/**
 * 38dp footer: connection/latency badge on the left, background monitor toggle
 * on the right.
 */
@Composable
fun TerminalFooter(
    connection: ConnectionState,
    latencyMs: Long?,
    onMirror: Boolean,
    monitorEnabled: Boolean,
    monitorPaused: Boolean,
    storedBars: Long,
    onToggleMonitor: () -> Unit,
    onResumeMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveColor = when {
        connection is ConnectionState.Live && !onMirror -> ApexColors.GreenPing
        connection is ConnectionState.Live -> ApexColors.Amber
        else -> ApexColors.RedChg
    }

    val monitorColor by animateColorAsState(
        targetValue = when {
            !monitorEnabled -> ApexColors.TextTertiary
            monitorPaused -> ApexColors.Amber
            else -> ApexColors.GreenPing
        },
        label = "monitorColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(ApexColors.SurfaceBase)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(liveColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = latencyMs?.let { "$it ms" } ?: "-- ms",
            style = MonoMetric,
            color = ApexColors.TextPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (connection) {
                is ConnectionState.Live -> if (onMirror) "LIVE · MIRROR" else "LIVE"
                is ConnectionState.Connecting -> "CONNECTING (${connection.attempt})"
                is ConnectionState.Reconnecting -> "RETRY (${connection.attempt})"
                ConnectionState.Idle -> "IDLE"
            },
            style = MonoMetric.copy(fontSize = 8.sp),
            color = ApexColors.TextSecondary,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "$storedBars bars",
            style = MonoMetric,
            color = ApexColors.TextTertiary,
        )

        Spacer(Modifier.width(10.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (monitorEnabled) ApexColors.SurfaceRaised else Color.Transparent)
                .border(
                    1.dp,
                    if (monitorEnabled) ApexColors.BorderDark else ApexColors.BorderDark,
                    RoundedCornerShape(5.dp),
                )
                .clickable {
                    if (monitorEnabled && monitorPaused) onResumeMonitor() else onToggleMonitor()
                }
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    !monitorEnabled -> Icons.Default.PlayCircleOutline
                    monitorPaused -> Icons.Default.PauseCircleOutline
                    else -> Icons.Default.Cached
                },
                contentDescription = null,
                tint = monitorColor,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = when {
                    !monitorEnabled -> "Monitor OFF"
                    monitorPaused -> "Paused"
                    else -> "Monitor ON"
                },
                style = if (monitorEnabled && !monitorPaused) MonoMetricBold else MonoMetric,
                color = monitorColor,
            )
        }
    }
}
