package com.campusfold.tracker.util

import com.campusfold.tracker.data.TransactionEntity

object SmsParser {

    private val amountPatterns = listOf(
        Regex("""(?:rs\.?|inr|₹|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹|rupees?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited|credited|spent|paid|withdrawn)\s*(?:by|with|for)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val merchantPatterns = listOf(
        Regex("""(?:VPA|UPI ID|to VPA)[:\s]+([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:paid to|transferred to|sent to|to)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|towards|merchant|store|info)\s*[:\s]+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
    )

    private val bankKeywords = mapOf(
        "sbi" to "State Bank of India",
        "hdfc" to "HDFC Bank",
        "icici" to "ICICI Bank",
        "axis" to "Axis Bank",
        "kotak" to "Kotak Mahindra Bank",
        "pnb" to "Punjab National Bank",
        "bob" to "Bank of Baroda",
        "canara" to "Canara Bank",
        "paytm" to "Paytm Payments Bank",
        "phonepe" to "PhonePe",
        "gpay" to "Google Pay",
        "idfc" to "IDFC First Bank",
        "yes" to "Yes Bank",
        "union" to "Union Bank",
        "federal" to "Federal Bank"
    )

    fun parse(body: String?, sender: String?, timestamp: Long = System.currentTimeMillis()): TransactionEntity? {
        if (body.isNullOrBlank()) return null
        val cleanBody = body.trim()
        val lowerBody = cleanBody.lowercase()
        val lowerSender = sender.orEmpty().lowercase()

        // Filter out OTPs and verification messages
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

        // Determine transaction type
        val isCredit = Regex("""\b(credited|received|credit|cashback|refund|reversal)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody)
        val type = if (isCredit) "CREDIT" else "DEBIT"

        // Determine bank
        var bank = "Bank Transaction"
        for ((key, name) in bankKeywords) {
            if (lowerSender.contains(key) || lowerBody.contains(key)) {
                bank = name
                break
            }
        }

        // Determine merchant
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
        val finalMerchant = merchant ?: if (type == "CREDIT") "Deposit / Refund" else bank

        // Determine category
        val category = categorize(finalMerchant, lowerBody)

        return TransactionEntity(
            amount = amount,
            type = type,
            merchant = finalMerchant,
            category = category,
            bank = bank,
            timestamp = timestamp,
            rawBody = cleanBody,
            sender = sender.orEmpty()
        )
    }

    private fun categorize(merchant: String, body: String): String {
        val text = "${merchant.lowercase()} $body"
        return when {
            Regex("""swiggy|zomato|mcdonald|kfc|starbucks|domino|pizza|burger|cafe|restaurant|food|bites|bakery""").containsMatchIn(text) -> "Food & Dining"
            Regex("""zepto|blinkit|instamart|bigbasket|dmart|grocery|supermarket|provision""").containsMatchIn(text) -> "Groceries"
            Regex("""uber|ola|rapido|metro|irctc|fuel|petrol|diesel|bus|auto|flight|indigo""").containsMatchIn(text) -> "Travel & Commute"
            Regex("""amazon|flipkart|myntra|ajio|meesho|tata|croma|shopping|store|mall""").containsMatchIn(text) -> "Shopping"
            Regex("""electricity|bescom|water|gas|wifi|airtel|jio|vi|broadband|recharge|bill""").containsMatchIn(text) -> "Bills & Utilities"
            Regex("""bookmyshow|pvr|inox|netflix|spotify|prime|hotstar|cinema|movie""").containsMatchIn(text) -> "Entertainment"
            Regex("""pharmacy|apollo|1mg|medplus|hospital|clinic|doctor|health""").containsMatchIn(text) -> "Health"
            Regex("""fee|tuition|books|college|campus|stationery|xerox""").containsMatchIn(text) -> "Campus & Education"
            else -> "General"
        }
    }
}
