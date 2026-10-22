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
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.util.CampusSmsParser
import com.campusfold.tracker.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val prefs = AppPreferences(appContext)
                val repository = TransactionRepository(db, prefs, appContext)

                val accounts = db.accountDao().getAllAccountsSync()
                val parsedResult = CampusSmsParser.parse(fullBody, sender, timestamp, accounts)

                if (parsedResult == null) {
                    // Optional AI Unstructured Message Fallback
                    try {
                        val aiTx = com.campusfold.tracker.ai.LocalAiManager.parseUnstructuredMessage(fullBody)
                        if (aiTx != null) {
                            val txType = try {
                                TransactionType.valueOf(aiTx.type)
                            } catch (e: Exception) {
                                TransactionType.EXPENSE
                            }
                            val (friendly, catId) = repository.resolveVendor(aiTx.merchant)
                            val resolvedMode = CampusSmsParser.resolvePaymentMode(fullBody, sender, accounts)
                            val tx = TransactionEntity(
                                amount = aiTx.amount,
                                myActualShare = aiTx.amount,
                                type = txType,
                                paymentMode = resolvedMode,
                                rawMerchant = aiTx.merchant,
                                friendlyVendorName = friendly,
                                categoryId = catId,
                                timestamp = timestamp,
                                upiRef = null,
                                rawSms = fullBody
                            )
                            repository.addTransaction(tx)
                            NotificationHelper.showCustomNotification(
                                appContext,
                                "AI Parsed Transaction",
                                "Logged ₹${aiTx.amount.toInt()} at $friendly via Local AI"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AI SMS fallback failed", e)
                    }
                    return@launch
                }

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

                // Route ONLY genuine INCOME to friend split settlement engine
                // (PNB CC Bill confirmations are CC_BILL_TRANSFER and are NEVER routed here)
                if (resolvedTx.type == TransactionType.INCOME && txId > 0) {
                    val persons = repository.getAllPersonsList()
                    val senderNameOrVpa = rawTx.rawMerchant.trim()
                    var handledExact = false

                    // Check friends who currently have an unsettled balance > 0
                    for (person in persons) {
                        val pendingBalance = db.splitItemDao().getNetPendingBalanceForPersonSync(person.id)
                        if (pendingBalance <= 0.0) continue

                        val aliases = person.exactSmsAliases.split(",")
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }

                        // Case A (Exact Match): auto-settle via FIFO & log as SPLIT_SETTLEMENT
                        val isExactMatch = aliases.any { alias -> alias.equals(senderNameOrVpa, ignoreCase = true) }
                        if (isExactMatch) {
                            val result = repository.settleAmountForPerson(person.id, resolvedTx.amount)

                            // Update transaction to SPLIT_SETTLEMENT
                            db.transactionDao().updateTransaction(
                                resolvedTx.copy(
                                    id = txId,
                                    type = TransactionType.SPLIT_SETTLEMENT,
                                    friendlyVendorName = "Settlement from ${person.name}"
                                )
                            )

                            val notifMsg = "Auto-settled ₹${result.settledAmount.toInt()} from ${person.name}. Remaining owed: ₹${result.remainingOwed.toInt()}"
                            NotificationHelper.showCustomNotification(appContext, "Friend Split Auto-Settled", notifMsg)
                            handledExact = true
                            break
                        }
                    }

                    // Case B (Fuzzy/First-Name Match): DO NOT auto-settle yet; create 1-tap suggestion
                    if (!handledExact) {
                        for (person in persons) {
                            val pendingBalance = db.splitItemDao().getNetPendingBalanceForPersonSync(person.id)
                            if (pendingBalance <= 0.0) continue

                            val firstName = person.name.split(" ").firstOrNull()?.trim()?.lowercase() ?: ""
                            val lowerSender = senderNameOrVpa.lowercase()
                            val aliases = person.exactSmsAliases.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }

                            val isFuzzy = (firstName.length >= 3 && lowerSender.contains(firstName)) ||
                                    aliases.any { alias -> alias.length >= 3 && (lowerSender.contains(alias) || alias.contains(lowerSender)) } ||
                                    fullBody.lowercase().contains(firstName)

                            if (isFuzzy) {
                                repository.createSettlementSuggestion(
                                    PendingSettlementSuggestionEntity(
                                        transactionId = txId,
                                        suggestedPersonId = person.id,
                                        detectedSenderName = senderNameOrVpa,
                                        amount = resolvedTx.amount,
                                        status = SettlementSuggestionStatus.PENDING
                                    )
                                )
                                val notifMsg = "Received ₹${resolvedTx.amount} from $senderNameOrVpa. Apply to ${person.name}'s ₹${pendingBalance.toInt()} balance?"
                                NotificationHelper.showCustomNotification(appContext, "Settlement Suggestion", notifMsg)
                                break
                            }
                        }
                    }
                }

                if (resolvedTx.type != TransactionType.INCOME) {
                    NotificationHelper.showTransactionNotification(appContext, resolvedTx)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
