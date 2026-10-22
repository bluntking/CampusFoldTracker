package com.campusfold.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.util.NotificationHelper
import com.campusfold.tracker.util.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for incoming SMS notifications.
 * Registered with high priority (999) in AndroidManifest.xml.
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

        // Group multipart SMS chunks by sender and accumulate bodies
        val sender = messages.firstOrNull()?.originatingAddress ?: ""
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val bodyBuilder = StringBuilder()

        for (msg in messages) {
            bodyBuilder.append(msg.messageBody ?: "")
        }

        val fullBody = bodyBuilder.toString()
        if (fullBody.isBlank()) return

        Log.d(TAG, "Received SMS from $sender: $fullBody")

        val transaction = SmsParser.parse(fullBody, sender, timestamp)
        if (transaction != null) {
            Log.d(TAG, "Parsed transaction: ${transaction.amount} INR at ${transaction.merchant}")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.transactionDao().insert(transaction)
                    NotificationHelper.showTransactionNotification(context, transaction)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving received SMS transaction", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
