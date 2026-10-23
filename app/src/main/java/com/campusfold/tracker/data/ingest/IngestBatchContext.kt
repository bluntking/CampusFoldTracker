package com.campusfold.tracker.data.ingest

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.RuleEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.VendorEntity

/**
 * In-memory cache context passed through batch message ingestion passes.
 * Eliminates redundant N+1 queries by caching accounts, vendors, categories,
 * active rules, and recent transactions across chunk iterations.
 */
data class IngestBatchContext(
    val accounts: MutableList<AccountEntity>,
    var recentTransactions: List<TransactionEntity>,
    val vendors: MutableList<VendorEntity>,
    val categories: MutableList<CategoryEntity>,
    val activeRules: List<RuleEntity>,
    val autoAcceptAi: Boolean
)
