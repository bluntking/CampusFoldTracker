/*
 * Adapted from PennyWise AI (https://github.com/sarim2000/pennywiseai-tracker)
 * Copyright (C) 2024-2026 Sarim Khan and Contributors
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0)
 *
 * This file is part of CampusFoldTracker.
 */

package com.campusfold.tracker.rules

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class TransactionField {
    AMOUNT,
    TYPE,
    CATEGORY,
    MERCHANT,
    NARRATION,
    SMS_TEXT,
    BANK_NAME,
    TRANSACTION_HOUR,
    TRANSACTION_TIME,
    TRANSACTION_DAY_OF_WEEK,
    TRANSACTION_DAY_OF_MONTH,
    TRANSACTION_DATE,
    ACCOUNT // Keyed "BankName||Last4"
}

enum class ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    REGEX_MATCHES,
    IS_EMPTY,
    IS_NOT_EMPTY
}

enum class LogicalOperator {
    AND,
    OR
}

enum class ActionType {
    SET,
    APPEND,
    CLEAR,
    BLOCK
}

data class RuleCondition(
    val field: TransactionField,
    val operator: ConditionOperator,
    val value: String,
    val logicalOperator: LogicalOperator = LogicalOperator.AND
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("field", field.name)
            put("operator", operator.name)
            put("value", value)
            put("logicalOperator", logicalOperator.name)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): RuleCondition {
            val field = TransactionField.valueOf(json.getString("field"))
            val operator = ConditionOperator.valueOf(json.getString("operator"))
            val value = json.optString("value", "")
            val logicalOpStr = json.optString("logicalOperator", "AND")
            val logicalOperator = runCatching { LogicalOperator.valueOf(logicalOpStr) }.getOrDefault(LogicalOperator.AND)
            return RuleCondition(field, operator, value, logicalOperator)
        }
    }
}

data class RuleAction(
    val field: TransactionField,
    val actionType: ActionType,
    val value: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("field", field.name)
            put("actionType", actionType.name)
            put("value", value)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): RuleAction {
            val field = TransactionField.valueOf(json.getString("field"))
            val actionType = ActionType.valueOf(json.getString("actionType"))
            val value = json.optString("value", "")
            return RuleAction(field, actionType, value)
        }
    }
}

data class RuleEvaluationResult(
    val modifiedTransaction: com.campusfold.tracker.data.entity.TransactionEntity,
    val isBlocked: Boolean,
    val blockingRuleId: Long? = null,
    val appliedRuleIds: List<Long> = emptyList(),
    val targetCategoryId: Long? = null,
    val targetCategoryName: String? = null
)
