package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.AccountAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountAliasDao {

    @Query("SELECT * FROM account_aliases WHERE accountId = :accountId")
    fun getAliasesForAccount(accountId: Long): Flow<List<AccountAliasEntity>>

    @Query("SELECT * FROM account_aliases WHERE accountId = :accountId")
    suspend fun getAliasesForAccountSync(accountId: Long): List<AccountAliasEntity>

    @Query("SELECT accountId FROM account_aliases WHERE bankKey = :bankKey AND last4 = :last4 LIMIT 1")
    suspend fun findAccountIdByAlias(bankKey: String, last4: String): Long?

    @Query("SELECT * FROM account_aliases")
    suspend fun getAllAliasesSync(): List<AccountAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: AccountAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<AccountAliasEntity>)

    @Delete
    suspend fun deleteAlias(alias: AccountAliasEntity)

    @Query("DELETE FROM account_aliases WHERE accountId = :accountId")
    suspend fun deleteAliasesForAccount(accountId: Long)

    @Query("UPDATE account_aliases SET accountId = :newAccountId WHERE accountId = :oldAccountId")
    suspend fun reassignAliases(oldAccountId: Long, newAccountId: Long)

    @Query("DELETE FROM account_aliases WHERE LOWER(bankKey) = LOWER(:bankKey) AND last4 = :last4")
    suspend fun deleteAliasByBankKeyAndLast4(bankKey: String, last4: String)
}
