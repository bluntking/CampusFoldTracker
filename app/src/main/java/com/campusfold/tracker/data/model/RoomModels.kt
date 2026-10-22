package com.campusfold.tracker.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity

/**
 * Calculated projection for a Person and their dynamic balance owed to me.
 * Guaranteed never to drift out of sync with actual split item rows.
 */
data class PersonWithBalance(
    val id: Long,
    val name: String,
    val exactSmsAliases: String,
    val totalOwedToMe: Double
)

/**
 * Complete transaction with all associated split shares.
 */
data class TransactionWithSplits(
    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val splits: List<SplitItemEntity>
)

/**
 * Split item joined with person information.
 */
data class SplitItemWithPerson(
    @Embedded
    val splitItem: SplitItemEntity,

    @Relation(
        parentColumn = "personId",
        entityColumn = "id"
    )
    val person: PersonEntity
)

/**
 * Category spending summary against monthly budget.
 */
data class CategorySpending(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val monthlyBudget: Double,
    val totalSpent: Double
)
