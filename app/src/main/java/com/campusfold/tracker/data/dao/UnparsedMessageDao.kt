package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.UnparsedMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedMessageDao {

    @Query("SELECT * FROM unparsed_messages ORDER BY receivedAt DESC")
    fun getAllUnparsedMessages(): Flow<List<UnparsedMessageEntity>>

    @Query("SELECT * FROM unparsed_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UnparsedMessageEntity?

    @Query("SELECT * FROM unparsed_messages WHERE bodyHash = :hash LIMIT 1")
    suspend fun getByBodyHash(hash: String): UnparsedMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: UnparsedMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<UnparsedMessageEntity>): List<Long>

    @Query("DELETE FROM unparsed_messages WHERE receivedAt < :cutoffTimestamp")
    suspend fun purgeOlderThan(cutoffTimestamp: Long): Int

    suspend fun purgeOlderThan90Days(): Int {
        val ninetyDaysMillis = 90L * 24 * 60 * 60 * 1000
        return purgeOlderThan(System.currentTimeMillis() - ninetyDaysMillis)
    }

    @Delete
    suspend fun deleteMessage(message: UnparsedMessageEntity)

    @Query("DELETE FROM unparsed_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM unparsed_messages")
    suspend fun clearAll()
}
