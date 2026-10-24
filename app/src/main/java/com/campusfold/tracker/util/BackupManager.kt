package com.campusfold.tracker.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
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
    val splitCount: Int,
    val suggestionCount: Int = 0,
    val ruleCount: Int = 0
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
            val aliases = db.accountAliasDao().getAllAliasesSync()
            val categories = db.categoryDao().getAllCategoriesList()
            val vendors = db.vendorDao().getAllVendorsList()
            val persons = db.personDao().getAllPersonsList()
            val transactions = db.transactionDao().getAllTransactionsSync()
            val splits = db.splitItemDao().getAllSplitsSync()
            val suggestions = db.pendingSettlementSuggestionDao().getAllSuggestionsSync()
            val rules = db.ruleDao().getAllRulesSync()
            val overallBudget = prefs.getOverallMonthlyBudgetSync()

            val rootJson = JSONObject().apply {
                put("app", "CampusFoldTracker")
                put("version", 4)
                put("exportTimestamp", System.currentTimeMillis())
                put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date()))

                // Preferences
                put("preferences", JSONObject().apply {
                    put("overallMonthlyBudget", overallBudget)
                })

                // Accounts
                val accountsArray = JSONArray()
                accounts.forEach { acc ->
                    accountsArray.put(JSONObject().apply {
                        put("id", acc.id)
                        put("bankName", acc.bankName)
                        put("bankKey", acc.bankKey)
                        put("last4", acc.last4)
                        put("type", acc.type.name)
                        put("displayName", acc.displayName)
                        put("color", acc.color)
                        put("balancePaise", acc.balancePaise)
                        put("balanceAsOf", acc.balanceAsOf)
                        put("balanceSource", acc.balanceSource.name)
                        put("creditLimitPaise", acc.creditLimitPaise)
                        put("isArchived", acc.isArchived)
                        put("editedFields", acc.editedFields)
                        put("openingBalancePaise", acc.openingBalancePaise)
                        put("openingOwedPaise", acc.openingOwedPaise)
                        put("openingAsOf", acc.openingAsOf)
                        put("anchorSet", acc.anchorSet)
                        // Backward-compatibility keys for older versions
                        put("name", acc.displayName)
                        put("accountType", acc.type.name)
                        put("creditLimitOrBalance", acc.creditLimitOrBalance)
                        put("colorHex", acc.color)
                    })
                }
                put("accounts", accountsArray)

                // Account Aliases
                val aliasesArray = JSONArray()
                aliases.forEach { al ->
                    aliasesArray.put(JSONObject().apply {
                        put("accountId", al.accountId)
                        put("bankKey", al.bankKey)
                        put("last4", al.last4)
                    })
                }
                put("accountAliases", aliasesArray)

                // Categories
                val categoriesArray = JSONArray()
                categories.forEach { cat ->
                    categoriesArray.put(JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("color", cat.color)
                        put("budgetPaise", cat.budgetPaise ?: JSONObject.NULL)
                        put("editedFields", cat.editedFields)
                        // Backward-compatibility keys
                        put("colorHex", cat.color)
                        put("monthlyBudget", cat.monthlyBudget)
                    })
                }
                put("categories", categoriesArray)

                // Vendors
                val vendorsArray = JSONArray()
                vendors.forEach { v ->
                    vendorsArray.put(JSONObject().apply {
                        put("id", v.id)
                        put("displayName", v.displayName)
                        put("normalizedKey", v.normalizedKey)
                        put("categoryId", v.categoryId ?: JSONObject.NULL)
                        put("editedFields", v.editedFields)
                        // Backward-compatibility keys
                        put("friendlyName", v.displayName)
                        put("rawSmsKeyword", v.normalizedKey)
                    })
                }
                put("vendors", vendorsArray)
                put("vendorMappings", vendorsArray) // Dual-key for backward compatibility

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
                        put("amountPaise", tx.amountPaise)
                        put("myActualSharePaise", tx.myActualSharePaise)
                        put("type", tx.type.name)
                        put("paymentMode", tx.paymentMode)
                        put("rawMerchant", tx.rawMerchant)
                        put("friendlyVendorName", tx.friendlyVendorName)
                        put("categoryId", tx.categoryId ?: JSONObject.NULL)
                        put("timestamp", tx.timestamp)
                        put("upiRef", tx.upiRef ?: JSONObject.NULL)
                        put("rawSms", tx.rawSms ?: JSONObject.NULL)
                        put("description", tx.description ?: JSONObject.NULL)
                        put("accountId", tx.accountId ?: JSONObject.NULL)
                        put("reference", tx.reference ?: JSONObject.NULL)
                        put("source", tx.source.name)
                        put("confidence", tx.confidence)
                        put("rawMessageHash", tx.rawMessageHash ?: JSONObject.NULL)
                        put("editedFields", tx.editedFields)
                        put("paidByPersonId", tx.paidByPersonId ?: JSONObject.NULL)
                        // Backward-compatibility keys
                        put("amount", tx.amount)
                        put("myActualShare", tx.myActualShare)
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
                        put("amountOwedPaise", s.amountOwedPaise)
                        put("amountPaidSoFarPaise", s.amountPaidSoFarPaise)
                        put("isSettled", s.isSettled)
                        put("createdAt", s.createdAt)
                        put("direction", s.direction.name)
                        // Backward-compatibility keys
                        put("amountOwed", s.amountOwed)
                        put("amountPaidSoFar", s.amountPaidSoFar)
                    })
                }
                put("splits", splitsArray)

                // Pending Settlement Suggestions
                val suggestionsArray = JSONArray()
                suggestions.forEach { s ->
                    suggestionsArray.put(JSONObject().apply {
                        put("id", s.id)
                        put("transactionId", s.transactionId ?: JSONObject.NULL)
                        put("suggestedPersonId", s.suggestedPersonId)
                        put("detectedSenderName", s.detectedSenderName)
                        put("amountPaise", s.amountPaise)
                        put("status", s.status.name)
                        put("direction", s.direction.name)
                        // Backward-compatibility keys
                        put("amount", s.amount)
                    })
                }
                put("pendingSettlementSuggestions", suggestionsArray)

                // Rules
                val rulesArray = JSONArray()
                rules.forEach { r ->
                    rulesArray.put(JSONObject().apply {
                        put("id", r.id)
                        put("conditionsJson", r.conditionsJson)
                        put("actionsJson", r.actionsJson)
                        put("priority", r.priority)
                        put("enabled", r.enabled)
                        put("source", r.source.name)
                    })
                }
                put("rules", rulesArray)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                java.io.BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
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
                    splitCount = splits.size,
                    suggestionCount = suggestions.size,
                    ruleCount = rules.size
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
            var restoredSuggestions = 0
            var restoredRules = 0

            var budgetToRestore: Double? = null
            if (rootJson.has("preferences")) {
                val prefObj = rootJson.getJSONObject("preferences")
                if (prefObj.has("overallMonthlyBudget")) {
                    budgetToRestore = prefObj.getDouble("overallMonthlyBudget")
                }
            }

            db.withTransaction {
                // 1. Restore Categories
                if (rootJson.has("categories")) {
                    val arr = rootJson.getJSONArray("categories")
                    val list = mutableListOf<CategoryEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val budgetPaise = if (obj.has("budgetPaise") && !obj.isNull("budgetPaise")) {
                            obj.getLong("budgetPaise")
                        } else if (obj.has("monthlyBudget")) {
                            val mb = obj.optDouble("monthlyBudget", 0.0)
                            if (mb > 0) CurrencyUtils.rupeesToPaise(mb) else null
                        } else null

                        list.add(
                            CategoryEntity(
                                id = obj.optLong("id", 0),
                                name = obj.getString("name"),
                                color = obj.optString("color", obj.optString("colorHex", "#10B981")),
                                budgetPaise = budgetPaise,
                                editedFields = obj.optString("editedFields", "[]")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.categoryDao().insertCategories(list)
                        restoredCategories = list.size
                    }
                }

                // 2. Restore Accounts
                if (rootJson.has("accounts")) {
                    val arr = rootJson.getJSONArray("accounts")
                    val list = mutableListOf<AccountEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val displayName = obj.optString("displayName", obj.optString("name", "Account"))
                        val bankName = obj.optString("bankName", "")
                        val bankKey = obj.optString("bankKey", bankName.trim().uppercase())
                        val last4 = obj.optString("last4", "")
                        val typeStr = obj.optString("type", obj.optString("accountType", "UNASSIGNED"))
                        val type = try {
                            AccountType.valueOf(typeStr)
                        } catch (_: Exception) {
                            AccountType.UNASSIGNED
                        }

                        val balancePaise = if (obj.has("balancePaise")) {
                            obj.getLong("balancePaise")
                        } else if (obj.has("creditLimitOrBalance") && type != AccountType.CREDIT_CARD) {
                            CurrencyUtils.rupeesToPaise(obj.optDouble("creditLimitOrBalance", 0.0))
                        } else 0L

                        val creditLimitPaise = if (obj.has("creditLimitPaise")) {
                            obj.getLong("creditLimitPaise")
                        } else if (obj.has("creditLimitOrBalance") && type == AccountType.CREDIT_CARD) {
                            CurrencyUtils.rupeesToPaise(obj.optDouble("creditLimitOrBalance", 0.0))
                        } else 0L

                        val balanceSourceStr = obj.optString("balanceSource", "MANUAL")
                        val balanceSource = try {
                            BalanceSource.valueOf(balanceSourceStr)
                        } catch (_: Exception) {
                            BalanceSource.MANUAL
                        }

                        val balanceAsOf = obj.optLong("balanceAsOf", System.currentTimeMillis())
                        val openingBalancePaise = if (obj.has("openingBalancePaise")) {
                            obj.getLong("openingBalancePaise")
                        } else if (balanceSource == BalanceSource.MANUAL && type != AccountType.CREDIT_CARD) {
                            balancePaise
                        } else 0L

                        val openingOwedPaise = if (obj.has("openingOwedPaise")) {
                            obj.getLong("openingOwedPaise")
                        } else if (balanceSource == BalanceSource.MANUAL && type == AccountType.CREDIT_CARD) {
                            balancePaise
                        } else 0L

                        val openingAsOf = obj.optLong("openingAsOf", if (balanceSource == BalanceSource.MANUAL) balanceAsOf else 0L)
                        val anchorSet = obj.optBoolean("anchorSet", balanceSource == BalanceSource.MANUAL && (openingBalancePaise > 0L || openingOwedPaise > 0L || obj.has("openingAsOf")))

                        list.add(
                            AccountEntity(
                                id = obj.optLong("id", 0),
                                bankName = bankName,
                                bankKey = bankKey,
                                last4 = last4,
                                type = type,
                                displayName = displayName,
                                color = obj.optString("color", obj.optString("colorHex", "#10B981")),
                                balancePaise = balancePaise,
                                balanceAsOf = balanceAsOf,
                                balanceSource = balanceSource,
                                creditLimitPaise = creditLimitPaise,
                                isArchived = obj.optBoolean("isArchived", false),
                                editedFields = obj.optString("editedFields", "[]"),
                                openingBalancePaise = openingBalancePaise,
                                openingOwedPaise = openingOwedPaise,
                                openingAsOf = openingAsOf,
                                anchorSet = anchorSet
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.accountDao().insertAccounts(list)
                        restoredAccounts = list.size
                    }
                }

                // 3. Restore Account Aliases
                if (rootJson.has("accountAliases")) {
                    val arr = rootJson.getJSONArray("accountAliases")
                    val list = mutableListOf<AccountAliasEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            AccountAliasEntity(
                                accountId = obj.getLong("accountId"),
                                bankKey = obj.getString("bankKey"),
                                last4 = obj.getString("last4")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.accountAliasDao().insertAliases(list)
                    }
                }

                // 4. Restore Vendors
                val vendorsArray = when {
                    rootJson.has("vendors") -> rootJson.getJSONArray("vendors")
                    rootJson.has("vendorMappings") -> rootJson.getJSONArray("vendorMappings")
                    else -> null
                }
                if (vendorsArray != null) {
                    val list = mutableListOf<VendorEntity>()
                    for (i in 0 until vendorsArray.length()) {
                        val obj = vendorsArray.getJSONObject(i)
                        val catId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId")
                        val displayName = obj.optString("displayName", obj.optString("friendlyName", "Vendor"))
                        val normalizedKey = obj.optString("normalizedKey", obj.optString("rawSmsKeyword", displayName)).trim().lowercase()

                        list.add(
                            VendorEntity(
                                id = obj.optLong("id", 0),
                                displayName = displayName,
                                normalizedKey = normalizedKey,
                                categoryId = catId,
                                editedFields = obj.optString("editedFields", "[]")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.vendorDao().insertVendors(list)
                        restoredVendors = list.size
                    }
                }

                // 5. Restore Persons
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
                        db.personDao().insertPersons(list)
                        restoredPersons = list.size
                    }
                }

                // 6. Restore Transactions
                if (rootJson.has("transactions")) {
                    val arr = rootJson.getJSONArray("transactions")
                    val list = mutableListOf<TransactionEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val typeStr = obj.optString("type", "EXPENSE")
                        val txType = try {
                            TransactionType.valueOf(typeStr)
                        } catch (_: Exception) {
                            TransactionType.EXPENSE
                        }

                        val amountPaise = if (obj.has("amountPaise")) {
                            obj.getLong("amountPaise")
                        } else {
                            CurrencyUtils.rupeesToPaise(obj.getDouble("amount"))
                        }

                        val myActualSharePaise = if (obj.has("myActualSharePaise")) {
                            obj.getLong("myActualSharePaise")
                        } else if (obj.has("myActualShare")) {
                            CurrencyUtils.rupeesToPaise(obj.getDouble("myActualShare"))
                        } else {
                            amountPaise
                        }

                        val sourceStr = obj.optString("source", "MANUAL")
                        val source = try {
                            TransactionSource.valueOf(sourceStr)
                        } catch (_: Exception) {
                            TransactionSource.MANUAL
                        }

                        val catId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId")
                        val upi = if (obj.isNull("upiRef")) null else obj.optString("upiRef")
                        val rawSms = if (obj.isNull("rawSms")) null else obj.optString("rawSms")
                        val description = if (obj.isNull("description")) null else obj.optString("description")
                        val accountId = if (obj.has("accountId") && !obj.isNull("accountId")) obj.optLong("accountId") else null
                        val reference = if (obj.has("reference") && !obj.isNull("reference")) obj.optString("reference") else null
                        val paidByPersonId = if (obj.has("paidByPersonId") && !obj.isNull("paidByPersonId")) obj.optLong("paidByPersonId") else null

                        list.add(
                            TransactionEntity(
                                id = obj.optLong("id", 0),
                                amountPaise = amountPaise,
                                myActualSharePaise = myActualSharePaise,
                                type = txType,
                                paymentMode = obj.optString("paymentMode", "Cash/Manual"),
                                rawMerchant = obj.optString("rawMerchant", "Store"),
                                friendlyVendorName = obj.optString("friendlyVendorName", "Store"),
                                categoryId = catId,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                upiRef = upi,
                                rawSms = rawSms,
                                description = description,
                                accountId = accountId,
                                reference = reference,
                                source = source,
                                confidence = obj.optDouble("confidence", 1.0).toFloat(),
                                rawMessageHash = if (obj.has("rawMessageHash") && !obj.isNull("rawMessageHash")) obj.optString("rawMessageHash") else null,
                                editedFields = obj.optString("editedFields", "[]"),
                                paidByPersonId = paidByPersonId
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.transactionDao().restoreTransactions(list)
                        restoredTransactions = list.size
                    }
                }

                // 7. Restore Splits
                if (rootJson.has("splits")) {
                    val arr = rootJson.getJSONArray("splits")
                    val list = mutableListOf<SplitItemEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val owedPaise = if (obj.has("amountOwedPaise")) {
                            obj.getLong("amountOwedPaise")
                        } else {
                            CurrencyUtils.rupeesToPaise(obj.getDouble("amountOwed"))
                        }

                        val paidPaise = if (obj.has("amountPaidSoFarPaise")) {
                            obj.getLong("amountPaidSoFarPaise")
                        } else {
                            CurrencyUtils.rupeesToPaise(obj.optDouble("amountPaidSoFar", 0.0))
                        }

                        val directionStr = obj.optString("direction", "OWED_TO_ME")
                        val splitDirection = try {
                            SplitDirection.valueOf(directionStr)
                        } catch (_: Exception) {
                            SplitDirection.OWED_TO_ME
                        }

                        list.add(
                            SplitItemEntity(
                                id = obj.optLong("id", 0),
                                transactionId = obj.getLong("transactionId"),
                                personId = obj.getLong("personId"),
                                amountOwedPaise = owedPaise,
                                amountPaidSoFarPaise = paidPaise,
                                isSettled = obj.optBoolean("isSettled", false),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                direction = splitDirection
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.splitItemDao().insertSplitItems(list)
                        restoredSplits = list.size
                    }
                }

                // 8. Restore Pending Settlement Suggestions
                if (rootJson.has("pendingSettlementSuggestions")) {
                    val arr = rootJson.getJSONArray("pendingSettlementSuggestions")
                    val list = mutableListOf<PendingSettlementSuggestionEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val txId = if (obj.isNull("transactionId")) null else obj.optLong("transactionId")
                        val statusStr = obj.optString("status", "PENDING")
                        val status = try {
                            SettlementSuggestionStatus.valueOf(statusStr)
                        } catch (_: Exception) {
                            SettlementSuggestionStatus.PENDING
                        }

                        val amtPaise = if (obj.has("amountPaise")) {
                            obj.getLong("amountPaise")
                        } else {
                            CurrencyUtils.rupeesToPaise(obj.getDouble("amount"))
                        }

                        val directionStr = obj.optString("direction", "OWED_TO_ME")
                        val suggestionDirection = try {
                            SplitDirection.valueOf(directionStr)
                        } catch (_: Exception) {
                            SplitDirection.OWED_TO_ME
                        }

                        list.add(
                            PendingSettlementSuggestionEntity(
                                id = obj.optLong("id", 0),
                                transactionId = txId,
                                suggestedPersonId = obj.getLong("suggestedPersonId"),
                                detectedSenderName = obj.optString("detectedSenderName", ""),
                                amountPaise = amtPaise,
                                status = status,
                                direction = suggestionDirection
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.pendingSettlementSuggestionDao().insertSuggestions(list)
                        restoredSuggestions = list.size
                    }
                }

                // 9. Restore Rules
                if (rootJson.has("rules")) {
                    val arr = rootJson.getJSONArray("rules")
                    val list = mutableListOf<RuleEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val sourceStr = obj.optString("source", "USER")
                        val source = try {
                            RuleSource.valueOf(sourceStr)
                        } catch (_: Exception) {
                            RuleSource.USER
                        }
                        list.add(
                            RuleEntity(
                                id = obj.optLong("id", 0),
                                conditionsJson = obj.getString("conditionsJson"),
                                actionsJson = obj.getString("actionsJson"),
                                priority = obj.optInt("priority", 0),
                                enabled = obj.optBoolean("enabled", true),
                                source = source
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        db.ruleDao().insertRules(list)
                        restoredRules = list.size
                    }
                }
            }

            // 10. Restore Preferences outside DB transaction
            if (budgetToRestore != null) {
                prefs.setOverallMonthlyBudget(budgetToRestore)
            }

            Result.success(
                BackupStats(
                    accountCount = restoredAccounts,
                    categoryCount = restoredCategories,
                    vendorCount = restoredVendors,
                    personCount = restoredPersons,
                    transactionCount = restoredTransactions,
                    splitCount = restoredSplits,
                    suggestionCount = restoredSuggestions,
                    ruleCount = restoredRules
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
