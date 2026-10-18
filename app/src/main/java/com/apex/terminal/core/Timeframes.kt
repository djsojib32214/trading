package com.apex.terminal.core

import java.util.Locale

/** Timeframe identifiers used across Room, Binance and the chart. */
object Timeframes {
    const val M1 = "1m"
    const val M3 = "3m"
    const val M5 = "5m"
    const val M15 = "15m"
    const val H1 = "1h"
    const val H4 = "4h"
    const val D1 = "1D"

    val ALL: List<String> = listOf(M1, M3, M5, M15, H1, H4, D1)

    fun label(tf: String): String = if (tf == D1) "1D" else tf

    fun durationMillis(tf: String): Long = when (tf) {
        M1 -> 60_000L
        M3 -> 3 * 60_000L
        M5 -> 5 * 60_000L
        M15 -> 15 * 60_000L
        H1 -> 60 * 60_000L
        H4 -> 4 * 60 * 60_000L
        D1 -> 24 * 60 * 60_000L
        else -> 60_000L
    }

    /** `BTCUSDT` -> `BTC/USDT` for display pills. */
    fun pretty(symbol: String): String {
        val quote = listOf("USDT", "FDUSD", "USDC", "TUSD", "BTC", "ETH", "BNB", "EUR", "TRY")
            .firstOrNull { symbol.uppercase(Locale.US).endsWith(it) } ?: return symbol
        val base = symbol.dropLast(quote.length)
        if (base.isEmpty()) return symbol
        return "$base/$quote"
    }
}
