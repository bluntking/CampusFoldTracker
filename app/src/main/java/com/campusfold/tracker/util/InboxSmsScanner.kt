package com.campusfold.tracker.util

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.ingest.IngestResult
import com.campusfold.tracker.data.ingest.MessageIngestor
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.data.resolver.DefaultEntityResolver
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage
import com.campusfold.tracker.worker.SmsScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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
     * Enqueues an asynchronous SMS scan via WorkManager.
     */
    fun enqueueSmsScanWorker(
        context: Context,
        startDateMillis: Long,
        endDateMillis: Long = System.currentTimeMillis()
    ): UUID {
        val workRequest = OneTimeWorkRequestBuilder<SmsScanWorker>()
            .setInputData(
                workDataOf(
                    SmsScanWorker.KEY_START_DATE to startDateMillis,
                    SmsScanWorker.KEY_END_DATE to endDateMillis
                )
            )
            .addTag(SmsScanWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sms_inbox_scan",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workRequest.id
    }

    /**
     * Historical SMS inbox scan using paged ContentResolver reads and MessageIngestor pipeline.
     */
    suspend fun scanInboxFromDate(
        context: Context,
        repository: TransactionRepository,
        startDateMillis: Long,
        endDateMillis: Long = System.currentTimeMillis(),
        maxMessages: Int = 3000,
        pageSize: Int = 100
    ): ScanResult = withContext(Dispatchers.IO) {
        var totalRead = 0
        var importedCount = 0
        var skippedDuplicates = 0
        val highestDetectedLimit: Double? = null

        val db = AppDatabase.getDatabase(context)
        val prefs = AppPreferences(context)
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
        val ingestor = MessageIngestor(db, resolver, repository, context, prefs, aiService)

        val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            startDateMillis.toString(),
            endDateMillis.toString()
        )

        var offset = 0
        try {
            while (totalRead < maxMessages) {
                val sortOrder = "${Telephony.Sms.DATE} ASC LIMIT $pageSize OFFSET $offset"
                val rawBatch = mutableListOf<RawMessage>()

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

                    while (c.moveToNext() && totalRead + rawBatch.size < maxMessages) {
                        val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                        val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                        val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""

                        if (body.isNotBlank()) {
                            rawBatch.add(
                                RawMessage(
                                    source = MessageSource.SMS,
                                    sender = address,
                                    body = body,
                                    timestamp = date
                                )
                            )
                        }
                    }
                }

                if (rawBatch.isEmpty()) {
                    break
                }

                totalRead += rawBatch.size
                offset += rawBatch.size

                val batchResults = ingestor.ingestBatch(rawBatch)
                for (res in batchResults) {
                    when (res) {
                        is IngestResult.Imported -> importedCount++
                        is IngestResult.Enriched,
                        is IngestResult.SkippedAlreadyProcessed -> skippedDuplicates++
                        else -> {}
                    }
                }

                if (rawBatch.size < pageSize) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SMS inbox", e)
        }

        prefs.setLastSmsSyncTimestamp(endDateMillis)
        try {
            repository.runAutoMergePass()
        } catch (_: Exception) {}

        ScanResult(
            totalRead = totalRead,
            importedCount = importedCount,
            skippedDuplicates = skippedDuplicates,
            latestCreditLimit = highestDetectedLimit
        )
    }
}
