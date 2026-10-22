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
import com.campusfold.tracker.util.NotificationHelper
import com.campusfold.tracker.util.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * High-priority SMS BroadcastReceiver for incoming transaction messages.
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

        val parsed = SmsParser.parse(fullBody, sender, timestamp)
        if (parsed != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val prefs = AppPreferences(context)
                    val repository = TransactionRepository(db, prefs)

                    // Resolve friendly vendor & default category from campus vendor mappings
                    val (friendlyName, categoryId) = repository.resolveVendor(parsed.rawMerchant)
                    val resolvedTx = parsed.copy(
                        friendlyVendorName = friendlyName,
                        categoryId = categoryId
                    )

                    val txId = repository.addTransaction(resolvedTx)

                    // If incoming money (CREDIT), check if it matches a friend's aliases for 1-tap settlement
                    if (resolvedTx.type == TransactionType.INCOME) {
                        val persons = db.personDao().getAllPersonsList()
                        val lowerBody = fullBody.lowercase()
                        for (person in persons) {
                            val aliases = person.exactSmsAliases.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                            val isMatch = aliases.any { alias -> lowerBody.contains(alias) } || lowerBody.contains(person.name.lowercase())
                            if (isMatch) {
                                repository.createSettlementSuggestion(
                                    PendingSettlementSuggestionEntity(
                                        transactionId = txId,
                                        suggestedPersonId = person.id,
                                        detectedSenderName = parsed.rawMerchant,
                                        amount = parsed.amount,
                                        status = SettlementSuggestionStatus.PENDING
                                    )
                                )
                                break
                            }
                        }
                    }

                    NotificationHelper.showTransactionNotification(context, resolvedTx)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming SMS transaction", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
