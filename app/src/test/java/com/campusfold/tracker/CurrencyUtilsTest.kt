package com.campusfold.tracker

import com.campusfold.tracker.util.CurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun testRupeesToPaiseConversion() {
        assertEquals(10000L, CurrencyUtils.rupeesToPaise(100.0))
        assertEquals(0L, CurrencyUtils.rupeesToPaise(0.0))
        assertEquals(50L, CurrencyUtils.rupeesToPaise(0.50))
        assertEquals(52153708L, CurrencyUtils.rupeesToPaise(521537.08))
    }

    @Test
    fun testRupeesToPaiseHalfUpRounding() {
        // 100.005 rounds up to 10001 paise (round half up)
        assertEquals(10001L, CurrencyUtils.rupeesToPaise(100.005))
        // 100.004 rounds down to 10000 paise
        assertEquals(10000L, CurrencyUtils.rupeesToPaise(100.004))
        // 0.006 rounds to 1 paise
        assertEquals(1L, CurrencyUtils.rupeesToPaise(0.006))
        // 0.004 rounds to 0 paise
        assertEquals(0L, CurrencyUtils.rupeesToPaise(0.004))
    }

    @Test
    fun testPaiseToRupeesConversion() {
        assertEquals(100.0, CurrencyUtils.paiseToRupees(10000L), 0.0001)
        assertEquals(521537.08, CurrencyUtils.paiseToRupees(52153708L), 0.0001)
        assertEquals(0.0, CurrencyUtils.paiseToRupees(0L), 0.0001)
        assertEquals(0.5, CurrencyUtils.paiseToRupees(50L), 0.0001)
    }

    @Test
    fun testInrFormattingIndianDigitGrouping() {
        // Spec required example: 5,21,537.08
        assertEquals("5,21,537.08", CurrencyUtils.formatInr(52153708L))
        assertEquals("₹5,21,537.08", CurrencyUtils.formatInr(52153708L, includeSymbol = true))

        // Zero
        assertEquals("0.00", CurrencyUtils.formatInr(0L))
        assertEquals("₹0.00", CurrencyUtils.formatInr(0L, includeSymbol = true))

        // Sub-rupee amounts
        assertEquals("0.50", CurrencyUtils.formatInr(50L))
        assertEquals("0.08", CurrencyUtils.formatInr(8L))

        // Small amounts (< 1,000)
        assertEquals("1.00", CurrencyUtils.formatInr(100L))
        assertEquals("999.99", CurrencyUtils.formatInr(99999L))

        // Thousands (1,000.00)
        assertEquals("1,000.00", CurrencyUtils.formatInr(100000L))
        assertEquals("12,345.67", CurrencyUtils.formatInr(1234567L))

        // Lakhs (1,00,000.00)
        assertEquals("1,00,000.00", CurrencyUtils.formatInr(10000000L))
        assertEquals("10,00,000.00", CurrencyUtils.formatInr(100000000L))

        // Crores (1,00,00,000.00)
        assertEquals("1,00,00,000.00", CurrencyUtils.formatInr(1000000000L))

        // Negative values
        assertEquals("-5,21,537.08", CurrencyUtils.formatInr(-52153708L))
        assertEquals("-₹5,21,537.08", CurrencyUtils.formatInr(-52153708L, includeSymbol = true))
    }

    @Test
    fun testFormatInrDoubleOverload() {
        assertEquals("5,21,537.08", CurrencyUtils.formatInr(521537.08))
        assertEquals("₹5,21,537.08", CurrencyUtils.formatInr(521537.08, includeSymbol = true))
    }
}
