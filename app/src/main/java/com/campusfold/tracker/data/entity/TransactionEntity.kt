package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
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
        Index(value = ["upiRef"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val myActualShare: Double,
    val type: TransactionType,
    val paymentMode: String, // e.g. 'PNB CC 8598', 'Axis Bank XX2003', 'Cash/Manual'
    val rawMerchant: String,
    val friendlyVendorName: String,
    val categoryId: Long? = null,
    val timestamp: Long,
    val upiRef: String? = null, // Unique index for deduplication
    val rawSms: String? = null,
    val description: String? = null
)
