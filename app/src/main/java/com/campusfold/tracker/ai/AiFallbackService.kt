package com.campusfold.tracker.ai

import android.util.Log
import com.campusfold.tracker.data.dao.AiCacheDao
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.AiCacheEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.data.pref.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class AiParsedTransaction(
    val amount: Double,
    val type: TransactionType,
    val merchant: String,
    val bankName: String? = null,
    val accountLast4: String? = null,
    val confidence: Float = 0.70f
)

data class AiAccountIdentification(
    val bankName: String,
    val accountLast4: String,
    val accountType: AccountType,
    val confidence: Float = 0.70f
)

open class AiFallbackService(
    private val aiCacheDao: AiCacheDao,
    private val appPreferences: AppPreferences? = null
) {
    companion object {
        private const val TAG = "AiFallbackService"
        const val AUTO_SAVE_THRESHOLD = 0.85f
        const val MODEL_CONFIDENCE_CAP = 0.70f

        const val TASK_PARSE_UNSTRUCTURED = "PARSE_UNSTRUCTURED"
        const val TASK_CATEGORIZE_VENDOR = "CATEGORIZE_VENDOR"
        const val TASK_ACCOUNT_IDENTIFICATION = "ACCOUNT_IDENTIFICATION"
        const val TASK_SPECIAL_CASE = "SPECIAL_CASE"
        const val TASK_VENDOR_IS_SAME = "VENDOR_IS_SAME"
        const val TASK_ACCOUNT_IS_SAME = "ACCOUNT_IS_SAME"

        fun computeHash(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.trim().toByteArray(StandardCharsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Checks if Local AI is loaded, ready, and enabled.
     */
    open fun isAiAvailable(): Boolean {
        return LocalAiManager.modelStatus.value is AiModelStatus.Ready &&
                LocalAiManager.isAiFallbackEnabled.value
    }

    /**
     * Parse unstructured message text failing standard regex/parsers.
     * Enforces strict JSON schema validation.
     * Discards invalid output (e.g. amount <= 0 or invalid enum).
     */
    suspend fun parseUnstructuredMessage(rawText: String): AiParsedTransaction? = withContext(Dispatchers.IO) {
        val inputHash = computeHash("$TASK_PARSE_UNSTRUCTURED:$rawText")

        // 1. Check cache
        val cached = aiCacheDao.getByInputHashAndTask(inputHash, TASK_PARSE_UNSTRUCTURED)
        if (cached != null) {
            val parsed = validateAndParseTransactionJson(cached.resultJson)
            if (parsed != null) return@withContext parsed
        }

        // 2. Offline / engine check
        if (!isAiAvailable() || appPreferences?.isAiTaskEnabledSync(TASK_PARSE_UNSTRUCTURED) == false) return@withContext null

        val prompt = """
            Extract financial transaction details from this text into strict JSON:
            Text: "$rawText"
            
            Format:
            {
              "amount": 100.0,
              "type": "EXPENSE",
              "merchant": "Vendor Name",
              "bankName": "Bank Name",
              "accountLast4": "1234"
            }
            Valid types: EXPENSE, INCOME, CC_BILL_TRANSFER, OWN_TRANSFER, REFUND, ATM, EMI.
            Reply ONLY with valid JSON.
        """.trimIndent()

        val rawOutput = runInference(prompt, timeoutMs = 20_000L) ?: return@withContext null
        val validated = validateAndParseTransactionJson(rawOutput)

        if (validated != null) {
            // Cache valid result
            try {
                aiCacheDao.insertCache(
                    AiCacheEntity(
                        inputHash = inputHash,
                        task = TASK_PARSE_UNSTRUCTURED,
                        resultJson = rawOutput
                    )
                )
            } catch (_: Exception) {}
        }

        return@withContext validated
    }

    /**
     * Categorize unknown vendor, preferring user's existing categories.
     */
    suspend fun categorizeVendor(vendorName: String, existingCategories: List<String>): String? = withContext(Dispatchers.IO) {
        if (vendorName.isBlank()) return@withContext null
        val inputHash = computeHash("$TASK_CATEGORIZE_VENDOR:$vendorName:${existingCategories.sorted().joinToString(",")}")

        val cached = aiCacheDao.getByInputHashAndTask(inputHash, TASK_CATEGORIZE_VENDOR)
        if (cached != null) {
            val cat = extractCategoryFromJson(cached.resultJson)
            if (!cat.isNullOrBlank()) return@withContext cat
        }

        if (!isAiAvailable() || appPreferences?.isAiTaskEnabledSync(TASK_CATEGORIZE_VENDOR) == false) return@withContext null

        val catListStr = if (existingCategories.isNotEmpty()) {
            existingCategories.joinToString(", ")
        } else {
            "Food & Dining, Travel, Groceries, Shopping, Entertainment, Utilities, Healthcare, Education"
        }

        val prompt = """
            Categorize payee '$vendorName'.
            Available categories: $catListStr.
            Reply with JSON: {"category": "Category Name"}
        """.trimIndent()

        val rawOutput = runInference(prompt, timeoutMs = 12_000L) ?: return@withContext null
        val cat = extractCategoryFromJson(rawOutput)

        if (!cat.isNullOrBlank()) {
            try {
                aiCacheDao.insertCache(
                    AiCacheEntity(
                        inputHash = inputHash,
                        task = TASK_CATEGORIZE_VENDOR,
                        resultJson = rawOutput
                    )
                )
            } catch (_: Exception) {}
        }

        return@withContext cat
    }

    /**
     * Account identification from messy text.
     */
    suspend fun identifyAccount(messyText: String): AiAccountIdentification? = withContext(Dispatchers.IO) {
        val inputHash = computeHash("$TASK_ACCOUNT_IDENTIFICATION:$messyText")

        val cached = aiCacheDao.getByInputHashAndTask(inputHash, TASK_ACCOUNT_IDENTIFICATION)
        if (cached != null) {
            val acc = validateAccountIdentificationJson(cached.resultJson)
            if (acc != null) return@withContext acc
        }

        if (!isAiAvailable() || appPreferences?.isAiTaskEnabledSync(TASK_ACCOUNT_IDENTIFICATION) == false) return@withContext null

        val prompt = """
            Extract bank and account number from this message into JSON:
            Text: "$messyText"
            Format:
            {
              "bankName": "Axis Bank",
              "accountLast4": "2003",
              "accountType": "SAVINGS" or "CREDIT_CARD" or "WALLET"
            }
        """.trimIndent()

        val rawOutput = runInference(prompt, timeoutMs = 15_000L) ?: return@withContext null
        val acc = validateAccountIdentificationJson(rawOutput)

        if (acc != null) {
            try {
                aiCacheDao.insertCache(
                    AiCacheEntity(
                        inputHash = inputHash,
                        task = TASK_ACCOUNT_IDENTIFICATION,
                        resultJson = rawOutput
                    )
                )
            } catch (_: Exception) {}
        }

        return@withContext acc
    }

    /**
     * Detects special transaction cases (own transfer, refund, EMI, card bill payment).
     */
    suspend fun resolveSpecialCase(text: String): TransactionType? = withContext(Dispatchers.IO) {
        val inputHash = computeHash("$TASK_SPECIAL_CASE:$text")

        val cached = aiCacheDao.getByInputHashAndTask(inputHash, TASK_SPECIAL_CASE)
        if (cached != null) {
            val type = extractTypeFromJson(cached.resultJson)
            if (type != null) return@withContext type
        }

        if (!isAiAvailable() || appPreferences?.isAiTaskEnabledSync(TASK_PARSE_UNSTRUCTURED) == false) return@withContext null

        val prompt = """
            Analyze if this transaction is a special case:
            Text: "$text"
            Possible types: OWN_TRANSFER, REFUND, EMI, CC_BILL_TRANSFER, NONE.
            Reply in JSON: {"type": "..."}
        """.trimIndent()

        val rawOutput = runInference(prompt, timeoutMs = 12_000L) ?: return@withContext null
        val detected = extractTypeFromJson(rawOutput)

        if (detected != null) {
            try {
                aiCacheDao.insertCache(
                    AiCacheEntity(
                        inputHash = inputHash,
                        task = TASK_SPECIAL_CASE,
                        resultJson = rawOutput
                    )
                )
            } catch (_: Exception) {}
        }

        return@withContext detected
    }

    /**
     * Judge if two vendor strings represent the same merchant.
     */
    suspend fun judgeVendorIsSame(vendorA: String, vendorB: String): Pair<Boolean, String?>? = withContext(Dispatchers.IO) {
        val inputHash = computeHash("$TASK_VENDOR_IS_SAME:$vendorA:$vendorB")

        val cached = aiCacheDao.getByInputHashAndTask(inputHash, TASK_VENDOR_IS_SAME)
        if (cached != null) {
            val res = validateVendorIsSameJson(cached.resultJson)
            if (res != null) return@withContext res
        }

        if (!isAiAvailable() || appPreferences?.isAiTaskEnabledSync(TASK_VENDOR_IS_SAME) == false) return@withContext null

        val prompt = """
            Are '$vendorA' and '$vendorB' the same merchant?
            Reply in JSON: {"isSame": true, "canonicalName": "Common Name"}
        """.trimIndent()

        val rawOutput = runInference(prompt, timeoutMs = 12_000L) ?: return@withContext null
        val res = validateVendorIsSameJson(rawOutput)

        if (res != null) {
            try {
                aiCacheDao.insertCache(
                    AiCacheEntity(
                        inputHash = inputHash,
                        task = TASK_VENDOR_IS_SAME,
                        resultJson = rawOutput
                    )
                )
            } catch (_: Exception) {}
        }

        return@withContext res
    }

    /**
     * Judge if two account descriptions refer to the same financial account.
     * AI-only matches are NEVER auto-merged; they are strictly used for Suggested Merges.
     */
    open suspend fun judgeDuplicateAccounts(
        nameA: String, bankA: String, last4A: String,
        nameB: String, bankB: String, last4B: String
    ): Boolean = withContext(Dispatchers.IO) {
        val inputHash = computeHash("$TASK_ACCOUNT_IS_SAME:$nameA:$bankA:$last4A:$nameB:$bankB:$last4B")

        val cached = aiCacheDao.getByInputHashAndTask(inputHash, TASK_ACCOUNT_IS_SAME)
        if (cached != null) {
            val res = try { JSONObject(cached.resultJson).optBoolean("isSame", false) } catch (_: Exception) { false }
            return@withContext res
        }

        if (!isAiAvailable() || appPreferences?.isAiTaskEnabledSync(TASK_ACCOUNT_IS_SAME) == false) return@withContext false

        val prompt = """
            Account 1: '$nameA' (Bank: '$bankA', Last4: '$last4A')
            Account 2: '$nameB' (Bank: '$bankB', Last4: '$last4B')
            Do these describe the same financial account?
            Reply in JSON: {"isSame": true/false}
        """.trimIndent()

        val rawOutput = runInference(prompt, timeoutMs = 12_000L) ?: return@withContext false
        val isSame = try {
            val clean = rawOutput.substringAfter("{").substringBeforeLast("}")
            JSONObject("{$clean}").optBoolean("isSame", false)
        } catch (_: Exception) {
            false
        }

        try {
            aiCacheDao.insertCache(
                AiCacheEntity(
                    inputHash = inputHash,
                    task = TASK_ACCOUNT_IS_SAME,
                    resultJson = JSONObject().put("isSame", isSame).toString()
                )
            )
        } catch (_: Exception) {}

        return@withContext isSame
    }

    private suspend fun runInference(prompt: String, timeoutMs: Long): String? {
        return try {
            LocalAiManager.completePrompt(prompt, maxTokens = 128, timeoutMs = timeoutMs)
        } catch (e: Exception) {
            Log.w(TAG, "Inference call failed: ${e.message}")
            null
        }
    }

    // ─── JSON Schema Validators ───────────────────────────────────────────────

    fun validateAndParseTransactionJson(jsonStr: String): AiParsedTransaction? {
        return try {
            val clean = jsonStr.substringAfter("{").substringBeforeLast("}")
            val obj = JSONObject("{$clean}")

            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return null

            val typeStr = obj.optString("type", "EXPENSE").trim().uppercase(Locale.US)
            val type = runCatching { TransactionType.valueOf(typeStr) }.getOrNull() ?: TransactionType.EXPENSE

            val merchant = obj.optString("merchant", "").trim()
            if (merchant.isBlank()) return null

            val bank = obj.optString("bankName", "").trim().ifBlank { null }
            val last4Raw = obj.optString("accountLast4", "").trim()
            val last4 = if (last4Raw.isNotBlank() && last4Raw.all { it.isDigit() } && last4Raw.length in 3..4) {
                last4Raw
            } else null

            AiParsedTransaction(
                amount = amount,
                type = type,
                merchant = merchant,
                bankName = bank,
                accountLast4 = last4,
                confidence = MODEL_CONFIDENCE_CAP
            )
        } catch (_: Exception) {
            null
        }
    }

    fun extractCategoryFromJson(jsonStr: String): String? {
        return try {
            val clean = jsonStr.substringAfter("{").substringBeforeLast("}")
            val obj = JSONObject("{$clean}")
            val cat = obj.optString("category", "").trim()
            cat.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun validateAccountIdentificationJson(jsonStr: String): AiAccountIdentification? {
        return try {
            val clean = jsonStr.substringAfter("{").substringBeforeLast("}")
            val obj = JSONObject("{$clean}")

            val bankName = obj.optString("bankName", "").trim()
            if (bankName.isBlank()) return null

            val last4 = obj.optString("accountLast4", "").trim()
            if (last4.isBlank() || !last4.all { it.isDigit() } || last4.length !in 3..4) return null

            val typeStr = obj.optString("accountType", "SAVINGS").trim().uppercase(Locale.US)
            val type = runCatching { AccountType.valueOf(typeStr) }.getOrDefault(AccountType.SAVINGS)

            AiAccountIdentification(
                bankName = bankName,
                accountLast4 = last4,
                accountType = type,
                confidence = MODEL_CONFIDENCE_CAP
            )
        } catch (_: Exception) {
            null
        }
    }

    fun extractTypeFromJson(jsonStr: String): TransactionType? {
        return try {
            val clean = jsonStr.substringAfter("{").substringBeforeLast("}")
            val obj = JSONObject("{$clean}")
            val typeStr = obj.optString("type", "").trim().uppercase(Locale.US)
            if (typeStr == "NONE" || typeStr.isBlank()) null
            else runCatching { TransactionType.valueOf(typeStr) }.getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun validateVendorIsSameJson(jsonStr: String): Pair<Boolean, String?>? {
        return try {
            val clean = jsonStr.substringAfter("{").substringBeforeLast("}")
            val obj = JSONObject("{$clean}")
            val isSame = obj.optBoolean("isSame", false)
            val canonical = obj.optString("canonicalName", "").trim().ifBlank { null }
            Pair(isSame, canonical)
        } catch (_: Exception) {
            null
        }
    }
}
