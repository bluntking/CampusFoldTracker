package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.campusfold.tracker.util.CurrencyUtils

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String,
    val budgetPaise: Long? = null,
    val editedFields: String = "[]"
) {
    // Backward-compatibility accessors
    val colorHex: String get() = color
    val monthlyBudget: Double get() = (budgetPaise ?: 0L) / 100.0

    @Ignore
    constructor(
        id: Long = 0,
        name: String,
        colorHex: String,
        monthlyBudget: Double
    ) : this(
        id = id,
        name = name,
        color = colorHex,
        budgetPaise = if (monthlyBudget > 0) CurrencyUtils.rupeesToPaise(monthlyBudget) else null,
        editedFields = "[]"
    )
}
