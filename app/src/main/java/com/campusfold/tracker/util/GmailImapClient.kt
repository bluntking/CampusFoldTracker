package com.campusfold.tracker.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class RawAxisEmail(
    val msgId: String,
    val subject: String,
    val from: String,
    val body: String,
    val dateMillis: Long
)

class GmailImapException(message: String, cause: Throwable? = null) : Exception(message, cause)

object GmailImapClient {

    private const val TAG = "GmailImapClient"
    private const val IMAP_HOST = "imap.gmail.com"
    private const val IMAP_PORT = 993

    /**
     * Connects securely over SSL to imap.gmail.com:993, authenticates via Google App Password,
     * searches for Axis Bank credit and debit emails (optionally since startDateMillis),
     * and fetches the message content without marking them as read (using BODY.PEEK).
     */
    suspend fun fetchAxisBankEmails(
        emailAddress: String,
        appPassword: String,
        startDateMillis: Long? = null,
        maxEmailsToFetch: Int = 150,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<RawAxisEmail> = withContext(Dispatchers.IO) {
        val cleanEmail = emailAddress.trim()
        val cleanPassword = appPassword.replace(" ", "").trim()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            throw GmailImapException("Gmail address and App Password cannot be empty.")
        }

        var socket: SSLSocket? = null
        var reader: BufferedReader? = null
        var writer: BufferedWriter? = null
        val fetchedEmails = mutableListOf<RawAxisEmail>()

        try {
            val factory = SSLSocketFactory.getDefault()
            socket = factory.createSocket(IMAP_HOST, IMAP_PORT) as SSLSocket
            socket.soTimeout = 25000 // 25s timeout

            reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8))

            // 1. Initial Greeting
            val greeting = reader.readLine() ?: throw GmailImapException("No response from Gmail IMAP server.")
            Log.d(TAG, "IMAP Greeting: $greeting")

            var tagIndex = 1

            fun sendCommand(cmd: String): List<String> {
                val tag = "A%03d".format(tagIndex++)
                val fullCmd = "$tag $cmd\r\n"
                writer.write(fullCmd)
                writer.flush()

                val responseLines = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    responseLines.add(line)
                    if (line.startsWith("$tag OK")) {
                        break
                    } else if (line.startsWith("$tag NO") || line.startsWith("$tag BAD")) {
                        val errMsg = line.removePrefix("$tag ").trim()
                        if (errMsg.contains("AUTHENTICATIONFAILED", ignoreCase = true) || errMsg.contains("Invalid credentials", ignoreCase = true)) {
                            throw GmailImapException("Authentication failed. Please verify your Gmail address and 16-character App Password (from myaccount.google.com/apppasswords).")
                        }
                        throw GmailImapException("Gmail IMAP error: $errMsg")
                    }
                }
                return responseLines
            }

            // 2. Login
            sendCommand("LOGIN \"$cleanEmail\" \"$cleanPassword\"")
            Log.d(TAG, "Successfully authenticated with Gmail IMAP for $cleanEmail")

            // 3. Select INBOX
            sendCommand("SELECT INBOX")

            // 4. Search Axis Bank Emails
            // Format IMAP date: dd-MMM-yyyy (e.g. 20-Oct-2026)
            val dateFilter = if (startDateMillis != null && startDateMillis > 0) {
                val imapDateFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                "SINCE ${imapDateFmt.format(Date(startDateMillis))}"
            } else ""

            val searchQuery = if (dateFilter.isNotBlank()) {
                "SEARCH OR FROM \"axis.bank.in\" FROM \"axisbank.com\" $dateFilter"
            } else {
                "SEARCH OR FROM \"axis.bank.in\" FROM \"axisbank.com\""
            }

            val searchResponses = sendCommand(searchQuery)
            val messageIds = mutableListOf<String>()

            for (line in searchResponses) {
                if (line.startsWith("* SEARCH")) {
                    val parts = line.removePrefix("* SEARCH").trim().split("\\s+".toRegex())
                    for (p in parts) {
                        if (p.isNotBlank() && p.all { it.isDigit() }) {
                            messageIds.add(p)
                        }
                    }
                }
            }

            Log.d(TAG, "Found ${messageIds.size} Axis Bank email message IDs in Gmail")

            // 5. Fetch recent messages (reverse order to get latest first)
            val toFetch = messageIds.reversed().take(maxEmailsToFetch)

            for ((index, id) in toFetch.withIndex()) {
                try {
                    onProgress?.invoke(index + 1, toFetch.size)
                    val fetchTag = "A%03d".format(tagIndex++)
                    writer.write("$fetchTag FETCH $id (BODY.PEEK[HEADER.FIELDS (SUBJECT FROM DATE)] BODY.PEEK[TEXT])\r\n")
                    writer.flush()

                    var subject = ""
                    var from = ""
                    var rawDateStr = ""
                    val bodyBuilder = StringBuilder()

                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("$fetchTag OK") || line.startsWith("$fetchTag NO") || line.startsWith("$fetchTag BAD")) {
                            break
                        }

                        val lower = line.lowercase()
                        when {
                            lower.startsWith("subject:") -> subject = line.substringAfter(":").trim()
                            lower.startsWith("from:") -> from = line.substringAfter(":").trim()
                            lower.startsWith("date:") -> rawDateStr = line.substringAfter(":").trim()
                            !line.startsWith("* ") && !line.startsWith(")") -> {
                                bodyBuilder.append(line).append("\n")
                            }
                        }
                    }

                    val dateMillis = parseRfc822Date(rawDateStr) ?: System.currentTimeMillis()
                    val cleanedBody = extractCleanBodyText(bodyBuilder.toString())
                    fetchedEmails.add(
                        RawAxisEmail(
                            msgId = id,
                            subject = subject,
                            from = from,
                            body = cleanedBody,
                            dateMillis = dateMillis
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetching email message ID $id", e)
                }
            }

            // 6. Logout
            try {
                sendCommand("LOGOUT")
            } catch (_: Exception) {}

        } catch (e: GmailImapException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "IMAP connection error", e)
            throw GmailImapException("Failed connecting to Gmail IMAP: ${e.localizedMessage}", e)
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { writer?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }

        return@withContext fetchedEmails
    }

    private fun extractCleanBodyText(rawBody: String): String {
        val lower = rawBody.lowercase()
        if (lower.contains("content-transfer-encoding: base64")) {
            val blocks = rawBody.split(Regex("(?i)content-transfer-encoding:\\s*base64"))
            val sb = StringBuilder()
            for (i in 1 until blocks.size) {
                val part = blocks[i]
                val dataPart = part.substringAfter("\n\n").substringBefore("\n--").replace("\r", "").replace("\n", "").trim()
                try {
                    val decoded = java.util.Base64.getDecoder().decode(dataPart)
                    sb.append(String(decoded, StandardCharsets.UTF_8)).append("\n")
                } catch (_: Exception) {}
            }
            if (sb.isNotEmpty()) {
                return sb.toString()
            }
        }
        return rawBody
    }

    private fun parseRfc822Date(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        val clean = dateStr.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        val formats = listOf(
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("d MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.US)
        )
        for (fmt in formats) {
            try {
                val d = fmt.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }
}
