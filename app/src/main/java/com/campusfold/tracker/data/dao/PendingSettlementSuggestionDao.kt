package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.PendingSettlementSuggestionEntity
import com.campusfold.tracker.data.entity.SettlementSuggestionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSettlementSuggestionDao {

    @Query("SELECT * FROM pending_settlement_suggestions WHERE status = 'PENDING' ORDER BY id DESC")
    fun getPendingSuggestions(): Flow<List<PendingSettlementSuggestionEntity>>

    @Query("SELECT * FROM pending_settlement_suggestions WHERE id = :id LIMIT 1")
    suspend fun getSuggestionById(id: Long): PendingSettlementSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: PendingSettlementSuggestionEntity): Long

    @Query("UPDATE pending_settlement_suggestions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SettlementSuggestionStatus)

    @Delete
    suspend fun deleteSuggestion(suggestion: PendingSettlementSuggestionEntity)

    @Query("DELETE FROM pending_settlement_suggestions WHERE id = :id")
    suspend fun deleteSuggestionById(id: Long)

    @Query("DELETE FROM pending_settlement_suggestions")
    suspend fun clearAll()
}
