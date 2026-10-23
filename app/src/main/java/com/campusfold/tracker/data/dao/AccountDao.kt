package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.BalanceSource
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY displayName ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccountsIncludingArchived(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY id ASC")
    suspend fun getActiveAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE bankKey = :bankKey AND last4 = :last4 LIMIT 1")
    suspend fun findByBankKeyAndLast4(bankKey: String, last4: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE isArchived = 0")
    suspend fun getAccountCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("UPDATE accounts SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean)

    @Query("UPDATE accounts SET balancePaise = :balancePaise, balanceAsOf = :balanceAsOf, balanceSource = :source WHERE id = :id")
    suspend fun updateBalance(id: Long, balancePaise: Long, balanceAsOf: Long, source: BalanceSource)
}
