package com.apex.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.terminal.core.Timeframes
import com.apex.terminal.domain.model.Ticker24h
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold
import com.apex.terminal.ui.theme.RobotoMono
import com.apex.terminal.util.Formatters

/**
 * 48dp TopAppBar: drawer affordance, symbol selector pill and the 24h metric
 * rail (high / low / quote volume).
 */
@Composable
fun TerminalTopBar(
    symbol: String,
    ticker: Ticker24h?,
    isWatched: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = ticker?.priceChangePercent
    val changeColor = when {
        change == null -> ApexColors.TextSecondary
        change > 0 -> ApexColors.GreenPing
        change < 0 -> ApexColors.RedChg
        else -> ApexColors.TextSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(ApexColors.SurfaceBase)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation drawer",
                tint = ApexColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ApexColors.SurfaceRaised)
                .border(1.dp, ApexColors.BorderDark, RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Timeframes.pretty(symbol),
                style = MonoMetricBold,
                color = ApexColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = Formatters.percent(change),
                style = MonoMetric,
                color = changeColor,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Change symbol",
                tint = ApexColors.TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }

        Spacer(Modifier.width(2.dp))

        IconButton(onClick = onToggleWatchlist, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isWatched) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Toggle watchlist",
                tint = if (isWatched) ApexColors.Amber else ApexColors.TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(4.dp))
            MiniMetric(label = "24H HIGH", value = Formatters.price(ticker?.highPrice))
            MiniMetric(label = "24H LOW", value = Formatters.price(ticker?.lowPrice))
            MiniMetric(label = "24H VOL", value = Formatters.compact(ticker?.quoteVolume))
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column(
        modifier = Modifier.padding(horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MonoMetric.copy(fontSize = 7.sp),
            color = ApexColors.TextTertiary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MonoMetric,
            color = ApexColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Standard hairline divider used between the chrome rows. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ApexColors.BorderDark),
    )
}
