package com.apex.terminal.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/** Thin, testable façade the ViewModel uses to drive the monitor service. */
class MonitorController(private val context: Context) {

    fun start() {
        val intent = Intent(context, ContinuousMarketMonitorService::class.java)
            .setAction(ContinuousMarketMonitorService.ACTION_START)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stop() {
        val intent = Intent(context, ContinuousMarketMonitorService::class.java)
            .setAction(ContinuousMarketMonitorService.ACTION_STOP)
        // Route the stop through the service so it can tear down cleanly, then
        // make sure it is gone even if the process was already being killed.
        runCatching { context.startService(intent) }
        runCatching { context.stopService(Intent(context, ContinuousMarketMonitorService::class.java)) }
    }

    fun notifyPaused(pausedUntilEpochMillis: Long) {
        val intent = Intent(context, ContinuousMarketMonitorService::class.java)
            .setAction(ContinuousMarketMonitorService.ACTION_PAUSE_1H)
            .putExtra(ContinuousMarketMonitorService.EXTRA_PAUSED_UNTIL, pausedUntilEpochMillis)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun resume() {
        val intent = Intent(context, ContinuousMarketMonitorService::class.java)
            .setAction(ContinuousMarketMonitorService.ACTION_RESUME)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
