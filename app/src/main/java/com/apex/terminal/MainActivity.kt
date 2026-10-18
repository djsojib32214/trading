package com.apex.terminal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.apex.terminal.service.ContinuousMarketMonitorService
import com.apex.terminal.ui.chart.ChartViewModel
import com.apex.terminal.ui.main.ChartViewModelFactory
import com.apex.terminal.ui.main.TerminalScreen
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.ApexTerminalTheme
import com.apex.terminal.util.ServiceLocator
import android.widget.Toast
import androidx.compose.material3.Surface

/**
 * Single-activity host.
 *
 * Declared `singleTask` in the manifest so the alert deep link
 * (`apexterminal://chart?symbol=…&interval=…`) is delivered through
 * [onNewIntent] and the WebView — including every user drawing — is never
 * recreated.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ChartViewModel by viewModels {
        ChartViewModelFactory(application, ServiceLocator.init(applicationContext))
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notifications are disabled — background strategy alerts cannot be shown.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ApexTerminalTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ApexColors.BgBlack),
                    color = ApexColors.BgBlack,
                ) {
                    TerminalScreen(viewModel = viewModel)
                }
            }
        }

        requestNotificationPermissionIfNeeded()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Routes both the URI deep link and the explicit extras payload. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW && !intent.hasExtra(ContinuousMarketMonitorService.EXTRA_SYMBOL)) {
            return
        }

        val fromUri = intent.data
        val symbol = intent.getStringExtra(ContinuousMarketMonitorService.EXTRA_SYMBOL)
            ?: fromUri?.getQueryParameter("symbol")
        val interval = intent.getStringExtra(ContinuousMarketMonitorService.EXTRA_TIMEFRAME)
            ?: fromUri?.getQueryParameter("interval")

        if (symbol.isNullOrBlank() && interval.isNullOrBlank()) return
        viewModel.applyDeepLink(symbol, interval)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
