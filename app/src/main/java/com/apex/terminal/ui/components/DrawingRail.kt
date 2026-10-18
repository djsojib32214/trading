package com.apex.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.apex.terminal.ui.chart.ChartCommands
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric

/** The five tools exposed by the floating drawing rail. */
enum class DrawingTool(
    val mode: String,
    val icon: ImageVector,
    val description: String,
) {
    CROSSHAIR(ChartCommands.MODE_CROSSHAIR, Icons.Default.CenterFocusStrong, "Crosshair / pan mode"),
    TRENDLINE(ChartCommands.MODE_TRENDLINE, Icons.Default.TrendingUp, "Trendline"),
    RAY(ChartCommands.MODE_RAY, Icons.Default.TurnSlightRight, "Ray"),
    FIBONACCI(ChartCommands.MODE_FIBONACCI, Icons.Default.Straighten, "Fibonacci retracement"),
    TRASH("clear", Icons.Default.DeleteOutline, "Clear all drawings"),
}

/**
 * 32dp-wide floating pill anchored to the left edge of the chart viewport.
 * Selecting a tool switches the WebView into drawing mode through the bridge.
 */
@Composable
fun DrawingRail(
    activeMode: String,
    drawingCount: Int,
    onSelect: (DrawingTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ApexColors.SurfaceCard.copy(alpha = 0.94f))
            .border(1.dp, ApexColors.BorderDark, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DrawingTool.entries.forEach { tool ->
            val active = tool.mode == activeMode && tool != DrawingTool.TRASH
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) ApexColors.BlueSoft else Color.Transparent)
                    .clickable { onSelect(tool) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.description,
                    tint = when {
                        active -> ApexColors.BlueMain
                        tool == DrawingTool.TRASH -> ApexColors.RedChg
                        else -> ApexColors.TextSecondary
                    },
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        if (drawingCount > 0) {
            Text(
                text = drawingCount.toString(),
                style = MonoMetric,
                color = ApexColors.BlueMain,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
