package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val bankName: String,
    val accountType: String, // "CREDIT_CARD", "SAVINGS", "WALLET", "OTHER"
    val last4: String,
    val creditLimitOrBalance: Double = 0.0,
    val colorHex: String = "#10B981"
)
