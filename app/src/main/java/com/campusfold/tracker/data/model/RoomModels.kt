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
 * Item-by-item unsettled bill projection for Splits UI.
 */
data class UnsettledBillItem(
    val splitId: Long,
    val transactionId: Long,
    val personId: Long,
    val vendorName: String,
    val transactionTotal: Double,
    val amountOwed: Double,
    val amountPaidSoFar: Double,
    val isSettled: Boolean,
    val createdAt: Long
)

/**
 * Result of FIFO settlement processing (partial payments, full payments & overpayments).
 */
data class SettlementResult(
    val friendName: String,
    val settledAmount: Double,
    val remainingOwed: Double,
    val excessAmount: Double = 0.0,
    val updatedSplitCount: Int
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
