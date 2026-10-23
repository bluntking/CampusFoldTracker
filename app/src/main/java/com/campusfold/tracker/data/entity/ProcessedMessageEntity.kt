package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processed_messages",
    indices = [
        Index(value = ["processedAt"])
    ]
)
data class ProcessedMessageEntity(
    @PrimaryKey
    val bodyHash: String,
    val source: String,
    val sourceId: String? = null,
    val outcome: String,
    val processedAt: Long = System.currentTimeMillis()
)
