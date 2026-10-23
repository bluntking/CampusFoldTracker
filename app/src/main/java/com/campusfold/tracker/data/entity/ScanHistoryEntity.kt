package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_history",
    indices = [
        Index(value = ["startedAt"])
    ]
)
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val read: Int = 0,
    val added: Int = 0,
    val duplicatesSkipped: Int = 0,
    val sentToReview: Int = 0,
    val resolvedByAi: Int = 0
)
