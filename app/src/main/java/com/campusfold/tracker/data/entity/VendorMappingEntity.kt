package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendor_mappings",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rawSmsKeyword"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class VendorMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawSmsKeyword: String,
    val friendlyName: String,
    val categoryId: Long? = null
)
