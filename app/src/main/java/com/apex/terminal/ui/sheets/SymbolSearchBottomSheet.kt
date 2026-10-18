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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.apex.terminal.domain.model.SymbolInfo
import com.apex.terminal.domain.model.Ticker24h
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import com.apex.terminal.ui.theme.MonoMetricBold
import com.apex.terminal.util.Formatters

/**
 * Symbol search sheet.
 *
 * Uses `Modifier.imePadding()` plus `WindowInsets`-aware sheet content so the
 * soft keyboard never covers the result list — the sheet grows above the IME
 * instead of being pushed off-screen.
 */
@Composable
fun SymbolSearchBottomSheet(
    sheetState: SheetState,
    catalog: List<SymbolInfo>,
    tickers: Map<String, Ticker24h>,
    selectedSymbol: String,
    watchlist: List<String>,
    onQuery: suspend (String) -> List<SymbolInfo>,
    onSelect: (String) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(catalog.take(80)) }

    LaunchedEffect(query, catalog) {
        results = onQuery(query).ifEmpty { if (query.isBlank()) catalog.take(80) else emptyList() }
    }

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
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = "SELECT MARKET",
                style = MonoMetric.copy(fontSize = 9.sp),
                color = ApexColors.TextTertiary,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                singleLine = true,
                placeholder = {
                    Text("Search BTC, ETH, PEPE…", style = MonoMetric, color = ApexColors.TextTertiary)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = ApexColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = ApexColors.TextSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                },
                textStyle = MonoMetric.copy(fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ApexColors.BlueMain,
                    unfocusedBorderColor = ApexColors.BorderDark,
                    focusedContainerColor = ApexColors.SurfaceBase,
                    unfocusedContainerColor = ApexColors.SurfaceBase,
                    cursorColor = ApexColors.BlueMain,
                    focusedTextColor = ApexColors.TextPrimary,
                    unfocusedTextColor = ApexColors.TextPrimary,
                ),
            )

            Spacer(Modifier.height(8.dp))

            if (results.isEmpty()) {
                EmptyState(query)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                ) {
                    items(results, key = { it.symbol }) { info ->
                        SymbolRow(
                            info = info,
                            ticker = tickers[info.symbol],
                            selected = info.symbol == selectedSymbol,
                            watched = watchlist.contains(info.symbol),
                            onSelect = { onSelect(info.symbol) },
                            onToggleWatchlist = { onToggleWatchlist(info.symbol) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolRow(
    info: SymbolInfo,
    ticker: Ticker24h?,
    selected: Boolean,
    watched: Boolean,
    onSelect: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val change = ticker?.priceChangePercent
    val changeColor = when {
        change == null -> ApexColors.TextTertiary
        change > 0 -> ApexColors.GreenPing
        change < 0 -> ApexColors.RedChg
        else -> ApexColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) ApexColors.SurfaceRaised else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinLogo(base = info.baseAsset)

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.baseAsset,
                style = MonoMetricBold.copy(fontSize = 12.sp),
                color = ApexColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = "${info.displayName} · ${info.quoteAsset}",
                style = MonoMetric,
                color = ApexColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatters.price(ticker?.lastPrice),
                style = MonoMetricBold,
                color = ApexColors.TextPrimary,
            )
            Text(
                text = Formatters.percent(change),
                style = MonoMetric,
                color = changeColor,
            )
        }

        IconButton(onClick = onToggleWatchlist, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = if (watched) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Toggle watchlist",
                tint = if (watched) ApexColors.Amber else ApexColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * Coin logo with an offline-safe circular monogram fallback: on a restricted
 * network the CDN is frequently unreachable, so the fallback is the primary
 * visual in practice.
 */
@Composable
private fun CoinLogo(base: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(ApexColors.SurfaceRaised)
            .border(1.dp, ApexColors.BorderDark, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data("https://bin.bnbstatic.com/static/assets/logos/$base.png")
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(CircleShape),
            loading = { Monogram(base) },
            error = { Monogram(base) },
        )
    }
}

@Composable
private fun Monogram(base: String) {
    Text(
        text = base.take(2).uppercase(),
        style = MonoMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
        color = ApexColors.TextSecondary,
    )
}

@Composable
private fun EmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (query.isBlank()) "No symbols available" else "No match for \"$query\"",
            style = MonoMetric,
            color = ApexColors.TextTertiary,
        )
    }
}

/** Shared sheet drag handle so every sheet looks identical. */
@Composable
fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexColors.BorderDark),
        )
    }
}
