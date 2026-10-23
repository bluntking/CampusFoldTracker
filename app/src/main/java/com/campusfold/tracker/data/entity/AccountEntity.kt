package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.campusfold.tracker.util.CurrencyUtils

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["bankKey"]),
        Index(value = ["last4"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankName: String,
    val bankKey: String,
    val last4: String,
    val type: AccountType = AccountType.UNASSIGNED,
    val displayName: String,
    val color: String = "#10B981",
    val balancePaise: Long = 0L,
    val balanceAsOf: Long = System.currentTimeMillis(),
    val balanceSource: BalanceSource = BalanceSource.MANUAL,
    val creditLimitPaise: Long = 0L,
    val isArchived: Boolean = false,
    val editedFields: String = "[]"
) {
    // Backward-compatibility accessors
    val name: String get() = displayName
    val colorHex: String get() = color
    val accountType: String get() = type.name
    val creditLimitOrBalance: Double
        get() = if (type == AccountType.CREDIT_CARD) creditLimitPaise / 100.0 else balancePaise / 100.0

    @Ignore
    constructor(
        id: Long = 0,
        name: String,
        bankName: String,
        accountType: String,
        last4: String,
        creditLimitOrBalance: Double = 0.0,
        colorHex: String = "#10B981"
    ) : this(
        id = id,
        bankName = bankName,
        bankKey = bankName.trim().uppercase(),
        last4 = last4,
        type = try {
            AccountType.valueOf(accountType)
        } catch (_: Exception) {
            AccountType.UNASSIGNED
        },
        displayName = name,
        color = colorHex,
        balancePaise = if (accountType != "CREDIT_CARD") CurrencyUtils.rupeesToPaise(creditLimitOrBalance) else 0L,
        balanceAsOf = System.currentTimeMillis(),
        balanceSource = BalanceSource.MANUAL,
        creditLimitPaise = if (accountType == "CREDIT_CARD") CurrencyUtils.rupeesToPaise(creditLimitOrBalance) else 0L,
        isArchived = false,
        editedFields = "[]"
    )
}
