package com.apex.terminal.domain.model

/**
 * Exchange kline reduced to the fields the terminal actually renders.
 *
 * [time] is the **exchange open time in epoch milliseconds**, which is also the
 * composite key used by Room. Lightweight Charts consumes epoch *seconds*, the
 * conversion happens exclusively at the JavaScript boundary.
 */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closed: Boolean = true,
) {
    val isBullish: Boolean get() = close >= open

    companion object {
        /** Keeps the `New Candle Open == Previous Candle Close` invariant. */
        fun applyContinuity(previous: Candle?, next: Candle): Candle =
            if (previous != null && next.open != previous.close) {
                next.copy(open = previous.close)
            } else {
                next
            }
    }
}

/** A single `(time, value)` pair for the EMA / RSI / Stochastic overlays. */
data class LinePoint(val time: Long, val value: Double)

/** 24h rolling ticker statistics for the TopAppBar metric row. */
data class Ticker24h(
    val symbol: String,
    val lastPrice: Double,
    val priceChangePercent: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val quoteVolume: Double,
)

/** Entry rendered by the symbol search bottom sheet. */
data class SymbolInfo(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val displayName: String = baseAsset,
) {
    val logoUrl: String get() = "https://bin.bnbstatic.com/static/assets/logos/$baseAsset.png"
}

/** Connection lifecycle surfaced by the terminal footer badge. */
sealed interface ConnectionState {
    val onMirror: Boolean get() = false

    val isLive: Boolean get() = this is Live

    data object Idle : ConnectionState

    data class Connecting(val attempt: Int, override val onMirror: Boolean) : ConnectionState

    data class Live(override val onMirror: Boolean) : ConnectionState

    data class Reconnecting(
        val attempt: Int,
        override val onMirror: Boolean,
        val reason: String?,
    ) : ConnectionState
}
