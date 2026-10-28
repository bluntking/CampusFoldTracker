package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.SplitDirection
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.model.UnsettledBillItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitItemDao {

    @Query("SELECT * FROM split_items WHERE transactionId = :transactionId ORDER BY id ASC")
    fun getSplitsForTransaction(transactionId: Long): Flow<List<SplitItemEntity>>

    @Query("SELECT * FROM split_items WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getSplitsForTransactionSync(transactionId: Long): List<SplitItemEntity>

    @Query("SELECT * FROM split_items WHERE personId = :personId AND direction = 'OWED_TO_ME' AND isSettled = 0 ORDER BY createdAt ASC")
    fun getUnsettledSplitsForPerson(personId: Long): Flow<List<SplitItemEntity>>

    @Query("SELECT * FROM split_items WHERE personId = :personId AND direction = 'OWED_TO_ME' AND isSettled = 0 ORDER BY createdAt ASC")
    suspend fun getUnsettledSplitsForPersonSync(personId: Long): List<SplitItemEntity>

    @Query("SELECT * FROM split_items WHERE personId = :personId AND direction = :direction AND isSettled = 0 ORDER BY createdAt ASC")
    suspend fun getUnsettledSplitsForPersonAndDirectionSync(personId: Long, direction: SplitDirection): List<SplitItemEntity>

    @Query("SELECT * FROM split_items WHERE isSettled = 0 ORDER BY createdAt DESC")
    fun getAllUnsettledSplits(): Flow<List<SplitItemEntity>>

    @Query("SELECT * FROM split_items ORDER BY id ASC")
    suspend fun getAllSplitsSync(): List<SplitItemEntity>

    @Query("SELECT * FROM split_items WHERE id = :id LIMIT 1")
    suspend fun getSplitById(id: Long): SplitItemEntity?

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'OWED_TO_ME' THEN (amountOwedPaise - amountPaidSoFarPaise) ELSE -(amountOwedPaise - amountPaidSoFarPaise) END), 0) FROM split_items WHERE personId = :personId AND isSettled = 0")
    fun getNetPendingBalanceForPerson(personId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'OWED_TO_ME' THEN (amountOwedPaise - amountPaidSoFarPaise) ELSE -(amountOwedPaise - amountPaidSoFarPaise) END), 0) FROM split_items WHERE personId = :personId AND isSettled = 0")
    suspend fun getNetPendingBalanceForPersonSync(personId: Long): Long

    @Query("""
        SELECT 
            s.id AS splitId,
            s.transactionId AS transactionId,
            s.personId AS personId,
            t.friendlyVendorName AS vendorName,
            t.amountPaise AS transactionTotalPaise,
            s.amountOwedPaise AS amountOwedPaise,
            s.amountPaidSoFarPaise AS amountPaidSoFarPaise,
            s.isSettled AS isSettled,
            s.createdAt AS createdAt,
            s.direction AS direction
        FROM split_items s
        INNER JOIN transactions t ON s.transactionId = t.id
        WHERE s.personId = :personId AND s.isSettled = 0
        ORDER BY s.createdAt ASC
    """)
    fun getUnsettledBillsForPerson(personId: Long): Flow<List<UnsettledBillItem>>

    @Query("""
        SELECT 
            s.id AS splitId,
            s.transactionId AS transactionId,
            s.personId AS personId,
            t.friendlyVendorName AS vendorName,
            t.amountPaise AS transactionTotalPaise,
            s.amountOwedPaise AS amountOwedPaise,
            s.amountPaidSoFarPaise AS amountPaidSoFarPaise,
            s.isSettled AS isSettled,
            s.createdAt AS createdAt,
            s.direction AS direction
        FROM split_items s
        INNER JOIN transactions t ON s.transactionId = t.id
        WHERE s.isSettled = 0
        ORDER BY s.createdAt ASC
    """)
    fun getAllUnsettledBills(): Flow<List<UnsettledBillItem>>

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

    @Query("UPDATE split_items SET transactionId = :newTxId WHERE transactionId = :oldTxId")
    suspend fun reassignSplits(oldTxId: Long, newTxId: Long)

    @Query("DELETE FROM split_items")
    suspend fun clearAll()

    @Query("UPDATE split_items SET isSettled = :isSettled WHERE id = :id")
    suspend fun markSettled(id: Long, isSettled: Boolean)

    @Query("UPDATE split_items SET amountPaidSoFarPaise = amountPaidSoFarPaise + :paymentAmountPaise WHERE id = :splitId")
    suspend fun recordPayment(splitId: Long, paymentAmountPaise: Long)
}
