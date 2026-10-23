package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "not_same_pairs",
    primaryKeys = ["accountIdA", "accountIdB"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountIdA"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountIdB"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountIdA"]),
        Index(value = ["accountIdB"])
    ]
)
data class NotSamePairEntity(
    val accountIdA: Long,
    val accountIdB: Long
)
