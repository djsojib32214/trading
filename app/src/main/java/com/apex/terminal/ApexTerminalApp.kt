package com.apex.terminal

import android.app.Application
import com.apex.terminal.service.NotificationHelper
import com.apex.terminal.util.ServiceLocator

class ApexTerminalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val locator = ServiceLocator.init(this)

        // Channels are created eagerly so the monitor service can post its
        // foreground notification the instant it is started.
        NotificationHelper(this).ensureChannels()

        // Warm the latency probe so the footer badge is populated on first frame.
        locator.marketRepository.startLatencyProbeInBackground()
    }
}
