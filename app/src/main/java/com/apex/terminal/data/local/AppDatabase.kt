package com.apex.terminal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apex.terminal.data.local.entity.CandleEntity
import com.apex.terminal.data.local.entity.WatchlistEntity

@Database(
    entities = [CandleEntity::class, WatchlistEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun candleDao(): CandleDao

    abstract fun watchlistDao(): WatchlistDao

    companion object {
        private const val DB_NAME = "apex_terminal.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                // WAL keeps the live-tick writer from ever blocking the chart reader.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration()
                .build()
    }
}
