package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.NotSamePairEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotSamePairDao {

    @Query("SELECT * FROM not_same_pairs")
    fun getAllNotSamePairs(): Flow<List<NotSamePairEntity>>

    @Query("SELECT * FROM not_same_pairs")
    suspend fun getAllNotSamePairsSync(): List<NotSamePairEntity>

    @Query("SELECT COUNT(*) > 0 FROM not_same_pairs WHERE (accountIdA = :idA AND accountIdB = :idB) OR (accountIdA = :idB AND accountIdB = :idA)")
    suspend fun isPairMarkedNotSame(idA: Long, idB: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPair(pair: NotSamePairEntity)

    @Delete
    suspend fun deletePair(pair: NotSamePairEntity)
}
