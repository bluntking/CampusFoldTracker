package com.campusfold.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.*
import com.campusfold.tracker.ui.sheets.AiCoachSheet
import com.campusfold.tracker.ui.sheets.SyncFromDateDialog
import com.campusfold.tracker.ui.sheets.TransactionDetailBottomSheet
import com.campusfold.tracker.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAddTransaction: () -> Unit,
    onManageAccounts: () -> Unit,
    onNavigateToSplits: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToRules: () -> Unit,
    initialSelectedTransaction: TransactionEntity? = null
) {
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val currentMonthTrueSpend by viewModel.currentMonthTotalTrueSpend.collectAsStateWithLifecycle()
    val monthlyBudgetLimit by viewModel.overallMonthlyBudget.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val personsWithBalances by viewModel.personsWithBalances.collectAsStateWithLifecycle()
    val allPersons by viewModel.allPersons.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val vendorMappings by viewModel.vendorMappings.collectAsStateWithLifecycle()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsStateWithLifecycle()
    val pendingReviewItems by viewModel.pendingReviewItems.collectAsStateWithLifecycle()
    val suggestedDuplicates by viewModel.suggestedDuplicates.collectAsStateWithLifecycle()
    val latestAutoMerge by viewModel.latestAutoMergeLog.collectAsStateWithLifecycle()
    val dismissedAutoMergeId by viewModel.dismissedAutoMergeLogId.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTransactionIds by viewModel.selectedTransactionIds.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isBalanceHidden by viewModel.hideBalances.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    val totalOwedToMe = remember(personsWithBalances) {
        personsWithBalances.filter { it.totalOwedToMe > 0 }.sumOf { it.totalOwedToMe }
    }
    val owedFriendsCount = remember(personsWithBalances) {
        personsWithBalances.count { it.totalOwedToMe > 0 }
    }

    val context = LocalContext.current
    var showSyncDateDialog by remember { mutableStateOf(false) }
    var selectedTransactionForSheet by remember { mutableStateOf<TransactionEntity?>(initialSelectedTransaction) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var showSetBudgetDialog by remember { mutableStateOf(false) }
    var showDiagnosticsScreen by remember { mutableStateOf(false) }
    var showAccountBalanceDialog by remember { mutableStateOf<AccountEntity?>(null) }
    var isViewAllExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Bulk action dialog states
    var showBulkRecategorizeDialog by remember { mutableStateOf(false) }
    var showBulkMoveAccountDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialSelectedTransaction) {
        if (initialSelectedTransaction != null) {
            selectedTransactionForSheet = initialSelectedTransaction
        }
    }

    // Dynamic Greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val greetingText = if (userName.isNotBlank()) "$greeting, $userName" else greeting

    // Filter transactions by search query if present
    val displayedTransactions = remember(filteredTransactions, searchQuery, isViewAllExpanded) {
        val searched = if (searchQuery.isBlank()) {
            filteredTransactions
        } else {
            val q = searchQuery.trim().lowercase()
            filteredTransactions.filter { txn ->
                txn.friendlyVendorName.lowercase().contains(q) ||
                txn.rawMerchant.lowercase().contains(q) ||
                (txn.description?.lowercase()?.contains(q) == true)
            }
        }
        if (isViewAllExpanded) searched else searched.take(8)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimensions.Padding.content),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Soft Gradient Header with Greeting ──────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CAMPUSFOLD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Manage Accounts Shortcut
                        IconButton(
                            onClick = onManageAccounts,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = "Manage Accounts",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Diagnostics / Settings Shortcut
                        IconButton(
                            onClick = { showDiagnosticsScreen = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Diagnostics & Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── 2. Missing Permissions Warning ────────────────────────────────
            if (!hasSmsPermission) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SMS PERMISSION REQUIRED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CampusFold needs SMS permissions to detect bank transaction alerts and auto-update account balances.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Grant Permissions", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            // ── 3. Reference Balance Card ──────────────────────────────────────
            item {
                BalanceCard(
                    accounts = accounts,
                    isBalanceHidden = isBalanceHidden,
                    onToggleBalanceVisibility = { viewModel.toggleHideBalances() },
                    onManageAccounts = onManageAccounts,
                    onUpdateAccountBalance = { acc -> showAccountBalanceDialog = acc }
                )
            }

            // ── 4. Horizontally Swipeable Metrics Carousel ─────────────────────
            item {
                HomeMetricsCarousel(
                    currentMonthTrueSpend = currentMonthTrueSpend,
                    monthlyBudgetLimit = monthlyBudgetLimit,
                    totalOwedToMe = totalOwedToMe,
                    owedFriendsCount = owedFriendsCount,
                    onNavigateToSplits = onNavigateToSplits,
                    onSetBudgetClick = { showSetBudgetDialog = true }
                )
            }

            // ── 5. Pending Items Cards (Review, Duplicates, Auto-Merged) ───────
            // 5a. Review Queue Count Banner
            val totalPendingReviews = pendingReviewItems.size + pendingSuggestions.size
            if (totalPendingReviews > 0) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PendingActions,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Review Queue",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "$totalPendingReviews unverified items pending",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onNavigateToRules,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Review", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            // 5b. Auto-Merged Notification Banner with Undo
            if (latestAutoMerge != null && latestAutoMerge?.undoneAt == null && latestAutoMerge?.id != dismissedAutoMergeId) {
                item {
                    AutoMergedCard(
                        mergeLog = latestAutoMerge!!,
                        accounts = accounts,
                        onUndo = { viewModel.undoMerge(it) },
                        onDismiss = { viewModel.dismissAutoMergeNotification(it) }
                    )
                }
            }

            // 5c. Possible Duplicate Accounts Suggested Cards
            items(suggestedDuplicates, key = { "${it.accountA.id}_${it.accountB.id}" }) { pair ->
                SuggestedDuplicateCard(
                    pair = pair,
                    onMerge = { source, target ->
                        viewModel.mergeAccounts(target.id, source.id)
                    },
                    onNotSame = { a, b -> viewModel.markPairNotSame(a, b) },
                    onDismiss = { viewModel.dismissSuggestedDuplicate(pair) }
                )
            }

            // ── 6. Recent Transactions Section Header & Search ────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isViewAllExpanded) "All Transactions" else "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        TextButton(onClick = { isViewAllExpanded = !isViewAllExpanded }) {
                            Text(
                                text = if (isViewAllExpanded) "Show Less" else "View All (${filteredTransactions.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Inline Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search merchant, vendor, or note...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category & Type Filter Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "ALL",
                                onClick = { viewModel.setFilter("ALL") },
                                label = { Text("All", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "EXPENSE",
                                onClick = { viewModel.setFilter("EXPENSE") },
                                label = { Text("Expenses", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "INCOME",
                                onClick = { viewModel.setFilter("INCOME") },
                                label = { Text("Income", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "CC_BILL",
                                onClick = { viewModel.setFilter("CC_BILL") },
                                label = { Text("CC Bills", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // ── 7. Transactions List ──────────────────────────────────────────
            if (displayedTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No transactions found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val categoryMap = remember(categories) { categories.associateBy { it.id } }
                val mappedKeywords = remember(vendorMappings) { vendorMappings.map { it.rawSmsKeyword.lowercase() }.toSet() }

                items(displayedTransactions, key = { it.id }) { txn ->
                    val category = categoryMap[txn.categoryId]
                    val isMapped = mappedKeywords.contains(txn.rawMerchant.lowercase())
                    val isSelected = selectedTransactionIds.contains(txn.id)

                    FoldTransactionFeedCard(
                        transaction = txn,
                        categoryName = category?.name ?: "General",
                        isUnmapped = !isMapped && txn.rawMerchant.isNotBlank(),
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onToggleSelect = { viewModel.toggleTransactionSelected(txn.id) },
                        onLongClick = { viewModel.startSelectionWith(txn.id) },
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleTransactionSelected(txn.id)
                            } else {
                                selectedTransactionForSheet = txn
                            }
                        }
                    )
                }
            }
        }

        // Selection Mode Bulk Action Bar (if active)
        if (isSelectionMode && selectedTransactionIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showBulkRecategorizeDialog = true }) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recategorize", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    TextButton(onClick = { showBulkMoveAccountDialog = true }) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Move A/c", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    TextButton(onClick = { showBulkDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }

    // ── Dialogs & Sheets ───────────────────────────────────────────────────────

    // 1. Transaction Detail Bottom Sheet
    selectedTransactionForSheet?.let { txn ->
        TransactionDetailBottomSheet(
            transaction = txn,
            categories = categories,
            accounts = accounts,
            vendorMappings = vendorMappings,
            allPersons = allPersons,
            allTransactions = allTransactions,
            onDismiss = { selectedTransactionForSheet = null },
            onSaveTransaction = { updatedTx, splits ->
                viewModel.editTransactionWithSplits(updatedTx, splits)
                selectedTransactionForSheet = null
            },
            onToggleCcBill = {
                viewModel.toggleCcBillTransfer(txn.id)
                selectedTransactionForSheet = null
            },
            onSaveVendorMapping = { raw, friendly, catId ->
                viewModel.saveVendorMappingForFuture(raw, friendly, catId)
            },
            onDelete = {
                viewModel.deleteTransaction(txn.id)
                selectedTransactionForSheet = null
            }
        )
    }

    // 2. Set Manual Account Balance Dialog
    showAccountBalanceDialog?.let { acc ->
        SetManualBalanceDialog(
            account = acc,
            onDismiss = { showAccountBalanceDialog = null },
            onSave = { amount ->
                viewModel.setAccountBalanceManually(acc.id, amount)
                showAccountBalanceDialog = null
            }
        )
    }

    // 3. Overall Monthly Budget Dialog
    if (showSetBudgetDialog) {
        var budgetInput by remember {
            mutableStateOf(if (monthlyBudgetLimit > 0) monthlyBudgetLimit.toInt().toString() else "20000")
        }
        AlertDialog(
            onDismissRequest = { showSetBudgetDialog = false },
            title = {
                Text(
                    text = "Overall Monthly Budget",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set your total monthly budget limit for the spending gauge and alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("Budget Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = budgetInput.toDoubleOrNull() ?: 0.0
                        viewModel.setOverallMonthlyBudget(limit)
                        showSetBudgetDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Bulk Recategorize Dialog
    if (showBulkRecategorizeDialog) {
        AlertDialog(
            onDismissRequest = { showBulkRecategorizeDialog = false },
            title = {
                Text(
                    text = "Recategorize ${selectedTransactionIds.size} Transactions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.bulkRecategorizeSelectedTransactions(cat.id)
                                    showBulkRecategorizeDialog = false
                                }
                        ) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkRecategorizeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Bulk Move Account Dialog
    if (showBulkMoveAccountDialog) {
        AlertDialog(
            onDismissRequest = { showBulkMoveAccountDialog = false },
            title = {
                Text(
                    text = "Move to Account (${selectedTransactionIds.size} selected)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.bulkMoveSelectedTransactionsToAccount(acc.id)
                                    showBulkMoveAccountDialog = false
                                }
                        ) {
                            Text(
                                text = "${acc.displayName.ifEmpty { acc.bankName }} (••${acc.last4})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkMoveAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Bulk Delete Confirm Dialog
    if (showBulkDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            title = {
                Text("Delete Transactions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Text("Are you sure you want to delete ${selectedTransactionIds.size} selected transactions? This action can be undone from the snackbar.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkDeleteSelectedTransactions()
                        showBulkDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 7. Diagnostics & Settings Screen
    if (showDiagnosticsScreen) {
        ModalBottomSheet(
            onDismissRequest = { showDiagnosticsScreen = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DiagnosticsScreen(
                onDismiss = { showDiagnosticsScreen = false }
            )
        }
    }
}
