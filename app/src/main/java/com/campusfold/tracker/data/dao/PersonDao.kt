package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.model.PersonWithBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY name ASC")
    suspend fun getAllPersonsList(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): PersonEntity?

    /**
     * Dynamic calculation of balances owed to me and owed by me in paise.
     * Computes SUM(amountOwedPaise - amountPaidSoFarPaise) from unsettled split items by direction.
     * Zero data drift because no static column is stored.
     */
    @Query("""
        SELECT 
            p.id, 
            p.name, 
            p.exactSmsAliases,
            COALESCE(SUM(CASE WHEN s.direction = 'OWED_TO_ME' THEN s.amountOwedPaise - s.amountPaidSoFarPaise ELSE 0 END), 0) AS totalOwedToMePaise,
            COALESCE(SUM(CASE WHEN s.direction = 'OWED_BY_ME' THEN s.amountOwedPaise - s.amountPaidSoFarPaise ELSE 0 END), 0) AS totalOwedByMePaise
        FROM persons p
        LEFT JOIN split_items s ON p.id = s.personId AND s.isSettled = 0
        GROUP BY p.id
        ORDER BY (totalOwedToMePaise + totalOwedByMePaise) DESC, p.name ASC
    """)
    fun getPersonsWithBalances(): Flow<List<PersonWithBalance>>

    @Query("""
        SELECT 
            p.id, 
            p.name, 
            p.exactSmsAliases,
            COALESCE(SUM(CASE WHEN s.direction = 'OWED_TO_ME' THEN s.amountOwedPaise - s.amountPaidSoFarPaise ELSE 0 END), 0) AS totalOwedToMePaise,
            COALESCE(SUM(CASE WHEN s.direction = 'OWED_BY_ME' THEN s.amountOwedPaise - s.amountPaidSoFarPaise ELSE 0 END), 0) AS totalOwedByMePaise
        FROM persons p
        LEFT JOIN split_items s ON p.id = s.personId AND s.isSettled = 0
        WHERE p.id = :personId
        GROUP BY p.id
    """)
    suspend fun getPersonWithBalance(personId: Long): PersonWithBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersons(persons: List<PersonEntity>): List<Long>

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePersonById(id: Long)
}
