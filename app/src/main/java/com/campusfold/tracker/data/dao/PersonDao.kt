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
     * Strictly dynamic calculation of balances owed to me.
     * Computes SUM(amountOwed - amountPaidSoFar) from unsettled split items.
     * ZERO data drift because no static column is stored.
     */
    @Query("""
        SELECT 
            p.id, 
            p.name, 
            p.exactSmsAliases,
            COALESCE(SUM(s.amountOwed - s.amountPaidSoFar), 0.0) AS totalOwedToMe
        FROM persons p
        LEFT JOIN split_items s ON p.id = s.personId AND s.isSettled = 0
        GROUP BY p.id
        ORDER BY totalOwedToMe DESC, p.name ASC
    """)
    fun getPersonsWithBalances(): Flow<List<PersonWithBalance>>

    @Query("""
        SELECT 
            p.id, 
            p.name, 
            p.exactSmsAliases,
            COALESCE(SUM(s.amountOwed - s.amountPaidSoFar), 0.0) AS totalOwedToMe
        FROM persons p
        LEFT JOIN split_items s ON p.id = s.personId AND s.isSettled = 0
        WHERE p.id = :personId
        GROUP BY p.id
    """)
    suspend fun getPersonWithBalance(personId: Long): PersonWithBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePersonById(id: Long)
}
