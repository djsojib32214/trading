package com.apex.terminal.data.remote

import com.apex.terminal.core.EndpointRouter
import com.apex.terminal.data.remote.dto.KlineEnvelope
import com.apex.terminal.domain.model.Candle
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min

/** Everything the terminal needs to know about the live socket. */
sealed interface StreamEvent {
    data class Connecting(val attempt: Int, val onMirror: Boolean) : StreamEvent

    data class Connected(val onMirror: Boolean) : StreamEvent

    data class Disconnected(val attempt: Int, val reason: String?) : StreamEvent

    data class Kline(val candle: Candle, val symbol: String, val interval: String) : StreamEvent
}

/**
 * Binance combined-stream client.
 *
 * Reconnects with capped exponential backoff, flips to the `binance.vision`
 * mirror on transport failure and opportunistically re-probes the primary host
 * once the router considers the outage window over.
 */
class BinanceStreamClient(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val router: EndpointRouter,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
) {

    /**
     * Subscribes to `<symbol>@kline_<interval>`. The flow never completes on
     * its own; cancel the collector to tear the socket down.
     */
    fun klineStream(symbol: String, interval: String): Flow<StreamEvent> = flow {
        val streamName = "${symbol.lowercase()}@kline_$interval"
        var attempt = 0

        while (currentCoroutineContext().isActive) {
            if (attempt > 0 && router.dueForPrimaryWsProbe()) {
                router.promoteWs()
            }
            val onMirror = router.wsRoute == EndpointRouter.Route.MIRROR
            val url = router.wsBase() + streamName
            emit(StreamEvent.Connecting(attempt = attempt, onMirror = onMirror))

            var sawTraffic = false
            try {
                emitAll(frameStream(url, symbol, interval) { sawTraffic = true })
                // Socket closed cleanly by the exchange -> treat as a reconnect.
                emit(StreamEvent.Disconnected(attempt = attempt, reason = "stream closed"))
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                emit(StreamEvent.Disconnected(attempt = attempt, reason = t.message ?: t.javaClass.simpleName))
            }

            if (sawTraffic) {
                attempt = 0
            } else {
                attempt++
                if (onMirror) {
                    router.promoteWs()
                } else {
                    router.failoverWs()
                }
            }
            delay(backoffFor(attempt))
        }
    }

    private fun frameStream(
        url: String,
        symbol: String,
        interval: String,
        onTraffic: () -> Unit,
    ): Flow<StreamEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                router.onWsConnected()
                trySend(StreamEvent.Connected(onMirror = router.wsRoute == EndpointRouter.Route.MIRROR))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onTraffic()
                val envelope = runCatching { gson.fromJson(text, KlineEnvelope::class.java) }.getOrNull() ?: return
                val payload = envelope.k ?: return
                trySend(
                    StreamEvent.Kline(
                        candle = payload.toCandle(),
                        symbol = envelope.s ?: symbol.uppercase(),
                        interval = payload.i.ifBlank { interval },
                    ),
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }

        val socket = client.newWebSocket(request, listener)
        awaitClose { runCatching { socket.cancel() } }
    }

    private fun backoffFor(attempt: Int): Long {
        if (attempt <= 0) return DEFAULT_BASE_BACKOFF_MS
        val exp = DEFAULT_BASE_BACKOFF_MS shl min(attempt - 1, 5)
        return min(exp, maxBackoffMs)
    }

    companion object {
        const val DEFAULT_BASE_BACKOFF_MS: Long = 1_000L
        const val DEFAULT_MAX_BACKOFF_MS: Long = 30_000L
        const val USER_AGENT: String = "ApexTerminal/1.0 (Android)"
    }
}
