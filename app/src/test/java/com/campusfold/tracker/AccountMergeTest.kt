package com.campusfold.tracker

import com.campusfold.tracker.ai.AiFallbackService
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.merge.AccountMergeManager
import com.campusfold.tracker.data.merge.DuplicatePair
import com.campusfold.tracker.data.merge.MergeConfidence
import com.campusfold.tracker.data.model.CategoryPeriodSpend
import com.campusfold.tracker.data.model.CategorySpending
import com.campusfold.tracker.data.model.PersonWithBalance
import com.campusfold.tracker.data.model.TransactionWithSplits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 6 Unit Tests: Account Merge Engine & Duplicate Detection
 * - Automatic Merge (HIGH confidence: same bankKey + last4 + type, alias links, shared transaction refs)
 * - Negative Constraints (Not Same pairs, edited fields protection, AI-only downgrade)
 * - Suggested Merge (MEDIUM confidence: missing last4, type divergence, name similarity, AI suggestion)
 * - Atomic Merge Execution (balance resolution, transaction reparenting, alias propagation, snapshotting)
 * - Undo Merge (exact account restoration, transaction roll-back, alias cleanup, log stamping)
 * - Manual Merge with user-chosen field overrides
 * - Survivor Account Selection heuristics
 */
class AccountMergeTest {

    private lateinit var fakeDb: FakeMergeAppDatabase
    private lateinit var accountMergeManager: AccountMergeManager

    @Before
    fun setUp() {
        fakeDb = FakeMergeAppDatabase()
        accountMergeManager = AccountMergeManager(
            db = fakeDb,
            accountDao = fakeDb.accountDao(),
            accountAliasDao = fakeDb.accountAliasDao(),
            mergeLogDao = fakeDb.mergeLogDao(),
            notSamePairDao = fakeDb.notSamePairDao(),
            transactionDao = fakeDb.transactionDao(),
            transactionRunner = { block -> block() }
        )
    }

    // ─── 1. Automatic Merge - High Confidence Detection ────────────────────────

    @Test
    fun testAutoMerge_HighConfidence_SameBankKeyLast4AndType() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Bank ••2003",
            balancePaise = 500000L,
            balanceAsOf = 1000L,
            balanceSource = BalanceSource.AUTO
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "AXIS BANK LTD",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Bank XX2003",
            balancePaise = 650000L,
            balanceAsOf = 2000L,
            balanceSource = BalanceSource.AUTO
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        val (autoPairs, suggestedPairs) = accountMergeManager.detectDuplicates()

        assertEquals("Should identify 1 auto-merge candidate", 1, autoPairs.size)
        assertEquals("Should have 0 suggested candidates", 0, suggestedPairs.size)
        assertEquals(MergeConfidence.HIGH, autoPairs[0].confidence)
        assertTrue(autoPairs[0].reason.contains("Same bank"))

        // Run auto-merge pass
        val logs = accountMergeManager.runAutoMergePass()
        assertEquals(1, logs.size)
        assertEquals(MergeMode.AUTO, logs[0].mode)
        assertEquals(1L, logs[0].survivingAccountId)

        // Survivor should remain, merged account deleted
        val activeAccounts = fakeDb.fakeAccountDao.getActiveAccountsSync()
        assertEquals(1, activeAccounts.size)
        assertEquals(1L, activeAccounts[0].id)
        // Newer AUTO balance (2000L > 1000L) should be kept
        assertEquals(650000L, activeAccounts[0].balancePaise)
    }

    @Test
    fun testAutoMerge_HighConfidence_ExistingAliasLink() = runBlocking {
        val acc1 = AccountEntity(
            id = 10L,
            bankName = "HDFC Bank",
            bankKey = "hdfc",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "HDFC Salary"
        )
        val acc2 = AccountEntity(
            id = 20L,
            bankName = "HDFC Bank",
            bankKey = "hdfc",
            last4 = "9999",
            type = AccountType.SAVINGS,
            displayName = "HDFC Secondary"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        // Alias points ("hdfc", "9999") to acc1
        fakeDb.fakeAccountAliasDao.insertAlias(
            AccountAliasEntity(accountId = 10L, bankKey = "hdfc", last4 = "9999")
        )

        val (autoPairs, _) = accountMergeManager.detectDuplicates()
        assertEquals(1, autoPairs.size)
        assertEquals(MergeConfidence.HIGH, autoPairs[0].confidence)
        assertTrue(autoPairs[0].reason.contains("alias"))
    }

    @Test
    fun testAutoMerge_HighConfidence_SharedTransactionReferences() = runBlocking {
        val acc1 = AccountEntity(
            id = 100L,
            bankName = "State Bank of India",
            bankKey = "sbi",
            last4 = "1111",
            type = AccountType.SAVINGS,
            displayName = "SBI 1"
        )
        val acc2 = AccountEntity(
            id = 200L,
            bankName = "State Bank of India",
            bankKey = "sbi",
            last4 = "2222",
            type = AccountType.SAVINGS,
            displayName = "SBI 2"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        // Give both accounts a transaction sharing the same UPI reference
        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 1L,
                accountId = 100L,
                amount = 50.0,
                amountPaise = 5000L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Shop",
                friendlyVendorName = "Shop",
                timestamp = 1000L,
                myActualSharePaise = 5000L,
                upiRef = "UPI/SHARED98765/Ref"
            )
        )
        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 2L,
                accountId = 200L,
                amount = 50.0,
                amountPaise = 5000L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Shop",
                friendlyVendorName = "Shop",
                timestamp = 1005L,
                myActualSharePaise = 5000L,
                upiRef = "UPI/SHARED98765/Ref"
            )
        )

        val (autoPairs, _) = accountMergeManager.detectDuplicates()
        assertEquals(1, autoPairs.size)
        assertEquals(MergeConfidence.HIGH, autoPairs[0].confidence)
        assertTrue(autoPairs[0].reason.contains("transaction reference"))
    }

    // ─── 2. Negative Constraints ───────────────────────────────────────────────

    @Test
    fun testNegativeConstraint_NotSamePairNeverAutoMergedOrSuggested() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis 1"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis 2"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        // Mark as Not Same
        accountMergeManager.markNotSame(acc1.id, acc2.id)

        val (autoPairs, suggestedPairs) = accountMergeManager.detectDuplicates()
        assertTrue("Auto candidates must be empty for Not Same pair", autoPairs.isEmpty())
        assertTrue("Suggested candidates must be empty for Not Same pair", suggestedPairs.isEmpty())
    }

    @Test
    fun testNegativeConstraint_EditedFieldsDowngradedToMedium() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "My Primary Savings",
            editedFields = "[\"displayName\"]" // User manually named it
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Bank XX2003",
            editedFields = "[]"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        val (autoPairs, suggestedPairs) = accountMergeManager.detectDuplicates()
        assertEquals("Must NOT be auto-merged", 0, autoPairs.size)
        assertEquals("Must be downgraded to suggested", 1, suggestedPairs.size)
        assertEquals(MergeConfidence.MEDIUM, suggestedPairs[0].confidence)
        assertTrue(suggestedPairs[0].reason.contains("user-edited fields"))

        // Auto merge pass must do nothing
        val autoLogs = accountMergeManager.runAutoMergePass()
        assertTrue("Auto merge pass must not touch edited accounts", autoLogs.isEmpty())
    }

    @Test
    fun testNegativeConstraint_AiMatchNeverAutoMerged() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Federal Bank",
            bankKey = "federal",
            last4 = "1111",
            type = AccountType.SAVINGS,
            displayName = "Jupiter Digital Account"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Federal Bank",
            bankKey = "federal",
            last4 = "2222",
            type = AccountType.SAVINGS,
            displayName = "Fi Money Digital Account"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        val fakeAiService = object : AiFallbackService(fakeDb.fakeAiCacheDao) {
            override fun isAiAvailable(): Boolean = true
            override suspend fun judgeDuplicateAccounts(
                nameA: String, bankA: String, last4A: String,
                nameB: String, bankB: String, last4B: String
            ): Boolean = true
        }

        val aiMergeManager = AccountMergeManager(
            db = fakeDb,
            accountDao = fakeDb.accountDao(),
            accountAliasDao = fakeDb.accountAliasDao(),
            mergeLogDao = fakeDb.mergeLogDao(),
            notSamePairDao = fakeDb.notSamePairDao(),
            transactionDao = fakeDb.transactionDao(),
            aiFallbackService = fakeAiService,
            transactionRunner = { block -> block() }
        )

        val (autoPairs, suggestedPairs) = aiMergeManager.detectDuplicates()
        assertTrue("AI match must NEVER be auto-merged", autoPairs.isEmpty())
        assertEquals("AI match must be suggested", 1, suggestedPairs.size)
        assertTrue(suggestedPairs[0].isAiOnly)
        assertEquals(MergeConfidence.MEDIUM, suggestedPairs[0].confidence)
        assertTrue(suggestedPairs[0].reason.contains("AI suggested"))
    }

    // ─── 3. Suggested Partial Matches ──────────────────────────────────────────

    @Test
    fun testSuggestedMerge_MissingLast4VsPresentLast4() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "PNB",
            bankKey = "pnb",
            last4 = "8598",
            type = AccountType.CREDIT_CARD,
            displayName = "PNB Credit Card 8598"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "PNB",
            bankKey = "pnb",
            last4 = "", // Missing last 4
            type = AccountType.CREDIT_CARD,
            displayName = "PNB Card"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        val (autoPairs, suggestedPairs) = accountMergeManager.detectDuplicates()
        assertTrue(autoPairs.isEmpty())
        assertEquals(1, suggestedPairs.size)
        assertEquals(MergeConfidence.MEDIUM, suggestedPairs[0].confidence)
        assertTrue(suggestedPairs[0].reason.contains("missing last 4 digits"))
    }

    @Test
    fun testSuggestedMerge_TypeDivergence() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Account"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.CURRENT, // Different type guess
            displayName = "Axis Current"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        val (autoPairs, suggestedPairs) = accountMergeManager.detectDuplicates()
        assertTrue(autoPairs.isEmpty())
        assertEquals(1, suggestedPairs.size)
        assertEquals(MergeConfidence.MEDIUM, suggestedPairs[0].confidence)
        assertTrue(suggestedPairs[0].reason.contains("different account types"))
    }

    @Test
    fun testSuggestedMerge_SimilarNamesOnSameBank() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "ICICI Bank",
            bankKey = "icici",
            last4 = "1001",
            type = AccountType.SAVINGS,
            displayName = "ICICI Wealth Account"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "ICICI Bank",
            bankKey = "icici",
            last4 = "1002",
            type = AccountType.SAVINGS,
            displayName = "ICICI Wealth Acc"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        val (autoPairs, suggestedPairs) = accountMergeManager.detectDuplicates()
        assertTrue(autoPairs.isEmpty())
        assertEquals(1, suggestedPairs.size)
        assertEquals(MergeConfidence.MEDIUM, suggestedPairs[0].confidence)
        assertTrue(suggestedPairs[0].reason.contains("Similar account names"))
    }

    // ─── 4. Atomic Merge Execution & Balance Resolution ────────────────────────

    @Test
    fun testMergeExecution_BalanceResolution_ManualOverAuto() = runBlocking {
        val autoAcc = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Auto",
            balancePaise = 500000L,
            balanceAsOf = 5000L,
            balanceSource = BalanceSource.AUTO
        )
        val manualAcc = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Manual",
            balancePaise = 1000000L,
            balanceAsOf = 1000L,
            balanceSource = BalanceSource.MANUAL
        )
        fakeDb.fakeAccountDao.insertAccount(autoAcc)
        fakeDb.fakeAccountDao.insertAccount(manualAcc)

        // Merge auto into manual
        accountMergeManager.mergeAccounts(
            survivingAccountId = manualAcc.id,
            mergedAccountId = autoAcc.id,
            mode = MergeMode.MANUAL
        )

        val surviving = fakeDb.fakeAccountDao.getAccountById(manualAcc.id)!!
        assertEquals(1000000L, surviving.balancePaise)
        assertEquals(BalanceSource.MANUAL, surviving.balanceSource)

        // Merged account deleted
        assertNull(fakeDb.fakeAccountDao.getAccountById(autoAcc.id))
    }

    @Test
    fun testMergeExecution_TransactionReparentingAndAliasMovement() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Survivor"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "5557",
            type = AccountType.SAVINGS,
            displayName = "Axis Merged"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        // Existing alias pointing to acc2
        fakeDb.fakeAccountAliasDao.insertAlias(
            AccountAliasEntity(accountId = 2L, bankKey = "axis", last4 = "OLD_ALIAS")
        )

        // Acc1 has 1 transaction, Acc2 has 2 transactions
        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 101L,
                accountId = 1L,
                amount = 100.0,
                amountPaise = 10000L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Vendor A",
                friendlyVendorName = "Vendor A",
                timestamp = 1000L,
                myActualSharePaise = 10000L
            )
        )
        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 201L,
                accountId = 2L,
                amount = 200.0,
                amountPaise = 20000L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Vendor B",
                friendlyVendorName = "Vendor B",
                timestamp = 2000L,
                myActualSharePaise = 20000L
            )
        )
        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 202L,
                accountId = 2L,
                amount = 300.0,
                amountPaise = 30000L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Vendor C",
                friendlyVendorName = "Vendor C",
                timestamp = 3000L,
                myActualSharePaise = 30000L
            )
        )

        val logId = accountMergeManager.mergeAccounts(
            survivingAccountId = 1L,
            mergedAccountId = 2L,
            mode = MergeMode.MANUAL
        )

        // All 3 transactions must now be on Account 1
        val txsOnAcc1 = fakeDb.fakeTransactionDao.getTransactionsByAccountId(1L)
        assertEquals(3, txsOnAcc1.size)
        assertTrue(txsOnAcc1.any { it.id == 101L })
        assertTrue(txsOnAcc1.any { it.id == 201L })
        assertTrue(txsOnAcc1.any { it.id == 202L })

        // No transactions on Account 2
        val txsOnAcc2 = fakeDb.fakeTransactionDao.getTransactionsByAccountId(2L)
        assertEquals(0, txsOnAcc2.size)

        // Aliases:
        // 1. Acc2's original alias ("OLD_ALIAS") reassigned to Acc1
        val reassigned = fakeDb.fakeAccountAliasDao.findAccountIdByAlias("axis", "OLD_ALIAS")
        assertEquals(1L, reassigned)

        // 2. Acc2's own identifier ("axis", "5557") inserted as alias for Acc1
        val newAlias = fakeDb.fakeAccountAliasDao.findAccountIdByAlias("axis", "5557")
        assertEquals(1L, newAlias)

        // Merge log entry created
        val log = fakeDb.fakeMergeLogDao.getMergeLogById(logId)
        assertNotNull(log)
        assertEquals(1L, log!!.survivingAccountId)
        assertEquals(MergeMode.MANUAL, log.mode)
        assertNull(log.undoneAt)
        val movedIds = AccountMergeManager.deserializeMovedTransactionIds(log.movedTransactionIds)
        assertEquals(listOf(201L, 202L), movedIds)
    }

    // ─── 5. Undo Merge Execution ───────────────────────────────────────────────

    @Test
    fun testUndoMerge_ExactRestorationOfAccountAndTransactions() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Survivor Account",
            balancePaise = 500000L,
            balanceAsOf = 1000L,
            balanceSource = BalanceSource.AUTO
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "5557",
            type = AccountType.SAVINGS,
            displayName = "Merged Account",
            balancePaise = 250000L,
            balanceAsOf = 900L,
            balanceSource = BalanceSource.AUTO,
            creditLimitPaise = 0L,
            editedFields = "[\"displayName\"]"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 101L,
                accountId = 1L,
                amount = 50.0,
                amountPaise = 5000L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Tx1",
                friendlyVendorName = "Tx1",
                timestamp = 1000L,
                myActualSharePaise = 5000L
            )
        )
        fakeDb.fakeTransactionDao.insertTransaction(
            TransactionEntity(
                id = 201L,
                accountId = 2L,
                amount = 75.0,
                amountPaise = 7500L,
                type = TransactionType.EXPENSE,
                rawMerchant = "Tx2",
                friendlyVendorName = "Tx2",
                timestamp = 2000L,
                myActualSharePaise = 7500L
            )
        )

        // 1. Merge
        val logId = accountMergeManager.mergeAccounts(
            survivingAccountId = 1L,
            mergedAccountId = 2L,
            mode = MergeMode.AUTO
        )
        assertNull(fakeDb.fakeAccountDao.getAccountById(2L))
        assertEquals(2, fakeDb.fakeTransactionDao.getTransactionsByAccountId(1L).size)

        // 2. Undo
        val undoSuccess = accountMergeManager.undoMerge(logId)
        assertTrue(undoSuccess)

        // Account 2 restored exactly from snapshot
        val restoredAcc2 = fakeDb.fakeAccountDao.getAccountById(2L)
        assertNotNull(restoredAcc2)
        assertEquals("Merged Account", restoredAcc2!!.displayName)
        assertEquals("5557", restoredAcc2.last4)
        assertEquals(250000L, restoredAcc2.balancePaise)
        assertEquals("[\"displayName\"]", restoredAcc2.editedFields)

        // Transactions moved back
        val txs1 = fakeDb.fakeTransactionDao.getTransactionsByAccountId(1L)
        val txs2 = fakeDb.fakeTransactionDao.getTransactionsByAccountId(2L)
        assertEquals(1, txs1.size)
        assertEquals(101L, txs1[0].id)
        assertEquals(1, txs2.size)
        assertEquals(201L, txs2[0].id)

        // Alias for Acc2 ("axis", "5557") removed
        assertNull(fakeDb.fakeAccountAliasDao.findAccountIdByAlias("axis", "5557"))

        // Log stamped as undone
        val updatedLog = fakeDb.fakeMergeLogDao.getMergeLogById(logId)
        assertNotNull(updatedLog!!.undoneAt)

        // Calling undo again fails
        assertFalse(accountMergeManager.undoMerge(logId))
    }

    // ─── 6. Manual Merge with Field Overrides ──────────────────────────────────

    @Test
    fun testManualMerge_UserCustomOverrides() = runBlocking {
        val acc1 = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis 1"
        )
        val acc2 = AccountEntity(
            id = 2L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2004",
            type = AccountType.CURRENT,
            displayName = "Axis 2"
        )
        fakeDb.fakeAccountDao.insertAccount(acc1)
        fakeDb.fakeAccountDao.insertAccount(acc2)

        accountMergeManager.mergeAccounts(
            survivingAccountId = 1L,
            mergedAccountId = 2L,
            mode = MergeMode.MANUAL,
            overrideDisplayName = "My Combined Axis",
            overrideType = AccountType.SALARY,
            overrideLast4 = "2003"
        )

        val survivor = fakeDb.fakeAccountDao.getAccountById(1L)!!
        assertEquals("My Combined Axis", survivor.displayName)
        assertEquals(AccountType.SALARY, survivor.type)
        assertEquals("2003", survivor.last4)
    }

    // ─── 7. Survivor Account Selection Heuristics ──────────────────────────────

    @Test
    fun testPickSurvivingAccount_Heuristics() {
        val manualAcc = AccountEntity(
            id = 5L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "Manual",
            balanceSource = BalanceSource.MANUAL
        )
        val autoAcc = AccountEntity(
            id = 1L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "Auto",
            balanceSource = BalanceSource.AUTO
        )
        // MANUAL beats AUTO
        assertEquals(manualAcc, accountMergeManager.pickSurvivingAccount(autoAcc, manualAcc))

        val accWithLast4 = AccountEntity(
            id = 10L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "With last4"
        )
        val accWithoutLast4 = AccountEntity(
            id = 2L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "",
            type = AccountType.SAVINGS,
            displayName = "Without last4"
        )
        // Non-blank last4 beats blank last4
        assertEquals(accWithLast4, accountMergeManager.pickSurvivingAccount(accWithoutLast4, accWithLast4))

        val typedAcc = AccountEntity(
            id = 20L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "Savings"
        )
        val unassignedAcc = AccountEntity(
            id = 3L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.UNASSIGNED,
            displayName = "Unassigned"
        )
        // Assigned type beats UNASSIGNED
        assertEquals(typedAcc, accountMergeManager.pickSurvivingAccount(unassignedAcc, typedAcc))

        val olderAcc = AccountEntity(
            id = 1L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "Older"
        )
        val newerAcc = AccountEntity(
            id = 2L,
            bankName = "Bank",
            bankKey = "bank",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "Newer"
        )
        // Smaller ID beats larger ID
        assertEquals(olderAcc, accountMergeManager.pickSurvivingAccount(newerAcc, olderAcc))
    }

    // ─── 8. Helpers & Serialization ────────────────────────────────────────────

    @Test
    fun testSerializationAndSimilarity() {
        val acc = AccountEntity(
            id = 42L,
            bankName = "Federal Bank",
            bankKey = "federal",
            last4 = "9876",
            type = AccountType.SAVINGS,
            displayName = "Federal Jupiter",
            color = "#00FFCC",
            balancePaise = 77700L,
            balanceAsOf = 54321L,
            balanceSource = BalanceSource.MANUAL,
            creditLimitPaise = 5000000L,
            isArchived = false,
            editedFields = "[\"displayName\",\"type\"]"
        )

        val json = AccountMergeManager.serializeAccountSnapshot(acc)
        val restored = AccountMergeManager.deserializeAccountSnapshot(json)

        assertEquals(acc.id, restored.id)
        assertEquals(acc.bankName, restored.bankName)
        assertEquals(acc.bankKey, restored.bankKey)
        assertEquals(acc.last4, restored.last4)
        assertEquals(acc.type, restored.type)
        assertEquals(acc.displayName, restored.displayName)
        assertEquals(acc.color, restored.color)
        assertEquals(acc.balancePaise, restored.balancePaise)
        assertEquals(acc.balanceAsOf, restored.balanceAsOf)
        assertEquals(acc.balanceSource, restored.balanceSource)
        assertEquals(acc.creditLimitPaise, restored.creditLimitPaise)
        assertEquals(acc.editedFields, restored.editedFields)

        // String similarity
        assertEquals(1.0, AccountMergeManager.stringSimilarity("Axis Bank", "Axis Bank"), 0.001)
        assertTrue(AccountMergeManager.stringSimilarity("Axis Bank XX2003", "Axis Bank ••2003") > 0.70)
        assertTrue(AccountMergeManager.hasEditedNameOrType(acc))

        val unedited = acc.copy(editedFields = "[]")
        assertFalse(AccountMergeManager.hasEditedNameOrType(unedited))
    }
}

// ─── Test Fake Database & DAOs ───────────────────────────────────────────────

class FakeMergeAppDatabase : AppDatabase() {
    val fakeAccountDao = FakeMergeAccountDao()
    val fakeAccountAliasDao = FakeMergeAccountAliasDao()
    val fakeMergeLogDao = FakeMergeMergeLogDao()
    val fakeNotSamePairDao = FakeMergeNotSamePairDao()
    val fakeTransactionDao = FakeMergeTransactionDao()
    val fakeAiCacheDao = FakeMergeAiCacheDao()

    override fun accountDao(): AccountDao = fakeAccountDao
    override fun accountAliasDao(): AccountAliasDao = fakeAccountAliasDao
    override fun mergeLogDao(): MergeLogDao = fakeMergeLogDao
    override fun notSamePairDao(): NotSamePairDao = fakeNotSamePairDao
    override fun transactionDao(): TransactionDao = fakeTransactionDao
    override fun aiCacheDao(): AiCacheDao = fakeAiCacheDao

    override fun categoryDao(): CategoryDao = throw NotImplementedError()
    override fun vendorDao(): VendorDao = throw NotImplementedError()
    override fun personDao(): PersonDao = throw NotImplementedError()
    override fun splitItemDao(): SplitItemDao = throw NotImplementedError()
    override fun pendingSettlementSuggestionDao(): PendingSettlementSuggestionDao = throw NotImplementedError()
    override fun ruleDao(): RuleDao = throw NotImplementedError()
    override fun reviewItemDao(): ReviewItemDao = throw NotImplementedError()
    override fun unparsedMessageDao(): UnparsedMessageDao = throw NotImplementedError()
    override fun processedMessageDao(): ProcessedMessageDao = throw NotImplementedError()
    override fun scanHistoryDao(): ScanHistoryDao = throw NotImplementedError()

    override fun clearAllTables() {}
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "accounts")
    }
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        throw NotImplementedError()
    }
}

class FakeMergeAccountDao : AccountDao {
    val accounts = mutableMapOf<Long, AccountEntity>()
    private var idCounter = 1L

    override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.filter { !it.isArchived })
    override fun getAllAccountsIncludingArchived(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts.values.toList()
    override suspend fun getActiveAccountsSync(): List<AccountEntity> = accounts.values.filter { !it.isArchived }
    override suspend fun getAccountById(id: Long): AccountEntity? = accounts[id]
    override suspend fun findByBankKeyAndLast4(bankKey: String, last4: String): AccountEntity? =
        accounts.values.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4 == last4 }

    override suspend fun getAccountCount(): Int = accounts.values.count { !it.isArchived }

    override suspend fun insertAccount(account: AccountEntity): Long {
        val id = if (account.id > 0) account.id else idCounter++
        accounts[id] = account.copy(id = id)
        return id
    }

    override suspend fun insertAccounts(list: List<AccountEntity>): List<Long> = list.map { insertAccount(it) }
    override suspend fun updateAccount(account: AccountEntity) { accounts[account.id] = account }
    override suspend fun deleteAccount(account: AccountEntity) { accounts.remove(account.id) }
    override suspend fun deleteAccountById(id: Long) { accounts.remove(id) }
    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = isArchived) }
    }
    override suspend fun updateBalance(id: Long, balancePaise: Long, balanceAsOf: Long, source: BalanceSource) {
        accounts[id]?.let {
            accounts[id] = it.copy(balancePaise = balancePaise, balanceAsOf = balanceAsOf, balanceSource = source)
        }
    }
}

class FakeMergeAccountAliasDao : AccountAliasDao {
    val aliases = mutableListOf<AccountAliasEntity>()

    override fun getAliasesForAccount(accountId: Long): Flow<List<AccountAliasEntity>> =
        flowOf(aliases.filter { it.accountId == accountId })

    override suspend fun getAliasesForAccountSync(accountId: Long): List<AccountAliasEntity> =
        aliases.filter { it.accountId == accountId }

    override suspend fun findAccountIdByAlias(bankKey: String, last4: String): Long? =
        aliases.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4.equals(last4, ignoreCase = true) }?.accountId

    override suspend fun getAllAliasesSync(): List<AccountAliasEntity> = aliases.toList()

    override suspend fun insertAlias(alias: AccountAliasEntity) {
        aliases.removeAll { it.accountId == alias.accountId && it.bankKey.equals(alias.bankKey, ignoreCase = true) && it.last4.equals(alias.last4, ignoreCase = true) }
        aliases.add(alias)
    }

    override suspend fun insertAliases(aliasesList: List<AccountAliasEntity>) {
        aliasesList.forEach { insertAlias(it) }
    }

    override suspend fun deleteAlias(alias: AccountAliasEntity) {
        aliases.remove(alias)
    }

    override suspend fun deleteAliasesForAccount(accountId: Long) {
        aliases.removeAll { it.accountId == accountId }
    }

    override suspend fun reassignAliases(oldAccountId: Long, newAccountId: Long) {
        for (i in aliases.indices) {
            if (aliases[i].accountId == oldAccountId) {
                aliases[i] = aliases[i].copy(accountId = newAccountId)
            }
        }
    }

    override suspend fun deleteAliasByBankKeyAndLast4(bankKey: String, last4: String) {
        aliases.removeAll { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4.equals(last4, ignoreCase = true) }
    }
}

class FakeMergeMergeLogDao : MergeLogDao {
    val logs = mutableMapOf<Long, MergeLogEntity>()
    private var idCounter = 1L

    override fun getAllMergeLogs(): Flow<List<MergeLogEntity>> = flowOf(logs.values.sortedByDescending { it.createdAt })
    override suspend fun getMergeLogById(id: Long): MergeLogEntity? = logs[id]
    override suspend fun getAllMergeLogsSync(): List<MergeLogEntity> = logs.values.toList()

    override suspend fun insertMergeLog(log: MergeLogEntity): Long {
        val id = if (log.id > 0) log.id else idCounter++
        logs[id] = log.copy(id = id)
        return id
    }

    override suspend fun updateMergeLog(log: MergeLogEntity) {
        logs[log.id] = log
    }

    override fun getLatestActiveAutoMergeLogFlow(): Flow<MergeLogEntity?> =
        flowOf(logs.values.filter { it.mode == MergeMode.AUTO && it.undoneAt == null }.maxByOrNull { it.createdAt })

    override suspend fun getLatestActiveAutoMergeLogSync(): MergeLogEntity? =
        logs.values.filter { it.mode == MergeMode.AUTO && it.undoneAt == null }.maxByOrNull { it.createdAt }

    override fun getActiveMergeLogsForAccount(accountId: Long): Flow<List<MergeLogEntity>> =
        flowOf(logs.values.filter { it.survivingAccountId == accountId && it.undoneAt == null }.sortedByDescending { it.createdAt })

    override suspend fun getActiveMergeLogsForAccountSync(accountId: Long): List<MergeLogEntity> =
        logs.values.filter { it.survivingAccountId == accountId && it.undoneAt == null }.sortedByDescending { it.createdAt }

    override fun getAllActiveMergeLogs(): Flow<List<MergeLogEntity>> =
        flowOf(logs.values.filter { it.undoneAt == null }.sortedByDescending { it.createdAt })

    override suspend fun getAllActiveMergeLogsSync(): List<MergeLogEntity> =
        logs.values.filter { it.undoneAt == null }.sortedByDescending { it.createdAt }

    override suspend fun markUndone(id: Long, undoneAt: Long) {
        logs[id]?.let { logs[id] = it.copy(undoneAt = undoneAt) }
    }
}

class FakeMergeNotSamePairDao : NotSamePairDao {
    val pairs = mutableListOf<NotSamePairEntity>()

    override fun getAllNotSamePairs(): Flow<List<NotSamePairEntity>> = flowOf(pairs)
    override suspend fun getAllNotSamePairsSync(): List<NotSamePairEntity> = pairs.toList()

    override suspend fun isPairMarkedNotSame(idA: Long, idB: Long): Boolean =
        pairs.any { (it.accountIdA == idA && it.accountIdB == idB) || (it.accountIdA == idB && it.accountIdB == idA) }

    override suspend fun insertPair(pair: NotSamePairEntity) {
        if (!isPairMarkedNotSame(pair.accountIdA, pair.accountIdB)) {
            pairs.add(pair)
        }
    }

    override suspend fun deletePair(pair: NotSamePairEntity) {
        pairs.removeAll { (it.accountIdA == pair.accountIdA && it.accountIdB == pair.accountIdB) || (it.accountIdA == pair.accountIdB && it.accountIdB == pair.accountIdA) }
    }
}

class FakeMergeTransactionDao : TransactionDao {
    val transactions = mutableMapOf<Long, TransactionEntity>()
    private var idCounter = 1L

    override fun getAllTransactions(): Flow<List<TransactionEntity>> = flowOf(transactions.values.sortedByDescending { it.timestamp })
    override fun getTransactionsBetween(start: Long, end: Long): Flow<List<TransactionEntity>> =
        flowOf(transactions.values.filter { it.timestamp in start..end })
    override suspend fun getTransactionsBetweenSync(start: Long, end: Long): List<TransactionEntity> =
        transactions.values.filter { it.timestamp in start..end }
    override fun getTransactionById(id: Long): Flow<TransactionEntity?> = flowOf(transactions[id])
    override suspend fun getTransactionByIdSync(id: Long): TransactionEntity? = transactions[id]
    override fun getTransactionWithSplits(id: Long): Flow<TransactionWithSplits?> = flowOf(null)
    override fun getAllTransactionsWithSplits(): Flow<List<TransactionWithSplits>> = flowOf(emptyList())

    override suspend fun findByUpiRef(upiRef: String): TransactionEntity? =
        transactions.values.find { it.upiRef == upiRef }

    override suspend fun findByReference(reference: String): TransactionEntity? =
        transactions.values.find { it.reference == reference }

    override suspend fun findByRawMessageHash(hash: String): TransactionEntity? =
        transactions.values.find { it.rawMessageHash == hash }

    override suspend fun findPotentialDuplicate(amountPaise: Long, paymentMode: String, minTime: Long, maxTime: Long): TransactionEntity? =
        transactions.values.find { it.amountPaise == amountPaise && it.paymentMode == paymentMode && it.timestamp in minTime..maxTime }

    override suspend fun findDuplicatesByAccountAmountTime(
        accountId: Long?,
        paymentMode: String,
        amountPaise: Long,
        minTime: Long,
        maxTime: Long
    ): List<TransactionEntity> = transactions.values.filter {
        (it.accountId == accountId || (accountId == null && it.paymentMode == paymentMode)) &&
                it.amountPaise == amountPaise && it.timestamp in minTime..maxTime
    }

    override suspend fun getTransactionsByAccountId(accountId: Long): List<TransactionEntity> =
        transactions.values.filter { it.accountId == accountId }

    override suspend fun countTransactionsByAccountId(accountId: Long): Int =
        transactions.values.count { it.accountId == accountId }

    override suspend fun updateAccountIdForTransactions(oldAccountId: Long, newAccountId: Long) {
        for ((id, tx) in transactions.entries) {
            if (tx.accountId == oldAccountId) {
                transactions[id] = tx.copy(accountId = newAccountId)
            }
        }
    }

    override suspend fun updateAccountIdForTransactionIds(ids: List<Long>, newAccountId: Long) {
        for (id in ids) {
            transactions[id]?.let {
                transactions[id] = it.copy(accountId = newAccountId)
            }
        }
    }

    override suspend fun getDistinctReferencesForAccount(accountId: Long): List<String> =
        transactions.values.filter { it.accountId == accountId }.mapNotNull { it.reference }.filter { it.isNotBlank() }.distinct()

    override suspend fun getDistinctUpiRefsForAccount(accountId: Long): List<String> =
        transactions.values.filter { it.accountId == accountId }.mapNotNull { it.upiRef }.filter { it.isNotBlank() }.distinct()

    override suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = if (transaction.id > 0) transaction.id else idCounter++
        transactions[id] = transaction.copy(id = id)
        return id
    }

    override suspend fun insertTransactions(txs: List<TransactionEntity>): List<Long> = txs.map { insertTransaction(it) }
    override suspend fun restoreTransactions(txs: List<TransactionEntity>): List<Long> = insertTransactions(txs)
    override suspend fun updateTransaction(transaction: TransactionEntity) { transactions[transaction.id] = transaction }
    override suspend fun deleteTransaction(transaction: TransactionEntity) { transactions.remove(transaction.id) }
    override suspend fun deleteTransactionById(id: Long) { transactions.remove(id) }
    override suspend fun clearAll() { transactions.clear() }

    override fun getTotalMyExpensePaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalGrossExpensePaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalIncomePaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalCcBillPaymentsPaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalSpentOnPaymentModePaise(mode: String): Flow<Long?> = flowOf(0L)
}

class FakeMergeAiCacheDao : AiCacheDao {
    val cache = mutableMapOf<String, AiCacheEntity>()
    override suspend fun getByInputHash(inputHash: String): AiCacheEntity? = cache[inputHash]
    override suspend fun getByInputHashAndTask(inputHash: String, task: String): AiCacheEntity? =
        cache.values.find { it.inputHash == inputHash && it.task == task }
    override suspend fun insertCache(cacheEntity: AiCacheEntity) { cache[cacheEntity.inputHash] = cacheEntity }
    override suspend fun clearAll() { cache.clear() }
}
