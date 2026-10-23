package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_items",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val payloadJson: String,
    val reason: String,
    val confidence: Float,
    val origin: ReviewOrigin = ReviewOrigin.RULES,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
