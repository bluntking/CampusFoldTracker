package com.campusfold.tracker.data.resolver

import com.campusfold.tracker.data.entity.VendorEntity
import java.util.Locale

object VendorResolver {

    fun cleanMerchantString(raw: String): String {
        var s = raw.trim()
        if (s.isBlank()) return "Merchant"

        // Strip reference numbers like "/123456789/", "Ref 12345", "UTR 12345", etc.
        s = s.replace(Regex("(?i)\\b(ref|utr|rrn|txn|id)[:\\s-]*[a-z0-9]+\\b"), "")
        s = s.replace(Regex("/[0-9]{6,}/"), "")

        // Strip common prefixes
        s = s.replace(Regex("^(?i)(UPI[-/]|POS[-/]|ATM[-/]|NET[- ]?BANKING[-/]|IMPS[-/]|NEFT[-/]|RTGS[-/]|BILLDESK[-/]|PAYU[-/]|RAZORPAY[-/]|AIRPAY[-/])"), "")
        s = s.replace(Regex("^(?i)(CR[-/]|DR[-/])"), "")

        // If it's a VPA (contains @), clean it
        if (s.contains("@")) {
            val handle = s.substringBefore("@")
            // Clean common payment aggregator prefixes e.g. "paytmqr2810..." or trailing digits
            val cleanedHandle = handle.replace(Regex("\\d+$"), "").replace(Regex("[._-]+"), " ").trim()
            if (cleanedHandle.isNotBlank()) {
                return capitalizeWords(cleanedHandle)
            }
        }

        s = s.replace(Regex("[._-]+"), " ").trim()
        s = s.replace(Regex("\\s+"), " ")
        return if (s.isBlank()) raw.trim() else capitalizeWords(s)
    }

    /**
     * Matches existing vendors by exact normalized key or whole-word match ONLY.
     * NEVER substring matches!
     */
    fun findMatchingVendor(cleanName: String, rawMerchant: String, existingVendors: List<VendorEntity>): VendorEntity? {
        val cleanLower = cleanName.lowercase(Locale.US)
        val rawLower = rawMerchant.lowercase(Locale.US)

        // 1. Exact match on normalizedKey
        val exactMatch = existingVendors.find {
            it.normalizedKey.equals(cleanLower, ignoreCase = true) ||
                    it.normalizedKey.equals(rawLower, ignoreCase = true) ||
                    it.displayName.equals(cleanName, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        // 2. Whole-word match ONLY (never substring)
        for (v in existingVendors) {
            val vendorKey = v.normalizedKey.trim().lowercase(Locale.US)
            if (vendorKey.isBlank()) continue

            // Word-boundary regex
            val pattern = Regex("""\b${Regex.escape(vendorKey)}\b""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(cleanLower) || pattern.containsMatchIn(rawLower)) {
                return v
            }
        }

        return null
    }

    private fun capitalizeWords(str: String): String {
        return str.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
    }
}
