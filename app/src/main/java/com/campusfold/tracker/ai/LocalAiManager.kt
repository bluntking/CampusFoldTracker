package com.campusfold.tracker.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AiModelStatus {
    object NotLoaded : AiModelStatus()
    data class Loading(val message: String, val progressPercent: Int = 0) : AiModelStatus()
    data class Ready(val modelName: String, val modelPath: String) : AiModelStatus()
    data class Error(val message: String) : AiModelStatus()
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ParsedAiTransaction(
    val amount: Double,
    val type: String, // "EXPENSE", "INCOME", "CC_BILL_TRANSFER"
    val merchant: String,
    val senderName: String? = null
)

/**
 * Local On-Device AI Manager powered by Google MediaPipe LLM Inference (PennyWiseAI style).
 * Allows loading Gemma-2B, Qwen-0.5B/1.5B, or any .bin / .task model file locally.
 * Injects Room DB context (true spend, campus vendors, friends owed) into prompts.
 * Falls back safely when no model file is loaded so the app never crashes.
 */
object LocalAiManager {

    private const val TAG = "LocalAiManager"
    private const val PREFS_NAME = "campusfold_ai_prefs"
    private const val KEY_MODEL_PATH = "saved_model_path"
    private const val KEY_MODEL_NAME = "saved_model_name"
    private const val KEY_UNSTRUCTURED_FALLBACK = "unstructured_ai_fallback_enabled"

    private var llmInference: LlmInference? = null

    private val _modelStatus = MutableStateFlow<AiModelStatus>(AiModelStatus.NotLoaded)
    val modelStatus: StateFlow<AiModelStatus> = _modelStatus.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AI",
                content = "👋 Hey Sahil! I'm your on-device CampusFold AI Coach. Ask me anything about your monthly spend, campus leaks at Akka shop or Bistro, or friend balances. Tap 'Select LLM Model' to load local Gemma/Qwen weights."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isAiFallbackEnabled = MutableStateFlow(true)
    val isAiFallbackEnabled: StateFlow<Boolean> = _isAiFallbackEnabled.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAiFallbackEnabled.value = prefs.getBoolean(KEY_UNSTRUCTURED_FALLBACK, true)

        val savedPath = prefs.getString(KEY_MODEL_PATH, null)
        val savedName = prefs.getString(KEY_MODEL_NAME, null)

        if (!savedPath.isNullOrBlank() && File(savedPath).exists()) {
            loadModelFromPath(context, savedPath, savedName ?: File(savedPath).name)
        }
    }

    fun setAiFallbackEnabled(context: Context, enabled: Boolean) {
        _isAiFallbackEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UNSTRUCTURED_FALLBACK, enabled)
            .apply()
    }

    /**
     * Copies selected model file from Storage Access Framework (SAF) URI into internal storage
     * and initializes MediaPipe LlmInference.
     */
    suspend fun loadModelFromUri(context: Context, uri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        try {
            _modelStatus.value = AiModelStatus.Loading("Preparing model file: $displayName...", 10)

            val modelsDir = File(context.filesDir, "llm_models").apply { mkdirs() }
            val cleanName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val destinationFile = File(modelsDir, cleanName)

            // If file already exists and is non-empty, use directly
            if (!destinationFile.exists() || destinationFile.length() == 0L) {
                _modelStatus.value = AiModelStatus.Loading("Copying model weights to sandbox...", 30)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                } ?: run {
                    _modelStatus.value = AiModelStatus.Error("Failed to open model file stream")
                    return@withContext
                }
            }

            loadModelFromPath(context, destinationFile.absolutePath, displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Error importing model from URI", e)
            _modelStatus.value = AiModelStatus.Error("Failed to load model: ${e.localizedMessage}")
        }
    }

    fun loadModelFromPath(context: Context, path: String, modelName: String) {
        _modelStatus.value = AiModelStatus.Loading("Initializing MediaPipe inference engine...", 80)
        try {
            val file = File(path)
            if (!file.exists()) {
                _modelStatus.value = AiModelStatus.Error("Model file does not exist at $path")
                return
            }

            // Close existing instance if any
            try {
                llmInference?.close()
            } catch (ignored: Exception) {}

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setTemperature(0.7f)
                .setTopK(40)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)

            _modelStatus.value = AiModelStatus.Ready(modelName, path)

            // Save in prefs
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODEL_PATH, path)
                .putString(KEY_MODEL_NAME, modelName)
                .apply()

            Log.i(TAG, "MediaPipe LLM loaded successfully from $path ($modelName)")

        } catch (e: Throwable) {
            Log.e(TAG, "Error creating LlmInference instance", e)
            _modelStatus.value = AiModelStatus.Error("Failed to initialize MediaPipe engine: ${e.message}")
        }
    }

    /**
     * Ask AI Coach with database context injection.
     */
    suspend fun askAiCoach(
        userQuery: String,
        currentMonthSpend: Double,
        categoryBreakdown: List<Pair<String, Double>>,
        topVendors: List<Pair<String, Double>>,
        pnbCreditLimit: Double,
        unsettledFriends: List<Pair<String, Double>>
    ) = withContext(Dispatchers.IO) {
        if (userQuery.isBlank()) return@withContext

        // Add user message to chat
        val userMsg = ChatMessage(sender = "USER", content = userQuery)
        _chatMessages.value = _chatMessages.value + userMsg
        _isGenerating.value = true

        try {
            val systemContextPrompt = buildString {
                appendLine("You are CampusFold AI, an intelligent on-device personal financial coach for a campus student.")
                appendLine("Analyze this live financial snapshot:")
                appendLine("- Current Month True Personal Spend: ₹${currentMonthSpend.toInt()} (excluding friend split shares & CC transfers)")
                appendLine("- Available PNB Credit Card 8598 Limit: ₹${pnbCreditLimit.toInt()}")
                appendLine("- Category Spending:")
                if (categoryBreakdown.isEmpty()) {
                    appendLine("  (No categorized spends yet)")
                } else {
                    categoryBreakdown.forEach { (cat, amt) ->
                        appendLine("  • $cat: ₹${amt.toInt()}")
                    }
                }
                appendLine("- Campus Vendors Spending:")
                if (topVendors.isEmpty()) {
                    appendLine("  (No vendor spends yet)")
                } else {
                    topVendors.forEach { (vendor, amt) ->
                        appendLine("  • $vendor: ₹${amt.toInt()}")
                    }
                }
                appendLine("- Unsettled Balances Friends Owe Me:")
                if (unsettledFriends.isEmpty()) {
                    appendLine("  • All friends are fully settled up! (₹0 pending)")
                } else {
                    unsettledFriends.forEach { (friend, owed) ->
                        appendLine("  • $friend owes: ₹${owed.toInt()}")
                    }
                }
                appendLine()
                appendLine("Student's Question: $userQuery")
                appendLine("Give a concise, direct, helpful, campus-focused response (under 120 words).")
            }

            val responseText: String = if (llmInference != null && _modelStatus.value is AiModelStatus.Ready) {
                try {
                    llmInference!!.generateResponse(systemContextPrompt)
                } catch (e: Exception) {
                    Log.w(TAG, "Inference error, using smart fallback", e)
                    generateRuleBasedFallback(userQuery, currentMonthSpend, categoryBreakdown, topVendors, pnbCreditLimit, unsettledFriends)
                }
            } else {
                generateRuleBasedFallback(userQuery, currentMonthSpend, categoryBreakdown, topVendors, pnbCreditLimit, unsettledFriends)
            }

            val aiMsg = ChatMessage(sender = "AI", content = responseText)
            _chatMessages.value = _chatMessages.value + aiMsg

        } catch (e: Exception) {
            Log.e(TAG, "Error in askAiCoach", e)
            val errorMsg = ChatMessage(sender = "AI", content = "Sorry, I encountered an issue processing your query: ${e.localizedMessage}")
            _chatMessages.value = _chatMessages.value + errorMsg
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * Fallback that provides smart data analysis even when a 2GB model is not yet loaded on device.
     */
    private fun generateRuleBasedFallback(
        query: String,
        spend: Double,
        categories: List<Pair<String, Double>>,
        vendors: List<Pair<String, Double>>,
        pnbLimit: Double,
        friends: List<Pair<String, Double>>
    ): String {
        val qLower = query.lowercase()
        val totalFriendsOwed = friends.sumOf { it.second }

        val prefixNotice = if (_modelStatus.value !is AiModelStatus.Ready) {
            "💡 [Model weights not loaded — analyzing via CampusFold live engine]\n\n"
        } else ""

        return when {
            qLower.contains("cut") || qLower.contains("save") || qLower.contains("control") || qLower.contains("leak") -> {
                val topCat = categories.maxByOrNull { it.second }
                val topVendor = vendors.maxByOrNull { it.second }
                "${prefixNotice}Your highest expense category is **${topCat?.first ?: "Food"}** at ₹${topCat?.second?.toInt() ?: 0}. " +
                        if (topVendor != null) "Your biggest campus leak is at **${topVendor.first}** (₹${topVendor.second.toInt()}). Try setting a weekly spending cap there!"
                        else "Keep track of frequent small spends at Akka shop or Bistro."
            }
            qLower.contains("owe") || qLower.contains("friend") || qLower.contains("split") || qLower.contains("balance") -> {
                if (friends.isEmpty()) {
                    "${prefixNotice}Great news! None of your friends owe you any pending balance. All campus bills are settled."
                } else {
                    val biggestDebtor = friends.maxByOrNull { it.second }
                    "${prefixNotice}Friends currently owe you a total of **₹${totalFriendsOwed.toInt()}**. " +
                            "**${biggestDebtor?.first}** has the largest unsettled portion of ₹${biggestDebtor?.second?.toInt()}. Tap the Splits tab to send a reminder!"
                }
            }
            qLower.contains("afford") || qLower.contains("bistro") || qLower.contains("dinner") || qLower.contains("limit") -> {
                "${prefixNotice}Your current monthly spend is ₹${spend.toInt()} and your PNB CC 8598 available limit is ₹${pnbLimit.toInt()}. " +
                        if (spend > 15000) "You're spending at a fast pace this month—opt for a budget meal at Akka shop!"
                        else "You have room in your budget for dinner at Bistro. Just make sure to split the bill with friends if dining together!"
            }
            else -> {
                val topCat = categories.maxByOrNull { it.second }
                "${prefixNotice}Here is your financial pulse: True personal spend is **₹${spend.toInt()}**, with **${topCat?.first ?: "Food"}** being your top category. " +
                        "Friends owe you **₹${totalFriendsOwed.toInt()}**, and available PNB credit limit is **₹${pnbLimit.toInt()}**. Load a local Gemma-2B .task model to unlock deep neural insights!"
            }
        }
    }

    /**
     * AI Unstructured Fallback: Parses arbitrary SMS or notification text failing regex into structured transaction data.
     */
    suspend fun parseUnstructuredMessage(rawText: String): ParsedAiTransaction? = withContext(Dispatchers.IO) {
        if (!_isAiFallbackEnabled.value || llmInference == null || _modelStatus.value !is AiModelStatus.Ready) {
            return@withContext null
        }

        try {
            val extractionPrompt = """
                Extract financial transaction information from this notification into pure JSON with NO explanation or markdown formatting:
                Message: "$rawText"
                
                Required JSON format:
                {
                  "amount": 100.0,
                  "type": "EXPENSE" or "INCOME" or "CC_BILL_TRANSFER",
                  "merchant": "Vendor name or Store",
                  "senderName": "Person name or null"
                }
            """.trimIndent()

            val result = llmInference!!.generateResponse(extractionPrompt).trim()
            val cleanJson = result.substringAfter("{").substringBeforeLast("}")
            val jsonObject = JSONObject("{$cleanJson}")

            val amount = jsonObject.optDouble("amount", 0.0)
            if (amount <= 0.0) return@withContext null

            val type = jsonObject.optString("type", "EXPENSE")
            val merchant = jsonObject.optString("merchant", "Unstructured Merchant")
            val senderName = if (jsonObject.isNull("senderName")) null else jsonObject.optString("senderName")

            ParsedAiTransaction(
                amount = amount,
                type = type,
                merchant = merchant,
                senderName = senderName
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unstructured AI parsing failed: ${e.message}")
            null
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = "AI",
                content = "Chat cleared. Ask me anything about your campus finances, or tap 'Select LLM Model' to load a new model!"
            )
        )
    }
}
