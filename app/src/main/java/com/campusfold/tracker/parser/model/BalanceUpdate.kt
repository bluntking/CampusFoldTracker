package com.campusfold.tracker.parser.model

data class BalanceUpdate(
    val bankName: String? = null,
    val accountLast4: String? = null,
    val balancePaise: Long,
    val availableCreditPaise: Long? = null,
    val timestamp: Long,
    val currency: String = "INR",
    val confidence: Float = 1.0f,
    val parserId: String
)
