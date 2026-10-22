package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SettlementSuggestionStatus {
    PENDING,
    CONFIRMED,
    DISMISSED
}

@Entity(
    tableName = "pending_settlement_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggestedPersonId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["suggestedPersonId"]),
        Index(value = ["status"])
    ]
)
data class PendingSettlementSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long? = null,
    val suggestedPersonId: Long,
    val detectedSenderName: String,
    val amount: Double,
    val status: SettlementSuggestionStatus = SettlementSuggestionStatus.PENDING
)
