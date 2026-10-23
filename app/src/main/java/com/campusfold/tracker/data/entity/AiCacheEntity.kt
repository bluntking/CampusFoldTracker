package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_cache",
    indices = [
        Index(value = ["task"]),
        Index(value = ["createdAt"])
    ]
)
data class AiCacheEntity(
    @PrimaryKey
    val inputHash: String,
    val task: String,
    val resultJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
