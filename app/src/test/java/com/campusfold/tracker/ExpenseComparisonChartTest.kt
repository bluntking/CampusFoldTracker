package com.campusfold.tracker

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.ui.screens.computeMonthOverMonthData
import com.campusfold.tracker.ui.screens.computeWeekOverWeekData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExpenseComparisonChartTest {

    @Test
    fun testEmptyTransactionsReturnsValidWeekData() {
        val points = computeWeekOverWeekData(emptyList())
        assertEquals(7, points.size)
        assertEquals("Mon", points[0].label)
        assertEquals("Sun", points[6].label)
        points.forEach { pt ->
            assertEquals(0.0, pt.currentSpend, 0.001)
            assertEquals(0.0, pt.previousSpend, 0.001)
            assertEquals(0.0, pt.currentCumulative, 0.001)
            assertEquals(0.0, pt.previousCumulative, 0.001)
        }
    }

    @Test
    fun testEmptyTransactionsReturnsValidMonthData() {
        val points = computeMonthOverMonthData(emptyList())
        assertTrue("Month data points should have at least 28 days", points.size >= 28)
        assertEquals("1", points[0].label)
        points.forEach { pt ->
            assertEquals(0.0, pt.currentSpend, 0.001)
            assertEquals(0.0, pt.previousSpend, 0.001)
            assertEquals(0.0, pt.currentCumulative, 0.001)
            assertEquals(0.0, pt.previousCumulative, 0.001)
        }
    }

    @Test
    fun testPreviousWeekExpenseAggregation() {
        val now = Calendar.getInstance()
        val prevWeekCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now.timeInMillis) {
                add(Calendar.WEEK_OF_YEAR, -1)
            }
            add(Calendar.WEEK_OF_YEAR, -1) // Go to previous week Monday
        }

        val testTxn = TransactionEntity(
            id = 1,
            amount = 500.0,
            myActualShare = 500.0,
            type = TransactionType.EXPENSE,
            paymentMode = "Axis Bank XX2003",
            rawMerchant = "Air India",
            friendlyVendorName = "Air India",
            timestamp = prevWeekCal.timeInMillis
        )

        val points = computeWeekOverWeekData(listOf(testTxn))
        assertEquals(7, points.size)
        // Previous week Monday (index 0) should have 500.0
        assertEquals(500.0, points[0].previousSpend, 0.001)
        assertEquals(500.0, points[0].previousCumulative, 0.001)
        // Cumulative should carry 500.0 across all subsequent days
        assertEquals(500.0, points[6].previousCumulative, 0.001)
    }

    @Test
    fun testIncomeTransactionsAreIgnoredInExpenseComparison() {
        val now = Calendar.getInstance()
        val incomeTxn = TransactionEntity(
            id = 2,
            amount = 10000.0,
            myActualShare = 10000.0,
            type = TransactionType.INCOME,
            paymentMode = "Salary",
            rawMerchant = "Employer",
            friendlyVendorName = "Salary",
            timestamp = now.timeInMillis
        )

        val points = computeWeekOverWeekData(listOf(incomeTxn))
        points.forEach { pt ->
            assertEquals(0.0, pt.currentSpend, 0.001)
            assertEquals(0.0, pt.previousSpend, 0.001)
        }
    }
}
