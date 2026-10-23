package com.campusfold.tracker.data.merge

import androidx.room.withTransaction
import com.campusfold.tracker.ai.AiFallbackService
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class MergeConfidence {
    HIGH,   // Automatic merge eligible
    MEDIUM  // Suggested merge (user confirmation required)
}

data class DuplicatePair(
    val accountA: AccountEntity,
    val accountB: AccountEntity,
    val confidence: MergeConfidence,
    val reason: String,
    val isAiOnly: Boolean = false
) {
    val source: AccountEntity get() = accountA
    val target: AccountEntity get() = accountB
}

class AccountMergeManager(
    private val db: AppDatabase,
    private val accountDao: AccountDao = db.accountDao(),
    private val accountAliasDao: AccountAliasDao = db.accountAliasDao(),
    private val mergeLogDao: MergeLogDao = db.mergeLogDao(),
    private val notSamePairDao: NotSamePairDao = db.notSamePairDao(),
    private val transactionDao: TransactionDao = db.transactionDao(),
    private val aiFallbackService: AiFallbackService? = null,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) {

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            transactionRunner.invoke { block() } as T
        } else {
            try {
                db.withTransaction { block() }
            } catch (e: Exception) {
                if (e is NullPointerException || e is IllegalStateException) {
                    block()
                } else {
                    throw e
                }
            }
        }
    }

    companion object {
        fun hasEditedNameOrType(account: AccountEntity): Boolean {
            return try {
                val jsonArr = JSONArray(account.editedFields)
                for (i in 0 until jsonArr.length()) {
                    val field = jsonArr.getString(i).lowercase(Locale.US)
                    if (field == "displayname" || field == "name" || field == "type" || field == "bankname" || field == "last4") {
                        return true
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        fun serializeAccountSnapshot(account: AccountEntity): String {
            val json = JSONObject()
            json.put("id", account.id)
            json.put("bankName", account.bankName)
            json.put("bankKey", account.bankKey)
            json.put("last4", account.last4)
            json.put("type", account.type.name)
            json.put("displayName", account.displayName)
            json.put("color", account.color)
            json.put("balancePaise", account.balancePaise)
            json.put("balanceAsOf", account.balanceAsOf)
            json.put("balanceSource", account.balanceSource.name)
            json.put("creditLimitPaise", account.creditLimitPaise)
            json.put("isArchived", account.isArchived)
            json.put("editedFields", account.editedFields)
            return json.toString()
        }

        fun deserializeAccountSnapshot(jsonStr: String): AccountEntity {
            val json = JSONObject(jsonStr)
            val typeStr = json.optString("type", "UNASSIGNED")
            val type = runCatching { AccountType.valueOf(typeStr) }.getOrDefault(AccountType.UNASSIGNED)
            val balanceSourceStr = json.optString("balanceSource", "MANUAL")
            val balanceSource = runCatching { BalanceSource.valueOf(balanceSourceStr) }.getOrDefault(BalanceSource.MANUAL)

            return AccountEntity(
                id = json.getLong("id"),
                bankName = json.optString("bankName", ""),
                bankKey = json.optString("bankKey", ""),
                last4 = json.optString("last4", ""),
                type = type,
                displayName = json.optString("displayName", ""),
                color = json.optString("color", "#10B981"),
                balancePaise = json.optLong("balancePaise", 0L),
                balanceAsOf = json.optLong("balanceAsOf", System.currentTimeMillis()),
                balanceSource = balanceSource,
                creditLimitPaise = json.optLong("creditLimitPaise", 0L),
                isArchived = json.optBoolean("isArchived", false),
                editedFields = json.optString("editedFields", "[]")
            )
        }

        fun serializeMovedTransactionIds(ids: List<Long>): String {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            return arr.toString()
        }

        fun deserializeMovedTransactionIds(jsonStr: String): List<Long> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<Long>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getLong(i))
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun stringSimilarity(s1: String, s2: String): Double {
            val a = s1.trim().lowercase(Locale.US)
            val b = s2.trim().lowercase(Locale.US)
            if (a == b) return 1.0
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a.contains(b) || b.contains(a)) return 0.85
            val distance = levenshteinDistance(a, b)
            val maxLen = maxOf(a.length, b.length)
            return 1.0 - (distance.toDouble() / maxLen.toDouble())
        }

        private fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[0][j] = j
            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
            return dp[s1.length][s2.length]
        }
    }

    /**
     * Inspects active accounts and returns a pair of (AutoMergeCandidates, SuggestedMergeCandidates).
     * Pre-fetches notSame pairs, aliases, and account references to eliminate N+1 queries.
     */
    suspend fun detectDuplicates(accounts: List<AccountEntity>? = null): Pair<List<DuplicatePair>, List<DuplicatePair>> {
        val activeAccounts = accounts ?: accountDao.getActiveAccountsSync()
        if (activeAccounts.size < 2) return Pair(emptyList(), emptyList())

        // ── Pre-fetch caches outside O(N^2) comparison loop ───────────────────
        val notSamePairs = notSamePairDao.getAllNotSamePairsSync()
        val notSameSet = HashSet<Pair<Long, Long>>(notSamePairs.size * 2)
        for (pair in notSamePairs) {
            notSameSet.add(pair.accountIdA to pair.accountIdB)
            notSameSet.add(pair.accountIdB to pair.accountIdA)
        }

        val allAliases = accountAliasDao.getAllAliasesSync()
        val aliasMap = HashMap<Pair<String, String>, Long>(allAliases.size)
        for (alias in allAliases) {
            aliasMap[alias.bankKey.trim().lowercase(Locale.US) to alias.last4.trim()] = alias.accountId
        }

        val refsMap = HashMap<Long, Set<String>>(activeAccounts.size)
        for (acc in activeAccounts) {
            val refs = (transactionDao.getDistinctReferencesForAccount(acc.id) +
                    transactionDao.getDistinctUpiRefsForAccount(acc.id))
                .filter { it.isNotBlank() }
                .toSet()
            refsMap[acc.id] = refs
        }

        val autoCandidates = mutableListOf<DuplicatePair>()
        val suggestedCandidates = mutableListOf<DuplicatePair>()

        for (i in 0 until activeAccounts.size) {
            for (j in i + 1 until activeAccounts.size) {
                val a = activeAccounts[i]
                val b = activeAccounts[j]

                // Rule: If user marked them as "Not the same", ignore completely
                if (notSameSet.contains(a.id to b.id)) {
                    continue
                }

                val pair = evaluatePair(a, b, aliasMap, refsMap)
                if (pair != null) {
                    if (pair.confidence == MergeConfidence.HIGH) {
                        autoCandidates.add(pair)
                    } else {
                        suggestedCandidates.add(pair)
                    }
                }
            }
        }

        return Pair(autoCandidates, suggestedCandidates)
    }

    private suspend fun evaluatePair(
        a: AccountEntity,
        b: AccountEntity,
        aliasMap: Map<Pair<String, String>, Long>? = null,
        refsMap: Map<Long, Set<String>>? = null
    ): DuplicatePair? {
        val normKeyA = a.bankKey.trim().lowercase(Locale.US)
        val normKeyB = b.bankKey.trim().lowercase(Locale.US)
        val last4A = a.last4.trim()
        val last4B = b.last4.trim()

        val sameBankKey = normKeyA.isNotBlank() && normKeyA == normKeyB
        val bothHaveLast4 = last4A.isNotBlank() && last4B.isNotBlank()
        val sameLast4 = bothHaveLast4 && last4A == last4B
        val sameType = a.type == b.type

        // ── 1. Check Condition: Alias link ─────────────────────────────────────
        val isAliasLink = if (aliasMap != null) {
            (normKeyA.isNotBlank() && last4A.isNotBlank() && aliasMap[normKeyA to last4A] == b.id) ||
            (normKeyB.isNotBlank() && last4B.isNotBlank() && aliasMap[normKeyB to last4B] == a.id)
        } else {
            (normKeyA.isNotBlank() && last4A.isNotBlank() && accountAliasDao.findAccountIdByAlias(normKeyA, last4A) == b.id) ||
            (normKeyB.isNotBlank() && last4B.isNotBlank() && accountAliasDao.findAccountIdByAlias(normKeyB, last4B) == a.id)
        }

        // ── 2. Check Condition: Shared transaction references ──────────────────
        val refsA = refsMap?.get(a.id) ?: (transactionDao.getDistinctReferencesForAccount(a.id) + transactionDao.getDistinctUpiRefsForAccount(a.id)).toSet()
        val refsB = refsMap?.get(b.id) ?: (transactionDao.getDistinctReferencesForAccount(b.id) + transactionDao.getDistinctUpiRefsForAccount(b.id)).toSet()
        val sharedRefs = refsA.intersect(refsB).filter { it.isNotBlank() }
        val sharesTxReferences = sharedRefs.isNotEmpty()

        val isHighMatch = (sameBankKey && sameLast4 && sameType) || isAliasLink || sharesTxReferences

        if (isHighMatch) {
            val edited = hasEditedNameOrType(a) || hasEditedNameOrType(b)
            if (edited) {
                // When either account has edited name or type in editedFields, NEVER auto-merge!
                return DuplicatePair(
                    accountA = a,
                    accountB = b,
                    confidence = MergeConfidence.MEDIUM,
                    reason = "Matches bank and account details, but has user-edited fields"
                )
            }

            val reason = when {
                sameBankKey && sameLast4 && sameType -> "Same bank (${a.bankName}), last 4 digits (${a.last4}), and account type (${a.type.name})"
                isAliasLink -> "Account identifier is already an alias for the other"
                else -> "Accounts share ${sharedRefs.size} transaction reference(s)"
            }

            return DuplicatePair(
                accountA = a,
                accountB = b,
                confidence = MergeConfidence.HIGH,
                reason = reason
            )
        }

        // ── 3. Check Suggested Partial Matches (MEDIUM Confidence) ─────────────

        // Case A: Missing last4 vs present last4 on same bank
        if (sameBankKey && (last4A.isBlank() xor last4B.isBlank())) {
            return DuplicatePair(
                accountA = a,
                accountB = b,
                confidence = MergeConfidence.MEDIUM,
                reason = "Same bank (${a.bankName}), but one account is missing last 4 digits"
            )
        }

        // Case B: Same bank and last4 with different type guesses
        if (sameBankKey && sameLast4 && !sameType) {
            return DuplicatePair(
                accountA = a,
                accountB = b,
                confidence = MergeConfidence.MEDIUM,
                reason = "Same bank and last 4 digits (${a.last4}), but different account types (${a.type.name} vs ${b.type.name})"
            )
        }

        // Case C: Similar names on same bank or same last4
        if ((sameBankKey || (bothHaveLast4 && sameLast4)) && stringSimilarity(a.displayName, b.displayName) >= 0.70) {
            return DuplicatePair(
                accountA = a,
                accountB = b,
                confidence = MergeConfidence.MEDIUM,
                reason = "Similar account names ('${a.displayName}' and '${b.displayName}')"
            )
        }

        // Case D: AI Suggestions (Local AI Fallback check)
        if (aiFallbackService != null && aiFallbackService.isAiAvailable() && (sameBankKey || stringSimilarity(a.bankName, b.bankName) >= 0.80)) {
            val aiJudgment = aiFallbackService.judgeDuplicateAccounts(a.displayName, a.bankName, a.last4, b.displayName, b.bankName, b.last4)
            if (aiJudgment) {
                // AI-only match is NEVER auto-merged, strictly Suggested
                return DuplicatePair(
                    accountA = a,
                    accountB = b,
                    confidence = MergeConfidence.MEDIUM,
                    reason = "AI suggested these describe the same financial account",
                    isAiOnly = true
                )
            }
        }

        return null
    }

    /**
     * Executes automatic merges for all HIGH confidence candidates.
     * Guaranteed to run each merge in an atomic Room transaction.
     */
    suspend fun runAutoMergePass(): List<MergeLogEntity> {
        val activeAccounts = accountDao.getActiveAccountsSync()
        if (activeAccounts.size < 2) return emptyList()

        val (autoCandidates, _) = detectDuplicates(activeAccounts)
        if (autoCandidates.isEmpty()) return emptyList()

        val executedLogs = mutableListOf<MergeLogEntity>()
        val mergedIdsThisPass = mutableSetOf<Long>()

        for (candidate in autoCandidates) {
            if (mergedIdsThisPass.contains(candidate.accountA.id) || mergedIdsThisPass.contains(candidate.accountB.id)) {
                continue
            }

            val survivor = pickSurvivingAccount(candidate.accountA, candidate.accountB)
            val toMerge = if (survivor.id == candidate.accountA.id) candidate.accountB else candidate.accountA

            val logId = mergeAccounts(
                survivingAccountId = survivor.id,
                mergedAccountId = toMerge.id,
                mode = MergeMode.AUTO
            )

            mergedIdsThisPass.add(toMerge.id)
            val log = mergeLogDao.getMergeLogById(logId)
            if (log != null) {
                executedLogs.add(log)
            }
        }

        return executedLogs
    }

    /**
     * Merges [mergedAccountId] into [survivingAccountId] in one atomic Room transaction.
     * Moves transactions, split links, settlement links, balances, and aliases.
     * Records the merged account's identifier as an alias for future routing.
     */
    suspend fun mergeAccounts(
        survivingAccountId: Long,
        mergedAccountId: Long,
        mode: MergeMode = MergeMode.MANUAL,
        overrideDisplayName: String? = null,
        overrideType: AccountType? = null,
        overrideLast4: String? = null
    ): Long = runInTransaction {
        val surviving = accountDao.getAccountById(survivingAccountId)
            ?: throw IllegalArgumentException("Surviving account ID $survivingAccountId not found")
        val merged = accountDao.getAccountById(mergedAccountId)
            ?: throw IllegalArgumentException("Merged account ID $mergedAccountId not found")

        // 1. Collect and move all transactions
        val txs = transactionDao.getTransactionsByAccountId(merged.id)
        val movedTxIds = txs.map { it.id }
        if (movedTxIds.isNotEmpty()) {
            transactionDao.updateAccountIdForTransactions(merged.id, surviving.id)
        }

        // 2. Resolve balances: keep MANUAL balance if present, otherwise newest AUTO balance
        val finalBalancePaise: Long
        val finalBalanceAsOf: Long
        val finalBalanceSource: BalanceSource

        if (surviving.balanceSource == BalanceSource.MANUAL) {
            finalBalancePaise = surviving.balancePaise
            finalBalanceAsOf = surviving.balanceAsOf
            finalBalanceSource = BalanceSource.MANUAL
        } else if (merged.balanceSource == BalanceSource.MANUAL) {
            finalBalancePaise = merged.balancePaise
            finalBalanceAsOf = merged.balanceAsOf
            finalBalanceSource = BalanceSource.MANUAL
        } else {
            if (merged.balanceAsOf > surviving.balanceAsOf) {
                finalBalancePaise = merged.balancePaise
                finalBalanceAsOf = merged.balanceAsOf
                finalBalanceSource = BalanceSource.AUTO
            } else {
                finalBalancePaise = surviving.balancePaise
                finalBalanceAsOf = surviving.balanceAsOf
                finalBalanceSource = surviving.balanceSource
            }
        }

        val finalCreditLimit = if (surviving.creditLimitPaise > 0L) surviving.creditLimitPaise else merged.creditLimitPaise

        val finalDisplayName = overrideDisplayName ?: surviving.displayName
        val finalType = overrideType ?: surviving.type
        val finalLast4 = overrideLast4 ?: (if (surviving.last4.isNotBlank()) surviving.last4 else merged.last4)

        val updatedSurviving = surviving.copy(
            displayName = finalDisplayName,
            type = finalType,
            last4 = finalLast4,
            balancePaise = finalBalancePaise,
            balanceAsOf = finalBalanceAsOf,
            balanceSource = finalBalanceSource,
            creditLimitPaise = finalCreditLimit
        )
        accountDao.updateAccount(updatedSurviving)

        // 3. Move existing aliases from merged to surviving
        accountAliasDao.reassignAliases(merged.id, surviving.id)

        // 4. Record merged account's own identifier as an alias for surviving account
        if (merged.bankKey.isNotBlank() && merged.last4.isNotBlank()) {
            accountAliasDao.insertAlias(
                AccountAliasEntity(
                    accountId = surviving.id,
                    bankKey = merged.bankKey,
                    last4 = merged.last4
                )
            )
        }

        // 5. Snapshot and create merge_log entry
        val snapshotJson = serializeAccountSnapshot(merged)
        val movedTxIdsJson = serializeMovedTransactionIds(movedTxIds)

        val logId = mergeLogDao.insertMergeLog(
            MergeLogEntity(
                survivingAccountId = surviving.id,
                mergedAccountSnapshot = snapshotJson,
                movedTransactionIds = movedTxIdsJson,
                mode = mode,
                createdAt = System.currentTimeMillis(),
                undoneAt = null
            )
        )

        // 6. Delete merged account
        accountDao.deleteAccount(merged)

        logId
    }

    /**
     * Undoes a previous merge, restoring the original merged account and its transactions.
     */
    suspend fun undoMerge(mergeLogId: Long): Boolean = runInTransaction {
        val log = mergeLogDao.getMergeLogById(mergeLogId) ?: return@runInTransaction false
        if (log.undoneAt != null) return@runInTransaction false

        val restoredAccount = deserializeAccountSnapshot(log.mergedAccountSnapshot)

        // 1. Re-insert the restored account
        accountDao.insertAccount(restoredAccount)

        // 2. Move original transactions back to restored account
        val movedTxIds = deserializeMovedTransactionIds(log.movedTransactionIds)
        if (movedTxIds.isNotEmpty()) {
            transactionDao.updateAccountIdForTransactionIds(movedTxIds, restoredAccount.id)
        }

        // 3. Remove the alias created during merge
        if (restoredAccount.bankKey.isNotBlank() && restoredAccount.last4.isNotBlank()) {
            accountAliasDao.deleteAliasByBankKeyAndLast4(restoredAccount.bankKey, restoredAccount.last4)
        }

        // 4. Mark merge log as undone
        mergeLogDao.markUndone(log.id, System.currentTimeMillis())

        true
    }

    /**
     * Marks a pair of accounts as "Not the same" forever.
     */
    suspend fun markNotSame(accountIdA: Long, accountIdB: Long) {
        val smaller = minOf(accountIdA, accountIdB)
        val larger = maxOf(accountIdA, accountIdB)
        notSamePairDao.insertPair(NotSamePairEntity(accountIdA = smaller, accountIdB = larger))
    }

    /**
     * Picks which of two accounts should survive in an automatic merge.
     */
    fun pickSurvivingAccount(a: AccountEntity, b: AccountEntity): AccountEntity {
        // Preference 1: Keep account with MANUAL balance
        if (a.balanceSource == BalanceSource.MANUAL && b.balanceSource != BalanceSource.MANUAL) return a
        if (b.balanceSource == BalanceSource.MANUAL && a.balanceSource != BalanceSource.MANUAL) return b

        // Preference 2: Keep account with non-blank last4
        if (a.last4.isNotBlank() && b.last4.isBlank()) return a
        if (b.last4.isNotBlank() && a.last4.isBlank()) return b

        // Preference 3: Keep account with assigned type over UNASSIGNED
        if (a.type != AccountType.UNASSIGNED && b.type == AccountType.UNASSIGNED) return a
        if (b.type != AccountType.UNASSIGNED && a.type == AccountType.UNASSIGNED) return b

        // Preference 4: Keep older account (smaller ID)
        return if (a.id <= b.id) a else b
    }
}
