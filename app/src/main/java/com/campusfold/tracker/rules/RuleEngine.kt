/*
 * Adapted from PennyWise AI (https://github.com/sarim2000/pennywiseai-tracker)
 * Copyright (C) 2024-2026 Sarim Khan and Contributors
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0)
 *
 * This file is part of CampusFoldTracker.
 */

package com.campusfold.tracker.rules

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.RuleEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

object RuleEngine {

    fun parseConditions(jsonStr: String): List<RuleCondition> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<RuleCondition>()
            for (i in 0 until arr.length()) {
                list.add(RuleCondition.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeConditions(conditions: List<RuleCondition>): String {
        val arr = JSONArray()
        for (c in conditions) {
            arr.put(c.toJson())
        }
        return arr.toString()
    }

    fun parseActions(jsonStr: String): List<RuleAction> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<RuleAction>()
            for (i in 0 until arr.length()) {
                list.add(RuleAction.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeActions(actions: List<RuleAction>): String {
        val arr = JSONArray()
        for (a in actions) {
            arr.put(a.toJson())
        }
        return arr.toString()
    }

    /**
     * Evaluates active rules against the given transaction, raw SMS, and account.
     * Rules run in descending order of priority.
     * Any field listed in transaction.editedFields is strictly preserved and not modified by actions.
     */
    fun evaluateRules(
        transaction: TransactionEntity,
        rawSms: String?,
        account: AccountEntity?,
        rules: List<RuleEntity>,
        categoryNameLookup: ((Long) -> String?)? = null
    ): RuleEvaluationResult {
        val sortedRules = rules.filter { it.enabled }.sortedByDescending { it.priority }

        var currentTx = transaction
        var isBlocked = false
        var blockingRuleId: Long? = null
        val appliedRules = mutableListOf<Long>()
        var targetCatName: String? = null

        val editedFieldsList = parseEditedFields(transaction.editedFields)

        for (rule in sortedRules) {
            val conditions = parseConditions(rule.conditionsJson)
            val actions = parseActions(rule.actionsJson)

            if (evaluateConditions(currentTx, rawSms, account, conditions, categoryNameLookup)) {
                appliedRules.add(rule.id)

                // Check for BLOCK action
                if (actions.any { it.actionType == ActionType.BLOCK }) {
                    isBlocked = true
                    blockingRuleId = rule.id
                    break
                }

                // Apply actions (respecting editedFields)
                for (action in actions) {
                    when (action.field) {
                        TransactionField.CATEGORY -> {
                            if (!editedFieldsList.contains("categoryId")) {
                                targetCatName = action.value.trim()
                            }
                        }

                        TransactionField.MERCHANT -> {
                            if (!editedFieldsList.contains("friendlyVendorName")) {
                                val currentMerchant = currentTx.friendlyVendorName
                                val newMerchant = when (action.actionType) {
                                    ActionType.SET -> action.value.trim()
                                    ActionType.APPEND -> "$currentMerchant ${action.value.trim()}".trim()
                                    ActionType.CLEAR -> ""
                                    else -> currentMerchant
                                }
                                currentTx = currentTx.copy(friendlyVendorName = newMerchant)
                            }
                        }

                        TransactionField.NARRATION -> {
                            if (!editedFieldsList.contains("description")) {
                                val currentDesc = currentTx.description ?: ""
                                val newDesc = when (action.actionType) {
                                    ActionType.SET -> action.value.trim()
                                    ActionType.APPEND -> "$currentDesc ${action.value.trim()}".trim()
                                    ActionType.CLEAR -> null
                                    else -> currentDesc
                                }
                                currentTx = currentTx.copy(description = newDesc)
                            }
                        }

                        TransactionField.TYPE -> {
                            if (!editedFieldsList.contains("type")) {
                                val mappedType = runCatching { TransactionType.valueOf(action.value.trim().uppercase(Locale.US)) }.getOrNull()
                                if (mappedType != null) {
                                    currentTx = currentTx.copy(type = mappedType)
                                }
                            }
                        }

                        TransactionField.BANK_NAME -> {
                            if (!editedFieldsList.contains("paymentMode")) {
                                currentTx = currentTx.copy(paymentMode = action.value.trim())
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

        return RuleEvaluationResult(
            modifiedTransaction = currentTx,
            isBlocked = isBlocked,
            blockingRuleId = blockingRuleId,
            appliedRuleIds = appliedRules,
            targetCategoryName = targetCatName
        )
    }

    private fun evaluateConditions(
        transaction: TransactionEntity,
        rawSms: String?,
        account: AccountEntity?,
        conditions: List<RuleCondition>,
        categoryNameLookup: ((Long) -> String?)?
    ): Boolean {
        if (conditions.isEmpty()) return true

        var result = evaluateCondition(transaction, rawSms, account, conditions[0], categoryNameLookup)
        for (i in 1 until conditions.size) {
            val cond = conditions[i]
            val condResult = evaluateCondition(transaction, rawSms, account, cond, categoryNameLookup)
            result = when (cond.logicalOperator) {
                LogicalOperator.AND -> result && condResult
                LogicalOperator.OR -> result || condResult
            }
        }
        return result
    }

    fun evaluateCondition(
        transaction: TransactionEntity,
        rawSms: String?,
        account: AccountEntity?,
        condition: RuleCondition,
        categoryNameLookup: ((Long) -> String?)? = null
    ): Boolean {
        val fieldValue = getFieldValue(transaction, rawSms, account, condition.field, categoryNameLookup)
        val targetValue = condition.value.trim()

        val isAmount = condition.field == TransactionField.AMOUNT
        val isNumericComparison = isAmount && targetValue.toBigDecimalOrNull() != null && fieldValue.toBigDecimalOrNull() != null

        return when (condition.operator) {
            ConditionOperator.EQUALS -> {
                if (isNumericComparison) compareNumeric(fieldValue, targetValue) == 0
                else fieldValue.equals(targetValue, ignoreCase = true)
            }
            ConditionOperator.NOT_EQUALS -> {
                if (isNumericComparison) compareNumeric(fieldValue, targetValue) != 0
                else !fieldValue.equals(targetValue, ignoreCase = true)
            }
            ConditionOperator.CONTAINS -> fieldValue.contains(targetValue, ignoreCase = true)
            ConditionOperator.NOT_CONTAINS -> !fieldValue.contains(targetValue, ignoreCase = true)
            ConditionOperator.STARTS_WITH -> fieldValue.startsWith(targetValue, ignoreCase = true)
            ConditionOperator.ENDS_WITH -> fieldValue.endsWith(targetValue, ignoreCase = true)
            ConditionOperator.LESS_THAN -> compareNumeric(fieldValue, targetValue) < 0
            ConditionOperator.GREATER_THAN -> compareNumeric(fieldValue, targetValue) > 0
            ConditionOperator.LESS_THAN_OR_EQUAL -> compareNumeric(fieldValue, targetValue) <= 0
            ConditionOperator.GREATER_THAN_OR_EQUAL -> compareNumeric(fieldValue, targetValue) >= 0
            ConditionOperator.IN -> targetValue.split(",").map { it.trim() }.any { fieldValue.equals(it, ignoreCase = true) }
            ConditionOperator.NOT_IN -> !targetValue.split(",").map { it.trim() }.any { fieldValue.equals(it, ignoreCase = true) }
            ConditionOperator.REGEX_MATCHES -> runCatching { fieldValue.matches(Regex(targetValue, RegexOption.IGNORE_CASE)) }.getOrDefault(false)
            ConditionOperator.IS_EMPTY -> fieldValue.isBlank()
            ConditionOperator.IS_NOT_EMPTY -> fieldValue.isNotBlank()
        }
    }

    private fun getFieldValue(
        transaction: TransactionEntity,
        rawSms: String?,
        account: AccountEntity?,
        field: TransactionField,
        categoryNameLookup: ((Long) -> String?)?
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }

        return when (field) {
            TransactionField.AMOUNT -> (transaction.amountPaise / 100.0).toString()
            TransactionField.TYPE -> transaction.type.name
            TransactionField.CATEGORY -> {
                transaction.categoryId?.let { categoryNameLookup?.invoke(it) } ?: ""
            }
            TransactionField.MERCHANT -> {
                transaction.friendlyVendorName.ifBlank { transaction.rawMerchant }
            }
            TransactionField.NARRATION -> transaction.description ?: ""
            TransactionField.SMS_TEXT -> rawSms ?: transaction.rawSms ?: ""
            TransactionField.BANK_NAME -> account?.bankName ?: transaction.paymentMode
            TransactionField.TRANSACTION_HOUR -> String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY))
            TransactionField.TRANSACTION_TIME -> String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            TransactionField.TRANSACTION_DAY_OF_WEEK -> {
                // Map Sunday=1 .. Saturday=7 to ISO Monday=1 .. Sunday=7
                val day = cal.get(Calendar.DAY_OF_WEEK)
                val isoDay = if (day == Calendar.SUNDAY) 7 else day - 1
                isoDay.toString()
            }
            TransactionField.TRANSACTION_DAY_OF_MONTH -> String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
            TransactionField.TRANSACTION_DATE -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            TransactionField.ACCOUNT -> {
                // Keyed "BankName||Last4"
                val bank = account?.bankName ?: transaction.paymentMode
                val last4 = account?.last4 ?: ""
                if (bank.isNotBlank() && last4.isNotBlank()) "$bank||$last4" else ""
            }
        }
    }

    private fun compareNumeric(val1: String, val2: String): Int {
        return try {
            BigDecimal(val1).compareTo(BigDecimal(val2))
        } catch (_: Exception) {
            val1.compareTo(val2, ignoreCase = true)
        }
    }

    private fun parseEditedFields(editedFieldsJson: String): Set<String> {
        if (editedFieldsJson.isBlank() || editedFieldsJson == "[]") return emptySet()
        return try {
            val arr = JSONArray(editedFieldsJson)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }
}
