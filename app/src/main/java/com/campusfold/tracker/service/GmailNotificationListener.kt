package com.campusfold.tracker.service

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.PendingSettlementSuggestionEntity
import com.campusfold.tracker.data.entity.SettlementSuggestionStatus
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Gmail-only NotificationListenerService that captures incoming bank credit alerts (e.g. Axis Bank, PNB, etc.).
 * Parses structured and semi-structured email bodies:
 *   Amount Credited: INR 20.00
 *   Account Number: XX2003
 *   Transaction Info: UPI/P2A/218606473101/KAVYA KOH/KKBK/Sent
 *
 * Implements:
 *   - Case A: Exact alias match -> Auto FIFO settlement
 *   - Case B: Prefix / Substring match (e.g. KAVYA KOH -> Kavya) -> 1-Tap settlement suggestion
 *   - Case C: Truncated preview fallback -> Log credit with sender hidden banner
 *
 * Robust against:
 *   - Bundled group summaries (FLAG_GROUP_SUMMARY ignored when child notifications are active)
 *   - Reposted/updated notification spam (deduplicated via persistent DataStore hash set + Room upiRef)
 *   - Multi-account dynamic DB matching (matches configured accounts or unknown tail fallback)
 */
class GmailNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "GmailNotifListener"
        const val GMAIL_PACKAGE = "com.google.android.gm"

        // Regex patterns for bank credit notifications
        private val AMOUNT_PATTERN_1 = Pattern.compile("INR\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*was credited", Pattern.CASE_INSENSITIVE)
        private val AMOUNT_PATTERN_2 = Pattern.compile("Amount Credited:\\s*INR\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        private val AMOUNT_PATTERN_3 = Pattern.compile("(?:credited\\s+by|credited\\s+with|credit\\s+of|credited|received)\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        private val AMOUNT_PATTERN_4 = Pattern.compile("(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:has been|is|was)?\\s*credited", Pattern.CASE_INSENSITIVE)

        private val ACCOUNT_PATTERN_1 = Pattern.compile("Account Number:\\s*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
        private val ACCOUNT_PATTERN_2 = Pattern.compile("(?:A/c|Account|acct|Card)(?:\\s+(?:no\\.?|num\\.?|ending))?[\\s:]*(?:X+|\\*+)?(\\d{3,4})", Pattern.CASE_INSENSITIVE)

        private val UPI_INFO_PATTERN_1 = Pattern.compile("UPI/(?:P2A|P2M)/(\\d+)/([^/\\n\\r]+)", Pattern.CASE_INSENSITIVE)
        private val UPI_INFO_PATTERN_2 = Pattern.compile("(?:UPI\\s+Ref(?:\\s+no)?|Ref\\s+no)\\.?\\s*:?\\s*(\\d{9,})", Pattern.CASE_INSENSITIVE)

        fun isNotificationAccessGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(packageName)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Strictly ignore any non-Gmail notifications
        if (sbn.packageName != GMAIL_PACKAGE) {
            return
        }

        val notification = sbn.notification ?: return

        // Ignore summary notifications if individual child notifications exist
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            val hasChildren = try {
                activeNotifications?.any { active ->
                    active.packageName == GMAIL_PACKAGE &&
                    active.key != sbn.key &&
                    active.groupKey == sbn.groupKey &&
                    (active.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                } ?: false
            } catch (e: Exception) {
                false
            }
            if (hasChildren) {
                Log.d(TAG, "Ignoring Gmail group summary notification because child notifications are present: ${sbn.key}")
                return
            }
        }

        val extras = notification.extras ?: return

        // Extract all text fields from notification extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val linesJoined = textLines?.joinToString("\n") { it.toString() } ?: ""

        val combinedContent = "$title\n$text\n$bigText\n$subText\n$linesJoined".trim()
        if (combinedContent.isBlank()) return

        // Check if notification mentions credit
        val isCredit = combinedContent.contains("credited", ignoreCase = true) ||
                combinedContent.contains("Amount Credited", ignoreCase = true) ||
                combinedContent.contains("was credited", ignoreCase = true) ||
                combinedContent.contains("credit alert", ignoreCase = true)

        val isBankOrAccount = combinedContent.contains("Bank", ignoreCase = true) ||
                combinedContent.contains("A/c", ignoreCase = true) ||
                combinedContent.contains("Account", ignoreCase = true) ||
                combinedContent.contains("UPI", ignoreCase = true) ||
                combinedContent.contains("alerts@", ignoreCase = true)

        if (!isCredit || !isBankOrAccount) {
            return
        }

        Log.d(TAG, "Bank credit email notification detected from Gmail: $combinedContent")
        processBankEmailNotification(combinedContent, sbn.postTime)
    }

    private fun processBankEmailNotification(content: String, timestamp: Long) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val prefs = AppPreferences(applicationContext)
                val repository = TransactionRepository(db, prefs, applicationContext)

                // Notification content hash deduplication to eliminate Gmail repost spam
                val contentHash = "GMAIL_${content.hashCode()}"
                if (prefs.isNotificationHashProcessed(contentHash)) {
                    Log.d(TAG, "Duplicate Gmail notification skipped via hash: $contentHash")
                    return@launch
                }

                // 1. Extract Amount
                var amount: Double? = null
                val mAmt1 = AMOUNT_PATTERN_1.matcher(content)
                if (mAmt1.find()) {
                    amount = mAmt1.group(1)?.replace(",", "")?.toDoubleOrNull()
                }
                if (amount == null) {
                    val mAmt2 = AMOUNT_PATTERN_2.matcher(content)
                    if (mAmt2.find()) {
                        amount = mAmt2.group(1)?.replace(",", "")?.toDoubleOrNull()
                    }
                }
                if (amount == null) {
                    val mAmt3 = AMOUNT_PATTERN_3.matcher(content)
                    if (mAmt3.find()) {
                        amount = mAmt3.group(1)?.replace(",", "")?.toDoubleOrNull()
                    }
                }
                if (amount == null) {
                    val mAmt4 = AMOUNT_PATTERN_4.matcher(content)
                    if (mAmt4.find()) {
                        amount = mAmt4.group(1)?.replace(",", "")?.toDoubleOrNull()
                    }
                }

                if (amount == null || amount <= 0.0) {
                    Log.w(TAG, "Could not extract valid credit amount from Gmail notification")
                    return@launch
                }

                // 2. Dynamic Account Resolution from DB
                val knownAccounts = try {
                    db.accountDao().getAllAccountsSync()
                } catch (e: Exception) {
                    emptyList()
                }

                var detectedLast4: String? = null
                val mAcc1 = ACCOUNT_PATTERN_1.matcher(content)
                if (mAcc1.find()) {
                    detectedLast4 = mAcc1.group(1)?.takeLast(4)
                }
                if (detectedLast4 == null) {
                    val mAcc2 = ACCOUNT_PATTERN_2.matcher(content)
                    if (mAcc2.find()) {
                        detectedLast4 = mAcc2.group(1)
                    }
                }

                // Try to match with user-configured accounts
                val matchedAccount = if (detectedLast4 != null) {
                    knownAccounts.find { it.last4.endsWith(detectedLast4) || detectedLast4.endsWith(it.last4) }
                } else {
                    knownAccounts.find { acc ->
                        (acc.bankName.isNotBlank() && content.contains(acc.bankName, ignoreCase = true)) ||
                        (acc.name.isNotBlank() && content.contains(acc.name, ignoreCase = true))
                    }
                }

                val paymentMode = when {
                    matchedAccount != null -> matchedAccount.name
                    detectedLast4 != null -> {
                        val bankGuess = when {
                            content.contains("Axis", ignoreCase = true) -> "Axis Bank"
                            content.contains("PNB", ignoreCase = true) || content.contains("Punjab", ignoreCase = true) -> "PNB"
                            content.contains("HDFC", ignoreCase = true) -> "HDFC Bank"
                            content.contains("SBI", ignoreCase = true) || content.contains("State Bank", ignoreCase = true) -> "SBI"
                            content.contains("ICICI", ignoreCase = true) -> "ICICI Bank"
                            content.contains("Kotak", ignoreCase = true) -> "Kotak Bank"
                            else -> "Bank"
                        }
                        "$bankGuess XX$detectedLast4"
                    }
                    content.contains("Axis", ignoreCase = true) -> "Axis Bank XX2003"
                    else -> "Bank Account"
                }

                val bankLabel = matchedAccount?.bankName ?: (if (paymentMode.contains("Axis")) "Axis Bank" else "Bank")

                // 3. Extract UPI Info & Sender Name
                var upiRef: String? = null
                var rawSender: String? = null

                val mUpi1 = UPI_INFO_PATTERN_1.matcher(content)
                if (mUpi1.find()) {
                    upiRef = mUpi1.group(1)?.trim()
                    rawSender = mUpi1.group(2)?.trim()
                } else {
                    val mUpi2 = UPI_INFO_PATTERN_2.matcher(content)
                    if (mUpi2.find()) {
                        upiRef = mUpi2.group(1)?.trim()
                    }
                }

                // Strict Deduplication via UPI Ref + Hash
                if (upiRef != null) {
                    if (repository.isTransactionDuplicate(upiRef) || prefs.isNotificationHashProcessed("UPI_$upiRef")) {
                        Log.d(TAG, "Duplicate Gmail credit notification skipped with upiRef: $upiRef")
                        prefs.markNotificationHashProcessed(contentHash)
                        return@launch
                    }
                } else {
                    // Fallback 3-minute window hash to prevent double counting on notification updates
                    val timeWindow = timestamp / (3 * 60 * 1000)
                    val syntheticRef = "GMAIL_${detectedLast4 ?: "ACC"}_${amount.toInt()}_$timeWindow"
                    if (repository.isTransactionDuplicate(syntheticRef) || prefs.isNotificationHashProcessed(syntheticRef)) {
                        Log.d(TAG, "Duplicate Gmail notification skipped with syntheticRef: $syntheticRef")
                        prefs.markNotificationHashProcessed(contentHash)
                        return@launch
                    }
                    upiRef = syntheticRef
                }

                val allPersons = repository.getAllPersonsList()

                // Check Case A: Exact Alias Match -> Auto FIFO settlement
                if (!rawSender.isNullOrBlank()) {
                    val senderClean = rawSender.trim()

                    for (person in allPersons) {
                        val pending = db.splitItemDao().getNetPendingBalanceForPersonSync(person.id)
                        if (pending <= 0.0) continue

                        val aliases = person.exactSmsAliases.split(",")
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }

                        val isExactMatch = aliases.any { alias -> alias.equals(senderClean, ignoreCase = true) }
                        if (isExactMatch) {
                            // Insert Transaction as SPLIT_SETTLEMENT
                            val tx = TransactionEntity(
                                amount = amount,
                                myActualShare = 0.0,
                                type = TransactionType.SPLIT_SETTLEMENT,
                                paymentMode = paymentMode,
                                rawMerchant = senderClean,
                                friendlyVendorName = "Settlement from ${person.name}",
                                categoryId = null,
                                timestamp = timestamp,
                                upiRef = upiRef,
                                rawSms = content
                            )
                            val txId = repository.addTransaction(tx)

                            val result = repository.settleAmountForPerson(person.id, amount)
                            val notifMsg = "Auto-settled ₹${result.settledAmount.toInt()} from ${person.name} via $bankLabel Gmail. Remaining owed: ₹${result.remainingOwed.toInt()}"
                            NotificationHelper.showCustomNotification(applicationContext, "$bankLabel Credit Auto-Settled", notifMsg)
                            Log.d(TAG, "Case A satisfied: auto-settled $amount from ${person.name} (txId: $txId)")

                            prefs.markNotificationHashProcessed(contentHash)
                            prefs.markNotificationHashProcessed("UPI_$upiRef")
                            return@launch
                        }
                    }

                    // Check Case B: Prefix / Substring Match (e.g., KAVYA KOH -> Kavya)
                    for (person in allPersons) {
                        val pending = db.splitItemDao().getNetPendingBalanceForPersonSync(person.id)
                        if (pending <= 0.0) continue

                        val firstName = person.name.split(" ").firstOrNull()?.trim()?.lowercase() ?: ""
                        val cleanLower = senderClean.lowercase()

                        val isMatch = (firstName.length >= 3 && (cleanLower.startsWith(firstName) || cleanLower.contains(firstName))) ||
                                person.exactSmsAliases.split(",").map { it.trim().lowercase() }.any { alias ->
                                    alias.length >= 3 && (cleanLower.contains(alias) || alias.contains(cleanLower))
                                }

                        if (isMatch) {
                            // Insert Transaction as INCOME
                            val tx = TransactionEntity(
                                amount = amount,
                                myActualShare = amount,
                                type = TransactionType.INCOME,
                                paymentMode = paymentMode,
                                rawMerchant = senderClean,
                                friendlyVendorName = "$bankLabel Credit from $senderClean",
                                categoryId = null,
                                timestamp = timestamp,
                                upiRef = upiRef,
                                rawSms = content
                            )
                            val txId = repository.addTransaction(tx)

                            repository.createSettlementSuggestion(
                                PendingSettlementSuggestionEntity(
                                    transactionId = txId,
                                    suggestedPersonId = person.id,
                                    detectedSenderName = senderClean,
                                    amount = amount,
                                    status = SettlementSuggestionStatus.PENDING
                                )
                            )

                            val notifMsg = "Received ₹${amount.toInt()} from $senderClean. Settle against ${person.name}'s ₹${pending.toInt()} balance & save alias?"
                            NotificationHelper.showCustomNotification(applicationContext, "$bankLabel Settlement Suggestion", notifMsg)
                            Log.d(TAG, "Case B satisfied: created suggestion for ${person.name} against $senderClean")

                            prefs.markNotificationHashProcessed(contentHash)
                            prefs.markNotificationHashProcessed("UPI_$upiRef")
                            return@launch
                        }
                    }
                }

                // Check Case C: Truncated Gmail Preview Fallback (or unknown sender)
                val friendlyName = if (rawSender.isNullOrBlank()) "$bankLabel Credit (Sender hidden)" else "$bankLabel Credit from $rawSender"
                val tx = TransactionEntity(
                    amount = amount,
                    myActualShare = amount,
                    type = TransactionType.INCOME,
                    paymentMode = paymentMode,
                    rawMerchant = rawSender ?: "$bankLabel Gmail Credit",
                    friendlyVendorName = friendlyName,
                    categoryId = null,
                    timestamp = timestamp,
                    upiRef = upiRef,
                    rawSms = content
                )
                repository.addTransaction(tx)

                val notifMsg = if (rawSender.isNullOrBlank()) {
                    "₹${amount.toInt()} credited to $paymentMode (Sender hidden). Tap to apply to a friend on the Splits tab."
                } else {
                    "₹${amount.toInt()} credited to $paymentMode from $rawSender."
                }
                NotificationHelper.showCustomNotification(applicationContext, "$bankLabel Credit Received", notifMsg)
                Log.d(TAG, "Case C satisfied: logged income transaction: $friendlyName")

                prefs.markNotificationHashProcessed(contentHash)
                prefs.markNotificationHashProcessed("UPI_$upiRef")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing bank email notification", e)
            }
        }
    }
}
