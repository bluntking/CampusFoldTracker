package com.campusfold.tracker.data

import android.content.Context
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.model.*
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.util.BudgetAlertEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

class TransactionRepository(
    private val db: AppDatabase,
    private val appPreferences: AppPreferences,
    private val context: Context? = null
) {

    private val transactionDao: TransactionDao = db.transactionDao()
    private val categoryDao: CategoryDao = db.categoryDao()
    private val vendorDao: VendorMappingDao = db.vendorMappingDao()
    private val personDao: PersonDao = db.personDao()
    private val splitDao: SplitItemDao = db.splitItemDao()
    private val suggestionDao: PendingSettlementSuggestionDao = db.pendingSettlementSuggestionDao()
    private val accountDao: AccountDao = db.accountDao()

    // ─── Accounts ─────────────────────────────────────────────────────────────
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun addAccount(account: AccountEntity): Long = withContext(Dispatchers.IO) {
        accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.deleteAccount(account)
    }

    suspend fun clearAllTransactionsAndReset() = withContext(Dispatchers.IO) {
        transactionDao.clearAll()
        splitDao.clearAll()
        suggestionDao.clearAll()
    }

    // ─── JSON Backup & Restore ────────────────────────────────────────────────

    suspend fun exportBackup(context: android.content.Context, uri: android.net.Uri): Result<com.campusfold.tracker.util.BackupStats> {
        return com.campusfold.tracker.util.BackupManager.exportBackup(context, uri, db, appPreferences)
    }

    suspend fun importBackup(context: android.content.Context, uri: android.net.Uri): Result<com.campusfold.tracker.util.BackupStats> {
        return com.campusfold.tracker.util.BackupManager.importBackup(context, uri, db, appPreferences)
    }

    // ─── Transactions Flow & Queries ──────────────────────────────────────────
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allTransactionsWithSplits: Flow<List<TransactionWithSplits>> = transactionDao.getAllTransactionsWithSplits()
    val totalMyExpense: Flow<Double?> = transactionDao.getTotalMyExpense()
    val totalGrossExpense: Flow<Double?> = transactionDao.getTotalGrossExpense()
    val totalIncome: Flow<Double?> = transactionDao.getTotalIncome()

    fun getTransactionWithSplits(id: Long): Flow<TransactionWithSplits?> =
        transactionDao.getTransactionWithSplits(id)

    // ─── Categories ───────────────────────────────────────────────────────────
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val categorySpending: Flow<List<CategorySpending>> = categoryDao.getCategorySpending()

    suspend fun addCategory(category: CategoryEntity): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(category)
    }

    // ─── Vendor Mappings ──────────────────────────────────────────────────────
    val allVendorMappings: Flow<List<VendorMappingEntity>> = vendorDao.getAllVendorMappings()

    suspend fun addVendorMapping(mapping: VendorMappingEntity): Long = withContext(Dispatchers.IO) {
        vendorDao.insertVendorMapping(mapping)
    }

    suspend fun updateVendorMapping(mapping: VendorMappingEntity) = withContext(Dispatchers.IO) {
        vendorDao.updateVendorMapping(mapping)
    }

    suspend fun deleteVendorMapping(mapping: VendorMappingEntity) = withContext(Dispatchers.IO) {
        vendorDao.deleteVendorMapping(mapping)
    }

    /**
     * Resolves friendly vendor name and category from raw merchant SMS text
     */
    suspend fun resolveVendor(rawMerchant: String): Pair<String, Long?> = withContext(Dispatchers.IO) {
        val mappings = vendorDao.getAllVendorMappingsList()
        val rawLower = rawMerchant.lowercase()
        for (m in mappings) {
            if (rawLower.contains(m.rawSmsKeyword.lowercase())) {
                return@withContext Pair(m.friendlyName, m.categoryId)
            }
        }
        Pair(rawMerchant, null)
    }

    // ─── Persons & Strict Dynamic Balances ────────────────────────────────────
    val personsWithBalances: Flow<List<PersonWithBalance>> = personDao.getPersonsWithBalances()
    val allPersons: Flow<List<PersonEntity>> = personDao.getAllPersons()

    suspend fun addPerson(person: PersonEntity): Long = withContext(Dispatchers.IO) {
        personDao.insertPerson(person)
    }

    suspend fun updatePerson(person: PersonEntity) = withContext(Dispatchers.IO) {
        personDao.updatePerson(person)
    }

    suspend fun deletePerson(person: PersonEntity) = withContext(Dispatchers.IO) {
        personDao.deletePerson(person)
    }

    suspend fun getAllPersonsList(): List<PersonEntity> = withContext(Dispatchers.IO) {
        personDao.getAllPersonsList()
    }

    suspend fun isTransactionDuplicate(upiRef: String): Boolean = withContext(Dispatchers.IO) {
        transactionDao.findByUpiRef(upiRef) != null
    }

    suspend fun toggleCcBillTransfer(transactionId: Long) = withContext(Dispatchers.IO) {
        val existing = transactionDao.getTransactionByIdSync(transactionId) ?: return@withContext
        val newType = if (existing.type == TransactionType.CC_BILL_TRANSFER) {
            TransactionType.EXPENSE
        } else {
            TransactionType.CC_BILL_TRANSFER
        }
        transactionDao.updateTransaction(existing.copy(type = newType))
    }

    // ─── Transaction CRUD & Clean Split Recalculation ─────────────────────────

    /**
     * Inserts a transaction and optionally any split items, automatically calculating myActualShare.
     */
    suspend fun addTransaction(
        transaction: TransactionEntity,
        splits: List<SplitItemEntity> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val totalSplitOwed = splits.sumOf { it.amountOwed }
        val adjustedShare = if (transaction.type == TransactionType.EXPENSE) {
            (transaction.amount - totalSplitOwed).coerceAtLeast(0.0)
        } else {
            transaction.amount
        }

        val finalizedTx = transaction.copy(myActualShare = adjustedShare)
        val txId = transactionDao.insertTransaction(finalizedTx)

        if (splits.isNotEmpty()) {
            val splitsWithTxId = splits.map { it.copy(transactionId = txId) }
            splitDao.insertSplitItems(splitsWithTxId)
        }

        // Evaluate Budget Thresholds (50, 70, 90, 100) if expense
        if (finalizedTx.type == TransactionType.EXPENSE && context != null) {
            BudgetAlertEvaluator.evaluateBudgetAlerts(context, db, appPreferences, finalizedTx.categoryId)
        }

        txId
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.updateTransaction(transaction)
    }

    /**
     * Updates transaction details and recalculates splits cleanly.
     */
    suspend fun editTransactionWithSplits(
        updatedTx: TransactionEntity,
        newSplits: List<SplitItemEntity>
    ) = withContext(Dispatchers.IO) {
        val totalSplitOwed = newSplits.sumOf { it.amountOwed }
        val adjustedShare = if (updatedTx.type == TransactionType.EXPENSE) {
            (updatedTx.amount - totalSplitOwed).coerceAtLeast(0.0)
        } else {
            updatedTx.amount
        }

        val finalizedTx = updatedTx.copy(myActualShare = adjustedShare)
        transactionDao.updateTransaction(finalizedTx)

        // Replace splits for this transaction
        splitDao.deleteSplitsForTransaction(updatedTx.id)
        if (newSplits.isNotEmpty()) {
            val splitsWithId = newSplits.map { it.copy(transactionId = updatedTx.id) }
            splitDao.insertSplitItems(splitsWithId)
        }

        // Re-evaluate Budget Thresholds on split adjustment
        if (finalizedTx.type == TransactionType.EXPENSE && context != null) {
            BudgetAlertEvaluator.evaluateBudgetAlerts(context, db, appPreferences, finalizedTx.categoryId)
        }
    }

    /**
     * Deletes a transaction. Cascade in Room automatically deletes all associated split items.
     */
    suspend fun deleteTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionById(id)
    }

    // ─── Dynamic Balance & Split Queries ─────────────────────────────────────
    fun getNetPendingBalanceForPerson(personId: Long): Flow<Double> =
        splitDao.getNetPendingBalanceForPerson(personId)

    fun getUnsettledBillsForPerson(personId: Long): Flow<List<com.campusfold.tracker.data.model.UnsettledBillItem>> =
        splitDao.getUnsettledBillsForPerson(personId)

    val allUnsettledBills: Flow<List<com.campusfold.tracker.data.model.UnsettledBillItem>> =
        splitDao.getAllUnsettledBills()

    // ─── FIFO Settlement Engine (Partial, Full & Overpayments) ───────────────

    /**
     * Settles amount X for a friend using strict FIFO (oldest split items first).
     * Handles partial payments (isSettled = false), full settlement (isSettled = true),
     * and excess overpayment.
     */
    suspend fun settleAmountForPerson(personId: Long, amountToSettle: Double): com.campusfold.tracker.data.model.SettlementResult = withContext(Dispatchers.IO) {
        val person = personDao.getPersonById(personId)
        val friendName = person?.name ?: "Friend"

        // Fetch unsettled rows ordered by createdAt ASC (FIFO)
        val unsettledRows = splitDao.getUnsettledSplitsForPersonSync(personId)
        val totalOwedBefore = unsettledRows.sumOf { it.amountOwed - it.amountPaidSoFar }

        var remainingX = amountToSettle
        var updatedCount = 0

        for (row in unsettledRows) {
            if (remainingX <= 0.0) break
            val rowRemaining = row.amountOwed - row.amountPaidSoFar

            if (remainingX < rowRemaining) {
                // Partial settlement
                val newPaid = row.amountPaidSoFar + remainingX
                splitDao.updateSplitItem(row.copy(amountPaidSoFar = newPaid, isSettled = false))
                remainingX = 0.0
                updatedCount++
            } else {
                // Full settlement of this row, carry remainder to next
                splitDao.updateSplitItem(row.copy(amountPaidSoFar = row.amountOwed, isSettled = true))
                remainingX -= rowRemaining
                updatedCount++
            }
        }

        val excess = if (remainingX > 0.0) remainingX else 0.0
        val newRemainingOwed = (totalOwedBefore - amountToSettle).coerceAtLeast(0.0)
        val actuallySettled = minOf(amountToSettle, totalOwedBefore)

        com.campusfold.tracker.data.model.SettlementResult(
            friendName = friendName,
            settledAmount = actuallySettled,
            remainingOwed = newRemainingOwed,
            excessAmount = excess,
            updatedSplitCount = updatedCount
        )
    }

    suspend fun recordSplitPayment(splitId: Long, paymentAmount: Double) = withContext(Dispatchers.IO) {
        splitDao.recordPayment(splitId, paymentAmount)
    }

    suspend fun markSplitSettled(splitId: Long, isSettled: Boolean) = withContext(Dispatchers.IO) {
        splitDao.markSettled(splitId, isSettled)
    }

    suspend fun logOverpaymentAsIncome(excessAmount: Double, friendName: String): Long = withContext(Dispatchers.IO) {
        val tx = TransactionEntity(
            amount = excessAmount,
            myActualShare = excessAmount,
            type = TransactionType.INCOME,
            paymentMode = "Cash/Manual",
            rawMerchant = "Advance from $friendName",
            friendlyVendorName = "Advance from $friendName",
            categoryId = null,
            timestamp = System.currentTimeMillis()
        )
        transactionDao.insertTransaction(tx)
    }

    // ─── Pending Settlement Suggestions & 1-Tap Alias Saving ──────────────────
    val pendingSuggestions: Flow<List<PendingSettlementSuggestionEntity>> = suggestionDao.getPendingSuggestions()

    suspend fun createSettlementSuggestion(suggestion: PendingSettlementSuggestionEntity): Long = withContext(Dispatchers.IO) {
        suggestionDao.insertSuggestion(suggestion)
    }

    /**
     * 1-Tap 'Confirm & Save Alias' button logic:
     * Settles the balance via FIFO, saves the detected SMS name to exactSmsAliases for future auto-settlement,
     * marks suggestion CONFIRMED, and updates transaction to SPLIT_SETTLEMENT.
     */
    suspend fun confirmAndSaveAlias(suggestionId: Long): com.campusfold.tracker.data.model.SettlementResult? = withContext(Dispatchers.IO) {
        val suggestion = suggestionDao.getSuggestionById(suggestionId) ?: return@withContext null
        val person = personDao.getPersonById(suggestion.suggestedPersonId) ?: return@withContext null

        // 1. Run FIFO settlement
        val result = settleAmountForPerson(person.id, suggestion.amount)

        // 2. Add detectedSenderName to exactSmsAliases for future auto-settlement
        val existingAliases = person.exactSmsAliases.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (!existingAliases.any { it.equals(suggestion.detectedSenderName.trim(), ignoreCase = true) }) {
            existingAliases.add(suggestion.detectedSenderName.trim())
            val updatedString = existingAliases.joinToString(", ")
            personDao.updatePerson(person.copy(exactSmsAliases = updatedString))
        }

        // 3. Mark suggestion CONFIRMED
        suggestionDao.updateStatus(suggestionId, SettlementSuggestionStatus.CONFIRMED)

        // 4. Update transaction type to SPLIT_SETTLEMENT if transaction exists
        suggestion.transactionId?.let { txId ->
            val tx = transactionDao.getTransactionByIdSync(txId)
            if (tx != null) {
                transactionDao.updateTransaction(
                    tx.copy(
                        type = TransactionType.SPLIT_SETTLEMENT,
                        friendlyVendorName = "Settlement from ${person.name}"
                    )
                )
            }
        }

        result
    }

    suspend fun dismissSettlementSuggestion(suggestionId: Long) = withContext(Dispatchers.IO) {
        suggestionDao.updateStatus(suggestionId, SettlementSuggestionStatus.DISMISSED)
    }

    // ─── Preferences & Budgets ───────────────────────────────────────────────
    val latestPnbCreditLimit: Flow<Double> = appPreferences.latestPnbCreditLimit
    val lastSmsSyncTimestamp: Flow<Long> = appPreferences.lastSmsSyncTimestamp
    val overallMonthlyBudget: Flow<Double> = appPreferences.overallMonthlyBudget

    suspend fun setPnbCreditLimit(limit: Double) = appPreferences.setPnbCreditLimit(limit)
    suspend fun setLastSmsSyncTimestamp(timestamp: Long) = appPreferences.setLastSmsSyncTimestamp(timestamp)
    suspend fun setOverallMonthlyBudget(budget: Double) = appPreferences.setOverallMonthlyBudget(budget)

    suspend fun updateCategoryBudget(categoryId: Long, budget: Double) = withContext(Dispatchers.IO) {
        val cat = categoryDao.getCategoryById(categoryId) ?: return@withContext
        categoryDao.updateCategory(cat.copy(monthlyBudget = budget))
        context?.let { ctx ->
            BudgetAlertEvaluator.evaluateBudgetAlerts(ctx, db, appPreferences, categoryId)
        }
    }

    // ─── True Personal Spend Analytics (type = 'EXPENSE' only) ────────────────

    fun getCurrentMonthCategorySpending(): Flow<List<CategorySpending>> {
        val (start, end) = BudgetAlertEvaluator.getMonthTimeBounds()
        return categoryDao.getCategorySpendingForMonth(start, end)
    }

    fun getCurrentMonthVendorSpending(): Flow<List<VendorSpendingItem>> {
        val (start, end) = BudgetAlertEvaluator.getMonthTimeBounds()
        return transactionDao.getVendorSpendingBetween(start, end)
    }

    fun getCurrentMonthTotalTrueSpend(): Flow<Double> {
        val (start, end) = BudgetAlertEvaluator.getMonthTimeBounds()
        return transactionDao.getMonthTotalMyExpenseFlow(start, end).map { it ?: 0.0 }
    }

    // ─── 'Where to Control' Actionable Insights ──────────────────────────────

    /**
     * a) Burn Rate Warning: Any category where % of budget spent is significantly ahead of % of days elapsed.
     */
    fun getBurnRateWarnings(): Flow<List<BurnRateInsight>> {
        val (start, end) = BudgetAlertEvaluator.getMonthTimeBounds()
        return categoryDao.getCategorySpendingForMonth(start, end).map { list ->
            val calendar = Calendar.getInstance()
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val daysElapsedPercent = (dayOfMonth.toDouble() / totalDays.toDouble()) * 100.0

            list.filter { it.monthlyBudget > 0.0 }
                .mapNotNull { cat ->
                    val spentPercent = (cat.totalSpent / cat.monthlyBudget) * 100.0
                    val pacingAhead = spentPercent - daysElapsedPercent
                    if (pacingAhead > 5.0) {
                        BurnRateInsight(
                            categoryName = cat.categoryName,
                            colorHex = cat.colorHex,
                            spent = cat.totalSpent,
                            budget = cat.monthlyBudget,
                            spentPercent = spentPercent,
                            daysElapsedPercent = daysElapsedPercent,
                            pacingAheadPercent = pacingAhead
                        )
                    } else null
                }.sortedByDescending { it.pacingAheadPercent }
        }
    }

    /**
     * b) High-Frequency Leaks: Vendors with >5 transactions in the current week.
     */
    val highFrequencyLeaks: Flow<List<HighFrequencyLeak>>
        get() {
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            return transactionDao.getHighFrequencyLeaksFlow(sevenDaysAgo).map { list ->
                list.map {
                    HighFrequencyLeak(
                        vendorName = it.vendorName,
                        transactionCount = it.transactionCount,
                        totalSpent = it.totalSpent
                    )
                }
            }
        }

    /**
     * c) Week-over-Week Spike: Category with the highest ₹ increase compared to the previous 7 days.
     */
    suspend fun calculateWeekOverWeekSpike(): WeekOverWeekSpike? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val weekMs = 7 * 24 * 60 * 60 * 1000L
        val currentWeekStart = now - weekMs
        val previousWeekStart = now - 2 * weekMs

        val currentWeekSpending = categoryDao.getCategorySpendBetweenSync(currentWeekStart, now)
        val previousWeekSpending = categoryDao.getCategorySpendBetweenSync(previousWeekStart, currentWeekStart)

        val previousMap = previousWeekSpending.associateBy { it.categoryId }

        val spikes = currentWeekSpending.map { curr ->
            val prevAmount = previousMap[curr.categoryId]?.totalSpent ?: 0.0
            val diff = curr.totalSpent - prevAmount
            val pct = if (prevAmount > 0.0) (diff / prevAmount) * 100.0 else if (curr.totalSpent > 0.0) 100.0 else 0.0
            WeekOverWeekSpike(
                categoryName = curr.categoryName,
                colorHex = curr.colorHex,
                currentWeekSpend = curr.totalSpent,
                previousWeekSpend = prevAmount,
                increaseAmount = diff,
                percentIncrease = pct
            )
        }.filter { it.increaseAmount > 0.0 }

        spikes.maxByOrNull { it.increaseAmount }
    }

    /**
     * Fallback safe-guard to ensure pre-seeded categories & mappings exist even if DB already existed.
     */
    suspend fun ensurePreseededData() = withContext(Dispatchers.IO) {
        val existingCategories = categoryDao.getAllCategoriesList()
        if (existingCategories.isEmpty()) {
            val defaults = listOf(
                CategoryEntity(1, "Food", "#10B981", 5000.0),
                CategoryEntity(2, "Chai & Smoke", "#F59E0B", 2000.0),
                CategoryEntity(3, "Beverages", "#06B6D4", 1500.0),
                CategoryEntity(4, "Lifestyle", "#8B5CF6", 3000.0),
                CategoryEntity(5, "Utilities", "#3B82F6", 1500.0),
                CategoryEntity(6, "Clothing", "#EC4899", 2500.0)
            )
            categoryDao.insertCategories(defaults)
        }

        val existingMappings = vendorDao.getAllVendorMappingsList()
        if (existingMappings.isEmpty()) {
            val defaultMappings = listOf(
                VendorMappingEntity(1, "Sekar store", "Akka shop", 2),
                VendorMappingEntity(2, "KARTHIK", "Bistro", 1),
                VendorMappingEntity(3, "CHAATTERBOX", "Chaaterbox", 1),
                VendorMappingEntity(4, "Mohan", "Chennai beverage", 3),
                VendorMappingEntity(5, "HungerBox", "Hungerbox", 1),
                VendorMappingEntity(6, "DD STORE", "Utility store", 5)
            )
            vendorDao.insertVendorMappings(defaultMappings)
        }

        val existingAccounts = accountDao.getAllAccountsSync()
        if (existingAccounts.isEmpty()) {
            val defaultAccounts = listOf(
                com.campusfold.tracker.data.entity.AccountEntity(1, "PNB Credit Card 8598", "PNB", "CREDIT_CARD", "8598", 59396.93, "#F43F5E"),
                com.campusfold.tracker.data.entity.AccountEntity(2, "Axis Bank XX2003", "Axis Bank", "SAVINGS", "2003", 0.0, "#8B5CF6"),
                com.campusfold.tracker.data.entity.AccountEntity(3, "Federal (Jupiter) 2548", "Federal Bank", "SAVINGS", "2548", 0.0, "#F59E0B"),
                com.campusfold.tracker.data.entity.AccountEntity(4, "PNB Savings Account 5557", "PNB", "SAVINGS", "5557", 0.0, "#06B6D4"),
                com.campusfold.tracker.data.entity.AccountEntity(5, "Axis Bank Savings Account 5476", "Axis Bank", "SAVINGS", "5476", 0.0, "#3B82F6"),
                com.campusfold.tracker.data.entity.AccountEntity(6, "Axis Bank Credit Card", "Axis Bank", "CREDIT_CARD", "Axis CC", 0.0, "#EC4899")
            )
            accountDao.insertAccounts(defaultAccounts)
        }
    }
}
