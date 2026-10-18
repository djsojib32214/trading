package com.apex.terminal.ui.chart

import com.apex.terminal.domain.Indicators
import com.apex.terminal.domain.model.Candle
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

/**
 * Exports the exact JavaScript statements the terminal emits, using the real
 * [ChartCommands] serialiser over a synthetic 1000-bar dataset.
 *
 * The fixture is consumed by `tools/chart-bridge-harness.js`, which replays it
 * against the **actual** TradingView Lightweight Charts v5.2.1 bundle inside a
 * headless DOM (see `tools/verify-chart-bridge.sh`). This is what keeps the
 * Kotlin serializer and `assets/chart.html` from drifting apart.
 */
class ChartPayloadFixtureTest {

    private fun syntheticCandles(count: Int): List<Candle> {
        var price = 60_000.0
        return (0 until count).map { index ->
            val drift = sin(index / 11.0) * 180.0 + sin(index / 3.0) * 24.0
            val open = price
            price += drift
            val close = price
            Candle(
                time = 1_700_000_000_000L + index * 60_000L,
                open = open,
                high = maxOf(open, close) + 15.5,
                low = minOf(open, close) - 15.5,
                close = close,
                volume = 100.0 + index % 37,
                closed = true,
            )
        }
    }

    @Test
    fun `export the kotlin to javascript contract for the headless harness`() {
        val candles = syntheticCandles(1000)
        val commands = linkedMapOf(
            "setInitialData" to ChartCommands.setInitialData(candles),
            "updateLiveTick" to ChartCommands.updateLiveTick(
                candles.last().copy(time = candles.last().time + 60_000L, close = candles.last().close + 12.25, closed = false),
            ),
            "setEmaData" to ChartCommands.setEmaData(
                Indicators.emaSeries(candles, 20),
                Indicators.emaSeries(candles, 50),
                Indicators.emaSeries(candles, 200),
            ),
            "setRsiData" to ChartCommands.setRsiData(Indicators.rsiSeries(candles, 14)),
            "setStochData" to Indicators.stochasticSeries(candles, 14, 3, 3).let { (k, d) ->
                ChartCommands.setStochData(k, d)
            },
            "setDrawMode" to ChartCommands.setDrawMode(ChartCommands.MODE_TRENDLINE),
            "fitContent" to ChartCommands.fitContent(),
            "scrollToRealtime" to ChartCommands.scrollToRealtime(),
            "clearDrawings" to ChartCommands.clearDrawings(),
        )

        val outDir = File("build/chart-bridge-fixture").apply { mkdirs() }
        val json = buildString {
            append("{\n")
            append("\"candleCount\":").append(candles.size).append(",\n")
            append("\"lastCandleClose\":").append(candles.last().close).append(",\n")
            append("\"firstCandleTime\":").append(candles.first().time / 1000L).append(",\n")
            append("\"lastCandleTime\":").append(candles.last().time / 1000L).append(",\n")
            append("\"commands\":{")
            commands.entries.forEachIndexed { index, (name, script) ->
                if (index > 0) append(",")
                append("\n  ").append(quote(name)).append(":").append(quote(script))
            }
            append("\n}\n}\n")
        }
        File(outDir, "payload.json").writeText(json)

        assertTrue(commands.values.all { it.startsWith("window.") })
        assertTrue(commands.values.none { it.contains("NaN") || it.contains("Infinity") })
        assertTrue(File(outDir, "payload.json").length() > 10_000L)
    }

    private fun quote(raw: String): String = buildString(raw.length + 16) {
        append('"')
        raw.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append(String.format("\\u%04x", ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
