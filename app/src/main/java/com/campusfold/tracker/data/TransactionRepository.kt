package com.campusfold.tracker.data

import android.content.Context
import androidx.room.withTransaction
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.model.*
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.util.AxisBankEmailParser
import com.campusfold.tracker.util.BudgetAlertEvaluator
import com.campusfold.tracker.util.CurrencyUtils
import com.campusfold.tracker.util.GmailImapClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar

class TransactionRepository(
    private val db: AppDatabase,
    private val appPreferences: AppPreferences,
    private val context: Context? = null
) {

    private val transactionDao: TransactionDao = db.transactionDao()
    private val categoryDao: CategoryDao = db.categoryDao()
    private val vendorDao: VendorDao = db.vendorDao()
    private val personDao: PersonDao = db.personDao()
    private val splitDao: SplitItemDao = db.splitItemDao()
    private val suggestionDao: PendingSettlementSuggestionDao = db.pendingSettlementSuggestionDao()
    private val accountDao: AccountDao = db.accountDao()
    private val accountAliasDao: AccountAliasDao = db.accountAliasDao()
    private val mergeLogDao: MergeLogDao = db.mergeLogDao()
    private val notSamePairDao: NotSamePairDao = db.notSamePairDao()
    private val ruleDao: RuleDao = db.ruleDao()
    private val reviewItemDao: ReviewItemDao = db.reviewItemDao()
    private val unparsedMessageDao: UnparsedMessageDao = db.unparsedMessageDao()
    private val processedMessageDao: ProcessedMessageDao = db.processedMessageDao()
    private val aiCacheDao: AiCacheDao = db.aiCacheDao()
    private val scanHistoryDao: ScanHistoryDao = db.scanHistoryDao()

    val accountMergeManager = com.campusfold.tracker.data.merge.AccountMergeManager(
        db = db,
        accountDao = accountDao,
        accountAliasDao = accountAliasDao,
        mergeLogDao = mergeLogDao,
        notSamePairDao = notSamePairDao,
        transactionDao = transactionDao,
        aiFallbackService = com.campusfold.tracker.ai.AiFallbackService(aiCacheDao, appPreferences)
    )

    // ─── Accounts & Merge Management ──────────────────────────────────────────
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val latestAutoMergeLog: Flow<MergeLogEntity?> = mergeLogDao.getLatestActiveAutoMergeLogFlow()
    val allActiveMergeLogs: Flow<List<MergeLogEntity>> = mergeLogDao.getAllActiveMergeLogs()

    suspend fun getAllAccountsList(): List<AccountEntity> = withContext(Dispatchers.IO) {
        accountDao.getAllAccountsSync()
    }

    suspend fun getActiveMergeLogsForAccount(accountId: Long): List<MergeLogEntity> = withContext(Dispatchers.IO) {
        mergeLogDao.getActiveMergeLogsForAccountSync(accountId)
    }

    suspend fun runAutoMergePass(): List<MergeLogEntity> = withContext(Dispatchers.IO) {
        accountMergeManager.runAutoMergePass()
    }

    suspend fun detectDuplicateAccounts(): Pair<List<com.campusfold.tracker.data.merge.DuplicatePair>, List<com.campusfold.tracker.data.merge.DuplicatePair>> = withContext(Dispatchers.IO) {
        accountMergeManager.detectDuplicates()
    }

    suspend fun mergeAccounts(
        survivingAccountId: Long,
        mergedAccountId: Long,
        mode: MergeMode = MergeMode.MANUAL,
        overrideDisplayName: String? = null,
        overrideType: AccountType? = null,
        overrideLast4: String? = null
    ): Long = withContext(Dispatchers.IO) {
        accountMergeManager.mergeAccounts(
            survivingAccountId = survivingAccountId,
            mergedAccountId = mergedAccountId,
            mode = mode,
            overrideDisplayName = overrideDisplayName,
            overrideType = overrideType,
            overrideLast4 = overrideLast4
        )
    }

    suspend fun undoMerge(mergeLogId: Long): Boolean = withContext(Dispatchers.IO) {
        accountMergeManager.undoMerge(mergeLogId)
    }

    suspend fun markPairNotSame(accountIdA: Long, accountIdB: Long) = withContext(Dispatchers.IO) {
        accountMergeManager.markNotSame(accountIdA, accountIdB)
    }

    suspend fun countTransactionsForAccount(accountId: Long): Int = withContext(Dispatchers.IO) {
        transactionDao.countTransactionsByAccountId(accountId)
    }

    suspend fun getRecentTransactionsList(limit: Int = 20): List<TransactionEntity> = withContext(Dispatchers.IO) {
        transactionDao.getRecentTransactionsSync(limit)
    }

    suspend fun updateAccountBalance(accountId: Long, balancePaise: Long, timestamp: Long, source: com.campusfold.tracker.data.entity.BalanceSource = com.campusfold.tracker.data.entity.BalanceSource.AUTO) = withContext(Dispatchers.IO) {
        accountDao.updateBalance(accountId, balancePaise, timestamp, source)
    }

    suspend fun addAccount(account: AccountEntity): Long = withContext(Dispatchers.IO) {
        val id = accountDao.insertAccount(account)
        try {
            accountMergeManager.runAutoMergePass()
        } catch (e: Exception) {
            android.util.Log.w("TransactionRepository", "Auto-merge duplicate check failed after addAccount: ${e.message}")
        }
        id
    }

    suspend fun updateAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.deleteAccount(account)
    }

    suspend fun deleteAccountWithReassign(accountId: Long, targetAccountId: Long?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val count = transactionDao.countTransactionsByAccountId(accountId)
            if (count > 0) {
                if (targetAccountId != null) {
                    transactionDao.updateAccountIdForTransactions(accountId, targetAccountId)
                } else {
                    transactionDao.unassignAccountForTransactions(accountId)
                }
            }
            if (targetAccountId != null) {
                accountAliasDao.reassignAliases(accountId, targetAccountId)
            } else {
                accountAliasDao.deleteAliasesForAccount(accountId)
            }
            accountDao.deleteAccountById(accountId)
        }
    }

    suspend fun setAccountArchived(accountId: Long, isArchived: Boolean) = withContext(Dispatchers.IO) {
        accountDao.setArchived(accountId, isArchived)
    }

    suspend fun setAccountBalanceManually(accountId: Long, balancePaise: Long) = withContext(Dispatchers.IO) {
        accountDao.updateBalance(accountId, balancePaise, System.currentTimeMillis(), BalanceSource.MANUAL)
    }

    suspend fun clearManualBalanceOverride(accountId: Long) = withContext(Dispatchers.IO) {
        val acc = accountDao.getAccountById(accountId) ?: return@withContext
        accountDao.updateAccount(acc.copy(balanceSource = BalanceSource.AUTO))
    }

    suspend fun updateAccountAnchor(
        accountId: Long,
        balancePaise: Long,
        owedPaise: Long,
        asOf: Long,
        source: BalanceSource = BalanceSource.MANUAL
    ) = withContext(Dispatchers.IO) {
        accountDao.updateAnchor(accountId, balancePaise, owedPaise, asOf, source)
    }

    // ─── Manual Transaction Control ───────────────────────────────────────────
    suspend fun bulkDeleteTransactions(ids: List<Long>): List<TransactionEntity> = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = transactionDao.getTransactionsByIdsSync(ids)
            transactionDao.deleteTransactionsByIds(ids)
            existing
        }
    }

    suspend fun restoreTransactions(transactions: List<TransactionEntity>) = withContext(Dispatchers.IO) {
        transactionDao.restoreTransactions(transactions)
    }

    suspend fun bulkRecategorizeTransactions(ids: List<Long>, categoryId: Long?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.updateCategoryIdForTransactionIds(ids, categoryId)
            val txs = transactionDao.getTransactionsByIdsSync(ids)
            for (tx in txs) {
                val editedSet = parseEditedFields(tx.editedFields).toMutableSet()
                editedSet.add("categoryId")
                transactionDao.updateTransaction(tx.copy(editedFields = JSONArray(editedSet.toList()).toString()))
            }
        }
    }

    suspend fun bulkMoveTransactionsToAccount(ids: List<Long>, targetAccountId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val targetAcc = accountDao.getAccountById(targetAccountId) ?: return@withTransaction
            transactionDao.updateAccountIdForTransactionIds(ids, targetAccountId)
            val txs = transactionDao.getTransactionsByIdsSync(ids)
            for (tx in txs) {
                val editedSet = parseEditedFields(tx.editedFields).toMutableSet()
                editedSet.add("accountId")
                editedSet.add("paymentMode")
                transactionDao.updateTransaction(
                    tx.copy(
                        paymentMode = targetAcc.displayName,
                        editedFields = JSONArray(editedSet.toList()).toString()
                    )
                )
            }
        }
    }

    suspend fun mergeDuplicateTransactions(survivingTxId: Long, duplicateTxId: Long): TransactionEntity = withContext(Dispatchers.IO) {
        db.withTransaction {
            val survivor = transactionDao.getTransactionByIdSync(survivingTxId)
                ?: throw IllegalArgumentException("Survivor transaction not found: $survivingTxId")
            val duplicate = transactionDao.getTransactionByIdSync(duplicateTxId)
                ?: throw IllegalArgumentException("Duplicate transaction not found: $duplicateTxId")

            // Reassign any splits from duplicate to survivor
            splitDao.reassignSplits(duplicateTxId, survivingTxId)

            // Combine descriptions
            val combinedDesc = when {
                survivor.description.isNullOrBlank() -> duplicate.description
                duplicate.description.isNullOrBlank() -> survivor.description
                survivor.description == duplicate.description -> survivor.description
                else -> "${survivor.description} | ${duplicate.description}"
            }

            // Combine references
            val finalRef = if (!survivor.reference.isNullOrBlank()) survivor.reference else duplicate.reference
            val finalUpi = if (!survivor.upiRef.isNullOrBlank()) survivor.upiRef else duplicate.upiRef

            // Combine sticky edits
            val survivorEdited = parseEditedFields(survivor.editedFields).toMutableSet()
            survivorEdited.addAll(parseEditedFields(duplicate.editedFields))

            val updated = survivor.copy(
                description = combinedDesc,
                reference = finalRef,
                upiRef = finalUpi,
                editedFields = JSONArray(survivorEdited.toList()).toString()
            )
            transactionDao.updateTransaction(updated)
            transactionDao.deleteTransaction(duplicate)
            updated
        }
    }

    // ─── Manual Vendor Control ────────────────────────────────────────────────
    suspend fun renameVendor(vendorId: Long, newName: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val vendor = vendorDao.getVendorById(vendorId) ?: return@withTransaction
            val oldName = vendor.displayName
            val edited = parseEditedFields(vendor.editedFields).toMutableSet().apply { add("displayName") }
            vendorDao.updateVendor(
                vendor.copy(
                    displayName = newName.trim(),
                    normalizedKey = newName.trim().lowercase(java.util.Locale.US),
                    editedFields = JSONArray(edited.toList()).toString()
                )
            )
            transactionDao.updateVendorNameForTransactions(oldName, newName.trim())
        }
    }

    suspend fun reassignVendorCategory(vendorId: Long, newCategoryId: Long?) = withContext(Dispatchers.IO) {
        val vendor = vendorDao.getVendorById(vendorId) ?: return@withContext
        val edited = parseEditedFields(vendor.editedFields).toMutableSet().apply { add("categoryId") }
        vendorDao.updateVendor(
            vendor.copy(
                categoryId = newCategoryId,
                editedFields = JSONArray(edited.toList()).toString()
            )
        )
    }

    suspend fun mergeVendors(survivingVendorId: Long, mergedVendorId: Long, survivingName: String, categoryId: Long?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val surviving = vendorDao.getVendorById(survivingVendorId) ?: return@withTransaction
            val merged = vendorDao.getVendorById(mergedVendorId) ?: return@withTransaction

            // Repoint transactions
            transactionDao.updateVendorNameAndCategoryForTransactions(merged.displayName, survivingName, categoryId)
            transactionDao.updateVendorNameAndCategoryForTransactions(surviving.displayName, survivingName, categoryId)

            val edited = parseEditedFields(surviving.editedFields).toMutableSet().apply {
                add("displayName")
                if (categoryId != null) add("categoryId")
            }

            vendorDao.updateVendor(
                surviving.copy(
                    displayName = survivingName.trim(),
                    normalizedKey = survivingName.trim().lowercase(java.util.Locale.US),
                    categoryId = categoryId ?: surviving.categoryId,
                    editedFields = JSONArray(edited.toList()).toString()
                )
            )
            vendorDao.deleteVendor(merged)
        }
    }

    // ─── Manual Category Control ──────────────────────────────────────────────
    suspend fun deleteCategoryWithReassign(categoryId: Long, targetCategoryId: Long?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.updateCategoryIdForTransactions(categoryId, targetCategoryId)
            vendorDao.updateCategoryIdForVendors(categoryId, targetCategoryId)
            categoryDao.deleteCategoryById(categoryId)
        }
    }

    suspend fun mergeCategories(survivingCategoryId: Long, mergedCategoryId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.updateCategoryIdForTransactions(mergedCategoryId, survivingCategoryId)
            vendorDao.updateCategoryIdForVendors(mergedCategoryId, survivingCategoryId)
            categoryDao.deleteCategoryById(mergedCategoryId)
        }
    }

    suspend fun setCategoryBudget(categoryId: Long, budgetPaise: Long) = withContext(Dispatchers.IO) {
        val cat = categoryDao.getCategoryById(categoryId) ?: return@withContext
        val edited = parseEditedFields(cat.editedFields).toMutableSet().apply { add("budgetPaise") }
        categoryDao.updateCategory(
            cat.copy(
                budgetPaise = if (budgetPaise > 0L) budgetPaise else null,
                editedFields = JSONArray(edited.toList()).toString()
            )
        )
    }

    // ─── Review Queue Batch Operations ────────────────────────────────────────
    suspend fun acceptAllHighConfidenceReviewItems(threshold: Float = 0.85f): Int = withContext(Dispatchers.IO) {
        val pending = reviewItemDao.getPendingReviewItemsSync()
        var count = 0
        for (item in pending) {
            val conf = item.confidence ?: 1.0f
            if (conf >= threshold || item.origin != ReviewOrigin.AI) {
                try {
                    acceptReviewItem(item.id)
                    count++
                } catch (_: Exception) {}
            }
        }
        count
    }

    suspend fun discardAllPendingReviewItems() = withContext(Dispatchers.IO) {
        reviewItemDao.discardAllPending()
    }

    // ─── Unparsed Messages & Scan History & Rebuild ───────────────────────────
    val allUnparsedMessages: Flow<List<UnparsedMessageEntity>> = unparsedMessageDao.getAllUnparsedMessages()
    val allScanHistory: Flow<List<ScanHistoryEntity>> = scanHistoryDao.getAllScanHistory()

    suspend fun deleteUnparsedMessage(id: Long) = withContext(Dispatchers.IO) {
        unparsedMessageDao.deleteById(id)
    }

    suspend fun rebuildFromScratch() = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.clearAll()
            splitDao.clearAll()
            suggestionDao.clearAll()
            processedMessageDao.clearAll()
            unparsedMessageDao.clearAll()
            scanHistoryDao.clearAll()
        }
        appPreferences.setLastSmsSyncTimestamp(0L)
        appPreferences.setGmailLastSyncTimestamp(0L)
        appPreferences.resetGmailUidSyncState()
    }

    suspend fun clearAllTransactionsAndReset() = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.clearAll()
            splitDao.clearAll()
            suggestionDao.clearAll()
        }
    }

    suspend fun clearTransactionsOnly() = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.clearAll()
            splitDao.clearAll()
            suggestionDao.clearAll()
            reviewItemDao.clearAll()
            unparsedMessageDao.clearAll()
            processedMessageDao.clearAll()
            scanHistoryDao.clearAll()
        }
        appPreferences.setLastSmsSyncTimestamp(0L)
        appPreferences.setGmailLastSyncTimestamp(0L)
        appPreferences.resetGmailUidSyncState()
    }

    suspend fun resetEverything() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.clearAllTables()
        }
        appPreferences.clearAllPreferences()
        ensurePreseededData()
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

    val totalMyExpensePaise: Flow<Long?> = transactionDao.getTotalMyExpensePaise()
    val totalGrossExpensePaise: Flow<Long?> = transactionDao.getTotalGrossExpensePaise()
    val totalIncomePaise: Flow<Long?> = transactionDao.getTotalIncomePaise()

    // ─── Derived Account Balances & Net Available Balance (Part 6) ─────────────

    fun computeDerivedAccountBalance(
        account: AccountEntity,
        transactions: List<TransactionEntity>
    ): DerivedAccountBalance {
        val txsSinceAnchor = transactions.filter { it.accountId == account.id && it.timestamp >= account.openingAsOf }

        if (account.type == AccountType.CREDIT_CARD) {
            val increases = txsSinceAnchor.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.EMI }.sumOf { it.amountPaise }
            val decreases = txsSinceAnchor.filter {
                it.type == TransactionType.CC_BILL_TRANSFER ||
                it.type == TransactionType.REFUND ||
                it.type == TransactionType.INCOME
            }.sumOf { it.amountPaise }

            val derivedOwed = maxOf(0L, account.openingOwedPaise + increases - decreases)
            val availableCredit = if (account.creditLimitPaise > 0L) maxOf(0L, account.creditLimitPaise - derivedOwed) else 0L

            val hasDiscrepancy = account.anchorSet && account.balanceSource == BalanceSource.AUTO &&
                    account.balancePaise > 0L && account.balancePaise != derivedOwed
            val discrepancyBankBalance = if (hasDiscrepancy) account.balancePaise else null

            return DerivedAccountBalance(
                account = account,
                derivedBalancePaise = 0L,
                derivedOwedPaise = derivedOwed,
                availableCreditPaise = availableCredit,
                hasDiscrepancy = hasDiscrepancy,
                discrepancyBankBalancePaise = discrepancyBankBalance
            )
        } else {
            val credits = txsSinceAnchor.filter {
                it.type == TransactionType.INCOME ||
                it.type == TransactionType.REFUND ||
                (it.type == TransactionType.SPLIT_SETTLEMENT && (it.friendlyVendorName.contains("Settlement from", ignoreCase = true) || it.rawMerchant.contains("Credit", ignoreCase = true))) ||
                (it.type == TransactionType.OWN_TRANSFER && (it.friendlyVendorName.contains("from", ignoreCase = true) || it.rawMerchant.contains("Inflow", ignoreCase = true) || it.description?.contains("INCOMING", ignoreCase = true) == true))
            }.sumOf { it.amountPaise }

            val debits = txsSinceAnchor.filter {
                it.type == TransactionType.EXPENSE ||
                it.type == TransactionType.ATM ||
                it.type == TransactionType.EMI ||
                it.type == TransactionType.CC_BILL_TRANSFER ||
                (it.type == TransactionType.SPLIT_SETTLEMENT && !it.friendlyVendorName.contains("Settlement from", ignoreCase = true)) ||
                (it.type == TransactionType.OWN_TRANSFER && !it.friendlyVendorName.contains("from", ignoreCase = true) && !it.rawMerchant.contains("Inflow", ignoreCase = true) && it.description?.contains("INCOMING", ignoreCase = true) != true)
            }.sumOf { it.amountPaise }

            val derivedBalance = account.openingBalancePaise + credits - debits

            val hasDiscrepancy = account.anchorSet && account.balanceSource == BalanceSource.AUTO &&
                    account.balancePaise > 0L && account.balancePaise != derivedBalance
            val discrepancyBankBalance = if (hasDiscrepancy) account.balancePaise else null

            return DerivedAccountBalance(
                account = account,
                derivedBalancePaise = derivedBalance,
                derivedOwedPaise = 0L,
                availableCreditPaise = 0L,
                hasDiscrepancy = hasDiscrepancy,
                discrepancyBankBalancePaise = discrepancyBankBalance
            )
        }
    }

    val derivedAccountBalances: Flow<List<DerivedAccountBalance>> = combine(
        allAccounts,
        allTransactions
    ) { accounts, txs ->
        accounts.map { computeDerivedAccountBalance(it, txs) }
    }

    val netBalanceOverview: Flow<NetBalanceOverview> = combine(
        allAccounts,
        allTransactions,
        splitDao.getAllUnsettledSplits()
    ) { accounts, txs, unsettledSplits ->
        val activeAccounts = accounts.filter { !it.isArchived }
        val derivedList = activeAccounts.map { computeDerivedAccountBalance(it, txs) }

        val totalSavings = derivedList.filter { it.account.type != AccountType.CREDIT_CARD }.sumOf { it.derivedBalancePaise }
        val totalCardOwed = derivedList.filter { it.account.type == AccountType.CREDIT_CARD }.sumOf { it.derivedOwedPaise }
        val netAvailable = totalSavings - totalCardOwed

        val owedToFriends = unsettledSplits.filter { it.direction == SplitDirection.OWED_BY_ME }.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }
        val friendsOweMe = unsettledSplits.filter { it.direction == SplitDirection.OWED_TO_ME }.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }

        val unanchored = activeAccounts.count { !it.anchorSet }

        val anchoredDates = activeAccounts.filter { it.anchorSet && it.openingAsOf > 0L }.map { it.openingAsOf }
        val earliestTx = txs.minOfOrNull { it.timestamp }
        val candidates = anchoredDates + listOfNotNull(earliestTx)
        val earliestTimestamp = candidates.minOrNull() ?: System.currentTimeMillis()

        NetBalanceOverview(
            netAvailableBalancePaise = netAvailable,
            totalSavingsBalancePaise = totalSavings,
            totalCardOwedPaise = totalCardOwed,
            totalOwedToFriendsPaise = owedToFriends,
            totalFriendsOweMePaise = friendsOweMe,
            unanchoredAccountsCount = unanchored,
            earliestTrackedTimestamp = earliestTimestamp
        )
    }

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

    // ─── Vendors ──────────────────────────────────────────────────────────────
    val allVendors: Flow<List<VendorEntity>> = vendorDao.getAllVendors()
    val allVendorMappings: Flow<List<VendorEntity>> = vendorDao.getAllVendors()

    suspend fun addVendor(vendor: VendorEntity): Long = withContext(Dispatchers.IO) {
        vendorDao.insertVendor(vendor)
    }

    suspend fun addVendorMapping(mapping: VendorEntity): Long = withContext(Dispatchers.IO) {
        vendorDao.insertVendor(mapping)
    }

    suspend fun updateVendor(vendor: VendorEntity) = withContext(Dispatchers.IO) {
        vendorDao.updateVendor(vendor)
    }

    suspend fun updateVendorMapping(mapping: VendorEntity) = withContext(Dispatchers.IO) {
        vendorDao.updateVendor(mapping)
    }

    suspend fun deleteVendor(vendor: VendorEntity) = withContext(Dispatchers.IO) {
        vendorDao.deleteVendor(vendor)
    }

    suspend fun deleteVendorMapping(mapping: VendorEntity) = withContext(Dispatchers.IO) {
        vendorDao.deleteVendor(mapping)
    }

    /**
     * Resolves friendly vendor name and category from raw merchant text.
     */
    suspend fun resolveVendor(rawMerchant: String): Pair<String, Long?> = withContext(Dispatchers.IO) {
        val mappings = vendorDao.getAllVendorsList()
        val rawLower = rawMerchant.lowercase()
        for (m in mappings) {
            if (rawLower.contains(m.normalizedKey.lowercase())) {
                return@withContext Pair(m.displayName, m.categoryId)
            }
        }
        Pair(rawMerchant, null)
    }

    /**
     * Uses On-Device Local AI & smart heuristics to automatically categorize transactions.
     */
    suspend fun autoCategorizeOrResolve(rawMerchant: String): Pair<String, Long?> = withContext(Dispatchers.IO) {
        if (rawMerchant.isBlank()) return@withContext Pair(rawMerchant, null)

        // 1. Check existing vendor mappings first
        val (mappedName, existingCatId) = resolveVendor(rawMerchant)
        if (existingCatId != null) {
            return@withContext Pair(mappedName, existingCatId)
        }

        // 2. Query Local AI / smart heuristics for predicted category
        val allCats = categoryDao.getAllCategoriesList()
        val catNames = allCats.map { it.name }
        val predictedCategoryName = com.campusfold.tracker.ai.LocalAiManager.predictCategoryForMerchant(rawMerchant, catNames)

        if (predictedCategoryName.equals("Other", ignoreCase = true) || predictedCategoryName.isBlank()) {
            return@withContext Pair(mappedName, null)
        }

        // 3. Check if predicted category already exists (case-insensitive)
        val matchedCategory = categoryDao.getCategoryByName(predictedCategoryName)
            ?: allCats.find { it.name.equals(predictedCategoryName, ignoreCase = true) }

        val finalCategoryId: Long = if (matchedCategory != null) {
            matchedCategory.id
        } else {
            val categoryColor = when (predictedCategoryName.lowercase()) {
                "travel" -> "#06B6D4" // Cyan
                "food & dining", "food" -> "#10B981" // Emerald
                "groceries" -> "#F59E0B" // Amber
                "entertainment" -> "#8B5CF6" // Purple
                "clothing" -> "#EC4899" // Pink
                "healthcare" -> "#EF4444" // Red
                "education" -> "#3B82F6" // Blue
                "utilities" -> "#6366F1" // Indigo
                else -> "#14B8A6" // Teal
            }
            val newCat = CategoryEntity(
                name = predictedCategoryName,
                color = categoryColor,
                budgetPaise = CurrencyUtils.rupeesToPaise(5000.0)
            )
            categoryDao.insertCategory(newCat)
        }

        // 4. Save vendor mapping so future transactions from this vendor are immediately mapped
        try {
            vendorDao.insertVendor(
                VendorEntity(
                    displayName = mappedName,
                    normalizedKey = rawMerchant.trim().lowercase(),
                    categoryId = finalCategoryId
                )
            )
        } catch (_: Exception) {}

        Pair(mappedName, finalCategoryId)
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
     * Wrapped in a Room transaction for atomic consistency.
     */
    suspend fun addTransaction(
        transaction: TransactionEntity,
        splits: List<SplitItemEntity> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        var finalizedTx: TransactionEntity? = null
        val txId = db.withTransaction {
            val totalSplitOwedPaise = splits.sumOf { it.amountOwedPaise }
            val adjustedSharePaise = if (transaction.type == TransactionType.EXPENSE) {
                maxOf(0L, transaction.amountPaise - totalSplitOwedPaise)
            } else {
                transaction.amountPaise
            }

            var txWithCategory = transaction
            if (txWithCategory.categoryId == null && txWithCategory.type == TransactionType.EXPENSE && txWithCategory.rawMerchant.isNotBlank()) {
                val (mappedVendor, autoCatId) = autoCategorizeOrResolve(txWithCategory.rawMerchant)
                if (autoCatId != null) {
                    txWithCategory = txWithCategory.copy(
                        categoryId = autoCatId,
                        friendlyVendorName = if (txWithCategory.friendlyVendorName == txWithCategory.rawMerchant) mappedVendor else txWithCategory.friendlyVendorName
                    )
                }
            }

            val readyTx = txWithCategory.copy(myActualSharePaise = adjustedSharePaise)
            finalizedTx = readyTx
            val insertedId = transactionDao.insertTransaction(readyTx)

            if (splits.isNotEmpty()) {
                val splitsWithTxId = splits.map { it.copy(transactionId = insertedId) }
                splitDao.insertSplitItems(splitsWithTxId)
            }

            insertedId
        }

        // Evaluate Budget Thresholds (50, 70, 90, 100) if expense outside transaction block
        finalizedTx?.let { ft ->
            if (ft.type == TransactionType.EXPENSE && context != null) {
                BudgetAlertEvaluator.evaluateBudgetAlerts(context, db, appPreferences, ft.categoryId)
            }
        }

        txId
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.updateTransaction(transaction)
    }

    /**
     * Updates transaction details and recalculates splits cleanly.
     * Wrapped in db.withTransaction.
     */
    suspend fun editTransactionWithSplits(
        updatedTx: TransactionEntity,
        newSplits: List<SplitItemEntity>
    ) = withContext(Dispatchers.IO) {
        var finalizedTx: TransactionEntity? = null
        db.withTransaction {
            val totalSplitOwedPaise = newSplits.sumOf { it.amountOwedPaise }
            val adjustedSharePaise = if (updatedTx.type == TransactionType.EXPENSE) {
                maxOf(0L, updatedTx.amountPaise - totalSplitOwedPaise)
            } else {
                updatedTx.amountPaise
            }

            val readyTx = updatedTx.copy(myActualSharePaise = adjustedSharePaise)
            finalizedTx = readyTx

            val oldTx = transactionDao.getTransactionByIdSync(updatedTx.id)
            if (oldTx != null) {
                createLearnedRuleForEdit(oldTx, readyTx)
            }

            transactionDao.updateTransaction(readyTx)

            // Replace splits for this transaction
            splitDao.deleteSplitsForTransaction(updatedTx.id)
            if (newSplits.isNotEmpty()) {
                val splitsWithId = newSplits.map { it.copy(transactionId = updatedTx.id) }
                splitDao.insertSplitItems(splitsWithId)
            }
        }

        // Re-evaluate Budget Thresholds on split adjustment
        finalizedTx?.let { ft ->
            if (ft.type == TransactionType.EXPENSE && context != null) {
                BudgetAlertEvaluator.evaluateBudgetAlerts(context, db, appPreferences, ft.categoryId)
            }
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionById(id)
    }

    // ─── Splits & FIFO Settlement ─────────────────────────────────────────────

    fun getSplitsForTransaction(transactionId: Long): Flow<List<SplitItemEntity>> =
        splitDao.getSplitsForTransaction(transactionId)

    suspend fun getSplitsForTransactionSync(transactionId: Long): List<SplitItemEntity> = withContext(Dispatchers.IO) {
        splitDao.getSplitsForTransactionSync(transactionId)
    }

    fun getUnsettledBillsForPerson(personId: Long): Flow<List<UnsettledBillItem>> =
        splitDao.getUnsettledBillsForPerson(personId)

    val allUnsettledBills: Flow<List<UnsettledBillItem>> =
        splitDao.getAllUnsettledBills()

    @JvmName("queryAllUnsettledBills")
    fun getAllUnsettledBills(): Flow<List<UnsettledBillItem>> = allUnsettledBills

    /**
     * Settles amount X for a friend using strict FIFO (oldest split items first).
     * Exact integer comparisons on paise: handles partial payments, full settlement, and excess overpayment.
     * Wrapped in db.withTransaction.
     */
    suspend fun settleAmountForPerson(personId: Long, amountToSettle: Double): SettlementResult {
        return settleAmountForPersonPaise(personId, CurrencyUtils.rupeesToPaise(amountToSettle))
    }

    suspend fun settleAmountForPersonPaise(personId: Long, amountToSettlePaise: Long): SettlementResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val person = personDao.getPersonById(personId)
            val friendName = person?.name ?: "Friend"

            // Fetch unsettled rows ordered by createdAt ASC (FIFO)
            val unsettledRows = splitDao.getUnsettledSplitsForPersonSync(personId)
            val totalOwedBeforePaise = unsettledRows.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }

            var remainingXPaise = amountToSettlePaise
            var updatedCount = 0

            for (row in unsettledRows) {
                if (remainingXPaise <= 0L) break
                val rowRemainingPaise = row.amountOwedPaise - row.amountPaidSoFarPaise

                if (remainingXPaise < rowRemainingPaise) {
                    // Partial settlement
                    val newPaid = row.amountPaidSoFarPaise + remainingXPaise
                    splitDao.updateSplitItem(row.copy(amountPaidSoFarPaise = newPaid, isSettled = false))
                    remainingXPaise = 0L
                    updatedCount++
                } else {
                    // Full settlement of this row, carry remainder to next
                    splitDao.updateSplitItem(row.copy(amountPaidSoFarPaise = row.amountOwedPaise, isSettled = true))
                    remainingXPaise -= rowRemainingPaise
                    updatedCount++
                }
            }

            val excessPaise = maxOf(0L, remainingXPaise)
            val newRemainingOwedPaise = maxOf(0L, totalOwedBeforePaise - amountToSettlePaise)
            val actuallySettledPaise = minOf(amountToSettlePaise, totalOwedBeforePaise)

            SettlementResult(
                friendName = friendName,
                settledAmountPaise = actuallySettledPaise,
                remainingOwedPaise = newRemainingOwedPaise,
                excessAmountPaise = excessPaise,
                updatedSplitCount = updatedCount
            )
        }
    }

    suspend fun repayFriendDebtPaise(
        personId: Long,
        accountId: Long,
        amountToPayPaise: Long,
        timestamp: Long = System.currentTimeMillis()
    ): RepaymentResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val person = personDao.getPersonById(personId)
            val friendName = person?.name ?: "Friend"
            val account = accountDao.getAccountById(accountId)
            val accountName = account?.displayName ?: "Account"

            val unsettledRows = splitDao.getUnsettledSplitsForPersonAndDirectionSync(personId, SplitDirection.OWED_BY_ME)
            val totalOwedBeforePaise = unsettledRows.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }

            val debtToSettlePaise = minOf(amountToPayPaise, totalOwedBeforePaise)
            val excessPaise = maxOf(0L, amountToPayPaise - totalOwedBeforePaise)

            var remainingToAllocate = debtToSettlePaise
            var updatedCount = 0
            for (row in unsettledRows) {
                if (remainingToAllocate <= 0L) break
                val rowRemaining = row.amountOwedPaise - row.amountPaidSoFarPaise
                if (remainingToAllocate < rowRemaining) {
                    val newPaid = row.amountPaidSoFarPaise + remainingToAllocate
                    splitDao.updateSplitItem(row.copy(amountPaidSoFarPaise = newPaid, isSettled = false))
                    remainingToAllocate = 0L
                    updatedCount++
                } else {
                    splitDao.updateSplitItem(row.copy(amountPaidSoFarPaise = row.amountOwedPaise, isSettled = true))
                    remainingToAllocate -= rowRemaining
                    updatedCount++
                }
            }

            val settlementTx = TransactionEntity(
                amountPaise = debtToSettlePaise,
                myActualSharePaise = 0L,
                type = TransactionType.SPLIT_SETTLEMENT,
                paymentMode = accountName,
                rawMerchant = "Repayment to $friendName",
                friendlyVendorName = "Repayment to $friendName",
                accountId = accountId,
                timestamp = timestamp,
                source = TransactionSource.MANUAL,
                description = "Repayment of debt to $friendName"
            )
            val settlementTxId = transactionDao.insertTransaction(settlementTx)

            if (excessPaise > 0L) {
                val excessTx = TransactionEntity(
                    amountPaise = excessPaise,
                    myActualSharePaise = excessPaise,
                    type = TransactionType.EXPENSE,
                    paymentMode = accountName,
                    rawMerchant = friendName,
                    friendlyVendorName = "Payment to $friendName",
                    accountId = accountId,
                    timestamp = timestamp,
                    source = TransactionSource.MANUAL,
                    description = "Excess repayment to $friendName recorded as expense"
                )
                transactionDao.insertTransaction(excessTx)
            }

            val remainingDebtPaise = maxOf(0L, totalOwedBeforePaise - debtToSettlePaise)

            RepaymentResult(
                friendName = friendName,
                debtSettledPaise = debtToSettlePaise,
                remainingDebtPaise = remainingDebtPaise,
                excessAsExpensePaise = excessPaise,
                updatedSplitCount = updatedCount,
                transactionId = settlementTxId
            )
        }
    }

    suspend fun addFriendPaidExpense(
        personId: Long,
        mySharePaise: Long,
        totalBillPaise: Long? = null,
        vendor: String,
        categoryId: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
        note: String = ""
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val person = personDao.getPersonById(personId)
            val friendName = person?.name ?: "Friend"

            val desc = buildString {
                if (totalBillPaise != null && totalBillPaise > mySharePaise) {
                    append("Total bill: ${CurrencyUtils.formatInr(totalBillPaise, includeSymbol = true)}. ")
                }
                if (note.isNotBlank()) {
                    append(note)
                }
            }.trim().ifBlank { null }

            val tx = TransactionEntity(
                amountPaise = mySharePaise,
                myActualSharePaise = mySharePaise,
                type = TransactionType.EXPENSE,
                paymentMode = "Paid by $friendName",
                rawMerchant = vendor.ifBlank { "Expense paid by $friendName" },
                friendlyVendorName = vendor.ifBlank { "Expense paid by $friendName" },
                categoryId = categoryId,
                timestamp = timestamp,
                description = desc,
                accountId = null,
                source = TransactionSource.MANUAL,
                confidence = 1.0f,
                paidByPersonId = personId
            )
            val txId = transactionDao.insertTransaction(tx)

            val splitItem = SplitItemEntity(
                transactionId = txId,
                personId = personId,
                amountOwedPaise = mySharePaise,
                amountPaidSoFarPaise = 0L,
                isSettled = false,
                createdAt = timestamp,
                direction = SplitDirection.OWED_BY_ME
            )
            splitDao.insertSplitItem(splitItem)

            txId
        }
    }

    suspend fun addManualTransaction(
        amountPaise: Long,
        type: TransactionType,
        accountId: Long?,
        toAccountId: Long? = null,
        vendor: String,
        categoryId: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
        note: String = ""
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val account = accountId?.let { accountDao.getAccountById(it) }
            val toAccount = toAccountId?.let { accountDao.getAccountById(it) }

            when (type) {
                TransactionType.CC_BILL_TRANSFER -> {
                    val leg1 = TransactionEntity(
                        amountPaise = amountPaise,
                        myActualSharePaise = 0L,
                        type = TransactionType.CC_BILL_TRANSFER,
                        paymentMode = account?.displayName ?: "Savings",
                        rawMerchant = toAccount?.displayName ?: "Credit Card",
                        friendlyVendorName = "Payment to ${toAccount?.displayName ?: "Credit Card"}",
                        accountId = accountId,
                        timestamp = timestamp,
                        description = note.ifBlank { null }
                    )
                    val leg1Id = transactionDao.insertTransaction(leg1)

                    if (toAccountId != null) {
                        val leg2 = TransactionEntity(
                            amountPaise = amountPaise,
                            myActualSharePaise = 0L,
                            type = TransactionType.CC_BILL_TRANSFER,
                            paymentMode = toAccount?.displayName ?: "Credit Card",
                            rawMerchant = account?.displayName ?: "Savings",
                            friendlyVendorName = "Payment from ${account?.displayName ?: "Savings"}",
                            accountId = toAccountId,
                            timestamp = timestamp,
                            description = note.ifBlank { null }
                        )
                        transactionDao.insertTransaction(leg2)
                    }
                    leg1Id
                }
                TransactionType.OWN_TRANSFER -> {
                    val leg1 = TransactionEntity(
                        amountPaise = amountPaise,
                        myActualSharePaise = 0L,
                        type = TransactionType.OWN_TRANSFER,
                        paymentMode = account?.displayName ?: "Account",
                        rawMerchant = "Transfer to ${toAccount?.displayName ?: "Account"}",
                        friendlyVendorName = "Transfer to ${toAccount?.displayName ?: "Account"}",
                        accountId = accountId,
                        timestamp = timestamp,
                        description = ("Transfer to ${toAccount?.displayName ?: "Account"} | $note").trim().removeSuffix("|").trim()
                    )
                    val leg1Id = transactionDao.insertTransaction(leg1)

                    if (toAccountId != null) {
                        val leg2 = TransactionEntity(
                            amountPaise = amountPaise,
                            myActualSharePaise = 0L,
                            type = TransactionType.OWN_TRANSFER,
                            paymentMode = toAccount?.displayName ?: "Account",
                            rawMerchant = "Transfer from ${account?.displayName ?: "Account"}",
                            friendlyVendorName = "Transfer from ${account?.displayName ?: "Account"}",
                            accountId = toAccountId,
                            timestamp = timestamp,
                            description = ("Transfer from ${account?.displayName ?: "Account"} | $note").trim().removeSuffix("|").trim()
                        )
                        transactionDao.insertTransaction(leg2)
                    }
                    leg1Id
                }
                else -> {
                    val tx = TransactionEntity(
                        amountPaise = amountPaise,
                        myActualSharePaise = amountPaise,
                        type = type,
                        paymentMode = account?.displayName ?: "Cash / Manual",
                        rawMerchant = vendor.ifBlank { "Manual ${type.name.lowercase().replaceFirstChar { it.uppercase() }}" },
                        friendlyVendorName = vendor.ifBlank { "Manual ${type.name.lowercase().replaceFirstChar { it.uppercase() }}" },
                        categoryId = categoryId,
                        timestamp = timestamp,
                        accountId = accountId,
                        description = note.ifBlank { null }
                    )
                    transactionDao.insertTransaction(tx)
                }
            }
        }
    }

    suspend fun recordSplitPayment(splitId: Long, paymentAmountPaise: Long) = withContext(Dispatchers.IO) {
        splitDao.recordPayment(splitId, paymentAmountPaise)
    }

    suspend fun recordSplitPayment(splitId: Long, paymentAmount: Double) = withContext(Dispatchers.IO) {
        recordSplitPayment(splitId, CurrencyUtils.rupeesToPaise(paymentAmount))
    }

    suspend fun markSplitSettled(splitId: Long, isSettled: Boolean) = withContext(Dispatchers.IO) {
        splitDao.markSettled(splitId, isSettled)
    }

    suspend fun logOverpaymentAsAdvanceIncome(friendName: String, excessAmountPaise: Long) = withContext(Dispatchers.IO) {
        if (excessAmountPaise <= 0L) return@withContext
        val tx = TransactionEntity(
            amountPaise = excessAmountPaise,
            type = TransactionType.INCOME,
            paymentMode = "Cash/Manual",
            rawMerchant = "Advance from $friendName",
            friendlyVendorName = "Advance from $friendName",
            categoryId = null,
            timestamp = System.currentTimeMillis()
        )
        transactionDao.insertTransaction(tx)
    }

    suspend fun logOverpaymentAsIncome(excessAmount: Double, friendName: String) =
        logOverpaymentAsAdvanceIncome(friendName, CurrencyUtils.rupeesToPaise(excessAmount))

    // ─── Pending Settlement Suggestions & 1-Tap Alias Saving ──────────────────
    val pendingSuggestions: Flow<List<PendingSettlementSuggestionEntity>> = suggestionDao.getPendingSuggestions()

    suspend fun createSettlementSuggestion(suggestion: PendingSettlementSuggestionEntity): Long = withContext(Dispatchers.IO) {
        suggestionDao.insertSuggestion(suggestion)
    }

    /**
     * 1-Tap 'Confirm & Save Alias' button logic:
     * Settles the balance via FIFO, saves the detected SMS name to exactSmsAliases for future auto-settlement,
     * marks suggestion CONFIRMED, and updates transaction to SPLIT_SETTLEMENT.
     * Wrapped in db.withTransaction.
     */
    suspend fun confirmAndSaveAlias(suggestionId: Long): SettlementResult? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val suggestion = suggestionDao.getSuggestionById(suggestionId) ?: return@withTransaction null
            val person = personDao.getPersonById(suggestion.suggestedPersonId) ?: return@withTransaction null

            // 1. Run FIFO settlement based on direction
            val result = if (suggestion.direction == SplitDirection.OWED_BY_ME) {
                // Outgoing repayment
                val unsettledRows = splitDao.getUnsettledSplitsForPersonAndDirectionSync(person.id, SplitDirection.OWED_BY_ME)
                val totalOwedBeforePaise = unsettledRows.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }
                val debtToSettle = minOf(suggestion.amountPaise, totalOwedBeforePaise)
                val excess = maxOf(0L, suggestion.amountPaise - totalOwedBeforePaise)

                var remaining = debtToSettle
                var count = 0
                for (row in unsettledRows) {
                    if (remaining <= 0L) break
                    val rowRemaining = row.amountOwedPaise - row.amountPaidSoFarPaise
                    if (remaining < rowRemaining) {
                        splitDao.updateSplitItem(row.copy(amountPaidSoFarPaise = row.amountPaidSoFarPaise + remaining, isSettled = false))
                        remaining = 0L
                        count++
                    } else {
                        splitDao.updateSplitItem(row.copy(amountPaidSoFarPaise = row.amountOwedPaise, isSettled = true))
                        remaining -= rowRemaining
                        count++
                    }
                }

                // If transaction exists, update it to SPLIT_SETTLEMENT
                suggestion.transactionId?.let { txId ->
                    val tx = transactionDao.getTransactionByIdSync(txId)
                    if (tx != null) {
                        if (excess > 0L) {
                            transactionDao.updateTransaction(
                                tx.copy(
                                    amountPaise = debtToSettle,
                                    myActualSharePaise = 0L,
                                    type = TransactionType.SPLIT_SETTLEMENT,
                                    friendlyVendorName = "Repayment to ${person.name}"
                                )
                            )
                            val excessTx = tx.copy(
                                id = 0,
                                amountPaise = excess,
                                myActualSharePaise = excess,
                                type = TransactionType.EXPENSE,
                                friendlyVendorName = "Payment to ${person.name}",
                                description = "Excess repayment to ${person.name}"
                            )
                            transactionDao.insertTransaction(excessTx)
                        } else {
                            transactionDao.updateTransaction(
                                tx.copy(
                                    type = TransactionType.SPLIT_SETTLEMENT,
                                    myActualSharePaise = 0L,
                                    friendlyVendorName = "Repayment to ${person.name}"
                                )
                            )
                        }
                    }
                }

                SettlementResult(
                    friendName = person.name,
                    settledAmountPaise = debtToSettle,
                    remainingOwedPaise = maxOf(0L, totalOwedBeforePaise - debtToSettle),
                    excessAmountPaise = excess,
                    updatedSplitCount = count
                )
            } else {
                // Incoming settlement
                val res = settleAmountForPersonPaise(person.id, suggestion.amountPaise)
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
                res
            }

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

            result
        }
    }

    suspend fun dismissSettlementSuggestion(suggestionId: Long) = withContext(Dispatchers.IO) {
        suggestionDao.updateStatus(suggestionId, SettlementSuggestionStatus.DISMISSED)
    }

    // ─── Review Queue API ─────────────────────────────────────────────────────
    val pendingReviewItems: Flow<List<ReviewItemEntity>> = reviewItemDao.getPendingReviewItems()
    val allReviewItems: Flow<List<ReviewItemEntity>> = reviewItemDao.getAllReviewItems()

    suspend fun getPendingReviewItemsList(): List<ReviewItemEntity> = withContext(Dispatchers.IO) {
        reviewItemDao.getAllReviewItemsSync().filter { it.status == ReviewStatus.PENDING }
    }

    suspend fun acceptReviewItem(reviewItemId: Long, editedPayloadJson: String? = null) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val item = reviewItemDao.getReviewItemById(reviewItemId) ?: return@withTransaction
            val payloadStr = editedPayloadJson ?: item.payloadJson
            val json = try { org.json.JSONObject(payloadStr) } catch (_: Exception) { null }

            if (json != null) {
                val type = json.optString("type", "")
                if (type == "CATEGORY_PREDICTION") {
                    val vendorName = json.optString("vendor", "")
                    val catName = json.optString("predictedCategory", "")
                    if (vendorName.isNotBlank() && catName.isNotBlank()) {
                        val cat = categoryDao.getCategoryByName(catName) ?: run {
                            val newCat = CategoryEntity(name = catName, color = "#10B981", budgetPaise = null)
                            val id = categoryDao.insertCategory(newCat)
                            newCat.copy(id = id)
                        }
                        vendorDao.insertVendor(
                            VendorEntity(
                                displayName = vendorName,
                                normalizedKey = vendorName.lowercase(java.util.Locale.US),
                                categoryId = cat.id
                            )
                        )
                    }
                }
            }

            reviewItemDao.updateStatus(reviewItemId, ReviewStatus.ACCEPTED)
        }
    }

    suspend fun editReviewItem(reviewItemId: Long, newPayloadJson: String) = withContext(Dispatchers.IO) {
        val item = reviewItemDao.getReviewItemById(reviewItemId) ?: return@withContext
        reviewItemDao.updateReviewItem(item.copy(payloadJson = newPayloadJson))
    }

    suspend fun discardReviewItem(reviewItemId: Long) = withContext(Dispatchers.IO) {
        reviewItemDao.updateStatus(reviewItemId, ReviewStatus.DISCARDED)
    }

    // ─── Rules Management & Learned Rules ─────────────────────────────────────
    val allRules: Flow<List<RuleEntity>> = ruleDao.getAllRules()

    suspend fun getAllRulesList(): List<RuleEntity> = withContext(Dispatchers.IO) {
        ruleDao.getAllRulesSync()
    }

    suspend fun addRule(rule: RuleEntity): Long = withContext(Dispatchers.IO) {
        ruleDao.insertRule(rule)
    }

    suspend fun updateRule(rule: RuleEntity) = withContext(Dispatchers.IO) {
        ruleDao.updateRule(rule)
    }

    suspend fun deleteRule(rule: RuleEntity) = withContext(Dispatchers.IO) {
        ruleDao.deleteRule(rule)
    }

    suspend fun deleteRuleById(id: Long) = withContext(Dispatchers.IO) {
        ruleDao.deleteRuleById(id)
    }

    suspend fun toggleRuleEnabled(ruleId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        val rule = ruleDao.getRuleById(ruleId) ?: return@withContext
        ruleDao.updateRule(rule.copy(enabled = enabled))
    }

    /**
     * Checks if a transaction edit (category change, vendor rename, or account fix)
     * warrants creating a LEARNED rule, and inserts it.
     */
    suspend fun createLearnedRuleForEdit(oldTx: TransactionEntity, newTx: TransactionEntity): Long? = withContext(Dispatchers.IO) {
        var createdRuleId: Long? = null

        // Case 1: Category Changed
        if (oldTx.categoryId != newTx.categoryId && newTx.categoryId != null) {
            val cat = categoryDao.getCategoryById(newTx.categoryId!!)
            val merchant = newTx.friendlyVendorName.ifBlank { newTx.rawMerchant }
            if (cat != null && merchant.isNotBlank()) {
                val conditions = listOf(
                    com.campusfold.tracker.rules.RuleCondition(
                        field = com.campusfold.tracker.rules.TransactionField.MERCHANT,
                        operator = com.campusfold.tracker.rules.ConditionOperator.EQUALS,
                        value = merchant
                    )
                )
                val actions = listOf(
                    com.campusfold.tracker.rules.RuleAction(
                        field = com.campusfold.tracker.rules.TransactionField.CATEGORY,
                        actionType = com.campusfold.tracker.rules.ActionType.SET,
                        value = cat.name
                    )
                )
                val rule = RuleEntity(
                    conditionsJson = com.campusfold.tracker.rules.RuleEngine.serializeConditions(conditions),
                    actionsJson = com.campusfold.tracker.rules.RuleEngine.serializeActions(actions),
                    priority = 10,
                    enabled = true,
                    source = RuleSource.LEARNED
                )
                createdRuleId = ruleDao.insertRule(rule)
            }
        }
        // Case 2: Vendor Renamed
        else if (oldTx.friendlyVendorName != newTx.friendlyVendorName && newTx.friendlyVendorName.isNotBlank()) {
            val raw = newTx.rawMerchant.ifBlank { oldTx.friendlyVendorName }
            val conditions = listOf(
                com.campusfold.tracker.rules.RuleCondition(
                    field = com.campusfold.tracker.rules.TransactionField.MERCHANT,
                    operator = com.campusfold.tracker.rules.ConditionOperator.EQUALS,
                    value = raw
                )
            )
            val actions = listOf(
                com.campusfold.tracker.rules.RuleAction(
                    field = com.campusfold.tracker.rules.TransactionField.MERCHANT,
                    actionType = com.campusfold.tracker.rules.ActionType.SET,
                    value = newTx.friendlyVendorName
                )
            )
            val rule = RuleEntity(
                conditionsJson = com.campusfold.tracker.rules.RuleEngine.serializeConditions(conditions),
                actionsJson = com.campusfold.tracker.rules.RuleEngine.serializeActions(actions),
                priority = 10,
                enabled = true,
                source = RuleSource.LEARNED
            )
            createdRuleId = ruleDao.insertRule(rule)
        }
        // Case 3: Account Fixed
        else if (oldTx.accountId != newTx.accountId && newTx.accountId != null) {
            val newAcc = accountDao.getAccountById(newTx.accountId!!)
            if (newAcc != null && !oldTx.rawSms.isNullOrBlank()) {
                val conditions = listOf(
                    com.campusfold.tracker.rules.RuleCondition(
                        field = com.campusfold.tracker.rules.TransactionField.SMS_TEXT,
                        operator = com.campusfold.tracker.rules.ConditionOperator.CONTAINS,
                        value = newAcc.last4
                    )
                )
                val actions = listOf(
                    com.campusfold.tracker.rules.RuleAction(
                        field = com.campusfold.tracker.rules.TransactionField.BANK_NAME,
                        actionType = com.campusfold.tracker.rules.ActionType.SET,
                        value = newAcc.displayName
                    )
                )
                val rule = RuleEntity(
                    conditionsJson = com.campusfold.tracker.rules.RuleEngine.serializeConditions(conditions),
                    actionsJson = com.campusfold.tracker.rules.RuleEngine.serializeActions(actions),
                    priority = 10,
                    enabled = true,
                    source = RuleSource.LEARNED
                )
                createdRuleId = ruleDao.insertRule(rule)
            }
        }

        createdRuleId
    }

    /**
     * Applies a rule to past matching transactions, respecting user-edited fields.
     */
    suspend fun applyRuleToPastTransactions(ruleId: Long): Int = withContext(Dispatchers.IO) {
        val rule = ruleDao.getRuleById(ruleId) ?: return@withContext 0
        val allTx = transactionDao.getAllTransactionsSync()
        val allCats = categoryDao.getAllCategoriesList().associate { it.name.lowercase(java.util.Locale.US) to it.id }
        var updatedCount = 0

        db.withTransaction {
            for (tx in allTx) {
                val res = com.campusfold.tracker.rules.RuleEngine.evaluateRules(
                    transaction = tx,
                    rawSms = tx.rawSms,
                    account = null,
                    rules = listOf(rule)
                )
                if (res.appliedRuleIds.contains(ruleId)) {
                    var modified = res.modifiedTransaction
                    if (res.targetCategoryName != null) {
                        val catId = allCats[res.targetCategoryName.lowercase(java.util.Locale.US)]
                        if (catId != null) {
                            modified = modified.copy(categoryId = catId)
                        }
                    }
                    if (modified != tx) {
                        transactionDao.updateTransaction(modified)
                        updatedCount++
                    }
                }
            }
        }
        updatedCount
    }

    // ─── Preferences & Budgets ───────────────────────────────────────────────
    val lastSmsSyncTimestamp: Flow<Long> = appPreferences.lastSmsSyncTimestamp
    val overallMonthlyBudget: Flow<Double> = appPreferences.overallMonthlyBudget
    val hasCompletedOnboarding: Flow<Boolean> = appPreferences.hasCompletedOnboarding
    val userName: Flow<String> = appPreferences.userName
    val hideBalances: Flow<Boolean> = appPreferences.hideBalances
    val themeMode: Flow<String> = appPreferences.themeMode
    val dynamicColor: Flow<Boolean> = appPreferences.dynamicColor
    val amoledMode: Flow<Boolean> = appPreferences.amoledMode

    suspend fun setLastSmsSyncTimestamp(timestamp: Long) = appPreferences.setLastSmsSyncTimestamp(timestamp)
    suspend fun setOverallMonthlyBudget(budget: Double) = appPreferences.setOverallMonthlyBudget(budget)
    suspend fun setCompletedOnboarding(completed: Boolean) = appPreferences.setCompletedOnboarding(completed)
    suspend fun setUserName(name: String) = appPreferences.setUserName(name)
    suspend fun setHideBalances(hide: Boolean) = appPreferences.setHideBalances(hide)
    suspend fun setThemeMode(mode: String) = appPreferences.setThemeMode(mode)
    suspend fun setDynamicColor(enabled: Boolean) = appPreferences.setDynamicColor(enabled)
    suspend fun setAmoledMode(enabled: Boolean) = appPreferences.setAmoledMode(enabled)

    suspend fun updateCategoryBudget(categoryId: Long, budget: Double) = withContext(Dispatchers.IO) {
        val cat = categoryDao.getCategoryById(categoryId) ?: return@withContext
        categoryDao.updateCategory(cat.copy(budgetPaise = if (budget > 0) CurrencyUtils.rupeesToPaise(budget) else null))
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

    fun getCurrentMonthTotalTrueSpendPaise(): Flow<Long> {
        val (start, end) = BudgetAlertEvaluator.getMonthTimeBounds()
        return transactionDao.getMonthTotalMyExpenseFlowPaise(start, end).map { it ?: 0L }
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

    // ─── Direct Gmail IMAP Sync for Any Bank ────────────────────────────────

    fun enqueueGmailSyncWorker(context: Context, startDateMillis: Long? = null): java.util.UUID {
        val input = androidx.work.workDataOf(
            com.campusfold.tracker.worker.GmailScanWorker.KEY_START_DATE to (startDateMillis ?: 0L)
        )
        val request = androidx.work.OneTimeWorkRequestBuilder<com.campusfold.tracker.worker.GmailScanWorker>()
            .setInputData(input)
            .addTag(com.campusfold.tracker.worker.GmailScanWorker.TAG)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            com.campusfold.tracker.worker.GmailScanWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    suspend fun syncAxisFromGmail(
        emailAddress: String,
        appPassword: String,
        startDateMillis: Long? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): GmailSyncStats = withContext(Dispatchers.IO) {
        val fetched = GmailImapClient.fetchBankEmails(
            emailAddress = emailAddress,
            appPassword = appPassword,
            appPreferences = appPreferences,
            startDateMillis = startDateMillis,
            onProgress = onProgress
        )

        val aiFallbackService = com.campusfold.tracker.ai.AiFallbackService(aiCacheDao, appPreferences)
        val resolver = com.campusfold.tracker.data.resolver.DefaultEntityResolver(
            accountDao = accountDao,
            accountAliasDao = accountAliasDao,
            vendorDao = vendorDao,
            categoryDao = categoryDao,
            ruleDao = ruleDao,
            reviewItemDao = reviewItemDao,
            aiFallbackService = aiFallbackService,
            appPreferences = appPreferences
        )
        val ingestor = com.campusfold.tracker.data.ingest.MessageIngestor(
            db = db,
            entityResolver = resolver,
            repository = this@TransactionRepository,
            context = context,
            appPreferences = appPreferences,
            aiFallbackService = aiFallbackService
        )

        val rawMessages = fetched.map { emailItem ->
            com.campusfold.tracker.parser.model.RawMessage(
                source = com.campusfold.tracker.parser.model.MessageSource.EMAIL,
                sender = emailItem.sender,
                body = emailItem.body,
                timestamp = emailItem.dateMillis,
                sourceId = emailItem.uid.toString()
            )
        }

        var importedDebits = 0
        var importedCredits = 0
        var skippedDuplicates = 0

        val results = ingestor.ingestBatch(rawMessages)
        for (res in results) {
            when (res) {
                is com.campusfold.tracker.data.ingest.IngestResult.Imported -> {
                    val tx = transactionDao.getTransactionByIdSync(res.transactionId)
                    if (tx?.type == TransactionType.INCOME) importedCredits++ else importedDebits++
                }
                is com.campusfold.tracker.data.ingest.IngestResult.Enriched,
                is com.campusfold.tracker.data.ingest.IngestResult.SkippedAlreadyProcessed -> {
                    skippedDuplicates++
                }
                else -> {}
            }
        }

        appPreferences.setGmailLastSyncTimestamp(System.currentTimeMillis())

        try {
            accountMergeManager.runAutoMergePass()
        } catch (_: Exception) {}

        GmailSyncStats(
            totalFound = fetched.size,
            importedDebits = importedDebits,
            importedCredits = importedCredits,
            autoSettledCredits = 0,
            pendingSuggestionsCreated = 0,
            skippedDuplicates = skippedDuplicates
        )
    }

    /**
     * Manually map/link an incoming money transfer (Income) to an existing Expense.
     * Reduces the user's out-of-pocket share (myActualShare) on the expense,
     * marks the income as a SPLIT_SETTLEMENT so it doesn't inflate gross personal income,
     * and updates any corresponding split records on the expense.
     * Uses exact integer arithmetic on paise. Wrapped in db.withTransaction.
     */
    suspend fun linkIncomeToExpense(
        incomeTransactionId: Long,
        expenseTransactionId: Long,
        customOffsetAmount: Double? = null
    ) = withContext(Dispatchers.IO) {
        val offsetPaise = customOffsetAmount?.let { CurrencyUtils.rupeesToPaise(it) }
        linkIncomeToExpensePaise(incomeTransactionId, expenseTransactionId, offsetPaise)
    }

    suspend fun linkIncomeToExpensePaise(
        incomeTransactionId: Long,
        expenseTransactionId: Long,
        customOffsetAmountPaise: Long? = null
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val incomeTx = transactionDao.getTransactionByIdSync(incomeTransactionId) ?: return@withTransaction
            val expenseTx = transactionDao.getTransactionByIdSync(expenseTransactionId) ?: return@withTransaction

            val offsetPaise = customOffsetAmountPaise ?: incomeTx.amountPaise
            val newMySharePaise = maxOf(0L, expenseTx.myActualSharePaise - offsetPaise)

            // 1. Update expense with reduced true personal spend
            transactionDao.updateTransaction(
                expenseTx.copy(myActualSharePaise = newMySharePaise)
            )

            // 2. Mark income as SPLIT_SETTLEMENT
            val expenseLabel = if (expenseTx.friendlyVendorName.isNotBlank()) {
                expenseTx.friendlyVendorName
            } else {
                expenseTx.rawMerchant.ifBlank { "Expense #${expenseTx.id}" }
            }
            transactionDao.updateTransaction(
                incomeTx.copy(
                    type = TransactionType.SPLIT_SETTLEMENT,
                    friendlyVendorName = "Reimbursement for $expenseLabel"
                )
            )

            // 3. Settle any split items attached to this expense
            val splits = splitDao.getSplitsForTransactionSync(expenseTransactionId)
            if (splits.isNotEmpty()) {
                var remainingPaise = offsetPaise
                for (split in splits) {
                    if (remainingPaise <= 0L) break
                    val unpaidPaise = maxOf(0L, split.amountOwedPaise - split.amountPaidSoFarPaise)
                    if (unpaidPaise > 0L) {
                        val paying = minOf(remainingPaise, unpaidPaise)
                        val newPaid = split.amountPaidSoFarPaise + paying
                        val isSettled = newPaid >= split.amountOwedPaise // Exact integer comparison
                        splitDao.updateSplitItem(
                            split.copy(
                                amountPaidSoFarPaise = newPaid,
                                isSettled = isSettled
                            )
                        )
                        remainingPaise -= paying
                    }
                }
            }
        }
    }

    /**
     * Batch inserts a list of transactions wrapped in db.withTransaction.
     */
    suspend fun batchInsertTransactions(transactions: List<TransactionEntity>): List<Long> = withContext(Dispatchers.IO) {
        db.withTransaction {
            transactionDao.insertTransactions(transactions)
        }
    }

    /**
     * Fresh install starts completely empty.
     */
    suspend fun ensurePreseededData() = withContext(Dispatchers.IO) {
        // No-op: fresh install starts completely empty - no pre-seeded personal or test data
    }

    companion object {
        fun parseEditedFields(jsonStr: String): Set<String> {
            if (jsonStr.isBlank() || jsonStr == "[]") return emptySet()
            return try {
                val arr = JSONArray(jsonStr)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                set
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
}
