package com.campusfold.tracker.parser.model

sealed class ExtractionResult {
    data class Transaction(val transaction: ExtractedTransaction) : ExtractionResult()
    data class BalanceOnly(val balanceUpdate: BalanceUpdate) : ExtractionResult()
    data class Ignored(val reason: String) : ExtractionResult()
    data class Unparsed(val rawMessage: RawMessage, val reason: String) : ExtractionResult()
}
