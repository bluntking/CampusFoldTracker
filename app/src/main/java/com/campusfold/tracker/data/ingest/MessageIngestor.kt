package com.campusfold.tracker.data.ingest

import android.content.Context
import androidx.room.withTransaction
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.data.resolver.EntityResolver
import com.campusfold.tracker.parser.MessageExtractor
import com.campusfold.tracker.parser.model.*
import com.campusfold.tracker.util.BudgetAlertEvaluator
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

sealed class IngestResult {
    data class SkippedAlreadyProcessed(val bodyHash: String) : IngestResult()
    data class Ignored(val reason: String, val bodyHash: String) : IngestResult()
    data class BalanceUpdated(val accountId: Long, val newBalancePaise: Long, val bodyHash: String) : IngestResult()
    data class Imported(val transactionId: Long, val bodyHash: String) : IngestResult()
    data class Enriched(val transactionId: Long, val bodyHash: String) : IngestResult()
    data class Unparsed(val unparsedId: Long, val bodyHash: String) : IngestResult()
}

class MessageIngestor(
    private val db: AppDatabase,
    private val entityResolver: EntityResolver,
    private val repository: TransactionRepository? = null,
    private val context: Context? = null,
    private val appPreferences: AppPreferences? = null,
    private val aiFallbackService: com.campusfold.tracker.ai.AiFallbackService? = null
) {
    companion object {
        fun computeBodyHash(body: String, timestamp: Long = 0L, sourceId: String? = null): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val payload = if (timestamp != 0L || !sourceId.isNullOrBlank()) {
                "${body.trim()}|$timestamp|${sourceId ?: ""}"
            } else {
                body.trim()
            }
            val hashBytes = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun ingest(rawMessage: RawMessage): IngestResult {
        return try {
            db.withTransaction {
                ingestInternal(rawMessage)
            }
        } catch (_: Exception) {
            ingestInternal(rawMessage)
        }
    }

    suspend fun ingestBatch(
        messages: List<RawMessage>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<IngestResult> {
        val results = mutableListOf<IngestResult>()
        val batchContext = IngestBatchContext(
            accounts = db.accountDao().getAllAccountsSync().toMutableList(),
            recentTransactions = db.transactionDao().getRecentTransactionsSync(20),
            vendors = db.vendorDao().getAllVendorsList().toMutableList(),
            categories = db.categoryDao().getAllCategoriesList().toMutableList(),
            activeRules = db.ruleDao().getActiveRulesSync(),
            autoAcceptAi = appPreferences?.isAutoAcceptAiResultsSync() ?: false
        )
        val chunkSize = 50
        messages.chunked(chunkSize).forEach { chunk ->
            val chunkResults = try {
                db.withTransaction {
                    chunk.map { msg -> ingestInternal(msg, batchContext) }
                }
            } catch (_: Exception) {
                chunk.map { msg -> ingestInternal(msg, batchContext) }
            }
            results.addAll(chunkResults)
            onProgress?.invoke(results.size, messages.size)
        }
        return results
    }

    private suspend fun ingestInternal(
        rawMessage: RawMessage,
        batchContext: IngestBatchContext? = null
    ): IngestResult {
        val bodyHash = computeBodyHash(rawMessage.body, rawMessage.timestamp, rawMessage.sourceId)

        // 1. Skip if bodyHash already in processed_messages
        if (db.processedMessageDao().isProcessed(bodyHash)) {
            return IngestResult.SkippedAlreadyProcessed(bodyHash)
        }

        // 2. MessageExtractor
        val accounts = batchContext?.accounts ?: db.accountDao().getAllAccountsSync()
        val recentTx = batchContext?.recentTransactions ?: db.transactionDao().getRecentTransactionsSync(20)
        val extractionResult = MessageExtractor.extract(rawMessage, accounts, recentTx)

        return when (extractionResult) {
            is ExtractionResult.Ignored -> {
                db.processedMessageDao().insertProcessed(
                    ProcessedMessageEntity(
                        bodyHash = bodyHash,
                        source = rawMessage.source.name,
                        sourceId = rawMessage.sourceId,
                        outcome = "IGNORED",
                        processedAt = System.currentTimeMillis()
                    )
                )
                IngestResult.Ignored(extractionResult.reason, bodyHash)
            }

            is ExtractionResult.BalanceOnly -> {
                val update = extractionResult.balanceUpdate
                val account = resolveAccountForBalance(update, accounts)
                if (account != null && account.balanceSource != BalanceSource.MANUAL) {
                    if (update.timestamp >= account.balanceAsOf) {
                        db.accountDao().updateBalance(account.id, update.balancePaise, update.timestamp, BalanceSource.AUTO)
                    }
                }
                db.processedMessageDao().insertProcessed(
                    ProcessedMessageEntity(
                        bodyHash = bodyHash,
                        source = rawMessage.source.name,
                        sourceId = rawMessage.sourceId,
                        outcome = "BALANCE_UPDATED",
                        processedAt = System.currentTimeMillis()
                    )
                )
                IngestResult.BalanceUpdated(account?.id ?: 0L, update.balancePaise, bodyHash)
            }

            is ExtractionResult.Unparsed -> {
                val unparsedEntity = UnparsedMessageEntity(
                    source = rawMessage.source.name,
                    sender = rawMessage.sender,
                    receivedAt = rawMessage.timestamp,
                    body = rawMessage.body,
                    bodyHash = bodyHash
                )
                val unparsedId = db.unparsedMessageDao().insertMessage(unparsedEntity)

                var aiHandled = false
                if (aiFallbackService != null && aiFallbackService.isAiAvailable()) {
                    val aiTx = aiFallbackService.parseUnstructuredMessage(rawMessage.body)
                    if (aiTx != null) {
                        val autoAccept = appPreferences?.isAutoAcceptAiResultsSync() ?: false
                        if (autoAccept) {
                            val amountPaise = com.campusfold.tracker.util.CurrencyUtils.rupeesToPaise(aiTx.amount)
                            val resolved = entityResolver.resolve(
                                bankName = aiTx.bankName,
                                bankKey = null,
                                accountLast4 = aiTx.accountLast4,
                                instrumentType = null,
                                merchantRaw = aiTx.merchant,
                                direction = if (aiTx.type == TransactionType.INCOME) Direction.CREDIT else Direction.DEBIT,
                                transactionType = aiTx.type,
                                rawMessage = rawMessage,
                                amountPaise = amountPaise,
                                timestamp = rawMessage.timestamp,
                                batchContext = batchContext
                            )
                            if (!resolved.isBlocked) {
                                val tx = TransactionEntity(
                                    amountPaise = amountPaise,
                                    myActualSharePaise = amountPaise,
                                    type = aiTx.type,
                                    paymentMode = resolved.account.displayName,
                                    rawMerchant = aiTx.merchant,
                                    friendlyVendorName = resolved.vendorDisplayName,
                                    categoryId = resolved.categoryId,
                                    timestamp = rawMessage.timestamp,
                                    accountId = resolved.account.id,
                                    source = if (rawMessage.source == MessageSource.SMS) TransactionSource.SMS else TransactionSource.EMAIL,
                                    confidence = aiTx.confidence,
                                    rawMessageHash = bodyHash
                                )
                                val insertedId = db.transactionDao().insertTransaction(tx)
                                db.processedMessageDao().insertProcessed(
                                    ProcessedMessageEntity(
                                        bodyHash = bodyHash,
                                        source = rawMessage.source.name,
                                        sourceId = rawMessage.sourceId,
                                        outcome = "IMPORTED_VIA_AI",
                                        processedAt = System.currentTimeMillis()
                                    )
                                )
                                return IngestResult.Imported(insertedId, bodyHash)
                            }
                        } else {
                            val payload = org.json.JSONObject().apply {
                                put("amount", aiTx.amount)
                                put("type", aiTx.type.name)
                                put("merchant", aiTx.merchant)
                                put("bankName", aiTx.bankName ?: "")
                                put("accountLast4", aiTx.accountLast4 ?: "")
                                put("rawText", rawMessage.body)
                            }.toString()
                            db.reviewItemDao().insertReviewItem(
                                ReviewItemEntity(
                                    payloadJson = payload,
                                    reason = "AI parsed transaction from unstructured message",
                                    confidence = aiTx.confidence,
                                    origin = ReviewOrigin.AI,
                                    status = ReviewStatus.PENDING
                                )
                            )
                            aiHandled = true
                        }
                    }
                }

                if (!aiHandled) {
                    val payload = org.json.JSONObject().apply {
                        put("rawText", rawMessage.body)
                        put("sender", rawMessage.sender)
                        put("timestamp", rawMessage.timestamp)
                    }.toString()
                    db.reviewItemDao().insertReviewItem(
                        ReviewItemEntity(
                            payloadJson = payload,
                            reason = "Unparsed message requiring review",
                            confidence = 0.0f,
                            origin = ReviewOrigin.MANUAL,
                            status = ReviewStatus.PENDING
                        )
                    )
                }

                db.processedMessageDao().insertProcessed(
                    ProcessedMessageEntity(
                        bodyHash = bodyHash,
                        source = rawMessage.source.name,
                        sourceId = rawMessage.sourceId,
                        outcome = "UNPARSED",
                        processedAt = System.currentTimeMillis()
                    )
                )
                IngestResult.Unparsed(unparsedId, bodyHash)
            }

            is ExtractionResult.Transaction -> {
                val extracted = extractionResult.transaction
                val resolved = entityResolver.resolve(
                    bankName = extracted.bankName,
                    bankKey = null,
                    accountLast4 = extracted.accountLast4,
                    instrumentType = extracted.instrumentType,
                    merchantRaw = extracted.merchantRaw,
                    direction = extracted.direction,
                    transactionType = extracted.type,
                    rawMessage = rawMessage,
                    amountPaise = extracted.amountPaise,
                    timestamp = extracted.timestamp,
                    batchContext = batchContext
                )

                if (resolved.isBlocked) {
                    db.processedMessageDao().insertProcessed(
                        ProcessedMessageEntity(
                            bodyHash = bodyHash,
                            source = rawMessage.source.name,
                            sourceId = rawMessage.sourceId,
                            outcome = "BLOCKED_BY_RULE",
                            processedAt = System.currentTimeMillis()
                        )
                    )
                    return IngestResult.Ignored("Blocked by rule #${resolved.blockingRuleId}", bodyHash)
                }

                val account = resolved.account
                val vendorName = resolved.vendorDisplayName
                val categoryId = resolved.categoryId

                // 3. Deduplicate
                var existingTx: TransactionEntity? = null
                if (!extracted.reference.isNullOrBlank()) {
                    existingTx = db.transactionDao().findByReference(extracted.reference!!)
                        ?: db.transactionDao().findByUpiRef(extracted.reference!!)
                }
                if (existingTx == null) {
                    val twoMinutes = 120_000L
                    val minTime = extracted.timestamp - twoMinutes
                    val maxTime = extracted.timestamp + twoMinutes
                    val candidates = db.transactionDao().findDuplicatesByAccountAmountTime(
                        accountId = account.id,
                        paymentMode = account.displayName,
                        amountPaise = extracted.amountPaise,
                        minTime = minTime,
                        maxTime = maxTime
                    )
                    existingTx = candidates.firstOrNull { cand ->
                        cand.type == extracted.type || (cand.type.name == extracted.type.name)
                    }
                }

                if (existingTx != null) {
                    // Enrich existing transaction
                    val editedFields = existingTx.editedFields

                    // Check if direction was previously wrong (e.g. credit saved as expense) and not user-edited
                    val existingIsDebit = existingTx.type == TransactionType.EXPENSE || existingTx.type == TransactionType.ATM || existingTx.type == TransactionType.EMI || existingTx.type == TransactionType.CC_BILL_TRANSFER
                    val existingIsCredit = existingTx.type == TransactionType.INCOME || existingTx.type == TransactionType.REFUND || existingTx.type == TransactionType.SPLIT_SETTLEMENT
                    val extractedIsCredit = extracted.direction == Direction.CREDIT || extracted.type == TransactionType.INCOME
                    val extractedIsDebit = extracted.direction == Direction.DEBIT || extracted.type == TransactionType.EXPENSE
                    val isOppositeDirection = (existingIsDebit && extractedIsCredit) || (existingIsCredit && extractedIsDebit)

                    val newType = if (isOppositeDirection && !editedFields.contains("type")) {
                        extracted.type
                    } else {
                        existingTx.type
                    }

                    val newVendor = if (isOppositeDirection && !editedFields.contains("friendlyVendorName") && (existingTx.friendlyVendorName.isBlank() || existingTx.friendlyVendorName.contains("Debit") || existingTx.friendlyVendorName.contains("Credit") || existingTx.friendlyVendorName == "Merchant")) {
                        vendorName
                    } else existingTx.friendlyVendorName

                    val newMerchant = if (isOppositeDirection && !editedFields.contains("rawMerchant") && (existingTx.rawMerchant.isBlank() || existingTx.rawMerchant.contains("Debit") || existingTx.rawMerchant.contains("Credit") || existingTx.rawMerchant == "Merchant")) {
                        extracted.merchantRaw ?: extracted.bankName ?: "Merchant"
                    } else existingTx.rawMerchant

                    val newSharePaise = if (isOppositeDirection && !editedFields.contains("myActualSharePaise")) {
                        extracted.amountPaise
                    } else existingTx.myActualSharePaise

                    val newRef = if (!editedFields.contains("reference") && existingTx.reference.isNullOrBlank()) extracted.reference else existingTx.reference
                    val newUpi = if (!editedFields.contains("upiRef") && existingTx.upiRef.isNullOrBlank()) extracted.reference else existingTx.upiRef
                    val newSms = if (!editedFields.contains("rawSms") && existingTx.rawSms.isNullOrBlank() && rawMessage.source == MessageSource.SMS) rawMessage.body else existingTx.rawSms
                    val newDesc = if (!editedFields.contains("description") && rawMessage.source == MessageSource.EMAIL) {
                        if (existingTx.description.isNullOrBlank()) "Email alert: ${rawMessage.sender}" else "${existingTx.description} | Email: ${rawMessage.sender}"
                    } else existingTx.description
                    val newAccountId = if (!editedFields.contains("accountId") && existingTx.accountId == null && account.type != AccountType.UNASSIGNED) account.id else existingTx.accountId
                    val newPaymentMode = if (!editedFields.contains("paymentMode") && (existingTx.paymentMode == "Cash/Manual" || existingTx.paymentMode.isBlank()) && account.type != AccountType.UNASSIGNED) account.displayName else existingTx.paymentMode
                    val newHash = existingTx.rawMessageHash ?: bodyHash

                    val enriched = existingTx.copy(
                        type = newType,
                        friendlyVendorName = newVendor,
                        rawMerchant = newMerchant,
                        myActualSharePaise = newSharePaise,
                        reference = newRef,
                        upiRef = newUpi,
                        rawSms = newSms,
                        description = newDesc,
                        accountId = newAccountId,
                        paymentMode = newPaymentMode,
                        rawMessageHash = newHash
                    )
                    db.transactionDao().updateTransaction(enriched)

                    // If type changed to INCOME, handle income settlement
                    if (isOppositeDirection && newType == TransactionType.INCOME && repository != null) {
                        handleIncomeSettlement(existingTx.id, enriched)
                    }

                    // Bank reported balance handling (Part 6)
                    if (extracted.balancePaise != null) {
                        val autoUse = appPreferences?.isAutoUseBankBalancesSync() ?: false
                        if (autoUse && extracted.timestamp >= account.balanceAsOf) {
                            db.accountDao().updateBalance(account.id, extracted.balancePaise, extracted.timestamp, BalanceSource.AUTO)
                        }
                    }

                    db.processedMessageDao().insertProcessed(
                        ProcessedMessageEntity(
                            bodyHash = bodyHash,
                            source = rawMessage.source.name,
                            sourceId = rawMessage.sourceId,
                            outcome = "DUPLICATE_ENRICHED",
                            processedAt = System.currentTimeMillis()
                        )
                    )
                    IngestResult.Enriched(existingTx.id, bodyHash)
                } else {
                    // New transaction
                    val source = if (rawMessage.source == MessageSource.SMS) TransactionSource.SMS else TransactionSource.EMAIL
                    val desc = if (rawMessage.source == MessageSource.EMAIL) "Email alert: ${rawMessage.sender}" else null
                    val newTx = TransactionEntity(
                        amountPaise = extracted.amountPaise,
                        myActualSharePaise = extracted.amountPaise,
                        type = extracted.type,
                        paymentMode = account.displayName,
                        rawMerchant = extracted.merchantRaw ?: extracted.bankName ?: "Merchant",
                        friendlyVendorName = vendorName,
                        categoryId = categoryId,
                        timestamp = extracted.timestamp,
                        upiRef = extracted.reference,
                        reference = extracted.reference,
                        rawSms = if (rawMessage.source == MessageSource.SMS) rawMessage.body else null,
                        description = desc,
                        accountId = account.id,
                        source = source,
                        confidence = extracted.confidence,
                        rawMessageHash = bodyHash
                    )
                    val insertedTxId = db.transactionDao().insertTransaction(newTx)
                    if (batchContext != null && insertedTxId > 0) {
                        batchContext.recentTransactions = listOf(newTx.copy(id = insertedTxId)) + batchContext.recentTransactions.take(19)
                    }

                    // Income / Friend Split Settlement handling
                    if (extracted.type == TransactionType.INCOME && insertedTxId > 0 && repository != null) {
                        handleIncomeSettlement(insertedTxId, newTx)
                    } else if (extracted.type == TransactionType.EXPENSE && insertedTxId > 0 && repository != null) {
                        handleOutgoingRepaymentSuggestion(insertedTxId, newTx)
                    }

                    // Bank reported balance handling (Part 6)
                    if (extracted.balancePaise != null) {
                        val autoUse = appPreferences?.isAutoUseBankBalancesSync() ?: false
                        if (autoUse && extracted.timestamp >= account.balanceAsOf) {
                            db.accountDao().updateBalance(account.id, extracted.balancePaise, extracted.timestamp, BalanceSource.AUTO)
                        }
                    }

                    // Budget alert evaluation
                    if (extracted.type == TransactionType.EXPENSE && context != null && appPreferences != null) {
                        try {
                            BudgetAlertEvaluator.evaluateBudgetAlerts(context, db, appPreferences, categoryId)
                        } catch (_: Exception) {}
                    }

                    db.processedMessageDao().insertProcessed(
                        ProcessedMessageEntity(
                            bodyHash = bodyHash,
                            source = rawMessage.source.name,
                            sourceId = rawMessage.sourceId,
                            outcome = "IMPORTED",
                            processedAt = System.currentTimeMillis()
                        )
                    )
                    IngestResult.Imported(insertedTxId, bodyHash)
                }
            }
        }
    }

    private suspend fun resolveAccountForBalance(update: BalanceUpdate, accounts: List<AccountEntity>): AccountEntity? {
        val last4 = update.accountLast4?.trim()
        val bankName = update.bankName?.trim()

        if (!last4.isNullOrBlank()) {
            val matchedByLast4 = accounts.find { it.last4.endsWith(last4) }
            if (matchedByLast4 != null) return matchedByLast4
        }
        if (!bankName.isNullOrBlank()) {
            val matchedByName = accounts.find { it.bankName.equals(bankName, ignoreCase = true) || it.bankKey.equals(bankName, ignoreCase = true) }
            if (matchedByName != null) return matchedByName
        }
        return null
    }

    private suspend fun handleIncomeSettlement(txId: Long, tx: TransactionEntity) {
        val persons = db.personDao().getAllPersonsList()
        val senderClean = tx.rawMerchant.trim()
        var handledExact = false

        for (person in persons) {
            val pending = db.splitItemDao().getNetPendingBalanceForPersonSync(person.id)
            if (pending <= 0L) continue

            val aliases = person.exactSmsAliases.split(",")
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.isNotEmpty() }

            if (aliases.any { it.equals(senderClean, ignoreCase = true) }) {
                repository?.settleAmountForPersonPaise(person.id, tx.amountPaise)
                db.transactionDao().updateTransaction(
                    tx.copy(
                        id = txId,
                        type = TransactionType.SPLIT_SETTLEMENT,
                        friendlyVendorName = "Settlement from ${person.name}"
                    )
                )
                handledExact = true
                break
            }
        }

        if (!handledExact) {
            for (person in persons) {
                val pending = db.splitItemDao().getNetPendingBalanceForPersonSync(person.id)
                if (pending <= 0L) continue

                val firstName = person.name.split(" ").firstOrNull()?.trim()?.lowercase(Locale.US) ?: ""
                val lowerSender = senderClean.lowercase(Locale.US)
                val aliases = person.exactSmsAliases.split(",")
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { it.isNotEmpty() }

                val isFuzzy = (firstName.length >= 3 && lowerSender.contains(firstName)) ||
                        aliases.any { alias -> alias.length >= 3 && (lowerSender.contains(alias) || alias.contains(lowerSender)) }

                if (isFuzzy) {
                    db.pendingSettlementSuggestionDao().insertSuggestion(
                        PendingSettlementSuggestionEntity(
                            transactionId = txId,
                            suggestedPersonId = person.id,
                            detectedSenderName = senderClean,
                            amountPaise = tx.amountPaise,
                            status = SettlementSuggestionStatus.PENDING,
                            direction = SplitDirection.OWED_TO_ME
                        )
                    )
                    break
                }
            }
        }
    }

    private suspend fun handleOutgoingRepaymentSuggestion(txId: Long, tx: TransactionEntity) {
        val persons = db.personDao().getAllPersonsList()
        val counterpartyClean = tx.rawMerchant.trim()
        val lowerCounterparty = counterpartyClean.lowercase(Locale.US)
        if (counterpartyClean.isEmpty()) return

        for (person in persons) {
            val unsettledSplits = db.splitItemDao().getUnsettledSplitsForPersonAndDirectionSync(person.id, SplitDirection.OWED_BY_ME)
            val pendingOwedByMe = unsettledSplits.sumOf { it.amountOwedPaise - it.amountPaidSoFarPaise }
            if (pendingOwedByMe <= 0L) continue

            val aliases = person.exactSmsAliases.split(",")
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.isNotEmpty() }
            val name = person.name.trim().lowercase(Locale.US)
            val firstName = person.name.split(" ").firstOrNull()?.trim()?.lowercase(Locale.US) ?: ""

            val matches = aliases.any { alias -> alias.length >= 3 && (lowerCounterparty.contains(alias) || alias.contains(lowerCounterparty)) } ||
                    (name.length >= 3 && (lowerCounterparty.contains(name) || name.contains(lowerCounterparty))) ||
                    (firstName.length >= 3 && lowerCounterparty.contains(firstName))

            if (matches) {
                db.pendingSettlementSuggestionDao().insertSuggestion(
                    PendingSettlementSuggestionEntity(
                        transactionId = txId,
                        suggestedPersonId = person.id,
                        detectedSenderName = counterpartyClean,
                        amountPaise = tx.amountPaise,
                        status = SettlementSuggestionStatus.PENDING,
                        direction = SplitDirection.OWED_BY_ME
                    )
                )
                break
            }
        }
    }
}
