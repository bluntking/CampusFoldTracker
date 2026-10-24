package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.campusfold.tracker.util.CurrencyUtils

enum class SplitDirection {
    OWED_TO_ME,
    OWED_BY_ME
}

@Entity(
    tableName = "split_items",
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
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["personId"]),
        Index(value = ["isSettled"])
    ]
)
data class SplitItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val personId: Long,
    val amountOwedPaise: Long,
    val amountPaidSoFarPaise: Long = 0L,
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val direction: SplitDirection = SplitDirection.OWED_TO_ME
) {
    // Backward-compatibility accessors
    val amountOwed: Double get() = amountOwedPaise / 100.0
    val amountPaidSoFar: Double get() = amountPaidSoFarPaise / 100.0

    @Ignore
    constructor(
        id: Long = 0,
        transactionId: Long,
        personId: Long,
        amountOwed: Double,
        amountPaidSoFar: Double = 0.0,
        isSettled: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        transactionId = transactionId,
        personId = personId,
        amountOwedPaise = CurrencyUtils.rupeesToPaise(amountOwed),
        amountPaidSoFarPaise = CurrencyUtils.rupeesToPaise(amountPaidSoFar),
        isSettled = isSettled,
        createdAt = createdAt
    )
}
