package com.apex.terminal.domain

import com.apex.terminal.domain.model.Candle
import com.apex.terminal.domain.model.LinePoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure, allocation-light indicator math executed on the Kotlin side.
 *
 * Every function returns a list aligned to the input list (index `i` of the
 * result corresponds to index `i` of the input) with `null` for the warm-up
 * region, which keeps the mapping to Lightweight Charts trivial.
 */
object Indicators {

    // ------------------------------------------------------------------ EMA --

    /**
     * Exponential moving average, seeded with the SMA of the first [period]
     * samples — the same convention TradingView uses, so the overlay matches
     * the numbers a trader sees on the web platform.
     */
    fun ema(values: DoubleArray, period: Int): DoubleArray {
        require(period > 0) { "period must be positive" }
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out

        var seed = 0.0
        for (i in 0 until period) seed += values[i]
        seed /= period
        out[period - 1] = seed

        val k = 2.0 / (period + 1.0)
        var prev = seed
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    fun emaSeries(candles: List<Candle>, period: Int): List<LinePoint> =
        ema(candles.map { it.close }.toDoubleArray(), period)
            .toLinePoints(candles, ::isFinite)

    fun emaLast(candles: List<Candle>, period: Int): Double? {
        if (candles.size < period) return null
        val arr = ema(candles.map { it.close }.toDoubleArray(), period)
        val v = arr.lastOrNull() ?: return null
        return if (isFinite(v)) v else null
    }

    // ------------------------------------------------------------------ RSI --

    /**
     * Wilder's Relative Strength Index. Returns values in `0..100` with the
     * first valid reading at index [period].
     */
    fun rsi(candles: List<Candle>, period: Int = 14): DoubleArray {
        val out = DoubleArray(candles.size) { Double.NaN }
        if (candles.size <= period) return out

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = candles[i].close - candles[i - 1].close
            if (change >= 0) gainSum += change else lossSum += -change
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        out[period] = rsiFrom(avgGain, avgLoss)

        for (i in period + 1 until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = rsiFrom(avgGain, avgLoss)
        }
        return out
    }

    fun rsiSeries(candles: List<Candle>, period: Int = 14): List<LinePoint> =
        rsi(candles, period).toLinePoints(candles, ::isFinite)

    fun rsiLast(candles: List<Candle>, period: Int = 14): Double? {
        val arr = rsi(candles, period)
        val v = arr.lastOrNull() ?: return null
        return if (isFinite(v)) v else null
    }

    private fun rsiFrom(avgGain: Double, avgLoss: Double): Double = when {
        avgLoss == 0.0 && avgGain == 0.0 -> 50.0
        avgLoss == 0.0 -> 100.0
        else -> {
            val rs = avgGain / avgLoss
            100.0 - (100.0 / (1.0 + rs))
        }
    }

    // ---------------------------------------------------------- Stochastic --

    /**
     * Slow stochastic `(14, 3, 3)`:
     * raw %K over [kPeriod] -> smoothed by [smoothK] (slow %K) -> %D is the
     * [smoothD] SMA of the smoothed %K.
     */
    fun stochastic(
        candles: List<Candle>,
        kPeriod: Int = 14,
        smoothK: Int = 3,
        smoothD: Int = 3,
    ): Pair<DoubleArray, DoubleArray> {
        val rawK = DoubleArray(candles.size) { Double.NaN }
        if (candles.size >= kPeriod) {
            for (i in (kPeriod - 1) until candles.size) {
                var hh = Double.NEGATIVE_INFINITY
                var ll = Double.POSITIVE_INFINITY
                for (j in (i - kPeriod + 1)..i) {
                    hh = max(hh, candles[j].high)
                    ll = min(ll, candles[j].low)
                }
                rawK[i] = if (hh - ll <= 1e-12) 50.0 else ((candles[i].close - ll) / (hh - ll)) * 100.0
            }
        }
        val slowK = smaIgnoringNaN(rawK, smoothK)
        val d = smaIgnoringNaN(slowK, smoothD)
        return slowK to d
    }

    fun stochasticSeries(
        candles: List<Candle>,
        kPeriod: Int = 14,
        smoothK: Int = 3,
        smoothD: Int = 3,
    ): Pair<List<LinePoint>, List<LinePoint>> {
        val (k, d) = stochastic(candles, kPeriod, smoothK, smoothD)
        return k.toLinePoints(candles, ::isFinite) to d.toLinePoints(candles, ::isFinite)
    }

    /** Wilder-style ATR (used by the strategy engine for volatility filters). */
    fun atr(candles: List<Candle>, period: Int = 14): Double? {
        if (candles.size <= period) return null
        val tr = DoubleArray(candles.size)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val prevClose = candles[i - 1].close
            tr[i] = max(
                candles[i].high - candles[i].low,
                max(abs(candles[i].high - prevClose), abs(candles[i].low - prevClose)),
            )
        }
        var sum = 0.0
        for (i in 1..period) sum += tr[i]
        var value = sum / period
        for (i in period + 1 until candles.size) {
            value = (value * (period - 1) + tr[i]) / period
        }
        return value
    }

    // ------------------------------------------------------------ helpers --

    private fun isFinite(v: Double): Boolean = !v.isNaN() && !v.isInfinite()

    private fun DoubleArray.toLinePoints(
        candles: List<Candle>,
        valid: (Double) -> Boolean,
    ): List<LinePoint> {
        val points = ArrayList<LinePoint>(size)
        for (i in indices) {
            val v = this[i]
            if (valid(v) && i < candles.size) points += LinePoint(candles[i].time, v)
        }
        return points
    }

    /** SMA that skips the NaN warm-up region instead of poisoning the window. */
    private fun smaIgnoringNaN(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (period <= 0 || values.size < period) return out
        for (i in (period - 1) until values.size) {
            var sum = 0.0
            var count = 0
            var ok = true
            for (j in (i - period + 1)..i) {
                val v = values[j]
                if (v.isNaN()) {
                    ok = false
                    break
                }
                sum += v
                count++
            }
            if (ok && count == period) out[i] = sum / period
        }
        return out
    }

    /** Simple standard deviation over the trailing [period] closes. */
    fun stdev(values: DoubleArray, period: Int): Double? {
        if (values.size < period) return null
        val window = values.copyOfRange(values.size - period, values.size)
        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / period
        return sqrt(variance)
    }
}
