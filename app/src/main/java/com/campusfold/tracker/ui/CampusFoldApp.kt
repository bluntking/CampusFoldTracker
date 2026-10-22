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
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val totalOwedToMe = personsWithBalances.sumOf { it.totalOwedToMe }

    val snackbarHostState = remember { SnackbarHostState() }

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
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Strict Normalized Split & Vendor Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (hasSmsPermission) {
                                viewModel.scanInbox()
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
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan SMS Inbox"
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
            // SMS Permission Banner
            if (!hasSmsPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "SMS Permissions Needed",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant READ_SMS & RECEIVE_SMS to auto-capture UPI payments offline.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Permission", fontSize = 12.sp)
                        }
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
                        .padding(vertical = 6.dp),
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
                                text = "Settlement Suggestion",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "Did $friendName settle ₹${suggestion.amount} via '${suggestion.detectedSenderName}'?",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

            // Dynamic Balance Header (Strict Normalization)
            ThreeWaySummaryCard(
                mySpent = totalMyExpense,
                owedToMe = totalOwedToMe,
                income = totalIncome
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { viewModel.setFilter("ALL") },
                    label = { Text("All", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "EXPENSE",
                    onClick = { viewModel.setFilter("EXPENSE") },
                    label = { Text("Expenses", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "INCOME",
                    onClick = { viewModel.setFilter("INCOME") },
                    label = { Text("Income", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "CC_BILL",
                    onClick = { viewModel.setFilter("CC_BILL") },
                    label = { Text("CC Bills", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                            text = "No Transactions Recorded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use '+' to add manual transactions or tap refresh to scan SMS.",
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
            // My Net Spent
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.timestamp))
    val isCredit = transaction.type == TransactionType.INCOME

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCredit) CreditGreen.copy(alpha = 0.12f)
                        else DebitRed.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (isCredit) CreditGreen else DebitRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

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

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isCredit) "+" else "-") + formatter.format(transaction.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isCredit) CreditGreen else DebitRed
                )
                if (transaction.myActualShare != transaction.amount) {
                    Text(
                        text = "My share: " + formatter.format(transaction.myActualShare),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    label = { Text("Payment Mode ('PNB CC 8598', etc.)") },
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
