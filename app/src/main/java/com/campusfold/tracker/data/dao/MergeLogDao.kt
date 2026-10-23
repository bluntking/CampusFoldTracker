package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.MergeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MergeLogDao {

    @Query("SELECT * FROM merge_log ORDER BY createdAt DESC")
    fun getAllMergeLogs(): Flow<List<MergeLogEntity>>

    @Query("SELECT * FROM merge_log WHERE id = :id LIMIT 1")
    suspend fun getMergeLogById(id: Long): MergeLogEntity?

    @Query("SELECT * FROM merge_log")
    suspend fun getAllMergeLogsSync(): List<MergeLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMergeLog(log: MergeLogEntity): Long

    @Update
    suspend fun updateMergeLog(log: MergeLogEntity)

    @Query("SELECT * FROM merge_log WHERE mode = 'AUTO' AND undoneAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    fun getLatestActiveAutoMergeLogFlow(): Flow<MergeLogEntity?>

    @Query("SELECT * FROM merge_log WHERE mode = 'AUTO' AND undoneAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestActiveAutoMergeLogSync(): MergeLogEntity?

    @Query("SELECT * FROM merge_log WHERE survivingAccountId = :accountId AND undoneAt IS NULL ORDER BY createdAt DESC")
    fun getActiveMergeLogsForAccount(accountId: Long): Flow<List<MergeLogEntity>>

    @Query("SELECT * FROM merge_log WHERE survivingAccountId = :accountId AND undoneAt IS NULL ORDER BY createdAt DESC")
    suspend fun getActiveMergeLogsForAccountSync(accountId: Long): List<MergeLogEntity>

    @Query("SELECT * FROM merge_log WHERE undoneAt IS NULL ORDER BY createdAt DESC")
    fun getAllActiveMergeLogs(): Flow<List<MergeLogEntity>>

    @Query("SELECT * FROM merge_log WHERE undoneAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllActiveMergeLogsSync(): List<MergeLogEntity>

    @Query("UPDATE merge_log SET undoneAt = :undoneAt WHERE id = :id")
    suspend fun markUndone(id: Long, undoneAt: Long = System.currentTimeMillis())
}
