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
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.screens.AccountsScreen
import com.campusfold.tracker.ui.screens.AnalyticsScreen
import com.campusfold.tracker.ui.screens.DashboardScreen
import com.campusfold.tracker.ui.screens.HomeScreen
import com.campusfold.tracker.ui.screens.SplitsScreen
import com.campusfold.tracker.ui.screens.VendorsCategoriesScreen
import com.campusfold.tracker.ui.theme.*

enum class AppNavScreen {
    DASHBOARD,
    TRANSACTIONS,
    ANALYTICS,
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
    var currentScreen by remember { mutableStateOf(AppNavScreen.DASHBOARD) }
    var highlightTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showManageAccountsSheet by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = OledBlack,
        bottomBar = {
            NavigationBar(
                containerColor = MatteCharcoal,
                modifier = Modifier
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .height(68.dp)
            ) {
                // 1. Dashboard Tab
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.DASHBOARD,
                    onClick = { currentScreen = AppNavScreen.DASHBOARD },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Dashboard",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Dashboard",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (currentScreen == AppNavScreen.DASHBOARD) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonEmerald,
                        selectedTextColor = NeonEmerald,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonEmerald.copy(alpha = 0.15f)
                    )
                )

                // 2. Transactions Tab
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.TRANSACTIONS,
                    onClick = {
                        highlightTransaction = null
                        currentScreen = AppNavScreen.TRANSACTIONS
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Transactions",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Transactions",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (currentScreen == AppNavScreen.TRANSACTIONS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonCyan.copy(alpha = 0.15f)
                    )
                )

                // 3. Analytics Tab
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.ANALYTICS,
                    onClick = { currentScreen = AppNavScreen.ANALYTICS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = "Analytics & Budgets",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Analytics",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (currentScreen == AppNavScreen.ANALYTICS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonPurple,
                        selectedTextColor = NeonPurple,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonPurple.copy(alpha = 0.15f)
                    )
                )

                // 4. Splits Tab
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.SPLITS,
                    onClick = { currentScreen = AppNavScreen.SPLITS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Splits & Friends",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Splits",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (currentScreen == AppNavScreen.SPLITS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonAmber,
                        selectedTextColor = NeonAmber,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonAmber.copy(alpha = 0.15f)
                    )
                )

                // 5. Rules Tab
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.RULES,
                    onClick = { currentScreen = AppNavScreen.RULES },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Rules & Budgets",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Rules",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (currentScreen == AppNavScreen.RULES) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF8AB4F8),
                        selectedTextColor = Color(0xFF8AB4F8),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = Color(0xFF8AB4F8).copy(alpha = 0.15f)
                    )
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    containerColor = CardElevated,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                ) {
                    Text(data.visuals.message, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
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
                    fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.98f, animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(160))
                },
                label = "ScreenSwitch"
            ) { targetScreen ->
                when (targetScreen) {
                    AppNavScreen.DASHBOARD -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToTransactions = {
                                highlightTransaction = null
                                currentScreen = AppNavScreen.TRANSACTIONS
                            },
                            onOpenTransaction = { tx ->
                                highlightTransaction = tx
                                currentScreen = AppNavScreen.TRANSACTIONS
                            },
                            onManageAccounts = { showManageAccountsSheet = true },
                            onOpenAddTransaction = { showAddDialog = true }
                        )
                    }
                    AppNavScreen.TRANSACTIONS -> {
                        HomeScreen(
                            viewModel = viewModel,
                            hasSmsPermission = hasSmsPermission,
                            onRequestPermissions = onRequestPermissions,
                            onOpenAddTransaction = { showAddDialog = true },
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
                                currentScreen = AppNavScreen.TRANSACTIONS
                            }
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
        }
    }

    // Manage Accounts Bottom Sheet (opened from Dashboard "Manage >")
    if (showManageAccountsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageAccountsSheet = false },
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
        ) {
            AccountsScreen(viewModel = viewModel)
        }
    }

    // Manual Add Transaction Dialog
    if (showAddDialog) {
        val accountsList by viewModel.accounts.collectAsState()
        AddManualTransactionDialog(
            categories = categories,
            accounts = accountsList,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, vendor, categoryId, type, paymentMode ->
                viewModel.addManualTransaction(amount, vendor, categoryId, type, paymentMode)
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
    onDismiss: () -> Unit,
    onAdd: (amount: Double, vendor: String, categoryId: Long?, type: TransactionType, paymentMode: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var vendorName by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var paymentMode by remember { mutableStateOf(accounts.firstOrNull()?.name ?: "PNB CC 8598") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "LOG TRANSACTION",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
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
                        label = { Text("Expense", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PillBgExpense,
                            selectedLabelColor = PillTextExpense
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == TransactionType.EXPENSE,
                            selectedBorderColor = PillTextExpense,
                            borderColor = BorderCharcoal
                        )
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("Income", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PillBgIncome,
                            selectedLabelColor = PillTextIncome
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == TransactionType.INCOME,
                            selectedBorderColor = PillTextIncome,
                            borderColor = BorderCharcoal
                        )
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.CC_BILL_TRANSFER,
                        onClick = { selectedType = TransactionType.CC_BILL_TRANSFER },
                        label = { Text("CC Bill", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PillBgCcBill,
                            selectedLabelColor = PillTextCcBill
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == TransactionType.CC_BILL_TRANSFER,
                            selectedBorderColor = PillTextCcBill,
                            borderColor = BorderCharcoal
                        )
                    )
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Vendor
                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Store / Merchant Name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Account selector chips
                if (accounts.isNotEmpty()) {
                    Text("Select Account:", fontSize = 11.sp, color = TextMuted)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = paymentMode == acc.name,
                                onClick = { paymentMode = acc.name },
                                label = {
                                    Text(
                                        text = acc.name,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

                // Payment Mode Override
                OutlinedTextField(
                    value = paymentMode,
                    onValueChange = { paymentMode = it },
                    label = { Text("Payment Mode", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Category chips
                if (categories.isNotEmpty()) {
                    Text("Category:", fontSize = 11.sp, color = TextMuted)
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
                        onAdd(amount, vendorName.trim(), selectedCategoryId, selectedType, paymentMode.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Log Transaction", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
