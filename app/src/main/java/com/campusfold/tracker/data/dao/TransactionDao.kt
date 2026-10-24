package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.model.TransactionWithSplits
import com.campusfold.tracker.data.model.VendorSpendingItem
import com.campusfold.tracker.util.CurrencyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTransactionsSync(limit: Int = 20): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionByIdSync(id: Long): TransactionEntity?

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionWithSplits(id: Long): Flow<TransactionWithSplits?>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsWithSplits(): Flow<List<TransactionWithSplits>>

    @Query("SELECT * FROM transactions WHERE upiRef = :upiRef LIMIT 1")
    suspend fun findByUpiRef(upiRef: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE reference = :reference LIMIT 1")
    suspend fun findByReference(reference: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE rawMessageHash = :hash LIMIT 1")
    suspend fun findByRawMessageHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE amountPaise = :amountPaise AND paymentMode = :paymentMode AND timestamp >= :minTime AND timestamp <= :maxTime LIMIT 1")
    suspend fun findPotentialDuplicate(amountPaise: Long, paymentMode: String, minTime: Long, maxTime: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE (accountId = :accountId OR (:accountId IS NULL AND paymentMode = :paymentMode))
          AND amountPaise = :amountPaise 
          AND timestamp >= :minTime 
          AND timestamp <= :maxTime
    """)
    suspend fun findDuplicatesByAccountAmountTime(
        accountId: Long?,
        paymentMode: String,
        amountPaise: Long,
        minTime: Long,
        maxTime: Long
    ): List<TransactionEntity>

    suspend fun findPotentialDuplicate(amount: Double, paymentMode: String, minTime: Long, maxTime: Long): TransactionEntity? =
        findPotentialDuplicate(CurrencyUtils.rupeesToPaise(amount), paymentMode, minTime, maxTime)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT * FROM transactions WHERE accountId = :accountId")
    suspend fun getTransactionsByAccountId(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getTransactionsForAccountSinceSync(accountId: Long, since: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp ASC")
    fun getTransactionsForAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT MIN(timestamp) FROM transactions")
    fun getEarliestTransactionTimestamp(): Flow<Long?>

    @Query("SELECT MIN(timestamp) FROM transactions")
    suspend fun getEarliestTransactionTimestampSync(): Long?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countTransactionsByAccountId(accountId: Long): Int

    @Query("UPDATE transactions SET accountId = :newAccountId WHERE accountId = :oldAccountId")
    suspend fun updateAccountIdForTransactions(oldAccountId: Long, newAccountId: Long)

    @Query("UPDATE transactions SET accountId = NULL, paymentMode = 'Cash/Manual' WHERE accountId = :accountId")
    suspend fun unassignAccountForTransactions(accountId: Long)

    @Query("UPDATE transactions SET accountId = :newAccountId WHERE id IN (:transactionIds)")
    suspend fun updateAccountIdForTransactionIds(transactionIds: List<Long>, newAccountId: Long)

    @Query("SELECT DISTINCT reference FROM transactions WHERE accountId = :accountId AND reference IS NOT NULL AND reference != ''")
    suspend fun getDistinctReferencesForAccount(accountId: Long): List<String>

    @Query("SELECT DISTINCT upiRef FROM transactions WHERE accountId = :accountId AND upiRef IS NOT NULL AND upiRef != ''")
    suspend fun getDistinctUpiRefsForAccount(accountId: Long): List<String>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getTransactionsByIdsSync(ids: List<Long>): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<Long>)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun updateCategoryIdForTransactionIds(ids: List<Long>, categoryId: Long?)

    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateCategoryIdForTransactions(oldCategoryId: Long, newCategoryId: Long?)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsByCategoryId(categoryId: Long): Int

    @Query("UPDATE transactions SET friendlyVendorName = :newName WHERE friendlyVendorName = :oldName")
    suspend fun updateVendorNameForTransactions(oldName: String, newName: String)

    @Query("UPDATE transactions SET friendlyVendorName = :newName, categoryId = COALESCE(:newCategoryId, categoryId) WHERE friendlyVendorName = :oldName")
    suspend fun updateVendorNameAndCategoryForTransactions(oldName: String, newName: String, newCategoryId: Long?)

    @Query("SELECT COUNT(*) FROM transactions WHERE friendlyVendorName = :vendorName")
    suspend fun countTransactionsByVendorName(vendorName: String): Int

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    // Aggregations in Paise
    @Query("SELECT SUM(myActualSharePaise) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalMyExpensePaise(): Flow<Long?>

    @Query("SELECT SUM(amountPaise) FROM transactions WHERE type = 'EXPENSE' AND paidByPersonId IS NULL")
    fun getTotalGrossExpensePaise(): Flow<Long?>

    @Query("SELECT SUM(amountPaise) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncomePaise(): Flow<Long?>

    @Query("SELECT SUM(amountPaise) FROM transactions WHERE type = 'CC_BILL_TRANSFER'")
    fun getTotalCcBillPaymentsPaise(): Flow<Long?>

    @Query("SELECT SUM(amountPaise) FROM transactions WHERE paymentMode = :mode AND type = 'EXPENSE'")
    fun getTotalSpentOnPaymentModePaise(mode: String): Flow<Long?>

    // Backward-compatibility delegating flows
    fun getTotalMyExpense(): Flow<Double?> = getTotalMyExpensePaise().map { it?.let { p -> CurrencyUtils.paiseToRupees(p) } }

    fun getTotalGrossExpense(): Flow<Double?> = getTotalGrossExpensePaise().map { it?.let { p -> CurrencyUtils.paiseToRupees(p) } }

    fun getTotalIncome(): Flow<Double?> = getTotalIncomePaise().map { it?.let { p -> CurrencyUtils.paiseToRupees(p) } }

    fun getTotalCcBillPayments(): Flow<Double?> = getTotalCcBillPaymentsPaise().map { it?.let { p -> CurrencyUtils.paiseToRupees(p) } }

    fun getTotalSpentOnPaymentMode(mode: String): Flow<Double?> = getTotalSpentOnPaymentModePaise(mode).map { it?.let { p -> CurrencyUtils.paiseToRupees(p) } }

    // True Personal Spend Analytics (type = 'EXPENSE' only, strictly excluding splits and CC bills)
    @Query("SELECT SUM(myActualSharePaise) FROM transactions WHERE categoryId = :categoryId AND type = 'EXPENSE' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getMonthSpendForCategoryPaise(categoryId: Long, start: Long, end: Long): Long?

    suspend fun getMonthSpendForCategory(categoryId: Long, start: Long, end: Long): Double? {
        return getMonthSpendForCategoryPaise(categoryId, start, end)?.let { CurrencyUtils.paiseToRupees(it) }
    }

    @Query("SELECT SUM(myActualSharePaise) FROM transactions WHERE type = 'EXPENSE' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getMonthTotalMyExpensePaise(start: Long, end: Long): Long?

    suspend fun getMonthTotalMyExpense(start: Long, end: Long): Double? {
        return getMonthTotalMyExpensePaise(start, end)?.let { CurrencyUtils.paiseToRupees(it) }
    }

    @Query("SELECT SUM(myActualSharePaise) FROM transactions WHERE type = 'EXPENSE' AND timestamp >= :start AND timestamp <= :end")
    fun getMonthTotalMyExpenseFlowPaise(start: Long, end: Long): Flow<Long?>

    fun getMonthTotalMyExpenseFlow(start: Long, end: Long): Flow<Double?> = getMonthTotalMyExpenseFlowPaise(start, end).map {
        it?.let { p -> CurrencyUtils.paiseToRupees(p) }
    }

    @Query("""
        SELECT 
            COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other') AS vendorName,
            COUNT(id) AS transactionCount,
            COALESCE(SUM(myActualSharePaise), 0) AS totalSpentPaise
        FROM transactions
        WHERE type = 'EXPENSE' AND timestamp >= :start AND timestamp <= :end
        GROUP BY COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other')
        ORDER BY totalSpentPaise DESC
    """)
    fun getVendorSpendingBetween(start: Long, end: Long): Flow<List<VendorSpendingItem>>

    @Query("""
        SELECT 
            COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other') AS vendorName,
            COUNT(id) AS transactionCount,
            COALESCE(SUM(myActualSharePaise), 0) AS totalSpentPaise
        FROM transactions
        WHERE type = 'EXPENSE' AND timestamp >= :start AND timestamp <= :end
        GROUP BY COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other')
        ORDER BY totalSpentPaise DESC
    """)
    suspend fun getVendorSpendingBetweenSync(start: Long, end: Long): List<VendorSpendingItem>

    @Query("""
        SELECT 
            COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other') AS vendorName,
            COUNT(id) AS transactionCount,
            COALESCE(SUM(myActualSharePaise), 0) AS totalSpentPaise
        FROM transactions
        WHERE type = 'EXPENSE' AND timestamp >= :sinceTimestamp
        GROUP BY COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other')
        HAVING COUNT(id) > 5
        ORDER BY transactionCount DESC
    """)
    fun getHighFrequencyLeaksFlow(sinceTimestamp: Long): Flow<List<VendorSpendingItem>>

    @Query("""
        SELECT 
            COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other') AS vendorName,
            COUNT(id) AS transactionCount,
            COALESCE(SUM(myActualSharePaise), 0) AS totalSpentPaise
        FROM transactions
        WHERE type = 'EXPENSE' AND timestamp >= :sinceTimestamp
        GROUP BY COALESCE(NULLIF(friendlyVendorName, ''), NULLIF(rawMerchant, ''), 'Other')
        HAVING COUNT(id) > 5
        ORDER BY transactionCount DESC
    """)
    suspend fun getHighFrequencyLeaksSync(sinceTimestamp: Long): List<VendorSpendingItem>
}
