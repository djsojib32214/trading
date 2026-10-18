package com.apex.terminal.ui.chart

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apex.terminal.BuildConfig
import com.apex.terminal.core.Timeframes
import com.apex.terminal.data.prefs.SettingsStore
import com.apex.terminal.data.prefs.TerminalSettings
import com.apex.terminal.data.remote.StreamEvent
import com.apex.terminal.data.repository.MarketRepository
import com.apex.terminal.data.repository.SymbolCatalog
import com.apex.terminal.domain.Indicators
import com.apex.terminal.domain.StrategyConfig
import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.ConnectionState
import com.apex.terminal.domain.model.LinePoint
import com.apex.terminal.domain.model.SymbolInfo
import com.apex.terminal.domain.model.Ticker24h
import com.apex.terminal.service.MonitorController
import com.apex.terminal.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which overlays the `fx` sheet has switched on. */
data class IndicatorToggles(
    val ema20: Boolean = true,
    val ema50: Boolean = true,
    val ema200: Boolean = false,
    val rsi14: Boolean = false,
    val stochastic: Boolean = false,
) {
    val anyEma: Boolean get() = ema20 || ema50 || ema200

    val anyOscillator: Boolean get() = rsi14 || stochastic
}

/** Immutable snapshot rendered by the whole Compose shell. */
data class TerminalUiState(
    val symbol: String = TerminalSettings.DEFAULT_SYMBOL,
    val interval: String = Timeframes.M1,
    val loading: Boolean = true,
    val candles: List<Candle> = emptyList(),
    val liveCandle: Candle? = null,
    val hoverCandle: Candle? = null,
    val indicatorToggles: IndicatorToggles = IndicatorToggles(),
    val ema20: Double? = null,
    val ema50: Double? = null,
    val ema200: Double? = null,
    val rsi: Double? = null,
    val stochK: Double? = null,
    val stochD: Double? = null,
    val ticker: Ticker24h? = null,
    val connection: ConnectionState = ConnectionState.Idle,
    val latencyMs: Long? = null,
    val restOnMirror: Boolean = false,
    val wsOnMirror: Boolean = false,
    val drawingMode: String = ChartCommands.MODE_CROSSHAIR,
    val drawingCount: Int = 0,
    val monitorEnabled: Boolean = false,
    val monitorPaused: Boolean = false,
    val storedBars: Long = 0L,
    val strategy: StrategyConfig = StrategyConfig(),
    val chartReady: Boolean = false,
    val message: String? = null,
) {
    /** The bar the HUD should print: hovered > live > last stored. */
    val displayCandle: Candle?
        get() = hoverCandle ?: liveCandle ?: candles.lastOrNull()

    /** Reference bar for the HUD deltas. */
    val referenceCandle: Candle?
        get() = candles.dropLast(1).lastOrNull()

    val isHovering: Boolean get() = hoverCandle != null
}

/**
 * Owns the chart data pipeline: history hydration, live tick reconciliation,
 * indicator math and the ordered JavaScript command stream pushed into the
 * WebView viewport.
 */
class ChartViewModel(
    application: Application,
    private val repository: MarketRepository,
    private val settingsStore: SettingsStore,
    private val catalog: SymbolCatalog,
    private val monitorController: MonitorController,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    /**
     * Ordered JS statements destined for the WebView. A single collector in
     * [ChartViewport] drains this; the buffer is unbounded so nothing is lost
     * while the WebView is still booting.
     */
    private val jsChannel = Channel<String>(Channel.UNLIMITED)
    val jsCommands: Flow<String> = jsChannel.receiveAsFlow()

    private var liveCandle: Candle? = null
    private var lastIndicatorPush = 0L

    /** Symbol list rendered by the search sheet (offline catalogue). */
    private val _catalogSymbols = MutableStateFlow<List<SymbolInfo>>(emptyList())
    val catalogSymbols: StateFlow<List<SymbolInfo>> = _catalogSymbols.asStateFlow()

    private val polledSymbols = MutableStateFlow(TerminalSettings.DEFAULT_WATCHLIST)

    /** Symbols the user pinned; drives the drawer and the 24h ticker poll set. */
    val watchlist: StateFlow<List<String>> = polledSymbols.asStateFlow()

    /** 24h statistics keyed by symbol, used by the TopBar, drawer and search. */
    val tickers: StateFlow<Map<String, Ticker24h>> = repository
        .tickerPoll(polledSymbols)
        .map { list -> list.associateBy { it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Tick by which the TopAppBar 24h metrics refresh. */
    val activeTicker: StateFlow<Ticker24h?> = combine(uiState, tickers) { state, map ->
        map[state.symbol]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val settingsFlow = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, TerminalSettings())

    init {
        viewModelScope.launch { _catalogSymbols.value = catalog.all() }

        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        indicatorToggles = IndicatorToggles(
                            ema20 = settings.ema20,
                            ema50 = settings.ema50,
                            ema200 = settings.ema200,
                            rsi14 = settings.rsi14,
                            stochastic = settings.stochastic,
                        ),
                        monitorEnabled = settings.monitorEnabled,
                        monitorPaused = settings.monitorPaused,
                        strategy = settings.strategy,
                    )
                }
                polledSymbols.value = (settings.watchlist + settings.symbol).distinct()
            }
        }

        // (symbol, interval) -> full dataset reload; collectLatest gives us the
        // cancellation semantics that stop the old socket before the new one.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.symbol to it.interval }
                .distinctUntilChanged()
                .collectLatest { (symbol, interval) -> activate(symbol, interval) }
        }

        // Room -> in-memory candle window (drives HUD + indicator math)
        viewModelScope.launch {
            settingsStore.settings
                .map { it.symbol to it.interval }
                .distinctUntilChanged()
                .flatMapLatest { (symbol, interval) -> repository.observeCandles(symbol, interval) }
                .collect { candles ->
                    _uiState.update { it.copy(candles = candles.takeLast(MAX_RENDER_BARS)) }
                }
        }

        // Indicator overlays recomputed whenever the bar set or a toggle flips
        viewModelScope.launch {
            _uiState
                .map { Triple(it.candles, it.liveCandle, it.indicatorToggles) }
                .distinctUntilChanged()
                .collect { (candles, live, toggles) ->
                    val merged = merge(candles, live)
                    if (merged.isEmpty()) return@collect
                    recomputeIndicatorSnapshot(merged, toggles, pushSeries = true)
                }
        }

        // Telemetry: latency badge + which host we ended up on.
        viewModelScope.launch { repository.startLatencyProbe().collect { } }
        viewModelScope.launch {
            repository.latencyMs.collect { ms ->
                _uiState.update {
                    it.copy(
                        latencyMs = ms,
                        restOnMirror = repository.route.restOnMirror,
                        wsOnMirror = repository.route.wsOnMirror,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.connection.collect { state ->
                _uiState.update { it.copy(connection = state) }
            }
        }
    }

    // ------------------------------------------------------------ loading --

    private suspend fun activate(symbol: String, interval: String) {
        liveCandle = null
        _uiState.update {
            it.copy(
                symbol = symbol,
                interval = interval,
                loading = true,
                candles = emptyList(),
                liveCandle = null,
                hoverCandle = null,
                ema20 = null, ema50 = null, ema200 = null,
                rsi = null, stochK = null, stochD = null,
            )
        }

        // 1. Reconcile the local store with the exchange BEFORE going live.
        runCatching { repository.syncHistory(symbol, interval) }

        // 2. Push whatever we now hold into the chart.
        pushFullDataset(symbol, interval)
        _uiState.update {
            it.copy(
                loading = false,
                storedBars = runCatching { repository.storedCount(symbol, interval) }.getOrDefault(0L),
            )
        }

        // 3. Attach the live socket; suspends until the symbol/timeframe changes.
        repository.liveTicks(symbol, interval).collect { event -> onStreamEvent(event) }
    }

    private fun onStreamEvent(event: StreamEvent) {
        if (event !is StreamEvent.Kline) return
        val candle = event.candle
        if (candle.time <= 0L) return
        if (candle.open <= 0.0 && candle.close <= 0.0) return

        // Invariant: New Candle Open == Previous Candle Close.
        val continuous = Candle.applyContinuity(liveCandle, candle)
        liveCandle = continuous

        jsChannel.trySend(ChartCommands.updateLiveTick(continuous))

        val now = System.currentTimeMillis()
        if (continuous.closed || now - lastIndicatorPush >= INDICATOR_PUSH_INTERVAL_MS) {
            lastIndicatorPush = now
            val merged = merge(_uiState.value.candles, continuous)
            viewModelScope.launch { recomputeIndicatorSnapshot(merged, _uiState.value.indicatorToggles) }
        }

        _uiState.update { it.copy(liveCandle = continuous) }
    }

    private suspend fun pushFullDataset(symbol: String, interval: String) {
        val snapshot = runCatching { repository.snapshot(symbol, interval) }
            .getOrDefault(emptyList())
            .takeLast(MAX_RENDER_BARS)
        jsChannel.trySend(ChartCommands.setInitialData(snapshot))
        lastIndicatorPush = System.currentTimeMillis()
        recomputeIndicatorSnapshot(snapshot, _uiState.value.indicatorToggles, pushSeries = true)
    }

    // --------------------------------------------------------- indicators --

    private fun merge(stored: List<Candle>, live: Candle?): List<Candle> {
        if (live == null) return stored
        if (stored.isEmpty()) return listOf(live)
        val last = stored.last()
        return when {
            live.time > last.time -> stored + live
            live.time == last.time -> stored.dropLast(1) + live
            else -> stored
        }
    }

    private data class IndicatorBundle(
        val ema20: List<LinePoint>?,
        val ema50: List<LinePoint>?,
        val ema200: List<LinePoint>?,
        val rsi: List<LinePoint>?,
        val stochK: List<LinePoint>?,
        val stochD: List<LinePoint>?,
    )

    private suspend fun recomputeIndicatorSnapshot(
        candles: List<Candle>,
        toggles: IndicatorToggles,
        pushSeries: Boolean = true,
    ) {
        if (candles.isEmpty()) return

        val bundle = withContext(Dispatchers.Default) {
            val ema20 = if (toggles.ema20) Indicators.emaSeries(candles, 20) else null
            val ema50 = if (toggles.ema50) Indicators.emaSeries(candles, 50) else null
            val ema200 = if (toggles.ema200) Indicators.emaSeries(candles, 200) else null
            val rsi = if (toggles.rsi14) Indicators.rsiSeries(candles, 14) else null
            val stoch = if (toggles.stochastic) Indicators.stochasticSeries(candles, 14, 3, 3) else null
            IndicatorBundle(
                ema20 = ema20,
                ema50 = ema50,
                ema200 = ema200,
                rsi = rsi,
                stochK = stoch?.first,
                stochD = stoch?.second,
            )
        }

        _uiState.update { state ->
            state.copy(
                ema20 = bundle.ema20?.lastOrNull()?.value,
                ema50 = bundle.ema50?.lastOrNull()?.value,
                ema200 = bundle.ema200?.lastOrNull()?.value,
                rsi = bundle.rsi?.lastOrNull()?.value,
                stochK = bundle.stochK?.lastOrNull()?.value,
                stochD = bundle.stochD?.lastOrNull()?.value,
            )
        }

        if (!pushSeries) return
        jsChannel.trySend(ChartCommands.setEmaData(bundle.ema20, bundle.ema50, bundle.ema200))
        jsChannel.trySend(ChartCommands.setRsiData(bundle.rsi))
        jsChannel.trySend(ChartCommands.setStochData(bundle.stochK, bundle.stochD))
    }

    // ------------------------------------------------------------ actions --

    fun onChartReady() {
        _uiState.update { it.copy(chartReady = true) }
        viewModelScope.launch {
            jsChannel.trySend(ChartCommands.setDrawMode(_uiState.value.drawingMode))
            pushFullDataset(_uiState.value.symbol, _uiState.value.interval)
        }
    }

    fun requestFullResync() {
        viewModelScope.launch { pushFullDataset(_uiState.value.symbol, _uiState.value.interval) }
    }

    /** Drops the local window and re-downloads the full 1000-bar history. */
    fun forceFullBackfill() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(loading = true) }
            repository.syncHistory(state.symbol, state.interval, force = true)
            pushFullDataset(state.symbol, state.interval)
            _uiState.update {
                it.copy(
                    loading = false,
                    storedBars = runCatching { repository.storedCount(state.symbol, state.interval) }.getOrDefault(0L),
                    message = "History backfilled from the exchange",
                )
            }
        }
    }

    fun selectSymbol(symbol: String) {
        viewModelScope.launch { settingsStore.setSymbol(symbol.uppercase()) }
    }

    fun selectInterval(interval: String) {
        viewModelScope.launch { settingsStore.setInterval(interval) }
    }

    /** Deep-link entry point used by the monitor notification. */
    fun applyDeepLink(symbol: String?, interval: String?) {
        viewModelScope.launch {
            if (!symbol.isNullOrBlank()) settingsStore.setSymbol(symbol.uppercase())
            if (!interval.isNullOrBlank() && Timeframes.ALL.contains(interval)) {
                settingsStore.setInterval(interval)
            }
            scrollToRealtime()
        }
    }

    fun toggleIndicator(key: SettingsStore.IndicatorKey, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setIndicator(key, enabled)
            val merged = merge(_uiState.value.candles, liveCandle)
            val toggles = _uiState.value.indicatorToggles.let { current ->
                when (key) {
                    SettingsStore.IndicatorKey.EMA20 -> current.copy(ema20 = enabled)
                    SettingsStore.IndicatorKey.EMA50 -> current.copy(ema50 = enabled)
                    SettingsStore.IndicatorKey.EMA200 -> current.copy(ema200 = enabled)
                    SettingsStore.IndicatorKey.RSI14 -> current.copy(rsi14 = enabled)
                    SettingsStore.IndicatorKey.STOCHASTIC -> current.copy(stochastic = enabled)
                }
            }
            recomputeIndicatorSnapshot(merged, toggles, pushSeries = true)
        }
    }

    fun setDrawingMode(mode: String) {
        _uiState.update { it.copy(drawingMode = mode) }
        jsChannel.trySend(ChartCommands.setDrawMode(mode))
    }

    fun clearDrawings() {
        jsChannel.trySend(ChartCommands.clearDrawings())
        _uiState.update { it.copy(drawingCount = 0) }
    }

    fun fitContent() {
        jsChannel.trySend(ChartCommands.fitContent())
    }

    fun scrollToRealtime() {
        jsChannel.trySend(ChartCommands.scrollToRealtime())
    }

    fun onCrosshair(open: Double, high: Double, low: Double, close: Double, timeMs: Long) {
        _uiState.update { it.copy(hoverCandle = ChartBridge.candleOf(open, high, low, close, timeMs)) }
    }

    fun onCrosshairReset() {
        _uiState.update { it.copy(hoverCandle = null) }
    }

    fun onDrawingAdded(type: String, count: Int) {
        _uiState.update { it.copy(drawingCount = count) }
    }

    fun onBridgeLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "chart-js: $message")
    }

    // ------------------------------------------------------------ monitor --

    fun setMonitorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setMonitorEnabled(enabled)
            if (enabled) monitorController.start() else monitorController.stop()
        }
    }

    fun pauseMonitorForOneHour() {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + ONE_HOUR_MS
            settingsStore.pauseMonitorUntil(until)
            monitorController.notifyPaused(until)
        }
    }

    fun resumeMonitor() {
        viewModelScope.launch {
            settingsStore.clearMonitorPause()
            monitorController.start()
        }
    }

    fun updateStrategy(transform: (StrategyConfig) -> StrategyConfig) {
        viewModelScope.launch {
            val updated = transform(settingsFlow.value.strategy)
            settingsStore.setStrategy(updated)
        }
    }

    // ----------------------------------------------------------- watchlist --

    fun toggleWatchlist(symbol: String) {
        viewModelScope.launch {
            val current = settingsFlow.value
            val upper = symbol.uppercase()
            val next = if (current.watchlist.contains(upper)) {
                current.watchlist - upper
            } else {
                current.watchlist + upper
            }
            settingsStore.setWatchlist(next)
            polledSymbols.value = (next + current.symbol).distinct()
        }
    }

    // --------------------------------------------------------------- misc --

    fun clearLocalCache() {
        viewModelScope.launch {
            val state = _uiState.value
            repository.clear(state.symbol, state.interval)
            pushFullDataset(state.symbol, state.interval)
            _uiState.update { it.copy(message = "Local candles cleared") }
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update { it.copy(message = "All cached candles cleared") }
        }
    }

    suspend fun searchSymbols(query: String): List<SymbolInfo> = withContext(Dispatchers.Default) {
        catalog.search(query)
    }

    suspend fun refreshTickersFor(symbols: List<String>): List<Ticker24h> =
        repository.tickers(symbols.map { it.uppercase() })

    fun formatPrice(value: Double?): String = Formatters.price(value)

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        private const val TAG = "ChartViewModel"
        const val MAX_RENDER_BARS: Int = 1000
        const val INDICATOR_PUSH_INTERVAL_MS: Long = 2_000L
        private const val ONE_HOUR_MS: Long = 60L * 60L * 1000L
    }
}
