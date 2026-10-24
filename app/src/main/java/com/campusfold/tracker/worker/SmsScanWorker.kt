package com.campusfold.tracker.worker

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.ScanHistoryEntity
import com.campusfold.tracker.data.ingest.IngestResult
import com.campusfold.tracker.data.ingest.MessageIngestor
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.data.resolver.DefaultEntityResolver
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "SmsScanWorker"
        const val KEY_START_DATE = "start_date"
        const val KEY_END_DATE = "end_date"
        const val KEY_PAGE_SIZE = "page_size"

        const val PROGRESS_READ = "progress_read"
        const val PROGRESS_ADDED = "progress_added"
        const val PROGRESS_DUPLICATES = "progress_duplicates"
        const val PROGRESS_STATUS = "progress_status"

        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appContext = applicationContext

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.READ_SMS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_SMS permission not granted. Aborting scan.")
            return@withContext Result.failure(
                workDataOf(PROGRESS_STATUS to "PERMISSION_DENIED")
            )
        }

        val db = AppDatabase.getDatabase(appContext)
        val prefs = AppPreferences(appContext)
        val repo = TransactionRepository(db, prefs, appContext)
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
        val ingestor = MessageIngestor(db, resolver, repo, appContext, prefs, aiService)

        val startDate = inputData.getLong(KEY_START_DATE, 0L)
        val endDate = inputData.getLong(KEY_END_DATE, System.currentTimeMillis())
        val pageSize = inputData.getInt(KEY_PAGE_SIZE, 100)

        val scanHistoryId = db.scanHistoryDao().insertScanHistory(
            ScanHistoryEntity(
                source = "SMS",
                startedAt = System.currentTimeMillis()
            )
        )

        var totalRead = 0
        var totalAdded = 0
        var totalDuplicates = 0
        var totalUnparsed = 0

        var lastDate = startDate - 1
        var lastId = -1L

        try {
            while (!isStopped) {
                val selection = "${Telephony.Sms.TYPE} = ? AND (${Telephony.Sms.DATE} > ? OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} > ?)) AND ${Telephony.Sms.DATE} <= ?"
                val selectionArgs = arrayOf(
                    Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                    lastDate.toString(),
                    lastDate.toString(),
                    lastId.toString(),
                    endDate.toString()
                )
                val sortOrder = "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"
                val rawMessages = mutableListOf<RawMessage>()
                var rowsFetchedInPage = 0

                appContext.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    SMS_PROJECTION,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)

                    while (cursor.moveToNext() && rowsFetchedInPage < pageSize && !isStopped) {
                        rowsFetchedInPage++
                        val id = if (idIdx != -1) cursor.getLong(idIdx) else -1L
                        val address = if (addressIdx != -1) cursor.getString(addressIdx) ?: "" else ""
                        val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                        val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""

                        lastDate = date
                        lastId = id

                        if (body.isNotBlank()) {
                            rawMessages.add(
                                RawMessage(
                                    source = MessageSource.SMS,
                                    sender = address,
                                    body = body,
                                    timestamp = date,
                                    sourceId = id.toString()
                                )
                            )
                        }
                    }
                }

                if (rowsFetchedInPage == 0) {
                    break // No more messages to read
                }

                totalRead += rawMessages.size

                val ingestResults = ingestor.ingestBatch(rawMessages)
                for (res in ingestResults) {
                    when (res) {
                        is IngestResult.Imported -> totalAdded++
                        is IngestResult.Enriched,
                        is IngestResult.SkippedAlreadyProcessed -> totalDuplicates++
                        is IngestResult.Unparsed -> totalUnparsed++
                        is IngestResult.Ignored,
                        is IngestResult.BalanceUpdated -> {}
                    }
                }

                setProgress(
                    workDataOf(
                        PROGRESS_READ to totalRead,
                        PROGRESS_ADDED to totalAdded,
                        PROGRESS_DUPLICATES to totalDuplicates,
                        PROGRESS_STATUS to "SCANNING"
                    )
                )

                if (rowsFetchedInPage < pageSize) {
                    break
                }
            }

            // Update scan history record
            val scanRecord = db.scanHistoryDao().getScanHistoryById(scanHistoryId)
            if (scanRecord != null) {
                db.scanHistoryDao().updateScanHistory(
                    scanRecord.copy(
                        finishedAt = System.currentTimeMillis(),
                        read = totalRead,
                        added = totalAdded,
                        duplicatesSkipped = totalDuplicates,
                        sentToReview = totalUnparsed
                    )
                )
            }

            if (isStopped) {
                Result.failure(
                    workDataOf(
                        PROGRESS_READ to totalRead,
                        PROGRESS_ADDED to totalAdded,
                        PROGRESS_STATUS to "CANCELLED"
                    )
                )
            } else {
                prefs.setLastSmsSyncTimestamp(endDate)
                try {
                    repo.runAutoMergePass()
                } catch (_: Exception) {}
                Result.success(
                    workDataOf(
                        PROGRESS_READ to totalRead,
                        PROGRESS_ADDED to totalAdded,
                        PROGRESS_DUPLICATES to totalDuplicates,
                        PROGRESS_STATUS to "COMPLETED"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS Scan failed", e)
            val scanRecord = db.scanHistoryDao().getScanHistoryById(scanHistoryId)
            if (scanRecord != null) {
                db.scanHistoryDao().updateScanHistory(
                    scanRecord.copy(
                        finishedAt = System.currentTimeMillis(),
                        read = totalRead,
                        added = totalAdded,
                        duplicatesSkipped = totalDuplicates,
                        sentToReview = totalUnparsed
                    )
                )
            }
            Result.failure(workDataOf("error" to (e.localizedMessage ?: "Unknown error")))
        }
    }
}
