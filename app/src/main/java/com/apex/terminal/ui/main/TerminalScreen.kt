package com.apex.terminal.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apex.terminal.domain.model.ConnectionState
import com.apex.terminal.ui.chart.ChartBridgeCallbacks
import com.apex.terminal.ui.chart.ChartViewport
import com.apex.terminal.ui.chart.ChartViewModel
import com.apex.terminal.ui.components.DrawingRail
import com.apex.terminal.ui.components.DrawingTool
import com.apex.terminal.ui.components.HairlineDivider
import com.apex.terminal.ui.components.TerminalFooter
import com.apex.terminal.ui.components.TerminalHudBar
import com.apex.terminal.ui.components.TerminalTopBar
import com.apex.terminal.ui.components.TimeframeStrip
import com.apex.terminal.ui.drawer.SystemDrawer
import com.apex.terminal.ui.sheets.IndicatorsBottomSheet
import com.apex.terminal.ui.sheets.StrategySettingsBottomSheet
import com.apex.terminal.ui.sheets.SymbolSearchBottomSheet
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import kotlinx.coroutines.launch

/**
 * The complete native shell: TopAppBar, HUD, timeframe strip, chart viewport
 * with its floating drawing rail, and the footer telemetry bar.
 */
@Composable
fun TerminalScreen(viewModel: ChartViewModel) {
    val state by viewModel.uiState.collectAsState()
    val tickers by viewModel.tickers.collectAsState()
    val catalog by viewModel.catalogSymbols.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var showIndicators by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val indicatorsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    val watchlistSymbols by viewModel.watchlist.collectAsState()

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SystemDrawer(
                watchlist = (listOf(state.symbol) + watchlistSymbols).distinct(),
                activeSymbol = state.symbol,
                tickers = tickers,
                storedBars = state.storedBars,
                latencyMs = state.latencyMs,
                restOnMirror = state.restOnMirror,
                wsOnMirror = state.wsOnMirror,
                monitorEnabled = state.monitorEnabled,
                monitorPaused = state.monitorPaused,
                onSelectSymbol = { symbol ->
                    viewModel.selectSymbol(symbol)
                    scope.launch { drawerState.close() }
                },
                onToggleMonitor = { enabled ->
                    viewModel.setMonitorEnabled(enabled)
                    scope.launch { drawerState.close() }
                },
                onResync = {
                    viewModel.requestFullResync()
                    scope.launch { drawerState.close() }
                },
                onForceRefresh = {
                    viewModel.forceFullBackfill()
                    scope.launch { drawerState.close() }
                },
                onClearCache = {
                    viewModel.clearLocalCache()
                    scope.launch { drawerState.close() }
                },
                onClearAll = {
                    viewModel.clearAllCache()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.BgBlack)
                .systemBarsPadding(),
        ) {
            TerminalTopBar(
                symbol = state.symbol,
                ticker = tickers[state.symbol],
                isWatched = watchlistSymbols.contains(state.symbol),
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSearch = { showSearch = true },
                onToggleWatchlist = { viewModel.toggleWatchlist(state.symbol) },
            )
            HairlineDivider()

            TerminalHudBar(state = state)
            HairlineDivider()

            TimeframeStrip(
                selected = state.interval,
                onSelect = viewModel::selectInterval,
                onOpenIndicators = { showIndicators = true },
                onOpenSettings = { showSettings = true },
                indicatorsActive = state.indicatorToggles.ema200 ||
                    state.indicatorToggles.rsi14 ||
                    state.indicatorToggles.stochastic,
            )
            HairlineDivider()

            Box(modifier = Modifier.weight(1f)) {
                ChartViewport(
                    jsCommands = viewModel.jsCommands,
                    callbacks = ChartBridgeCallbacks(
                        crosshair = viewModel::onCrosshair,
                        reset = viewModel::onCrosshairReset,
                        drawing = viewModel::onDrawingAdded,
                        log = viewModel::onBridgeLog,
                    ),
                    onChartReady = viewModel::onChartReady,
                    modifier = Modifier.fillMaxSize(),
                )

                DrawingRail(
                    activeMode = state.drawingMode,
                    drawingCount = state.drawingCount,
                    onSelect = { tool ->
                        if (tool == DrawingTool.TRASH) {
                            viewModel.clearDrawings()
                        } else {
                            viewModel.setDrawingMode(tool.mode)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 6.dp),
                )

                if (state.loading) {
                    Text(
                        text = "loading ${state.symbol} ${state.interval}…",
                        style = MonoMetric,
                        color = ApexColors.TextTertiary,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    )
                }
            }

            HairlineDivider()
            TerminalFooter(
                connection = state.connection,
                latencyMs = state.latencyMs,
                onMirror = state.restOnMirror || state.wsOnMirror,
                monitorEnabled = state.monitorEnabled,
                monitorPaused = state.monitorPaused,
                storedBars = state.storedBars,
                onToggleMonitor = { viewModel.setMonitorEnabled(!state.monitorEnabled) },
                onResumeMonitor = viewModel::resumeMonitor,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 48.dp),
        ) { data ->
            Snackbar(
                containerColor = ApexColors.SurfaceRaised,
                contentColor = ApexColors.TextPrimary,
                content = { Text(data.visuals.message, style = MonoMetric) },
            )
        }
    }

    if (showSearch) {
        SymbolSearchBottomSheet(
            sheetState = searchSheetState,
            catalog = catalog,
            tickers = tickers,
            selectedSymbol = state.symbol,
            watchlist = watchlistSymbols,
            onQuery = viewModel::searchSymbols,
            onSelect = { symbol ->
                viewModel.selectSymbol(symbol)
                showSearch = false
            },
            onToggleWatchlist = viewModel::toggleWatchlist,
            onDismiss = { showSearch = false },
        )
    }

    if (showIndicators) {
        IndicatorsBottomSheet(
            sheetState = indicatorsSheetState,
            toggles = state.indicatorToggles,
            onToggle = viewModel::toggleIndicator,
            onDismiss = { showIndicators = false },
        )
    }

    if (showSettings) {
        StrategySettingsBottomSheet(
            sheetState = settingsSheetState,
            strategy = state.strategy,
            monitorEnabled = state.monitorEnabled,
            onMonitorEnabled = viewModel::setMonitorEnabled,
            onStrategy = viewModel::updateStrategy,
            onClearCache = viewModel::clearLocalCache,
            onFitContent = viewModel::fitContent,
            onDismiss = { showSettings = false },
        )
    }
}
