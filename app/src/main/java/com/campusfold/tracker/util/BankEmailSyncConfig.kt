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

    fun isBankSender(sender: String): Boolean {
        val lower = sender.lowercase()
        return BANK_SENDER_DOMAINS.any { domain -> lower.contains(domain) }
    }

    fun isBankSubject(subject: String): Boolean {
        val lower = subject.lowercase()
        return BANK_SUBJECT_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    fun matchesBankEmail(sender: String, subject: String): Boolean {
        return isBankSender(sender) || isBankSubject(subject)
    }
}
