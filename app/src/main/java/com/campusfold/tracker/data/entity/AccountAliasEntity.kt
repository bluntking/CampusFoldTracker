package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "account_aliases",
    primaryKeys = ["accountId", "bankKey", "last4"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bankKey", "last4"]),
        Index(value = ["accountId"])
    ]
)
data class AccountAliasEntity(
    val accountId: Long,
    val bankKey: String,
    val last4: String
)
