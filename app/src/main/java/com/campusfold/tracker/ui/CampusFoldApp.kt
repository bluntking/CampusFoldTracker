package com.campusfold.tracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.components.FloatingPillBottomBar
import com.campusfold.tracker.ui.screens.AccountsScreen
import com.campusfold.tracker.ui.screens.AnalyticsScreen
import com.campusfold.tracker.ui.screens.HomeScreen
import com.campusfold.tracker.ui.screens.SplitsScreen
import com.campusfold.tracker.ui.screens.VendorsCategoriesScreen
import com.campusfold.tracker.ui.screens.onboarding.OnboardingScreen
import com.campusfold.tracker.ui.sheets.AiCoachSheet
import com.campusfold.tracker.ui.sheets.SyncFromDateDialog
import com.campusfold.tracker.ui.theme.*

enum class AppNavScreen {
    HOME,
    ANALYTICS,
    AI_COACH,
    SPLITS,
    RULES
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

    // ── Main App Shell ────────────────────────────────────────────────────────
    var currentScreen by remember { mutableStateOf(AppNavScreen.HOME) }
    var highlightTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showManageAccountsSheet by remember { mutableStateOf(false) }
    var showSyncFromDateDialog by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
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
                            hasSmsPermission = hasSmsPermission,
                            onRequestPermissions = onRequestPermissions,
                            onOpenAddTransaction = { showAddDialog = true },
                            onManageAccounts = { showManageAccountsSheet = true },
                            onNavigateToSplits = { currentScreen = AppNavScreen.SPLITS },
                            onNavigateToAnalytics = { currentScreen = AppNavScreen.ANALYTICS },
                            onNavigateToRules = { currentScreen = AppNavScreen.RULES },
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
                                    val catName = parts.getOrNull(1) ?: "Uncategorized"
                                    if (rawCatId.equals("uncategorized", ignoreCase = true) || rawCatId == null) {
                                        viewModel.filterByCategory(null, "Uncategorized")
                                    } else {
                                        viewModel.filterByCategory(rawCatId.toLongOrNull(), catName)
                                    }
                                } else {
                                    viewModel.filterByVendor(filterValue)
                                }
                                highlightTransaction = null
                                currentScreen = AppNavScreen.HOME
                            }
                        )
                    }

                    AppNavScreen.AI_COACH -> {
                        AiCoachSheet(
                            viewModel = viewModel,
                            onDismiss = { currentScreen = AppNavScreen.HOME }
                        )
                    }

                    AppNavScreen.SPLITS -> {
                        SplitsScreen(viewModel = viewModel)
                    }

                    AppNavScreen.RULES -> {
                        VendorsCategoriesScreen(viewModel = viewModel)
                    }
                }
            }

            // Floating Pill-Shaped Bottom Navigation Bar
            FloatingPillBottomBar(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    highlightTransaction = null
                    currentScreen = screen
                },
                onAddTransaction = { showAddDialog = true },
                onSyncNow = { showSyncFromDateDialog = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Manage Accounts Bottom Sheet
    if (showManageAccountsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageAccountsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            AccountsScreen(viewModel = viewModel)
        }
    }

    // Sync From Date Dialog
    if (showSyncFromDateDialog) {
        SyncFromDateDialog(
            isScanning = false,
            onDismiss = { showSyncFromDateDialog = false },
            onConfirmSync = { fromMillis ->
                viewModel.scanSmsFromDate(fromMillis)
                showSyncFromDateDialog = false
            }
        )
    }

    // Manual Add Transaction Dialog
    if (showAddDialog) {
        val accountsList by viewModel.accounts.collectAsStateWithLifecycle()
        val allPersonsList by viewModel.allPersons.collectAsStateWithLifecycle()
        AddManualTransactionDialog(
            categories = categories,
            accounts = accountsList,
            allPersons = allPersonsList,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, vendor, categoryId, type, paymentMode, desc ->
                viewModel.addManualTransaction(amount, vendor, categoryId, type, paymentMode, emptyList(), desc)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddManualTransactionDialog(
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity> = emptyList(),
    allPersons: List<PersonEntity> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (amount: Double, vendor: String, categoryId: Long?, type: TransactionType, paymentMode: String, description: String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var vendorName by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var paymentMode by remember { mutableStateOf(accounts.firstOrNull()?.name ?: "Cash / Bank") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Transaction",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Type Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("Expense", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("Income", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.CC_BILL_TRANSFER,
                        onClick = { selectedType = TransactionType.CC_BILL_TRANSFER },
                        label = { Text("CC Bill", fontSize = 11.sp, maxLines = 1) }
                    )
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Vendor
                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Store / Merchant Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Paid Friend for My Share Quick Helper
                if (selectedType == TransactionType.EXPENSE && allPersons.isNotEmpty()) {
                    Text(
                        text = "Friend paid for you? Tap friend to set share:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allPersons.forEach { friend ->
                            val isSelected = vendorName.contains(friend.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    vendorName = "Paid to ${friend.name} (My Share)"
                                    if (descriptionText.isBlank()) {
                                        descriptionText = "My share paid to ${friend.name}"
                                    }
                                },
                                label = { Text(friend.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Note / Description
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Note / Description (Optional)") },
                    placeholder = { Text("e.g. Dinner with Rahul, groceries", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Account selector chips
                if (accounts.isNotEmpty()) {
                    Text("Select Account:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = paymentMode == acc.displayName.ifEmpty { acc.bankName },
                                onClick = { paymentMode = acc.displayName.ifEmpty { acc.bankName } },
                                label = {
                                    Text(
                                        text = acc.displayName.ifEmpty { acc.bankName },
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

                // Category chips
                if (categories.isNotEmpty()) {
                    Text("Category:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = {
                                    Text(
                                        text = cat.name,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0 && vendorName.isNotBlank()) {
                        onAdd(
                            amount,
                            vendorName.trim(),
                            selectedCategoryId,
                            selectedType,
                            paymentMode.trim(),
                            descriptionText.trim().ifBlank { null }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Log Transaction", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
