package com.apex.terminal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apex.terminal.data.local.entity.CandleEntity
import com.apex.terminal.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CandleDao {

    /**
     * Composite-unique upsert. Rows are matched on
     * `(symbol, interval, timestamp)`; a replayed tick or a REST backfill page
     * that overlaps the live stream simply replaces the existing row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(candles: List<CandleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandle(candle: CandleEntity)

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp ASC")
    fun getCandles(symbol: String, interval: String): Flow<List<CandleEntity>>

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp ASC")
    suspend fun getCandlesSnapshot(symbol: String, interval: String): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestCandles(symbol: String, interval: String, limit: Int): List<CandleEntity>

    @Query("SELECT MAX(timestamp) FROM candles WHERE symbol = :symbol AND interval = :interval")
    suspend fun getLatestTimestamp(symbol: String, interval: String): Long?

    @Query("SELECT MIN(timestamp) FROM candles WHERE symbol = :symbol AND interval = :interval")
    suspend fun getOldestTimestamp(symbol: String, interval: String): Long?

    @Query("SELECT COUNT(*) FROM candles WHERE symbol = :symbol AND interval = :interval")
    suspend fun count(symbol: String, interval: String): Long

    @Query("SELECT COUNT(*) FROM candles")
    suspend fun totalCount(): Long

    @Query("DELETE FROM candles WHERE symbol = :symbol AND interval = :interval")
    suspend fun clear(symbol: String, interval: String)

    @Query("DELETE FROM candles")
    suspend fun clearAll()
}

@Dao
interface WatchlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun remove(symbol: String)

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>
}
