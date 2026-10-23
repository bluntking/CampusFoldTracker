package com.campusfold.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.model.*
import com.campusfold.tracker.data.pref.AppPreferences
import com.campusfold.tracker.util.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository

    // Transactions
    val allTransactions: StateFlow<List<TransactionEntity>>
    val allTransactionsWithSplits: StateFlow<List<TransactionWithSplits>>
    val totalMyExpense: StateFlow<Double>
    val totalGrossExpense: StateFlow<Double>
    val totalIncome: StateFlow<Double>

    // Categories & Budgets
    val categories: StateFlow<List<CategoryEntity>>
    val categorySpending: StateFlow<List<CategorySpending>>
    val monthCategorySpending: StateFlow<List<CategorySpending>>
    val monthVendorSpending: StateFlow<List<VendorSpendingItem>>
    val currentMonthTotalTrueSpend: StateFlow<Double>
    val overallMonthlyBudget: StateFlow<Double>

    // 'Where to Control' Actionable Insights
    val burnRateWarnings: StateFlow<List<BurnRateInsight>>
    val highFrequencyLeaks: StateFlow<List<HighFrequencyLeak>>

    private val _weekOverWeekSpike = MutableStateFlow<WeekOverWeekSpike?>(null)
    val weekOverWeekSpike = _weekOverWeekSpike.asStateFlow()

    // Vendor Mappings
    val vendorMappings: StateFlow<List<VendorMappingEntity>>

    // Persons (strictly normalized dynamic balances)
    val personsWithBalances: StateFlow<List<PersonWithBalance>>
    val allPersons: StateFlow<List<PersonEntity>>

    // Pending 1-tap settlement suggestions
    val pendingSuggestions: StateFlow<List<PendingSettlementSuggestionEntity>>

    // Unsettled split bills
    val allUnsettledBills: StateFlow<List<com.campusfold.tracker.data.model.UnsettledBillItem>>

    // Preferences
    val lastSmsSyncTimestamp: StateFlow<Long>
    val gmailSyncEmail: StateFlow<String?>
    val gmailSyncAppPassword: StateFlow<String?>
    val gmailLastSyncTimestamp: StateFlow<Long>

    // Gmail IMAP Sync State
    private val _isGmailSyncing = MutableStateFlow(false)
    val isGmailSyncing = _isGmailSyncing.asStateFlow()

    private val _gmailSyncProgress = MutableStateFlow<String?>(null)
    val gmailSyncProgress = _gmailSyncProgress.asStateFlow()

    // Accounts
    val accounts: StateFlow<List<com.campusfold.tracker.data.entity.AccountEntity>>

    // Duplicate Accounts & Merge Management
    val latestAutoMergeLog: StateFlow<MergeLogEntity?>
    val allActiveMergeLogs: StateFlow<List<MergeLogEntity>>
    private val _suggestedDuplicates = MutableStateFlow<List<com.campusfold.tracker.data.merge.DuplicatePair>>(emptyList())
    val suggestedDuplicates = _suggestedDuplicates.asStateFlow()

    // Onboarding & Theme Preferences
    val hasCompletedOnboarding: StateFlow<Boolean>
    val userName: StateFlow<String>
    val hideBalances: StateFlow<Boolean>
    val themeMode: StateFlow<String>
    val dynamicColor: StateFlow<Boolean>
    val amoledMode: StateFlow<Boolean>

    private val _dismissedAutoMergeLogId = MutableStateFlow<Long?>(null)
    val dismissedAutoMergeLogId = _dismissedAutoMergeLogId.asStateFlow()

    // Rules & AI Review Queue
    val allRules: StateFlow<List<RuleEntity>>
    val pendingReviewItems: StateFlow<List<ReviewItemEntity>>
    val autoAcceptAiResults: StateFlow<Boolean>
    val unparsedMessages: StateFlow<List<UnparsedMessageEntity>>
    val scanHistory: StateFlow<List<ScanHistoryEntity>>
    val aiFallbackParsingEnabled: StateFlow<Boolean>
    val aiFallbackAccountsEnabled: StateFlow<Boolean>
    val aiFallbackVendorsEnabled: StateFlow<Boolean>
    val aiFallbackCategoriesEnabled: StateFlow<Boolean>

    // Multi-Select State
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedTransactionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTransactionIds = _selectedTransactionIds.asStateFlow()

    private val _showArchivedAccounts = MutableStateFlow(false)
    val showArchivedAccounts = _showArchivedAccounts.asStateFlow()

    private var deletedTransactionsStash: List<TransactionEntity> = emptyList()

    // UI state
    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val filteredTransactions: StateFlow<List<TransactionEntity>>
    val unallocatedHiddenSenderCredits: StateFlow<List<TransactionEntity>>

    init {
        val db = AppDatabase.getDatabase(application)
        val prefs = AppPreferences(application)
        repository = TransactionRepository(db, prefs, application)

        // Ensure default pre-seeded categories & mappings exist
        viewModelScope.launch {
            repository.ensurePreseededData()
        }

        // Initialize Local On-Device AI Manager
        com.campusfold.tracker.ai.LocalAiManager.init(application)

        allTransactions = repository.allTransactions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allTransactionsWithSplits = repository.allTransactionsWithSplits.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        totalMyExpense = repository.totalMyExpense.map { it ?: 0.0 }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        totalGrossExpense = repository.totalGrossExpense.map { it ?: 0.0 }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        totalIncome = repository.totalIncome.map { it ?: 0.0 }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        categories = repository.allCategories.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        categorySpending = repository.categorySpending.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        monthCategorySpending = repository.getCurrentMonthCategorySpending().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        monthVendorSpending = repository.getCurrentMonthVendorSpending().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        currentMonthTotalTrueSpend = repository.getCurrentMonthTotalTrueSpend().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        overallMonthlyBudget = repository.overallMonthlyBudget.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            20000.0
        )

        burnRateWarnings = repository.getBurnRateWarnings().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        highFrequencyLeaks = repository.highFrequencyLeaks.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch(Dispatchers.Default) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            allTransactions.debounce(300L).collect {
                _weekOverWeekSpike.value = repository.calculateWeekOverWeekSpike()
            }
        }

        vendorMappings = repository.allVendorMappings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        personsWithBalances = repository.personsWithBalances.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allPersons = repository.allPersons.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        pendingSuggestions = repository.pendingSuggestions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allUnsettledBills = repository.allUnsettledBills.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        lastSmsSyncTimestamp = repository.lastSmsSyncTimestamp.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0L
        )

        gmailSyncEmail = prefs.gmailSyncEmail.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

        gmailSyncAppPassword = prefs.gmailSyncAppPassword.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

        gmailLastSyncTimestamp = prefs.gmailLastSyncTimestamp.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0L
        )

        accounts = repository.allAccounts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        latestAutoMergeLog = repository.latestAutoMergeLog.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

        allActiveMergeLogs = repository.allActiveMergeLogs.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch(Dispatchers.Default) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            accounts.debounce(300L).collect {
                refreshDuplicateAccounts()
            }
        }

        allRules = repository.allRules.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        pendingReviewItems = repository.pendingReviewItems.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        autoAcceptAiResults = prefs.autoAcceptAiResults.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )

        unparsedMessages = repository.allUnparsedMessages.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        scanHistory = repository.allScanHistory.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        aiFallbackParsingEnabled = prefs.aiFallbackParsingEnabled.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

        aiFallbackAccountsEnabled = prefs.aiFallbackAccountsEnabled.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

        aiFallbackVendorsEnabled = prefs.aiFallbackVendorsEnabled.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

        aiFallbackCategoriesEnabled = prefs.aiFallbackCategoriesEnabled.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

        hasCompletedOnboarding = repository.hasCompletedOnboarding.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )

        userName = repository.userName.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ""
        )

        hideBalances = repository.hideBalances.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )

        themeMode = repository.themeMode.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            "SYSTEM"
        )

        dynamicColor = repository.dynamicColor.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            true
        )

        amoledMode = repository.amoledMode.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )

        filteredTransactions = combine(allTransactions, _selectedFilter) { list, filter ->
            when {
                filter == "EXPENSE" -> list.filter { it.type == TransactionType.EXPENSE }
                filter == "INCOME" -> list.filter { it.type == TransactionType.INCOME }
                filter == "CC_BILL" -> list.filter { it.type == TransactionType.CC_BILL_TRANSFER }
                filter.startsWith("CATEGORY:") -> {
                    val rawCat = filter.removePrefix("CATEGORY:").substringBefore(":")
                    if (rawCat.equals("uncategorized", ignoreCase = true)) {
                        list.filter { it.categoryId == null }
                    } else {
                        val catId = rawCat.toLongOrNull()
                        if (catId != null) list.filter { it.categoryId == catId } else list
                    }
                }
                filter.startsWith("VENDOR:") -> {
                    val vendorName = filter.removePrefix("VENDOR:").trim()
                    list.filter {
                        it.friendlyVendorName.equals(vendorName, ignoreCase = true) ||
                        it.rawMerchant.contains(vendorName, ignoreCase = true)
                    }
                }
                else -> list
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        unallocatedHiddenSenderCredits = allTransactions.map { list ->
            list.filter { it.type == TransactionType.INCOME && (it.friendlyVendorName.contains("Sender hidden") || it.rawMerchant == "Axis Gmail Credit") }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Auto-sync Axis Bank Gmail IMAP on launch (Option 1) in background coroutine once all state flows are ready
        viewModelScope.launch {
            checkAndAutoSyncGmail()
        }
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun filterByCategory(categoryId: Long?, categoryName: String) {
        if (categoryId == null || categoryName.equals("Uncategorized", ignoreCase = true)) {
            _selectedFilter.value = "CATEGORY:uncategorized:Uncategorized"
        } else {
            _selectedFilter.value = "CATEGORY:${categoryId}:${categoryName}"
        }
    }

    fun filterByVendor(vendorName: String) {
        _selectedFilter.value = "VENDOR:${vendorName}"
    }

    // ─── Accounts CRUD ────────────────────────────────────────────────────────

    fun addAccount(
        name: String,
        bankName: String,
        accountType: String,
        last4: String,
        creditLimitOrBalance: Double,
        colorHex: String
    ) {
        viewModelScope.launch {
            val account = com.campusfold.tracker.data.entity.AccountEntity(
                name = name,
                bankName = bankName,
                accountType = accountType,
                last4 = last4,
                creditLimitOrBalance = creditLimitOrBalance,
                colorHex = colorHex
            )
            repository.addAccount(account)
            _statusMessage.value = "Account '$name' added."
        }
    }

    fun addAccount(
        name: String,
        bankName: String,
        type: com.campusfold.tracker.data.entity.AccountType,
        last4: String,
        creditLimitOrBalance: Double,
        colorHex: String
    ) = addAccount(name, bankName, type.name, last4, creditLimitOrBalance, colorHex)

    fun editAccount(
        id: Long,
        name: String,
        bankName: String,
        accountType: String,
        last4: String,
        creditLimitOrBalance: Double,
        colorHex: String
    ) {
        viewModelScope.launch {
            val account = com.campusfold.tracker.data.entity.AccountEntity(
                id = id,
                name = name,
                bankName = bankName,
                accountType = accountType,
                last4 = last4,
                creditLimitOrBalance = creditLimitOrBalance,
                colorHex = colorHex
            )
            repository.updateAccount(account)
            _statusMessage.value = "Account '$name' updated."
        }
    }

    fun editAccount(
        id: Long,
        name: String,
        bankName: String,
        type: com.campusfold.tracker.data.entity.AccountType,
        last4: String,
        creditLimitOrBalance: Double,
        colorHex: String
    ) = editAccount(id, name, bankName, type.name, last4, creditLimitOrBalance, colorHex)

    fun deleteAccount(account: com.campusfold.tracker.data.entity.AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            _statusMessage.value = "Account '${account.name}' removed."
        }
    }

    // ─── Start Afresh: Reset All Transactions ─────────────────────────────────

    fun clearAllTransactionsAndReset() {
        viewModelScope.launch {
            repository.clearAllTransactionsAndReset()
            _statusMessage.value = "All transactions cleared. Fresh start active."
        }
    }

    // ─── 1-Tap JSON Backup & Restore ──────────────────────────────────────────

    fun exportBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _statusMessage.value = "Exporting data to JSON..."
            val result = repository.exportBackup(context, uri)
            result.onSuccess { stats ->
                _statusMessage.value = "Backup saved! (${stats.transactionCount} txns, ${stats.splitCount} splits, ${stats.accountCount} accounts)"
            }.onFailure { e ->
                _statusMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun importBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _statusMessage.value = "Restoring data from JSON..."
            val result = repository.importBackup(context, uri)
            result.onSuccess { stats ->
                _statusMessage.value = "Backup restored! (${stats.transactionCount} txns, ${stats.splitCount} splits, ${stats.accountCount} accounts)"
            }.onFailure { e ->
                _statusMessage.value = "Import failed: ${e.localizedMessage}"
            }
        }
    }

    // ─── Transaction CRUD & Split Recalculation ──────────────────────────────

    fun addManualTransaction(
        amount: Double,
        vendorName: String,
        categoryId: Long?,
        type: TransactionType = TransactionType.EXPENSE,
        paymentMode: String = "Cash/Manual",
        splits: List<SplitItemEntity> = emptyList(),
        description: String? = null
    ) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                amount = amount,
                myActualShare = amount,
                type = type,
                paymentMode = paymentMode,
                rawMerchant = vendorName,
                friendlyVendorName = vendorName,
                categoryId = categoryId,
                timestamp = System.currentTimeMillis(),
                description = description
            )
            repository.addTransaction(tx, splits)
            _statusMessage.value = "Transaction added successfully."
        }
    }

    fun editTransaction(
        id: Long,
        amount: Double,
        vendorName: String,
        categoryId: Long?,
        type: TransactionType,
        paymentMode: String,
        splits: List<SplitItemEntity> = emptyList()
    ) {
        viewModelScope.launch {
            val amountPaiseVal = com.campusfold.tracker.util.CurrencyUtils.rupeesToPaise(amount)
            val updated = (existing ?: TransactionEntity(
                id = id,
                amountPaise = amountPaiseVal,
                myActualSharePaise = amountPaiseVal,
                type = type,
                paymentMode = paymentMode,
                rawMerchant = vendorName,
                friendlyVendorName = vendorName,
                categoryId = categoryId,
                timestamp = System.currentTimeMillis()
            )).copy(
                amountPaise = amountPaiseVal,
                friendlyVendorName = vendorName,
                categoryId = categoryId,
                type = type,
                paymentMode = paymentMode
            )

            // Recalculates splits and myActualShare cleanly
            repository.editTransactionWithSplits(updated, splits)
            _statusMessage.value = "Transaction updated and splits recalculated."
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            _statusMessage.value = "Transaction deleted (associated splits removed automatically)."
        }
    }

    fun deleteTransaction(id: Long) = deleteTransactionById(id)

    fun addTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.addTransaction(tx, emptyList())
            _statusMessage.value = "Transaction added successfully."
        }
    }

    fun deleteTransactionById(id: Long) {
        viewModelScope.launch {
            repository.deleteTransactionById(id)
            _statusMessage.value = "Transaction removed."
        }
    }

    fun toggleCcBillTransfer(transactionId: Long) {
        viewModelScope.launch {
            repository.toggleCcBillTransfer(transactionId)
            _statusMessage.value = "Toggled CC Bill Transfer status."
        }
    }

    fun getTransactionWithSplits(id: Long): Flow<TransactionWithSplits?> =
        repository.getTransactionWithSplits(id)

    fun saveVendorMappingForFuture(rawKeyword: String, friendlyName: String, categoryId: Long?) {
        viewModelScope.launch {
            repository.addVendorMapping(
                VendorMappingEntity(
                    rawSmsKeyword = rawKeyword.trim(),
                    friendlyName = friendlyName.trim(),
                    categoryId = categoryId
                )
            )
            _statusMessage.value = "Saved mapping: '$rawKeyword' → '$friendlyName' for future SMS"
        }
    }

    fun updateTransactionWithSplits(
        transaction: TransactionEntity,
        splits: List<SplitItemEntity>
    ) {
        viewModelScope.launch {
            repository.editTransactionWithSplits(transaction, splits)
            _statusMessage.value = "Transaction and splits updated cleanly."
        }
    }

    fun editTransactionWithSplits(
        transaction: TransactionEntity,
        splits: List<SplitItemEntity>
    ) = updateTransactionWithSplits(transaction, splits)

    // ─── Category CRUD ────────────────────────────────────────────────────────

    fun addCategory(name: String, colorHex: String, monthlyBudget: Double) {
        viewModelScope.launch {
            repository.addCategory(CategoryEntity(name = name, colorHex = colorHex, monthlyBudget = monthlyBudget))
            _statusMessage.value = "Category '$name' created."
        }
    }

    fun editCategory(id: Long, name: String, colorHex: String, monthlyBudget: Double) {
        viewModelScope.launch {
            repository.updateCategory(CategoryEntity(id = id, name = name, colorHex = colorHex, monthlyBudget = monthlyBudget))
            _statusMessage.value = "Category '$name' updated."
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            _statusMessage.value = "Category '${category.name}' deleted."
        }
    }

    // ─── Vendor Mapping CRUD ──────────────────────────────────────────────────

    fun addVendorMapping(rawSmsKeyword: String, friendlyName: String, categoryId: Long?) {
        viewModelScope.launch {
            repository.addVendorMapping(
                VendorMappingEntity(rawSmsKeyword = rawSmsKeyword, friendlyName = friendlyName, categoryId = categoryId)
            )
            _statusMessage.value = "Mapped '$rawSmsKeyword' to '$friendlyName'."
        }
    }

    fun editVendorMapping(id: Long, rawSmsKeyword: String, friendlyName: String, categoryId: Long?) {
        viewModelScope.launch {
            repository.updateVendorMapping(
                VendorMappingEntity(id = id, rawSmsKeyword = rawSmsKeyword, friendlyName = friendlyName, categoryId = categoryId)
            )
            _statusMessage.value = "Vendor mapping updated."
        }
    }

    fun deleteVendorMapping(mapping: VendorMappingEntity) {
        viewModelScope.launch {
            repository.deleteVendorMapping(mapping)
            _statusMessage.value = "Vendor mapping removed."
        }
    }

    // ─── Person CRUD ──────────────────────────────────────────────────────────

    fun addPerson(name: String, exactSmsAliases: String) {
        viewModelScope.launch {
            repository.addPerson(PersonEntity(name = name, exactSmsAliases = exactSmsAliases))
            _statusMessage.value = "Friend '$name' added."
        }
    }

    fun editPerson(id: Long, name: String, exactSmsAliases: String) {
        viewModelScope.launch {
            repository.updatePerson(PersonEntity(id = id, name = name, exactSmsAliases = exactSmsAliases))
            _statusMessage.value = "Friend profile updated."
        }
    }

    fun deletePerson(person: PersonEntity) {
        viewModelScope.launch {
            repository.deletePerson(person)
            _statusMessage.value = "Friend removed (and associated splits removed)."
        }
    }

    // ─── Split Settlements ────────────────────────────────────────────────────

    fun recordSplitPayment(splitId: Long, paymentAmount: Double) {
        viewModelScope.launch {
            repository.recordSplitPayment(splitId, paymentAmount)
            _statusMessage.value = "Recorded payment of ₹$paymentAmount."
        }
    }

    fun markSplitSettled(splitId: Long, isSettled: Boolean) {
        viewModelScope.launch {
            repository.markSplitSettled(splitId, isSettled)
            _statusMessage.value = if (isSettled) "Marked as fully settled." else "Marked unsettled."
        }
    }

    // ─── FIFO Settlement Engine ──────────────────────────────────────────────

    fun settleAmountForPerson(
        personId: Long,
        amount: Double,
        onExcess: ((excess: Double, friendName: String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.settleAmountForPerson(personId, amount)
            if (result.excessAmount > 0.0) {
                _statusMessage.value = "Settled ₹${result.settledAmount.toInt()} from ${result.friendName}. Overpayment of ₹${result.excessAmount.toInt()}!"
                onExcess?.invoke(result.excessAmount, result.friendName)
            } else {
                _statusMessage.value = "Settled ₹${result.settledAmount.toInt()} from ${result.friendName}. Remaining owed: ₹${result.remainingOwed.toInt()}"
            }
        }
    }

    fun logOverpaymentAsIncome(excessAmount: Double, friendName: String) {
        viewModelScope.launch {
            repository.logOverpaymentAsIncome(excessAmount, friendName)
            _statusMessage.value = "Logged excess ₹${excessAmount.toInt()} as general income / advance."
        }
    }

    // ─── 1-Tap 'Confirm & Save Alias' Suggestions ─────────────────────────────

    fun confirmAndSaveAlias(suggestionId: Long) {
        viewModelScope.launch {
            val result = repository.confirmAndSaveAlias(suggestionId)
            if (result != null) {
                _statusMessage.value = "Settled ₹${result.settledAmount.toInt()} from ${result.friendName} & saved alias for future SMS!"
            } else {
                _statusMessage.value = "Settlement applied."
            }
        }
    }

    fun confirmSettlementSuggestion(suggestionId: Long) {
        confirmAndSaveAlias(suggestionId)
    }

    fun dismissSettlementSuggestion(suggestionId: Long) {
        viewModelScope.launch {
            repository.dismissSettlementSuggestion(suggestionId)
            _statusMessage.value = "Settlement suggestion dismissed."
        }
    }

    fun applyHiddenCreditToFriend(transaction: TransactionEntity, personId: Long, friendName: String) {
        viewModelScope.launch {
            repository.settleAmountForPerson(personId, transaction.amount)
            repository.updateTransaction(
                transaction.copy(
                    type = TransactionType.SPLIT_SETTLEMENT,
                    friendlyVendorName = "Settlement from $friendName (Axis Gmail)"
                )
            )
            _statusMessage.value = "Applied ₹${transaction.amount.toInt()} credit towards $friendName's balance!"
        }
    }

    // ─── Split an Expense with Friends ────────────────────────────────────────

    fun splitExpense(transactionId: Long, splits: List<SplitItemEntity>) {
        viewModelScope.launch {
            val existing = allTransactions.value.find { it.id == transactionId } ?: return@launch
            repository.editTransactionWithSplits(existing, splits)
            _statusMessage.value = "Bill split updated! My share: ₹${(existing.amount - splits.sumOf { it.amountOwed }).toInt()}"
        }
    }

    // ─── Preferences & Budgets ───────────────────────────────────────────────

    fun setOverallMonthlyBudget(budget: Double) {
        viewModelScope.launch {
            repository.setOverallMonthlyBudget(budget)
            _statusMessage.value = "Overall monthly budget updated to ₹${budget.toInt()}."
        }
    }

    fun updateCategoryBudget(categoryId: Long, budget: Double) {
        viewModelScope.launch {
            repository.updateCategoryBudget(categoryId, budget)
            _statusMessage.value = "Category budget updated to ₹${budget.toInt()}."
        }
    }

    fun setCompletedOnboarding(completed: Boolean) {
        viewModelScope.launch {
            repository.setCompletedOnboarding(completed)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            repository.setUserName(name)
        }
    }

    fun setHideBalances(hide: Boolean) {
        viewModelScope.launch {
            repository.setHideBalances(hide)
        }
    }

    fun toggleHideBalances() {
        viewModelScope.launch {
            val current = hideBalances.value
            repository.setHideBalances(!current)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAmoledMode(enabled)
        }
    }

    fun refreshInsights() {
        viewModelScope.launch {
            _weekOverWeekSpike.value = repository.calculateWeekOverWeekSpike()
        }
    }

    // ─── Inbox Scanning ───────────────────────────────────────────────────────

    fun scanInboxFromDate(startDateMillis: Long, endDateMillis: Long = System.currentTimeMillis()) {
        val dateFmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        val dateStr = dateFmt.format(java.util.Date(startDateMillis))
        _statusMessage.value = "Started background SMS scan from $dateStr..."

        com.campusfold.tracker.util.InboxSmsScanner.enqueueSmsScanWorker(
            context = getApplication(),
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis
        )
    }

    fun scanInbox(daysBack: Int = 90) {
        val startTime = System.currentTimeMillis() - (daysBack.toLong() * 24L * 60L * 60L * 1000L)
        scanInboxFromDate(startTime)
    }

    fun scanSmsFromDate(startDateMillis: Long) {
        scanInboxFromDate(startDateMillis)
    }

    fun scanSmsMessages(monthsBack: Int) {
        scanInbox(monthsBack * 30)
    }

    // ─── Direct Axis Bank Gmail IMAP Sync ─────────────────────────────────────

    fun syncAxisFromGmail(
        emailAddress: String,
        appPassword: String,
        startDateMillis: Long?,
        saveCredentials: Boolean = true,
        onComplete: ((GmailSyncStats?, String?) -> Unit)? = null
    ) {
        if (_isGmailSyncing.value) return
        _isGmailSyncing.value = true
        _gmailSyncProgress.value = "Connecting to Gmail IMAP securely..."

        viewModelScope.launch {
            try {
                if (saveCredentials) {
                    val prefs = AppPreferences(getApplication())
                    prefs.saveGmailCredentials(emailAddress, appPassword)
                }
                val stats = repository.syncAxisFromGmail(
                    emailAddress = emailAddress,
                    appPassword = appPassword,
                    startDateMillis = startDateMillis,
                    onProgress = { current, total ->
                        _gmailSyncProgress.value = "Fetching Axis email $current of $total..."
                    }
                )
                val totalImported = stats.importedDebits + stats.importedCredits
                val summary = "✓ Synced $totalImported Axis txns (${stats.importedDebits} debits, ${stats.importedCredits} credits, ${stats.autoSettledCredits} settled, ${stats.skippedDuplicates} dupes skipped)"
                _statusMessage.value = summary
                _gmailSyncProgress.value = summary
                onComplete?.invoke(stats, null)
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Failed connecting to Gmail"
                _statusMessage.value = "Gmail Sync Error: $errorMsg"
                _gmailSyncProgress.value = errorMsg
                onComplete?.invoke(null, errorMsg)
            } finally {
                _isGmailSyncing.value = false
            }
        }
    }

    fun saveGmailCredentials(email: String, appPassword: String) {
        viewModelScope.launch {
            val prefs = AppPreferences(getApplication())
            prefs.saveGmailCredentials(email, appPassword)
            _statusMessage.value = "Saved Gmail credentials."
        }
    }

    fun clearGmailCredentials() {
        viewModelScope.launch {
            val prefs = AppPreferences(getApplication())
            prefs.clearGmailCredentials()
            _statusMessage.value = "Cleared saved Gmail credentials."
        }
    }

    /**
     * Silent auto-sync on app open / foreground.
     * Triggers a Gmail sync via WorkManager if last sync was > 15 minutes ago.
     */
    fun checkAndAutoSyncGmail() {
        try {
            val email = gmailSyncEmail.value
            val password = gmailSyncAppPassword.value
            if (email.isNullOrBlank() || password.isNullOrBlank()) return

            val lastSync = gmailLastSyncTimestamp.value
            val fifteenMinutesMillis = 15L * 60L * 1000L
            if (lastSync > 0L && (System.currentTimeMillis() - lastSync) <= fifteenMinutesMillis) {
                return
            }

            repository.enqueueGmailSyncWorker(getApplication())
        } catch (e: Exception) {
            android.util.Log.e("TransactionViewModel", "Silent Gmail auto-sync check error", e)
        }
    }

    /**
     * Maps an incoming transfer (Income) directly to an Expense as a reimbursement.
     * Decreases the user's out-of-pocket share (myActualShare) on the expense and updates split balances.
     */
    fun linkIncomeToExpense(
        incomeTransactionId: Long,
        expenseTransactionId: Long,
        offsetAmount: Double? = null,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                repository.linkIncomeToExpense(incomeTransactionId, expenseTransactionId, offsetAmount)
                _statusMessage.value = "✓ Successfully mapped income to expense reimbursement!"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error mapping income to expense: ${e.message}"
            }
        }
    }

    // ─── Rules Management ─────────────────────────────────────────────────────

    fun addRule(rule: RuleEntity, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val id = repository.addRule(rule)
                _statusMessage.value = "Rule created successfully"
                onComplete?.invoke(id)
            } catch (e: Exception) {
                _statusMessage.value = "Error creating rule: ${e.message}"
            }
        }
    }

    fun updateRule(rule: RuleEntity, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.updateRule(rule)
                _statusMessage.value = "Rule updated"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error updating rule: ${e.message}"
            }
        }
    }

    fun deleteRule(rule: RuleEntity, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteRule(rule)
                _statusMessage.value = "Rule deleted"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting rule: ${e.message}"
            }
        }
    }

    fun deleteRuleById(ruleId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteRuleById(ruleId)
                _statusMessage.value = "Rule deleted"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting rule: ${e.message}"
            }
        }
    }

    fun toggleRuleEnabled(ruleId: Long, enabled: Boolean, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.toggleRuleEnabled(ruleId, enabled)
                _statusMessage.value = if (enabled) "Rule enabled" else "Rule disabled"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error toggling rule: ${e.message}"
            }
        }
    }

    fun applyRuleToPastTransactions(ruleId: Long, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val count = repository.applyRuleToPastTransactions(ruleId)
                _statusMessage.value = "Rule applied to $count past transaction(s)"
                onComplete?.invoke(count)
            } catch (e: Exception) {
                _statusMessage.value = "Error applying rule: ${e.message}"
            }
        }
    }

    // ─── Review Items Management ───────────────────────────────────────────────

    fun acceptReviewItem(reviewItemId: Long, editedPayloadJson: String? = null, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.acceptReviewItem(reviewItemId, editedPayloadJson)
                _statusMessage.value = "Item approved"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error approving item: ${e.message}"
            }
        }
    }

    fun editReviewItem(reviewItemId: Long, newPayloadJson: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.editReviewItem(reviewItemId, newPayloadJson)
                _statusMessage.value = "Item updated"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error editing item: ${e.message}"
            }
        }
    }

    fun discardReviewItem(reviewItemId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.discardReviewItem(reviewItemId)
                _statusMessage.value = "Item discarded"
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error discarding item: ${e.message}"
            }
        }
    }

    fun setAutoAcceptAiResults(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val prefs = AppPreferences(getApplication())
                prefs.setAutoAcceptAiResults(enabled)
                _statusMessage.value = if (enabled) "Auto-accept AI predictions enabled" else "Auto-accept AI predictions disabled"
            } catch (e: Exception) {
                _statusMessage.value = "Error updating setting: ${e.message}"
            }
        }
    }

    // ─── Duplicate Accounts & Merge Management ───────────────────────────────

    fun refreshDuplicateAccounts() {
        viewModelScope.launch {
            try {
                val (_, suggested) = repository.detectDuplicateAccounts()
                _suggestedDuplicates.value = suggested
            } catch (e: Exception) {
                android.util.Log.w("TransactionViewModel", "Error detecting duplicate accounts: ${e.message}")
            }
        }
    }

    fun dismissAutoMergeNotification(logId: Long) {
        _dismissedAutoMergeLogId.value = logId
    }

    fun undoMerge(mergeLogId: Long, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val success = repository.undoMerge(mergeLogId)
                if (success) {
                    _statusMessage.value = "✓ Merge undone. Restored original account and transactions."
                    refreshDuplicateAccounts()
                } else {
                    _statusMessage.value = "Unable to undo merge."
                }
                onComplete?.invoke(success)
            } catch (e: Exception) {
                _statusMessage.value = "Error undoing merge: ${e.message}"
            }
        }
    }

    fun mergeAccounts(
        survivingAccountId: Long,
        mergedAccountId: Long,
        overrideDisplayName: String? = null,
        overrideType: AccountType? = null,
        overrideLast4: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                repository.mergeAccounts(
                    survivingAccountId = survivingAccountId,
                    mergedAccountId = mergedAccountId,
                    mode = MergeMode.MANUAL,
                    overrideDisplayName = overrideDisplayName,
                    overrideType = overrideType,
                    overrideLast4 = overrideLast4
                )
                _statusMessage.value = "✓ Accounts merged successfully."
                refreshDuplicateAccounts()
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error merging accounts: ${e.message}"
            }
        }
    }

    fun markPairNotSame(accountIdA: Long, accountIdB: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.markPairNotSame(accountIdA, accountIdB)
                _suggestedDuplicates.value = _suggestedDuplicates.value.filterNot {
                    (it.accountA.id == accountIdA && it.accountB.id == accountIdB) ||
                    (it.accountA.id == accountIdB && it.accountB.id == accountIdA)
                }
                _statusMessage.value = "Marked as different accounts."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun dismissSuggestedDuplicate(pair: com.campusfold.tracker.data.merge.DuplicatePair) {
        _suggestedDuplicates.value = _suggestedDuplicates.value.filterNot {
            it.accountA.id == pair.accountA.id && it.accountB.id == pair.accountB.id
        }
    }

    fun getActiveMergeLogsForAccount(accountId: Long, onResult: (List<MergeLogEntity>) -> Unit) {
        viewModelScope.launch {
            try {
                val logs = repository.getActiveMergeLogsForAccount(accountId)
                onResult(logs)
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }

    fun countTransactionsForAccount(accountId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val count = repository.countTransactionsForAccount(accountId)
                onResult(count)
            } catch (_: Exception) {
                onResult(0)
            }
        }
    }

    // ─── Selection Mode & Bulk Actions ────────────────────────────────────────
    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedTransactionIds.value = emptySet()
        }
    }

    fun startSelectionWith(transactionId: Long) {
        _isSelectionMode.value = true
        _selectedTransactionIds.value = setOf(transactionId)
    }

    fun toggleTransactionSelected(transactionId: Long) {
        val current = _selectedTransactionIds.value.toMutableSet()
        if (current.contains(transactionId)) {
            current.remove(transactionId)
            if (current.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            current.add(transactionId)
        }
        _selectedTransactionIds.value = current
    }

    fun selectAllTransactions() {
        val allIds = filteredTransactions.value.map { it.id }.toSet()
        _selectedTransactionIds.value = allIds
    }

    fun clearTransactionSelection() {
        _selectedTransactionIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun bulkDeleteSelectedTransactions(onComplete: (() -> Unit)? = null) {
        val ids = _selectedTransactionIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val deleted = repository.bulkDeleteTransactions(ids)
                deletedTransactionsStash = deleted
                _statusMessage.value = "Deleted ${deleted.size} transaction(s)."
                clearTransactionSelection()
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting transactions: ${e.message}"
            }
        }
    }

    fun bulkRecategorizeSelectedTransactions(categoryId: Long?, onComplete: (() -> Unit)? = null) {
        val ids = _selectedTransactionIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.bulkRecategorizeTransactions(ids, categoryId)
                _statusMessage.value = "Recategorized ${ids.size} transaction(s)."
                clearTransactionSelection()
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error recategorizing: ${e.message}"
            }
        }
    }

    fun bulkMoveSelectedTransactionsToAccount(accountId: Long, onComplete: (() -> Unit)? = null) {
        val ids = _selectedTransactionIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.bulkMoveTransactionsToAccount(ids, accountId)
                _statusMessage.value = "Moved ${ids.size} transaction(s) to account."
                clearTransactionSelection()
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error moving transactions: ${e.message}"
            }
        }
    }

    // ─── Single Transaction Manual Actions & Undo ─────────────────────────────
    fun deleteTransactionWithUndo(tx: TransactionEntity, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(tx)
                deletedTransactionsStash = listOf(tx)
                _statusMessage.value = "Deleted transaction of ₹${tx.amount.toInt()}."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting transaction: ${e.message}"
            }
        }
    }

    fun undoLastDelete() {
        val stash = deletedTransactionsStash
        if (stash.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.restoreTransactions(stash)
                deletedTransactionsStash = emptyList()
                _statusMessage.value = "✓ Restored ${stash.size} deleted item(s)."
            } catch (e: Exception) {
                _statusMessage.value = "Error restoring: ${e.message}"
            }
        }
    }

    fun mergeTransactions(survivingTxId: Long, duplicateTxId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.mergeDuplicateTransactions(survivingTxId, duplicateTxId)
                _statusMessage.value = "✓ Transactions merged successfully."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error merging transactions: ${e.message}"
            }
        }
    }

    // ─── Manual Account Actions ───────────────────────────────────────────────
    fun deleteAccountWithReassign(accountId: Long, targetAccountId: Long?, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteAccountWithReassign(accountId, targetAccountId)
                _statusMessage.value = "Account deleted and transactions reassigned."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error removing account: ${e.message}"
            }
        }
    }

    fun toggleShowArchivedAccounts() {
        _showArchivedAccounts.value = !_showArchivedAccounts.value
    }

    fun setAccountArchived(accountId: Long, isArchived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setAccountArchived(accountId, isArchived)
                _statusMessage.value = if (isArchived) "Account archived." else "Account unarchived."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun setAccountBalanceManually(accountId: Long, balancePaise: Long) {
        viewModelScope.launch {
            try {
                repository.setAccountBalanceManually(accountId, balancePaise)
                _statusMessage.value = "Manual balance updated."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun setAccountBalanceManually(accountId: Long, balanceRupees: Double) {
        setAccountBalanceManually(accountId, com.campusfold.tracker.util.CurrencyUtils.rupeesToPaise(balanceRupees))
    }

    fun clearManualBalanceOverride(accountId: Long) {
        viewModelScope.launch {
            try {
                repository.clearManualBalanceOverride(accountId)
                _statusMessage.value = "Reverted to auto balance updates."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    // ─── Manual Vendor Actions ────────────────────────────────────────────────
    fun renameVendor(vendorId: Long, newName: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.renameVendor(vendorId, newName)
                _statusMessage.value = "Vendor renamed."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun reassignVendorCategory(vendorId: Long, newCategoryId: Long?, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.reassignVendorCategory(vendorId, newCategoryId)
                _statusMessage.value = "Vendor category reassigned."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun mergeVendors(survivingVendorId: Long, mergedVendorId: Long, survivingName: String, categoryId: Long?, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.mergeVendors(survivingVendorId, mergedVendorId, survivingName, categoryId)
                _statusMessage.value = "Vendors merged successfully."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error merging vendors: ${e.message}"
            }
        }
    }

    fun mergeVendors(survivingVendorId: Long, mergedVendorId: Long, onComplete: (() -> Unit)? = null) {
        val surviving = vendorMappings.value.find { it.id == survivingVendorId }
        val survivingName = surviving?.displayName ?: ""
        val categoryId = surviving?.categoryId
        mergeVendors(survivingVendorId, mergedVendorId, survivingName, categoryId, onComplete)
    }

    // ─── Manual Category Actions ──────────────────────────────────────────────
    fun deleteCategoryWithReassign(categoryId: Long, targetCategoryId: Long?, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteCategoryWithReassign(categoryId, targetCategoryId)
                _statusMessage.value = "Category deleted and transactions reassigned."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting category: ${e.message}"
            }
        }
    }

    fun mergeCategories(survivingCategoryId: Long, mergedCategoryId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.mergeCategories(survivingCategoryId, mergedCategoryId)
                _statusMessage.value = "Categories merged successfully."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error merging categories: ${e.message}"
            }
        }
    }

    fun setCategoryBudgetPaise(categoryId: Long, budgetPaise: Long) {
        viewModelScope.launch {
            try {
                repository.setCategoryBudget(categoryId, budgetPaise)
                _statusMessage.value = "Budget updated."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    // ─── Review Queue Batch Actions ───────────────────────────────────────────
    fun acceptAllHighConfidenceReviewItems(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val count = repository.acceptAllHighConfidenceReviewItems()
                _statusMessage.value = "Accepted $count high-confidence items."
                onComplete?.invoke(count)
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun discardAllPendingReviewItems(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.discardAllPendingReviewItems()
                _statusMessage.value = "Discarded all pending items."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    // ─── Unparsed Messages & Scan History & Rebuild ───────────────────────────
    fun deleteUnparsedMessage(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteUnparsedMessage(id)
                _statusMessage.value = "Unparsed message dismissed."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun rebuildFromScratch(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.rebuildFromScratch()
                _statusMessage.value = "All transaction & scan data reset for rebuild."
                onComplete?.invoke()
            } catch (e: Exception) {
                _statusMessage.value = "Error rebuilding: ${e.message}"
            }
        }
    }

    // ─── AI Task Settings ─────────────────────────────────────────────────────
    fun setAiTaskEnabled(task: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val prefs = AppPreferences(getApplication())
                prefs.setAiTaskEnabled(task, enabled)
                _statusMessage.value = "AI setting updated for $task."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
