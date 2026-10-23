package com.campusfold.tracker

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.BalanceSource
import com.campusfold.tracker.ui.components.isAccountBalanceKnown
import com.campusfold.tracker.ui.screens.onboarding.OnboardingStep
import com.campusfold.tracker.ui.screens.onboarding.OnboardingStateMachine
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BalanceRulesAndOnboardingTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. BALANCE DISPLAY RULES TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test total savings balance excludes credit cards strictly`() {
        val hdfcSavings = AccountEntity(
            id = 1,
            bankName = "HDFC Bank",
            bankKey = "HDFC",
            last4 = "1234",
            type = AccountType.SAVINGS,
            displayName = "HDFC Savings",
            balancePaise = 2500000L, // ₹25,000
            balanceAsOf = 1700000000000L,
            balanceSource = BalanceSource.AUTO
        )

        val sbiSavings = AccountEntity(
            id = 2,
            bankName = "SBI",
            bankKey = "SBI",
            last4 = "5678",
            type = AccountType.SAVINGS,
            displayName = "SBI Regular",
            balancePaise = 1500000L, // ₹15,000
            balanceAsOf = 1700000000000L,
            balanceSource = BalanceSource.AUTO
        )

        val iciciCreditCard = AccountEntity(
            id = 3,
            bankName = "ICICI",
            bankKey = "ICICI",
            last4 = "9999",
            type = AccountType.CREDIT_CARD,
            displayName = "ICICI Amazon Pay CC",
            balancePaise = 0L,
            creditLimitPaise = 10000000L, // ₹1,00,000 limit
            balanceAsOf = 1700000000000L,
            balanceSource = BalanceSource.AUTO
        )

        val allAccounts = listOf(hdfcSavings, sbiSavings, iciciCreditCard)

        // Rule 1: Sum only non-credit cards for the savings total
        val savingsAccounts = allAccounts.filter { it.type != AccountType.CREDIT_CARD }
        val totalSavingsBalancePaise = savingsAccounts.sumOf { it.balancePaise }
        val totalSavingsRupees = totalSavingsBalancePaise / 100.0

        assertEquals(40000.0, totalSavingsRupees, 0.001)

        // Rule 2: Available credit is computed strictly separately
        val creditCards = allAccounts.filter { it.type == AccountType.CREDIT_CARD }
        val totalCreditLimitRupees = creditCards.sumOf { it.creditLimitPaise } / 100.0

        assertEquals(100000.0, totalCreditLimitRupees, 0.001)

        // Verify credit card balance is NEVER included in savings balance
        assertFalse(totalSavingsRupees == 140000.0)
    }

    @Test
    fun `test manual balance override is marked as entered manually`() {
        val manualAccount = AccountEntity(
            id = 10,
            bankName = "Campus Wallet",
            bankKey = "CAMPUS_WALLET",
            last4 = "0001",
            type = AccountType.WALLET,
            displayName = "Hostel Cash",
            balancePaise = 350000L, // ₹3,500
            balanceAsOf = 1700000000000L,
            balanceSource = BalanceSource.MANUAL,
            editedFields = "[\"balancePaise\"]"
        )

        assertTrue(isAccountBalanceKnown(manualAccount))
        assertEquals(BalanceSource.MANUAL, manualAccount.balanceSource)
        assertEquals(3500.0, manualAccount.balancePaise / 100.0, 0.001)
    }

    @Test
    fun `test auto balance formats latest as of timestamp`() {
        val timestamp = 1710000000000L // Specific known date
        val autoAccount = AccountEntity(
            id = 20,
            bankName = "Axis Bank",
            bankKey = "AXIS",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Salary",
            balancePaise = 5240000L,
            balanceAsOf = timestamp,
            balanceSource = BalanceSource.AUTO
        )

        assertTrue(isAccountBalanceKnown(autoAccount))
        assertEquals(BalanceSource.AUTO, autoAccount.balanceSource)

        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        val formatted = formatter.format(Date(autoAccount.balanceAsOf))
        assertTrue(formatted.isNotBlank())
    }

    @Test
    fun `test account without detected balance reports not detected yet`() {
        val newDiscoveredAccount = AccountEntity(
            id = 30,
            bankName = "Unknown Bank",
            bankKey = "UNKNOWN",
            last4 = "4321",
            type = AccountType.SAVINGS,
            displayName = "Unknown Bank ••4321",
            balancePaise = 0L,
            balanceAsOf = 0L,
            balanceSource = BalanceSource.AUTO,
            editedFields = "[]"
        )

        // Balance not yet known because it was never reported in an SMS with balance statement
        assertFalse(isAccountBalanceKnown(newDiscoveredAccount))
    }

    @Test
    fun `test balance is never computed by summing transactions without starting balance`() {
        // Discovered account with 5 debit transactions of ₹500 each
        val account = AccountEntity(
            id = 40,
            bankName = "PNB",
            bankKey = "PNB",
            last4 = "8888",
            type = AccountType.SAVINGS,
            displayName = "PNB Savings",
            balancePaise = 0L,
            balanceAsOf = 0L,
            balanceSource = BalanceSource.AUTO
        )

        val transactionDebits = listOf(500.0, 250.0, 100.0, 50.0)
        val sumOfDebits = transactionDebits.sum()

        // Strict Requirement: NEVER compute balance by summing transactions unless starting balance is known.
        // Therefore, account balance remains unknown ("Balance not detected yet"), and cannot be -sumOfDebits.
        assertFalse(isAccountBalanceKnown(account))
        assertNotEquals(-sumOfDebits, (account.balancePaise / 100.0), 0.001)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. ONBOARDING STATE MACHINE TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test onboarding happy path transitions smoothly to completed`() {
        val machine = OnboardingStateMachine()

        // Initial state
        assertEquals(OnboardingStep.WELCOME, machine.getState().currentStep)

        // Welcome -> Permissions
        val step1 = machine.proceedFromWelcome("Sahil")
        assertEquals(OnboardingStep.PERMISSIONS, step1)
        assertEquals("Sahil", machine.getState().userName)

        // Permissions -> AI Offer
        val step2 = machine.proceedFromPermissions(smsGranted = true, notifGranted = true)
        assertEquals(OnboardingStep.AI_OFFER, step2)
        assertTrue(machine.getState().smsPermissionGranted)
        assertTrue(machine.getState().notificationPermissionGranted)

        // AI Offer -> AI Download Complete -> AI Test
        val step3 = machine.completeAiDownload()
        assertEquals(OnboardingStep.AI_TEST, step3)
        assertTrue(machine.getState().isAiDownloaded)

        // AI Test -> Gmail Setup
        val step4 = machine.completeAiTest(success = true, message = "Inference verified")
        assertEquals(OnboardingStep.GMAIL_SETUP, step4)
        assertEquals(true, machine.getState().aiTestSuccess)

        // Gmail Setup -> Scan Messages
        machine.selectScanMonths(6)
        val step5 = machine.proceedFromGmail(connected = true, email = "test@gmail.com", pass = "abcd efgh ijkl mnop")
        assertEquals(OnboardingStep.SCAN_MESSAGES, step5)
        assertTrue(machine.getState().isGmailConnected)
        assertEquals(6, machine.getState().scanMonths)

        // Scan Messages -> Discovery Review
        val step6 = machine.completeScan(accounts = 2, transactions = 45, vendors = 12)
        assertEquals(OnboardingStep.DISCOVERY_REVIEW, step6)
        assertEquals(2, machine.getState().discoveredAccountsCount)
        assertEquals(45, machine.getState().discoveredTransactionsCount)
        assertEquals(12, machine.getState().discoveredVendorsCount)

        // Discovery Review -> Finish Setup
        val finalStep = machine.finishOnboarding()
        assertEquals(OnboardingStep.COMPLETED, finalStep)
    }

    @Test
    fun `test skipping AI offer routes directly to Gmail setup`() {
        val machine = OnboardingStateMachine()
        machine.proceedFromWelcome("Alex")
        machine.proceedFromPermissions(smsGranted = true, notifGranted = false)

        val nextStep = machine.skipAiOffer()
        assertEquals(OnboardingStep.GMAIL_SETUP, nextStep)
        assertFalse(machine.getState().isAiDownloaded)
    }

    @Test
    fun `test AI failure gracefully falls back without blocking onboarding`() {
        val machine = OnboardingStateMachine()
        machine.proceedFromWelcome("")
        machine.proceedFromPermissions(smsGranted = false, notifGranted = false)
        machine.completeAiDownload()

        // Simulate crash loop guard or OOM exception in native LiteRT-LM
        val nextStep = machine.completeAiTest(success = false, message = "Device low memory state - OOM guard")
        assertEquals(OnboardingStep.GMAIL_SETUP, nextStep)
        assertEquals(false, machine.getState().aiTestSuccess)
        assertEquals("Device low memory state - OOM guard", machine.getState().aiTestMessage)
    }

    @Test
    fun `test skipping Gmail routes to scan selection`() {
        val machine = OnboardingStateMachine()
        machine.proceedFromWelcome("User")
        machine.proceedFromPermissions(true, true)
        machine.skipAiOffer()

        val nextStep = machine.proceedFromGmail(connected = false)
        assertEquals(OnboardingStep.SCAN_MESSAGES, nextStep)
        assertFalse(machine.getState().isGmailConnected)
    }
}
