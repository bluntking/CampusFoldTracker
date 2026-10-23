package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "merge_log",
    indices = [
        Index(value = ["survivingAccountId"])
    ]
)
data class MergeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val survivingAccountId: Long,
    val mergedAccountSnapshot: String, // JSON snapshot of the merged account
    val movedTransactionIds: String,   // JSON array of moved transaction IDs
    val mode: MergeMode = MergeMode.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val undoneAt: Long? = null
)
