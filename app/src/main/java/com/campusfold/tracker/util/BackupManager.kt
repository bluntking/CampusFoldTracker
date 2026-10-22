package com.campusfold.tracker.util

import android.content.Context
import android.net.Uri
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.pref.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupStats(
    val accountCount: Int,
    val categoryCount: Int,
    val vendorCount: Int,
    val personCount: Int,
    val transactionCount: Int,
    val splitCount: Int
)

object BackupManager {

    suspend fun exportBackup(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        prefs: AppPreferences
    ): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val accounts = db.accountDao().getAllAccountsSync()
            val categories = db.categoryDao().getAllCategoriesList()
            val vendors = db.vendorMappingDao().getAllVendorMappingsList()
            val persons = db.personDao().getAllPersonsList()
            val transactions = db.transactionDao().getAllTransactionsSync()
            val splits = db.splitItemDao().getAllSplitsSync()
            val pnbLimit = prefs.getLatestPnbCreditLimitSync()
            val overallBudget = prefs.getOverallMonthlyBudgetSync()

            val rootJson = JSONObject().apply {
                put("app", "CampusFoldTracker")
                put("version", 1)
                put("exportTimestamp", System.currentTimeMillis())
                put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date()))

                // Preferences
                put("preferences", JSONObject().apply {
                    put("pnbCreditLimit", pnbLimit)
                    put("overallMonthlyBudget", overallBudget)
                })

                // Accounts
                val accountsArray = JSONArray()
                accounts.forEach { acc ->
                    accountsArray.put(JSONObject().apply {
                        put("id", acc.id)
                        put("name", acc.name)
                        put("bankName", acc.bankName)
                        put("accountType", acc.accountType)
                        put("last4", acc.last4)
                        put("creditLimitOrBalance", acc.creditLimitOrBalance)
                        put("colorHex", acc.colorHex)
                    })
                }
                put("accounts", accountsArray)

                // Categories
                val categoriesArray = JSONArray()
                categories.forEach { cat ->
                    categoriesArray.put(JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("colorHex", cat.colorHex)
                        put("monthlyBudget", cat.monthlyBudget)
                    })
                }
                put("categories", categoriesArray)

                // Vendor Mappings
                val vendorsArray = JSONArray()
                vendors.forEach { v ->
                    vendorsArray.put(JSONObject().apply {
                        put("id", v.id)
                        put("rawSmsKeyword", v.rawSmsKeyword)
                        put("friendlyName", v.friendlyName)
                        put("categoryId", v.categoryId ?: JSONObject.NULL)
                    })
                }
                put("vendorMappings", vendorsArray)

                // Persons
                val personsArray = JSONArray()
                persons.forEach { p ->
                    personsArray.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("exactSmsAliases", p.exactSmsAliases)
                    })
                }
                put("persons", personsArray)

                // Transactions
                val txArray = JSONArray()
                transactions.forEach { tx ->
                    txArray.put(JSONObject().apply {
                        put("id", tx.id)
                        put("amount", tx.amount)
                        put("myActualShare", tx.myActualShare)
                        put("type", tx.type.name)
                        put("paymentMode", tx.paymentMode)
                        put("rawMerchant", tx.rawMerchant)
                        put("friendlyVendorName", tx.friendlyVendorName)
                        put("categoryId", tx.categoryId ?: JSONObject.NULL)
                        put("timestamp", tx.timestamp)
                        put("upiRef", tx.upiRef ?: JSONObject.NULL)
                        put("rawSms", tx.rawSms ?: JSONObject.NULL)
                    })
                }
                put("transactions", txArray)

                // Splits
                val splitsArray = JSONArray()
                splits.forEach { s ->
                    splitsArray.put(JSONObject().apply {
                        put("id", s.id)
                        put("transactionId", s.transactionId)
                        put("personId", s.personId)
                        put("amountOwed", s.amountOwed)
                        put("amountPaidSoFar", s.amountPaidSoFar)
                        put("isSettled", s.isSettled)
                        put("createdAt", s.createdAt)
                    })
                }
                put("splits", splitsArray)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(rootJson.toString(2))
                    writer.flush()
                }
            } ?: return@withContext Result.failure(Exception("Unable to open export output stream"))

            Result.success(
                BackupStats(
                    accountCount = accounts.size,
                    categoryCount = categories.size,
                    vendorCount = vendors.size,
                    personCount = persons.size,
                    transactionCount = transactions.size,
                    splitCount = splits.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        prefs: AppPreferences
    ): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext Result.failure(Exception("Unable to open import input stream"))

            val rootJson = JSONObject(content)
            if (!rootJson.has("transactions") && !rootJson.has("categories")) {
                return@withContext Result.failure(Exception("Invalid CampusFold backup file format."))
            }

            var restoredAccounts = 0
            var restoredCategories = 0
            var restoredVendors = 0
            var restoredPersons = 0
            var restoredTransactions = 0
            var restoredSplits = 0

            db.runInTransaction {
                // 1. Restore Categories
                if (rootJson.has("categories")) {
                    val arr = rootJson.getJSONArray("categories")
                    val list = mutableListOf<CategoryEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            CategoryEntity(
                                id = obj.optLong("id", 0),
                                name = obj.getString("name"),
                                colorHex = obj.optString("colorHex", "#10B981"),
                                monthlyBudget = obj.optDouble("monthlyBudget", 0.0)
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        kotlinx.coroutines.runBlocking {
                            db.categoryDao().insertCategories(list)
                        }
                        restoredCategories = list.size
                    }
                }

                // 2. Restore Accounts
                if (rootJson.has("accounts")) {
                    val arr = rootJson.getJSONArray("accounts")
                    val list = mutableListOf<AccountEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            AccountEntity(
                                id = obj.optLong("id", 0),
                                name = obj.getString("name"),
                                bankName = obj.optString("bankName", ""),
                                accountType = obj.optString("accountType", "SAVINGS"),
                                last4 = obj.optString("last4", ""),
                                creditLimitOrBalance = obj.optDouble("creditLimitOrBalance", 0.0),
                                colorHex = obj.optString("colorHex", "#10B981")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        kotlinx.coroutines.runBlocking {
                            db.accountDao().insertAccounts(list)
                        }
                        restoredAccounts = list.size
                    }
                }

                // 3. Restore Vendor Mappings
                if (rootJson.has("vendorMappings")) {
                    val arr = rootJson.getJSONArray("vendorMappings")
                    val list = mutableListOf<VendorMappingEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId")
                        list.add(
                            VendorMappingEntity(
                                id = obj.optLong("id", 0),
                                rawSmsKeyword = obj.getString("rawSmsKeyword"),
                                friendlyName = obj.getString("friendlyName"),
                                categoryId = catId
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        kotlinx.coroutines.runBlocking {
                            db.vendorMappingDao().insertVendorMappings(list)
                        }
                        restoredVendors = list.size
                    }
                }

                // 4. Restore Persons
                if (rootJson.has("persons")) {
                    val arr = rootJson.getJSONArray("persons")
                    val list = mutableListOf<PersonEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            PersonEntity(
                                id = obj.optLong("id", 0),
                                name = obj.getString("name"),
                                exactSmsAliases = obj.optString("exactSmsAliases", "")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        kotlinx.coroutines.runBlocking {
                            db.personDao().insertPersons(list)
                        }
                        restoredPersons = list.size
                    }
                }

                // 5. Restore Transactions
                if (rootJson.has("transactions")) {
                    val arr = rootJson.getJSONArray("transactions")
                    val list = mutableListOf<TransactionEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val typeStr = obj.optString("type", "EXPENSE")
                        val txType = try {
                            TransactionType.valueOf(typeStr)
                        } catch (e: Exception) {
                            TransactionType.EXPENSE
                        }
                        val catId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId")
                        val upi = if (obj.isNull("upiRef")) null else obj.optString("upiRef")
                        val rawSms = if (obj.isNull("rawSms")) null else obj.optString("rawSms")

                        list.add(
                            TransactionEntity(
                                id = obj.optLong("id", 0),
                                amount = obj.getDouble("amount"),
                                myActualShare = obj.optDouble("myActualShare", obj.getDouble("amount")),
                                type = txType,
                                paymentMode = obj.optString("paymentMode", "Cash/Manual"),
                                rawMerchant = obj.optString("rawMerchant", "Store"),
                                friendlyVendorName = obj.optString("friendlyVendorName", "Store"),
                                categoryId = catId,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                upiRef = upi,
                                rawSms = rawSms
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        kotlinx.coroutines.runBlocking {
                            db.transactionDao().restoreTransactions(list)
                        }
                        restoredTransactions = list.size
                    }
                }

                // 6. Restore Splits
                if (rootJson.has("splits")) {
                    val arr = rootJson.getJSONArray("splits")
                    val list = mutableListOf<SplitItemEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            SplitItemEntity(
                                id = obj.optLong("id", 0),
                                transactionId = obj.getLong("transactionId"),
                                personId = obj.getLong("personId"),
                                amountOwed = obj.getDouble("amountOwed"),
                                amountPaidSoFar = obj.optDouble("amountPaidSoFar", 0.0),
                                isSettled = obj.optBoolean("isSettled", false),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        kotlinx.coroutines.runBlocking {
                            db.splitItemDao().insertSplitItems(list)
                        }
                        restoredSplits = list.size
                    }
                }

                // 7. Restore Preferences
                if (rootJson.has("preferences")) {
                    val prefObj = rootJson.getJSONObject("preferences")
                    kotlinx.coroutines.runBlocking {
                        if (prefObj.has("pnbCreditLimit")) {
                            prefs.setPnbCreditLimit(prefObj.getDouble("pnbCreditLimit"))
                        }
                        if (prefObj.has("overallMonthlyBudget")) {
                            prefs.setOverallMonthlyBudget(prefObj.getDouble("overallMonthlyBudget"))
                        }
                    }
                }
            }

            Result.success(
                BackupStats(
                    accountCount = restoredAccounts,
                    categoryCount = restoredCategories,
                    vendorCount = restoredVendors,
                    personCount = restoredPersons,
                    transactionCount = restoredTransactions,
                    splitCount = restoredSplits
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
