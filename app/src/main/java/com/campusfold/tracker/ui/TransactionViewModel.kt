package com.campusfold.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionEntity
import com.campusfold.tracker.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository

    val allTransactions: StateFlow<List<TransactionEntity>>
    val totalExpense: StateFlow<Double>
    val totalIncome: StateFlow<Double>

    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val filteredTransactions: StateFlow<List<TransactionEntity>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TransactionRepository(db.transactionDao())

        allTransactions = repository.allTransactions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        totalExpense = repository.totalExpense.map { it ?: 0.0 }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        totalIncome = repository.totalIncome.map { it ?: 0.0 }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        filteredTransactions = combine(allTransactions, _selectedFilter) { list, filter ->
            when (filter) {
                "DEBIT" -> list.filter { it.type == "DEBIT" }
                "CREDIT" -> list.filter { it.type == "CREDIT" }
                else -> list
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun scanInbox() {
        if (_isScanning.value) return
        _isScanning.value = true
        _statusMessage.value = "Scanning SMS inbox for bank transactions..."

        viewModelScope.launch {
            try {
                val count = repository.scanInboxAndImport(getApplication(), daysBack = 90)
                _statusMessage.value = "Imported $count transactions from SMS inbox."
            } catch (e: Exception) {
                _statusMessage.value = "Error scanning SMS: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
