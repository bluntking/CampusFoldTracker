package com.campusfold.tracker

import com.campusfold.tracker.data.dao.TransactionDao
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.model.TransactionWithSplits
import com.campusfold.tracker.data.model.VendorSpendingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class FakeBaseTransactionDao : TransactionDao {
    val transactions = mutableMapOf<Long, TransactionEntity>()
    var idCounter = 1L

    override fun getAllTransactions(): Flow<List<TransactionEntity>> =
        flowOf(transactions.values.sortedByDescending { it.timestamp })

    override suspend fun getAllTransactionsSync(): List<TransactionEntity> =
        transactions.values.sortedByDescending { it.timestamp }

    override suspend fun getRecentTransactionsSync(limit: Int): List<TransactionEntity> =
        transactions.values.sortedByDescending { it.timestamp }.take(limit)

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

    override suspend fun unassignAccountForTransactions(accountId: Long) {
        for ((id, tx) in transactions.entries) {
            if (tx.accountId == accountId) {
                transactions[id] = tx.copy(accountId = null, paymentMode = "Cash/Manual")
            }
        }
    }

    override suspend fun updateAccountIdForTransactionIds(transactionIds: List<Long>, newAccountId: Long) {
        for (id in transactionIds) {
            transactions[id]?.let { transactions[id] = it.copy(accountId = newAccountId) }
        }
    }

    override suspend fun getDistinctReferencesForAccount(accountId: Long): List<String> =
        transactions.values.filter { it.accountId == accountId }.mapNotNull { it.reference }.filter { it.isNotBlank() }.distinct()

    override suspend fun getDistinctUpiRefsForAccount(accountId: Long): List<String> =
        transactions.values.filter { it.accountId == accountId }.mapNotNull { it.upiRef }.filter { it.isNotBlank() }.distinct()

    override suspend fun getTransactionsByIdsSync(ids: List<Long>): List<TransactionEntity> =
        ids.mapNotNull { transactions[it] }

    override suspend fun deleteTransactionsByIds(ids: List<Long>) {
        ids.forEach { transactions.remove(it) }
    }

    override suspend fun updateCategoryIdForTransactionIds(ids: List<Long>, categoryId: Long?) {
        ids.forEach { transactions[it]?.let { tx -> transactions[it] = tx.copy(categoryId = categoryId) } }
    }

    override suspend fun updateCategoryIdForTransactions(oldCategoryId: Long, newCategoryId: Long?) {
        transactions.entries.filter { it.value.categoryId == oldCategoryId }.forEach { (id, tx) ->
            transactions[id] = tx.copy(categoryId = newCategoryId)
        }
    }

    override suspend fun countTransactionsByCategoryId(categoryId: Long): Int =
        transactions.values.count { it.categoryId == categoryId }

    override suspend fun updateVendorNameForTransactions(oldName: String, newName: String) {
        transactions.entries.filter { it.value.friendlyVendorName == oldName }.forEach { (id, tx) ->
            transactions[id] = tx.copy(friendlyVendorName = newName)
        }
    }

    override suspend fun updateVendorNameAndCategoryForTransactions(oldName: String, newName: String, newCategoryId: Long?) {
        transactions.entries.filter { it.value.friendlyVendorName == oldName }.forEach { (id, tx) ->
            transactions[id] = tx.copy(friendlyVendorName = newName, categoryId = newCategoryId ?: tx.categoryId)
        }
    }

    override suspend fun countTransactionsByVendorName(vendorName: String): Int =
        transactions.values.count { it.friendlyVendorName == vendorName }

    override suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = if (transaction.id > 0) transaction.id else idCounter++
        transactions[id] = transaction.copy(id = id)
        return id
    }

    override suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long> =
        transactions.map { insertTransaction(it) }

    override suspend fun restoreTransactions(transactions: List<TransactionEntity>): List<Long> =
        insertTransactions(transactions)

    override suspend fun updateTransaction(transaction: TransactionEntity) {
        transactions[transaction.id] = transaction
    }

    override suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactions.remove(transaction.id)
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactions.remove(id)
    }

    override suspend fun clearAll() {
        transactions.clear()
    }

    override fun getTotalMyExpensePaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalGrossExpensePaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalIncomePaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalCcBillPaymentsPaise(): Flow<Long?> = flowOf(0L)
    override fun getTotalSpentOnPaymentModePaise(mode: String): Flow<Long?> = flowOf(0L)

    override suspend fun getMonthSpendForCategoryPaise(categoryId: Long, start: Long, end: Long): Long? = 0L
    override suspend fun getMonthTotalMyExpensePaise(start: Long, end: Long): Long? = 0L
    override fun getMonthTotalMyExpenseFlowPaise(start: Long, end: Long): Flow<Long?> = flowOf(0L)
    override fun getVendorSpendingBetween(start: Long, end: Long): Flow<List<VendorSpendingItem>> = flowOf(emptyList())
    override suspend fun getVendorSpendingBetweenSync(start: Long, end: Long): List<VendorSpendingItem> = emptyList()
    override fun getHighFrequencyLeaksFlow(sinceTimestamp: Long): Flow<List<VendorSpendingItem>> = flowOf(emptyList())
    override suspend fun getHighFrequencyLeaksSync(sinceTimestamp: Long): List<VendorSpendingItem> = emptyList()
}
