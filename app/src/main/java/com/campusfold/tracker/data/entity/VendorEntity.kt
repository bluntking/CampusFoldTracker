package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendors",
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
        Index(value = ["normalizedKey"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class VendorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val normalizedKey: String,
    val categoryId: Long? = null,
    val editedFields: String = "[]"
) {
    // Backward-compatibility accessors
    val friendlyName: String get() = displayName
    val rawSmsKeyword: String get() = normalizedKey

    @Ignore
    constructor(
        id: Long = 0,
        rawSmsKeyword: String,
        friendlyName: String,
        categoryId: Long? = null
    ) : this(
        id = id,
        displayName = friendlyName,
        normalizedKey = rawSmsKeyword.trim().lowercase(),
        categoryId = categoryId,
        editedFields = "[]"
    )
}

/**
 * Typealias for backward compatibility with existing codebase.
 */
typealias VendorMappingEntity = VendorEntity
