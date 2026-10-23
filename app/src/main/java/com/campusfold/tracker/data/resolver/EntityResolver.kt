package com.campusfold.tracker.data.resolver

import com.campusfold.tracker.ai.AiFallbackService
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.ingest.IngestBatchContext
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.parser.model.Direction
import com.campusfold.tracker.parser.model.RawMessage
import com.campusfold.tracker.rules.ActionType
import com.campusfold.tracker.rules.RuleEngine
import com.campusfold.tracker.rules.TransactionField
import org.json.JSONObject
import java.util.Locale

data class ResolvedEntities(
    val account: AccountEntity,
    val vendorDisplayName: String,
    val categoryId: Long?,
    val isBlocked: Boolean = false,
    val blockingRuleId: Long? = null,
    val confidence: Float = 1.0f,
    val needsReview: Boolean = false,
    val reviewReason: String? = null
)

interface EntityResolver {
    suspend fun resolve(
        bankName: String?,
        bankKey: String?,
        accountLast4: String?,
        instrumentType: String?,
        merchantRaw: String?,
        direction: Direction,
        transactionType: TransactionType,
        rawMessage: RawMessage? = null,
        amountPaise: Long = 0L,
        timestamp: Long = System.currentTimeMillis(),
        editedFields: String = "[]",
        batchContext: IngestBatchContext? = null
    ): ResolvedEntities

    suspend fun resolve(
        bankName: String?,
        bankKey: String?,
        accountLast4: String?,
        instrumentType: String?,
        merchantRaw: String?,
        direction: Direction,
        transactionType: TransactionType
    ): ResolvedEntities = resolve(
        bankName = bankName,
        bankKey = bankKey,
        accountLast4 = accountLast4,
        instrumentType = instrumentType,
        merchantRaw = merchantRaw,
        direction = direction,
        transactionType = transactionType,
        rawMessage = null,
        amountPaise = 0L,
        timestamp = System.currentTimeMillis(),
        editedFields = "[]",
        batchContext = null
    )
}

class DefaultEntityResolver(
    private val accountDao: AccountDao,
    private val accountAliasDao: AccountAliasDao,
    private val vendorDao: VendorDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao? = null,
    private val reviewItemDao: ReviewItemDao? = null,
    private val aiFallbackService: AiFallbackService? = null,
    private val appPreferences: AppPreferences? = null
) : EntityResolver {

    private val categoryColors = listOf(
        "#10B981", // Emerald
        "#06B6D4", // Cyan
        "#F59E0B", // Amber
        "#8B5CF6", // Purple
        "#EC4899", // Pink
        "#EF4444", // Red
        "#3B82F6", // Blue
        "#6366F1", // Indigo
        "#14B8A6"  // Teal
    )

    override suspend fun resolve(
        bankName: String?,
        bankKey: String?,
        accountLast4: String?,
        instrumentType: String?,
        merchantRaw: String?,
        direction: Direction,
        transactionType: TransactionType,
        rawMessage: RawMessage?,
        amountPaise: Long,
        timestamp: Long,
        editedFields: String,
        batchContext: IngestBatchContext?
    ): ResolvedEntities {
        var needsReview = false
        var reviewReason: String? = null
        var overallConfidence = 1.0f

        // ─── 1. ACCOUNTS RESOLUTION ───────────────────────────────────────────
        val (normKey, normName) = AccountNormalizer.normalizeBank(bankKey ?: bankName)
        val normLast4 = AccountNormalizer.normalizeLast4(accountLast4)
        var resolvedAccount: AccountEntity? = null

        if (normKey.isNotBlank() && normLast4 != null) {
            // Check alias first
            val aliasAccountId = accountAliasDao.findAccountIdByAlias(normKey, normLast4)
            if (aliasAccountId != null) {
                resolvedAccount = batchContext?.accounts?.find { it.id == aliasAccountId }
                    ?: accountDao.getAccountById(aliasAccountId)
            }

            // Check primary account
            if (resolvedAccount == null) {
                resolvedAccount = batchContext?.accounts?.find { it.bankKey.equals(normKey, ignoreCase = true) && it.last4 == normLast4 }
                    ?: accountDao.findByBankKeyAndLast4(normKey, normLast4)
            }

            // Create new account if not found
            if (resolvedAccount == null) {
                val inferredType = AccountNormalizer.inferAccountType(instrumentType ?: rawMessage?.body)
                val autoDisplayName = AccountNormalizer.formatDisplayName(normName, normLast4)
                val newAcc = AccountEntity(
                    bankName = normName,
                    bankKey = normKey,
                    last4 = normLast4,
                    type = inferredType,
                    displayName = autoDisplayName,
                    balanceSource = BalanceSource.AUTO
                )
                val insertedId = accountDao.insertAccount(newAcc)
                resolvedAccount = newAcc.copy(id = insertedId)
                batchContext?.accounts?.add(resolvedAccount)
            }
        }

        // Fallback: Check AI for account identification if missing
        if (resolvedAccount == null && aiFallbackService != null && aiFallbackService.isAiAvailable() && rawMessage != null) {
            val aiIdentified = aiFallbackService.identifyAccount(rawMessage.body)
            if (aiIdentified != null) {
                val (aiKey, aiName) = AccountNormalizer.normalizeBank(aiIdentified.bankName)
                val aiLast4 = aiIdentified.accountLast4
                resolvedAccount = batchContext?.accounts?.find { it.bankKey.equals(aiKey, ignoreCase = true) && it.last4 == aiLast4 }
                    ?: accountDao.findByBankKeyAndLast4(aiKey, aiLast4)
                if (resolvedAccount == null) {
                    val autoDisplayName = AccountNormalizer.formatDisplayName(aiName, aiLast4)
                    val newAcc = AccountEntity(
                        bankName = aiName,
                        bankKey = aiKey,
                        last4 = aiLast4,
                        type = aiIdentified.accountType,
                        displayName = autoDisplayName,
                        balanceSource = BalanceSource.AUTO
                    )
                    val insertedId = accountDao.insertAccount(newAcc)
                    resolvedAccount = newAcc.copy(id = insertedId)
                    batchContext?.accounts?.add(resolvedAccount)
                }
                overallConfidence = minOf(overallConfidence, aiIdentified.confidence)
            }
        }

        // Final Fallback: Unassigned Account & Queue Review Item
        if (resolvedAccount == null) {
            var unassigned = batchContext?.accounts?.find { it.bankKey.equals("UNASSIGNED", ignoreCase = true) && it.last4 == "0000" }
                ?: accountDao.findByBankKeyAndLast4("UNASSIGNED", "0000")
            if (unassigned == null) {
                val newUnassigned = AccountEntity(
                    bankName = "Unassigned",
                    bankKey = "UNASSIGNED",
                    last4 = "0000",
                    type = AccountType.UNASSIGNED,
                    displayName = "Unassigned Account",
                    balanceSource = BalanceSource.MANUAL
                )
                val unassignedId = accountDao.insertAccount(newUnassigned)
                unassigned = newUnassigned.copy(id = unassignedId)
            }
            batchContext?.accounts?.add(unassigned)
            resolvedAccount = unassigned
            needsReview = true
            reviewReason = "Missing account identifier; assigned to Unassigned Account"
            overallConfidence = 0.40f

            // Queue review item
            queueReviewItem(
                payload = JSONObject().apply {
                    put("type", "ACCOUNT_ASSIGNMENT")
                    put("rawMerchant", merchantRaw ?: "")
                    put("amountPaise", amountPaise)
                    put("smsText", rawMessage?.body ?: "")
                }.toString(),
                reason = "Missing account identifier; assign an account",
                confidence = 0.40f,
                origin = ReviewOrigin.RULES
            )
        }

        // ─── 2. VENDORS CLEANING & WHOLE-WORD MATCHING ────────────────────────
        val cleanVendor = VendorResolver.cleanMerchantString(merchantRaw ?: "")
        val allVendors = batchContext?.vendors ?: vendorDao.getAllVendorsList()
        val matchedVendor = VendorResolver.findMatchingVendor(cleanVendor, merchantRaw ?: "", allVendors)

        var vendorDisplayName = matchedVendor?.displayName ?: cleanVendor
        var vendorMappedCatId = matchedVendor?.categoryId

        // ─── 3. RULES ENGINE EVALUATION ───────────────────────────────────────
        val candidateTx = TransactionEntity(
            amountPaise = amountPaise,
            type = transactionType,
            paymentMode = resolvedAccount.displayName,
            rawMerchant = merchantRaw ?: "",
            friendlyVendorName = vendorDisplayName,
            categoryId = vendorMappedCatId,
            timestamp = timestamp,
            accountId = resolvedAccount.id,
            editedFields = editedFields
        )

        val activeRules = batchContext?.activeRules ?: (ruleDao?.getActiveRulesSync() ?: emptyList())
        val allCategories = batchContext?.categories ?: categoryDao.getAllCategoriesList()
        val categoryMap = allCategories.associate { it.id to it.name }

        val ruleResult = RuleEngine.evaluateRules(
            transaction = candidateTx,
            rawSms = rawMessage?.body,
            account = resolvedAccount,
            rules = activeRules,
            categoryNameLookup = { id -> categoryMap[id] }
        )

        if (ruleResult.isBlocked) {
            return ResolvedEntities(
                account = resolvedAccount,
                vendorDisplayName = vendorDisplayName,
                categoryId = vendorMappedCatId,
                isBlocked = true,
                blockingRuleId = ruleResult.blockingRuleId
            )
        }

        // Rule overrides for merchant/vendor
        vendorDisplayName = ruleResult.modifiedTransaction.friendlyVendorName
        val finalTxType = ruleResult.modifiedTransaction.type

        // ─── 4. CATEGORIES PRECEDENCE ORDER ───────────────────────────────────
        // Order: user rules -> learned rules -> vendor mapping -> built-in keyword heuristic -> AI -> Uncategorized
        val finalCategoryId = if (finalTxType == TransactionType.INCOME ||
            finalTxType == TransactionType.CC_BILL_TRANSFER ||
            finalTxType == TransactionType.OWN_TRANSFER
        ) {
            null
        } else {
            resolveCategoryWithPrecedence(
                ruleCategoryName = ruleResult.targetCategoryName,
                vendorMappedCatId = vendorMappedCatId,
                cleanVendorName = vendorDisplayName,
                rawMerchant = merchantRaw,
                rawSmsText = rawMessage?.body,
                allCategories = allCategories,
                batchContext = batchContext,
                onAiPredicted = { aiConfidence, catName ->
                    overallConfidence = minOf(overallConfidence, aiConfidence)
                    val autoAccept = batchContext?.autoAcceptAi ?: (appPreferences?.isAutoAcceptAiResultsSync() ?: false)
                    if (!autoAccept) {
                        needsReview = true
                        reviewReason = "Category '$catName' predicted by Local AI"
                        queueReviewItem(
                            payload = JSONObject().apply {
                                put("type", "CATEGORY_PREDICTION")
                                put("vendor", vendorDisplayName)
                                put("predictedCategory", catName)
                                put("confidence", aiConfidence.toDouble())
                            }.toString(),
                            reason = "Category '$catName' predicted by AI",
                            confidence = aiConfidence,
                            origin = ReviewOrigin.AI
                        )
                    }
                }
            )
        }

        return ResolvedEntities(
            account = resolvedAccount,
            vendorDisplayName = vendorDisplayName,
            categoryId = finalCategoryId,
            isBlocked = false,
            confidence = overallConfidence,
            needsReview = needsReview,
            reviewReason = reviewReason
        )
    }

    private suspend fun resolveCategoryWithPrecedence(
        ruleCategoryName: String?,
        vendorMappedCatId: Long?,
        cleanVendorName: String,
        rawMerchant: String?,
        rawSmsText: String?,
        allCategories: List<CategoryEntity>,
        batchContext: IngestBatchContext? = null,
        onAiPredicted: suspend (Float, String) -> Unit
    ): Long? {
        // 1 & 2. Rules (User or Learned)
        if (!ruleCategoryName.isNullOrBlank()) {
            return findOrCreateCategory(ruleCategoryName, allCategories, batchContext)
        }

        // 3. Existing vendor mapped category
        if (vendorMappedCatId != null) {
            return vendorMappedCatId
        }

        // 4. Built-in keyword heuristic
        val lowerText = "$cleanVendorName ${rawMerchant ?: ""} ${rawSmsText ?: ""}".lowercase(Locale.US)
        val heuristicCategory = classifyHeuristically(lowerText)
        if (heuristicCategory != null) {
            return findOrCreateCategory(heuristicCategory, allCategories, batchContext)
        }

        // 5. Local AI Fallback
        if (aiFallbackService != null && aiFallbackService.isAiAvailable()) {
            val existingNames = allCategories.map { it.name }
            val aiCategory = aiFallbackService.categorizeVendor(cleanVendorName, existingNames)
            if (!aiCategory.isNullOrBlank() && !aiCategory.equals("Other", ignoreCase = true)) {
                onAiPredicted(AiFallbackService.MODEL_CONFIDENCE_CAP, aiCategory)
                return findOrCreateCategory(aiCategory, allCategories, batchContext)
            }
        }

        // 6. Uncategorized
        return null
    }

    private suspend fun findOrCreateCategory(
        categoryName: String,
        existingCategories: List<CategoryEntity>,
        batchContext: IngestBatchContext? = null
    ): Long {
        val existing = existingCategories.find { it.name.equals(categoryName.trim(), ignoreCase = true) }
            ?: categoryDao.getCategoryByName(categoryName.trim())

        if (existing != null) {
            return existing.id
        }

        // Category created on first use with automatic color and NO budget (budgetPaise null)
        val colorIndex = Math.abs(categoryName.hashCode()) % categoryColors.size
        val autoColor = categoryColors[colorIndex]

        val newCat = CategoryEntity(
            name = categoryName.trim(),
            color = autoColor,
            budgetPaise = null // NO budget, NO alerts until explicitly set
        )
        val insertedId = categoryDao.insertCategory(newCat)
        val savedCat = newCat.copy(id = insertedId)
        batchContext?.categories?.add(savedCat)
        return insertedId
    }

    private fun classifyHeuristically(lower: String): String? {
        return when {
            containsAny(lower, "swiggy", "zomato", "restaurant", "cafe", "food", "dining", "mess", "canteen", "baker", "eat", "kitchen", "burger", "pizza", "biryani", "dhaba", "subway", "kfc", "mcdonald", "starbucks") -> "Food & Dining"
            containsAny(lower, "uber", "ola", "metro", "irctc", "rail", "flight", "indigo", "petrol", "fuel", "fastag", "toll", "auto", "cab", "transport", "airline", "airways", "hpcl", "bpcl", "iocl") -> "Travel"
            containsAny(lower, "blinkit", "zepto", "instamart", "supermarket", "grocery", "mart", "store", "kirana", "basket", "provisions", "dmart", "reliance fresh") -> "Groceries"
            containsAny(lower, "bookmyshow", "cinema", "netflix", "spotify", "prime", "movie", "game", "theatre", "entertainment", "hotstar", "youtube", "pvr", "inox") -> "Entertainment"
            containsAny(lower, "pharmacy", "apollo", "medplus", "hospital", "clinic", "health", "dr.", "medical", "chemist", "diagnostic", "1mg", "practo") -> "Healthcare"
            containsAny(lower, "amazon", "flipkart", "myntra", "ajio", "zara", "clothing", "apparel", "shopping", "retail", "fashion", "h&m", "uniqlo") -> "Shopping"
            containsAny(lower, "recharge", "airtel", "jio", "electricity", "bescom", "broadband", "wifi", "bill", "water", "gas", "utility", "billdesk", "tneb") -> "Utilities"
            containsAny(lower, "college", "fees", "books", "tuition", "campus", "school", "university", "stationery", "xerox", "udemy", "coursera") -> "Education"
            containsAny(lower, "chai", "tea", "smoke", "pan shop", "paan", "tapri", "sutta") -> "Chai & Smoke"
            else -> null
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    private suspend fun queueReviewItem(payload: String, reason: String, confidence: Float, origin: ReviewOrigin) {
        try {
            reviewItemDao?.insertReviewItem(
                ReviewItemEntity(
                    payloadJson = payload,
                    reason = reason,
                    confidence = confidence,
                    origin = origin,
                    status = ReviewStatus.PENDING
                )
            )
        } catch (_: Exception) {}
    }
}
