package com.campusfold.tracker

import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitDirection
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.model.RepaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendPaidAndSplitsTest {

    private fun simulateRepayFriendDebt(
        unsettledDebtItems: List<SplitItemEntity>,
        amountToPayPaise: Long,
        friendName: String = "Rahul"
    ): Pair<RepaymentResult, List<SplitItemEntity>> {
        val totalOwedBeforePaise = unsettledDebtItems.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }
        var remainingPayment = amountToPayPaise
        val updatedItems = mutableListOf<SplitItemEntity>()

        for (item in unsettledDebtItems) {
            if (remainingPayment <= 0L) {
                updatedItems.add(item)
                continue
            }

            val itemRemaining = item.amountOwedPaise - item.amountPaidSoFarPaise
            if (remainingPayment < itemRemaining) {
                updatedItems.add(item.copy(
                    amountPaidSoFarPaise = item.amountPaidSoFarPaise + remainingPayment,
                    isSettled = false
                ))
                remainingPayment = 0L
            } else {
                updatedItems.add(item.copy(
                    amountPaidSoFarPaise = item.amountOwedPaise,
                    isSettled = true
                ))
                remainingPayment -= itemRemaining
            }
        }

        val settledDebtPaise = minOf(amountToPayPaise, totalOwedBeforePaise)
        val excessExpensePaise = maxOf(0L, remainingPayment)

        val message = if (excessExpensePaise > 0L) {
            "Settled ₹${settledDebtPaise / 100.0} debt to $friendName. Excess ₹${excessExpensePaise / 100.0} recorded as normal expense."
        } else {
            "Settled ₹${settledDebtPaise / 100.0} debt to $friendName."
        }

        return Pair(
            RepaymentResult(
                friendName = friendName,
                debtSettledPaise = settledDebtPaise,
                remainingDebtPaise = maxOf(0L, totalOwedBeforePaise - amountToPayPaise),
                excessAsExpensePaise = excessExpensePaise,
                updatedSplitCount = updatedItems.size
            ),
            updatedItems
        )
    }

    @Test
    fun testFriendPaidExpenseStructure() {
        val friend = PersonEntity(id = 10L, name = "Aman")
        val myShare = 450_00L // Rs 450

        // TransactionEntity for a friend-paid expense
        val txn = TransactionEntity(
            id = 1L,
            amountPaise = myShare,
            myActualSharePaise = myShare,
            type = TransactionType.EXPENSE,
            rawMerchant = "Zomato",
            friendlyVendorName = "Zomato",
            paymentMode = "Paid by Aman",
            accountId = null, // No account debited!
            paidByPersonId = friend.id,
            timestamp = System.currentTimeMillis()
        )

        val debtItem = SplitItemEntity(
            id = 101L,
            transactionId = txn.id,
            personId = friend.id,
            amountOwedPaise = myShare,
            amountPaidSoFarPaise = 0L,
            isSettled = false,
            direction = SplitDirection.OWED_BY_ME
        )

        assertEquals(null, txn.accountId)
        assertEquals(friend.id, txn.paidByPersonId)
        assertEquals(myShare, txn.amountPaise)
        assertEquals(myShare, txn.myActualSharePaise)
        assertEquals(SplitDirection.OWED_BY_ME, debtItem.direction)
        assertEquals(false, debtItem.isSettled)
    }

    @Test
    fun testFifoRepaymentExactSettlement() {
        // Two debts to Rahul: Rs 300 (oldest) and Rs 500 (newer)
        val item1 = SplitItemEntity(
            id = 1L,
            transactionId = 10L,
            personId = 5L,
            amountOwedPaise = 300_00L,
            amountPaidSoFarPaise = 0L,
            isSettled = false,
            direction = SplitDirection.OWED_BY_ME
        )
        val item2 = SplitItemEntity(
            id = 2L,
            transactionId = 11L,
            personId = 5L,
            amountOwedPaise = 500_00L,
            amountPaidSoFarPaise = 0L,
            isSettled = false,
            direction = SplitDirection.OWED_BY_ME
        )

        // Repay Rs 400
        val (result, updated) = simulateRepayFriendDebt(listOf(item1, item2), 400_00L, "Rahul")

        assertEquals(400_00L, result.debtSettledPaise)
        assertEquals(0L, result.excessAsExpensePaise)

        // Item 1 should be fully settled (Rs 300)
        assertTrue(updated[0].isSettled)
        assertEquals(300_00L, updated[0].amountPaidSoFarPaise)

        // Item 2 should have Rs 100 paid so far, remaining Rs 400 unsettled
        assertEquals(false, updated[1].isSettled)
        assertEquals(100_00L, updated[1].amountPaidSoFarPaise)
    }

    @Test
    fun testFifoRepaymentWithExcessRecordedAsExpense() {
        // Total debt Rs 250
        val item = SplitItemEntity(
            id = 1L,
            transactionId = 10L,
            personId = 5L,
            amountOwedPaise = 250_00L,
            amountPaidSoFarPaise = 0L,
            isSettled = false,
            direction = SplitDirection.OWED_BY_ME
        )

        // Repay Rs 300 (Rs 50 overpayment)
        val (result, updated) = simulateRepayFriendDebt(listOf(item), 300_00L, "Rahul")

        assertEquals(250_00L, result.debtSettledPaise)
        assertEquals(50_00L, result.excessAsExpensePaise)
        assertTrue(updated[0].isSettled)
        assertTrue(result.message.contains("Excess ₹50.0 recorded as normal expense"))
    }
}
