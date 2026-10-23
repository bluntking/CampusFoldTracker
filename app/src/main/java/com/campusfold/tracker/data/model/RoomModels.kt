package com.campusfold.tracker.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.util.CurrencyUtils

/**
 * Calculated projection for a Person and their dynamic balance owed to me.
 * Guaranteed never to drift out of sync with actual split item rows.
 */
data class PersonWithBalance(
    val id: Long,
    val name: String,
    val exactSmsAliases: String,
    val totalOwedToMePaise: Long
) {
    val totalOwedToMe: Double get() = totalOwedToMePaise / 100.0
    val netBalance: Double get() = totalOwedToMe

    constructor(
        id: Long,
        name: String,
        exactSmsAliases: String,
        totalOwedToMe: Double
    ) : this(
        id = id,
        name = name,
        exactSmsAliases = exactSmsAliases,
        totalOwedToMePaise = CurrencyUtils.rupeesToPaise(totalOwedToMe)
    )
}

/**
 * Item-by-item unsettled bill projection for Splits UI.
 */
data class UnsettledBillItem(
    val splitId: Long,
    val transactionId: Long,
    val personId: Long,
    val vendorName: String,
    val transactionTotalPaise: Long,
    val amountOwedPaise: Long,
    val amountPaidSoFarPaise: Long,
    val isSettled: Boolean,
    val createdAt: Long
) {
    val transactionTotal: Double get() = transactionTotalPaise / 100.0
    val amountOwed: Double get() = amountOwedPaise / 100.0
    val amountPaidSoFar: Double get() = amountPaidSoFarPaise / 100.0

    constructor(
        splitId: Long,
        transactionId: Long,
        personId: Long,
        vendorName: String,
        transactionTotal: Double,
        amountOwed: Double,
        amountPaidSoFar: Double,
        isSettled: Boolean,
        createdAt: Long
    ) : this(
        splitId = splitId,
        transactionId = transactionId,
        personId = personId,
        vendorName = vendorName,
        transactionTotalPaise = CurrencyUtils.rupeesToPaise(transactionTotal),
        amountOwedPaise = CurrencyUtils.rupeesToPaise(amountOwed),
        amountPaidSoFarPaise = CurrencyUtils.rupeesToPaise(amountPaidSoFar),
        isSettled = isSettled,
        createdAt = createdAt
    )
}

/**
 * Result of FIFO settlement processing (partial payments, full payments & overpayments).
 */
data class SettlementResult(
    val friendName: String,
    val settledAmountPaise: Long,
    val remainingOwedPaise: Long,
    val excessAmountPaise: Long = 0L,
    val updatedSplitCount: Int
) {
    val settledAmount: Double get() = settledAmountPaise / 100.0
    val remainingOwed: Double get() = remainingOwedPaise / 100.0
    val excessAmount: Double get() = excessAmountPaise / 100.0

    constructor(
        friendName: String,
        settledAmount: Double,
        remainingOwed: Double,
        excessAmount: Double = 0.0,
        updatedSplitCount: Int
    ) : this(
        friendName = friendName,
        settledAmountPaise = CurrencyUtils.rupeesToPaise(settledAmount),
        remainingOwedPaise = CurrencyUtils.rupeesToPaise(remainingOwed),
        excessAmountPaise = CurrencyUtils.rupeesToPaise(excessAmount),
        updatedSplitCount = updatedSplitCount
    )
}

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
    val color: String,
    val budgetPaise: Long?,
    val totalSpentPaise: Long
) {
    val colorHex: String get() = color
    val monthlyBudget: Double get() = (budgetPaise ?: 0L) / 100.0
    val totalSpent: Double get() = totalSpentPaise / 100.0
}

/**
 * Campus vendor spending breakdown for true personal expenses.
 */
data class VendorSpendingItem(
    val vendorName: String,
    val transactionCount: Int,
    val totalSpentPaise: Long
) {
    val totalSpent: Double get() = totalSpentPaise / 100.0

    constructor(
        vendorName: String,
        transactionCount: Int,
        totalSpent: Double
    ) : this(
        vendorName = vendorName,
        transactionCount = transactionCount,
        totalSpentPaise = CurrencyUtils.rupeesToPaise(totalSpent)
    )
}

/**
 * Category spending within a specific date range.
 */
data class CategoryPeriodSpend(
    val categoryId: Long,
    val categoryName: String,
    val color: String,
    val totalSpentPaise: Long
) {
    val colorHex: String get() = color
    val totalSpent: Double get() = totalSpentPaise / 100.0
}

/**
 * Burn Rate Warning: % of budget spent is significantly ahead of % of days elapsed in the month.
 */
data class BurnRateInsight(
    val categoryName: String,
    val colorHex: String,
    val spentPaise: Long,
    val budgetPaise: Long,
    val spentPercent: Double,
    val daysElapsedPercent: Double,
    val pacingAheadPercent: Double
) {
    val spent: Double get() = spentPaise / 100.0
    val budget: Double get() = budgetPaise / 100.0

    constructor(
        categoryName: String,
        colorHex: String,
        spent: Double,
        budget: Double,
        spentPercent: Double,
        daysElapsedPercent: Double,
        pacingAheadPercent: Double
    ) : this(
        categoryName = categoryName,
        colorHex = colorHex,
        spentPaise = CurrencyUtils.rupeesToPaise(spent),
        budgetPaise = CurrencyUtils.rupeesToPaise(budget),
        spentPercent = spentPercent,
        daysElapsedPercent = daysElapsedPercent,
        pacingAheadPercent = pacingAheadPercent
    )
}

/**
 * High-Frequency Leak: Vendors with >5 transactions in the current week.
 */
data class HighFrequencyLeak(
    val vendorName: String,
    val transactionCount: Int,
    val totalSpentPaise: Long
) {
    val totalSpent: Double get() = totalSpentPaise / 100.0

    constructor(
        vendorName: String,
        transactionCount: Int,
        totalSpent: Double
    ) : this(
        vendorName = vendorName,
        transactionCount = transactionCount,
        totalSpentPaise = CurrencyUtils.rupeesToPaise(totalSpent)
    )
}

/**
 * Week-over-Week Spike: Category with the highest ₹ increase compared to the previous 7 days.
 */
data class WeekOverWeekSpike(
    val categoryName: String,
    val colorHex: String,
    val currentWeekSpendPaise: Long,
    val previousWeekSpendPaise: Long,
    val increaseAmountPaise: Long,
    val percentIncrease: Double
) {
    val currentWeekSpend: Double get() = currentWeekSpendPaise / 100.0
    val previousWeekSpend: Double get() = previousWeekSpendPaise / 100.0
    val increaseAmount: Double get() = increaseAmountPaise / 100.0

    constructor(
        categoryName: String,
        colorHex: String,
        currentWeekSpend: Double,
        previousWeekSpend: Double,
        increaseAmount: Double,
        percentIncrease: Double
    ) : this(
        categoryName = categoryName,
        colorHex = colorHex,
        currentWeekSpendPaise = CurrencyUtils.rupeesToPaise(currentWeekSpend),
        previousWeekSpendPaise = CurrencyUtils.rupeesToPaise(previousWeekSpend),
        increaseAmountPaise = CurrencyUtils.rupeesToPaise(increaseAmount),
        percentIncrease = percentIncrease
    )
}

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
