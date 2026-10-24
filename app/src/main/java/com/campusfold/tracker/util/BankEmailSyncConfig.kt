package com.campusfold.tracker.util

/**
 * Central configuration for bank email synchronization.
 * Contains sender domains and subject keywords for Indian banking alerts.
 */
object BankEmailSyncConfig {

    val BANK_SENDER_DOMAINS = listOf(
        "axisbank.com",
        "axis.bank.in",
        "alerts.axisbank.com",
        "hdfcbank.com",
        "hdfcbank.net",
        "icicibank.com",
        "sbi.co.in",
        "pnb.co.in",
        "pnbindia.in",
        "kotak.com",
        "kotakbank.com",
        "indusind.com",
        "idfcfirstbank.com",
        "federalbank.co.in",
        "jupiter.money",
        "bankofbaroda.com",
        "bankofbaroda.co.in",
        "canarabank.com",
        "unionbankofindia.bank",
        "yesbank.in",
        "rblbank.com",
        "idbibank.co.in",
        "sc.com",
        "hsbc.co.in",
        "sliceit.com",
        "onecard.co"
    )

    val BANK_SUBJECT_KEYWORDS = listOf(
        "debited",
        "credited",
        "transaction",
        "upi",
        "a/c",
        "card",
        "statement alert",
        "spent",
        "withdrawn",
        "received",
        "sent",
        "payment",
        "alert",
        "transferred"
    )

    enum class EmailMatchResult {
        MATCHED_BY_SENDER,
        MATCHED_BY_SUBJECT_ONLY,
        SKIPPED
    }

    fun isBankSender(sender: String, customSenders: Set<String> = emptySet()): Boolean {
        val lower = sender.lowercase().trim()
        val inDefaultList = BANK_SENDER_DOMAINS.any { domain -> lower.contains(domain) }
        if (inDefaultList) return true

        val inCustomList = customSenders.any { custom -> custom.isNotBlank() && lower.contains(custom.lowercase().trim()) }
        if (inCustomList) return true

        // RBI compliance: .bank.in and .fin.in domains
        val domainMatches = lower.contains(".bank.in") || lower.contains(".fin.in") ||
                Regex("""@[a-z0-9.-]+\.(bank\.in|fin\.in)\b""").containsMatchIn(lower)
        return domainMatches
    }

    fun isBankSubject(subject: String): Boolean {
        val lower = subject.lowercase()
        return BANK_SUBJECT_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    /**
     * RBI compliant email filter.
     * Subject keywords count ONLY when the sender is a known bank sender or custom user added sender.
     * If scanAllSenders is enabled in Settings, subject-only matches are permitted as a fallback.
     */
    fun matchesBankEmail(
        sender: String,
        subject: String,
        customSenders: Set<String> = emptySet(),
        scanAllSenders: Boolean = false
    ): Boolean {
        val senderMatched = isBankSender(sender, customSenders)
        val subjectMatched = isBankSubject(subject)

        return if (scanAllSenders) {
            senderMatched || subjectMatched
        } else {
            senderMatched && subjectMatched
        }
    }

    fun classifyEmail(
        sender: String,
        subject: String,
        customSenders: Set<String> = emptySet(),
        scanAllSenders: Boolean = false
    ): EmailMatchResult {
        val senderMatched = isBankSender(sender, customSenders)
        val subjectMatched = isBankSubject(subject)

        return when {
            senderMatched && subjectMatched -> EmailMatchResult.MATCHED_BY_SENDER
            subjectMatched && scanAllSenders -> EmailMatchResult.MATCHED_BY_SUBJECT_ONLY
            else -> EmailMatchResult.SKIPPED
        }
    }
}
