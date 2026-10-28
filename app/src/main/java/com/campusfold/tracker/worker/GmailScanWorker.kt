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
        const val KEY_SOURCE = "key_source"

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
        val aiService = com.campusfold.tracker.ai.AiFallbackService(db.aiCacheDao(), prefs).takeIf { it.isAiAvailable() }
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

        val syncSource = inputData.getString(KEY_SOURCE) ?: "BACKGROUND"
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
        var totalFilteredOut = 0
        var totalParsed = 0

        try {
            setProgress(
                workDataOf(
                    PROGRESS_READ to 0,
                    PROGRESS_ADDED to 0,
                    PROGRESS_DUPLICATES to 0,
                    PROGRESS_STATUS to "Connecting..."
                )
            )

            GmailImapClient.fetchBankEmailsInChunks(
                emailAddress = email,
                appPassword = password,
                appPreferences = prefs,
                startDateMillis = startDate,
                chunkSize = 100,
                isCancelled = { isStopped },
                onProgress = { current, total ->
                    setProgressAsync(
                        workDataOf(
                            PROGRESS_READ to current,
                            PROGRESS_ADDED to totalAdded,
                            PROGRESS_DUPLICATES to totalDuplicates,
                            PROGRESS_STATUS to "Downloading $current of $total..."
                        )
                    )
                },
                onChunkReady = { chunkEmails, serverUidValidity, chunkHighestUid, chunkFilteredCount, isInbox ->
                    totalFilteredOut += chunkFilteredCount
                    totalRead += chunkEmails.size

                    setProgressAsync(
                        workDataOf(
                            PROGRESS_READ to totalRead,
                            PROGRESS_ADDED to totalAdded,
                            PROGRESS_DUPLICATES to totalDuplicates,
                            PROGRESS_STATUS to "Saving ${chunkEmails.size} emails..."
                        )
                    )

                    var lastHandledUid = 0L

                    for (emailItem in chunkEmails) {
                        val rawMessage = RawMessage(
                            source = MessageSource.EMAIL,
                            sender = emailItem.sender,
                            body = emailItem.body,
                            timestamp = emailItem.dateMillis,
                            sourceId = emailItem.uid.toString(),
                            subject = emailItem.subject
                        )

                        try {
                            val res = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                                ingestor.ingest(rawMessage)
                            } ?: IngestResult.Unparsed(0L, "")

                            when (res) {
                                is IngestResult.Imported -> {
                                    totalAdded++
                                    totalParsed++
                                }
                                is IngestResult.Enriched -> {
                                    totalDuplicates++
                                    totalParsed++
                                }
                                is IngestResult.SkippedAlreadyProcessed -> {
                                    totalDuplicates++
                                }
                                is IngestResult.Unparsed -> {
                                    totalUnparsed++
                                }
                                is IngestResult.BalanceUpdated -> {
                                    totalParsed++
                                }
                                is IngestResult.Ignored -> {}
                            }
                            lastHandledUid = maxOf(lastHandledUid, emailItem.uid)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed ingesting email UID ${emailItem.uid}", e)
                            try {
                                val payload = org.json.JSONObject().apply {
                                    put("rawText", emailItem.body)
                                    put("sender", emailItem.sender)
                                    put("subject", emailItem.subject)
                                    put("timestamp", emailItem.dateMillis)
                                    put("error", e.localizedMessage ?: "Ingestion error")
                                }.toString()
                                db.reviewItemDao().insertReviewItem(
                                    com.campusfold.tracker.data.entity.ReviewItemEntity(
                                        payloadJson = payload,
                                        reason = "Gmail ingest error: ${e.localizedMessage ?: "Unknown error"}",
                                        confidence = 0.0f,
                                        origin = com.campusfold.tracker.data.entity.ReviewOrigin.MANUAL,
                                        status = com.campusfold.tracker.data.entity.ReviewStatus.PENDING
                                    )
                                )
                                totalUnparsed++
                                lastHandledUid = maxOf(lastHandledUid, emailItem.uid)
                            } catch (_: Exception) {
                                // If even review item insertion failed, do not advance UID past this email!
                                break
                            }
                        }
                    }

                    // Advance UID checkpoint ONLY for emails that were safely handled on INBOX
                    if (lastHandledUid > 0L && isInbox) {
                        val (_, storedLastUid) = prefs.getGmailUidSyncState()
                        prefs.setGmailUidSyncState(serverUidValidity, maxOf(storedLastUid, lastHandledUid))
                    }
                }
            )

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

            val now = System.currentTimeMillis()
            val finalStats = com.campusfold.tracker.data.model.GmailSyncStats(
                timestamp = now,
                emailsFetched = totalRead,
                filteredOut = totalFilteredOut,
                parsed = totalParsed,
                sentToReview = totalUnparsed,
                saved = totalAdded,
                lastError = null,
                source = syncSource,
                lastAttemptResult = "COMPLETED",
                lastSuccessTimestamp = now
            )
            prefs.setGmailSyncStats(finalStats)

            // Always update lastSyncTimestamp on successful sync completion even if 0 new emails found
            prefs.setGmailLastSyncTimestamp(now)

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

            val plainError = when {
                e is com.campusfold.tracker.util.GmailImapException && e.cause is javax.mail.AuthenticationFailedException ->
                    "Gmail authentication failed. Check your email & 16-character App Password."
                e is com.campusfold.tracker.util.GmailImapException && (e.message?.contains("Couldn't connect", ignoreCase = true) == true || e.message?.contains("timeout", ignoreCase = true) == true) ->
                    "Can't reach Gmail (connection timeout). Check your internet connection."
                e.message?.contains("Authentication failed", ignoreCase = true) == true ->
                    "Gmail authentication failed. Please verify your App Password."
                e.message?.contains("timeout", ignoreCase = true) == true || e.message?.contains("host", ignoreCase = true) == true ->
                    "Can't reach Gmail (connection timeout). Check your network."
                else -> e.localizedMessage ?: "Gmail sync failed"
            }

            val now = System.currentTimeMillis()
            val errStats = com.campusfold.tracker.data.model.GmailSyncStats(
                timestamp = now,
                emailsFetched = totalRead,
                filteredOut = totalFilteredOut,
                parsed = totalParsed,
                sentToReview = totalUnparsed,
                saved = totalAdded,
                lastError = plainError,
                source = syncSource,
                lastAttemptResult = "FAILED: $plainError"
            )
            prefs.setGmailSyncStats(errStats)

            val isAuthError = (e is com.campusfold.tracker.util.GmailImapException && e.cause is javax.mail.AuthenticationFailedException) ||
                    e.message?.contains("Authentication failed", ignoreCase = true) == true

            if (!isAuthError && runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to plainError))
            }
        }
    }
}
