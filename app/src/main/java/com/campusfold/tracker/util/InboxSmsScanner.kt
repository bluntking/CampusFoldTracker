package com.campusfold.tracker.util

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.PendingSettlementSuggestionEntity
import com.campusfold.tracker.data.entity.SettlementSuggestionStatus
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(
    val totalRead: Int,
    val importedCount: Int,
    val skippedDuplicates: Int,
    val latestCreditLimit: Double?
)

object InboxSmsScanner {

    private const val TAG = "InboxSmsScanner"

    private val SMS_PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.DATE,
        Telephony.Sms.BODY,
        Telephony.Sms.TYPE
    )

    /**
     * Scans Telephony.Sms.Inbox for messages over the given time window (e.g. 30 or 90 days),
     * parses transactions with CampusSmsParser, deduplicates against existing records,
     * updates PNB credit limits in DataStore, and creates 1-tap settlement suggestions.
     */
    suspend fun scanInbox(
        context: Context,
        repository: TransactionRepository,
        daysBack: Int = 90,
        maxMessages: Int = 2000
    ): ScanResult = withContext(Dispatchers.IO) {
        var totalRead = 0
        var importedCount = 0
        var skippedDuplicates = 0
        var highestDetectedLimit: Double? = null

        val startTime = System.currentTimeMillis() - (daysBack.toLong() * 24L * 60L * 60L * 1000L)
        val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            startTime.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} ASC" // Process chronologically

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)

                while (c.moveToNext() && totalRead < maxMessages) {
                    totalRead++
                    val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""

                    if (body.isBlank()) continue

                    val parsedResult = CampusSmsParser.parse(body, address, date) ?: continue

                    // Update credit limit if extracted from PNB SMS
                    if (parsedResult.extractedCreditLimit != null) {
                        highestDetectedLimit = parsedResult.extractedCreditLimit
                        repository.setPnbCreditLimit(parsedResult.extractedCreditLimit)
                    }

                    val rawTx = parsedResult.transaction

                    // Deduplication check via upiRef / dedupeHash
                    if (rawTx.upiRef != null && repository.isTransactionDuplicate(rawTx.upiRef)) {
                        skippedDuplicates++
                        continue
                    }

                    // Resolve campus friendly vendor name and default category
                    val (friendlyName, categoryId) = repository.resolveVendor(rawTx.rawMerchant)
                    val resolvedTx = rawTx.copy(
                        friendlyVendorName = friendlyName,
                        categoryId = categoryId
                    )

                    val insertedId = repository.addTransaction(resolvedTx)
                    if (insertedId > 0) {
                        importedCount++

                        // Route only genuine INCOME (never CC_BILL_TRANSFER) to friend split suggestions
                        if (resolvedTx.type == TransactionType.INCOME) {
                            checkFriendSplitSuggestion(repository, insertedId, resolvedTx, body)
                        }
                    } else {
                        skippedDuplicates++
                    }
                }
            }

            repository.setLastSmsSyncTimestamp(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Error while scanning SMS inbox cursor", e)
        }

        ScanResult(
            totalRead = totalRead,
            importedCount = importedCount,
            skippedDuplicates = skippedDuplicates,
            latestCreditLimit = highestDetectedLimit
        )
    }

    private suspend fun checkFriendSplitSuggestion(
        repository: TransactionRepository,
        transactionId: Long,
        transaction: TransactionEntity,
        rawBody: String
    ) {
        val persons = repository.getAllPersonsList()
        val lowerBody = rawBody.lowercase()

        for (person in persons) {
            val aliases = person.exactSmsAliases.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            val matchesAlias = aliases.any { alias -> lowerBody.contains(alias) }
            val matchesName = lowerBody.contains(person.name.lowercase())

            if (matchesAlias || matchesName) {
                repository.createSettlementSuggestion(
                    PendingSettlementSuggestionEntity(
                        transactionId = transactionId,
                        suggestedPersonId = person.id,
                        detectedSenderName = transaction.rawMerchant,
                        amount = transaction.amount,
                        status = SettlementSuggestionStatus.PENDING
                    )
                )
                break
            }
        }
    }
}
