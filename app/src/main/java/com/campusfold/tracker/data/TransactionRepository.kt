package com.campusfold.tracker.data

import android.content.Context
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.model.CategorySpending
import com.campusfold.tracker.data.model.PersonWithBalance
import com.campusfold.tracker.data.model.TransactionWithSplits
import com.campusfold.tracker.data.pref.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TransactionRepository(
    private val db: AppDatabase,
    private val appPreferences: AppPreferences
) {

    private val transactionDao: TransactionDao = db.transactionDao()
    private val categoryDao: CategoryDao = db.categoryDao()
    private val vendorDao: VendorMappingDao = db.vendorMappingDao()
    private val personDao: PersonDao = db.personDao()
    private val splitDao: SplitItemDao = db.splitItemDao()
    private val suggestionDao: PendingSettlementSuggestionDao = db.pendingSettlementSuggestionDao()

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
        txId
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

    // ─── Split Settlements ───────────────────────────────────────────────────

    suspend fun recordSplitPayment(splitId: Long, paymentAmount: Double) = withContext(Dispatchers.IO) {
        splitDao.recordPayment(splitId, paymentAmount)
    }

    suspend fun markSplitSettled(splitId: Long, isSettled: Boolean) = withContext(Dispatchers.IO) {
        splitDao.markSettled(splitId, isSettled)
    }

    // ─── Pending Settlement Suggestions ───────────────────────────────────────
    val pendingSuggestions: Flow<List<PendingSettlementSuggestionEntity>> = suggestionDao.getPendingSuggestions()

    suspend fun createSettlementSuggestion(suggestion: PendingSettlementSuggestionEntity): Long = withContext(Dispatchers.IO) {
        suggestionDao.insertSuggestion(suggestion)
    }

    /**
     * 1-Tap Confirmation for a settlement suggestion.
     * Records the payment against unsettled splits for the suggested friend.
     */
    suspend fun confirmSettlementSuggestion(suggestionId: Long) = withContext(Dispatchers.IO) {
        val suggestion = suggestionDao.getSuggestionById(suggestionId) ?: return@withContext
        var remainingCredit = suggestion.amount

        // Find unsettled splits for this person
        val unsettledSplits = splitDao.getUnsettledSplitsForPersonSync(suggestion.suggestedPersonId)
        for (split in unsettledSplits) {
            if (remainingCredit <= 0.0) break
            val outstanding = split.amountOwed - split.amountPaidSoFar
            val paymentForThisSplit = minOf(remainingCredit, outstanding)
            splitDao.recordPayment(split.id, paymentForThisSplit)
            remainingCredit -= paymentForThisSplit
        }

        suggestionDao.updateStatus(suggestionId, SettlementSuggestionStatus.CONFIRMED)
    }

    suspend fun dismissSettlementSuggestion(suggestionId: Long) = withContext(Dispatchers.IO) {
        suggestionDao.updateStatus(suggestionId, SettlementSuggestionStatus.DISMISSED)
    }

    // ─── Preferences ─────────────────────────────────────────────────────────
    val latestPnbCreditLimit: Flow<Double> = appPreferences.latestPnbCreditLimit
    val lastSmsSyncTimestamp: Flow<Long> = appPreferences.lastSmsSyncTimestamp

    suspend fun setPnbCreditLimit(limit: Double) = appPreferences.setPnbCreditLimit(limit)
    suspend fun setLastSmsSyncTimestamp(timestamp: Long) = appPreferences.setLastSmsSyncTimestamp(timestamp)

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
    }
}
