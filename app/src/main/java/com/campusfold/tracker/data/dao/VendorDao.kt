package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.VendorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {

    @Query("SELECT * FROM vendors ORDER BY displayName ASC")
    fun getAllVendors(): Flow<List<VendorEntity>>

    @Query("SELECT * FROM vendors")
    suspend fun getAllVendorsList(): List<VendorEntity>

    @Query("SELECT * FROM vendors WHERE id = :id LIMIT 1")
    suspend fun getVendorById(id: Long): VendorEntity?

    @Query("SELECT * FROM vendors WHERE LOWER(normalizedKey) = LOWER(:key) LIMIT 1")
    suspend fun findByNormalizedKey(key: String): VendorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: VendorEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVendors(vendors: List<VendorEntity>): List<Long>

    @Update
    suspend fun updateVendor(vendor: VendorEntity)

    @Delete
    suspend fun deleteVendor(vendor: VendorEntity)

    @Query("DELETE FROM vendors WHERE id = :id")
    suspend fun deleteVendorById(id: Long)

    @Query("UPDATE vendors SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateCategoryIdForVendors(oldCategoryId: Long, newCategoryId: Long?)

    // Backward-compatibility delegates
    fun getAllVendorMappings(): Flow<List<VendorEntity>> = getAllVendors()
    suspend fun getAllVendorMappingsList(): List<VendorEntity> = getAllVendorsList()
    suspend fun getVendorMappingById(id: Long): VendorEntity? = getVendorById(id)
    suspend fun findByKeyword(keyword: String): VendorEntity? = findByNormalizedKey(keyword.trim().lowercase())
    suspend fun insertVendorMapping(mapping: VendorEntity): Long = insertVendor(mapping)
    suspend fun insertVendorMappings(mappings: List<VendorEntity>): List<Long> = insertVendors(mappings)
    suspend fun updateVendorMapping(mapping: VendorEntity) = updateVendor(mapping)
    suspend fun deleteVendorMapping(mapping: VendorEntity) = deleteVendor(mapping)
    suspend fun deleteVendorMappingById(id: Long) = deleteVendorById(id)
}

typealias VendorMappingDao = VendorDao
