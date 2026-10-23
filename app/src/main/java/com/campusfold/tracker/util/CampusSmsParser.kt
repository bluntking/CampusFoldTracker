package com.campusfold.tracker.util

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedCampusSms(
    val transaction: TransactionEntity,
    val extractedCreditLimit: Double? = null
)

object CampusSmsParser {

    // ─── Format 1: Credit Card Patterns (Debits & Bill Payment Confirmations) ───
    private val cardPattern = Regex("""\b(?:([A-Za-z]+)\s+)?(?:Credit\s+Card|CC)\s*(?:ending(?:\s+(?:with|in))?|no\.?)?\s*(?:XX|[*]+)?([0-9]{3,6})\b""", RegexOption.IGNORE_CASE)
    private val cardDebitMerchantPattern = Regex("""\bat\s+([A-Za-z0-9 &'._-]{2,50}?)\s+on\b""", RegexOption.IGNORE_CASE)
    private val cardDateTimePattern = Regex("""\bon\s+([0-9]{2}-[0-9]{2}-[0-9]{4}\s+[0-9]{2}:[0-9]{2})\b""", RegexOption.IGNORE_CASE)
    private val cardUpiPattern = Regex("""(?:through\s+UPI|UPI)[:\s]+([0-9]{8,18})""", RegexOption.IGNORE_CASE)
    private val cardLimitPattern = Regex("""(?:Avl|Available)\s+limit[\s:]*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // Credit Card Bill Confirmation Rule
    private val cardBillReceivedPattern = Regex(
        """\b(?:payment\s+received|thank\s+you\s+for\s+(?:your\s+)?payment|credited\s+to\s+your\s+(?:[A-Za-z]+\s+)?(?:Credit\s+Card|CC)|payment\s+of\s+Rs.*?received)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Format 2: Bank Account Debit / UPI Line Patterns ─────────────────────
    private val accountPattern = Regex("""\b(?:A/c\s+no\.?\s*(?:XX|X*|[*]+)?([0-9]{3,6}))\b""", RegexOption.IGNORE_CASE)
    private val accountAmountPattern = Regex("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+debited""", RegexOption.IGNORE_CASE)
    private val accountDateTimePattern = Regex("""([0-9]{2}-[0-9]{2}-[0-9]{2,4},\s*[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)""")
    private val accountUpiLinePattern = Regex("""UPI/(?:P2M|P2P|p2m|p2p|P2A|p2a)/([0-9A-Za-z]{8,20})/([^\r\n]+)""")

    // Strict Word-Boundary Rule for Outbound Credit Card Bill Payments
    private val outboundCcBillStrictPattern = Regex(
        """\b(CRED|BILLDESK|CCAVENUE|CREDIT\s+CARD\s+BILL|CC\s+BILL|CARD\s+BILL|CARD\s+PAYMENT)\b|(?:\b(?:CARD|CC)\b.*?\b(?:BILL|PAYMENT)\b)""",
        RegexOption.IGNORE_CASE
    )

    // Generic Fallback Patterns
    private val genericAmountPatterns = listOf(
        Regex("""(?:rs\.?|inr|₹|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹|rupees?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited|credited|spent|paid|withdrawn)\s*(?:by|with|for)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val genericUpiPatterns = listOf(
        Regex("""UPI/(?:P2M|P2P|p2m|p2p|P2A|p2a)/([0-9A-Za-z]{8,20})""", RegexOption.IGNORE_CASE),
        Regex("""(?:upi\s*ref(?:erence)?\s*(?:no\.?|num)?|utr|ref\s*no\.?)[:\s]+([0-9]{8,18})""", RegexOption.IGNORE_CASE),
        Regex("""(?:rrn|txn\s*id)[:\s]+([0-9A-Za-z]{8,20})""", RegexOption.IGNORE_CASE)
    )

    private val genericMerchantPatterns = listOf(
        Regex("""(?:VPA|UPI ID|to VPA)[:\s]+([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})\b""", RegexOption.IGNORE_CASE),
        Regex("""UPI/(?:P2M|P2P|p2m|p2p|P2A|p2a)/[0-9A-Za-z]{8,20}/([^\r\n.,;]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:paid to|transferred to|sent to|to)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:received from|transferred by|sent by|from|by)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|towards|merchant|store|info)\s*[:\s]+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
    )

    /**
     * Parses an incoming SMS text into a normalized transaction and optional extracted credit limit.
     * Dynamically matches accounts against user-configured accounts from database.
     */
    fun parse(
        body: String?,
        sender: String?,
        timestampFallback: Long = System.currentTimeMillis(),
        knownAccounts: List<AccountEntity> = emptyList()
    ): ParsedCampusSms? {
        if (body.isNullOrBlank()) return null
        val cleanBody = body.trim()

        // Filter out OTPs and authentication verification codes
        if (Regex("""\b(otp|one time password|verification code|secret code|login code)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody)) {
            return null
        }

        // Try Format 1: Credit Card Format
        parseFormat1Card(cleanBody, sender, timestampFallback, knownAccounts)?.let { return it }

        // Try Format 2: Bank Account Line Format
        parseFormat2Account(cleanBody, sender, timestampFallback, knownAccounts)?.let { return it }

        // Fallback: Generic Indian Banking / UPI SMS
        return parseGenericFallback(cleanBody, sender, timestampFallback, knownAccounts)
    }

    /**
     * Format 1 — Credit Card (Debits & Bill Payment Confirmations)
     * e.g.: "PNB Credit Card 8598 debited with Rs.100 [CODE:WY9024] at Sekar store on 22-10-2026 14:18 through UPI: 629525888174 Avl limit Rs. 59396.93. -PNB"
     */
    private fun parseFormat1Card(
        body: String,
        sender: String?,
        timestampFallback: Long,
        knownAccounts: List<AccountEntity> = emptyList()
    ): ParsedCampusSms? {
        val cardMatch = cardPattern.find(body)
        val isCard = cardMatch != null || (body.contains("Credit Card", ignoreCase = true) || body.contains(" CC ", ignoreCase = true))

        if (!isCard) return null

        val rawBank = cardMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val detectedCardBank = if (rawBank != null && rawBank.length > 1) {
            detectBankName(rawBank, sender) ?: rawBank
        } else {
            detectBankName(body, sender) ?: "Credit Card"
        }
        val cardLast4 = cardMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: Regex("""\b(?:ending(?:\s+(?:with|in))?|no\.?)\s*(?:XX|[*]+)?([0-9]{3,6})\b""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)

        val paymentMode = resolveCardPaymentMode(detectedCardBank, cardLast4, knownAccounts)

        // Extract Avl limit if present (e.g. 59396.93)
        val creditLimit = cardLimitPattern.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        // Check Credit Card Bill Confirmation Rule
        if (cardBillReceivedPattern.containsMatchIn(body)) {
            val amount = extractAmount(body) ?: 0.0
            val upiRef = cardUpiPattern.find(body)?.groupValues?.getOrNull(1)
            val dedupeKey = upiRef ?: generateDedupeHash(timestampFallback, amount, "${detectedCardBank}_CC_BILL_CONFIRMATION")

            val tx = TransactionEntity(
                amount = amount,
                myActualShare = amount,
                type = TransactionType.CC_BILL_TRANSFER,
                paymentMode = paymentMode,
                rawMerchant = "$detectedCardBank Credit Card Bill Confirmation",
                friendlyVendorName = "$detectedCardBank CC Bill Payment",
                categoryId = null,
                timestamp = timestampFallback,
                upiRef = dedupeKey,
                rawSms = body
            )
            return ParsedCampusSms(transaction = tx, extractedCreditLimit = creditLimit)
        }

        // Regular Credit Card Debit
        val isDebit = body.contains("debited", ignoreCase = true) || body.contains("spent", ignoreCase = true)
        if (!isDebit) return null

        val amount = extractAmount(body) ?: return null

        // Extract Raw Merchant between 'at ' and ' on '
        val merchantMatch = cardDebitMerchantPattern.find(body)
        val rawMerchant = merchantMatch?.groupValues?.getOrNull(1)?.trim() ?: run {
            genericMerchantPatterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.trim() } ?: "Merchant"
        }

        // Extract Date / Time (e.g. 22-10-2026 14:18)
        val parsedTime = cardDateTimePattern.find(body)?.groupValues?.getOrNull(1)?.let { dateStr ->
            try {
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH).parse(dateStr)?.time
            } catch (e: Exception) {
                null
            }
        } ?: timestampFallback

        // Extract UPI Ref
        val upiRef = cardUpiPattern.find(body)?.groupValues?.getOrNull(1)
        val dedupeKey = upiRef ?: generateDedupeHash(parsedTime, amount, rawMerchant)

        val tx = TransactionEntity(
            amount = amount,
            myActualShare = amount,
            type = TransactionType.EXPENSE,
            paymentMode = paymentMode,
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
     * Format 2 — Bank Account Debit & Outbound CC Bill Payments
     * e.g.: "INR 60.00 debited\nA/c no. XX2003\n21-10-26, 17:54:38\nUPI/P2M/666090797851/DD STORE"
     */
    private fun parseFormat2Account(
        body: String,
        sender: String?,
        timestampFallback: Long,
        knownAccounts: List<AccountEntity> = emptyList()
    ): ParsedCampusSms? {
        val hasDebit = accountAmountPattern.containsMatchIn(body) ||
                (body.contains("debited", ignoreCase = true) && accountPattern.containsMatchIn(body))

        if (!hasDebit) return null

        // Format 2 is strictly for debits & outbound CC bill transfers.
        // Incoming credits / deposits / reversals fall through to generic income / settlement handling.
        val isCredit = Regex("""\b(credited|received|cashback|refund|reversal|deposit|deposited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (isCredit) return null

        val accountTailMatch = accountPattern.find(body)
        val accountTail = accountTailMatch?.groupValues?.getOrNull(1)

        val detectedBank = detectBankName(body, sender) ?: "Bank"
        val paymentMode = resolveAccountPaymentMode(detectedBank, accountTail, knownAccounts)

        // Extract Amount
        val amount = accountAmountPattern.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            ?: extractAmount(body)
            ?: return null

        // Extract Date / Time (e.g. 21-10-26, 17:54:38)
        val parsedTime = accountDateTimePattern.find(body)?.groupValues?.getOrNull(1)?.let { dateStr ->
            try {
                val cleanDateStr = dateStr.trim()
                if (cleanDateStr.contains(",")) {
                    SimpleDateFormat("dd-MM-yy, HH:mm:ss", Locale.ENGLISH).parse(cleanDateStr)?.time
                        ?: SimpleDateFormat("dd-MM-yyyy, HH:mm:ss", Locale.ENGLISH).parse(cleanDateStr)?.time
                } else {
                    SimpleDateFormat("dd-MM-yy HH:mm:ss", Locale.ENGLISH).parse(cleanDateStr)?.time
                }
            } catch (e: Exception) {
                null
            }
        } ?: timestampFallback

        // Extract UPI Ref and Raw Merchant from UPI line: UPI/P2M/666090797851/DD STORE
        val upiLineMatch = accountUpiLinePattern.find(body)
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
            genericMerchantPatterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.trim() } ?: "$detectedBank Merchant"
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
            paymentMode = paymentMode,
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
    private fun parseGenericFallback(
        body: String,
        sender: String?,
        timestampFallback: Long,
        knownAccounts: List<AccountEntity> = emptyList()
    ): ParsedCampusSms? {
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

        val paymentMode = resolvePaymentMode(body, sender, knownAccounts)

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

    private fun resolveCardPaymentMode(
        bank: String,
        cardLast4: String?,
        knownAccounts: List<AccountEntity>
    ): String {
        if (!cardLast4.isNullOrBlank() && knownAccounts.isNotEmpty()) {
            val matched = knownAccounts.find { it.last4.endsWith(cardLast4) }
            if (matched != null) return matched.displayName
        }
        return if (!cardLast4.isNullOrBlank()) "$bank CC $cardLast4" else "$bank Credit Card"
    }

    private fun resolveAccountPaymentMode(
        bank: String,
        accountTail: String?,
        knownAccounts: List<AccountEntity>
    ): String {
        if (!accountTail.isNullOrBlank() && knownAccounts.isNotEmpty()) {
            val matched = knownAccounts.find { it.last4.endsWith(accountTail) }
            if (matched != null) return matched.displayName
        }
        return if (!accountTail.isNullOrBlank()) "$bank XX$accountTail" else "$bank Account"
    }

    /**
     * Dynamically resolves payment mode from database accounts, known formats, or unknown account tails.
     */
    fun resolvePaymentMode(
        body: String,
        sender: String?,
        knownAccounts: List<AccountEntity> = emptyList()
    ): String {
        val lowerBody = body.lowercase()
        val lowerSender = (sender ?: "").lowercase()

        // 1. Dynamic Match against user's added accounts in database
        if (knownAccounts.isNotEmpty()) {
            for (acc in knownAccounts) {
                val last4 = acc.last4.trim().lowercase()
                val bank = acc.bankName.trim().lowercase()
                if (last4.length >= 4 && (lowerBody.contains(last4) || lowerBody.contains("xx$last4"))) {
                    if (bank.isNotEmpty() && (lowerBody.contains(bank) || lowerSender.contains(bank))) {
                        return acc.displayName
                    }
                }
            }
            for (acc in knownAccounts) {
                val last4 = acc.last4.trim().lowercase()
                if (last4.length >= 4 && (lowerBody.contains(last4) || lowerBody.contains("xx$last4"))) {
                    return acc.displayName
                }
            }
            for (acc in knownAccounts) {
                val name = acc.displayName.trim().lowercase()
                if (name.length >= 5 && lowerBody.contains(name)) {
                    return acc.displayName
                }
            }
        }

        // 2. Dynamic Unknown Account Tail Extraction
        val tailRegex = Regex("""\b(?:A/c|Account|Card|Acct|CC)\s*(?:no\.?)?\s*(?:XX|[*]+)?([0-9]{3,6})\b|\bending\s*(?:in|with)?\s*(?:XX|[*]+)?([0-9]{4})\b""", RegexOption.IGNORE_CASE)
        val tailMatch = tailRegex.find(body)
        val extractedTail = tailMatch?.groupValues?.getOrNull(1)?.ifEmpty { null }
            ?: tailMatch?.groupValues?.getOrNull(2)?.ifEmpty { null }
            ?: Regex("""\b([0-9]{4})\b""").find(body)?.groupValues?.getOrNull(1)

        val detectedBank = detectBankName(body, sender)

        if (detectedBank != null && extractedTail != null) {
            val isCard = lowerBody.contains("credit card") || lowerBody.contains("card") || lowerBody.contains(" cc ") || extractedTail == "8598"
            val isSavings = lowerBody.contains("savings") || lowerBody.contains("saving") || extractedTail == "5557" || extractedTail == "5476"
            val isJupiter = lowerBody.contains("jupiter") || extractedTail == "2548"

            return when {
                isJupiter -> "$detectedBank (Jupiter) $extractedTail"
                isCard -> "$detectedBank CC $extractedTail"
                isSavings -> "$detectedBank Savings Account $extractedTail"
                else -> "$detectedBank XX$extractedTail"
            }
        }

        if (extractedTail != null) {
            return "A/c XX$extractedTail"
        }

        if (detectedBank != null) {
            return "$detectedBank A/c"
        }

        return "Cash/Manual"
    }

    fun detectBankName(body: String, sender: String?): String? {
        val lowerBody = body.lowercase()
        val lowerSender = (sender ?: "").lowercase()

        return when {
            lowerBody.contains("pnb") || lowerSender.contains("pnb") || lowerBody.contains("punjab national") -> "PNB"
            lowerBody.contains("axis") || lowerSender.contains("axis") -> "Axis Bank"
            lowerBody.contains("hdfc") || lowerSender.contains("hdfc") -> "HDFC Bank"
            lowerBody.contains("icici") || lowerSender.contains("icici") -> "ICICI Bank"
            lowerBody.contains("sbi") || lowerSender.contains("sbi") || lowerBody.contains("state bank") -> "SBI"
            lowerBody.contains("federal") || lowerSender.contains("fed") -> "Federal Bank"
            lowerBody.contains("kotak") || lowerSender.contains("kotak") -> "Kotak Bank"
            lowerBody.contains("idfc") || lowerSender.contains("idfc") -> "IDFC FIRST"
            lowerBody.contains("indusind") || lowerSender.contains("indus") -> "IndusInd Bank"
            lowerBody.contains("canara") || lowerSender.contains("can") -> "Canara Bank"
            lowerBody.contains("bob") || lowerBody.contains("bank of baroda") || lowerSender.contains("bob") -> "Bank of Baroda"
            lowerBody.contains("union") || lowerSender.contains("union") -> "Union Bank"
            lowerBody.contains("yes bank") || lowerSender.contains("yes") -> "Yes Bank"
            else -> null
        }
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
