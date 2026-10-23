package com.campusfold.tracker.data.resolver

import com.campusfold.tracker.data.entity.AccountType
import java.util.Locale

object AccountNormalizer {

    fun normalizeLast4(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 4) {
            digits.takeLast(4)
        } else if (digits.isNotEmpty()) {
            digits
        } else {
            null
        }
    }

    fun normalizeBank(rawBankName: String?): Pair<String, String> {
        val s = rawBankName?.trim() ?: ""
        val lower = s.lowercase(Locale.US)
        return when {
            lower.contains("axis") -> Pair("axis", "Axis Bank")
            lower.contains("hdfc") -> Pair("hdfc", "HDFC Bank")
            lower.contains("sbi") || lower.contains("state bank") -> Pair("sbi", "State Bank of India")
            lower.contains("icici") -> Pair("icici", "ICICI Bank")
            lower.contains("pnb") || lower.contains("punjab national") -> Pair("pnb", "Punjab National Bank")
            lower.contains("kotak") -> Pair("kotak", "Kotak Mahindra Bank")
            lower.contains("jupiter") || lower.contains("federal") -> Pair("federal", "Federal Bank")
            lower.contains("indusind") -> Pair("indusind", "IndusInd Bank")
            lower.contains("idfc") -> Pair("idfc", "IDFC FIRST Bank")
            lower.contains("baroda") || lower.contains("bob") -> Pair("bob", "Bank of Baroda")
            lower.contains("canara") -> Pair("canara", "Canara Bank")
            lower.contains("union") -> Pair("union", "Union Bank of India")
            lower.contains("yes") -> Pair("yes", "Yes Bank")
            lower.contains("rbl") -> Pair("rbl", "RBL Bank")
            lower.contains("idbi") -> Pair("idbi", "IDBI Bank")
            lower.contains("sc ") || lower.contains("standard chartered") -> Pair("sc", "Standard Chartered")
            lower.contains("hsbc") -> Pair("hsbc", "HSBC")
            lower.contains("onecard") -> Pair("onecard", "OneCard")
            lower.contains("slice") -> Pair("slice", "Slice")
            lower.contains("paytm") -> Pair("paytm", "Paytm Payments Bank")
            lower.contains("airtel") -> Pair("airtel", "Airtel Payments Bank")
            lower.isBlank() -> Pair("", "")
            else -> {
                val clean = s.replace(Regex("(?i)\\b(ltd|limited|corporation|corp|inc)\\b"), "").trim()
                val key = clean.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
                Pair(key, clean)
            }
        }
    }

    fun inferAccountType(text: String?): AccountType {
        if (text.isNullOrBlank()) return AccountType.SAVINGS
        val lower = text.lowercase(Locale.US)
        return when {
            listOf("credit", "card", "cc", "visa", "mastercard", "rupay", "overdraft").any { lower.contains(it) } -> AccountType.CREDIT_CARD
            listOf("wallet", "paytm wallet", "amazon pay", "phonepe wallet", "mobikwik").any { lower.contains(it) } -> AccountType.WALLET
            listOf("savings", "saving", "sb", "a/c", "acct", "current").any { lower.contains(it) } -> AccountType.SAVINGS
            else -> AccountType.SAVINGS
        }
    }

    fun formatDisplayName(bankDisplayName: String, last4: String?): String {
        return if (!last4.isNullOrBlank()) {
            "$bankDisplayName ••$last4"
        } else {
            bankDisplayName.ifBlank { "Unassigned Account" }
        }
    }
}
