package com.apex.terminal.data.remote.dto

import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.Ticker24h
import com.google.gson.JsonElement

/**
 * Binance `/api/v3/klines` returns a positional array:
 * `[openTime, open, high, low, close, volume, closeTime, quoteVolume, trades,
 * takerBuyBase, takerBuyQuote, ignore]`.
 */
object KlineMapper {

    fun toCandle(row: List<JsonElement>): Candle? {
        if (row.size < 6) return null
        return runCatching {
            Candle(
                time = row[0].doubleValue().toLong(),
                open = row[1].doubleValue(),
                high = row[2].doubleValue(),
                low = row[3].doubleValue(),
                close = row[4].doubleValue(),
                volume = row[5].doubleValue(),
                closed = true,
            )
        }.getOrNull()
    }

    private fun JsonElement.doubleValue(): Double = getAsJsonPrimitive().getAsDouble().toDouble()
}

/** `/api/v3/time` -> `{"serverTime": 1729000000000}` */
data class ServerTimeDto(val serverTime: Long = 0L)

/** `/api/v3/ticker/24hr` entry (numeric fields are string encoded upstream). */
data class Ticker24hDto(
    val symbol: String = "",
    val lastPrice: String = "0",
    val priceChangePercent: String = "0",
    val highPrice: String = "0",
    val lowPrice: String = "0",
    val quoteVolume: String = "0",
) {
    fun toDomain(): Ticker24h = Ticker24h(
        symbol = symbol,
        lastPrice = lastPrice.toDoubleOrNull() ?: 0.0,
        priceChangePercent = priceChangePercent.toDoubleOrNull() ?: 0.0,
        highPrice = highPrice.toDoubleOrNull() ?: 0.0,
        lowPrice = lowPrice.toDoubleOrNull() ?: 0.0,
        quoteVolume = quoteVolume.toDoubleOrNull() ?: 0.0,
    )
}

/** Envelope of a `<symbol>@kline_<interval>` stream payload. */
data class KlineEnvelope(
    val e: String? = null,
    val s: String? = null,
    val k: KlinePayload? = null,
)

data class KlinePayload(
    val t: Long = 0L,
    val s: String = "",
    val i: String = "",
    val o: String = "0",
    val h: String = "0",
    val l: String = "0",
    val c: String = "0",
    val v: String = "0",
    /** `true` once the exchange has finalised the bar — the only moment our
     *  strategy engine is allowed to evaluate a condition. */
    val x: Boolean = false,
) {
    fun toCandle(): Candle = Candle(
        time = t,
        open = o.toDoubleOrNull() ?: 0.0,
        high = h.toDoubleOrNull() ?: 0.0,
        low = l.toDoubleOrNull() ?: 0.0,
        close = c.toDoubleOrNull() ?: 0.0,
        volume = v.toDoubleOrNull() ?: 0.0,
        closed = x,
    )
}
