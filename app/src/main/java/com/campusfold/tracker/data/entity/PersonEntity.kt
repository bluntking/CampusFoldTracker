package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Person entity for split-bill tracking.
 * NOTE: Does NOT store a 'totalOwedToMe' column. Unsettled balances are always
 * derived dynamically via SQL queries against unsettled SplitItemEntity rows
 * to strictly prevent balance drift.
 */
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val exactSmsAliases: String // Comma-separated exact UPI / sender names
)
