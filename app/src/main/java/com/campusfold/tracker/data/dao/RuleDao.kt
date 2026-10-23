package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getActiveRulesSync(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): RuleEntity?

    @Query("SELECT * FROM rules")
    suspend fun getAllRulesSync(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>): List<Long>

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
