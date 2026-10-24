package com.campusfold.tracker.util

import android.util.Log
import com.campusfold.tracker.data.pref.AppPreferences
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.mail.*
import javax.mail.internet.MimeUtility
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

data class RawAxisEmail(
    val msgId: String,
    val subject: String,
    val from: String,
    val body: String,
    val dateMillis: Long
)

data class FetchedEmail(
    val uid: Long,
    val sender: String,
    val subject: String,
    val body: String,
    val dateMillis: Long
)

class GmailImapException(message: String, cause: Throwable? = null) : Exception(message, cause)

object GmailImapClient {

    private const val TAG = "GmailImapClient"
    private const val IMAP_HOST = "imap.gmail.com"
    private const val IMAP_PORT = 993

    /**
     * Tests IMAP connection and credentials with imap.gmail.com:993.
     */
    suspend fun testConnection(
        emailAddress: String,
        appPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = emailAddress.trim()
        val cleanPassword = appPassword.replace(" ", "").trim()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            return@withContext Result.failure(GmailImapException("Gmail address and App Password cannot be empty."))
        }

        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", IMAP_HOST)
            put("mail.imaps.port", IMAP_PORT.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "15000")
        }

        var store: Store? = null
        var folder: Folder? = null
        try {
            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(IMAP_HOST, IMAP_PORT, cleanEmail, cleanPassword)
            folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val count = folder.messageCount
            Result.success("Connected successfully! ($count messages in INBOX)")
        } catch (e: AuthenticationFailedException) {
            Result.failure(GmailImapException("Authentication failed. Check your Gmail address and 16-character App Password.", e))
        } catch (e: Exception) {
            Result.failure(GmailImapException(e.localizedMessage ?: "Connection failed.", e))
        } finally {
            try { folder?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Connects securely over TLS/SSL to imap.gmail.com:993, authenticates via Google App Password,
     * performs UID-based incremental sync with UIDVALIDITY tracking, matches Indian bank sender domains
     * and subject keywords from BankEmailSyncConfig, and parses quoted-printable, base64 and multipart MIME bodies.
     * Uses FetchProfile for fast prefetching and processes in 100-msg chunks oldest-to-newest.
     */
    suspend fun fetchBankEmailsInChunks(
        emailAddress: String,
        appPassword: String,
        appPreferences: AppPreferences? = null,
        startDateMillis: Long? = null,
        chunkSize: Int = 100,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onChunkReady: suspend (chunk: List<FetchedEmail>, serverUidValidity: Long, chunkHighestUid: Long) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val cleanEmail = emailAddress.trim()
        val cleanPassword = appPassword.replace(" ", "").trim()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            throw GmailImapException("Gmail address and App Password cannot be empty.")
        }

        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", IMAP_HOST)
            put("mail.imaps.port", IMAP_PORT.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "30000")
            put("mail.imaps.timeout", "30000")
        }

        var store: Store? = null
        var folder: IMAPFolder? = null
        var totalFetchedCount = 0

        val customSenders = appPreferences?.getCustomBankSendersSync() ?: emptySet()
        val scanAllSenders = appPreferences?.isScanAllSendersSync() ?: false

        try {
            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            try {
                store.connect(IMAP_HOST, IMAP_PORT, cleanEmail, cleanPassword)
            } catch (e: AuthenticationFailedException) {
                throw GmailImapException("Authentication failed. Please verify your Gmail address and 16-character App Password.", e)
            } catch (e: Exception) {
                throw GmailImapException("Failed connecting to Gmail IMAP: ${e.localizedMessage}", e)
            }

            folder = store.getFolder("INBOX") as? IMAPFolder
                ?: throw GmailImapException("Unable to open INBOX folder on Gmail IMAP.")
            folder.open(Folder.READ_ONLY)

            val serverUidValidity = folder.uidValidity
            val (storedUidValidity, storedLastUid) = if (appPreferences != null) {
                appPreferences.getGmailUidSyncState()
            } else {
                Pair(0L, 0L)
            }

            val effectiveLastUid = if (storedUidValidity != serverUidValidity) {
                Log.d(TAG, "UIDVALIDITY changed ($storedUidValidity -> $serverUidValidity), resetting last UID.")
                0L
            } else {
                storedLastUid
            }

            val messagesToProcess: Array<out Message> = if (effectiveLastUid > 0L) {
                Log.d(TAG, "Incremental sync: fetching messages with UID > $effectiveLastUid")
                try {
                    folder.getMessagesByUID(effectiveLastUid + 1, UIDFolder.LASTUID)
                } catch (e: Exception) {
                    Log.w(TAG, "getMessagesByUID failed, falling back to date query", e)
                    val date = if (startDateMillis != null && startDateMillis > 0) Date(startDateMillis) else Date(System.currentTimeMillis() - 30L * 86400000L)
                    folder.search(ReceivedDateTerm(ComparisonTerm.GE, date))
                }
            } else {
                val filterDate = if (startDateMillis != null && startDateMillis > 0) {
                    Date(startDateMillis)
                } else {
                    Date(System.currentTimeMillis() - 30L * 86400000L)
                }
                Log.d(TAG, "Initial sync: fetching messages since $filterDate")
                folder.search(ReceivedDateTerm(ComparisonTerm.GE, filterDate))
            }

            val totalCandidateCount = messagesToProcess.size
            Log.d(TAG, "Total candidate messages to inspect: $totalCandidateCount")
            if (totalCandidateCount == 0) return@withContext 0

            // Prefetch envelope, flags, and UIDs with FetchProfile
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(UIDFolder.FetchProfileItem.UID)
            }
            folder.fetch(messagesToProcess, fp)

            // Sort oldest to newest (by UID ASC) so we don't skip emails when committing UIDs
            val sortedMessages = messagesToProcess.toList().sortedWith(compareBy({
                try { folder.getUID(it) } catch (_: Exception) { 0L }
            }, {
                it.receivedDate?.time ?: it.sentDate?.time ?: 0L
            }))

            var processedIndex = 0
            val chunks = sortedMessages.chunked(chunkSize)

            for (chunk in chunks) {
                if (isCancelled?.invoke() == true) {
                    Log.d(TAG, "Gmail sync cancelled.")
                    break
                }

                val chunkFetchedEmails = mutableListOf<FetchedEmail>()
                var chunkHighestUid = 0L

                for (msg in chunk) {
                    if (isCancelled?.invoke() == true) break
                    processedIndex++
                    onProgress?.invoke(processedIndex, sortedMessages.size)

                    try {
                        val uid = folder.getUID(msg)
                        if (uid > chunkHighestUid) {
                            chunkHighestUid = uid
                        }

                        val sender = msg.from?.firstOrNull()?.let {
                            try { MimeUtility.decodeText(it.toString()) } catch (_: Exception) { it.toString() }
                        } ?: ""

                        val subject = try {
                            msg.subject?.let { MimeUtility.decodeText(it) } ?: ""
                        } catch (_: Exception) {
                            msg.subject ?: ""
                        }

                        if (!BankEmailSyncConfig.matchesBankEmail(sender, subject, customSenders, scanAllSenders)) {
                            continue
                        }

                        val dateMillis = msg.receivedDate?.time ?: msg.sentDate?.time ?: System.currentTimeMillis()
                        val body = MimeMessageDecoder.extractCleanBodyText(msg)

                        chunkFetchedEmails.add(
                            FetchedEmail(
                                uid = uid,
                                sender = sender,
                                subject = subject,
                                body = body,
                                dateMillis = dateMillis
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing message index $processedIndex", e)
                    }
                }

                if (chunkFetchedEmails.isNotEmpty()) {
                    totalFetchedCount += chunkFetchedEmails.size
                    onChunkReady(chunkFetchedEmails, serverUidValidity, chunkHighestUid)
                } else if (chunkHighestUid > 0L) {
                    appPreferences?.setGmailUidSyncState(serverUidValidity, chunkHighestUid)
                }
            }

        } finally {
            try { folder?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }

        return@withContext totalFetchedCount
    }

    suspend fun fetchBankEmails(
        emailAddress: String,
        appPassword: String,
        appPreferences: AppPreferences? = null,
        startDateMillis: Long? = null,
        maxEmailsToFetch: Int = 200,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<FetchedEmail> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FetchedEmail>()
        fetchBankEmailsInChunks(
            emailAddress = emailAddress,
            appPassword = appPassword,
            appPreferences = appPreferences,
            startDateMillis = startDateMillis,
            chunkSize = 100,
            isCancelled = isCancelled,
            onProgress = onProgress,
            onChunkReady = { chunk, _, _ ->
                result.addAll(chunk)
            }
        )
        return@withContext result.take(maxEmailsToFetch)
    }

    suspend fun fetchAxisBankEmails(
        emailAddress: String,
        appPassword: String,
        startDateMillis: Long? = null,
        maxEmailsToFetch: Int = 150,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<RawAxisEmail> = withContext(Dispatchers.IO) {
        val fetched = fetchBankEmails(
            emailAddress = emailAddress,
            appPassword = appPassword,
            startDateMillis = startDateMillis,
            maxEmailsToFetch = maxEmailsToFetch,
            onProgress = onProgress
        )
        return@withContext fetched.map {
            RawAxisEmail(
                msgId = it.uid.toString(),
                subject = it.subject,
                from = it.sender,
                body = it.body,
                dateMillis = it.dateMillis
            )
        }
    }
}
