package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.AiCacheEntity

@Dao
interface AiCacheDao {

    @Query("SELECT * FROM ai_cache WHERE inputHash = :inputHash LIMIT 1")
    suspend fun getByInputHash(inputHash: String): AiCacheEntity?

    @Query("SELECT * FROM ai_cache WHERE inputHash = :inputHash AND task = :task LIMIT 1")
    suspend fun getByInputHashAndTask(inputHash: String, task: String): AiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: AiCacheEntity)

    @Query("DELETE FROM ai_cache")
    suspend fun clearAll()
}
