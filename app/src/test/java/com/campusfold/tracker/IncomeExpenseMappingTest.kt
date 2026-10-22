package com.campusfold.tracker

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import org.junit.Assert.assertEquals
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
        val updatedExpense = originalExpense.copy(myActualShare = updatedExpenseShare)

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
}
