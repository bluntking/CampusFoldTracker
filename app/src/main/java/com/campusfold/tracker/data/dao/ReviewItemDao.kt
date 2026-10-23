package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.ReviewItemEntity
import com.campusfold.tracker.data.entity.ReviewStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewItemDao {

    @Query("SELECT * FROM review_items WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingReviewItems(): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_items ORDER BY createdAt DESC")
    fun getAllReviewItems(): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_items WHERE id = :id LIMIT 1")
    suspend fun getReviewItemById(id: Long): ReviewItemEntity?

    @Query("SELECT * FROM review_items")
    suspend fun getAllReviewItemsSync(): List<ReviewItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewItem(item: ReviewItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewItems(items: List<ReviewItemEntity>): List<Long>

    @Update
    suspend fun updateReviewItem(item: ReviewItemEntity)

    @Query("UPDATE review_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ReviewStatus)

    @Query("UPDATE review_items SET status = 'DISCARDED' WHERE status = 'PENDING'")
    suspend fun discardAllPending()

    @Query("SELECT * FROM review_items WHERE status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingReviewItemsSync(): List<ReviewItemEntity>

    @Delete
    suspend fun deleteReviewItem(item: ReviewItemEntity)

    @Query("DELETE FROM review_items WHERE id = :id")
    suspend fun deleteReviewItemById(id: Long)
}
