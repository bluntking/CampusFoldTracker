package com.campusfold.tracker.worker

import android.content.Context
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
import com.campusfold.tracker.util.GmailImapClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GmailScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "GmailScanWorker"
        const val WORK_NAME = "gmail_sync_work"
        const val KEY_START_DATE = "start_date"

        const val PROGRESS_READ = "progress_read"
        const val PROGRESS_ADDED = "progress_added"
        const val PROGRESS_DUPLICATES = "progress_duplicates"
        const val PROGRESS_STATUS = "progress_status"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appContext = applicationContext
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

        val (email, password) = prefs.getGmailCredentialsSync()
        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            Log.d(TAG, "No Gmail credentials configured. Skipping sync.")
            return@withContext Result.success(workDataOf("info" to "No credentials configured"))
        }

        val startDate = inputData.getLong(KEY_START_DATE, 0L).let {
            if (it > 0L) it else null
        }

        val scanHistoryId = db.scanHistoryDao().insertScanHistory(
            ScanHistoryEntity(
                source = "GMAIL",
                startedAt = System.currentTimeMillis()
            )
        )

        var totalRead = 0
        var totalAdded = 0
        var totalDuplicates = 0
        var totalUnparsed = 0

        try {
            setProgress(
                workDataOf(
                    PROGRESS_READ to 0,
                    PROGRESS_ADDED to 0,
                    PROGRESS_DUPLICATES to 0,
                    PROGRESS_STATUS to "CONNECTING"
                )
            )

            val fetchedEmails = GmailImapClient.fetchBankEmails(
                emailAddress = email,
                appPassword = password,
                appPreferences = prefs,
                startDateMillis = startDate,
                maxEmailsToFetch = 200,
                isCancelled = { isStopped },
                onProgress = { current, total ->
                    setProgress(
                        workDataOf(
                            PROGRESS_READ to current,
                            PROGRESS_ADDED to totalAdded,
                            PROGRESS_DUPLICATES to totalDuplicates,
                            PROGRESS_STATUS to "FETCHING ($current/$total)"
                        )
                    )
                }
            )

            totalRead = fetchedEmails.size

            val rawMessages = fetchedEmails.map { emailItem ->
                RawMessage(
                    source = MessageSource.EMAIL,
                    sender = emailItem.sender,
                    body = emailItem.body,
                    timestamp = emailItem.dateMillis,
                    sourceId = emailItem.uid.toString()
                )
            }

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

            prefs.setGmailLastSyncTimestamp(System.currentTimeMillis())
            try {
                repo.runAutoMergePass()
            } catch (_: Exception) {}

            if (isStopped) {
                Result.failure(
                    workDataOf(
                        PROGRESS_READ to totalRead,
                        PROGRESS_ADDED to totalAdded,
                        PROGRESS_STATUS to "CANCELLED"
                    )
                )
            } else {
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
            Log.e(TAG, "Gmail IMAP scan failed", e)
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
