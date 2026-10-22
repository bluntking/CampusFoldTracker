package com.campusfold.tracker.data

import android.content.Context
import com.campusfold.tracker.util.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TransactionRepository(private val dao: TransactionDao) {

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
    val totalExpense: Flow<Double?> = dao.getTotalExpense()
    val totalIncome: Flow<Double?> = dao.getTotalIncome()

    suspend fun insert(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.insert(transaction)
    }

    suspend fun delete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.delete(transaction)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun scanInboxAndImport(context: Context, daysBack: Int = 90): Int = withContext(Dispatchers.IO) {
        val parsedList = SmsReader.readInboxTransactions(context, daysBack)
        if (parsedList.isNotEmpty()) {
            dao.insertAll(parsedList)
        }
        parsedList.size
    }
}
