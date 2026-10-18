package com.apex.terminal.util

import java.util.Locale
import kotlin.math.abs

/**
 * Presentation-only number formatting. Prices are printed with an adaptive
 * precision so a `0.00000123` SHIB quote is still readable next to a `68,412.50`
 * BTC quote.
 */
object Formatters {

    fun price(value: Double?): String {
        if (value == null || value.isNaN() || value.isInfinite()) return "--"
        val magnitude = abs(value)
        val decimals = when {
            magnitude == 0.0 -> 2
            magnitude >= 10_000 -> 2
            magnitude >= 100 -> 3
            magnitude >= 1 -> 4
            magnitude >= 0.01 -> 5
            magnitude >= 0.0001 -> 6
            else -> 8
        }
        return String.format(Locale.US, "%,.${decimals}f", value)
    }

    fun signed(value: Double?, decimals: Int = 2, suffix: String = ""): String {
        if (value == null || value.isNaN()) return "--"
        return String.format(Locale.US, "%+,.${decimals}f%s", value, suffix)
    }

    fun percent(value: Double?): String {
        if (value == null || value.isNaN()) return "--"
        return String.format(Locale.US, "%+.2f%%", value)
    }

    fun compact(value: Double?): String {
        if (value == null || value.isNaN()) return "--"
        val abs = abs(value)
        return when {
            abs >= 1_000_000_000_000 -> String.format(Locale.US, "%.2fT", value / 1_000_000_000_000)
            abs >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000)
            abs >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
            abs >= 1_000 -> String.format(Locale.US, "%.2fK", value / 1_000)
            else -> String.format(Locale.US, "%.2f", value)
        }
    }

    fun clock(epochMillis: Long): String {
        val fmt = java.text.SimpleDateFormat("HH:mm", Locale.US)
        return fmt.format(java.util.Date(epochMillis))
    }

    fun dateTime(epochMillis: Long): String {
        val fmt = java.text.SimpleDateFormat("dd MMM HH:mm", Locale.US)
        return fmt.format(java.util.Date(epochMillis))
    }
}
