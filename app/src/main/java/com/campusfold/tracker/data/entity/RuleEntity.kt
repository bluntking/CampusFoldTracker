package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rules",
    indices = [
        Index(value = ["priority"]),
        Index(value = ["enabled"])
    ]
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conditionsJson: String,
    val actionsJson: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val source: RuleSource = RuleSource.USER
)
