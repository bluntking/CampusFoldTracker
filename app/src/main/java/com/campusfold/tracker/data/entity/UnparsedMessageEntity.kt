package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unparsed_messages",
    indices = [
        Index(value = ["bodyHash"]),
        Index(value = ["receivedAt"])
    ]
)
data class UnparsedMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    val sender: String,
    val receivedAt: Long,
    val body: String,
    val bodyHash: String
)
