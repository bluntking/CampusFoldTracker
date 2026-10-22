package com.campusfold.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.model.CategorySpending
import com.campusfold.tracker.data.model.PersonWithBalance
import com.campusfold.tracker.data.model.TransactionWithSplits
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

    // Categories
    val categories: StateFlow<List<CategoryEntity>>
    val categorySpending: StateFlow<List<CategorySpending>>

    // Vendor Mappings
    val vendorMappings: StateFlow<List<VendorMappingEntity>>

    // Persons (strictly normalized dynamic balances)
    val personsWithBalances: StateFlow<List<PersonWithBalance>>

    // Pending 1-tap settlement suggestions
    val pendingSuggestions: StateFlow<List<PendingSettlementSuggestionEntity>>

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
        repository = TransactionRepository(db, prefs)

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

        pendingSuggestions = repository.pendingSuggestions.stateIn(
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

    // ─── 1-Tap Settlement Suggestions ─────────────────────────────────────────

    fun confirmSettlementSuggestion(suggestionId: Long) {
        viewModelScope.launch {
            repository.confirmSettlementSuggestion(suggestionId)
            _statusMessage.value = "Settlement confirmed and split payment applied."
        }
    }

    fun dismissSettlementSuggestion(suggestionId: Long) {
        viewModelScope.launch {
            repository.dismissSettlementSuggestion(suggestionId)
            _statusMessage.value = "Settlement suggestion dismissed."
        }
    }

    // ─── Preferences ─────────────────────────────────────────────────────────

    fun updatePnbCreditLimit(limit: Double) {
        viewModelScope.launch {
            repository.setPnbCreditLimit(limit)
            _statusMessage.value = "PNB credit limit updated to ₹$limit."
        }
    }

    // ─── Inbox Scanning ───────────────────────────────────────────────────────

    fun scanInbox() {
        if (_isScanning.value) return
        _isScanning.value = true
        _statusMessage.value = "Scanning SMS inbox for bank transactions..."

        viewModelScope.launch {
            try {
                val parsedList = SmsReader.readInboxTransactions(getApplication(), daysBack = 90)
                var imported = 0
                for (item in parsedList) {
                    val (friendlyName, categoryId) = repository.resolveVendor(item.rawMerchant)
                    val finalized = item.copy(
                        friendlyVendorName = friendlyName,
                        categoryId = categoryId
                    )
                    repository.addTransaction(finalized)
                    imported++
                }
                repository.setLastSmsSyncTimestamp(System.currentTimeMillis())
                _statusMessage.value = "Imported $imported transactions from SMS inbox."
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
