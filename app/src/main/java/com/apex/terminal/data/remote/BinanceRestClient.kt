package com.apex.terminal.data.remote

import com.apex.terminal.core.EndpointRouter
import com.apex.terminal.core.Endpoints
import com.apex.terminal.data.remote.dto.KlineMapper
import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.Ticker24h
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * REST engine with automatic ISP failover.
 *
 * Every call is attempted against the router's current host; on any transport
 * failure the router flips to the `binance.vision` mirror and the call is
 * retried exactly once, so a blocked primary host costs the user a single
 * connect timeout rather than an error banner.
 */
class BinanceRestClient(
    private val api: BinanceApi,
    private val router: EndpointRouter,
    private val callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
) {

    val activeRestBase: String get() = router.restBase()

    suspend fun klines(
        symbol: String,
        interval: String,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 1000,
    ): List<Candle> = withFailover { base ->
        api.klines(
            url = base + Endpoints.KLINES_PATH,
            symbol = symbol.uppercase(),
            interval = interval,
            startTime = startTime,
            endTime = endTime,
            limit = limit,
        ).mapNotNull(KlineMapper::toCandle)
    }

    /**
     * Issues the continuity backfill described in the PRD:
     * `startTime = latestTimestamp + 1`, walking forward until the local store
     * catches up with the exchange.
     */
    suspend fun backfill(
        symbol: String,
        interval: String,
        latestTimestamp: Long,
        pageSize: Int = 1000,
        intervalMillis: Long = 60_000L,
    ): List<Candle> {
        val collected = ArrayList<Candle>()
        var cursor = latestTimestamp + 1
        val horizon = System.currentTimeMillis() - intervalMillis
        var guard = 0
        while (guard++ < MAX_BACKFILL_PAGES) {
            if (cursor >= horizon) break
            val page = klines(symbol, interval, startTime = cursor, limit = pageSize)
            if (page.isEmpty()) break
            collected += page
            if (page.size < pageSize) break
            cursor = page.last().time + 1
        }
        return collected
    }

    suspend fun serverTime(): Long? = runCatching {
        withFailover { base -> api.serverTime(base + Endpoints.SERVER_TIME_PATH).serverTime }
    }.getOrNull()

    suspend fun ticker24h(symbols: List<String>): List<Ticker24h> = withFailover { base ->
        val query = if (symbols.isEmpty()) null else symbols.joinToString(
            prefix = "[", postfix = "]", separator = ",",
        ) { "\"${it.uppercase()}\"" }
        api.ticker24h(base + Endpoints.TICKER_24H_PATH, query).map { it.toDomain() }
    }

    /**
     * Round-trip latency of the currently selected REST host. This is the value
     * rendered by the terminal footer badge.
     */
    suspend fun pingLatencyMs(): Long? {
        val started = System.nanoTime()
        val time = serverTime() ?: return null
        if (time <= 0L) return null
        return ((System.nanoTime() - started) / 1_000_000L)
    }

    // ---------------------------------------------------------------- core --

    private suspend inline fun <T> withFailover(
        crossinline block: suspend (String) -> T,
    ): T {
        var lastError: Throwable? = null
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val route = router.restRoute
            val base = router.restBase()
            try {
                val result = withTimeoutOrNull(callTimeoutMs) { block(base) }
                    ?: throw java.io.IOException("timeout after ${callTimeoutMs}ms")
                router.onRestResult(route, success = true)
                return result
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                router.onRestResult(route, success = false)
            }
        }
        throw lastError ?: java.io.IOException("request failed")
    }

    companion object {
        const val DEFAULT_CALL_TIMEOUT_MS: Long = 12_000L
        private const val MAX_BACKFILL_PAGES = 40
    }
}
