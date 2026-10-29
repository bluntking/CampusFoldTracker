package com.campusfold.tracker.util

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedEmailTransaction(
    val transaction: TransactionEntity,
    val senderOrMerchant: String,
    val isCredit: Boolean,
    val isUpi: Boolean,
    val isConflict: Boolean = false
)

private enum class SignalDirection { CREDIT, DEBIT, CONFLICT, NONE }

object AxisBankEmailParser {

    // Amount patterns
    private val AMOUNT_DEBITED_PATTERN = Pattern.compile("Amount\\s+Debited:?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val AMOUNT_CREDITED_PATTERN = Pattern.compile("Amount\\s+Credited:?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val SUBJECT_DEBIT_PATTERN = Pattern.compile("(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)?\\s*was\\s+debited", Pattern.CASE_INSENSITIVE)
    private val SUBJECT_CREDIT_PATTERN = Pattern.compile("(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)?\\s*was\\s+credited", Pattern.CASE_INSENSITIVE)
    private val GENERIC_AMOUNT_PATTERN = Pattern.compile("(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)

    // Account patterns
    private val ACCOUNT_NUMBER_PATTERN = Pattern.compile("Account\\s+Number:?\\s*(?:XX|X+|\\*+)?(\\d{3,4})", Pattern.CASE_INSENSITIVE)
    private val ACCOUNT_SUBJECT_PATTERN = Pattern.compile("(?:A/c|Account|acct)\\s+(?:no\\.?|ending\\s+with)?[\\s:]*(?:XX|X+|\\*+)?(\\d{3,4})", Pattern.CASE_INSENSITIVE)

    // UPI info: e.g. UPI/P2M/666113532290/DD STORE or UPI/P2A/218606473101/KAVYA KOH/KKBK/Sent
    private val UPI_INFO_PATTERN = Pattern.compile("UPI/(?:P2M|P2A|P2P)/(\\d+)/([^/\\r\\n<]+)", Pattern.CASE_INSENSITIVE)
    private val UPI_REF_ONLY_PATTERN = Pattern.compile("(?:UPI\\s+Ref(?:\\s+no)?|Ref\\s+no|RRN)\\.?\\s*:?\\s*(\\d{9,})", Pattern.CASE_INSENSITIVE)
    private val AVAILABLE_BAL_PATTERN = Pattern.compile("(?:Available\\s+Balance|Avl\\s+Bal(?:ance)?):?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)

    // Date & Time pattern: e.g. 22-10-26, 20:36:32 IST or 21-10-2026
    private val DATE_TIME_PATTERN = Pattern.compile("Date\\s*&\\s*Time:?\\s*([0-9]{2}-[0-9]{2}-[0-9]{2,4}[^\\r\\n<]*)", Pattern.CASE_INSENSITIVE)
    private val DATE_HEADER_PATTERN = Pattern.compile("\\b(\\d{2}-\\d{2}-\\d{4})\\b")

    // Transaction Info fallback pattern: e.g. Transaction Info: UPI/P2M/666113532290/DD STORE or Transaction Info: DD STORE
    private val TX_INFO_LINE_PATTERN = Pattern.compile("Transaction\\s+Info:?\\s*([^\\r\\n<]+)", Pattern.CASE_INSENSITIVE)
    private val PAID_TO_PATTERN = Pattern.compile("(?:Paid\\s+to|Sent\\s+to|Merchant:?|To:?)\\s+([^\\r\\n<,]+)", Pattern.CASE_INSENSITIVE)
    private val RECEIVED_FROM_PATTERN = Pattern.compile("(?:Received\\s+from|From:?|Sender:?)\\s+([^\\r\\n<,]+)", Pattern.CASE_INSENSITIVE)

    /**
     * Parses an Axis Bank email (plain text or HTML) for either debit or credit transactions.
     * Matches against configured accounts in DB or falls back cleanly to Axis Bank XX2003.
     */
    fun parse(
        subject: String,
        body: String,
        emailTimestamp: Long = System.currentTimeMillis(),
        accounts: List<AccountEntity> = emptyList()
    ): ParsedEmailTransaction? {
        val cleanSubject = decodeMimeHeader(subject)
        val cleanBody = stripHtmlAndNormalize(body)
        val primaryBody = removeBoilerplateAndFooter(cleanBody)
        val combined = "$cleanSubject\n$cleanBody"
        if (com.campusfold.tracker.parser.MessageExtractor.classifyIgnored(combined) != null) return null

        // Primary Signal Evaluation (in order of priority):
        // 1. Labelled field: "Amount Credited" vs "Amount Debited"
        val hasLabelCredit = AMOUNT_CREDITED_PATTERN.matcher(cleanBody).find() ||
                Regex("""(?i)\b(?:Amount|Amt\.?)\s+(?:Credited|Credit)\b|\b(?:Credited|Credit)\s+Amount\b|\bAmount\s+Credited\b""").containsMatchIn(cleanBody)
        val hasLabelDebit = AMOUNT_DEBITED_PATTERN.matcher(cleanBody).find() ||
                Regex("""(?i)\b(?:Amount|Amt\.?)\s+(?:Debited|Debit)\b|\b(?:Debited|Debit)\s+Amount\b|\bAmount\s+Debited\b""").containsMatchIn(cleanBody)

        // 2. Subject sentence: "was credited" / "credited to your" vs "was debited" / "debited from your"
        val hasSubjCredit = cleanSubject.contains("was credited", ignoreCase = true) ||
                cleanSubject.contains("credited to your", ignoreCase = true) ||
                cleanSubject.contains("has been credited", ignoreCase = true) ||
                cleanSubject.contains("credited with", ignoreCase = true) ||
                Regex("""(?i)\b(?:INR|Rs\.?|₹)?\s*[\d,]+(?:\.\d{1,2})?\s*(?:was\s+|is\s+|has been\s+)?credited\b""").containsMatchIn(cleanSubject)
        val hasSubjDebit = cleanSubject.contains("was debited", ignoreCase = true) ||
                cleanSubject.contains("debited from your", ignoreCase = true) ||
                cleanSubject.contains("has been debited", ignoreCase = true) ||
                cleanSubject.contains("debited with", ignoreCase = true) ||
                Regex("""(?i)\b(?:INR|Rs\.?|₹)?\s*[\d,]+(?:\.\d{1,2})?\s*(?:was\s+|is\s+|has been\s+)?debited\b""").containsMatchIn(cleanSubject)

        // 3. Body sentence in primary body (excluding boilerplate/footer)
        val hasBodyCredit = primaryBody.contains("was credited", ignoreCase = true) ||
                primaryBody.contains("credited to your", ignoreCase = true) ||
                primaryBody.contains("has been credited", ignoreCase = true) ||
                primaryBody.contains("credited with", ignoreCase = true) ||
                primaryBody.contains("Amount Credited", ignoreCase = true)
        val hasBodyDebit = primaryBody.contains("was debited", ignoreCase = true) ||
                primaryBody.contains("debited from your", ignoreCase = true) ||
                primaryBody.contains("has been debited", ignoreCase = true) ||
                primaryBody.contains("debited with", ignoreCase = true) ||
                primaryBody.contains("Amount Debited", ignoreCase = true)

        // Check for genuine conflicts between signals
        val labelSignal = when {
            hasLabelCredit && hasLabelDebit -> SignalDirection.CONFLICT
            hasLabelCredit -> SignalDirection.CREDIT
            hasLabelDebit -> SignalDirection.DEBIT
            else -> SignalDirection.NONE
        }

        val subjSignal = when {
            hasSubjCredit && hasSubjDebit -> SignalDirection.CONFLICT
            hasSubjCredit -> SignalDirection.CREDIT
            hasSubjDebit -> SignalDirection.DEBIT
            else -> SignalDirection.NONE
        }

        val bodySignal = when {
            hasBodyCredit && hasBodyDebit -> SignalDirection.CONFLICT
            hasBodyCredit -> SignalDirection.CREDIT
            hasBodyDebit -> SignalDirection.DEBIT
            else -> SignalDirection.NONE
        }

        if (labelSignal == SignalDirection.CONFLICT || subjSignal == SignalDirection.CONFLICT || (bodySignal == SignalDirection.CONFLICT && labelSignal == SignalDirection.NONE && subjSignal == SignalDirection.NONE)) {
            val dummyTx = TransactionEntity(amountPaise = 0L, type = TransactionType.EXPENSE)
            return ParsedEmailTransaction(transaction = dummyTx, senderOrMerchant = "", isCredit = false, isUpi = false, isConflict = true)
        }

        if (labelSignal != SignalDirection.NONE && subjSignal != SignalDirection.NONE && labelSignal != subjSignal) {
            val dummyTx = TransactionEntity(amountPaise = 0L, type = TransactionType.EXPENSE)
            return ParsedEmailTransaction(transaction = dummyTx, senderOrMerchant = "", isCredit = false, isUpi = false, isConflict = true)
        }

        val chosenDirection = when {
            labelSignal != SignalDirection.NONE -> labelSignal
            subjSignal != SignalDirection.NONE -> subjSignal
            bodySignal != SignalDirection.NONE -> bodySignal
            else -> SignalDirection.NONE
        }

        if (chosenDirection == SignalDirection.NONE) {
            return null
        }

        val isCredit = (chosenDirection == SignalDirection.CREDIT)
        val isDebit = !isCredit

        // 1. Extract Amount
        var amount: Double? = null
        if (isDebit) {
            val m = AMOUNT_DEBITED_PATTERN.matcher(cleanBody)
            if (m.find()) {
                amount = m.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
            if (amount == null) {
                val sm = SUBJECT_DEBIT_PATTERN.matcher(cleanSubject)
                if (sm.find()) {
                    amount = sm.group(1)?.replace(",", "")?.toDoubleOrNull()
                }
            }
        } else {
            val m = AMOUNT_CREDITED_PATTERN.matcher(cleanBody)
            if (m.find()) {
                amount = m.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
            if (amount == null) {
                val sm = SUBJECT_CREDIT_PATTERN.matcher(cleanSubject)
                if (sm.find()) {
                    amount = sm.group(1)?.replace(",", "")?.toDoubleOrNull()
                }
            }
        }

        if (amount == null) {
            val gm = GENERIC_AMOUNT_PATTERN.matcher(primaryBody.ifBlank { cleanBody })
            if (gm.find()) {
                amount = gm.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }
        if (amount == null) {
            val smGen = GENERIC_AMOUNT_PATTERN.matcher(cleanSubject)
            if (smGen.find()) {
                amount = smGen.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }

        if (amount == null || amount <= 0.0) {
            return null
        }

        val detectedBank = "Axis Bank"

        // 2. Extract Account Tail (e.g. 2003)
        var accountTail: String? = null
        val accM = ACCOUNT_NUMBER_PATTERN.matcher(cleanBody)
        if (accM.find()) {
            accountTail = accM.group(1)
        } else {
            val accSubM = ACCOUNT_SUBJECT_PATTERN.matcher(combined)
            if (accSubM.find()) {
                accountTail = accSubM.group(1)
            }
        }

        // Match against database accounts
        val matchedAccount = accounts.find {
            accountTail != null && it.last4.isNotBlank() && accountTail.endsWith(it.last4)
        }
        val paymentMode = matchedAccount?.displayName ?: matchedAccount?.name
            ?: if (accountTail != null) "$detectedBank XX$accountTail" else "$detectedBank Account"
        val categoryId: Long? = null

        // 3. Extract UPI Info (Ref & Merchant / Sender)
        var upiRef: String? = null
        var rawMerchant: String? = null

        val upiM = UPI_INFO_PATTERN.matcher(cleanBody)
        if (upiM.find()) {
            upiRef = upiM.group(1)?.trim()
            rawMerchant = upiM.group(2)?.trim()
        } else {
            val refM = UPI_REF_ONLY_PATTERN.matcher(cleanBody)
            if (refM.find()) {
                upiRef = refM.group(1)?.trim()
            }
        }

        // If no merchant extracted from UPI pattern, try fallback patterns
        if (rawMerchant.isNullOrBlank()) {
            val txLineM = TX_INFO_LINE_PATTERN.matcher(cleanBody)
            if (txLineM.find()) {
                val fullInfo = txLineM.group(1)?.trim() ?: ""
                // Might look like "UPI/P2M/666113532290/DD STORE" or "UPI/P2A/218606473101/KAVYA KOH/KKBK/Sent"
                val parts = fullInfo.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 4 && parts[0].equals("UPI", ignoreCase = true)) {
                    if (upiRef == null && parts[2].all { it.isDigit() }) upiRef = parts[2]
                    rawMerchant = parts[3]
                } else if (parts.size >= 3) {
                    if (upiRef == null && parts[1].all { it.isDigit() }) upiRef = parts[1]
                    rawMerchant = if (parts[0].equals("UPI", ignoreCase = true)) parts[2] else parts.last()
                } else if (parts.size == 2) {
                    rawMerchant = parts[1]
                } else if (fullInfo.isNotBlank()) {
                    rawMerchant = fullInfo
                }
            }
        }

        if (rawMerchant.isNullOrBlank()) {
            val pattern = if (isDebit) PAID_TO_PATTERN else RECEIVED_FROM_PATTERN
            val m = pattern.matcher(cleanBody)
            if (m.find()) {
                rawMerchant = m.group(1)?.trim()
            }
        }

        rawMerchant = rawMerchant?.trim()?.removeSuffix("/Sent")?.removeSuffix("/sent")?.trim()

        if (rawMerchant.isNullOrBlank()) {
            rawMerchant = if (isDebit) "$detectedBank Debit" else "$detectedBank Credit"
        }

        // 4. Parse Date & Time
        var timestamp = emailTimestamp
        val dateM = DATE_TIME_PATTERN.matcher(cleanBody)
        if (dateM.find()) {
            val rawDateStr = dateM.group(1)?.trim()
            val parsedTs = parseDateTimeString(rawDateStr)
            if (parsedTs != null) {
                timestamp = parsedTs
            }
        }

        val txType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE
        val friendlyName = rawMerchant

        val tx = TransactionEntity(
            amount = amount,
            myActualShare = amount,
            type = txType,
            paymentMode = paymentMode,
            rawMerchant = rawMerchant,
            friendlyVendorName = friendlyName,
            categoryId = categoryId,
            timestamp = timestamp,
            upiRef = upiRef,
            rawSms = "Email: $subject"
        )

        return ParsedEmailTransaction(
            transaction = tx,
            senderOrMerchant = rawMerchant,
            isCredit = isCredit,
            isUpi = upiRef != null,
            isConflict = false
        )
    }

    fun removeBoilerplateAndFooter(body: String): String {
        val cutoffRegex = Regex("""(?i)\b(Call us at:|To block UPI:|Feel free to connect|Footerimg|\*\*\*\*This is a system generated|Reach us at:|Please do not share your Internet Banking|RBI never deals|This email is confidential|If this transaction was not initiated by you|In case you did not initiate|Make UPI spends rewarding|Terms & Conditions apply|Copyright Axis Bank)""")
        val match = cutoffRegex.find(body)
        return if (match != null) body.substring(0, match.range.first) else body
    }

    private fun parseDateTimeString(raw: String?): Long? {
        if (raw == null) return null
        val clean = raw.replace("IST", "").trim()
        val formats = listOf(
            SimpleDateFormat("dd-MM-yy, HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy, HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yy HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                val d = fmt.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    fun stripHtmlAndNormalize(input: String): String {
        val decoded = decodeQuotedPrintable(input)
        return decoded
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)</td>"), " ")
            .replace(Regex("(?i)</tr>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * Decodes MIME encoded-word syntax e.g. =?UTF-8?B?...?= or =?UTF-8?Q?...?=
     */
    fun decodeMimeHeader(raw: String): String {
        if (!raw.contains("=?")) return raw
        return try {
            val pattern = Pattern.compile("=\\?([^?]+)\\?([BQbq])\\?([^?]+)\\?=")
            val matcher = pattern.matcher(raw)
            val sb = StringBuffer()
            while (matcher.find()) {
                val charset = matcher.group(1) ?: "UTF-8"
                val encoding = matcher.group(2)?.uppercase() ?: "B"
                val encodedText = matcher.group(3) ?: ""
                val decoded = try {
                    if (encoding == "B") {
                        val bytes = java.util.Base64.getDecoder().decode(encodedText.trim())
                        String(bytes, java.nio.charset.Charset.forName(charset))
                    } else {
                        decodeQuotedPrintable(encodedText.replace('_', ' '))
                    }
                } catch (_: Exception) {
                    matcher.group(0)
                }
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(decoded))
            }
            matcher.appendTail(sb)
            sb.toString()
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Decodes standard Quoted-Printable bytes into UTF-8 text
     */
    fun decodeQuotedPrintable(input: String): String {
        if (!input.contains("=") && !input.contains("&")) return input
        return try {
            val bytes = input.toByteArray(Charsets.ISO_8859_1)
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < bytes.size) {
                val b = bytes[i]
                if (b == '='.code.toByte()) {
                    if (i + 1 < bytes.size && (bytes[i + 1] == '\r'.code.toByte() || bytes[i + 1] == '\n'.code.toByte())) {
                        // Soft line break: =\r\n or =\n
                        i++
                        if (i < bytes.size && bytes[i] == '\r'.code.toByte() && i + 1 < bytes.size && bytes[i + 1] == '\n'.code.toByte()) {
                            i++
                        }
                    } else if (i + 2 < bytes.size) {
                        val hex1 = bytes[i + 1].toInt().toChar()
                        val hex2 = bytes[i + 2].toInt().toChar()
                        val hexVal = "${hex1}${hex2}".toIntOrNull(16)
                        if (hexVal != null) {
                            out.write(hexVal)
                            i += 2
                        } else {
                            out.write(b.toInt())
                        }
                    } else {
                        out.write(b.toInt())
                    }
                } else {
                    out.write(b.toInt())
                }
                i++
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            input
        }
    }
}
