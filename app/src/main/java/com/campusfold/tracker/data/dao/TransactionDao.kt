package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.model.TransactionWithSplits
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    // Aggregations
    @Query("SELECT SUM(myActualShare) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalMyExpense(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalGrossExpense(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CC_BILL_TRANSFER'")
    fun getTotalCcBillPayments(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE paymentMode = :mode AND type = 'EXPENSE'")
    fun getTotalSpentOnPaymentMode(mode: String): Flow<Double?>
}
