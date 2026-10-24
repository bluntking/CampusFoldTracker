package com.campusfold.tracker

import com.campusfold.tracker.util.BankEmailSyncConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankEmailFilterTest {

    @Test
    fun testDefaultBankSendersMatch() {
        assertTrue(BankEmailSyncConfig.isBankSender("alerts@axisbank.com"))
        assertTrue(BankEmailSyncConfig.isBankSender("AXIS BANK <alerts@axisbank.com>"))
        assertTrue(BankEmailSyncConfig.isBankSender("creditcards@hdfcbank.net"))
        assertTrue(BankEmailSyncConfig.isBankSender("notify@icicibank.com"))
    }

    @Test
    fun testRbiSpecialDomainsMatch() {
        // RBI reserved financial TLDs (.bank.in and .fin.in)
        assertTrue(BankEmailSyncConfig.isBankSender("alerts@axis.bank.in"))
        assertTrue(BankEmailSyncConfig.isBankSender("Axis Bank Alerts <alerts@axis.bank.in>"))
        assertTrue(BankEmailSyncConfig.isBankSender("alerts@sbi.bank.in"))
        assertTrue(BankEmailSyncConfig.isBankSender("State Bank <noreply@statebank.bank.in>"))
        assertTrue(BankEmailSyncConfig.isBankSender("statements@cred.fin.in"))
    }

    @Test
    fun testCustomBankSenderMatch() {
        val customSenders = setOf("updates@jupiter.money", "alerts@fi.care")
        assertTrue(BankEmailSyncConfig.isBankSender("updates@jupiter.money", customSenders))
        assertTrue(BankEmailSyncConfig.isBankSender("alerts@fi.care", customSenders))
        assertFalse(BankEmailSyncConfig.isBankSender("newsletter@random.com", customSenders))
    }

    @Test
    fun testNonBankSendersRejectedByDefault() {
        assertFalse(BankEmailSyncConfig.isBankSender("promotions@swiggy.in"))
        assertFalse(BankEmailSyncConfig.isBankSender("receipts@uber.com"))
        assertFalse(BankEmailSyncConfig.isBankSender("support@google.com"))
    }

    @Test
    fun testMatchesBankEmailWithScanAllSenders() {
        val subject = "Transaction alert: INR 500 debited from account XX1234"
        val nonBankSender = "alerts@genericpayment.com"

        // When scanAllSenders is false, non-bank sender is rejected
        assertFalse(BankEmailSyncConfig.matchesBankEmail(nonBankSender, subject, scanAllSenders = false))

        // When scanAllSenders is true, transaction keywords in subject allow it
        assertTrue(BankEmailSyncConfig.matchesBankEmail(nonBankSender, subject, scanAllSenders = true))
    }
}
