package com.campusfold.tracker.util

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedCampusSms(
    val transaction: TransactionEntity,
    val extractedCreditLimit: Double? = null
)

object CampusSmsParser {

    // ─── Format 1: PNB Credit Card 8598 Patterns ──────────────────────────────
    private val pnbCardPattern = Regex("""\b(?:PNB|Punjab National Bank).*?(?:Credit Card|CC).*?8598\b|8598\b""", RegexOption.IGNORE_CASE)
    private val pnbDebitMerchantPattern = Regex("""\bat\s+([A-Za-z0-9 &'._-]{2,50}?)\s+on\b""", RegexOption.IGNORE_CASE)
    private val pnbDateTimePattern = Regex("""\bon\s+([0-9]{2}-[0-9]{2}-[0-9]{4}\s+[0-9]{2}:[0-9]{2})\b""", RegexOption.IGNORE_CASE)
    private val pnbUpiPattern = Regex("""(?:through\s+UPI|UPI)[:\s]+([0-9]{8,18})""", RegexOption.IGNORE_CASE)
    private val pnbLimitPattern = Regex("""(?:Avl|Available)\s+limit\s+Rs\.?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // PNB Credit Card Bill Confirmation Rule
    private val pnbBillReceivedPattern = Regex(
        """\b(?:payment\s+received|thank\s+you\s+for\s+(?:your\s+)?payment|credited\s+to\s+your\s+PNB\s+Credit\s+Card|payment\s+of\s+Rs.*?received)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Format 2: Axis Bank XX2003 Patterns ──────────────────────────────────
    private val axisAccountPattern = Regex("""\b(?:A/c\s+no\.?\s*XX2003|XX2003)\b""", RegexOption.IGNORE_CASE)
    private val axisAmountPattern = Regex("""(?:INR|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)\s+debited""", RegexOption.IGNORE_CASE)
    private val axisDateTimePattern = Regex("""([0-9]{2}-[0-9]{2}-[0-9]{2},\s*[0-9]{2}:[0-9]{2}:[0-9]{2})""")
    private val axisUpiLinePattern = Regex("""UPI/(?:P2M|P2P|p2m|p2p)/([0-9A-Za-z]{8,20})/([^\r\n]+)""")

    // Strict Word-Boundary Rule for Outbound Credit Card Bill Payments
    // \b(CRED|BILLDESK|CCAVENUE)\b OR (\bPNB\b.*\b(CARD|CC|BILL)\b|\b(CREDIT CARD|CC BILL)\b)
    private val outboundCcBillStrictPattern = Regex(
        """\b(CRED|BILLDESK|CCAVENUE)\b|(?:\bPNB\b.*?\b(?:CARD|CC|BILL)\b|\b(?:CREDIT\s+CARD|CC\s+BILL)\b)""",
        RegexOption.IGNORE_CASE
    )

    // Generic Fallback Patterns
    private val genericAmountPatterns = listOf(
        Regex("""(?:rs\.?|inr|₹|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹|rupees?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited|credited|spent|paid|withdrawn)\s*(?:by|with|for)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val genericUpiPatterns = listOf(
        Regex("""UPI/(?:P2M|P2P|p2m|p2p)/([0-9A-Za-z]{8,20})""", RegexOption.IGNORE_CASE),
        Regex("""(?:upi\s*ref(?:erence)?\s*(?:no\.?|num)?|utr|ref\s*no\.?)[:\s]+([0-9]{8,18})""", RegexOption.IGNORE_CASE),
        Regex("""(?:rrn|txn\s*id)[:\s]+([0-9A-Za-z]{8,20})""", RegexOption.IGNORE_CASE)
    )

    private val genericMerchantPatterns = listOf(
        Regex("""(?:VPA|UPI ID|to VPA)[:\s]+([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})\b""", RegexOption.IGNORE_CASE),
        Regex("""UPI/(?:P2M|P2P|p2m|p2p)/[0-9A-Za-z]{8,20}/([^\r\n.,;]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:paid to|transferred to|sent to|to)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:received from|transferred by|sent by|from|by)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|towards|merchant|store|info)\s*[:\s]+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
    )

    /**
     * Parses an incoming SMS text into a normalized transaction and optional extracted credit limit.
     */
    fun parse(
        body: String?,
        sender: String?,
        timestampFallback: Long = System.currentTimeMillis()
    ): ParsedCampusSms? {
        if (body.isNullOrBlank()) return null
        val cleanBody = body.trim()

        // Filter out OTPs and authentication verification codes
        if (Regex("""\b(otp|one time password|verification code|secret code|login code)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody)) {
            return null
        }

        // Try Format 1: PNB Credit Card 8598
        parseFormat1Pnb(cleanBody, timestampFallback)?.let { return it }

        // Try Format 2: Axis Bank XX2003
        parseFormat2Axis(cleanBody, timestampFallback)?.let { return it }

        // Fallback: Generic Indian Banking / UPI SMS
        return parseGenericFallback(cleanBody, sender, timestampFallback)
    }

    /**
     * Format 1 — PNB Credit Card 8598 (Debits & Bill Payment Confirmations)
     * Debit Example:
     * "PNB Credit Card 8598 debited with Rs.100 [CODE:WY9024] at Sekar store on 22-10-2026 14:18 through UPI: 629525888174 Avl limit Rs. 59396.93. -PNB"
     */
    private fun parseFormat1Pnb(body: String, timestampFallback: Long): ParsedCampusSms? {
        val isPnb8598 = pnbCardPattern.containsMatchIn(body) ||
                (body.contains("8598") && (body.contains("PNB", ignoreCase = true) || body.contains("Credit Card", ignoreCase = true)))

        if (!isPnb8598) return null

        // Extract Avl limit if present (e.g. 59396.93)
        val creditLimit = pnbLimitPattern.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        // Check PNB Credit Card Bill Confirmation Rule
        if (pnbBillReceivedPattern.containsMatchIn(body)) {
            val amount = extractAmount(body) ?: 0.0
            val upiRef = pnbUpiPattern.find(body)?.groupValues?.getOrNull(1)
            val dedupeKey = upiRef ?: generateDedupeHash(timestampFallback, amount, "PNB_CC_BILL_CONFIRMATION")

            val tx = TransactionEntity(
                amount = amount,
                myActualShare = amount,
                type = TransactionType.CC_BILL_TRANSFER,
                paymentMode = "PNB CC 8598",
                rawMerchant = "PNB Credit Card Bill Confirmation",
                friendlyVendorName = "PNB CC Bill Payment",
                categoryId = null,
                timestamp = timestampFallback,
                upiRef = dedupeKey,
                rawSms = body
            )
            return ParsedCampusSms(transaction = tx, extractedCreditLimit = creditLimit)
        }

        // Regular PNB Debit
        val isDebit = body.contains("debited", ignoreCase = true) || body.contains("spent", ignoreCase = true)
        if (!isDebit) return null

        val amount = extractAmount(body) ?: return null

        // Extract Raw Merchant between 'at ' and ' on '
        val merchantMatch = pnbDebitMerchantPattern.find(body)
        val rawMerchant = merchantMatch?.groupValues?.getOrNull(1)?.trim() ?: run {
            genericMerchantPatterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.trim() } ?: "Sekar store"
        }

        // Extract Date / Time (e.g. 22-10-2026 14:18)
        val parsedTime = pnbDateTimePattern.find(body)?.groupValues?.getOrNull(1)?.let { dateStr ->
            try {
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH).parse(dateStr)?.time
            } catch (e: Exception) {
                null
            }
        } ?: timestampFallback

        // Extract UPI Ref
        val upiRef = pnbUpiPattern.find(body)?.groupValues?.getOrNull(1)
        val dedupeKey = upiRef ?: generateDedupeHash(parsedTime, amount, rawMerchant)

        val tx = TransactionEntity(
            amount = amount,
            myActualShare = amount,
            type = TransactionType.EXPENSE,
            paymentMode = "PNB CC 8598",
            rawMerchant = rawMerchant,
            friendlyVendorName = rawMerchant,
            categoryId = null,
            timestamp = parsedTime,
            upiRef = dedupeKey,
            rawSms = body
        )

        return ParsedCampusSms(transaction = tx, extractedCreditLimit = creditLimit)
    }

    /**
     * Format 2 — Axis Bank XX2003 (Debits & Outbound CC Bill Payments)
     * Debit Example:
     * "INR 60.00 debited\nA/c no. XX2003\n21-10-26, 17:54:38\nUPI/P2M/666090797851/DD STORE"
     */
    private fun parseFormat2Axis(body: String, timestampFallback: Long): ParsedCampusSms? {
        val isAxis2003 = axisAccountPattern.containsMatchIn(body) ||
                (body.contains("XX2003", ignoreCase = true) && body.contains("debited", ignoreCase = true))

        if (!isAxis2003) return null

        // Format 2 is strictly for Axis debits & outbound CC bill transfers.
        // Incoming credits / deposits / reversals fall through to generic income / settlement handling.
        val isCredit = Regex("""\b(credited|received|cashback|refund|reversal|deposit|deposited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (isCredit) return null

        // Extract Amount
        val amount = axisAmountPattern.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            ?: extractAmount(body)
            ?: return null

        // Extract Date / Time (e.g. 21-10-26, 17:54:38)
        val parsedTime = axisDateTimePattern.find(body)?.groupValues?.getOrNull(1)?.let { dateStr ->
            try {
                SimpleDateFormat("dd-MM-yy, HH:mm:ss", Locale.ENGLISH).parse(dateStr.trim())?.time
            } catch (e: Exception) {
                null
            }
        } ?: timestampFallback

        // Extract UPI Ref and Raw Merchant from UPI line: UPI/P2M/666090797851/DD STORE
        val upiLineMatch = axisUpiLinePattern.find(body)
        var upiRef: String? = null
        var rawMerchant: String? = null

        if (upiLineMatch != null) {
            upiRef = upiLineMatch.groupValues.getOrNull(1)?.trim()
            rawMerchant = upiLineMatch.groupValues.getOrNull(2)?.trim()
        } else {
            // Fallback split on line containing UPI
            val lines = body.split("\n", "\r")
            val upiLine = lines.find { it.contains("UPI/", ignoreCase = true) }
            if (upiLine != null) {
                val parts = upiLine.split("/")
                if (parts.size >= 4) {
                    upiRef = parts[2].trim()
                    rawMerchant = parts[3].trim()
                }
            }
        }

        val finalMerchant = rawMerchant ?: run {
            genericMerchantPatterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.trim() } ?: "Axis Merchant"
        }

        // Strict Word-Boundary Rule for Outbound CC Bill Payments
        val isCcBillPayment = outboundCcBillStrictPattern.containsMatchIn(finalMerchant) ||
                outboundCcBillStrictPattern.containsMatchIn(body)

        val txType = if (isCcBillPayment) TransactionType.CC_BILL_TRANSFER else TransactionType.EXPENSE
        val friendlyName = if (isCcBillPayment) "CC Bill Payment ($finalMerchant)" else finalMerchant

        val dedupeKey = upiRef ?: generateDedupeHash(parsedTime, amount, finalMerchant)

        val tx = TransactionEntity(
            amount = amount,
            myActualShare = amount,
            type = txType,
            paymentMode = "Axis Bank XX2003",
            rawMerchant = finalMerchant,
            friendlyVendorName = friendlyName,
            categoryId = null,
            timestamp = parsedTime,
            upiRef = dedupeKey,
            rawSms = body
        )

        return ParsedCampusSms(transaction = tx, extractedCreditLimit = null)
    }

    /**
     * Generic fallback for other bank and UPI messages.
     */
    private fun parseGenericFallback(body: String, sender: String?, timestampFallback: Long): ParsedCampusSms? {
        val isFinancial = Regex(
            """\b(debited|credited|paid|spent|transferred|withdrawn|received|refund|cashback|purchase)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(body)

        if (!isFinancial) return null

        val amount = extractAmount(body) ?: return null

        val isCredit = Regex("""\b(credited|received|cashback|refund|reversal)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)

        // Check if message is a CC bill payment via generic sender
        val isCcBill = outboundCcBillStrictPattern.containsMatchIn(body)

        val type = when {
            isCcBill -> TransactionType.CC_BILL_TRANSFER
            isCredit -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        val upiRef = genericUpiPatterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1) }

        var merchant: String? = null
        for (pattern in genericMerchantPatterns) {
            val candidate = pattern.find(body)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() &&
                !candidate.equals("a/c", ignoreCase = true) &&
                !candidate.equals("account", ignoreCase = true) &&
                !candidate.equals("upi", ignoreCase = true) &&
                !candidate.equals("bank", ignoreCase = true) &&
                !candidate.equals("card", ignoreCase = true)) {
                merchant = candidate
                break
            }
        }
        val finalMerchant = merchant ?: if (type == TransactionType.INCOME) "Deposit / Refund" else "Merchant Store"

        val lowerBody = body.lowercase()
        val paymentMode = when {
            lowerBody.contains("8598") -> "PNB CC 8598"
            lowerBody.contains("2003") -> "Axis Bank XX2003"
            lowerBody.contains("2548") || lowerBody.contains("jupiter") -> "Federal (Jupiter) 2548"
            lowerBody.contains("5557") -> "PNB Savings Account 5557"
            lowerBody.contains("5476") -> "Axis Bank Savings Account 5476"
            lowerBody.contains("axis") && (lowerBody.contains("card") || lowerBody.contains("credit")) -> "Axis Bank Credit Card"
            lowerBody.contains("pnb") -> "PNB Savings Account 5557"
            lowerBody.contains("axis") -> "Axis Bank XX2003"
            lowerBody.contains("federal") -> "Federal (Jupiter) 2548"
            lowerBody.contains("sbi") -> "SBI A/c"
            lowerBody.contains("hdfc") -> "HDFC Bank"
            lowerBody.contains("icici") -> "ICICI Bank"
            else -> "Cash/Manual"
        }

        val dedupeKey = upiRef ?: generateDedupeHash(timestampFallback, amount, finalMerchant)

        val tx = TransactionEntity(
            amount = amount,
            myActualShare = amount,
            type = type,
            paymentMode = paymentMode,
            rawMerchant = finalMerchant,
            friendlyVendorName = finalMerchant,
            categoryId = null,
            timestamp = timestampFallback,
            upiRef = dedupeKey,
            rawSms = body
        )

        return ParsedCampusSms(transaction = tx, extractedCreditLimit = null)
    }

    private fun extractAmount(text: String): Double? {
        for (pattern in genericAmountPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val num = match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
                if (num != null && num > 0) return num
            }
        }
        return null
    }

    /**
     * Generates a deterministic deduplication hash when no UPI reference is present in the SMS.
     * Prevents duplicate imports from historical inbox scans.
     */
    fun generateDedupeHash(timestamp: Long, amount: Double, rawMerchant: String): String {
        val cleanMerchant = rawMerchant.trim().lowercase().replace("\\s+".toRegex(), "_")
        return "HASH_${timestamp}_${amount}_${cleanMerchant}"
    }
}
