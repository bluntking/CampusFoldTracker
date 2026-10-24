package com.campusfold.tracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.campusfold.tracker.util.CurrencyUtils

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidByPersonId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["upiRef"], unique = true),
        Index(value = ["reference"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["paidByPersonId"]),
        Index(value = ["timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountPaise: Long,
    val myActualSharePaise: Long = amountPaise,
    val type: TransactionType,
    val paymentMode: String = "Cash/Manual", // e.g. 'Account 2003', 'Cash/Manual'
    val rawMerchant: String = "",
    val friendlyVendorName: String = "",
    val categoryId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val upiRef: String? = null, // Unique index for deduplication
    val rawSms: String? = null,
    val description: String? = null,
    val accountId: Long? = null,
    val reference: String? = null, // Nullable unique index where not null
    val source: TransactionSource = TransactionSource.MANUAL,
    val confidence: Float = 1.0f,
    val rawMessageHash: String? = null,
    val editedFields: String = "[]", // JSON list of field names user edited manually
    val paidByPersonId: Long? = null
) {
    // Backward-compatibility accessors
    val amount: Double get() = amountPaise / 100.0
    val myActualShare: Double get() = myActualSharePaise / 100.0
    val vendor: String get() = friendlyVendorName.ifBlank { rawMerchant }.ifBlank { "Unknown Merchant" }

    @Ignore
    constructor(
        id: Long = 0,
        amount: Double,
        myActualShare: Double = amount,
        type: TransactionType,
        paymentMode: String = "Cash/Manual",
        rawMerchant: String = "",
        friendlyVendorName: String = "",
        categoryId: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
        upiRef: String? = null,
        rawSms: String? = null,
        description: String? = null,
        accountId: Long? = null,
        reference: String? = null,
        source: TransactionSource = TransactionSource.MANUAL,
        confidence: Float = 1.0f,
        rawMessageHash: String? = null,
        editedFields: String = "[]"
    ) : this(
        id = id,
        amountPaise = CurrencyUtils.rupeesToPaise(amount),
        myActualSharePaise = CurrencyUtils.rupeesToPaise(myActualShare),
        type = type,
        paymentMode = paymentMode,
        rawMerchant = rawMerchant,
        friendlyVendorName = friendlyVendorName,
        categoryId = categoryId,
        timestamp = timestamp,
        upiRef = upiRef,
        rawSms = rawSms,
        description = description,
        accountId = accountId,
        reference = reference,
        source = source,
        confidence = confidence,
        rawMessageHash = rawMessageHash,
        editedFields = editedFields
    )
}
