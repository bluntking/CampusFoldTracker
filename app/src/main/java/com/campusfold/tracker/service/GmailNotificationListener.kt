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
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Gmail-only NotificationListenerService that captures incoming Axis Bank credit alerts.
 * Parses the structured body:
 *   Amount Credited: INR 20.00
 *   Account Number: XX2003
 *   Transaction Info: UPI/P2A/218606473101/KAVYA KOH/KKBK/Sent
 *
 * Implements:
 *   - Case A: Exact alias match -> Auto FIFO settlement
 *   - Case B: Prefix / Substring match (e.g. KAVYA KOH -> Kavya) -> 1-Tap settlement suggestion
 *   - Case C: Truncated preview fallback -> Log credit with sender hidden banner
 */
class GmailNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "GmailNotifListener"
        const val GMAIL_PACKAGE = "com.google.android.gm"

        // Regex patterns for Axis Bank credit notifications
        private val AMOUNT_PATTERN_1 = Pattern.compile("INR\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*was credited", Pattern.CASE_INSENSITIVE)
        private val AMOUNT_PATTERN_2 = Pattern.compile("Amount Credited:\\s*INR\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        private val ACCOUNT_PATTERN = Pattern.compile("Account Number:\\s*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
        private val UPI_INFO_PATTERN = Pattern.compile("UPI/(?:P2A|P2M)/(\\d+)/([^/\\n\\r]+)", Pattern.CASE_INSENSITIVE)

        fun isNotificationAccessGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(packageName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Strictly ignore any non-Gmail notifications
        if (sbn.packageName != GMAIL_PACKAGE) {
            return
        }

        val notification = sbn.notification ?: return
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

        // Verify Axis Bank credit indicators
        val isAxis = combinedContent.contains("Axis Bank", ignoreCase = true) ||
                combinedContent.contains("alerts@axis.bank.in", ignoreCase = true) ||
                combinedContent.contains("XX2003", ignoreCase = true) ||
                combinedContent.contains("2003", ignoreCase = true)

        val isCredit = combinedContent.contains("credited to your A/c", ignoreCase = true) ||
                combinedContent.contains("Amount Credited", ignoreCase = true) ||
                combinedContent.contains("was credited", ignoreCase = true)

        if (!isAxis || !isCredit) {
            return
        }

        Log.d(TAG, "Axis Bank credit email notification detected from Gmail: $combinedContent")
        processAxisEmailNotification(combinedContent, sbn.postTime)
    }

    private fun processAxisEmailNotification(content: String, timestamp: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val prefs = AppPreferences(applicationContext)
                val repository = TransactionRepository(db, prefs, applicationContext)

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
                if (amount == null || amount <= 0.0) {
                    Log.w(TAG, "Could not extract valid credit amount from Gmail notification")
                    return@launch
                }

                // 2. Extract Account Number
                var accountLast4 = "2003"
                val mAcc = ACCOUNT_PATTERN.matcher(content)
                if (mAcc.find()) {
                    accountLast4 = mAcc.group(1)?.takeLast(4) ?: "2003"
                }
                val paymentMode = "Axis Bank XX$accountLast4"

                // 3. Extract UPI Info & Sender Name
                var upiRef: String? = null
                var rawSender: String? = null

                val mUpi = UPI_INFO_PATTERN.matcher(content)
                if (mUpi.find()) {
                    upiRef = mUpi.group(1)?.trim()
                    rawSender = mUpi.group(2)?.trim()
                }

                // Deduplication
                if (upiRef != null) {
                    if (repository.isTransactionDuplicate(upiRef)) {
                        Log.d(TAG, "Duplicate Gmail credit notification skipped with upiRef: $upiRef")
                        return@launch
                    }
                } else {
                    // Fallback 3-minute window hash to prevent double counting on notification updates
                    val timeWindow = timestamp / (3 * 60 * 1000)
                    val syntheticRef = "GMAIL_${accountLast4}_${amount.toInt()}_$timeWindow"
                    if (repository.isTransactionDuplicate(syntheticRef)) {
                        Log.d(TAG, "Duplicate Gmail notification skipped with syntheticRef: $syntheticRef")
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
                            val notifMsg = "Auto-settled ₹${result.settledAmount.toInt()} from ${person.name} via Axis Gmail. Remaining owed: ₹${result.remainingOwed.toInt()}"
                            NotificationHelper.showCustomNotification(applicationContext, "Axis Credit Auto-Settled", notifMsg)
                            Log.d(TAG, "Case A satisfied: auto-settled $amount from ${person.name} (txId: $txId)")
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
                                friendlyVendorName = "Axis Credit from $senderClean",
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
                            NotificationHelper.showCustomNotification(applicationContext, "Axis Settlement Suggestion", notifMsg)
                            Log.d(TAG, "Case B satisfied: created suggestion for ${person.name} against $senderClean")
                            return@launch
                        }
                    }
                }

                // Check Case C: Truncated Gmail Preview Fallback (or unknown sender)
                val friendlyName = if (rawSender.isNullOrBlank()) "Axis Bank Credit (Sender hidden)" else "Axis Credit from $rawSender"
                val tx = TransactionEntity(
                    amount = amount,
                    myActualShare = amount,
                    type = TransactionType.INCOME,
                    paymentMode = paymentMode,
                    rawMerchant = rawSender ?: "Axis Gmail Credit",
                    friendlyVendorName = friendlyName,
                    categoryId = null,
                    timestamp = timestamp,
                    upiRef = upiRef,
                    rawSms = content
                )
                repository.addTransaction(tx)

                val notifMsg = if (rawSender.isNullOrBlank()) {
                    "₹${amount.toInt()} credited to Axis Bank (Sender hidden). Tap to apply to a friend on the Splits tab."
                } else {
                    "₹${amount.toInt()} credited to Axis Bank from $rawSender."
                }
                NotificationHelper.showCustomNotification(applicationContext, "Axis Credit Received", notifMsg)
                Log.d(TAG, "Case C satisfied: logged income transaction: $friendlyName")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing Axis email notification", e)
            }
        }
    }
}
