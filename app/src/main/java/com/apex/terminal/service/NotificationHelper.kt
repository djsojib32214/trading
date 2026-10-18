package com.apex.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.apex.terminal.MainActivity
import com.apex.terminal.R
import com.apex.terminal.core.Timeframes
import com.apex.terminal.domain.AlertSignal
import com.apex.terminal.util.Formatters

/**
 * Owns every notification surface of the terminal.
 *
 * Two channels are declared, matching the PRD:
 *  - `monitor` — `IMPORTANCE_LOW`, silent ongoing status, two actions.
 *  - `alerts`  — `IMPORTANCE_HIGH`, heads-up with sound + vibration.
 */
class NotificationHelper(private val context: Context) {

    private val manager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_monitor_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
            enableVibration(true)
            vibrationPattern = ALERT_VIBRATION
            enableLights(true)
            lightColor = Color.parseColor("#2563EB")
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        system.createNotificationChannel(monitorChannel)
        system.createNotificationChannel(alertChannel)
    }

    // ------------------------------------------------------------ monitor --

    fun buildMonitorNotification(
        symbol: String,
        interval: String,
        latencyMs: Long?,
        lastPrice: Double?,
        onMirror: Boolean,
        paused: Boolean,
    ): Notification {
        val content = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLink(symbol, interval))
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val contentPending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            content,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pauseLabel = if (paused) "RESUME MONITOR" else context.getString(R.string.monitor_action_pause)
        val pauseIntent = PendingIntent.getService(
            context,
            REQUEST_PAUSE,
            Intent(context, ContinuousMarketMonitorService::class.java)
                .setAction(
                    if (paused) ContinuousMarketMonitorService.ACTION_RESUME
                    else ContinuousMarketMonitorService.ACTION_PAUSE_1H,
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, ContinuousMarketMonitorService::class.java)
                .setAction(ContinuousMarketMonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val status = buildString {
            append(if (paused) "PAUSED" else "MONITORING")
            append(" • ")
            append(Timeframes.pretty(symbol))
            append(" ")
            append(interval)
            if (lastPrice != null) {
                append(" • ")
                append(Formatters.price(lastPrice))
            }
            append(" • ")
            append(latencyMs?.let { "${it}ms" } ?: "--ms")
            if (onMirror) append(" • MIRROR")
        }

        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle("ApexTerminal monitor")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setContentIntent(contentPending)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setColor(Color.parseColor("#2563EB"))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, pauseLabel, pauseIntent)
            .addAction(0, context.getString(R.string.monitor_action_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun updateMonitor(notification: Notification) {
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID_MONITOR, notification) }
    }

    // -------------------------------------------------------------- alerts --

    fun notifyAlert(symbol: String, interval: String, signal: AlertSignal) {
        if (!manager.areNotificationsEnabled()) return

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLink(symbol, interval))
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ContinuousMarketMonitorService.EXTRA_SYMBOL, symbol)
            putExtra(ContinuousMarketMonitorService.EXTRA_TIMEFRAME, interval)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_ALERT,
            deepLinkIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = "${Timeframes.pretty(symbol)} • ${signal.title}"
        val body = buildString {
            append(signal.detail)
            append("\n")
            append(interval)
            append(" bar closed ")
            append(Formatters.clock(signal.candleTime))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(title)
            .setContentText(signal.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(Color.parseColor("#089981"))
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(ALERT_VIBRATION)
            .setContentIntent(pending)
            .addAction(0, context.getString(R.string.monitor_action_open), pending)
            .build()

        runCatching { manager.notify(alertTag(symbol, signal), NOTIFICATION_ID_ALERT, notification) }
    }

    private fun alertTag(symbol: String, signal: AlertSignal): String =
        "alert:$symbol:${signal.candleTime}:${signal::class.simpleName}"

    fun canPostNotifications(): Boolean = manager.areNotificationsEnabled()

    companion object {
        const val CHANNEL_MONITOR: String = "apex_monitor"
        const val CHANNEL_ALERTS: String = "apex_alerts"

        const val NOTIFICATION_ID_MONITOR: Int = 4201
        const val NOTIFICATION_ID_ALERT: Int = 4202

        private const val REQUEST_OPEN = 91
        private const val REQUEST_PAUSE = 92
        private const val REQUEST_STOP = 93
        private const val REQUEST_ALERT = 94

        private val ALERT_VIBRATION = longArrayOf(0L, 260L, 180L, 260L)

        /** `apexterminal://chart?symbol=BTCUSDT&interval=1m` */
        fun deepLink(symbol: String, interval: String): String =
            "apexterminal://chart?symbol=$symbol&interval=$interval"
    }
}
