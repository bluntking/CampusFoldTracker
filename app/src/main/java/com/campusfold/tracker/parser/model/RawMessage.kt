package com.campusfold.tracker.parser.model

data class RawMessage(
    val source: MessageSource,
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceId: String? = null
)
