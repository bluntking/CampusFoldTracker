package com.campusfold.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.ingest.IngestResult
import com.campusfold.tracker.data.ingest.MessageIngestor
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.data.resolver.DefaultEntityResolver
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage
import com.campusfold.tracker.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * High-priority SMS BroadcastReceiver for incoming transaction messages.
 * Routes directly through MessageIngestor pipeline.
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

        Log.d(TAG, "Incoming SMS received from sender: $sender (length: ${fullBody.length})")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CampusFold:SmsReceiverWakeLock")
        try {
            wakeLock?.acquire(15_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock", e)
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(10_000L) {
                    val db = AppDatabase.getDatabase(appContext)
                    val prefs = AppPreferences(appContext)
                    val repository = TransactionRepository(db, prefs, appContext)
                    val aiService = com.campusfold.tracker.ai.AiFallbackService(db.aiCacheDao(), prefs)
                    val resolver = DefaultEntityResolver(
                        accountDao = db.accountDao(),
                        accountAliasDao = db.accountAliasDao(),
                        vendorDao = db.vendorDao(),
                        categoryDao = db.categoryDao(),
                        ruleDao = db.ruleDao(),
                        reviewItemDao = db.reviewItemDao(),
                        aiFallbackService = aiService,
                        appPreferences = prefs
                    )
                    val ingestor = MessageIngestor(db, resolver, repository, appContext, prefs, aiService)

                    val rawMessage = RawMessage(
                        source = MessageSource.SMS,
                        sender = sender,
                        body = fullBody,
                        timestamp = timestamp
                    )

                    val result = ingestor.ingest(rawMessage)
                    Log.d(TAG, "SmsReceiver ingest result: $result")

                    when (result) {
                        is IngestResult.Imported, is IngestResult.Enriched -> {
                            val txId = if (result is IngestResult.Imported) result.transactionId else (result as IngestResult.Enriched).transactionId
                            val tx = db.transactionDao().getTransactionByIdSync(txId)
                            if (tx != null && tx.type != TransactionType.INCOME) {
                                val categoryName = tx.categoryId?.let { catId ->
                                    try { db.categoryDao().getCategoryById(catId)?.name } catch (_: Exception) { null }
                                } ?: "Uncategorized"
                                NotificationHelper.showTransactionNotification(appContext, tx, categoryName)
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SMS", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
