package com.campusfold.tracker.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.campusfold.tracker.data.pref.AppPreferences
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

sealed class AiModelStatus {
    object NotLoaded : AiModelStatus()
    data class Loading(val message: String, val progressPercent: Int = 0) : AiModelStatus()
    data class Ready(val modelName: String, val modelPath: String) : AiModelStatus()
    data class Stopped(val reason: String) : AiModelStatus()
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
 * Local On-Device AI Manager powered by LiteRT-LM.
 * Operates on an isolated background process (:ai) so native engine crashes never kill the app.
 * Adheres to strict Crash-Loop Guard, safety RAM checks, and resumable SHA-256 verified downloads.
 */
object LocalAiManager {

    private const val TAG = "LocalAiManager"
    private const val PREFS_NAME = "campusfold_ai_prefs"
    private const val KEY_MODEL_PATH = "saved_model_path"
    private const val KEY_MODEL_NAME = "saved_model_name"
    private const val KEY_UNSTRUCTURED_FALLBACK = "unstructured_ai_fallback_enabled"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var remoteEngine: RemoteAiEngine? = null

    private val _modelStatus = MutableStateFlow<AiModelStatus>(AiModelStatus.NotLoaded)
    val modelStatus: StateFlow<AiModelStatus> = _modelStatus.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AI",
                content = "👋 Hey there! I'm your on-device CampusFold AI Coach. Ask me anything about your monthly spend, campus leaks, or friend balances. Download an on-device model to activate neural inference."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isAiFallbackEnabled = MutableStateFlow(true)
    val isAiFallbackEnabled: StateFlow<Boolean> = _isAiFallbackEnabled.asStateFlow()

    private var applicationContext: Context? = null

    /**
     * Lazily binds to isolated AI service on demand (e.g. when opening AI Coach).
     */
    fun ensureEngineBound() {
        val app = applicationContext
        if (remoteEngine == null && app != null) {
            val engine = RemoteAiEngine(app)
            remoteEngine = engine
            scope.launch {
                engine.state.collect { aiState ->
                    _modelStatus.value = when (aiState) {
                        is AiState.NotLoaded -> AiModelStatus.NotLoaded
                        is AiState.Loading -> AiModelStatus.Loading(aiState.message, aiState.progressPercent)
                        is AiState.Ready -> AiModelStatus.Ready(aiState.modelName, aiState.modelPath)
                        is AiState.Stopped -> AiModelStatus.Stopped(aiState.reason)
                        is AiState.Error -> AiModelStatus.Error(aiState.message)
                    }
                }
            }
        }
        remoteEngine?.bindService()
    }

    /**
     * Startup initialization:
     * 1. Installs uncaught exception handler and initializes diagnostics.
     * 2. Executes gated one-time cleanup (clearing legacy model paths once, never touching Room).
     * 3. Checks for previous crash-loop (quarantines file if previous load crashed mid-flight).
     * 4. DOES NOT auto-load model weights (lazy load on first AI use).
     */
    fun init(context: Context) {
        val app = context.applicationContext
        applicationContext = app

        // 1. Diagnostics & Uncaught Exception Handler
        AiDiagnostics.init(app)

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAiFallbackEnabled.value = prefs.getBoolean(KEY_UNSTRUCTURED_FALLBACK, true)

        // Initialize isolated process engine bridge
        if (remoteEngine == null) {
            val engine = RemoteAiEngine(app)
            remoteEngine = engine

            scope.launch {
                engine.state.collect { aiState ->
                    _modelStatus.value = when (aiState) {
                        is AiState.NotLoaded -> AiModelStatus.NotLoaded
                        is AiState.Loading -> AiModelStatus.Loading(aiState.message, aiState.progressPercent)
                        is AiState.Ready -> AiModelStatus.Ready(aiState.modelName, aiState.modelPath)
                        is AiState.Stopped -> AiModelStatus.Stopped(aiState.reason)
                        is AiState.Error -> AiModelStatus.Error(aiState.message)
                    }
                }
            }
        }

        scope.launch {
            val dataStore = AppPreferences(app)

            // 2. Gated one-time cleanup
            CrashLoopGuard.performOneTimeCleanupIfNeeded(app, dataStore)

            // 3. Check crash loop from prior run
            val quarantine = CrashLoopGuard.checkCrashLoopAndQuarantine(app)
            if (quarantine != null) {
                _modelStatus.value = AiModelStatus.Error(
                    "Model crashed during previous load and was quarantined to prevent crash-loop. File: ${File(quarantine.originalPath).name}"
                )
            }
        }
    }

    /**
     * Lazy model loader: loads saved model only when first required by the user or an AI task.
     */
    suspend fun ensureEngineLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (_modelStatus.value is AiModelStatus.Ready) return@withContext true

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dataStore = AppPreferences(app)

        var savedPath = dataStore.getSavedAiModelPathSync()
        if (savedPath.isNullOrBlank()) {
            savedPath = prefs.getString(KEY_MODEL_PATH, null)
        }
        val savedName = prefs.getString(KEY_MODEL_NAME, null)

        if (savedPath.isNullOrBlank() || !File(savedPath).exists()) {
            return@withContext false
        }

        // Safety RAM check
        val mem = AiModelCatalog.getMemoryInfo(app)
        if (mem.lowMemory || mem.availMem < 1_200L * 1024L * 1024L) {
            val err = "Available RAM too low (${mem.availMem / (1024 * 1024)} MB). Skipping lazy load."
            Log.w(TAG, err)
            AiDiagnostics.recordEvent("RAM_CHECK", "Lazy load skipped due to low memory", err)
            return@withContext false
        }

        loadModelFromPath(app, savedPath, savedName ?: File(savedPath).name)
        _modelStatus.value is AiModelStatus.Ready
    }

    fun setAiFallbackEnabled(context: Context, enabled: Boolean) {
        _isAiFallbackEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UNSTRUCTURED_FALLBACK, enabled)
            .apply()
    }

    /**
     * Imports a local .litertlm or model file from Storage Access Framework (SAF) URI.
     */
    suspend fun loadModelFromUri(context: Context, uri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        try {
            _modelStatus.value = AiModelStatus.Loading("Copying model to app storage...", 0)

            val appContext = context.applicationContext
            val modelsDir = File(appContext.filesDir, "llm_models").apply { mkdirs() }
            val cleanName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val destinationFile = File(modelsDir, cleanName)

            val totalBytes = try {
                appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (e: Exception) {
                -1L
            }

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

            loadModelFromPath(appContext, destinationFile.absolutePath, displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Error importing model from URI", e)
            _modelStatus.value = AiModelStatus.Error("Failed to import model: ${e.localizedMessage}")
        }
    }

    /**
     * Downloads an AI model from Hugging Face with resumable Range header, SHA-256 verification,
     * and RAM checks.
     */
    suspend fun downloadModel(context: Context, model: AiModelInfo) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        // Pre-download RAM verification
        val ramError = AiModelCatalog.checkRamForLoad(appContext, model)
        if (ramError != null) {
            _modelStatus.value = AiModelStatus.Error(ramError)
            return@withContext
        }

        _modelStatus.value = AiModelStatus.Loading("Preparing download for ${model.displayName}...", 0)

        val result = ModelDownloader.downloadModel(appContext, model) { progress, downloaded, total ->
            val dlMb = downloaded / (1024 * 1024)
            val totMb = if (total > 0) "${total / (1024 * 1024)}MB" else "unknown"
            _modelStatus.value = AiModelStatus.Loading("Downloading ${model.displayName} ($dlMb MB / $totMb)...", progress)
        }

        result.onSuccess { targetFile ->
            loadModelFromPath(appContext, targetFile.absolutePath, model.displayName)
        }.onFailure { err ->
            _modelStatus.value = AiModelStatus.Error(err.localizedMessage ?: "Download failed")
        }
    }

    /**
     * Loads model file into isolated process engine (:ai) with crash-loop guard protection.
     */
    suspend fun loadModelFromPath(context: Context, path: String, modelName: String) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val file = File(path)
        if (!file.exists()) {
            _modelStatus.value = AiModelStatus.Error("Model file does not exist at $path")
            return@withContext
        }

        // Safety RAM check
        val mem = AiModelCatalog.getMemoryInfo(app)
        if (mem.lowMemory) {
            val err = "Cannot load model: device is in low-memory state."
            _modelStatus.value = AiModelStatus.Error(err)
            return@withContext
        }

        // 1. Crash-Loop Guard: write load_in_progress BEFORE touching native engine
        CrashLoopGuard.markLoadStarted(app, path)

        _modelStatus.value = AiModelStatus.Loading("Loading $modelName into isolated AI engine...", 80)

        val engine = remoteEngine ?: RemoteAiEngine(app).also { remoteEngine = it }
        val loadResult = engine.loadModel(path, modelName)

        if (loadResult.isSuccess) {
            // 2. Crash-Loop Guard: ONLY save path after successful load
            CrashLoopGuard.markLoadSuccess(app, AppPreferences(app), path, modelName)
            _modelStatus.value = AiModelStatus.Ready(modelName, path)
            Log.i(TAG, "Model loaded and verified ready: $modelName ($path)")
        } else {
            val err = loadResult.exceptionOrNull()?.message ?: "Failed to initialize LiteRT-LM engine"
            CrashLoopGuard.markLoadFailed(app, path, err)
            _modelStatus.value = AiModelStatus.Error(err)
            Log.e(TAG, "Model load failed: $err")
        }
    }

    /**
     * Retry loading after an engine stop or unexpected crash.
     */
    suspend fun retry(context: Context) = withContext(Dispatchers.IO) {
        val engine = remoteEngine ?: return@withContext
        _modelStatus.value = AiModelStatus.Loading("Restarting AI engine...", 10)
        val result = engine.retry()
        if (result.isFailure) {
            _modelStatus.value = AiModelStatus.Error("Retry failed: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Runs a safe, non-blocking test inference prompt using CrashLoopGuard to confirm
     * the native engine is functional without crashing or running out of memory.
     */
    suspend fun runInferenceTest(context: Context): Boolean = withContext(Dispatchers.IO) {
        val engine = remoteEngine ?: return@withContext false
        val currentStatus = _modelStatus.value
        if (currentStatus !is AiModelStatus.Ready) {
            return@withContext false
        }
        return@withContext try {
            val result = engine.complete("Hello, test!")
            result.isSuccess
        } catch (e: Throwable) {
            Log.w(TAG, "Inference test failed: ${e.message}")
            false
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
        creditLimit: Double,
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
                appendLine("- Available Credit Card Limit: ₹${creditLimit.toInt()}")
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

            val engine = remoteEngine
            val responseText: String = if (engine != null && _modelStatus.value is AiModelStatus.Ready) {
                val completionResult = engine.complete(systemContextPrompt, maxTokens = 512, timeoutMs = 45_000L)
                completionResult.getOrElse { e ->
                    Log.w(TAG, "Inference error, using smart fallback", e)
                    generateRuleBasedFallback(userQuery, currentMonthSpend, categoryBreakdown, topVendors, creditLimit, unsettledFriends)
                }
            } else {
                generateRuleBasedFallback(userQuery, currentMonthSpend, categoryBreakdown, topVendors, creditLimit, unsettledFriends)
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
     * Fallback that provides smart data analysis even when local weights are not loaded.
     */
    private fun generateRuleBasedFallback(
        query: String,
        spend: Double,
        categories: List<Pair<String, Double>>,
        vendors: List<Pair<String, Double>>,
        creditLimit: Double,
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
                        else "Keep track of frequent small spends at campus stores or cafes."
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
            qLower.contains("afford") || qLower.contains("dinner") || qLower.contains("limit") -> {
                "${prefixNotice}Your current monthly spend is ₹${spend.toInt()} and your credit card available limit is ₹${creditLimit.toInt()}. " +
                        if (spend > 15000) "You're spending at a fast pace this month—opt for a budget meal!"
                        else "You have room in your budget for dinner out. Just make sure to split the bill with friends if dining together!"
            }
            else -> {
                val topCat = categories.maxByOrNull { it.second }
                "${prefixNotice}Here is your financial pulse: True personal spend is **₹${spend.toInt()}**, with **${topCat?.first ?: "Food"}** being your top category. " +
                        "Friends owe you **₹${totalFriendsOwed.toInt()}**, and available credit limit is **₹${creditLimit.toInt()}**. Download a local Qwen 2.5 model to unlock deep neural insights!"
            }
        }
    }

    /**
     * AI Unstructured Fallback: Parses arbitrary SMS or notification text failing regex into structured transaction data.
     */
    suspend fun parseUnstructuredMessage(rawText: String): ParsedAiTransaction? = withContext(Dispatchers.IO) {
        val engine = remoteEngine ?: return@withContext null
        if (!_isAiFallbackEnabled.value || _modelStatus.value !is AiModelStatus.Ready) {
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

            val result = engine.complete(extractionPrompt, maxTokens = 256, timeoutMs = 20_000L).getOrNull()?.trim()
                ?: return@withContext null

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
     * Executes raw prompt completion with the loaded LiteRT engine.
     */
    suspend fun completePrompt(prompt: String, maxTokens: Int = 256, timeoutMs: Long = 15_000L): String? = withContext(Dispatchers.IO) {
        val engine = remoteEngine ?: return@withContext null
        if (_modelStatus.value !is AiModelStatus.Ready) {
            return@withContext null
        }
        try {
            engine.complete(prompt, maxTokens = maxTokens, timeoutMs = timeoutMs).getOrNull()?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "AI prompt completion failed: ${e.message}")
            null
        }
    }

    /**
     * Intelligent on-device AI categorization.
     * Uses fast local knowledge heuristics first, then queries the isolated LiteRT engine if loaded.
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

        // 2. Query isolated LiteRT LLM if loaded
        val engine = remoteEngine
        if (engine != null && _modelStatus.value is AiModelStatus.Ready) {
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
                val response = engine.complete(prompt, maxTokens = 30, timeoutMs = 10_000L).getOrNull()?.trim()
                val clean = response?.lines()?.firstOrNull()?.trim()?.replace("[^a-zA-Z &]".toRegex(), "") ?: ""
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
                content = "Chat cleared. Ask me anything about your campus finances, or select an AI model to load weights!"
            )
        )
    }
}
