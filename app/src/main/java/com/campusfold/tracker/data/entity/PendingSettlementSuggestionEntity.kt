package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.campusfold.tracker.util.CurrencyUtils

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
    val amountPaise: Long,
    val status: SettlementSuggestionStatus = SettlementSuggestionStatus.PENDING,
    val direction: SplitDirection = SplitDirection.OWED_TO_ME
) {
    // Backward-compatibility accessor
    val amount: Double get() = amountPaise / 100.0

    @Ignore
    constructor(
        id: Long = 0,
        transactionId: Long? = null,
        suggestedPersonId: Long,
        detectedSenderName: String,
        amount: Double,
        status: SettlementSuggestionStatus = SettlementSuggestionStatus.PENDING
    ) : this(
        id = id,
        transactionId = transactionId,
        suggestedPersonId = suggestedPersonId,
        detectedSenderName = detectedSenderName,
        amountPaise = CurrencyUtils.rupeesToPaise(amount),
        status = status
    )
}
