package com.apex.terminal.ui.chart

import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.LinePoint
import java.util.Locale

/**
 * Serialises Kotlin state into the exact JavaScript calls exported by
 * `assets/chart.html`.
 *
 * Everything here is a pure function over domain values so the wire format can
 * be verified in unit tests without an emulator.
 */
object ChartCommands {

    /** 4-decimal formatting keeps the payload small without losing tick fidelity. */
    private fun num(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val abs = kotlin.math.abs(value)
        return if (abs != 0.0 && (abs < 1e-4 || abs >= 1e9)) {
            String.format(Locale.US, "%.10e", value)
        } else {
            String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun jsonString(raw: String): String = "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun candle(c: Candle): String = buildString(96) {
        append("{\"time\":").append(c.time / 1000L)
        append(",\"open\":").append(num(c.open))
        append(",\"high\":").append(num(c.high))
        append(",\"low\":").append(num(c.low))
        append(",\"close\":").append(num(c.close))
        append(",\"volume\":").append(num(c.volume))
        append('}')
    }

    fun candles(list: List<Candle>): String = list.joinToString(prefix = "[", postfix = "]", separator = ",") { candle(it) }

    fun volumes(list: List<Candle>): String = list.joinToString(prefix = "[", postfix = "]", separator = ",") { c ->
        buildString(56) {
            append("{\"time\":").append(c.time / 1000L)
            append(",\"value\":").append(num(c.volume))
            append(",\"color\":\"").append(if (c.isBullish) "#2563EB44" else "#1E222D44").append("\"}")
        }
    }

    fun line(points: List<LinePoint>): String =
        points.joinToString(prefix = "[", postfix = "]", separator = ",") { p ->
            "{\"time\":${p.time / 1000L},\"value\":${num(p.value)}}"
        }

    fun setInitialData(candles: List<Candle>): String =
        "window.setInitialData(${jsonString(candles(candles))},${jsonString(volumes(candles))});"

    fun updateLiveTick(candle: Candle): String =
        "window.updateLiveTick(${jsonString(candle(candle))});"

    fun setEmaData(
        ema20: List<LinePoint>?,
        ema50: List<LinePoint>?,
        ema200: List<LinePoint>?,
    ): String = "window.setEmaData(${payload(ema20)},${payload(ema50)},${payload(ema200)});"

    private fun payload(points: List<LinePoint>?): String = if (points == null) "null" else jsonString(line(points))

    fun setRsiData(points: List<LinePoint>?): String =
        "window.setRsiData(${payload(points)});"

    fun setStochData(k: List<LinePoint>?, d: List<LinePoint>?): String =
        "window.setStochData(${payload(k)},${payload(d)});"

    fun setDrawMode(mode: String): String = "window.setDrawMode(${jsonString(mode)});"

    fun clearDrawings(): String = "window.clearDrawings();"

    fun fitContent(): String = "window.fitContent();"

    fun scrollToRealtime(): String = "window.scrollToRealtime();"

    /** Drawing tool identifiers shared with [com.apex.terminal.ui.components.DrawingTool]. */
    const val MODE_CROSSHAIR: String = "crosshair"
    const val MODE_TRENDLINE: String = "trendline"
    const val MODE_RAY: String = "ray"
    const val MODE_FIBONACCI: String = "fib"
}
