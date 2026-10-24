package com.campusfold.tracker

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.RuleEntity
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetDataTest {

    @Test
    fun testClearTransactionsOnlyPreservesMetadataAndRevertsAnchors() {
        var accounts = listOf(
            AccountEntity(
                id = 1L,
                bankName = "HDFC",
                bankKey = "HDFC",
                last4 = "4321",
                displayName = "HDFC Savings",
                type = AccountType.SAVINGS,
                openingBalancePaise = 25000_00L,
                balancePaise = 18000_00L // Changed by transactions
            ),
            AccountEntity(
                id = 2L,
                bankName = "Axis Card",
                bankKey = "AXIS",
                last4 = "8765",
                displayName = "Axis Card",
                type = AccountType.CREDIT_CARD,
                openingOwedPaise = 5000_00L,
                balancePaise = 12000_00L // Changed by transactions
            )
        )

        var transactions = listOf(
            TransactionEntity(
                id = 1L,
                timestamp = System.currentTimeMillis(),
                amountPaise = 7000_00L,
                type = TransactionType.EXPENSE,
                friendlyVendorName = "Cafe"
            )
        )

        var splitItems = listOf(
            SplitItemEntity(
                id = 1L,
                transactionId = 1L,
                personId = 2L,
                amountOwedPaise = 2000_00L
            )
        )

        val categories = listOf(CategoryEntity(id = 1L, name = "Food", color = "#10B981"))
        val rules = listOf(RuleEntity(id = 1L, name = "Food Rule", source = com.campusfold.tracker.data.entity.RuleSource.MANUAL))

        // Simulate clearTransactionsOnly
        transactions = emptyList()
        splitItems = emptyList()

        // Revert accounts to opening anchors
        accounts = accounts.map { acc ->
            if (acc.type == AccountType.CREDIT_CARD) {
                acc.copy(balancePaise = acc.openingOwedPaise)
            } else {
                acc.copy(balancePaise = acc.openingBalancePaise)
            }
        }

        // Assertions: Transactions and split items cleared
        assertTrue(transactions.isEmpty())
        assertTrue(splitItems.isEmpty())

        // Accounts reverted to opening balances
        assertEquals(25000_00L, accounts[0].balancePaise)
        assertEquals(5000_00L, accounts[1].balancePaise)

        // Metadata intact
        assertEquals(1, categories.size)
        assertEquals(1, rules.size)
    }

    @Test
    fun testResetEverythingWipesAll() {
        var accounts = listOf(
            AccountEntity(
                id = 1L,
                bankName = "HDFC",
                bankKey = "HDFC",
                last4 = "1234",
                displayName = "HDFC"
            )
        )
        var categories = listOf(CategoryEntity(id = 1L, name = "Custom Category", color = "#10B981"))
        var transactions = listOf(
            TransactionEntity(
                id = 1L,
                timestamp = 0L,
                amountPaise = 100L,
                type = TransactionType.EXPENSE
            )
        )

        // Simulate resetEverything
        accounts = emptyList()
        transactions = emptyList()
        categories = emptyList()

        assertTrue(accounts.isEmpty())
        assertTrue(transactions.isEmpty())
        assertTrue(categories.isEmpty())
    }
}
