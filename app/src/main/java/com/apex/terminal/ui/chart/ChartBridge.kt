package com.apex.terminal.ui.chart

import android.webkit.JavascriptInterface
import com.apex.terminal.domain.model.Candle

/**
 * Bidirectional Kotlin <-> Lightweight Charts bridge.
 *
 * The WebView invokes these members from its private "JavaBridge" thread, so
 * every callback lands on a plain lambda; the ViewModel state holders it talks
 * to ([kotlinx.coroutines.flow.MutableStateFlow]) are thread-safe by contract.
 */
class ChartBridge(
    private val onCrosshair: (Double, Double, Double, Double, Long) -> Unit,
    private val onReset: () -> Unit,
    private val onReady: () -> Unit = {},
    private val onDrawingAdded: (String, Int) -> Unit = { _, _ -> },
    private val onLog: (String) -> Unit = {},
) {

    /**
     * `time` arrives in Unix **seconds** (Lightweight Charts convention) and is
     * normalised to the epoch-millisecond domain used by Room and the DAOs.
     */
    @JavascriptInterface
    fun onCrosshairUpdate(open: Double, high: Double, low: Double, close: Double, time: Long) {
        runCatching { onCrosshair(open, high, low, close, time * 1_000L) }
    }

    @JavascriptInterface
    fun onCrosshairReset() {
        runCatching { onReset() }
    }

    @JavascriptInterface
    fun onChartReady() {
        runCatching { onReady() }
    }

    @JavascriptInterface
    fun onDrawingAdded(type: String, count: Int) {
        runCatching { onDrawingAdded(type, count) }
    }

    @JavascriptInterface
    fun onLog(message: String) {
        runCatching { onLog(message) }
    }

    companion object {
        /** The name the JavaScript side resolves the interface under. */
        const val INTERFACE_NAME: String = "AndroidBridge"

        /** Graph for a hovered bar, normalised into the Kotlin domain model. */
        fun candleOf(open: Double, high: Double, low: Double, close: Double, timeMs: Long): Candle =
            Candle(time = timeMs, open = open, high = high, low = low, close = close, volume = 0.0, closed = false)
    }
}
