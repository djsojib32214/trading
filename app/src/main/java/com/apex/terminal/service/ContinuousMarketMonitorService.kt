package com.apex.terminal.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.apex.terminal.core.Timeframes
import com.apex.terminal.data.prefs.SettingsStore
import com.apex.terminal.data.prefs.TerminalSettings
import com.apex.terminal.data.remote.StreamEvent
import com.apex.terminal.data.repository.MarketRepository
import com.apex.terminal.domain.StrategyConfig
import com.apex.terminal.domain.StrategyEngine
import com.apex.terminal.domain.model.Candle
import com.apex.terminal.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Continuous background market monitor.
 *
 * Runs as a `dataSync` foreground service with a partial wake lock so it
 * survives Doze/OLED-screen-off sessions while the user games or streams.
 *
 * Strategy conditions are evaluated **exclusively on closed candles**
 * (`k.x == true`), which is what stops the alerts from repainting.
 */
class ContinuousMarketMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var notifications: NotificationHelper
    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: MarketRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var engineJob: Job? = null
    private var settingsJob: Job? = null
    private var pauseJob: Job? = null

    private var symbol: String = TerminalSettings.DEFAULT_SYMBOL
    private var interval: String = Timeframes.M1
    private var strategy: StrategyConfig = StrategyConfig()
    private var paused: Boolean = false
    private var lastPrice: Double? = null
    private var lastAlertCandleTime: Long = Long.MIN_VALUE
    private var lastNotificationAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val locator = ServiceLocator.init(applicationContext)
        notifications = NotificationHelper(this).also { it.ensureChannels() }
        settingsStore = locator.settingsStore
        repository = locator.marketRepository

        promoteToForeground()
        acquireWakeLock()
        observeSettings()
        // Keep latency telemetry alive even when the chart screen is closed.
        scope.launch { runCatching { repository.startLatencyProbe().collect { } } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { settingsStore.setMonitorEnabled(false) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE_1H -> {
                val until = intent.getLongExtra(EXTRA_PAUSED_UNTIL, 0L)
                    .takeIf { it > System.currentTimeMillis() }
                    ?: (System.currentTimeMillis() + ONE_HOUR_MS)
                scope.launch { settingsStore.pauseMonitorUntil(until) }
            }

            ACTION_RESUME -> {
                scope.launch {
                    settingsStore.clearMonitorPause()
                    settingsStore.setMonitorEnabled(true)
                }
            }

            else -> Unit // ACTION_START or a system restart: just keep running
        }
        promoteToForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        engineJob?.cancel()
        settingsJob?.cancel()
        pauseJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ---------------------------------------------------------- foreground --

    private fun promoteToForeground() {
        val notification = notifications.buildMonitorNotification(
            symbol = symbol,
            interval = interval,
            latencyMs = repository.latencyMs.value,
            lastPrice = lastPrice,
            onMirror = repository.route.anyOnMirror,
            paused = paused,
        )
        runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_MONITOR,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }
    }

    private fun refreshNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAt < NOTIFICATION_THROTTLE_MS) return
        lastNotificationAt = now
        notifications.updateMonitor(
            notifications.buildMonitorNotification(
                symbol = symbol,
                interval = interval,
                latencyMs = repository.latencyMs.value,
                lastPrice = lastPrice,
                onMirror = repository.route.anyOnMirror,
                paused = paused,
            ),
        )
    }

    private fun acquireWakeLock() {
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            runCatching { acquire(MAX_WAKE_LOCK_MS) }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    // ------------------------------------------------------------ settings --

    private fun observeSettings() {
        settingsJob = scope.launch {
            settingsStore.settings.collect { settings ->
                strategy = settings.strategy

                pauseJob?.cancel()
                val now = System.currentTimeMillis()
                if (settings.pausedUntil > now) {
                    paused = true
                    val remaining = settings.pausedUntil - now
                    pauseJob = scope.launch {
                        delay(remaining)
                        paused = false
                        refreshNotification(force = true)
                    }
                } else {
                    paused = false
                }

                if (settings.symbol != symbol || settings.interval != interval) {
                    symbol = settings.symbol
                    interval = settings.interval
                    lastAlertCandleTime = Long.MIN_VALUE
                    restartEngine()
                }
                refreshNotification(force = true)
            }
        }
    }

    /** Restarts the socket whenever the monitored symbol or timeframe changes. */
    private fun restartEngine() {
        engineJob?.cancel()
        engineJob = scope.launch {
            val bars = ArrayDeque(repository.snapshot(symbol, interval).takeLast(MAX_WINDOW_BARS))

            repository.liveTicks(symbol, interval).collect { event ->
                if (event !is StreamEvent.Kline) return@collect
                val candle = event.candle
                if (candle.close > 0.0) lastPrice = candle.close

                mergeBar(bars, candle)
                refreshNotification()

                // Closed-candle gate: `k.x == true` only.
                if (candle.closed) evaluate(bars.toList(), candle.time)
            }
        }
    }

    private fun mergeBar(bars: ArrayDeque<Candle>, candle: Candle) {
        val last = bars.lastOrNull()
        when {
            last == null -> bars.addLast(candle)
            candle.time > last.time -> bars.addLast(candle)
            candle.time == last.time -> {
                bars.removeLast()
                bars.addLast(candle)
            }
        }
        while (bars.size > MAX_WINDOW_BARS) bars.removeFirst()
    }

    private fun evaluate(bars: List<Candle>, closedBarTime: Long) {
        if (paused) return
        if (!strategy.anyEnabled) return
        val signals = StrategyEngine.evaluate(bars, strategy, lastAlertCandleTime)
        if (signals.isEmpty()) return
        lastAlertCandleTime = closedBarTime
        signals.forEach { notifications.notifyAlert(symbol, interval, it) }
    }

    companion object {
        const val ACTION_START = "com.apex.terminal.action.MONITOR_START"
        const val ACTION_STOP = "com.apex.terminal.action.MONITOR_STOP"
        const val ACTION_PAUSE_1H = "com.apex.terminal.action.MONITOR_PAUSE_1H"
        const val ACTION_RESUME = "com.apex.terminal.action.MONITOR_RESUME"

        const val EXTRA_SYMBOL = "extra_symbol"
        const val EXTRA_TIMEFRAME = "extra_timeframe"
        const val EXTRA_PAUSED_UNTIL = "extra_paused_until"

        /** Symbol/timeframe echoed back to the Activity by the alert deep link. */
        const val KEY_DEEP_LINK_HANDLED = "key_deep_link_handled"

        private const val WAKE_LOCK_TAG = "ApexTerminal::MarketMonitor"
        private const val MAX_WAKE_LOCK_MS = 6L * 60L * 60L * 1000L
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
        private const val NOTIFICATION_THROTTLE_MS = 3_000L
        private const val MAX_WINDOW_BARS = 600
    }
}
