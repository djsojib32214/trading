package com.apex.terminal.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apex.terminal.core.Timeframes
import com.apex.terminal.domain.StrategyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Everything the terminal persists across process death. */
data class TerminalSettings(
    val symbol: String = DEFAULT_SYMBOL,
    val interval: String = Timeframes.M1,
    val ema20: Boolean = true,
    val ema50: Boolean = true,
    val ema200: Boolean = false,
    val rsi14: Boolean = false,
    val stochastic: Boolean = false,
    val monitorEnabled: Boolean = false,
    val pausedUntil: Long = 0L,
    val strategy: StrategyConfig = StrategyConfig(),
    val watchlist: List<String> = DEFAULT_WATCHLIST,
) {
    val monitorPaused: Boolean get() = pausedUntil > System.currentTimeMillis()

    companion object {
        const val DEFAULT_SYMBOL: String = "BTCUSDT"
        val DEFAULT_WATCHLIST: List<String> = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT")
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_settings")

/** Preference keys; kept top-level so the nested [IndicatorKey] enum can see them. */
private object Keys {
    val symbol = stringPreferencesKey("symbol")
    val interval = stringPreferencesKey("interval")
    val ema20 = booleanPreferencesKey("ind_ema20")
    val ema50 = booleanPreferencesKey("ind_ema50")
    val ema200 = booleanPreferencesKey("ind_ema200")
    val rsi14 = booleanPreferencesKey("ind_rsi14")
    val stochastic = booleanPreferencesKey("ind_stoch")
    val monitorEnabled = booleanPreferencesKey("monitor_enabled")
    val pausedUntil = longPreferencesKey("monitor_paused_until")
    val alertEmaCross = booleanPreferencesKey("alert_ema_cross")
    val alertRsi = booleanPreferencesKey("alert_rsi")
    val alertStoch = booleanPreferencesKey("alert_stoch")
    val alertBreakout = booleanPreferencesKey("alert_breakout")
    val rsiUpper = floatPreferencesKey("rsi_upper")
    val rsiLower = floatPreferencesKey("rsi_lower")
    val breakoutLookback = intPreferencesKey("breakout_lookback")
    val watchlist = stringPreferencesKey("watchlist")
}

class SettingsStore(private val context: Context) {

    val settings: Flow<TerminalSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val defaults = TerminalSettings()
            TerminalSettings(
                symbol = prefs[Keys.symbol] ?: defaults.symbol,
                interval = prefs[Keys.interval] ?: defaults.interval,
                ema20 = prefs[Keys.ema20] ?: defaults.ema20,
                ema50 = prefs[Keys.ema50] ?: defaults.ema50,
                ema200 = prefs[Keys.ema200] ?: defaults.ema200,
                rsi14 = prefs[Keys.rsi14] ?: defaults.rsi14,
                stochastic = prefs[Keys.stochastic] ?: defaults.stochastic,
                monitorEnabled = prefs[Keys.monitorEnabled] ?: defaults.monitorEnabled,
                pausedUntil = prefs[Keys.pausedUntil] ?: defaults.pausedUntil,
                strategy = StrategyConfig(
                    emaCross = prefs[Keys.alertEmaCross] ?: defaults.strategy.emaCross,
                    rsiExtremes = prefs[Keys.alertRsi] ?: defaults.strategy.rsiExtremes,
                    stochasticCross = prefs[Keys.alertStoch] ?: defaults.strategy.stochasticCross,
                    rangeBreakout = prefs[Keys.alertBreakout] ?: defaults.strategy.rangeBreakout,
                    rsiUpper = (prefs[Keys.rsiUpper] ?: defaults.strategy.rsiUpper.toFloat()).toDouble(),
                    rsiLower = (prefs[Keys.rsiLower] ?: defaults.strategy.rsiLower.toFloat()).toDouble(),
                    breakoutLookback = prefs[Keys.breakoutLookback] ?: defaults.strategy.breakoutLookback,
                ),
                watchlist = prefs[Keys.watchlist]
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?: defaults.watchlist,
            )
        }

    suspend fun setSymbol(symbol: String) = edit { it[Keys.symbol] = symbol }

    suspend fun setInterval(interval: String) = edit { it[Keys.interval] = interval }

    suspend fun setIndicator(key: IndicatorKey, enabled: Boolean) = edit { it[key.prefKey] = enabled }

    suspend fun setMonitorEnabled(enabled: Boolean) = edit { it[Keys.monitorEnabled] = enabled }

    suspend fun pauseMonitorUntil(epochMillis: Long) = edit { it[Keys.pausedUntil] = epochMillis }

    suspend fun clearMonitorPause() = edit { it[Keys.pausedUntil] = 0L }

    suspend fun setStrategy(strategy: StrategyConfig) = edit { prefs ->
        prefs[Keys.alertEmaCross] = strategy.emaCross
        prefs[Keys.alertRsi] = strategy.rsiExtremes
        prefs[Keys.alertStoch] = strategy.stochasticCross
        prefs[Keys.alertBreakout] = strategy.rangeBreakout
        prefs[Keys.rsiUpper] = strategy.rsiUpper.toFloat()
        prefs[Keys.rsiLower] = strategy.rsiLower.toFloat()
        prefs[Keys.breakoutLookback] = strategy.breakoutLookback
    }

    suspend fun setWatchlist(symbols: List<String>) =
        edit { it[Keys.watchlist] = symbols.distinct().joinToString(",") }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    enum class IndicatorKey(internal val prefKey: Preferences.Key<Boolean>) {
        EMA20(Keys.ema20),
        EMA50(Keys.ema50),
        EMA200(Keys.ema200),
        RSI14(Keys.rsi14),
        STOCHASTIC(Keys.stochastic),
    }
}

