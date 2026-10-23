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
     * Connects securely over TLS/SSL to imap.gmail.com:993, authenticates via Google App Password,
     * performs UID-based incremental sync with UIDVALIDITY tracking, matches Indian bank sender domains
     * and subject keywords from BankEmailSyncConfig, and parses quoted-printable, base64 and multipart MIME bodies.
     */
    suspend fun fetchBankEmails(
        emailAddress: String,
        appPassword: String,
        appPreferences: AppPreferences? = null,
        startDateMillis: Long? = null,
        maxEmailsToFetch: Int = 200,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<FetchedEmail> = withContext(Dispatchers.IO) {
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
            put("mail.imaps.ssl.checkserveridentity", "true") // Enforce TLS certificate check
            put("mail.imaps.connectiontimeout", "30000")
            put("mail.imaps.timeout", "30000")
        }

        var store: Store? = null
        var folder: IMAPFolder? = null
        val fetchedList = mutableListOf<FetchedEmail>()

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

            // UIDVALIDITY state check & reset
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

            // Take up to maxEmailsToFetch (most recent first)
            val sortedMessages = messagesToProcess.toList().reversed().take(maxEmailsToFetch)
            var highestProcessedUid = effectiveLastUid

            for ((index, msg) in sortedMessages.withIndex()) {
                if (isCancelled?.invoke() == true) {
                    Log.d(TAG, "Gmail sync cancelled.")
                    break
                }
                onProgress?.invoke(index + 1, sortedMessages.size)

                try {
                    val uid = folder.getUID(msg)
                    if (uid > highestProcessedUid) {
                        highestProcessedUid = uid
                    }

                    val sender = msg.from?.firstOrNull()?.let {
                        try { MimeUtility.decodeText(it.toString()) } catch (_: Exception) { it.toString() }
                    } ?: ""

                    val subject = try {
                        msg.subject?.let { MimeUtility.decodeText(it) } ?: ""
                    } catch (_: Exception) {
                        msg.subject ?: ""
                    }

                    if (!BankEmailSyncConfig.matchesBankEmail(sender, subject)) {
                        continue
                    }

                    val dateMillis = msg.receivedDate?.time ?: msg.sentDate?.time ?: System.currentTimeMillis()
                    val body = MimeMessageDecoder.extractCleanBodyText(msg)

                    fetchedList.add(
                        FetchedEmail(
                            uid = uid,
                            sender = sender,
                            subject = subject,
                            body = body,
                            dateMillis = dateMillis
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing message index $index", e)
                }
            }

            // Update UID sync state
            if (appPreferences != null && highestProcessedUid > 0L) {
                appPreferences.setGmailUidSyncState(serverUidValidity, highestProcessedUid)
            }

        } finally {
            try { folder?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }

        return@withContext fetchedList
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
