package com.campusfold.tracker.util

import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.parser.MessageExtractor
import com.campusfold.tracker.parser.model.ExtractionResult
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage

object SmsParser {

    fun parse(
        body: String?,
        sender: String?,
        timestamp: Long = System.currentTimeMillis()
    ): TransactionEntity? {
        if (body.isNullOrBlank()) return null
        val cleanBody = body.trim()

        val rawMessage = RawMessage(
            source = MessageSource.SMS,
            sender = sender ?: "",
            body = cleanBody,
            timestamp = timestamp
        )

        return when (val result = MessageExtractor.extract(rawMessage)) {
            is ExtractionResult.Transaction -> {
                result.transaction.toTransactionEntity(rawSms = cleanBody)
            }
            else -> null
        }
    }
}

