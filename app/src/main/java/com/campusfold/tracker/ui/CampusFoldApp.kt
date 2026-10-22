package com.campusfold.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.screens.AnalyticsScreen
import com.campusfold.tracker.ui.screens.HomeScreen
import com.campusfold.tracker.ui.screens.SplitsScreen
import com.campusfold.tracker.ui.screens.VendorsCategoriesScreen
import com.campusfold.tracker.ui.theme.*

enum class AppNavScreen {
    HOME,
    ANALYTICS,
    SPLITS,
    VENDORS_AND_CATEGORIES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusFoldApp(
    viewModel: TransactionViewModel,
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppNavScreen.HOME) }
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
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.HOME,
                    onClick = { currentScreen = AppNavScreen.HOME },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Home Dashboard",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Dashboard",
                            fontSize = 11.sp,
                            fontWeight = if (currentScreen == AppNavScreen.HOME) FontWeight.Bold else FontWeight.Normal
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

                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.VENDORS_AND_CATEGORIES,
                    onClick = { currentScreen = AppNavScreen.VENDORS_AND_CATEGORIES },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Vendors & Categories",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Rules & Budgets",
                            fontSize = 11.sp,
                            fontWeight = if (currentScreen == AppNavScreen.VENDORS_AND_CATEGORIES) FontWeight.Bold else FontWeight.Normal
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
            when (currentScreen) {
                AppNavScreen.HOME -> {
                    HomeScreen(
                        viewModel = viewModel,
                        hasSmsPermission = hasSmsPermission,
                        onRequestPermissions = onRequestPermissions,
                        onOpenAddTransaction = { showAddDialog = true }
                    )
                }
                AppNavScreen.ANALYTICS -> {
                    AnalyticsScreen(viewModel = viewModel)
                }
                AppNavScreen.SPLITS -> {
                    SplitsScreen(viewModel = viewModel)
                }
                AppNavScreen.VENDORS_AND_CATEGORIES -> {
                    VendorsCategoriesScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Manual Add Transaction Dialog
    if (showAddDialog) {
        AddManualTransactionDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, vendor, categoryId, type, paymentMode ->
                viewModel.addManualTransaction(amount, vendor, categoryId, type, paymentMode)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddManualTransactionDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (amount: Double, vendor: String, categoryId: Long?, type: TransactionType, paymentMode: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var vendorName by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var paymentMode by remember { mutableStateOf("PNB CC 8598") }

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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("Expense", fontSize = 10.sp) },
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
                        label = { Text("Income", fontSize = 10.sp) },
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
                        label = { Text("CC Bill", fontSize = 10.sp) },
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

                // Payment Mode
                OutlinedTextField(
                    value = paymentMode,
                    onValueChange = { paymentMode = it },
                    label = { Text("Payment Mode ('PNB CC 8598', 'Axis Bank XX2003')", color = TextSecondary) },
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
                Text("Category:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, fontSize = 10.sp) }
                        )
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
