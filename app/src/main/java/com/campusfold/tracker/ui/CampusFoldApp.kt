package com.campusfold.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.theme.CreditGreen
import com.campusfold.tracker.ui.theme.DebitRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusFoldApp(
    viewModel: TransactionViewModel,
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val totalMyExpense by viewModel.totalMyExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val personsWithBalances by viewModel.personsWithBalances.collectAsState()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsState()
    val pnbCreditLimit by viewModel.pnbCreditLimit.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val totalOwedToMe = personsWithBalances.sumOf { it.totalOwedToMe }

    val snackbarHostState = remember { SnackbarHostState() }

    // Dropdown menu state for SMS sync window
    var showSyncMenu by remember { mutableStateOf(false) }

    // Dialog state for Add / Edit Transaction
    var showAddEditDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CampusFold Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "PNB 8598 • Axis XX2003 • Splits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = {
                                if (hasSmsPermission) {
                                    showSyncMenu = true
                                } else {
                                    onRequestPermissions()
                                }
                            },
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync SMS Options"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSyncMenu,
                            onDismissRequest = { showSyncMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sync Last 30 Days") },
                                onClick = {
                                    showSyncMenu = false
                                    viewModel.scanInbox(daysBack = 30)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sync Last 90 Days") },
                                onClick = {
                                    showSyncMenu = false
                                    viewModel.scanInbox(daysBack = 90)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    transactionToEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Permission Requester Banner
            if (!hasSmsPermission) {
                PermissionRequesterCard(onRequestPermissions = onRequestPermissions)
            }

            // PNB Credit Limit Pill Banner
            if (pnbCreditLimit > 0) {
                val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PNB CC 8598 Avl Limit:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatter.format(pnbCreditLimit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 1-Tap Pending Settlement Suggestions Banner
            if (pendingSuggestions.isNotEmpty()) {
                val suggestion = pendingSuggestions.first()
                val friendName = personsWithBalances.find { it.id == suggestion.suggestedPersonId }?.name ?: "Friend"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Friend Settlement Detected",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Did $friendName settle ₹${suggestion.amount} via '${suggestion.detectedSenderName}'?",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.confirmSettlementSuggestion(suggestion.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = CreditGreen)
                            ) {
                                Text("1-Tap Confirm")
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissSettlementSuggestion(suggestion.id) }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            // Dynamic Balance Header (Strict Normalization: Excludes CC_BILL_TRANSFER)
            ThreeWaySummaryCard(
                mySpent = totalMyExpense,
                owedToMe = totalOwedToMe,
                income = totalIncome
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { viewModel.setFilter("ALL") },
                    label = { Text("All", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "EXPENSE",
                    onClick = { viewModel.setFilter("EXPENSE") },
                    label = { Text("Expenses", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "INCOME",
                    onClick = { viewModel.setFilter("INCOME") },
                    label = { Text("Income", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "CC_BILL",
                    onClick = { viewModel.setFilter("CC_BILL") },
                    label = { Text("CC Bills", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Transaction List
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Transactions Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the sync icon above to scan last 30/90 days of SMS.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
                ) {
                    items(transactions, key = { it.id }) { txn ->
                        val categoryName = categories.find { it.id == txn.categoryId }?.name ?: "General"
                        TransactionCard(
                            transaction = txn,
                            categoryName = categoryName,
                            onToggleCcBill = { viewModel.toggleCcBillTransfer(txn.id) },
                            onEdit = {
                                transactionToEdit = txn
                                showAddEditDialog = true
                            },
                            onDelete = { viewModel.deleteTransaction(txn) }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Transaction Dialog
    if (showAddEditDialog) {
        AddEditTransactionDialog(
            initialTx = transactionToEdit,
            categories = categories,
            onDismiss = { showAddEditDialog = false },
            onSave = { amount, vendor, categoryId, type, paymentMode ->
                if (transactionToEdit == null) {
                    viewModel.addManualTransaction(amount, vendor, categoryId, type, paymentMode)
                } else {
                    viewModel.editTransaction(
                        transactionToEdit!!.id,
                        amount,
                        vendor,
                        categoryId,
                        type,
                        paymentMode
                    )
                }
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun PermissionRequesterCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SMS & Notification Access Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CampusFold runs 100% offline on your device to parse PNB CC 8598 and Axis Bank XX2003 debit alerts. It requires:\n• READ_SMS (historical 30/90 days inbox scan)\n• RECEIVE_SMS (real-time transaction capture)\n• POST_NOTIFICATIONS (transaction confirmation alerts)",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Grant Permissions", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ThreeWaySummaryCard(mySpent: Double, owedToMe: Double, income: Double) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // My Net Spent (CC_BILL_TRANSFER is strictly excluded)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Share",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatter.format(mySpent),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DebitRed
                )
            }

            // Owed to Me (Strict dynamic balance)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Owed to Me",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatter.format(owedToMe),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFF59E0B)
                )
            }

            // Income / Received
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Received",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatter.format(income),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CreditGreen
                )
            }
        }
    }
}

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    categoryName: String,
    onToggleCcBill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.timestamp))
    val isCredit = transaction.type == TransactionType.INCOME
    val isCcBill = transaction.type == TransactionType.CC_BILL_TRANSFER

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon indicator
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCcBill -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                isCredit -> CreditGreen.copy(alpha = 0.15f)
                                else -> DebitRed.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isCcBill -> Icons.Default.SwapHoriz
                            isCredit -> Icons.Default.ArrowUpward
                            else -> Icons.Default.ArrowDownward
                        },
                        contentDescription = null,
                        tint = when {
                            isCcBill -> Color(0xFF3B82F6)
                            isCredit -> CreditGreen
                            else -> DebitRed
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.friendlyVendorName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(categoryName, fontSize = 9.sp) },
                            modifier = Modifier.height(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.paymentMode,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Amount
                Column(horizontalAlignment = Alignment.End) {
                    val sign = when {
                        isCcBill -> "⇌ "
                        isCredit -> "+"
                        else -> "-"
                    }
                    Text(
                        text = sign + formatter.format(transaction.amount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            isCcBill -> Color(0xFF3B82F6)
                            isCredit -> CreditGreen
                            else -> DebitRed
                        }
                    )
                    if (transaction.myActualShare != transaction.amount && !isCcBill) {
                        Text(
                            text = "My share: " + formatter.format(transaction.myActualShare),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom action row: 1-Tap CC Bill Toggle & Edit/Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1-Tap CC Bill Toggle Chip
                AssistChip(
                    onClick = onToggleCcBill,
                    label = {
                        Text(
                            text = if (isCcBill) "CC Bill (Excluded)" else "Mark as CC Bill",
                            fontSize = 10.sp,
                            color = if (isCcBill) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isCcBill) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionDialog(
    initialTx: TransactionEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (amount: Double, vendor: String, categoryId: Long?, type: TransactionType, paymentMode: String) -> Unit
) {
    var amountText by remember { mutableStateOf(initialTx?.amount?.toString() ?: "") }
    var vendorName by remember { mutableStateOf(initialTx?.friendlyVendorName ?: "") }
    var selectedCategoryId by remember { mutableStateOf(initialTx?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedType by remember { mutableStateOf(initialTx?.type ?: TransactionType.EXPENSE) }
    var paymentMode by remember { mutableStateOf(initialTx?.paymentMode ?: "PNB CC 8598") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTx == null) "Add Transaction" else "Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Vendor / Store Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = paymentMode,
                    onValueChange = { paymentMode = it },
                    label = { Text("Payment Mode ('PNB CC 8598', 'Axis Bank XX2003', etc.)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, fontSize = 10.sp) }
                        )
                    }
                }

                Text("Type:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("Expense", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("Income", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.CC_BILL_TRANSFER,
                        onClick = { selectedType = TransactionType.CC_BILL_TRANSFER },
                        label = { Text("CC Bill", fontSize = 10.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0 && vendorName.isNotBlank()) {
                        onSave(amount, vendorName, selectedCategoryId, selectedType, paymentMode)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
