package com.campusfold.tracker.parser

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.parser.model.*
import com.campusfold.tracker.util.AxisBankEmailParser
import com.campusfold.tracker.util.CampusSmsParser
import com.campusfold.tracker.util.CurrencyUtils
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.BankParserFactory
import java.util.Locale
import java.util.regex.Pattern

/**
 * Unified MessageExtractor combining:
 * 1. Filtering & classification (OTPs, promotional spam, mandate reminders, balance-only alerts)
 * 2. Multi-bank parsing engine (PennyWise BankParserFactory)
 * 3. Specialized CampusFold parsers (CampusSmsParser, AxisBankEmailParser)
 * 4. Generic regex fallback parser
 * 5. Own-transfer detection post-step (cross-account transfers & balance matching)
 */
object MessageExtractor {

    private const val TAG = "MessageExtractor"

    // ─── Regular Expressions for Classification ──────────────────────────────

    // Completed money movement verbs
    private val COMPLETED_MONEY_VERBS = Regex(
        """\b(debited|deducted|credited|deposited|withdrawn|spent|received|transferred|sent|charged|paid|purchased|refunded|reversed)\b""",
        RegexOption.IGNORE_CASE
    )

    // OTP and security verification patterns
    private val OTP_PATTERN = Regex(
        """\b(otp|one[-\s]?time password|verification code|secret code|login code|auth code|security code)\b""",
        RegexOption.IGNORE_CASE
    )

    // Promotional & marketing indicators
    private val PROMO_PATTERNS = listOf(
        Regex("""\b(?:pre-approved|instant loan|apply for loan|personal loan offer|avail loan|credit limit increase)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:upgrade your card|apply for credit card|lifetime free credit card|card approved)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:flat \d+% off|use code|cashback offer|exclusive offer|special discount|hurry up|win cash|congratulations you won)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reward points accumulated|claim your reward|claim your voucher|bonus points)\b""", RegexOption.IGNORE_CASE)
    )

    // Mandate / bill reminder patterns (only active when no completed money verb is present)
    private val MANDATE_REMINDER_PATTERNS = listOf(
        Regex("""\b(?:will|shall)\s+be\s+(?:auto[-\s]?)?debited\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:upcoming\s+mandate|mandate\s+set\s+for)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:will be auto-debited|will be auto debited|will be debited on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:mandate\s+(?:created|scheduled|active|notification|set)|e-mandate\s+(?:set up|created))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:is due on|bill due date|due date is|bill of (?:INR|Rs\.?|₹)?.*?due on|payment due on|minimum amount due|min amount due|pay before|pay by)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment request|has requested|collect request from|requests rs|requesting payment)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reminder:|bill generated|invoice generated|statement generated)\b""", RegexOption.IGNORE_CASE)
    )

    // ASBA / IPO blocking notices (funds blocked, not spent)
    private val ASBA_PATTERN = Regex(
        """\b(?:asba|is blocked in your|earmarked|blocking\s+of\s+funds|upi[-\s]?mandate\s+collect\s+request|collect\s+request\s+for\s+blocking|unblocked|revoked\s+mandate)\b|""" +
        """\bipo\b.*\b(?:app\s*no|application\s*no|application|collect\s+request|blocking\s+of\s+funds|bid\s+received|received.*collect\s+request|bid)\b""",
        RegexOption.IGNORE_CASE
    )

    // Convert-to-EMI offers (marketing invitations, not actual EMI debits)
    private val CONVERT_TO_EMI_PATTERN = Regex(
        """\b(?:convert\s+(?:your\s+)?(?:bill|transaction|purchase)\s+into\s+emi|convert\s+to\s+smartemi|sms\s+smartemi\s+yes)\b""",
        RegexOption.IGNORE_CASE
    )

    // Non-financial administrative alerts
    private val NON_FINANCIAL_PATTERNS = listOf(
        Regex("""\b(?:password (?:has been )?changed|mpin (?:has been )?reset|profile updated|kyc updated|kyc pending|sim swap)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:service request (?:no\.?|number)|request has been registered|complaint resolved)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:welcome to|successfully registered for net banking)\b""", RegexOption.IGNORE_CASE)
    )

    // Balance-only notification patterns
    private val BALANCE_ALERT_PATTERNS = listOf(
        Regex("""(?:avail(?:able)?\s+bal(?:ance)?|clear\s+balance|bal\s+in\s+a/c|balance\s+(?:is|in)|ledger\s+balance|effective\s+balance|account\s+balance\s+is|avail(?:able)?\s+(?:credit\s+)?limit|outstanding\s+bal(?:ance)?).*?(?:\bis\b|:)?\s*(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+(?:is\s+)?(?:your\s+)?(?:avail(?:able)?\s+bal(?:ance)?)""", RegexOption.IGNORE_CASE)
    )

    // Generic transaction extraction regexes
    private val GENERIC_AMOUNT_PATTERN = Regex("""(?:rs\.?|inr|₹|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val GENERIC_ACCOUNT_PATTERN = Regex("""\b(?:A/c|Account|Card|Acct|CC)\s*(?:no\.?)?\s*(?:XX|[*]+)?([0-9]{3,6})\b|\bending\s*(?:in|with)?\s*(?:XX|[*]+)?([0-9]{4})\b""", RegexOption.IGNORE_CASE)
    private val GENERIC_REF_PATTERN = Regex("""(?:upi\s*ref(?:erence)?\s*(?:no\.?|num)?|utr|rrn|txn\s*id|ref\s*no\.?)[:\s]+([0-9A-Za-z]{8,22})""", RegexOption.IGNORE_CASE)

    // Outbound credit card bill payments (strict word boundary to avoid false positives on 'credited')
    private val OUTBOUND_CC_BILL_PATTERN = Regex(
        """\b(CRED|BILLDESK|CCAVENUE|CREDIT\s+CARD\s+BILL|CC\s+BILL|CARD\s+BILL|CARD\s+PAYMENT)\b|(?:\b(?:CARD|CC)\b.*?\b(?:BILL|PAYMENT)\b)""",
        RegexOption.IGNORE_CASE
    )

    // Inbound credit card bill confirmation
    private val INBOUND_CC_BILL_CONFIRMATION_PATTERN = Regex(
        """\b(?:payment\s+received|thank\s+you\s+for\s+(?:your\s+)?payment|credited\s+to\s+your\s+(?:[A-Za-z]+\s+)?(?:Credit\s+Card|CC)|payment\s+of\s+Rs.*?received)\b""",
        RegexOption.IGNORE_CASE
    )

    // ATM Cash withdrawal patterns
    private val ATM_PATTERN = Regex("""\b(atm\s+withdrawal|withdrawn\s+at\s+atm|cash\s+withdrawal|atm\s+cash|withdrawn\s+from\s+atm)\b""", RegexOption.IGNORE_CASE)

    // EMI Payment patterns
    private val EMI_PATTERN = Regex("""\b(towards\s+(?:smart\s*)?emi|smartemi|loan\s+emi|smartemi\s+debited|debited\s+towards\s+(?:smart\s*)?emi|emi\s+debited|auto-debited\s+towards\s+emi)\b""", RegexOption.IGNORE_CASE)

    // Investment / Mutual Funds / SIP / Brokerage patterns
    private val INVESTMENT_PATTERN = Regex(
        """\b(?:ipo\s+allot(?:ment|ted)|mutual\s+fund|sip\s+(?:debit|purchase|installment)|sip\b|zerodha|groww|upstox|angel\s*one|kuvera|coin\s+by\s+zerodha|indmoney|smallcase|nsdl|cdsl|demat)\b""",
        RegexOption.IGNORE_CASE
    )

    // Refund / Reversal patterns
    private val REFUND_PATTERN = Regex("""\b(refund|reversal|refunded|reversed|cashback)\b""", RegexOption.IGNORE_CASE)

    // Self-transfer / Own-transfer phrasing
    private val SELF_TRANSFER_PHRASES = Regex(
        """\b(self\s+transfer|transfer\s+to\s+(?:own|self|another)\s+a/c|own\s+account\s+transfer|internal\s+transfer|transfer\s+to\s+self)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Main Public API ──────────────────────────────────────────────────────

    /**
     * Extracts financial information from a RawMessage.
     * Order of execution:
     * 1. Check for Ignored messages (OTPs, Promotions, Mandates, Non-financial).
     * 2. Check for Balance-Only alerts.
     * 3. PennyWise bank-specific parser chosen by sender / content.
     * 4. Specialized parsers (CampusSmsParser, AxisBankEmailParser).
     * 5. Generic regex fallback.
     * 6. Return Unparsed if no financial details matched.
     * 7. Post-process own-transfer check.
     */
    fun extract(
        rawMessage: RawMessage,
        knownAccounts: List<AccountEntity> = emptyList(),
        recentTransactions: List<TransactionEntity> = emptyList()
    ): ExtractionResult {
        val cleanBody = rawMessage.body.trim()
        if (cleanBody.isBlank()) {
            return ExtractionResult.Unparsed(rawMessage, "Message body is empty")
        }

        // Step 1: Check for Ignored Messages (No content stored)
        val ignoreReason = classifyIgnored(cleanBody)
        if (ignoreReason != null) {
            return ExtractionResult.Ignored(ignoreReason)
        }

        // Step 2: Check for Balance-Only Alerts
        val balanceAlert = checkBalanceAlert(cleanBody, rawMessage.sender, rawMessage.timestamp)
        if (balanceAlert != null) {
            return ExtractionResult.BalanceOnly(balanceAlert)
        }

        // If email or HTML, try CampusFold Specialized Parsers (AxisBankEmailParser) first
        val isEmailOrHtml = rawMessage.source == MessageSource.EMAIL || cleanBody.contains("<html>", ignoreCase = true) || rawMessage.sender.contains("@")
        if (isEmailOrHtml) {
            val emailParsed = AxisBankEmailParser.parse(
                subject = rawMessage.subject ?: "",
                body = cleanBody,
                emailTimestamp = rawMessage.timestamp,
                accounts = knownAccounts
            )
            if (emailParsed != null) {
                if (emailParsed.isConflict) {
                    return ExtractionResult.Unparsed(rawMessage, "Conflicting debit/credit signals in Axis Bank email")
                }
                val tx = emailParsed.transaction
                val direction = if (emailParsed.isCredit) Direction.CREDIT else Direction.DEBIT
                val last4 = extractLast4Digits(tx.paymentMode)
                val bank = CampusSmsParser.detectBankName(cleanBody, rawMessage.sender) ?: "Bank"

                val emailTx = ExtractedTransaction(
                    amountPaise = tx.amountPaise,
                    direction = direction,
                    type = tx.type,
                    bankName = bank,
                    accountLast4 = last4,
                    instrumentType = if (emailParsed.isUpi) "UPI" else "ACCOUNT",
                    merchantRaw = tx.rawMerchant,
                    counterpartyName = tx.friendlyVendorName,
                    reference = tx.upiRef,
                    timestamp = tx.timestamp,
                    balancePaise = null,
                    availableCreditPaise = null,
                    currency = "INR",
                    confidence = 0.90f,
                    parserId = "campusfold:AxisBankEmailParser"
                )
                val adjusted = checkOwnTransfer(emailTx, cleanBody, knownAccounts, recentTransactions)
                return ExtractionResult.Transaction(adjusted)
            }
        }

        // Step 3: Attempt PennyWise Bank Parsers
        val pennyWiseTx = tryPennyWiseParser(rawMessage)
        if (pennyWiseTx != null) {
            val adjusted = checkOwnTransfer(pennyWiseTx, cleanBody, knownAccounts, recentTransactions)
            return ExtractionResult.Transaction(adjusted)
        }

        // Step 4: Attempt CampusFold Specialized Parsers
        val campusFoldTx = tryCampusFoldParsers(rawMessage, knownAccounts)
        if (campusFoldTx != null) {
            val adjusted = checkOwnTransfer(campusFoldTx, cleanBody, knownAccounts, recentTransactions)
            return ExtractionResult.Transaction(adjusted)
        }

        // Step 5: Attempt Generic Regex Fallback
        val genericTx = tryGenericRegexParser(rawMessage, knownAccounts)
        if (genericTx != null) {
            val adjusted = checkOwnTransfer(genericTx, cleanBody, knownAccounts, recentTransactions)
            return ExtractionResult.Transaction(adjusted)
        }

        // Step 6: Fallback to Unparsed for subsequent AI step
        return ExtractionResult.Unparsed(rawMessage, "No financial pattern matched message")
    }

    // ─── Step 1: Classification for Ignored Messages ──────────────────────────

    fun classifyIgnored(body: String): String? {
        // Hard pre-check for future-debit mandate notices (e.g. "will be debited from", "upcoming mandate set for")
        // Must NOT match past debits like "was debited" or "has been debited"
        val isFutureMandateNotice = Regex("""\b(?:will|shall)\s+be\s+(?:auto[-\s]?)?debited\b|\bupcoming\s+mandate\b|\bmandate\s+set\s+for\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val isCompletedPastDebit = body.contains("was debited", ignoreCase = true) ||
                body.contains("has been debited", ignoreCase = true) ||
                body.contains("has debited", ignoreCase = true) ||
                body.contains("is debited", ignoreCase = true)

        if (isFutureMandateNotice && !isCompletedPastDebit) {
            return "Upcoming mandate debit notice"
        }

        val hasCompletedMoneyMovement = COMPLETED_MONEY_VERBS.containsMatchIn(body)

        // OTPs & verification codes
        // If money movement has already completed (e.g. debited, credited, spent, paid),
        // any mention of OTP/password/pin is purely in bank security advisory footers ("do not share OTP", etc.).
        if (!hasCompletedMoneyMovement) {
            // Strip any warning disclaimers like "Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTP with anyone"
            val disclaimerRegex = Regex("""(?i)\b(?:never|do not|don't|please do not)\s+share\s+[^.\r\n]*?(?:otp|password|pin|cvv)[^.\r\n]*""")
            val bankNeverAsksRegex = Regex("""(?i)\b(?:bank|axis bank|sbi|hdfc|icici)\s+never\s+asks\s+[^.\r\n]*?(?:otp|password|pin|cvv)[^.\r\n]*""")
            val bodyWithoutDisclaimer = body.replace(disclaimerRegex, "").replace(bankNeverAsksRegex, "")
            if (OTP_PATTERN.containsMatchIn(bodyWithoutDisclaimer)) {
                return "OTP / Verification Code"
            }
        } else {
            // Money verbs present: Only classify as OTP if it is an explicit OTP authorization request for a pending txn
            val isExplicitOtpRequest = Regex("""\b(?:otp\s+is|is\s+your\s+otp|use\s+otp|enter\s+otp|otp\s+to\s+(?:approve|authenticate|verify|complete)|verification\s+code\s+is)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
            if (isExplicitOtpRequest && !body.contains("was debited", ignoreCase = true) && !body.contains("was credited", ignoreCase = true) && !body.contains("Amount Debited", ignoreCase = true) && !body.contains("Amount Credited", ignoreCase = true)) {
                return "OTP / Verification Code"
            }
        }

        // ASBA / IPO fund earmarking
        if (ASBA_PATTERN.containsMatchIn(body)) {
            return "ASBA / IPO Fund Blocking"
        }

        // Convert to EMI offers (only when not an actual debit)
        if (CONVERT_TO_EMI_PATTERN.containsMatchIn(body) && !hasCompletedMoneyMovement) {
            return "Convert to EMI Marketing Offer"
        }

        // Mandate & due reminders (when no money has moved yet, or future auto-debit)
        val isFutureDebit = Regex("""\b(?:will be|scheduled to be|set to be)\s+(?:auto-?)?debited\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (!hasCompletedMoneyMovement || isFutureDebit) {
            for (pattern in MANDATE_REMINDER_PATTERNS) {
                if (pattern.containsMatchIn(body)) {
                    return "Mandate / Due Date Reminder"
                }
            }

            for (pattern in PROMO_PATTERNS) {
                if (pattern.containsMatchIn(body)) {
                    return "Promotional / Marketing Offer"
                }
            }

            for (pattern in NON_FINANCIAL_PATTERNS) {
                if (pattern.containsMatchIn(body)) {
                    return "Non-Financial Administrative Alert"
                }
            }
        }

        return null
    }

    // ─── Step 2: Balance-Only Alert Detection ─────────────────────────────────

    fun checkBalanceAlert(body: String, sender: String, timestamp: Long): BalanceUpdate? {
        // If it contains completed movement verbs, it's not a balance-only alert
        if (COMPLETED_MONEY_VERBS.containsMatchIn(body)) {
            return null
        }

        for (pattern in BALANCE_ALERT_PATTERNS) {
            val match = pattern.find(body)
            if (match != null) {
                val rawAmount = match.groupValues.getOrNull(1)?.replace(",", "")
                val balanceRupees = rawAmount?.toDoubleOrNull()
                if (balanceRupees != null && balanceRupees >= 0.0) {
                    val balancePaise = CurrencyUtils.rupeesToPaise(balanceRupees)
                    val tailMatch = GENERIC_ACCOUNT_PATTERN.find(body)
                    val last4 = tailMatch?.groupValues?.getOrNull(1)?.ifEmpty { null }
                        ?: tailMatch?.groupValues?.getOrNull(2)?.ifEmpty { null }
                        ?: extractLast4Digits(body)
                    val bankName = CampusSmsParser.detectBankName(body, sender)

                    return BalanceUpdate(
                        bankName = bankName,
                        accountLast4 = last4,
                        balancePaise = balancePaise,
                        availableCreditPaise = null,
                        timestamp = timestamp,
                        currency = "INR",
                        confidence = 0.90f,
                        parserId = "balance:alert"
                    )
                }
            }
        }

        return null
    }

    // ─── Step 3: PennyWise Parser Integration ─────────────────────────────────

    private fun tryPennyWiseParser(rawMessage: RawMessage): ExtractedTransaction? {
        val cleanSender = rawMessage.sender.trim()
        val cleanBody = rawMessage.body.trim()
        val timestamp = rawMessage.timestamp

        // 1. Try matching parsers by sender
        var parsers = BankParserFactory.getParsers(cleanSender)

        // If sender didn't match directly (e.g. email or generic sender), search by detected bank
        if (parsers.isEmpty()) {
            val detectedBank = CampusSmsParser.detectBankName(cleanBody, cleanSender)
            if (detectedBank != null) {
                val bankParser = BankParserFactory.getParserByName(detectedBank)
                if (bankParser != null) {
                    parsers = listOf(bankParser)
                }
            }
        }

        if (parsers.isEmpty()) return null

        val parsedTx: ParsedTransaction = parsers.firstNotNullOfOrNull { parser ->
            try {
                parser.parse(cleanBody, cleanSender, timestamp)
            } catch (e: Exception) {
                null
            }
        } ?: return null

        // Handle BALANCE_UPDATE returned by PennyWise parsers (e.g. FederalBankParser)
        if (parsedTx.type == com.pennywiseai.parser.core.TransactionType.BALANCE_UPDATE) {
            return null // Balance-only handled separately
        }

        val amountPaise = CurrencyUtils.rupeesToPaise(parsedTx.amount.toDouble())
        if (amountPaise <= 0L) return null

        val balancePaise = parsedTx.balance?.toDouble()?.let { CurrencyUtils.rupeesToPaise(it) }
        val creditLimitPaise = parsedTx.creditLimit?.toDouble()?.let { CurrencyUtils.rupeesToPaise(it) }
            ?: Regex("""(?:Avl|Available)\s+limit[\s:]*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
                .find(cleanBody)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
                ?.let { CurrencyUtils.rupeesToPaise(it) }

        // Determine Direction & TransactionType
        val (direction, type) = mapPennyWiseType(parsedTx, cleanBody)

        val instrumentType = when {
            parsedTx.isFromCard -> "CARD"
            cleanBody.contains("upi", ignoreCase = true) -> "UPI"
            parsedTx.isMobileWallet -> "WALLET"
            else -> "ACCOUNT"
        }

        val last4 = parsedTx.accountLast4?.trim()?.takeIf { it.isNotBlank() }
        val confidence = if (last4 != null) 0.95f else 0.85f

        return ExtractedTransaction(
            amountPaise = amountPaise,
            direction = direction,
            type = type,
            bankName = parsedTx.bankName,
            accountLast4 = last4,
            instrumentType = instrumentType,
            merchantRaw = parsedTx.merchant?.trim()?.takeIf { it.isNotBlank() },
            counterpartyName = parsedTx.merchant?.trim()?.takeIf { it.isNotBlank() },
            reference = parsedTx.reference?.trim()?.takeIf { it.isNotBlank() },
            timestamp = if (parsedTx.timestamp > 0L) parsedTx.timestamp else timestamp,
            balancePaise = balancePaise,
            availableCreditPaise = creditLimitPaise,
            currency = parsedTx.currency.ifBlank { "INR" },
            confidence = confidence,
            parserId = "pennywise:${parsedTx.bankName}"
        )
    }

    private fun mapPennyWiseType(
        parsedTx: ParsedTransaction,
        body: String
    ): Pair<Direction, TransactionType> {
        val lower = body.lowercase()

        return when (parsedTx.type) {
            com.pennywiseai.parser.core.TransactionType.INCOME -> {
                // Check if card bill payment confirmation
                if (INBOUND_CC_BILL_CONFIRMATION_PATTERN.containsMatchIn(body) ||
                    (lower.contains("payment received") && (lower.contains("card") || lower.contains("cc")))) {
                    Direction.CREDIT to TransactionType.CC_BILL_TRANSFER
                } else if (REFUND_PATTERN.containsMatchIn(body)) {
                    Direction.CREDIT to TransactionType.REFUND
                } else {
                    Direction.CREDIT to TransactionType.INCOME
                }
            }

            com.pennywiseai.parser.core.TransactionType.CREDIT,
            com.pennywiseai.parser.core.TransactionType.EXPENSE -> {
                val isCcBill = OUTBOUND_CC_BILL_PATTERN.containsMatchIn(parsedTx.merchant ?: "") ||
                        OUTBOUND_CC_BILL_PATTERN.containsMatchIn(body)

                val txType = when {
                    isCcBill -> TransactionType.CC_BILL_TRANSFER
                    INVESTMENT_PATTERN.containsMatchIn(body) || INVESTMENT_PATTERN.containsMatchIn(parsedTx.merchant ?: "") -> TransactionType.INVESTMENT
                    ATM_PATTERN.containsMatchIn(body) -> TransactionType.ATM
                    EMI_PATTERN.containsMatchIn(body) -> TransactionType.EMI
                    else -> TransactionType.EXPENSE
                }
                Direction.DEBIT to txType
            }

            com.pennywiseai.parser.core.TransactionType.TRANSFER -> {
                val isDebit = lower.contains("debited") || lower.contains("sent") || lower.contains("paid")
                val direction = if (isDebit) Direction.DEBIT else Direction.CREDIT
                val type = if (isDebit) TransactionType.EXPENSE else TransactionType.INCOME
                direction to type
            }

            com.pennywiseai.parser.core.TransactionType.INVESTMENT -> {
                Direction.DEBIT to TransactionType.INVESTMENT
            }

            com.pennywiseai.parser.core.TransactionType.BALANCE_UPDATE -> {
                Direction.DEBIT to TransactionType.EXPENSE
            }
        }
    }

    // ─── Step 4: Specialized CampusFold Parsers ───────────────────────────────

    private fun tryCampusFoldParsers(
        rawMessage: RawMessage,
        knownAccounts: List<AccountEntity>
    ): ExtractedTransaction? {
        val cleanBody = rawMessage.body.trim()
        val sender = rawMessage.sender.trim()
        val timestamp = rawMessage.timestamp

        // A. If email, try AxisBankEmailParser
        if (rawMessage.source == MessageSource.EMAIL || cleanBody.contains("<html>", ignoreCase = true) || sender.contains("@")) {
            val emailParsed = AxisBankEmailParser.parse(
                subject = rawMessage.subject ?: "",
                body = cleanBody,
                emailTimestamp = timestamp,
                accounts = knownAccounts
            )
            if (emailParsed != null) {
                val tx = emailParsed.transaction
                val direction = if (emailParsed.isCredit) Direction.CREDIT else Direction.DEBIT
                val last4 = extractLast4Digits(tx.paymentMode)
                val bank = CampusSmsParser.detectBankName(cleanBody, sender) ?: "Bank"

                return ExtractedTransaction(
                    amountPaise = tx.amountPaise,
                    direction = direction,
                    type = tx.type,
                    bankName = bank,
                    accountLast4 = last4,
                    instrumentType = if (emailParsed.isUpi) "UPI" else "ACCOUNT",
                    merchantRaw = tx.rawMerchant,
                    counterpartyName = tx.friendlyVendorName,
                    reference = tx.upiRef,
                    timestamp = tx.timestamp,
                    balancePaise = null,
                    availableCreditPaise = null,
                    currency = "INR",
                    confidence = 0.90f,
                    parserId = "campusfold:AxisBankEmailParser"
                )
            }
        }

        // B. Try CampusSmsParser (Format 1 Card, Format 2 Account Line, and Generic Fallback)
        val campusParsed = CampusSmsParser.parse(cleanBody, sender, timestamp, knownAccounts)
        if (campusParsed != null) {
            val tx = campusParsed.transaction
            val isIncome = tx.type == TransactionType.INCOME ||
                    (tx.type == TransactionType.CC_BILL_TRANSFER && cleanBody.contains("payment received", ignoreCase = true))
            val direction = if (isIncome) Direction.CREDIT else Direction.DEBIT
            val last4 = extractLast4Digits(tx.paymentMode)
            val bank = CampusSmsParser.detectBankName(cleanBody, sender) ?: "Bank"

            val isCard = tx.paymentMode.contains("CC", ignoreCase = true) ||
                    cleanBody.contains("Credit Card", ignoreCase = true)
            val instrument = if (isCard) "CARD" else if (cleanBody.contains("upi", true)) "UPI" else "ACCOUNT"

            val confidence = if (last4 != null) 0.88f else 0.75f
            val limitPaise = campusParsed.extractedCreditLimit?.let { CurrencyUtils.rupeesToPaise(it) }

            return ExtractedTransaction(
                amountPaise = tx.amountPaise,
                direction = direction,
                type = tx.type,
                bankName = bank,
                accountLast4 = last4,
                instrumentType = instrument,
                merchantRaw = tx.rawMerchant,
                counterpartyName = tx.friendlyVendorName,
                reference = tx.upiRef,
                timestamp = tx.timestamp,
                balancePaise = null,
                availableCreditPaise = limitPaise,
                currency = "INR",
                confidence = confidence,
                parserId = "campusfold:CampusSmsParser"
            )
        }

        return null
    }

    // ─── Step 5: Generic Regex Fallback Parser ────────────────────────────────

    private fun tryGenericRegexParser(
        rawMessage: RawMessage,
        knownAccounts: List<AccountEntity>
    ): ExtractedTransaction? {
        val cleanBody = rawMessage.body.trim()
        val lower = cleanBody.lowercase()

        // Must have an amount
        val amountMatch = GENERIC_AMOUNT_PATTERN.find(cleanBody) ?: return null
        val amountRupees = amountMatch.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amountRupees <= 0.0) return null
        val amountPaise = CurrencyUtils.rupeesToPaise(amountRupees)

        // Determine direction
        val hasDebit = Regex("""\b(debited|spent|paid|withdrawn|deducted|charged)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody)
        val hasCredit = Regex("""\b(credited|received|refunded|deposited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanBody)

        if (!hasDebit && !hasCredit) return null
        val direction = if (hasCredit) Direction.CREDIT else Direction.DEBIT

        // Detect type
        val isCcBill = OUTBOUND_CC_BILL_PATTERN.containsMatchIn(cleanBody)
        val isRefund = REFUND_PATTERN.containsMatchIn(cleanBody)
        val isAtm = ATM_PATTERN.containsMatchIn(cleanBody)
        val isEmi = EMI_PATTERN.containsMatchIn(cleanBody)
        val isInvestment = INVESTMENT_PATTERN.containsMatchIn(cleanBody)

        val type = when {
            isCcBill -> TransactionType.CC_BILL_TRANSFER
            isRefund -> TransactionType.REFUND
            isAtm -> TransactionType.ATM
            isEmi -> TransactionType.EMI
            isInvestment && direction == Direction.DEBIT -> TransactionType.INVESTMENT
            direction == Direction.CREDIT && !cleanBody.contains("ipo", ignoreCase = true) -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        // Extract account tail & bank
        val accountTailMatch = GENERIC_ACCOUNT_PATTERN.find(cleanBody)
        val last4 = accountTailMatch?.groupValues?.getOrNull(1)?.ifEmpty { null }
            ?: accountTailMatch?.groupValues?.getOrNull(2)?.ifEmpty { null }
        val bankName = CampusSmsParser.detectBankName(cleanBody, rawMessage.sender)

        // Extract reference
        val reference = GENERIC_REF_PATTERN.find(cleanBody)?.groupValues?.getOrNull(1)

        // Extract merchant/counterparty
        val merchant = extractGenericMerchant(cleanBody, direction)

        val confidence = when {
            last4 != null && bankName != null -> 0.75f
            last4 != null || bankName != null -> 0.65f
            else -> 0.50f
        }

        val instrument = when {
            cleanBody.contains("card", ignoreCase = true) -> "CARD"
            cleanBody.contains("upi", ignoreCase = true) -> "UPI"
            else -> "ACCOUNT"
        }

        return ExtractedTransaction(
            amountPaise = amountPaise,
            direction = direction,
            type = type,
            bankName = bankName,
            accountLast4 = last4,
            instrumentType = instrument,
            merchantRaw = merchant,
            counterpartyName = merchant,
            reference = reference,
            timestamp = rawMessage.timestamp,
            balancePaise = null,
            availableCreditPaise = null,
            currency = "INR",
            confidence = confidence,
            parserId = "campusfold:GenericRegex"
        )
    }

    private fun extractGenericMerchant(body: String, direction: Direction): String {
        val merchantPatterns = listOf(
            Regex("""(?:VPA|UPI ID|to VPA)[:\s]+([A-Za-z0-9._-]{2,60}@[A-Za-z0-9._-]{2,30})""", RegexOption.IGNORE_CASE),
            Regex("""(?:paid to|transferred to|sent to|to)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:received from|transferred by|sent by|from|by)\s+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:at|towards|merchant|store|info)\s*[:\s]+([A-Za-z0-9 &'._-]{2,40}?)(?=\s+(?:on|via|using|ref|upi|utr|a/c|acct|from|avbl)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val candidate = match.groupValues.getOrNull(1)?.trim()
                if (!candidate.isNullOrBlank() &&
                    !candidate.equals("a/c", ignoreCase = true) &&
                    !candidate.equals("account", ignoreCase = true) &&
                    !candidate.equals("card", ignoreCase = true) &&
                    !candidate.equals("upi", ignoreCase = true)) {
                    return candidate
                }
            }
        }

        return if (direction == Direction.CREDIT) "Credit / Inflow" else "Debit / Spend"
    }

    // ─── Step 7: Own-Transfer Detection (Post-Step) ───────────────────────────

    /**
     * Checks if an extracted transaction is an internal transfer between the user's own accounts:
     * 1. If text explicitly mentions another of the user's accounts or says "self transfer"
     * 2. If an opposite debit/credit with the exact same amount exists within a 10-minute window across different accounts
     */
    fun checkOwnTransfer(
        extracted: ExtractedTransaction,
        body: String,
        knownAccounts: List<AccountEntity>,
        recentTransactions: List<TransactionEntity>
    ): ExtractedTransaction {
        // Condition A: Explicit self-transfer phrases
        if (SELF_TRANSFER_PHRASES.containsMatchIn(body)) {
            return extracted.copy(type = TransactionType.OWN_TRANSFER)
        }

        // Condition B: Message text names another of the user's accounts
        if (knownAccounts.size >= 2) {
            val myAccountLast4 = extracted.accountLast4
            val otherAccounts = knownAccounts.filter { it.last4.isNotBlank() && it.last4 != myAccountLast4 }

            for (other in otherAccounts) {
                val otherTail = other.last4.trim().lowercase()
                val otherBank = other.bankName.trim().lowercase()
                val otherName = other.displayName.trim().lowercase()
                val lowerBody = body.lowercase()

                val mentionsOtherTail = otherTail.length >= 4 && (lowerBody.contains(otherTail) || lowerBody.contains("xx$otherTail"))
                val mentionsOtherBank = otherBank.isNotEmpty() && lowerBody.contains(otherBank)
                val mentionsOtherName = otherName.length >= 5 && lowerBody.contains(otherName)

                if (mentionsOtherName || (mentionsOtherTail && (mentionsOtherBank || lowerBody.contains("transfer") || lowerBody.contains("to a/c")))) {
                    return extracted.copy(type = TransactionType.OWN_TRANSFER)
                }
            }
        }

        // Condition C: Matching opposite transaction with same amount within 10 minutes
        // Crucial: Only applies when the two legs are on two distinct accounts belonging to the user!
        if (recentTransactions.isNotEmpty()) {
            val tenMinutesMs = 10 * 60 * 1000L
            val isMatchingOpposite = recentTransactions.any { recent ->
                val timeDiff = kotlin.math.abs(recent.timestamp - extracted.timestamp)
                if (timeDiff <= tenMinutesMs && recent.amountPaise == extracted.amountPaise) {
                    val recentIsDebit = recent.type == TransactionType.EXPENSE || recent.type == TransactionType.OWN_TRANSFER
                    val recentIsCredit = recent.type == TransactionType.INCOME || recent.type == TransactionType.SPLIT_SETTLEMENT
                    val oppositeDirection = (extracted.direction == Direction.DEBIT && recentIsCredit) ||
                            (extracted.direction == Direction.CREDIT && recentIsDebit)

                    if (!oppositeDirection) return@any false

                    val recentAccount = knownAccounts.find { it.id == recent.accountId || (recent.paymentMode.isNotBlank() && it.displayName.equals(recent.paymentMode, ignoreCase = true)) }
                    val extractedAccount = knownAccounts.find {
                        (extracted.accountLast4 != null && it.last4.isNotBlank() && it.last4.endsWith(extracted.accountLast4)) ||
                        (extracted.bankName != null && it.bankName.equals(extracted.bankName, ignoreCase = true))
                    }
                    val isDifferentOwnAccounts = recentAccount != null && extractedAccount != null && recentAccount.id != extractedAccount.id

                    isDifferentOwnAccounts
                } else {
                    false
                }
            }

            if (isMatchingOpposite) {
                return extracted.copy(type = TransactionType.OWN_TRANSFER)
            }
        }

        return extracted
    }

    /**
     * Batch post-processor for historical scans or inbox imports.
     * Identifies debit-credit pairs with the same amount within a 10-minute window and marks them as OWN_TRANSFER.
     */
    fun postProcessOwnTransfers(
        transactions: List<ExtractedTransaction>,
        knownAccounts: List<AccountEntity> = emptyList()
    ): List<ExtractedTransaction> {
        val tenMinutesMs = 10 * 60 * 1000L
        val matchedIndices = mutableSetOf<Int>()

        for (i in transactions.indices) {
            val tx1 = transactions[i]
            if (tx1.type == TransactionType.OWN_TRANSFER) {
                matchedIndices.add(i)
                continue
            }

            for (j in (i + 1) until transactions.size) {
                val tx2 = transactions[j]
                if (j in matchedIndices) continue

                val timeDiff = kotlin.math.abs(tx1.timestamp - tx2.timestamp)
                if (timeDiff <= tenMinutesMs && tx1.amountPaise == tx2.amountPaise && tx1.direction != tx2.direction) {
                    // Check they belong to different accounts if known
                    val diffAccounts = tx1.accountLast4 == null || tx2.accountLast4 == null || tx1.accountLast4 != tx2.accountLast4
                    if (diffAccounts) {
                        matchedIndices.add(i)
                        matchedIndices.add(j)
                        break
                    }
                }
            }
        }

        return transactions.mapIndexed { index, tx ->
            if (index in matchedIndices) tx.copy(type = TransactionType.OWN_TRANSFER) else tx
        }
    }

    // ─── Utility Helpers ──────────────────────────────────────────────────────

    private fun extractLast4Digits(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""(?:XX|[*]+)?([0-9]{4})\b""").find(text)
        return match?.groupValues?.getOrNull(1)
    }
}
