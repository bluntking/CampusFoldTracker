package com.campusfold.tracker

import com.campusfold.tracker.util.BudgetAlertEvaluator
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class BudgetAlertEvaluatorTest {

    @Test
    fun testThresholdCalculation() {
        val budget = 2000.0

        // Under 50%
        assertEquals(0, BudgetAlertEvaluator.calculateThreshold(0.0, budget))
        assertEquals(0, BudgetAlertEvaluator.calculateThreshold(500.0, budget))
        assertEquals(0, BudgetAlertEvaluator.calculateThreshold(999.0, budget))

        // 50% threshold
        assertEquals(50, BudgetAlertEvaluator.calculateThreshold(1000.0, budget))
        assertEquals(50, BudgetAlertEvaluator.calculateThreshold(1399.0, budget))

        // 70% threshold
        assertEquals(70, BudgetAlertEvaluator.calculateThreshold(1400.0, budget))
        assertEquals(70, BudgetAlertEvaluator.calculateThreshold(1799.0, budget))

        // 90% threshold
        assertEquals(90, BudgetAlertEvaluator.calculateThreshold(1800.0, budget))
        assertEquals(90, BudgetAlertEvaluator.calculateThreshold(1999.0, budget))

        // 100% threshold
        assertEquals(100, BudgetAlertEvaluator.calculateThreshold(2000.0, budget))
        assertEquals(100, BudgetAlertEvaluator.calculateThreshold(2500.0, budget))
    }

    @Test
    fun testNoSpamSameThreshold() {
        val budget = 3000.0
        val spend1 = 1600.0 // 53.3% -> crossed 50
        val threshold1 = BudgetAlertEvaluator.calculateThreshold(spend1, budget)
        assertEquals(50, threshold1)

        var lastAlerted = threshold1 // recorded in DataStore

        // Another small expense of ₹100 occurs -> total 1700 (56.6%) -> still 50
        val spend2 = 1700.0
        val threshold2 = BudgetAlertEvaluator.calculateThreshold(spend2, budget)
        assertEquals(50, threshold2)

        // Threshold did not increase, so notification will not re-trigger
        val shouldTriggerAlert = threshold2 > lastAlerted
        assertFalse(shouldTriggerAlert)
    }

    @Test
    fun testAlertEscalationOnHigherThreshold() {
        val budget = 3000.0
        var lastAlerted = 50

        // Spend jumps to 2200 (73.3%) -> crosses 70%
        val newSpend = 2200.0
        val newThreshold = BudgetAlertEvaluator.calculateThreshold(newSpend, budget)
        assertEquals(70, newThreshold)

        val shouldTriggerAlert = newThreshold > lastAlerted
        assertTrue(shouldTriggerAlert)
    }

    @Test
    fun testMonthTimeBoundsCalculation() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 22, 14, 30, 0)
        }

        val (start, end) = BudgetAlertEvaluator.getMonthTimeBounds(calendar)

        val calStart = Calendar.getInstance().apply { timeInMillis = start }
        val calEnd = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calStart.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calStart.get(Calendar.MINUTE))
        assertEquals(0, calStart.get(Calendar.SECOND))

        assertEquals(31, calEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calEnd.get(Calendar.MINUTE))
        assertEquals(59, calEnd.get(Calendar.SECOND))
    }

    @Test
    fun testBurnRatePacingFormula() {
        val dayOfMonth = 22
        val totalDaysInMonth = 31
        val daysElapsedPercent = (dayOfMonth.toDouble() / totalDaysInMonth.toDouble()) * 100.0 // ~70.96%

        val budget = 2000.0
        val spent = 1700.0 // 85% spent
        val spentPercent = (spent / budget) * 100.0

        val pacingAhead = spentPercent - daysElapsedPercent
        assertTrue(pacingAhead > 14.0) // ~14.03% ahead of pacing
    }
}
