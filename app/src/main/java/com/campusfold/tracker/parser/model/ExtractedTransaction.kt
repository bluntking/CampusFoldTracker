package com.campusfold.tracker.parser.model

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionSource
import com.campusfold.tracker.data.entity.TransactionType

data class ExtractedTransaction(
    val amountPaise: Long,
    val direction: Direction,
    val type: TransactionType,
    val bankName: String? = null,
    val accountLast4: String? = null,
    val instrumentType: String? = null,
    val merchantRaw: String? = null,
    val counterpartyName: String? = null,
    val reference: String? = null,
    val timestamp: Long,
    val balancePaise: Long? = null,
    val availableCreditPaise: Long? = null,
    val currency: String = "INR",
    val confidence: Float = 1.0f,
    val parserId: String
) {
    fun toTransactionEntity(
        accountId: Long? = null,
        resolvedPaymentMode: String? = null,
        resolvedVendorName: String? = null,
        categoryId: Long? = null,
        rawSms: String? = null,
        source: TransactionSource = TransactionSource.SMS,
        rawMessageHash: String? = null
    ): TransactionEntity {
        val cleanMerchant = merchantRaw?.takeIf { it.isNotBlank() }
            ?: counterpartyName?.takeIf { it.isNotBlank() }
            ?: bankName
            ?: "Unknown Merchant"
        val friendly = resolvedVendorName?.takeIf { it.isNotBlank() } ?: cleanMerchant
        val mode = resolvedPaymentMode?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(bankName, accountLast4?.let { "XX$it" }).joinToString(" ").ifBlank { "Cash/Manual" }

        return TransactionEntity(
            amountPaise = amountPaise,
            myActualSharePaise = amountPaise,
            type = type,
            paymentMode = mode,
            rawMerchant = cleanMerchant,
            friendlyVendorName = friendly,
            categoryId = categoryId,
            timestamp = timestamp,
            upiRef = reference,
            rawSms = rawSms,
            description = null,
            accountId = accountId,
            reference = reference,
            source = source,
            confidence = confidence,
            rawMessageHash = rawMessageHash,
            editedFields = "[]"
        )
    }
}
