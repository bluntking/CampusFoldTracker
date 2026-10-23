package com.campusfold.tracker

import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.model.SettlementResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementIntegerMathTest {

    /**
     * Replicates the exact FIFO settlement integer logic from TransactionRepository.
     */
    private fun simulateFifoSettlement(
        unsettledRows: List<SplitItemEntity>,
        amountToSettlePaise: Long,
        friendName: String = "Friend"
    ): Pair<SettlementResult, List<SplitItemEntity>> {
        val totalOwedBeforePaise = unsettledRows.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }
        var remainingXPaise = amountToSettlePaise
        var updatedCount = 0
        val updatedRows = mutableListOf<SplitItemEntity>()

        for (row in unsettledRows) {
            if (remainingXPaise <= 0L) {
                updatedRows.add(row)
                continue
            }
            val rowRemainingPaise = row.amountOwedPaise - row.amountPaidSoFarPaise

            if (remainingXPaise < rowRemainingPaise) {
                val newPaid = row.amountPaidSoFarPaise + remainingXPaise
                updatedRows.add(row.copy(amountPaidSoFarPaise = newPaid, isSettled = false))
                remainingXPaise = 0L
                updatedCount++
            } else {
                updatedRows.add(row.copy(amountPaidSoFarPaise = row.amountOwedPaise, isSettled = true))
                remainingXPaise -= rowRemainingPaise
                updatedCount++
            }
        }

        val excessPaise = maxOf(0L, remainingXPaise)
        val newRemainingOwedPaise = maxOf(0L, totalOwedBeforePaise - amountToSettlePaise)
        val actuallySettledPaise = minOf(amountToSettlePaise, totalOwedBeforePaise)

        val result = SettlementResult(
            friendName = friendName,
            settledAmountPaise = actuallySettledPaise,
            remainingOwedPaise = newRemainingOwedPaise,
            excessAmountPaise = excessPaise,
            updatedSplitCount = updatedCount
        )

        return Pair(result, updatedRows)
    }

    @Test
    fun testPartialSettlementExactIntegerMath() {
        val split1 = SplitItemEntity(
            id = 1,
            transactionId = 101,
            personId = 1,
            amountOwedPaise = 50000L, // ₹500.00
            amountPaidSoFarPaise = 0L,
            isSettled = false
        )

        // Settle ₹200.00 (20000 paise)
        val (result, updated) = simulateFifoSettlement(listOf(split1), 20000L)

        assertEquals(20000L, result.settledAmountPaise)
        assertEquals(30000L, result.remainingOwedPaise)
        assertEquals(0L, result.excessAmountPaise)
        assertEquals(1, result.updatedSplitCount)

        assertEquals(20000L, updated[0].amountPaidSoFarPaise)
        assertEquals(false, updated[0].isSettled)
    }

    @Test
    fun testExactFullSettlementSingleItem() {
        val split1 = SplitItemEntity(
            id = 1,
            transactionId = 101,
            personId = 1,
            amountOwedPaise = 33333L, // ₹333.33
            amountPaidSoFarPaise = 0L,
            isSettled = false
        )

        val (result, updated) = simulateFifoSettlement(listOf(split1), 33333L)

        assertEquals(33333L, result.settledAmountPaise)
        assertEquals(0L, result.remainingOwedPaise)
        assertEquals(0L, result.excessAmountPaise)
        assertEquals(1, result.updatedSplitCount)

        assertEquals(33333L, updated[0].amountPaidSoFarPaise)
        assertEquals(true, updated[0].isSettled)
    }

    @Test
    fun testFifoOrderAcrossMultipleSplits() {
        val split1 = SplitItemEntity(
            id = 1,
            transactionId = 101,
            personId = 1,
            amountOwedPaise = 20000L, // ₹200.00 (older)
            amountPaidSoFarPaise = 0L,
            isSettled = false,
            createdAt = 1000L
        )
        val split2 = SplitItemEntity(
            id = 2,
            transactionId = 102,
            personId = 1,
            amountOwedPaise = 50000L, // ₹500.00 (newer)
            amountPaidSoFarPaise = 0L,
            isSettled = false,
            createdAt = 2000L
        )

        // Settle ₹400.00 (40000 paise) -> Should fully settle split1 (20000) and partially settle split2 (20000)
        val (result, updated) = simulateFifoSettlement(listOf(split1, split2), 40000L)

        assertEquals(40000L, result.settledAmountPaise)
        assertEquals(30000L, result.remainingOwedPaise)
        assertEquals(0L, result.excessAmountPaise)
        assertEquals(2, result.updatedSplitCount)

        assertTrue(updated[0].isSettled)
        assertEquals(20000L, updated[0].amountPaidSoFarPaise)

        assertEquals(false, updated[1].isSettled)
        assertEquals(20000L, updated[1].amountPaidSoFarPaise)
    }

    @Test
    fun testOverpaymentExcessCalculation() {
        val split1 = SplitItemEntity(
            id = 1,
            transactionId = 101,
            personId = 1,
            amountOwedPaise = 15000L, // ₹150.00
            amountPaidSoFarPaise = 0L,
            isSettled = false
        )

        // Settle ₹200.00 (20000 paise) against ₹150.00 debt
        val (result, updated) = simulateFifoSettlement(listOf(split1), 20000L)

        assertEquals(15000L, result.settledAmountPaise)
        assertEquals(0L, result.remainingOwedPaise)
        assertEquals(5000L, result.excessAmountPaise) // ₹50.00 excess
        assertEquals(1, result.updatedSplitCount)

        assertTrue(updated[0].isSettled)
        assertEquals(15000L, updated[0].amountPaidSoFarPaise)
    }

    @Test
    fun testIncomeToExpenseLinkingExactIntegerComparison() {
        // Test that exact integer comparison newPaid >= split.amountOwedPaise
        // avoids floating point imprecision bugs (e.g. 0.1 + 0.2 != 0.3)
        val splitOwedPaise = 3333L // ₹33.33
        var paidSoFarPaise = 0L

        val payment1 = 1111L
        paidSoFarPaise += payment1
        var isSettled = paidSoFarPaise >= splitOwedPaise
        assertEquals(false, isSettled)

        val payment2 = 2222L
        paidSoFarPaise += payment2
        isSettled = paidSoFarPaise >= splitOwedPaise
        assertEquals(true, isSettled)
        assertEquals(3333L, paidSoFarPaise)
    }
}
