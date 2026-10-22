package com.campusfold.tracker.util

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.campusfold.tracker.data.TransactionEntity

object SmsReader {

    private const val TAG = "SmsReader"

    private val SMS_PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.DATE,
        Telephony.Sms.BODY,
        Telephony.Sms.TYPE
    )

    /**
     * Reads SMS from the system inbox cursor over the specified time window (default 90 days).
     * Parses valid financial transaction messages.
     */
    fun readInboxTransactions(
        context: Context,
        daysBack: Int = 90,
        maxMessages: Int = 1000
    ): List<TransactionEntity> {
        val transactions = mutableListOf<TransactionEntity>()
        val startTime = System.currentTimeMillis() - (daysBack.toLong() * 24L * 60L * 60L * 1000L)

        val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            startTime.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC"

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

                var count = 0
                while (c.moveToNext() && count < maxMessages) {
                    val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""

                    if (body.isNotBlank()) {
                        val parsed = SmsParser.parse(body, address, date)
                        if (parsed != null) {
                            transactions.add(parsed)
                        }
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading SMS inbox cursor", e)
        }

        return transactions
    }
}
