package com.apex.terminal.domain

import com.apex.terminal.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyEngineTest {

    private fun build(closes: List<Double>, start: Long = 1_700_000_000_000L): List<Candle> =
        closes.mapIndexed { index, close ->
            val previous = if (index == 0) close else closes[index - 1]
            Candle(
                time = start + index * 60_000L,
                open = previous,
                high = maxOf(previous, close) + 0.5,
                low = minOf(previous, close) - 0.5,
                close = close,
                volume = 5.0,
            )
        }

    @Test
    fun `no signals while every condition is disabled`() {
        val candles = build((1..120).map { it.toDouble() })
        val signals = StrategyEngine.evaluate(candles, StrategyConfig(
            emaCross = false,
            rsiExtremes = false,
            stochasticCross = false,
            rangeBreakout = false,
        ))
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `insufficient history produces nothing`() {
        val candles = build((1..10).map { it.toDouble() })
        assertTrue(StrategyEngine.evaluate(candles, StrategyConfig()).isEmpty())
    }

    @Test
    fun `range breakout fires on a close beyond the lookback high`() {
        val flat = List(60) { 100.0 }
        val candles = build(flat + 130.0)
        val signals = StrategyEngine.evaluate(
            candles,
            StrategyConfig(
                emaCross = false,
                rsiExtremes = false,
                stochasticCross = false,
                rangeBreakout = true,
                breakoutLookback = 20,
            ),
        )
        val breakout = signals.filterIsInstance<AlertSignal.RangeBreakout>().single()
        assertTrue(breakout.upward)
        assertEquals(130.0, breakout.price, 1e-9)
    }

    @Test
    fun `range breakdown fires on a close below the lookback low`() {
        val flat = List(60) { 100.0 }
        val candles = build(flat + 70.0)
        val signals = StrategyEngine.evaluate(
            candles,
            StrategyConfig(
                emaCross = false,
                rsiExtremes = false,
                stochasticCross = false,
                rangeBreakout = true,
                breakoutLookback = 20,
            ),
        )
        val breakout = signals.filterIsInstance<AlertSignal.RangeBreakout>().single()
        assertTrue(!breakout.upward)
    }

    @Test
    fun `rsi overbought fires when the reading crosses the upper bound`() {
        // A long flat base followed by a sharp rally drives RSI(14) from 50 to
        // 100. The engine is invoked once per newly closed bar, exactly as the
        // monitor service drives it.
        val candles = build(List(80) { 100.0 } + (1..6).map { 100.0 + it * 3 })
        val config = StrategyConfig(
            emaCross = false,
            stochasticCross = false,
            rangeBreakout = false,
            rsiExtremes = true,
        )
        val overbought = walk(candles, config).any { it is AlertSignal.RsiExtreme && it.overbought }
        assertTrue("expected an overbought RSI alert", overbought)
    }

    @Test
    fun `rsi oversold fires when the reading crosses the lower bound`() {
        val candles = build(List(80) { 100.0 } + (1..6).map { 100.0 - it * 3 })
        val config = StrategyConfig(
            emaCross = false,
            stochasticCross = false,
            rangeBreakout = false,
            rsiExtremes = true,
        )
        val oversold = walk(candles, config).any { it is AlertSignal.RsiExtreme && !it.overbought }
        assertTrue("expected an oversold RSI alert", oversold)
    }

    @Test
    fun `ema cross is detected on a reversal`() {
        val down = (1..90).map { 200.0 - it }
        val up = (1..30).map { 110.0 + it * 2.0 }
        val candles = build(down + up)
        val config = StrategyConfig(
            emaCross = true,
            rsiExtremes = false,
            stochasticCross = false,
            rangeBreakout = false,
            emaFast = 20,
            emaSlow = 50,
        )
        val bullish = walk(candles, config).any { it is AlertSignal.EmaCross && it.bullish }
        assertTrue("expected a bullish EMA cross alert", bullish)
    }

    @Test
    fun `stochastic cross fires on a reversal out of the oversold region`() {
        val down = (1..60).map { 300.0 - it * 2 }
        val up = (1..15).map { 180.0 + it * 4.0 }
        val candles = build(down + up)
        val config = StrategyConfig(
            emaCross = false,
            rsiExtremes = false,
            stochasticCross = true,
            rangeBreakout = false,
        )
        val cross = walk(candles, config).any { it is AlertSignal.StochCross && it.bullish }
        assertTrue("expected a stochastic bullish cross", cross)
    }

    /**
     * Simulates the live loop: the engine only ever sees a growing window whose
     * final element is the bar the exchange has just closed.
     */
    private fun walk(candles: List<Candle>, config: StrategyConfig): List<AlertSignal> {
        val found = ArrayList<AlertSignal>()
        for (size in 40..candles.size) {
            found += StrategyEngine.evaluate(candles.take(size), config, lastAlertCandleTime = Long.MIN_VALUE)
        }
        return found
    }

    @Test
    fun `the same closed bar is never alerted twice`() {
        val candles = build(List(60) { 100.0 } + 130.0)
        val config = StrategyConfig(
            emaCross = false,
            rsiExtremes = false,
            stochasticCross = false,
            rangeBreakout = true,
        )
        val first = StrategyEngine.evaluate(candles, config, lastAlertCandleTime = Long.MIN_VALUE)
        assertTrue(first.isNotEmpty())

        val second = StrategyEngine.evaluate(candles, config, lastAlertCandleTime = candles.last().time)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `signal payloads carry human readable titles`() {
        val signal = AlertSignal.RsiExtreme(candleTime = 1L, value = 71.5, overbought = true)
        assertEquals("RSI overbought", signal.title)
        assertTrue(signal.detail.contains("71.5"))

        val cross = AlertSignal.EmaCross(candleTime = 1L, fast = 20, slow = 50, bullish = true, price = 1.0)
        assertEquals("EMA20 crossed ABOVE EMA50", cross.title)
    }
}
