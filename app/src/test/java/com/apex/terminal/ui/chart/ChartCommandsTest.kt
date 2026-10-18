package com.apex.terminal.ui.chart

import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.LinePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartCommandsTest {

    private val candle = Candle(
        time = 1_700_000_000_000L,
        open = 68_000.5,
        high = 68_400.0,
        low = 67_900.25,
        close = 68_123.75,
        volume = 12.5,
    )

    @Test
    fun `epoch milliseconds are converted to lightweight charts seconds`() {
        val json = ChartCommands.candle(candle)
        assertTrue(json.contains("\"time\":1700000000"))
        assertTrue(!json.contains("1700000000000"))
    }

    @Test
    fun `candle payload carries every ohlcv field`() {
        val json = ChartCommands.candle(candle)
        listOf("open", "high", "low", "close", "volume").forEach { field ->
            assertTrue("missing $field in $json", json.contains("\"$field\""))
        }
        assertTrue(json.contains("68000.5"))
        assertTrue(json.contains("68123.75"))
    }

    @Test
    fun `setInitialData embeds both candles and volumes as parseable JS string literals`() {
        val script = ChartCommands.setInitialData(listOf(candle))

        assertTrue(script.startsWith("window.setInitialData("))
        assertTrue(script.endsWith(");"))
        // The JSON is escaped, so quotes appear as \" inside the literal.
        assertTrue(script.contains("\\\"time\\\":1700000000"))
        assertTrue(script.contains("#2563EB44"))
    }

    @Test
    fun `volume colour follows the candle direction`() {
        val bearish = candle.copy(open = 69_000.0, close = 68_000.0)
        assertTrue(ChartCommands.volumes(listOf(bearish)).contains("#1E222D44"))
        assertTrue(ChartCommands.volumes(listOf(candle)).contains("#2563EB44"))
    }

    @Test
    fun `null indicator payloads are forwarded as JS null so the series is cleared`() {
        val script = ChartCommands.setEmaData(null, null, null)
        assertEquals("window.setEmaData(null,null,null);", script)
    }

    @Test
    fun `indicator payloads are escaped JS string literals`() {
        val points = listOf(LinePoint(1_700_000_000_000L, 68_123.75))
        val script = ChartCommands.setEmaData(points, null, null)

        assertTrue(script.contains("\\\"time\\\":1700000000"))
        assertTrue(script.contains("\\\"value\\\":68123.75"))
        assertTrue(script.contains(",null,null);"))
    }

    @Test
    fun `very small and very large numbers stay machine parseable`() {
        val tiny = ChartCommands.candle(candle.copy(open = 0.0000012345))
        assertTrue(tiny.contains("1.2345") || tiny.contains("e-6"))

        val huge = ChartCommands.candle(candle.copy(high = 1.5e12))
        assertTrue(huge.contains("1.5") && (huge.contains("e+12") || huge.contains("1500000000000")))
    }

    @Test
    fun `drawing commands target the exported js api`() {
        assertEquals("window.setDrawMode(\"trendline\");", ChartCommands.setDrawMode("trendline"))
        assertEquals("window.clearDrawings();", ChartCommands.clearDrawings())
        assertEquals("window.fitContent();", ChartCommands.fitContent())
        assertEquals("window.scrollToRealtime();", ChartCommands.scrollToRealtime())
    }

    @Test
    fun `live tick command round trips a single candle object`() {
        val script = ChartCommands.updateLiveTick(candle)
        assertTrue(script.startsWith("window.updateLiveTick("))
        assertTrue(script.endsWith("\");"))
        assertTrue(script.contains("\\\"close\\\""))
    }
}
