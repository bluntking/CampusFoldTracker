package com.campusfold.tracker

import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.util.CurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration3To4Test {

    @Test
    fun testMigrationVersion() {
        val migration = AppDatabase.MIGRATION_3_4
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)
    }

    @Test
    fun testAccountTypeMappingLogic() {
        // Test account type mapping from legacy string to new AccountType
        fun mapType(legacy: String): String = when (legacy) {
            "CREDIT_CARD" -> "CREDIT_CARD"
            "SAVINGS" -> "SAVINGS"
            "WALLET" -> "WALLET"
            else -> "UNASSIGNED"
        }

        assertEquals("CREDIT_CARD", mapType("CREDIT_CARD"))
        assertEquals("SAVINGS", mapType("SAVINGS"))
        assertEquals("WALLET", mapType("WALLET"))
        assertEquals("UNASSIGNED", mapType("OTHER"))
        assertEquals("UNASSIGNED", mapType("UNKNOWN"))
    }

    @Test
    fun testBankKeyNormalizationLogic() {
        fun normalizeBankKey(bankName: String): String = bankName.trim().uppercase()

        assertEquals("PNB", normalizeBankKey("PNB"))
        assertEquals("AXIS BANK", normalizeBankKey("Axis Bank"))
        assertEquals("FEDERAL BANK", normalizeBankKey(" Federal Bank "))
    }

    @Test
    fun testAccountBalancesRoutingLogic() {
        // If CREDIT_CARD, creditLimitOrBalance goes to creditLimitPaise; else balancePaise
        fun routeBalances(type: String, amount: Double): Pair<Long, Long> {
            val paise = CurrencyUtils.rupeesToPaise(amount)
            return if (type == "CREDIT_CARD") {
                Pair(0L, paise) // balancePaise, creditLimitPaise
            } else {
                Pair(paise, 0L)
            }
        }

        val cc = routeBalances("CREDIT_CARD", 59396.93)
        assertEquals(0L, cc.first)
        assertEquals(5939693L, cc.second)

        val savings = routeBalances("SAVINGS", 15420.50)
        assertEquals(1542050L, savings.first)
        assertEquals(0L, savings.second)
    }

    @Test
    fun testTransactionToAccountLinkingLogic() {
        data class AccountMock(val id: Long, val displayName: String, val last4: String)

        val accounts = listOf(
            AccountMock(1, "PNB Credit Card 8598", "8598"),
            AccountMock(2, "Axis Bank XX2003", "2003"),
            AccountMock(3, "Federal Jupiter 2548", "2548")
        )

        fun linkTransactionToAccount(paymentMode: String): Long? {
            return accounts.firstOrNull { acc ->
                (acc.last4.isNotEmpty() && paymentMode.contains(acc.last4)) ||
                        paymentMode.lowercase().contains(acc.displayName.lowercase())
            }?.id
        }

        // Exact last4 match
        assertEquals(1L, linkTransactionToAccount("PNB CC 8598"))
        assertEquals(2L, linkTransactionToAccount("Axis Bank XX2003"))
        assertEquals(3L, linkTransactionToAccount("Jupiter 2548"))

        // Unmatched falls back to null
        assertEquals(null, linkTransactionToAccount("Cash/Manual"))
        assertEquals(null, linkTransactionToAccount("Unknown UPI"))
    }

    @Test
    fun testVendorMappingKeyNormalization() {
        fun normalizeVendorKey(rawSmsKeyword: String): String = rawSmsKeyword.trim().lowercase()

        assertEquals("sekar store", normalizeVendorKey("Sekar store"))
        assertEquals("karthik", normalizeVendorKey(" KARTHIK "))
        assertEquals("dd store", normalizeVendorKey("DD STORE"))
    }

    @Test
    fun testCategoryBudgetConversionLogic() {
        fun convertBudget(monthlyBudget: Double): Long? {
            return if (monthlyBudget > 0.0) CurrencyUtils.rupeesToPaise(monthlyBudget) else null
        }

        assertEquals(500000L, convertBudget(5000.0)) // ₹5,000.00 -> 500,000 paise
        assertEquals(null, convertBudget(0.0))
        assertEquals(null, convertBudget(-10.0))
    }
}
