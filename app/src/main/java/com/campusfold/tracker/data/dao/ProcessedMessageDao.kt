package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.ProcessedMessageEntity

@Dao
interface ProcessedMessageDao {

    @Query("SELECT COUNT(*) > 0 FROM processed_messages WHERE bodyHash = :bodyHash")
    suspend fun isProcessed(bodyHash: String): Boolean

    @Query("SELECT * FROM processed_messages WHERE bodyHash = :bodyHash LIMIT 1")
    suspend fun getByBodyHash(bodyHash: String): ProcessedMessageEntity?

    @Query("SELECT * FROM processed_messages")
    suspend fun getAllProcessedMessagesSync(): List<ProcessedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessed(message: ProcessedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedMessages(messages: List<ProcessedMessageEntity>)

    @Query("DELETE FROM processed_messages")
    suspend fun clearAll()
}
