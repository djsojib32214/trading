package com.apex.terminal.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.apex.terminal.domain.model.Candle

/**
 * One exchange kline. The composite unique index on
 * `(symbol, interval, timestamp)` is the guarantee against duplicate keys —
 * the streaming client, the REST backfill and the reconnect reconciliation all
 * write through the same `OnConflictStrategy.REPLACE` upsert.
 */
@Entity(
    tableName = "candles",
    indices = [Index(value = ["symbol", "interval", "timestamp"], unique = true)],
)
data class CandleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val interval: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
) {
    fun toDomain(closed: Boolean = true): Candle = Candle(
        time = timestamp,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        closed = closed,
    )

    companion object {
        fun from(symbol: String, interval: String, candle: Candle): CandleEntity = CandleEntity(
            symbol = symbol,
            interval = interval,
            timestamp = candle.time,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
    }
}

/** Symbols the user pinned; survives process death and drives the drawer. */
@Entity(tableName = "watchlist", indices = [Index(value = ["symbol"], unique = true)])
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val addedAt: Long,
)
