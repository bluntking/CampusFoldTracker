package com.campusfold.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.components.FloatingPillBottomBar
import com.campusfold.tracker.ui.screens.AccountsScreen
import com.campusfold.tracker.ui.screens.AnalyticsScreen
import com.campusfold.tracker.ui.screens.HomeScreen
import com.campusfold.tracker.ui.screens.SettingsScreen
import com.campusfold.tracker.ui.screens.SplitsScreen
import com.campusfold.tracker.ui.screens.TransactionsScreen
import com.campusfold.tracker.ui.screens.onboarding.OnboardingScreen
import com.campusfold.tracker.ui.sheets.AddTransactionSheet
import com.campusfold.tracker.ui.sheets.AiCoachSheet
import com.campusfold.tracker.ui.theme.*

enum class AppNavScreen {
    HOME,
    ANALYTICS,
    SPLITS,
    SETTINGS
}

sealed class PushedScreen {
    data class Transactions(val categoryId: Long? = null, val vendorName: String? = null, val accountId: Long? = null) : PushedScreen()
    object Accounts : PushedScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusFoldApp(
    viewModel: TransactionViewModel,
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()

    // ── Onboarding Gate ────────────────────────────────────────────────────────
    if (!hasCompletedOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onOnboardingComplete = {
                // Setting completed state enters main app
            }
        )
        return
    }

    // ── Main App Shell Navigation ─────────────────────────────────────────────
    var currentScreen by remember { mutableStateOf(AppNavScreen.HOME) }
    var pushedScreen by remember { mutableStateOf<PushedScreen?>(null) }
    var highlightTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var preselectFriendPaid by remember { mutableStateOf(false) }
    var showAiCoachSheet by remember { mutableStateOf(false) }

    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            if (msg.contains("Deleted", ignoreCase = true)) {
                val res = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastDelete()
                }
            } else {
                snackbarHostState.showSnackbar(msg)
            }
            viewModel.clearStatusMessage()
        }
    }

    // Handle system back on pushed screens
    BackHandler(enabled = pushedScreen != null) {
        pushedScreen = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content area
            val activePushed = pushedScreen
            if (activePushed != null) {
                when (activePushed) {
                    is PushedScreen.Transactions -> {
                        TransactionsScreen(
                            viewModel = viewModel,
                            onBack = { pushedScreen = null },
                            initialCategoryFilter = activePushed.categoryId,
                            initialVendorFilter = activePushed.vendorName,
                            initialAccountFilter = activePushed.accountId
                        )
                    }
                    is PushedScreen.Accounts -> {
                        AccountsScreen(
                            viewModel = viewModel,
                            onBack = { pushedScreen = null },
                            onAccountClick = { accId ->
                                pushedScreen = PushedScreen.Transactions(accountId = accId)
                            }
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(140)) togetherWith
                                fadeOut(animationSpec = tween(100))
                    },
                    label = "ScreenSwitch"
                ) { targetScreen ->
                    when (targetScreen) {
                        AppNavScreen.HOME -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenAiCoach = { showAiCoachSheet = true },
                                onNavigateToTransactions = { pushedScreen = PushedScreen.Transactions() },
                                onNavigateToAccounts = { pushedScreen = PushedScreen.Accounts },
                                onNavigateToAccountTransactions = { accId ->
                                    pushedScreen = PushedScreen.Transactions(accountId = accId)
                                },
                                onNavigateToSplits = { currentScreen = AppNavScreen.SPLITS },
                                onNavigateToSettingsReview = { currentScreen = AppNavScreen.SETTINGS },
                                initialSelectedTransaction = highlightTransaction
                            )
                        }

                        AppNavScreen.ANALYTICS -> {
                            AnalyticsScreen(
                                viewModel = viewModel,
                                onNavigateToTransactions = { filterType, filterValue ->
                                    if (filterType == "CATEGORY") {
                                        val parts = filterValue.split(":")
                                        val rawCatId = parts.getOrNull(0)
                                        val catId = if (rawCatId.equals("uncategorized", ignoreCase = true)) null else rawCatId?.toLongOrNull()
                                        pushedScreen = PushedScreen.Transactions(categoryId = catId, vendorName = null)
                                    } else {
                                        pushedScreen = PushedScreen.Transactions(categoryId = null, vendorName = filterValue)
                                    }
                                }
                            )
                        }

                        AppNavScreen.SPLITS -> {
                            SplitsScreen(
                                viewModel = viewModel,
                                onAddFriendPaidExpense = {
                                    preselectFriendPaid = true
                                    showAddTransactionSheet = true
                                }
                            )
                        }

                        AppNavScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToAccounts = { pushedScreen = PushedScreen.Accounts },
                                onOpenAiCoach = { showAiCoachSheet = true }
                            )
                        }
                    }
                }
            }

            // Floating Pill-Shaped Bottom Navigation Bar (visible across all screens, pushed screens keep bar visible with Home highlighted)
            FloatingPillBottomBar(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    pushedScreen = null
                    highlightTransaction = null
                    currentScreen = screen
                },
                onAddTransaction = {
                    preselectFriendPaid = false
                    showAddTransactionSheet = true
                },
                isPushedScreenActive = pushedScreen != null,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // AI Coach Bottom Sheet (opened from Home header or Settings)
    if (showAiCoachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiCoachSheet = false },
            containerColor = OledBlack,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
        ) {
            AiCoachSheet(
                viewModel = viewModel,
                onDismiss = { showAiCoachSheet = false }
            )
        }
    }

    // Full-height Add Transaction Sheet (Expense, Income, Card Bill, Transfer, Friend Paid)
    if (showAddTransactionSheet) {
        AddTransactionSheet(
            viewModel = viewModel,
            preselectFriendPaid = preselectFriendPaid,
            onDismiss = {
                showAddTransactionSheet = false
                preselectFriendPaid = false
            }
        )
    }
}
