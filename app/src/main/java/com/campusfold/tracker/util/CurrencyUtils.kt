package com.campusfold.tracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * Currency utilities for INR (Indian Rupee).
 *
 * Money is strictly stored and calculated as Long paise (1 INR = 100 paise).
 * Formats values using Indian digit grouping (e.g. 52153708 paise -> 5,21,537.08).
 */
object CurrencyUtils {

    /**
     * Converts a Double rupee amount to Long paise with half-up rounding.
     */
    fun rupeesToPaise(rupees: Double): Long {
        return BigDecimal.valueOf(rupees)
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * Converts Long paise to Double rupees.
     */
    fun paiseToRupees(paise: Long): Double {
        return paise / 100.0
    }

    /**
     * Formats Long paise as an INR string with Indian digit grouping (e.g. 5,21,537.08).
     *
     * @param paise The amount in integer paise.
     * @param includeSymbol If true, prefixes the currency symbol (₹).
     */
    fun formatInr(paise: Long, includeSymbol: Boolean = false): String {
        val isNegative = paise < 0
        val absPaise = abs(paise)
        val rupees = absPaise / 100
        val remainderPaise = absPaise % 100

        val decimalPart = String.format(Locale.US, "%02d", remainderPaise)
        val integerPart = formatIndianInteger(rupees)

        val formattedAmount = "$integerPart.$decimalPart"
        val prefix = if (isNegative) "-" else ""
        val symbol = if (includeSymbol) "₹" else ""

        return "$prefix$symbol$formattedAmount"
    }

    /**
     * Overload for Double rupee values.
     */
    fun formatInr(rupees: Double, includeSymbol: Boolean = false): String {
        return formatInr(rupeesToPaise(rupees), includeSymbol)
    }

    /**
     * Formats the rupee integer part with Indian digit grouping:
     * Rightmost 3 digits grouped, then groups of 2 digits to the left.
     */
    private fun formatIndianInteger(value: Long): String {
        if (value < 1000) {
            return value.toString()
        }

        val lastThree = String.format(Locale.US, "%03d", value % 1000)
        var remaining = value / 1000

        val groups = mutableListOf<String>()
        while (remaining > 0) {
            if (remaining >= 100) {
                groups.add(String.format(Locale.US, "%02d", remaining % 100))
                remaining /= 100
            } else {
                groups.add(remaining.toString())
                remaining = 0
            }
        }

        groups.reverse()
        val higherGroups = groups.joinToString(",")
        return "$higherGroups,$lastThree"
    }
}
