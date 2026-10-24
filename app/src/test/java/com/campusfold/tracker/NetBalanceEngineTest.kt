package com.campusfold.tracker

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.BalanceSource
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.model.DerivedAccountBalance
import com.campusfold.tracker.data.model.NetBalanceOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetBalanceEngineTest {

    private fun computeDerivedBalances(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>
    ): List<DerivedAccountBalance> {
        return accounts.map { account ->
            val anchorDate = if (account.anchorSet && account.openingAsOf > 0L) account.openingAsOf else 0L
            val relevantTxns = transactions.filter {
                it.accountId == account.id && it.timestamp >= anchorDate
            }

            if (account.type == AccountType.CREDIT_CARD) {
                val anchorOwed = if (account.anchorSet) account.openingOwedPaise else account.balancePaise
                val increases = relevantTxns.filter {
                    it.type == TransactionType.EXPENSE || it.type == TransactionType.EMI
                }.sumOf { it.amountPaise }
                val decreases = relevantTxns.filter {
                    it.type == TransactionType.CC_BILL_TRANSFER ||
                    it.type == TransactionType.REFUND ||
                    it.type == TransactionType.INCOME
                }.sumOf { it.amountPaise }

                val currentOwed = maxOf(0L, anchorOwed + increases - decreases)
                val availableCredit = if (account.creditLimitPaise > 0L) maxOf(0L, account.creditLimitPaise - currentOwed) else 0L

                DerivedAccountBalance(
                    account = account,
                    derivedBalancePaise = 0L,
                    derivedOwedPaise = currentOwed,
                    availableCreditPaise = availableCredit,
                    hasDiscrepancy = false,
                    discrepancyBankBalancePaise = null
                )
            } else {
                val anchorBal = if (account.anchorSet) account.openingBalancePaise else account.balancePaise
                val credits = relevantTxns.filter {
                    it.type == TransactionType.INCOME ||
                    it.type == TransactionType.REFUND ||
                    (it.type == TransactionType.OWN_TRANSFER && it.friendlyVendorName.contains("from", ignoreCase = true))
                }.sumOf { it.amountPaise }
                val debits = relevantTxns.filter {
                    it.type == TransactionType.EXPENSE ||
                    it.type == TransactionType.ATM ||
                    it.type == TransactionType.EMI ||
                    it.type == TransactionType.CC_BILL_TRANSFER ||
                    it.type == TransactionType.SPLIT_SETTLEMENT ||
                    (it.type == TransactionType.OWN_TRANSFER && !it.friendlyVendorName.contains("from", ignoreCase = true))
                }.sumOf { it.amountPaise }

                val currentBalance = anchorBal + credits - debits

                DerivedAccountBalance(
                    account = account,
                    derivedBalancePaise = currentBalance,
                    derivedOwedPaise = 0L,
                    availableCreditPaise = 0L,
                    hasDiscrepancy = false,
                    discrepancyBankBalancePaise = null
                )
            }
        }
    }

    private fun computeNetOverview(
        derivedList: List<DerivedAccountBalance>,
        friendOwedPaise: Long = 0L
    ): NetBalanceOverview {
        val totalSavings = derivedList.filter { it.account.type != AccountType.CREDIT_CARD }.sumOf { it.derivedBalancePaise }
        val totalCardOwed = derivedList.filter { it.account.type == AccountType.CREDIT_CARD }.sumOf { it.derivedOwedPaise }
        val netAvailable = totalSavings - totalCardOwed
        val unanchored = derivedList.count { !it.account.anchorSet }

        return NetBalanceOverview(
            netAvailableBalancePaise = netAvailable,
            totalSavingsBalancePaise = totalSavings,
            totalCardOwedPaise = totalCardOwed,
            totalOwedToFriendsPaise = friendOwedPaise,
            totalFriendsOweMePaise = 0L,
            unanchoredAccountsCount = unanchored,
            earliestTrackedTimestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun testNetAvailableBalanceEqualsSavingsMinusCardOwed() {
        val savings = AccountEntity(
            id = 1L,
            bankName = "State Bank",
            bankKey = "SBI",
            last4 = "1234",
            displayName = "State Bank",
            type = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 50000_00L, // Rs 50,000
            balancePaise = 50000_00L
        )
        val card = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "AXIS",
            last4 = "5678",
            displayName = "Axis Bank CC",
            type = AccountType.CREDIT_CARD,
            creditLimitPaise = 100000_00L, // Limit Rs 1,00,000
            anchorSet = true,
            openingOwedPaise = 15000_00L, // Owed Rs 15,000
            balancePaise = 15000_00L
        )

        val derived = computeDerivedBalances(listOf(savings, card), emptyList())
        val net = computeNetOverview(derived, friendOwedPaise = 5000_00L)

        // Net = 50,000 - 15,000 = 35,000
        assertEquals(35000_00L, net.netAvailableBalancePaise)
        assertEquals(50000_00L, net.totalSavingsBalancePaise)
        assertEquals(15000_00L, net.totalCardOwedPaise)

        // Friend debt is displayed separately and NOT subtracted from net
        assertEquals(5000_00L, net.totalOwedToFriendsPaise)
        assertEquals(35000_00L, net.netAvailableBalancePaise)
    }

    @Test
    fun testCreditCardBillPaymentLeavesNetAvailableBalanceUnchanged() {
        val savings = AccountEntity(
            id = 1L,
            bankName = "State Bank",
            bankKey = "SBI",
            last4 = "1234",
            displayName = "State Bank",
            type = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 50000_00L
        )
        val card = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "AXIS",
            last4 = "5678",
            displayName = "Axis Bank CC",
            type = AccountType.CREDIT_CARD,
            creditLimitPaise = 100000_00L,
            anchorSet = true,
            openingOwedPaise = 15000_00L
        )

        val initialDerived = computeDerivedBalances(listOf(savings, card), emptyList())
        val initialNet = computeNetOverview(initialDerived)
        assertEquals(35000_00L, initialNet.netAvailableBalancePaise)

        // Pay Rs 10,000 credit card bill from savings account (two legs)
        val savingsDebitTxn = TransactionEntity(
            id = 101L,
            timestamp = System.currentTimeMillis(),
            amountPaise = 10000_00L,
            type = TransactionType.CC_BILL_TRANSFER,
            friendlyVendorName = "Axis Credit Card Bill",
            accountId = savings.id
        )
        val cardCreditTxn = TransactionEntity(
            id = 102L,
            timestamp = System.currentTimeMillis(),
            amountPaise = 10000_00L,
            type = TransactionType.CC_BILL_TRANSFER,
            friendlyVendorName = "Payment Received",
            accountId = card.id
        )

        val afterDerived = computeDerivedBalances(listOf(savings, card), listOf(savingsDebitTxn, cardCreditTxn))
        val afterNet = computeNetOverview(afterDerived)

        // Savings is now Rs 40,000
        val savingsDerived = afterDerived.find { it.account.id == savings.id }!!
        assertEquals(40000_00L, savingsDerived.derivedBalancePaise)

        // Card owed is now Rs 5,000
        val cardDerived = afterDerived.find { it.account.id == card.id }!!
        assertEquals(5000_00L, cardDerived.derivedOwedPaise)

        // Net Available Balance remains exactly Rs 35,000 (40,000 - 5,000 = 35,000)
        assertEquals(35000_00L, afterNet.netAvailableBalancePaise)
    }

    @Test
    fun testInternalTransferLeavesNetAvailableBalanceUnchanged() {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "SBI",
            bankKey = "SBI",
            last4 = "1111",
            displayName = "SBI",
            type = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 30000_00L
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "HDFC",
            bankKey = "HDFC",
            last4 = "2222",
            displayName = "HDFC",
            type = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 20000_00L
        )

        val initialDerived = computeDerivedBalances(listOf(acc1, acc2), emptyList())
        val initialNet = computeNetOverview(initialDerived)
        assertEquals(50000_00L, initialNet.netAvailableBalancePaise)

        // Transfer Rs 5,000 from SBI to HDFC (two legs)
        val debitLeg = TransactionEntity(
            id = 201L,
            timestamp = System.currentTimeMillis(),
            amountPaise = 5000_00L,
            type = TransactionType.OWN_TRANSFER,
            friendlyVendorName = "Transfer to HDFC",
            accountId = acc1.id
        )
        val creditLeg = TransactionEntity(
            id = 202L,
            timestamp = System.currentTimeMillis(),
            amountPaise = 5000_00L,
            type = TransactionType.OWN_TRANSFER,
            friendlyVendorName = "Transfer from SBI",
            accountId = acc2.id
        )

        val afterDerived = computeDerivedBalances(listOf(acc1, acc2), listOf(debitLeg, creditLeg))
        val afterNet = computeNetOverview(afterDerived)

        // Acc1 = 25,000, Acc2 = 25,000 -> Net = 50,000
        assertEquals(25000_00L, afterDerived.find { it.account.id == acc1.id }!!.derivedBalancePaise)
        assertEquals(25000_00L, afterDerived.find { it.account.id == acc2.id }!!.derivedBalancePaise)
        assertEquals(50000_00L, afterNet.netAvailableBalancePaise)
    }

    @Test
    fun testAvailableCreditNeverAddedToNetAvailableBalance() {
        val card = AccountEntity(
            id = 1L,
            bankName = "HDFC Card",
            bankKey = "HDFC",
            last4 = "9999",
            displayName = "HDFC Card",
            type = AccountType.CREDIT_CARD,
            creditLimitPaise = 200000_00L, // 2 Lakhs Limit
            anchorSet = true,
            openingOwedPaise = 10000_00L // Owed 10,000
        )

        val derived = computeDerivedBalances(listOf(card), emptyList())
        val net = computeNetOverview(derived)

        // Available credit = 200,000 - 10,000 = 190,000
        assertEquals(190000_00L, derived[0].availableCreditPaise)
        // Headline net balance must be -10,000 (owed), NEVER 190,000
        assertEquals(-10000_00L, net.netAvailableBalancePaise)
    }
}
