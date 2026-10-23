package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Query("SELECT * FROM scan_history ORDER BY startedAt DESC")
    fun getAllScanHistory(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    suspend fun getScanHistoryById(id: Long): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history")
    suspend fun getAllScanHistorySync(): List<ScanHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanHistory(scan: ScanHistoryEntity): Long

    @Update
    suspend fun updateScanHistory(scan: ScanHistoryEntity)

    @Delete
    suspend fun deleteScanHistory(scan: ScanHistoryEntity)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
