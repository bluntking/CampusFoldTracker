package com.campusfold.tracker

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
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
                (it.accountId == account.id || it.toAccountId == account.id) && it.timestamp >= anchorDate
            }

            if (account.accountType == AccountType.CREDIT_CARD) {
                val anchorOwed = if (account.anchorSet) account.openingOwedPaise else account.balancePaise
                var debitsSince = 0L
                var creditsSince = 0L

                for (t in relevantTxns) {
                    if (t.accountId == account.id) {
                        when (t.type) {
                            TransactionType.EXPENSE -> debitsSince += t.amountPaise
                            TransactionType.INCOME, TransactionType.REFUND -> creditsSince += t.amountPaise
                            TransactionType.CC_BILL_TRANSFER -> creditsSince += t.amountPaise
                            TransactionType.SPLIT_SETTLEMENT -> creditsSince += t.amountPaise
                            TransactionType.TRANSFER -> debitsSince += t.amountPaise
                        }
                    }
                    if (t.toAccountId == account.id && t.type == TransactionType.CC_BILL_TRANSFER) {
                        creditsSince += t.amountPaise
                    }
                }

                val currentOwed = (anchorOwed + debitsSince - creditsSince).coerceAtLeast(0L)
                val availableCredit = (account.creditLimitPaise - currentOwed).coerceAtLeast(0L)

                DerivedAccountBalance(
                    account = account,
                    derivedBalancePaise = -currentOwed,
                    derivedOwedPaise = currentOwed,
                    availableCreditPaise = availableCredit,
                    anchorDate = anchorDate,
                    isCreditCard = true,
                    discrepancyPaise = Math.abs(currentOwed - account.balancePaise)
                )
            } else {
                val anchorBal = if (account.anchorSet) account.openingBalancePaise else account.balancePaise
                var debitsSince = 0L
                var creditsSince = 0L

                for (t in relevantTxns) {
                    if (t.accountId == account.id) {
                        when (t.type) {
                            TransactionType.EXPENSE, TransactionType.CC_BILL_TRANSFER, TransactionType.SPLIT_SETTLEMENT ->
                                debitsSince += t.amountPaise
                            TransactionType.TRANSFER -> debitsSince += t.amountPaise
                            TransactionType.INCOME, TransactionType.REFUND -> creditsSince += t.amountPaise
                        }
                    }
                    if (t.toAccountId == account.id && t.type == TransactionType.TRANSFER) {
                        creditsSince += t.amountPaise
                    }
                }

                val currentBalance = anchorBal + creditsSince - debitsSince

                DerivedAccountBalance(
                    account = account,
                    derivedBalancePaise = currentBalance,
                    derivedOwedPaise = 0L,
                    availableCreditPaise = 0L,
                    anchorDate = anchorDate,
                    isCreditCard = false,
                    discrepancyPaise = Math.abs(currentBalance - account.balancePaise)
                )
            }
        }
    }

    private fun computeNetOverview(
        derivedList: List<DerivedAccountBalance>,
        friendOwedPaise: Long = 0L
    ): NetBalanceOverview {
        val totalSavings = derivedList.filter { !it.isCreditCard }.sumOf { it.derivedBalancePaise }
        val totalCardOwed = derivedList.filter { it.isCreditCard }.sumOf { it.derivedOwedPaise }
        val netAvailable = totalSavings - totalCardOwed
        val hasMissingAnchor = derivedList.any { !it.account.anchorSet }

        return NetBalanceOverview(
            netAvailableBalancePaise = netAvailable,
            totalSavingsPaise = totalSavings,
            totalCreditCardOwedPaise = totalCardOwed,
            totalOwedToFriendsPaise = friendOwedPaise,
            hasMissingAnchor = hasMissingAnchor
        )
    }

    @Test
    fun testNetAvailableBalanceEqualsSavingsMinusCardOwed() {
        val savings = AccountEntity(
            id = 1L,
            bankName = "State Bank",
            last4 = "1234",
            accountType = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 50000_00L, // Rs 50,000
            balancePaise = 50000_00L
        )
        val card = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            last4 = "5678",
            accountType = AccountType.CREDIT_CARD,
            creditLimitPaise = 100000_00L, // Limit Rs 1,00,000
            anchorSet = true,
            openingOwedPaise = 15000_00L, // Owed Rs 15,000
            balancePaise = 15000_00L
        )

        val derived = computeDerivedBalances(listOf(savings, card), emptyList())
        val net = computeNetOverview(derived, friendOwedPaise = 5000_00L)

        // Net = 50,000 - 15,000 = 35,000
        assertEquals(35000_00L, net.netAvailableBalancePaise)
        assertEquals(50000_00L, net.totalSavingsPaise)
        assertEquals(15000_00L, net.totalCreditCardOwedPaise)

        // Friend debt is displayed separately and NOT subtracted from net
        assertEquals(5000_00L, net.totalOwedToFriendsPaise)
        assertEquals(35000_00L, net.netAvailableBalancePaise)
    }

    @Test
    fun testCreditCardBillPaymentLeavesNetAvailableBalanceUnchanged() {
        val savings = AccountEntity(
            id = 1L,
            bankName = "State Bank",
            last4 = "1234",
            accountType = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 50000_00L
        )
        val card = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            last4 = "5678",
            accountType = AccountType.CREDIT_CARD,
            creditLimitPaise = 100000_00L,
            anchorSet = true,
            openingOwedPaise = 15000_00L
        )

        val initialDerived = computeDerivedBalances(listOf(savings, card), emptyList())
        val initialNet = computeNetOverview(initialDerived)
        assertEquals(35000_00L, initialNet.netAvailableBalancePaise)

        // Pay Rs 10,000 credit card bill from savings account
        val billTxn = TransactionEntity(
            id = 101L,
            rawMessageId = "msg_1",
            sender = "AXISBK",
            timestamp = System.currentTimeMillis(),
            amountPaise = 10000_00L,
            type = TransactionType.CC_BILL_TRANSFER,
            vendorName = "Axis Credit Card Bill",
            accountId = savings.id, // Debited from savings
            toAccountId = card.id    // Credited to card
        )

        val afterDerived = computeDerivedBalances(listOf(savings, card), listOf(billTxn))
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
            last4 = "1111",
            accountType = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 30000_00L
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "HDFC",
            last4 = "2222",
            accountType = AccountType.SAVINGS,
            anchorSet = true,
            openingBalancePaise = 20000_00L
        )

        val initialDerived = computeDerivedBalances(listOf(acc1, acc2), emptyList())
        val initialNet = computeNetOverview(initialDerived)
        assertEquals(50000_00L, initialNet.netAvailableBalancePaise)

        // Transfer Rs 5,000 from SBI to HDFC
        val transferTxn = TransactionEntity(
            id = 201L,
            rawMessageId = "msg_2",
            sender = "SBIINB",
            timestamp = System.currentTimeMillis(),
            amountPaise = 5000_00L,
            type = TransactionType.TRANSFER,
            vendorName = "Self Transfer",
            accountId = acc1.id,
            toAccountId = acc2.id
        )

        val afterDerived = computeDerivedBalances(listOf(acc1, acc2), listOf(transferTxn))
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
            last4 = "9999",
            accountType = AccountType.CREDIT_CARD,
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
