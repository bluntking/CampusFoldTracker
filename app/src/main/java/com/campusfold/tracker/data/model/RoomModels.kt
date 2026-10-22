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

/**
 * Campus vendor spending breakdown for true personal expenses.
 */
data class VendorSpendingItem(
    val vendorName: String,
    val transactionCount: Int,
    val totalSpent: Double
)

/**
 * Category spending within a specific date range.
 */
data class CategoryPeriodSpend(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val totalSpent: Double
)

/**
 * Burn Rate Warning: % of budget spent is significantly ahead of % of days elapsed in the month.
 */
data class BurnRateInsight(
    val categoryName: String,
    val colorHex: String,
    val spent: Double,
    val budget: Double,
    val spentPercent: Double,
    val daysElapsedPercent: Double,
    val pacingAheadPercent: Double
)

/**
 * High-Frequency Leak: Vendors with >5 transactions in the current week.
 */
data class HighFrequencyLeak(
    val vendorName: String,
    val transactionCount: Int,
    val totalSpent: Double
)

/**
 * Week-over-Week Spike: Category with the highest ₹ increase compared to the previous 7 days.
 */
data class WeekOverWeekSpike(
    val categoryName: String,
    val colorHex: String,
    val currentWeekSpend: Double,
    val previousWeekSpend: Double,
    val increaseAmount: Double,
    val percentIncrease: Double
)

/**
 * Summary stats from a Gmail IMAP sync operation for Axis Bank.
 */
data class GmailSyncStats(
    val totalFound: Int,
    val importedDebits: Int,
    val importedCredits: Int,
    val autoSettledCredits: Int,
    val pendingSuggestionsCreated: Int,
    val skippedDuplicates: Int,
    val errorMessage: String? = null
)
