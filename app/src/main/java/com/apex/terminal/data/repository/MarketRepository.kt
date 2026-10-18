package com.apex.terminal.data.repository

import com.apex.terminal.core.EndpointRouter
import com.apex.terminal.core.RouteSnapshot
import com.apex.terminal.core.Timeframes
import com.apex.terminal.data.local.CandleDao
import com.apex.terminal.data.local.entity.CandleEntity
import com.apex.terminal.data.remote.BinanceRestClient
import com.apex.terminal.data.remote.BinanceStreamClient
import com.apex.terminal.data.remote.StreamEvent
import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.ConnectionState
import com.apex.terminal.domain.model.Ticker24h
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * The single source of truth for chart data.
 *
 * Responsibilities:
 *  - hydrate history from Room,
 *  - reconcile gaps against the exchange over REST before going live,
 *  - persist streamed ticks (closed bars immediately, open bars throttled),
 *  - expose connection + latency telemetry for the HUD/footer.
 */
class MarketRepository(
    private val dao: CandleDao,
    private val rest: BinanceRestClient,
    private val stream: BinanceStreamClient,
    private val router: EndpointRouter,
    private val scope: CoroutineScope,
) {

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private val lastOpenBarPersist = AtomicLong(0L)

    val route: RouteSnapshot get() = router.snapshot

    // ----------------------------------------------------------- history ---

    fun observeCandles(symbol: String, interval: String): Flow<List<Candle>> =
        dao.getCandles(symbol, interval).map { rows -> rows.map { it.toDomain() } }

    suspend fun snapshot(symbol: String, interval: String): List<Candle> =
        dao.getCandlesSnapshot(symbol, interval).map { it.toDomain() }

    suspend fun latestTimestamp(symbol: String, interval: String): Long? =
        dao.getLatestTimestamp(symbol, interval)

    suspend fun storedCount(symbol: String, interval: String): Long = dao.count(symbol, interval)

    suspend fun totalStored(): Long = dao.totalCount()

    suspend fun clear(symbol: String, interval: String) = dao.clear(symbol, interval)

    suspend fun clearAll() = dao.clearAll()

    /**
     * Continuity guarantee: on cold start fetch the first [HISTORY_BARS] bars;
     * afterwards ask [BinanceRestClient.backfill] for everything after the last
     * stored timestamp and upsert it *before* the socket is allowed to stream.
     */
    suspend fun syncHistory(symbol: String, interval: String, force: Boolean = false): Int {
        val latest = dao.getLatestTimestamp(symbol, interval)
        if (latest == null || force) {
            val seed = rest.klines(symbol, interval, limit = HISTORY_BARS)
            if (seed.isEmpty()) return 0
            dao.upsertCandles(seed.map { CandleEntity.from(symbol, interval, it) })
            return seed.size
        }

        val gap = rest.backfill(
            symbol = symbol,
            interval = interval,
            latestTimestamp = latest,
            intervalMillis = Timeframes.durationMillis(interval),
        )
        if (gap.isNotEmpty()) {
            dao.upsertCandles(gap.map { CandleEntity.from(symbol, interval, it) })
        }
        return gap.size
    }

    // ------------------------------------------------------------ stream ---

    /**
     * Live socket wired to persistence and telemetry. Emits the raw
     * [StreamEvent]s so callers (chart VM, monitor service) can react to
     * closed bars independently.
     */
    fun liveTicks(symbol: String, interval: String): Flow<StreamEvent> =
        stream.klineStream(symbol, interval)
            .onEach { event -> handleEvent(symbol, interval, event) }

    private suspend fun handleEvent(symbol: String, interval: String, event: StreamEvent) {
        when (event) {
            is StreamEvent.Connecting -> {
                _connection.value = ConnectionState.Connecting(event.attempt, event.onMirror)
            }

            is StreamEvent.Connected -> {
                _connection.value = ConnectionState.Live(event.onMirror)
            }

            is StreamEvent.Disconnected -> {
                _connection.value = ConnectionState.Reconnecting(
                    attempt = event.attempt + 1,
                    onMirror = router.wsRoute == EndpointRouter.Route.MIRROR,
                    reason = event.reason,
                )
            }

            is StreamEvent.Kline -> persistTick(symbol, interval, event.candle)
        }
    }

    /**
     * Persists a streamed tick. Closed bars are written immediately (they are
     * final and drive the strategy engine); the still-forming bar is written at
     * most once every [OPEN_BAR_PERSIST_INTERVAL_MS] to keep the WAL writer
     * quiet while preserving crash recovery.
     */
    suspend fun persistTick(symbol: String, interval: String, candle: Candle) {
        val now = System.currentTimeMillis()
        val shouldWrite = candle.closed ||
            now - lastOpenBarPersist.get() >= OPEN_BAR_PERSIST_INTERVAL_MS
        if (!shouldWrite) return
        lastOpenBarPersist.set(now)
        runCatching { dao.upsertCandle(CandleEntity.from(symbol, interval, candle)) }
    }

    // ----------------------------------------------------------- tickers ---

    /** Polls 24h statistics for the pinned symbol set; emits on every refresh. */
    fun tickerPoll(
        symbols: Flow<List<String>>,
        pollIntervalMs: Long = TICKER_POLL_MS,
    ): Flow<List<Ticker24h>> = symbols
        .distinctUntilChanged()
        .flatMapLatest { list ->
            val upper = list.map { it.uppercase() }.distinct()
            if (upper.isEmpty()) {
                flowOf(emptyList())
            } else {
                flow {
                    while (true) {
                        val result = runCatching { rest.ticker24h(upper) }.getOrNull()
                        if (result != null) emit(result)
                        delay(pollIntervalMs)
                    }
                }
            }
        }

    /** One-shot variant used by the search sheet when it opens. */
    suspend fun tickers(symbols: List<String>): List<Ticker24h> =
        runCatching { rest.ticker24h(symbols) }.getOrDefault(emptyList())

    // ---------------------------------------------------------- telemetry ---

    private val _serverSkewMs = MutableStateFlow<Long?>(null)
    val serverSkewMs: StateFlow<Long?> = _serverSkewMs.asStateFlow()

    /**
     * Lightweight latency probe. `GET /api/v3/time` is the cheapest endpoint on
     * the exchange, so polling it every few seconds costs nothing while giving
     * an honest round-trip figure for the footer badge.
     */
    fun startLatencyProbe(intervalMs: Long = LATENCY_PROBE_MS): Flow<Long?> = flow {
        emitAll(
            flow {
                while (true) {
                    val started = System.currentTimeMillis()
                    val serverTime = rest.serverTime()
                    val elapsed = System.currentTimeMillis() - started
                    if (serverTime != null) {
                        _latencyMs.value = elapsed
                        _serverSkewMs.value = serverTime - (started + elapsed / 2)
                        emit(elapsed)
                    } else {
                        emit(null)
                    }
                    delay(intervalMs)
                }
            },
        )
    }

    /** Fires the latency probe on the repository scope (fire and forget). */
    fun startLatencyProbeInBackground(intervalMs: Long = LATENCY_PROBE_MS) {
        scope.launch { runCatching { startLatencyProbe(intervalMs).collect { } } }
    }

    companion object {
        const val HISTORY_BARS: Int = 1000
        const val OPEN_BAR_PERSIST_INTERVAL_MS: Long = 5_000L
        const val TICKER_POLL_MS: Long = 15_000L
        const val LATENCY_PROBE_MS: Long = 20_000L
    }
}
