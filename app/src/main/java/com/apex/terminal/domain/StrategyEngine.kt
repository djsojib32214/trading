package com.apex.terminal.domain

import com.apex.terminal.domain.model.Candle
import java.util.Locale

/** User-configurable alert matrix for the background monitor service. */
data class StrategyConfig(
    val emaCross: Boolean = true,
    val rsiExtremes: Boolean = true,
    val stochasticCross: Boolean = true,
    val rangeBreakout: Boolean = true,
    val rsiUpper: Double = 70.0,
    val rsiLower: Double = 30.0,
    val emaFast: Int = 20,
    val emaSlow: Int = 50,
    val breakoutLookback: Int = 20,
) {
    val anyEnabled: Boolean
        get() = emaCross || rsiExtremes || stochasticCross || rangeBreakout
}

/** A condition that fired on a **closed** candle. */
sealed interface AlertSignal {
    val candleTime: Long
    val title: String
    val detail: String

    data class EmaCross(
        override val candleTime: Long,
        val fast: Int,
        val slow: Int,
        val bullish: Boolean,
        val price: Double,
    ) : AlertSignal {
        override val title: String
            get() = if (bullish) "EMA$fast crossed ABOVE EMA$slow" else "EMA$fast crossed BELOW EMA$slow"
        override val detail: String
            get() = String.format(Locale.US, "%s • close %.8f", if (bullish) "Bullish" else "Bearish", price)
    }

    data class RsiExtreme(
        override val candleTime: Long,
        val value: Double,
        val overbought: Boolean,
    ) : AlertSignal {
        override val title: String
            get() = if (overbought) "RSI overbought" else "RSI oversold"
        override val detail: String
            get() = String.format(Locale.US, "RSI(14) = %.2f", value)
    }

    data class StochCross(
        override val candleTime: Long,
        val k: Double,
        val d: Double,
        val bullish: Boolean,
    ) : AlertSignal {
        override val title: String
            get() = if (bullish) "Stochastic bullish cross" else "Stochastic bearish cross"
        override val detail: String
            get() = String.format(Locale.US, "%%K %.2f / %%D %.2f", k, d)
    }

    data class RangeBreakout(
        override val candleTime: Long,
        val lookback: Int,
        val upward: Boolean,
        val price: Double,
    ) : AlertSignal {
        override val title: String
            get() = if (upward) "Breakout above $lookback-bar high" else "Breakdown below $lookback-bar low"
        override val detail: String
            get() = String.format(Locale.US, "close %.8f", price)
    }
}

/**
 * Deterministic strategy evaluator.
 *
 * Contract: [evaluate] is only ever invoked with a candle list whose **last**
 * element is a candle the exchange has already closed (`k.x == true`), which is
 * what guarantees indicators never repaint mid-bar.
 */
object StrategyEngine {

    fun evaluate(
        candles: List<Candle>,
        config: StrategyConfig,
        lastAlertCandleTime: Long = Long.MIN_VALUE,
    ): List<AlertSignal> {
        if (!config.anyEnabled) return emptyList()
        val minBars = maxOf(config.emaSlow + 2, 35, config.breakoutLookback + 2)
        if (candles.size < minBars) return emptyList()

        val last = candles.last()
        if (last.time <= lastAlertCandleTime) return emptyList()

        val signals = ArrayList<AlertSignal>(4)

        if (config.emaCross) {
            emaCrossSignal(candles, config, last)?.let(signals::add)
        }
        if (config.rsiExtremes) {
            rsiSignal(candles, config, last)?.let(signals::add)
        }
        if (config.stochasticCross) {
            stochasticSignal(candles, last)?.let(signals::add)
        }
        if (config.rangeBreakout) {
            breakoutSignal(candles, config, last)?.let(signals::add)
        }
        return signals
    }

    private fun emaCrossSignal(
        candles: List<Candle>,
        config: StrategyConfig,
        last: Candle,
    ): AlertSignal? {
        val window = candles.takeLast(config.emaSlow * 6 + 10)
        val fast = Indicators.ema(window.map { it.close }.toDoubleArray(), config.emaFast)
        val slow = Indicators.ema(window.map { it.close }.toDoubleArray(), config.emaSlow)
        val i = window.lastIndex
        if (i < 1) return null
        val fastNow = fast[i]
        val slowNow = slow[i]
        val fastPrev = fast[i - 1]
        val slowPrev = slow[i - 1]
        if (fastNow.isNaN() || slowNow.isNaN() || fastPrev.isNaN() || slowPrev.isNaN()) return null

        val crossedUp = fastPrev <= slowPrev && fastNow > slowNow
        val crossedDown = fastPrev >= slowPrev && fastNow < slowNow
        if (!crossedUp && !crossedDown) return null
        return AlertSignal.EmaCross(
            candleTime = last.time,
            fast = config.emaFast,
            slow = config.emaSlow,
            bullish = crossedUp,
            price = last.close,
        )
    }

    private fun rsiSignal(
        candles: List<Candle>,
        config: StrategyConfig,
        last: Candle,
    ): AlertSignal? {
        val series = Indicators.rsi(candles, 14)
        val i = series.lastIndex
        if (i < 1) return null
        val now = series[i]
        val prev = series[i - 1]
        if (now.isNaN() || prev.isNaN()) return null

        val crossedAbove = prev < config.rsiUpper && now >= config.rsiUpper
        val crossedBelow = prev > config.rsiLower && now <= config.rsiLower
        if (!crossedAbove && !crossedBelow) return null
        return AlertSignal.RsiExtreme(candleTime = last.time, value = now, overbought = crossedAbove)
    }

    private fun stochasticSignal(candles: List<Candle>, last: Candle): AlertSignal? {
        val (kArr, dArr) = Indicators.stochastic(candles, 14, 3, 3)
        val i = kArr.lastIndex
        if (i < 1) return null
        val k = kArr[i]
        val d = dArr[i]
        val kp = kArr[i - 1]
        val dp = dArr[i - 1]
        if (k.isNaN() || d.isNaN() || kp.isNaN() || dp.isNaN()) return null

        val crossedUp = kp <= dp && k > d
        val crossedDown = kp >= dp && k < d
        if (!crossedUp && !crossedDown) return null
        return AlertSignal.StochCross(candleTime = last.time, k = k, d = d, bullish = crossedUp)
    }

    private fun breakoutSignal(
        candles: List<Candle>,
        config: StrategyConfig,
        last: Candle,
    ): AlertSignal? {
        val lookback = config.breakoutLookback
        if (candles.size < lookback + 2) return null
        val history = candles.subList(candles.size - lookback - 1, candles.size - 1)
        val priorHigh = history.maxOf { it.high }
        val priorLow = history.minOf { it.low }
        return when {
            last.close > priorHigh -> AlertSignal.RangeBreakout(last.time, lookback, true, last.close)
            last.close < priorLow -> AlertSignal.RangeBreakout(last.time, lookback, false, last.close)
            else -> null
        }
    }
}
