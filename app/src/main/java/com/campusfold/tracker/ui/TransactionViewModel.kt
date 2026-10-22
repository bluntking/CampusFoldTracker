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
    val pnbCreditLimit: StateFlow<Double>
    val lastSmsSyncTimestamp: StateFlow<Long>

    // UI state
    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val filteredTransactions: StateFlow<List<TransactionEntity>>

    init {
        val db = AppDatabase.getDatabase(application)
        val prefs = AppPreferences(application)
        repository = TransactionRepository(db, prefs, application)

        // Ensure default pre-seeded categories & mappings exist
        viewModelScope.launch {
            repository.ensurePreseededData()
        }

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

        viewModelScope.launch {
            allTransactions.collect {
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

        pnbCreditLimit = repository.latestPnbCreditLimit.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            50000.0
        )

        lastSmsSyncTimestamp = repository.lastSmsSyncTimestamp.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0L
        )

        filteredTransactions = combine(allTransactions, _selectedFilter) { list, filter ->
            when (filter) {
                "EXPENSE" -> list.filter { it.type == TransactionType.EXPENSE }
                "INCOME" -> list.filter { it.type == TransactionType.INCOME }
                "CC_BILL" -> list.filter { it.type == TransactionType.CC_BILL_TRANSFER }
                else -> list
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    // ─── Transaction CRUD & Split Recalculation ──────────────────────────────

    fun addManualTransaction(
        amount: Double,
        vendorName: String,
        categoryId: Long?,
        type: TransactionType = TransactionType.EXPENSE,
        paymentMode: String = "Cash/Manual",
        splits: List<SplitItemEntity> = emptyList()
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
                timestamp = System.currentTimeMillis()
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
            val existing = allTransactions.value.find { it.id == id }
            val updated = (existing ?: TransactionEntity(
                id = id,
                amount = amount,
                myActualShare = amount,
                type = type,
                paymentMode = paymentMode,
                rawMerchant = vendorName,
                friendlyVendorName = vendorName,
                categoryId = categoryId,
                timestamp = System.currentTimeMillis()
            )).copy(
                amount = amount,
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

    // ─── Split an Expense with Friends ────────────────────────────────────────

    fun splitExpense(transactionId: Long, splits: List<SplitItemEntity>) {
        viewModelScope.launch {
            val existing = allTransactions.value.find { it.id == transactionId } ?: return@launch
            repository.editTransactionWithSplits(existing, splits)
            _statusMessage.value = "Bill split updated! My share: ₹${(existing.amount - splits.sumOf { it.amountOwed }).toInt()}"
        }
    }

    // ─── Preferences & Budgets ───────────────────────────────────────────────

    fun updatePnbCreditLimit(limit: Double) {
        viewModelScope.launch {
            repository.setPnbCreditLimit(limit)
            _statusMessage.value = "PNB credit limit updated to ₹$limit."
        }
    }

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

    fun refreshInsights() {
        viewModelScope.launch {
            _weekOverWeekSpike.value = repository.calculateWeekOverWeekSpike()
        }
    }

    // ─── Inbox Scanning ───────────────────────────────────────────────────────

    fun scanInbox(daysBack: Int = 90) {
        if (_isScanning.value) return
        _isScanning.value = true
        _statusMessage.value = "Scanning SMS inbox ($daysBack days) with CampusSmsParser..."

        viewModelScope.launch {
            try {
                val result = com.campusfold.tracker.util.InboxSmsScanner.scanInbox(
                    context = getApplication(),
                    repository = repository,
                    daysBack = daysBack
                )
                val limitInfo = if (result.latestCreditLimit != null) " • PNB Limit: ₹${result.latestCreditLimit}" else ""
                _statusMessage.value = "Imported ${result.importedCount} new txns (${result.skippedDuplicates} dupes skipped)$limitInfo"
            } catch (e: Exception) {
                _statusMessage.value = "Error scanning SMS: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
