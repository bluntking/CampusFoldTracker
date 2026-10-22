package com.campusfold.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp", "amount", "merchant"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val merchant: String,
    val category: String,
    val bank: String,
    val timestamp: Long,
    val rawBody: String,
    val sender: String
)
