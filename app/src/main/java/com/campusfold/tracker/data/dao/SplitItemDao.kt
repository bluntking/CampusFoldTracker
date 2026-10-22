package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.SplitItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitItemDao {

    @Query("SELECT * FROM split_items WHERE transactionId = :transactionId ORDER BY id ASC")
    fun getSplitsForTransaction(transactionId: Long): Flow<List<SplitItemEntity>>

    @Query("SELECT * FROM split_items WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getSplitsForTransactionSync(transactionId: Long): List<SplitItemEntity>

    @Query("SELECT * FROM split_items WHERE personId = :personId AND isSettled = 0 ORDER BY createdAt ASC")
    fun getUnsettledSplitsForPerson(personId: Long): Flow<List<SplitItemEntity>>

    @Query("SELECT * FROM split_items WHERE personId = :personId AND isSettled = 0 ORDER BY createdAt ASC")
    suspend fun getUnsettledSplitsForPersonSync(personId: Long): List<SplitItemEntity>

    @Query("SELECT * FROM split_items WHERE isSettled = 0 ORDER BY createdAt DESC")
    fun getAllUnsettledSplits(): Flow<List<SplitItemEntity>>

    @Query("SELECT * FROM split_items WHERE id = :id LIMIT 1")
    suspend fun getSplitById(id: Long): SplitItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitItem(split: SplitItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitItems(splits: List<SplitItemEntity>): List<Long>

    @Update
    suspend fun updateSplitItem(split: SplitItemEntity)

    @Delete
    suspend fun deleteSplitItem(split: SplitItemEntity)

    @Query("DELETE FROM split_items WHERE transactionId = :transactionId")
    suspend fun deleteSplitsForTransaction(transactionId: Long)

    @Query("UPDATE split_items SET isSettled = :isSettled WHERE id = :id")
    suspend fun markSettled(id: Long, isSettled: Boolean)

    @Query("""
        UPDATE split_items 
        SET amountPaidSoFar = amountPaidSoFar + :additionalPayment,
            isSettled = CASE WHEN (amountPaidSoFar + :additionalPayment) >= amountOwed THEN 1 ELSE isSettled END
        WHERE id = :id
    """)
    suspend fun recordPayment(id: Long, additionalPayment: Double)
}
