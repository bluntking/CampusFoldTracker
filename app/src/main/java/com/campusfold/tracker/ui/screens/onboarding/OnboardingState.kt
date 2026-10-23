package com.campusfold.tracker.ui.screens.onboarding

import com.campusfold.tracker.ai.AiModelCatalog
import com.campusfold.tracker.ai.AiModelInfo

enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    AI_OFFER,
    AI_TEST,
    GMAIL_SETUP,
    SCAN_MESSAGES,
    DISCOVERY_REVIEW,
    COMPLETED
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val userName: String = "",
    val smsPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val selectedAiModel: AiModelInfo = AiModelCatalog.QWEN_1_5B,
    val isAiDownloading: Boolean = false,
    val aiDownloadProgress: Float = 0f,
    val aiDownloadPaused: Boolean = false,
    val isAiDownloaded: Boolean = false,
    val aiTestSuccess: Boolean? = null,
    val aiTestMessage: String? = null,
    val gmailEmail: String = "",
    val gmailPassword: String = "",
    val isGmailConnected: Boolean = false,
    val scanMonths: Int = 3,
    val isScanning: Boolean = false,
    val scanProgressPercent: Int = 0,
    val scanStatusMessage: String = "",
    val discoveredAccountsCount: Int = 0,
    val discoveredTransactionsCount: Int = 0,
    val discoveredVendorsCount: Int = 0
)

/**
 * Pure state transition function for testability with JVM unit tests.
 */
class OnboardingStateMachine(
    private var state: OnboardingUiState = OnboardingUiState()
) {
    fun getState(): OnboardingUiState = state

    fun proceedFromWelcome(userName: String): OnboardingStep {
        state = state.copy(
            userName = userName.trim(),
            currentStep = OnboardingStep.PERMISSIONS
        )
        return state.currentStep
    }

    fun proceedFromPermissions(smsGranted: Boolean, notifGranted: Boolean): OnboardingStep {
        state = state.copy(
            smsPermissionGranted = smsGranted,
            notificationPermissionGranted = notifGranted,
            currentStep = OnboardingStep.AI_OFFER
        )
        return state.currentStep
    }

    fun skipAiOffer(): OnboardingStep {
        state = state.copy(
            isAiDownloaded = false,
            currentStep = OnboardingStep.GMAIL_SETUP
        )
        return state.currentStep
    }

    fun completeAiDownload(): OnboardingStep {
        state = state.copy(
            isAiDownloaded = true,
            isAiDownloading = false,
            currentStep = OnboardingStep.AI_TEST
        )
        return state.currentStep
    }

    fun completeAiTest(success: Boolean, message: String): OnboardingStep {
        state = state.copy(
            aiTestSuccess = success,
            aiTestMessage = message,
            currentStep = OnboardingStep.GMAIL_SETUP
        )
        return state.currentStep
    }

    fun proceedFromGmail(connected: Boolean, email: String = "", pass: String = ""): OnboardingStep {
        state = state.copy(
            isGmailConnected = connected,
            gmailEmail = email,
            gmailPassword = pass,
            currentStep = OnboardingStep.SCAN_MESSAGES
        )
        return state.currentStep
    }

    fun selectScanMonths(months: Int) {
        state = state.copy(scanMonths = months)
    }

    fun completeScan(accounts: Int, transactions: Int, vendors: Int): OnboardingStep {
        state = state.copy(
            isScanning = false,
            scanProgressPercent = 100,
            discoveredAccountsCount = accounts,
            discoveredTransactionsCount = transactions,
            discoveredVendorsCount = vendors,
            currentStep = OnboardingStep.DISCOVERY_REVIEW
        )
        return state.currentStep
    }

    fun finishOnboarding(): OnboardingStep {
        state = state.copy(currentStep = OnboardingStep.COMPLETED)
        return state.currentStep
    }
}
