package com.campusfold.tracker.util

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType

object SmsParser {

    private val amountPatterns = listOf(
        Regex("""(?:rs\.?|inr|₹|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹|rupees?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited|credited|spent|paid|withdrawn)\s*(?:by|with|for)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val upiRefPatterns = listOf(
        Regex("""(?:upi\s*ref(?:erence)?\s*(?:no\.?|num)?|utr|ref\s*no\.?)[:\s]+([0-9]{8,16})""", RegexOption.IGNORE_CASE),
        Regex("""(?:rrn|txn\s*id)[:\s]+([0-9A-Za-z]{8,20})""", RegexOption.IGNORE_CASE)
    )

    private val merchantPatterns = listOf(
        Regex("""(?:VPA|UPI ID|to VPA)[:\s]+([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:paid to|transferred to|sent to|to)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|towards|merchant|store|info)\s*[:\s]+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
    )

    fun parse(
        body: String?,
        sender: String?,
        timestamp: Long = System.currentTimeMillis()
    ): TransactionEntity? {
        if (body.isNullOrBlank()) return null
        val cleanBody = body.trim()

        // Filter out OTPs and non-transaction security messages
        if (Regex("""\b(otp|one time password|verification code|secret code|login code)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody)) {
            return null
        }

        // Must indicate financial transaction
        val isTransaction = Regex(
            """\b(debited|credited|paid|spent|transferred|withdrawn|withdrawal|sent|received|refund|cashback|purchase)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(cleanBody)

        if (!isTransaction) return null

        // Extract amount
        var parsedAmount: Double? = null
        for (pattern in amountPatterns) {
            val match = pattern.find(cleanBody)
            if (match != null) {
                val rawNumber = match.groupValues.getOrNull(1)?.replace(",", "")
                parsedAmount = rawNumber?.toDoubleOrNull()
                if (parsedAmount != null && parsedAmount > 0) break
            }
        }
        val amount = parsedAmount ?: return null

        // Extract UPI reference for deduplication
        var upiRef: String? = null
        for (refPattern in upiRefPatterns) {
            val match = refPattern.find(cleanBody)
            if (match != null) {
                upiRef = match.groupValues.getOrNull(1)
                if (!upiRef.isNullOrBlank()) break
            }
        }

        // Detect Payment Mode (e.g. 'PNB CC 8598', 'Axis Bank XX2003', 'Cash/Manual')
        val lowerText = cleanBody.lowercase()
        val paymentMode = when {
            lowerText.contains("8598") || (lowerText.contains("pnb") && lowerText.contains("card")) -> "PNB CC 8598"
            lowerText.contains("2003") || (lowerText.contains("axis")) -> "Axis Bank XX2003"
            lowerText.contains("pnb") -> "PNB Bank"
            lowerText.contains("sbi") -> "SBI A/c"
            lowerText.contains("hdfc") -> "HDFC Bank"
            lowerText.contains("icici") -> "ICICI Bank"
            else -> "Cash/Manual"
        }

        // Detect Transaction Type
        val type = when {
            Regex("""cc bill|credit card bill|card payment""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody) ->
                TransactionType.CC_BILL_TRANSFER
            Regex("""\b(credited|received|credit|cashback|refund|reversal)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody) ->
                TransactionType.INCOME
            else ->
                TransactionType.EXPENSE
        }

        // Extract Merchant Name
        var merchant: String? = null
        for (pattern in merchantPatterns) {
            val match = pattern.find(cleanBody)
            if (match != null) {
                val candidate = match.groupValues.getOrNull(1)?.trim()
                if (!candidate.isNullOrBlank() && !candidate.equals("a/c", ignoreCase = true) && !candidate.equals("account", ignoreCase = true)) {
                    merchant = candidate
                    break
                }
            }
        }
        val finalMerchant = merchant ?: if (type == TransactionType.INCOME) "Deposit / Refund" else "Merchant Store"

        return TransactionEntity(
            amount = amount,
            myActualShare = amount, // Defaults to 100% until splits are attached
            type = type,
            paymentMode = paymentMode,
            rawMerchant = finalMerchant,
            friendlyVendorName = finalMerchant, // Will be mapped by VendorDao
            categoryId = null,
            timestamp = timestamp,
            upiRef = upiRef,
            rawSms = cleanBody
        )
    }
}
