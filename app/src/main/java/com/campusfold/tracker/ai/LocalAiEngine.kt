package com.campusfold.tracker.ai

import kotlinx.coroutines.flow.StateFlow

sealed interface AiState {
    object NotLoaded : AiState
    data class Loading(val message: String, val progressPercent: Int = 0) : AiState
    data class Ready(val modelName: String, val modelPath: String) : AiState
    data class Stopped(val reason: String) : AiState
    data class Error(val message: String) : AiState
}

/**
 * Stable interface for on-device AI inference engine.
 * Calls must go through a single queue, support cancel, and never run on the main thread.
 */
interface LocalAiEngine {
    val state: StateFlow<AiState>
    suspend fun complete(
        prompt: String,
        maxTokens: Int = 1024,
        timeoutMs: Long = 60_000L
    ): Result<String>
}
