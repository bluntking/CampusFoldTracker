package com.campusfold.tracker.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitDirection
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.util.CurrencyUtils

/**
 * Calculated projection for a Person and their dynamic balance owed to me / owed by me.
 * Guaranteed never to drift out of sync with actual split item rows.
 */
data class PersonWithBalance(
    val id: Long,
    val name: String,
    val exactSmsAliases: String,
    val totalOwedToMePaise: Long = 0L,
    val totalOwedByMePaise: Long = 0L
) {
    val totalOwedToMe: Double get() = totalOwedToMePaise / 100.0
    val totalOwedByMe: Double get() = totalOwedByMePaise / 100.0
    val netBalancePaise: Long get() = totalOwedToMePaise - totalOwedByMePaise
    val netBalance: Double get() = netBalancePaise / 100.0

    constructor(
        id: Long,
        name: String,
        exactSmsAliases: String,
        totalOwedToMe: Double
    ) : this(
        id = id,
        name = name,
        exactSmsAliases = exactSmsAliases,
        totalOwedToMePaise = CurrencyUtils.rupeesToPaise(totalOwedToMe),
        totalOwedByMePaise = 0L
    )

    constructor(
        id: Long,
        name: String,
        exactSmsAliases: String,
        totalOwedToMe: Double,
        totalOwedByMe: Double
    ) : this(
        id = id,
        name = name,
        exactSmsAliases = exactSmsAliases,
        totalOwedToMePaise = CurrencyUtils.rupeesToPaise(totalOwedToMe),
        totalOwedByMePaise = CurrencyUtils.rupeesToPaise(totalOwedByMe)
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
    val createdAt: Long,
    val direction: SplitDirection = SplitDirection.OWED_TO_ME
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
        createdAt = createdAt,
        direction = SplitDirection.OWED_TO_ME
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
 * Result of repaying a friend debt FIFO ("I paid them").
 */
data class RepaymentResult(
    val friendName: String,
    val debtSettledPaise: Long,
    val remainingDebtPaise: Long,
    val excessAsExpensePaise: Long = 0L,
    val updatedSplitCount: Int,
    val transactionId: Long = 0L
) {
    val debtSettled: Double get() = debtSettledPaise / 100.0
    val remainingDebt: Double get() = remainingDebtPaise / 100.0
    val excessAsExpense: Double get() = excessAsExpensePaise / 100.0
    val message: String
        get() = if (excessAsExpensePaise > 0L) {
            "Settled ₹$debtSettled debt to $friendName. Excess ₹$excessAsExpense recorded as normal expense."
        } else {
            "Settled ₹$debtSettled debt to $friendName."
        }
}

/**
 * Derived account balance calculation models (Part 6).
 */
data class DerivedAccountBalance(
    val account: AccountEntity,
    val derivedBalancePaise: Long,
    val derivedOwedPaise: Long,
    val availableCreditPaise: Long,
    val hasDiscrepancy: Boolean = false,
    val discrepancyBankBalancePaise: Long? = null
) {
    val derivedBalance: Double get() = derivedBalancePaise / 100.0
    val derivedOwed: Double get() = derivedOwedPaise / 100.0
    val availableCredit: Double get() = availableCreditPaise / 100.0
    val discrepancyBankBalance: Double? get() = discrepancyBankBalancePaise?.let { it / 100.0 }
}

data class NetBalanceOverview(
    val netAvailableBalancePaise: Long = 0L,
    val totalSavingsBalancePaise: Long = 0L,
    val totalCardOwedPaise: Long = 0L,
    val totalOwedToFriendsPaise: Long = 0L,
    val totalFriendsOweMePaise: Long = 0L,
    val unanchoredAccountsCount: Int = 0,
    val earliestTrackedTimestamp: Long = System.currentTimeMillis()
) {
    val netAvailableBalance: Double get() = netAvailableBalancePaise / 100.0
    val totalSavingsBalance: Double get() = totalSavingsBalancePaise / 100.0
    val totalCardOwed: Double get() = totalCardOwedPaise / 100.0
    val totalOwedToFriends: Double get() = totalOwedToFriendsPaise / 100.0
    val totalFriendsOweMe: Double get() = totalFriendsOweMePaise / 100.0
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

    constructor(
        categoryId: Long,
        categoryName: String,
        colorHex: String,
        monthlyBudget: Double,
        totalSpent: Double
    ) : this(
        categoryId = categoryId,
        categoryName = categoryName,
        color = colorHex,
        budgetPaise = if (monthlyBudget > 0) CurrencyUtils.rupeesToPaise(monthlyBudget) else null,
        totalSpentPaise = CurrencyUtils.rupeesToPaise(totalSpent)
    )
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
 * Summary stats from a Gmail IMAP sync operation.
 * Used for Settings > Sync & Data "Last sync" panel and historical tracking.
 */
data class GmailSyncStats(
    val timestamp: Long = System.currentTimeMillis(),
    val emailsFetched: Int = 0,
    val filteredOut: Int = 0,
    val parsed: Int = 0,
    val sentToReview: Int = 0,
    val saved: Int = 0,
    val lastError: String? = null,
    val totalFound: Int = emailsFetched,
    val importedDebits: Int = saved,
    val importedCredits: Int = 0,
    val autoSettledCredits: Int = 0,
    val pendingSuggestionsCreated: Int = 0,
    val skippedDuplicates: Int = 0,
    val errorMessage: String? = lastError
)
