package com.campusfold.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.PendingSettlementSuggestionEntity
import com.campusfold.tracker.data.entity.SettlementSuggestionStatus
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.util.CampusSmsParser
import com.campusfold.tracker.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * High-priority SMS BroadcastReceiver for incoming transaction messages.
 * Uses CampusSmsParser to parse PNB CC 8598, Axis Bank XX2003, and general UPI alerts.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val sender = messages.firstOrNull()?.originatingAddress ?: ""
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val bodyBuilder = StringBuilder()

        for (msg in messages) {
            bodyBuilder.append(msg.messageBody ?: "")
        }

        val fullBody = bodyBuilder.toString()
        if (fullBody.isBlank()) return

        Log.d(TAG, "Incoming SMS from $sender: $fullBody")

        val parsedResult = CampusSmsParser.parse(fullBody, sender, timestamp) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val prefs = AppPreferences(context)
                val repository = TransactionRepository(db, prefs)

                // Update credit limit if mentioned in PNB SMS
                if (parsedResult.extractedCreditLimit != null) {
                    repository.setPnbCreditLimit(parsedResult.extractedCreditLimit)
                }

                val rawTx = parsedResult.transaction

                // Deduplication check
                if (rawTx.upiRef != null && repository.isTransactionDuplicate(rawTx.upiRef)) {
                    Log.d(TAG, "Skipping duplicate transaction with upiRef: ${rawTx.upiRef}")
                    return@launch
                }

                // Resolve campus friendly vendor and default category
                val (friendlyName, categoryId) = repository.resolveVendor(rawTx.rawMerchant)
                val resolvedTx = rawTx.copy(
                    friendlyVendorName = friendlyName,
                    categoryId = categoryId
                )

                val txId = repository.addTransaction(resolvedTx)

                // Route ONLY genuine INCOME to friend split settlement suggestions
                // (PNB CC Bill confirmations are CC_BILL_TRANSFER and are NEVER routed here)
                if (resolvedTx.type == TransactionType.INCOME && txId > 0) {
                    val persons = repository.getAllPersonsList()
                    val lowerBody = fullBody.lowercase()
                    for (person in persons) {
                        val aliases = person.exactSmsAliases.split(",")
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }
                        val matchesAlias = aliases.any { alias -> lowerBody.contains(alias) }
                        val matchesName = lowerBody.contains(person.name.lowercase())

                        if (matchesAlias || matchesName) {
                            repository.createSettlementSuggestion(
                                PendingSettlementSuggestionEntity(
                                    transactionId = txId,
                                    suggestedPersonId = person.id,
                                    detectedSenderName = rawTx.rawMerchant,
                                    amount = rawTx.amount,
                                    status = SettlementSuggestionStatus.PENDING
                                )
                            )
                            break
                        }
                    }
                }

                NotificationHelper.showTransactionNotification(context, resolvedTx)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
