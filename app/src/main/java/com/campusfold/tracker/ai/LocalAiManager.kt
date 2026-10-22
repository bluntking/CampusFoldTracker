package com.campusfold.tracker.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.campusfold.tracker.data.pref.AppPreferences
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AiModelStatus {
    object NotLoaded : AiModelStatus()
    data class Loading(val message: String, val progressPercent: Int = 0) : AiModelStatus()
    data class Ready(val modelName: String, val modelPath: String) : AiModelStatus()
    data class Error(val message: String) : AiModelStatus()
}

enum class QwenModelVariant(
    val displayName: String,
    val fileName: String,
    val url: String,
    val minValidSizeBytes: Long
) {
    QWEN_0_5B(
        displayName = "Qwen 2.5 (0.5B)",
        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        minValidSizeBytes = 500L * 1024 * 1024
    ),
    QWEN_1_5B(
        displayName = "Qwen 2.5 (1.5B)",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        minValidSizeBytes = 1500L * 1024 * 1024
    )
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

    const val QWEN_MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val QWEN_MODEL_FILENAME = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"

    private var llmInference: LlmInference? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _modelStatus = MutableStateFlow<AiModelStatus>(AiModelStatus.NotLoaded)
    val modelStatus: StateFlow<AiModelStatus> = _modelStatus.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AI",
                content = "👋 Hey Sahil! I'm your on-device CampusFold AI Coach. Ask me anything about your monthly spend, campus leaks at Akka shop or Bistro, or friend balances. Tap 'Select LLM Model' or 1-Tap 'Download Qwen 2.5' to load local weights."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isAiFallbackEnabled = MutableStateFlow(true)
    val isAiFallbackEnabled: StateFlow<Boolean> = _isAiFallbackEnabled.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAiFallbackEnabled.value = prefs.getBoolean(KEY_UNSTRUCTURED_FALLBACK, true)

        scope.launch {
            // First check DataStore for saved model path, then fallback to SharedPreferences
            val dataStore = AppPreferences(appContext)
            var savedPath = dataStore.getSavedAiModelPathSync()
            if (savedPath.isNullOrBlank()) {
                savedPath = prefs.getString(KEY_MODEL_PATH, null)
            }
            val savedName = prefs.getString(KEY_MODEL_NAME, null)

            if (!savedPath.isNullOrBlank() && File(savedPath).exists()) {
                loadModelFromPath(appContext, savedPath, savedName ?: File(savedPath).name)
            }
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
     * and initializes MediaPipe LlmInference on Dispatchers.IO.
     */
    suspend fun loadModelFromUri(context: Context, uri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        try {
            _modelStatus.value = AiModelStatus.Loading("Copying model to app storage...", 0)

            val appContext = context.applicationContext
            val modelsDir = File(appContext.filesDir, "llm_models").apply { mkdirs() }
            val cleanName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val destinationFile = File(modelsDir, cleanName)

            // Determine content size if possible
            val totalBytes = try {
                appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (e: Exception) {
                -1L
            }

            // If file already exists and is non-empty, use directly
            if (!destinationFile.exists() || destinationFile.length() == 0L || (totalBytes > 0 && destinationFile.length() != totalBytes)) {
                _modelStatus.value = AiModelStatus.Loading("Copying model to app storage...", 1)
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var copiedBytes = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes > 0) {
                                val progress = ((copiedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(1, 99)
                                _modelStatus.value = AiModelStatus.Loading("Copying model to app storage...", progress)
                            }
                        }
                        output.flush()
                    }
                } ?: run {
                    _modelStatus.value = AiModelStatus.Error("Failed to open model file stream")
                    return@withContext
                }
            }

            // Save path in DataStore & SharedPreferences
            AppPreferences(appContext).setSavedAiModelPath(destinationFile.absolutePath)
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODEL_PATH, destinationFile.absolutePath)
                .putString(KEY_MODEL_NAME, displayName)
                .apply()

            loadModelFromPath(appContext, destinationFile.absolutePath, displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Error importing model from URI", e)
            _modelStatus.value = AiModelStatus.Error("Failed to load model: ${e.localizedMessage}")
        }
    }

    /**
     * 1-Tap Download Qwen 2.5 (0.5B - 547MB or 1.5B - 1.6GB) directly from HuggingFace to context.filesDir.
     * Follows HTTP 302 redirects properly and tracks download progress.
     */
    suspend fun downloadQwenModel(
        context: Context,
        variant: QwenModelVariant = QwenModelVariant.QWEN_0_5B
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val modelsDir = File(appContext.filesDir, "llm_models").apply { mkdirs() }
        val targetFile = File(modelsDir, variant.fileName)
        val tempFile = File(modelsDir, "${variant.fileName}.tmp")

        // If file already exists and has valid size, initialize directly
        if (targetFile.exists() && targetFile.length() > variant.minValidSizeBytes) {
            Log.i(TAG, "${variant.displayName} model already downloaded (${targetFile.length()} bytes). Initializing...")
            AppPreferences(appContext).setSavedAiModelPath(targetFile.absolutePath)
            loadModelFromPath(appContext, targetFile.absolutePath, variant.displayName)
            return@withContext
        }

        try {
            _modelStatus.value = AiModelStatus.Loading("Connecting to Hugging Face for ${variant.displayName}...", 0)

            var currentUrl = variant.url
            var connection: HttpURLConnection
            var redirectCount = 0

            // Follow redirects manually (HF returns 302 to CDN)
            while (true) {
                val urlObj = URL(currentUrl)
                connection = urlObj.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.setRequestProperty("User-Agent", "CampusFoldTracker-Android")
                connection.connect()

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLocation.isNullOrBlank() || redirectCount++ > 5) {
                        throw Exception("Too many redirects or missing Location header")
                    }
                    currentUrl = newLocation
                } else if (code == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    throw Exception("Server returned HTTP $code")
                }
            }

            val totalBytes = connection.contentLengthLong
            Log.i(TAG, "Downloading ${variant.displayName} total size: $totalBytes bytes")

            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 250) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
                            } else 0
                            val mbDownloaded = downloadedBytes / (1024 * 1024)
                            val totalMb = if (totalBytes > 0) "${totalBytes / (1024 * 1024)}MB" else "calculating..."
                            _modelStatus.value = AiModelStatus.Loading("Downloading ${variant.displayName} ($mbDownloaded MB / $totalMb)...", progress)
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } else {
                throw Exception("Downloaded file is empty")
            }

            // Save in DataStore & SharedPreferences
            AppPreferences(appContext).setSavedAiModelPath(targetFile.absolutePath)
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODEL_PATH, targetFile.absolutePath)
                .putString(KEY_MODEL_NAME, variant.displayName)
                .apply()

            loadModelFromPath(appContext, targetFile.absolutePath, variant.displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading ${variant.displayName}", e)
            if (tempFile.exists()) tempFile.delete()
            _modelStatus.value = AiModelStatus.Error("Download failed: ${e.localizedMessage}")
        }
    }

    /**
     * Initializes MediaPipe LlmInference strictly on Dispatchers.IO (never on Main UI thread).
     */
    suspend fun loadModelFromPath(context: Context, path: String, modelName: String) = withContext(Dispatchers.IO) {
        _modelStatus.value = AiModelStatus.Loading("Initializing MediaPipe inference engine...", 90)
        try {
            val file = File(path)
            if (!file.exists()) {
                _modelStatus.value = AiModelStatus.Error("Model file does not exist at $path")
                return@withContext
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

            llmInference = LlmInference.createFromOptions(context.applicationContext, options)

            _modelStatus.value = AiModelStatus.Ready(modelName, path)

            // Save in DataStore & SharedPreferences
            AppPreferences(context.applicationContext).setSavedAiModelPath(path)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODEL_PATH, path)
                .putString(KEY_MODEL_NAME, modelName)
                .apply()

            Log.i(TAG, "MediaPipe LLM loaded successfully on Dispatchers.IO from $path ($modelName)")

        } catch (e: Throwable) {
            Log.e(TAG, "Error creating LlmInference instance on Dispatchers.IO", e)
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

    /**
     * Intelligent on-device AI categorization.
     * Uses loaded MediaPipe LLM if available, or fast local knowledge heuristics.
     * E.g. "Air India" -> "Travel"
     */
    suspend fun predictCategoryForMerchant(
        merchantName: String,
        existingCategories: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val lower = merchantName.lowercase().trim()
        if (lower.isBlank()) return@withContext "Other"

        // 1. Fast local knowledge heuristics (0ms instant classification)
        val heuristicCategory = classifyMerchantHeuristically(lower)
        if (heuristicCategory != null) {
            return@withContext heuristicCategory
        }

        // 2. If MediaPipe LLM is loaded and ready, query the on-device LLM
        if (llmInference != null && _modelStatus.value is AiModelStatus.Ready) {
            try {
                val catListStr = if (existingCategories.isNotEmpty()) {
                    existingCategories.joinToString(", ")
                } else {
                    "Food & Dining, Travel, Groceries, Shopping, Entertainment, Utilities, Healthcare, Education, Chai & Smoke, Clothing, Personal Care"
                }
                val prompt = """
                    Categorize the payee or merchant '$merchantName' into one expense category (e.g. Travel, Food & Dining, Groceries, Shopping, Entertainment, Utilities, Healthcare, Education, Clothing, Personal Care).
                    Available options: $catListStr.
                    If it is an airline, flight, cab, train, hotel booking, metro, or fuel, reply 'Travel'.
                    Reply ONLY with the single category name, no extra words or punctuation:
                """.trimIndent()
                val response = llmInference!!.generateResponse(prompt).trim()
                val clean = response.lines().firstOrNull()?.trim()?.replace("[^a-zA-Z &]".toRegex(), "") ?: ""
                if (clean.isNotBlank() && clean.length < 30) {
                    return@withContext clean
                }
            } catch (e: Exception) {
                Log.w(TAG, "LLM auto-categorization error: ${e.message}")
            }
        }

        return@withContext "Other"
    }

    private fun classifyMerchantHeuristically(lower: String): String? {
        return when {
            // Travel / Flights / Cabs / Trains / Fuel
            listOf("air india", "indigo", "spicejet", "akasa", "vistara", "airasia", "emirates", "qatar", "lufthansa", "boeing",
                "uber", "ola", "rapido", "irctc", "makemytrip", "easemytrip", "goibibo", "yatra", "cleartrip",
                "flight", "airline", "airways", "railway", "metro", "cab", "taxi", "petrol", "diesel", "fuel", "hpcl", "bpcl",
                "iocl", "indian oil", "shell", "redbus", "abhibus", "fastag", "toll", "parking", "auto", "travel", "rickshaw").any { lower.contains(it) } -> "Travel"

            // Food & Dining / Cafes / Restaurants
            listOf("zomato", "swiggy", "bistro", "hungerbox", "mcdonald", "kfc", "burger", "pizza", "domino", "starbucks",
                "cafe", "coffee", "restaurant", "hotel", "dhaba", "canteen", "mess", "bakery", "subway", "chaaterbox",
                "haldiram", "biryani", "barbeque", "dosa", "food", "kitchen", "eats", "dining", "bar", "pub").any { lower.contains(it) } -> "Food & Dining"

            // Groceries / Supermarkets
            listOf("zepto", "blinkit", "instamart", "bigbasket", "supermarket", "dmart", "reliance fresh", "kirana",
                "provision", "mart", "dd store", "akka shop", "nature's basket", "grocer", "vegetable", "fruit", "dairy", "milk").any { lower.contains(it) } -> "Groceries"

            // Chai & Smoke (Campus staple)
            listOf("chai", "tea", "smoke", "pan shop", "paan", "tapri", "sutta", "beedi", "cigarette", "tobacco").any { lower.contains(it) } -> "Chai & Smoke"

            // Beverages
            listOf("beverage", "juice", "shake", "smoothie", "boba", "lassi", "soda", "lemonade", "chennai beverage").any { lower.contains(it) } -> "Beverages"

            // Entertainment / Subscriptions
            listOf("netflix", "prime video", "hotstar", "spotify", "bookmyshow", "pvr", "inox", "cinepolis", "cinema",
                "theatre", "youtube", "gaming", "steam", "playstation", "sony liv", "zee5", "apple music").any { lower.contains(it) } -> "Entertainment"

            // Clothing / Fashion / Apparel
            listOf("zara", "h&m", "uniqlo", "myntra", "ajio", "nykaa", "meesho", "clothing", "apparel", "trends",
                "westside", "max fashion", "pantaloons", "snitch", "souled store", "lifestyle", "shoppers stop", "fashion", "garment").any { lower.contains(it) } -> "Clothing"

            // Utilities & Bills
            listOf("electricity", "bescom", "airtel", "jio", "vi ", "vodafone", "broadband", "wifi", "gas",
                "water bill", "recharge", "billdesk", "tneb", "cesc", "tatasky", "dth", "cylinder").any { lower.contains(it) } -> "Utilities"

            // Healthcare / Pharmacy
            listOf("apollo", "pharmacy", "medplus", "1mg", "practo", "hospital", "clinic", "medical", "doctor",
                "dentist", "diagnostic", "chemist", "pathology", "lab").any { lower.contains(it) } -> "Healthcare"

            // Education / Courses / Books
            listOf("udemy", "coursera", "book", "xerox", "print", "stationery", "tuition", "college", "fees",
                "school", "academy", "exam", "pen", "paper").any { lower.contains(it) } -> "Education"

            else -> null
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
