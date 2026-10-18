package com.apex.terminal.ui.drawer

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.terminal.domain.model.Ticker24h
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold
import com.apex.terminal.util.Formatters

/**
 * Navigation drawer: watchlist with live 24h deltas plus the operational
 * actions (re-sync, cache maintenance, monitor shortcuts).
 */
@Composable
fun SystemDrawer(
    watchlist: List<String>,
    activeSymbol: String,
    tickers: Map<String, Ticker24h>,
    storedBars: Long,
    latencyMs: Long?,
    restOnMirror: Boolean,
    wsOnMirror: Boolean,
    monitorEnabled: Boolean,
    monitorPaused: Boolean,
    onSelectSymbol: (String) -> Unit,
    onToggleMonitor: (Boolean) -> Unit,
    onResync: () -> Unit,
    onForceRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onClearAll: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = ApexColors.SurfaceCard,
        drawerContentColor = ApexColors.TextPrimary,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)) {
            Text("APEXTERMINAL", style = MonoMetricBold.copy(fontSize = 15.sp), color = ApexColors.TextPrimary)
            Text(
                "Local Lightweight Charts v5 · Clean Architecture",
                style = MonoMetric.copy(fontSize = 9.sp),
                color = ApexColors.TextTertiary,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(ApexColors.SurfaceRaised)
                    .border(1.dp, ApexColors.BorderDark, RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DrawerStat("LATENCY", latencyMs?.let { "$it ms" } ?: "--")
                DrawerStat("BARS", storedBars.toString())
                DrawerStat("ROUTE", if (restOnMirror || wsOnMirror) "MIRROR" else "PRIMARY")
            }
        }

        HorizontalDivider(color = ApexColors.BorderDark)

        Text(
            "WATCHLIST",
            style = MonoMetric.copy(fontSize = 9.sp),
            color = ApexColors.TextTertiary,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 4.dp),
        )

        LazyColumn(modifier = Modifier.height(240.dp)) {
            items(watchlist, key = { it }) { symbol ->
                val ticker = tickers[symbol]
                NavigationDrawerItem(
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = com.apex.terminal.core.Timeframes.pretty(symbol),
                                style = MonoMetric,
                                color = if (symbol == activeSymbol) ApexColors.BlueMain else ApexColors.TextPrimary,
                            )
                            Text(
                                text = Formatters.percent(ticker?.priceChangePercent),
                                style = MonoMetric,
                                color = when {
                                    ticker == null -> ApexColors.TextTertiary
                                    ticker.priceChangePercent > 0 -> ApexColors.GreenPing
                                    ticker.priceChangePercent < 0 -> ApexColors.RedChg
                                    else -> ApexColors.TextSecondary
                                },
                            )
                        }
                    },
                    selected = symbol == activeSymbol,
                    onClick = { onSelectSymbol(symbol) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = ApexColors.SurfaceRaised,
                        unselectedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                )
            }
        }

        HorizontalDivider(color = ApexColors.BorderDark)

        DrawerAction(
            icon = Icons.Default.NotificationsActive,
            label = when {
                !monitorEnabled -> "Start background monitor"
                monitorPaused -> "Resume background monitor"
                else -> "Stop background monitor"
            },
            onClick = { onToggleMonitor(!monitorEnabled || monitorPaused) },
        )
        DrawerAction(Icons.Default.Refresh, "Re-sync this market", onResync)
        DrawerAction(Icons.Default.CloudDownload, "Force full history backfill", onForceRefresh)
        DrawerAction(Icons.Default.ShowChart, "Clear candles for this market", onClearCache)
        DrawerAction(Icons.Default.DeleteSweep, "Clear all cached candles", onClearAll)

        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ApexColors.TextTertiary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ApexTerminal 1.0.0 · minSdk 33 / targetSdk 35",
                    style = MonoMetric.copy(fontSize = 9.sp),
                    color = ApexColors.TextTertiary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = ApexColors.TextTertiary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Room WAL · OkHttp streaming · ISP failover armed",
                    style = MonoMetric.copy(fontSize = 9.sp),
                    color = ApexColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun DrawerStat(label: String, value: String) {
    Column {
        Text(label, style = MonoMetric.copy(fontSize = 8.sp), color = ApexColors.TextTertiary)
        Text(value, style = MonoMetricBold, color = ApexColors.TextPrimary)
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ApexColors.TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MonoMetric, color = ApexColors.TextPrimary)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ApexColors.BorderDark.copy(alpha = 0.4f)),
    )
}
