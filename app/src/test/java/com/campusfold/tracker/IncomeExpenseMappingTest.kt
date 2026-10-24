package com.campusfold.tracker

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.util.CurrencyUtils
import org.junit.Assert.*
import org.junit.Test

class IncomeExpenseMappingTest {

    @Test
    fun testReimbursementReducesExpenseShare() {
        val originalExpense = TransactionEntity(
            id = 10,
            amount = 1200.0,
            myActualShare = 1200.0,
            type = TransactionType.EXPENSE,
            paymentMode = "Axis Bank XX2003",
            rawMerchant = "Bistro Dinner",
            friendlyVendorName = "Bistro Dinner",
            timestamp = System.currentTimeMillis()
        )

        val friendPayment = TransactionEntity(
            id = 11,
            amount = 400.0,
            myActualShare = 400.0,
            type = TransactionType.INCOME,
            paymentMode = "UPI",
            rawMerchant = "Pratosh via UPI",
            friendlyVendorName = "Pratosh via UPI",
            timestamp = System.currentTimeMillis()
        )

        // Simulate link calculation
        val offsetAmount = friendPayment.amount
        val updatedExpenseShare = (originalExpense.myActualShare - offsetAmount).coerceAtLeast(0.0)
        val updatedExpense = originalExpense.copy(myActualSharePaise = CurrencyUtils.rupeesToPaise(updatedExpenseShare))

        val updatedIncome = friendPayment.copy(
            type = TransactionType.SPLIT_SETTLEMENT,
            friendlyVendorName = "Reimbursement for ${originalExpense.friendlyVendorName}"
        )

        assertEquals(800.0, updatedExpense.myActualShare, 0.001)
        assertEquals(TransactionType.SPLIT_SETTLEMENT, updatedIncome.type)
        assertEquals("Reimbursement for Bistro Dinner", updatedIncome.friendlyVendorName)
    }

    @Test
    fun testReimbursementExceedingShareClampsToZero() {
        val originalExpense = TransactionEntity(
            id = 20,
            amount = 300.0,
            myActualShare = 200.0,
            type = TransactionType.EXPENSE,
            paymentMode = "Axis Bank XX2003",
            rawMerchant = "Coffee",
            friendlyVendorName = "Coffee",
            timestamp = System.currentTimeMillis()
        )

        val offsetAmount = 250.0
        val updatedExpenseShare = (originalExpense.myActualShare - offsetAmount).coerceAtLeast(0.0)

        assertEquals(0.0, updatedExpenseShare, 0.001)
    }

    @Test
    fun testUnlinkRestoresExpenseShareAndIncomeType() {
        val originalExpense = TransactionEntity(
            id = 30,
            amountPaise = 150000L,
            myActualSharePaise = 150000L,
            type = TransactionType.EXPENSE,
            paymentMode = "Axis Bank XX2003",
            rawMerchant = "Dinner",
            friendlyVendorName = "Dinner",
            timestamp = 1000L
        )

        val incomeTx = TransactionEntity(
            id = 31,
            amountPaise = 50000L,
            myActualSharePaise = 50000L,
            type = TransactionType.INCOME,
            paymentMode = "UPI",
            rawMerchant = "Alex Morgan",
            friendlyVendorName = "Alex Morgan",
            timestamp = 2000L
        )

        // Perform link
        val offsetPaise = 50000L
        val linkedExpense = originalExpense.copy(
            myActualSharePaise = originalExpense.myActualSharePaise - offsetPaise,
            description = "[LINKED_INCOME:${incomeTx.id}|OFFSET:$offsetPaise]"
        )
        val linkedIncome = incomeTx.copy(
            type = TransactionType.SPLIT_SETTLEMENT,
            friendlyVendorName = "Reimbursement for ${originalExpense.friendlyVendorName}",
            description = "[LINKED_TO_EXPENSE:${originalExpense.id}|OFFSET:$offsetPaise|PREV_SHARE:${originalExpense.myActualSharePaise}]"
        )

        assertEquals(100000L, linkedExpense.myActualSharePaise)
        assertEquals(TransactionType.SPLIT_SETTLEMENT, linkedIncome.type)

        // Perform unlink logic
        val incomeLinkMatch = Regex("""\[LINKED_TO_EXPENSE:(\d+)\|OFFSET:(\d+)(?:\|PREV_SHARE:(\d+))?\]""").find(linkedIncome.description!!)
        assertNotNull(incomeLinkMatch)
        val prevShare = incomeLinkMatch!!.groupValues[3].toLong()
        val restoredExpense = linkedExpense.copy(
            myActualSharePaise = prevShare,
            description = null
        )
        val restoredIncome = linkedIncome.copy(
            type = TransactionType.INCOME,
            friendlyVendorName = linkedIncome.rawMerchant,
            description = null
        )

        assertEquals("Expense share must be restored to original share", 150000L, restoredExpense.myActualSharePaise)
        assertEquals("Income type must be restored to INCOME", TransactionType.INCOME, restoredIncome.type)
        assertEquals("Vendor name must be restored to original raw merchant", "Alex Morgan", restoredIncome.friendlyVendorName)
    }
}
