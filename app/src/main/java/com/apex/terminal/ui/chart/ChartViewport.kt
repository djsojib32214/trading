package com.apex.terminal.ui.chart

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.apex.terminal.BuildConfig
import com.apex.terminal.ui.theme.ApexColors
import com.apex.terminal.ui.theme.MonoMetric
import kotlinx.coroutines.flow.Flow

/** Kotlin-side handlers invoked by the JavaScript chart. */
class ChartBridgeCallbacks(
    val crosshair: (Double, Double, Double, Double, Long) -> Unit = { _, _, _, _, _ -> },
    val reset: () -> Unit = {},
    val drawing: (String, Int) -> Unit = { _, _ -> },
    val log: (String) -> Unit = {},
)

/**
 * Hardware-accelerated WebView hosting the local TradingView Lightweight
 * Charts v5 bundle.
 *
 * Kotlin -> JS flows through [jsCommands]; JS -> Kotlin flows through
 * [ChartBridge] registered as `AndroidBridge`.
 *
 * Commands produced before the chart reports `onChartReady` are queued instead
 * of dropped, so a symbol switch performed during cold start is never lost.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChartViewport(
    jsCommands: Flow<String>,
    callbacks: ChartBridgeCallbacks,
    onChartReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val pending = remember { ArrayDeque<String>() }
    val ready = remember { mutableStateOf(false) }

    val latestCallbacks = rememberUpdatedState(callbacks)
    val latestOnReady = rememberUpdatedState(onChartReady)

    val bridge = remember {
        ChartBridge(
            onCrosshair = { o, h, l, c, t -> latestCallbacks.value.crosshair(o, h, l, c, t) },
            onReset = { latestCallbacks.value.reset() },
            onReady = { mainHandler.post { ready.value = true } },
            onDrawingAdded = { type, count -> latestCallbacks.value.drawing(type, count) },
            onLog = { message -> latestCallbacks.value.log(message) },
        )
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isHapticFeedbackEnabled = false
            settings.configureForCharts()

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    true // the viewport only ever shows the bundled asset
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("ChartViewport", "[js] ${message.message()} @${message.lineNumber()}")
                    }
                    return true
                }
            }

            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
    }

    DisposableEffect(webView, bridge) {
        webView.addJavascriptInterface(bridge, ChartBridge.INTERFACE_NAME)
        webView.loadUrl(ASSET_URL)
        onDispose {
            runCatching {
                webView.removeJavascriptInterface(ChartBridge.INTERFACE_NAME)
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    // Kotlin -> JS with pre-ready queueing.
    LaunchedEffect(webView) {
        jsCommands.collect { command ->
            if (ready.value) {
                webView.evaluateJavascript(command, null)
            } else {
                if (pending.size >= MAX_PENDING_COMMANDS) pending.removeFirst()
                pending.addLast(command)
            }
        }
    }

    // Flush the queue exactly once when the chart engine reports ready.
    LaunchedEffect(webView, ready.value) {
        if (!ready.value) return@LaunchedEffect
        while (pending.isNotEmpty()) {
            webView.evaluateJavascript(pending.removeFirst(), null)
        }
        latestOnReady.value()
    }

    Box(modifier = modifier.background(ApexColors.BgBlack)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        if (!ready.value) {
            Text(
                text = "initialising chart engine…",
                style = MonoMetric,
                color = ApexColors.TextTertiary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun WebSettings.configureForCharts() {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = false
    setSupportZoom(false)
    builtInZoomControls = false
    displayZoomControls = false
    loadWithOverviewMode = false
    useWideViewPort = true
    mediaPlaybackRequiresUserGesture = false
    cacheMode = WebSettings.LOAD_DEFAULT
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    // The page is app-bundled and lives at file:///android_asset/chart.html;
    // it pulls the engine in through a relative <script src>, so the asset
    // origin must stay fully readable. Nothing outside the APK can ever reach
    // this WebView: shouldOverrideUrlLoading rejects every navigation, content
    // access is off and cross-origin fetches stay blocked.
    allowFileAccess = true
    allowContentAccess = false
    blockNetworkImage = false
    textZoom = 100
}

private const val ASSET_URL = "file:///android_asset/chart.html"
private const val MAX_PENDING_COMMANDS = 64
