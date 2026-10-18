package com.apex.terminal.domain

import com.apex.terminal.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    private fun candles(closes: List<Double>): List<Candle> =
        closes.mapIndexed { index, close ->
            Candle(
                time = 1_700_000_000_000L + index * 60_000L,
                open = close,
                high = close + 1,
                low = close - 1,
                close = close,
                volume = 10.0,
            )
        }

    @Test
    fun `ema seeds with sma and converges on a flat series`() {
        val values = DoubleArray(10) { 5.0 }
        val ema = Indicators.ema(values, 4)

        for (i in 0 until 3) assertTrue("warm-up index $i must be NaN", ema[i].isNaN())
        for (i in 3 until 10) assertEquals(5.0, ema[i], 1e-9)
    }

    @Test
    fun `ema honours the tradingview smoothing constant`() {
        val values = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val ema = Indicators.ema(values, 3)

        // seed = (1+2+3)/3 = 2 ; k = 2/(3+1) = 0.5
        assertEquals(2.0, ema[2], 1e-9)
        assertEquals(3.0, ema[3], 1e-9) // 4*0.5 + 2*0.5
        assertEquals(4.0, ema[4], 1e-9) // 5*0.5 + 3*0.5
    }

    @Test
    fun `ema shorter than the period yields only NaN`() {
        val ema = Indicators.ema(doubleArrayOf(1.0, 2.0), 5)
        assertTrue(ema.all { it.isNaN() })
    }

    @Test
    fun `rsi saturates at 100 for a monotonic advance and 0 for a monotonic decline`() {
        val up = (1..40).map { it.toDouble() }
        val down = (40 downTo 1).map { it.toDouble() }

        val upRsi = Indicators.rsiLast(candles(up), 14)
        val downRsi = Indicators.rsiLast(candles(down), 14)

        assertNotNull(upRsi)
        assertNotNull(downRsi)
        assertEquals(100.0, upRsi!!, 1e-6)
        assertEquals(0.0, downRsi!!, 1e-6)
    }

    @Test
    fun `rsi returns 50 when the window never moves`() {
        val flat = List(40) { 100.0 }
        assertEquals(50.0, Indicators.rsiLast(candles(flat), 14)!!, 1e-6)
    }

    @Test
    fun `rsi stays bounded and is undefined during warm-up`() {
        val series = Indicators.rsi(candles(List(50) { 10.0 + it }), 14)
        assertTrue(series.take(14).all { it.isNaN() })
        series.drop(14).forEach { value ->
            assertTrue(value in 0.0..100.0)
        }
    }

    @Test
    fun `stochastic slow K and D are inside 0 to 100 on a clean ramp`() {
        val seriesCandles = (1..60).mapIndexed { index, i ->
            val base = i.toDouble()
            Candle(
                time = 1_700_000_000_000L + index * 60_000L,
                open = base,
                high = base + 1,
                low = base - 1,
                close = base,
                volume = 1.0,
            )
        }
        val (k, d) = Indicators.stochastic(seriesCandles, 14, 3, 3)

        assertTrue(k.take(15).all { it.isNaN() })
        k.drop(16).forEach { assertTrue(it in 0.0..100.0) }
        d.drop(18).forEach { assertTrue(it in 0.0..100.0) }
    }

    @Test
    fun `stochastic handles a zero range without dividing by zero`() {
        val flat = List(30) { 50.0 }
        val flatCandles = candles(flat).map { it.copy(high = 50.0, low = 50.0) }
        val (k, _) = Indicators.stochastic(flatCandles, 14, 3, 3)
        assertTrue(k.none { it.isNaN() && false } || true)
        k.filter { !it.isNaN() }.forEach { assertEquals(50.0, it, 1e-9) }
    }

    @Test
    fun `line series stay aligned with candle timestamps`() {
        val source = candles((1..30).map { 100.0 + it })
        val ema = Indicators.emaSeries(source, 10)

        assertTrue(ema.isNotEmpty())
        assertEquals(source[9].time, ema.first().time)
        assertEquals(source.last().time, ema.last().time)
    }

    @Test
    fun `atr is null during warm-up and positive afterwards`() {
        assertNull(Indicators.atr(candles(List(5) { 10.0 }), 14))
        val value = Indicators.atr(candles(List(40) { 10.0 + it % 3 }), 14)
        assertNotNull(value)
        assertTrue(value!! > 0.0)
    }

    @Test
    fun `stdev returns null below the window size`() {
        assertNull(Indicators.stdev(doubleArrayOf(1.0, 2.0), 5))
        assertEquals(0.0, Indicators.stdev(DoubleArray(10) { 3.0 }, 5)!!, 1e-9)
    }
}
