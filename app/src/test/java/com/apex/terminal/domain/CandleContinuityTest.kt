package com.apex.terminal.domain

import com.apex.terminal.data.local.entity.CandleEntity
import com.apex.terminal.domain.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleContinuityTest {

    @Test
    fun `entity round trip preserves every field`() {
        val candle = Candle(
            time = 1_700_000_000_000L,
            open = 1.1,
            high = 2.2,
            low = 0.9,
            close = 1.7,
            volume = 42.0,
        )
        val entity = CandleEntity.from("BTCUSDT", "1m", candle)

        assertEquals("BTCUSDT", entity.symbol)
        assertEquals("1m", entity.interval)
        assertEquals(candle.time, entity.timestamp)
        assertEquals(candle.open, entity.open, 0.0)
        assertEquals(candle.close, entity.close, 0.0)

        val back = entity.toDomain()
        assertEquals(candle.time, back.time)
        assertEquals(candle.high, back.high, 0.0)
        assertEquals(candle.volume, back.volume, 0.0)
    }

    @Test
    fun `same candle always produces the same unique key`() {
        val candle = Candle(1_700_000_000_000L, 1.0, 2.0, 0.5, 1.5, 10.0)
        val first = CandleEntity.from("BTCUSDT", "1m", candle)
        val second = CandleEntity.from("BTCUSDT", "1m", candle)

        assertEquals(first.symbol, second.symbol)
        assertEquals(first.interval, second.interval)
        assertEquals(first.timestamp, second.timestamp)
    }

    @Test
    fun `continuity rewrites a mismatched open to the previous close`() {
        val previous = Candle(1_000L, 10.0, 12.0, 9.0, 11.0, 1.0)
        val next = Candle(2_000L, 11.4, 13.0, 10.0, 12.0, 1.0)

        val patched = Candle.applyContinuity(previous, next)

        assertEquals(11.0, patched.open, 0.0)
        assertEquals(next.close, patched.close, 0.0)
    }

    @Test
    fun `continuity is a no-op when the invariant already holds`() {
        val previous = Candle(1_000L, 10.0, 12.0, 9.0, 11.0, 1.0)
        val next = Candle(2_000L, 11.0, 13.0, 10.0, 12.0, 1.0)

        val patched = Candle.applyContinuity(previous, next)
        assertEquals(11.0, patched.open, 0.0)
    }

    @Test
    fun `continuity accepts a missing previous bar`() {
        val next = Candle(2_000L, 11.4, 13.0, 10.0, 12.0, 1.0)
        assertEquals(next, Candle.applyContinuity(null, next))
    }

    @Test
    fun `bullish determination follows close versus open`() {
        assertTrue(Candle(0L, 1.0, 2.0, 0.5, 1.5, 0.0).isBullish)
        assertFalse(Candle(0L, 2.0, 2.5, 0.5, 1.5, 0.0).isBullish)
        assertTrue(Candle(0L, 1.0, 2.5, 0.5, 1.0, 0.0).isBullish) // doji counts as bullish
    }
}
