package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.VendorMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorMappingDao {

    @Query("SELECT * FROM vendor_mappings ORDER BY friendlyName ASC")
    fun getAllVendorMappings(): Flow<List<VendorMappingEntity>>

    @Query("SELECT * FROM vendor_mappings")
    suspend fun getAllVendorMappingsList(): List<VendorMappingEntity>

    @Query("SELECT * FROM vendor_mappings WHERE id = :id LIMIT 1")
    suspend fun getVendorMappingById(id: Long): VendorMappingEntity?

    @Query("SELECT * FROM vendor_mappings WHERE LOWER(rawSmsKeyword) = LOWER(:keyword) LIMIT 1")
    suspend fun findByKeyword(keyword: String): VendorMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendorMapping(mapping: VendorMappingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVendorMappings(mappings: List<VendorMappingEntity>): List<Long>

    @Update
    suspend fun updateVendorMapping(mapping: VendorMappingEntity)

    @Delete
    suspend fun deleteVendorMapping(mapping: VendorMappingEntity)

    @Query("DELETE FROM vendor_mappings WHERE id = :id")
    suspend fun deleteVendorMappingById(id: Long)
}
