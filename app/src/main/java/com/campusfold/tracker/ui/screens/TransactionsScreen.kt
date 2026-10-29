package com.campusfold.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldTransactionFeedCard
import com.campusfold.tracker.ui.sheets.TransactionDetailBottomSheet
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class TxTypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Expense"),
    INCOME("Income"),
    INVESTMENT("Investment"),
    TRANSFERS("Transfers"),
    CARD_BILLS("Card Bills")
}

/**
 * Pushed TransactionsScreen (Part 4):
 * - Back arrow & system-back handling.
 * - Search bar.
 * - Filter chips: All, Expense, Income, Transfers, Card bills.
 * - Category and Vendor filters (supports pre-selected filter from Analytics drill-down).
 * - Grouped by date (Today, Yesterday, dd MMM yyyy).
 * - Multi-select bulk actions (recategorize, move account, delete).
 * - Tapping a row opens existing TransactionDetailBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionViewModel,
    onBack: () -> Unit,
    initialCategoryFilter: Long? = null,
    initialVendorFilter: String? = null,
    initialAccountFilter: Long? = null,
    onScrollVisibilityChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf(TxTypeFilter.ALL) }
    var selectedCategoryFilter by remember(initialCategoryFilter) { mutableStateOf(initialCategoryFilter) }
    var selectedVendorFilter by remember(initialVendorFilter) { mutableStateOf(initialVendorFilter) }
    var selectedAccountId by remember(initialAccountFilter) { mutableStateOf(initialAccountFilter) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // Responsive scroll & visibility state
    val listState = rememberLazyListState()
    var isBarsVisible by remember { mutableStateOf(true) }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -12f) { // User scrolling down -> hide bars
                    if (isBarsVisible && !isSelectionMode) {
                        isBarsVisible = false
                    }
                } else if (delta > 12f) { // User scrolling up -> show bars
                    if (!isBarsVisible) {
                        isBarsVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isAtTop, isSelectionMode) {
        if (isAtTop || isSelectionMode) {
            isBarsVisible = true
        }
    }

    LaunchedEffect(isBarsVisible) {
        onScrollVisibilityChange?.invoke(isBarsVisible)
    }

    DisposableEffect(Unit) {
        onDispose {
            onScrollVisibilityChange?.invoke(true)
        }
    }

    // Dialogs for bulk actions
    var showRecategorizeDialog by remember { mutableStateOf(false) }
    var showMoveAccountDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var selectedTransactionForSheet by remember { mutableStateOf<TransactionEntity?>(null) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val selectedAccObj = remember(accounts, selectedAccountId) {
        accounts.find { it.id == selectedAccountId }
    }

    // Filter transactions
    val filteredList = remember(
        allTransactions,
        searchQuery,
        selectedTypeFilter,
        selectedCategoryFilter,
        selectedVendorFilter,
        selectedAccountId,
        selectedAccObj
    ) {
        allTransactions.filter { tx ->
            // Search query filter
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            val matchesQuery = query.isEmpty() ||
                    tx.vendor.lowercase(Locale.getDefault()).contains(query) ||
                    (tx.description?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                    tx.paymentMode.lowercase(Locale.getDefault()).contains(query) ||
                    (tx.amountPaise.toString().contains(query))

            // Type filter
            val matchesType = when (selectedTypeFilter) {
                TxTypeFilter.ALL -> true
                TxTypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                TxTypeFilter.INCOME -> tx.type == TransactionType.INCOME
                TxTypeFilter.INVESTMENT -> tx.type == TransactionType.INVESTMENT
                TxTypeFilter.TRANSFERS -> tx.type == TransactionType.OWN_TRANSFER
                TxTypeFilter.CARD_BILLS -> tx.type == TransactionType.CC_BILL_TRANSFER
            }

            // Category filter
            val matchesCategory = selectedCategoryFilter == null || tx.categoryId == selectedCategoryFilter

            // Vendor filter
            val matchesVendor = selectedVendorFilter == null ||
                    tx.vendor.equals(selectedVendorFilter, ignoreCase = true) ||
                    tx.rawMerchant.equals(selectedVendorFilter, ignoreCase = true)

            // Account filter
            val matchesAccount = selectedAccountId == null ||
                    tx.accountId == selectedAccountId ||
                    (tx.accountId == null && selectedAccObj != null && (
                        tx.paymentMode.equals(selectedAccObj.displayName, ignoreCase = true) ||
                        tx.paymentMode.equals(selectedAccObj.bankName, ignoreCase = true) ||
                        (selectedAccObj.last4.isNotBlank() && tx.paymentMode.contains(selectedAccObj.last4))
                    ))

            matchesQuery && matchesType && matchesCategory && matchesVendor && matchesAccount
        }
    }

    // Group filtered transactions by date
    val groupedByDate = remember(filteredList) {
        val nowCal = Calendar.getInstance()
        val todayYear = nowCal.get(Calendar.YEAR)
        val todayDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

        val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yestYear = yestCal.get(Calendar.YEAR)
        val yestDayOfYear = yestCal.get(Calendar.DAY_OF_YEAR)

        val dateFmt = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()

        filteredList.groupBy { tx ->
            cal.timeInMillis = tx.timestamp
            val txYear = cal.get(Calendar.YEAR)
            val txDay = cal.get(Calendar.DAY_OF_YEAR)
            when {
                txYear == todayYear && txDay == todayDayOfYear -> "Today"
                txYear == yestYear && txDay == yestDayOfYear -> "Yesterday"
                else -> dateFmt.format(Date(tx.timestamp))
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isBarsVisible || isSelectionMode || isAtTop,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OledBlack)
                        .statusBarsPadding()
                ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }

                    if (isSelectionMode) {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        // Bulk Actions Icons
                        IconButton(onClick = { showRecategorizeDialog = true }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Label, contentDescription = "Recategorize", tint = NeonEmerald)
                        }
                        IconButton(onClick = { showMoveAccountDialog = true }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.AccountBalance, contentDescription = "Move Account", tint = NeonCyan)
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonCrimson)
                        }
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection", tint = TextSecondary)
                        }
                    } else {
                        Text(
                            text = "Transactions",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select multiple", tint = TextSecondary)
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search merchant, note, amount...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Type Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TxTypeFilter.values().forEach { filter ->
                        val isSelected = selectedTypeFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTypeFilter = filter },
                            label = { Text(filter.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald,
                                selectedLabelColor = OledBlack
                            )
                        )
                    }
                }

                // Active Category, Vendor or Account Filter Banner (from drill-down)
                if (selectedCategoryFilter != null || selectedVendorFilter != null || selectedAccountId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedAccountId?.let { accId ->
                            val accName = selectedAccObj?.displayName ?: "Account"
                            AssistChip(
                                onClick = { selectedAccountId = null },
                                label = { Text("Account: $accName ✕", fontSize = 11.sp, color = NeonPurple) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple)
                            )
                        }
                        selectedCategoryFilter?.let { catId ->
                            val catName = categoryMap[catId]?.name ?: "Category"
                            AssistChip(
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("Category: $catName ✕", fontSize = 11.sp, color = NeonEmerald) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald)
                            )
                        }
                        selectedVendorFilter?.let { vendor ->
                            AssistChip(
                                onClick = { selectedVendorFilter = null },
                                label = { Text("Vendor: $vendor ✕", fontSize = 11.sp, color = NeonCyan) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                            )
                        }
                    }
                }
            }
        }
    },
    containerColor = OledBlack
) { innerPadding ->
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching transactions",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                groupedByDate.forEach { (dateHeader, txsInDate) ->
                    item(key = "header_$dateHeader") {
                        Text(
                            text = dateHeader,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }

                    items(txsInDate, key = { it.id }) { tx ->
                        val cat = categoryMap[tx.categoryId]
                        val isSelected = selectedIds.contains(tx.id)

                        FoldTransactionFeedCard(
                            transaction = tx,
                            categoryName = cat?.name ?: "Uncategorized",
                            isUnmapped = tx.categoryId == null,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedIds.remove(tx.id) else selectedIds.add(tx.id)
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds.add(tx.id)
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedIds.remove(tx.id) else selectedIds.add(tx.id)
                                } else {
                                    selectedTransactionForSheet = tx
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    selectedTransactionForSheet?.let { tx ->
        TransactionDetailBottomSheet(
            transaction = tx,
            viewModel = viewModel,
            onDismiss = { selectedTransactionForSheet = null }
        )
    }

    // Bulk Recategorize Dialog
    if (showRecategorizeDialog) {
        AlertDialog(
            onDismissRequest = { showRecategorizeDialog = false },
            title = { Text("Recategorize ${selectedIds.size} transactions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.bulkRecategorizeTransactions(selectedIds.toList(), cat.id)
                                    showRecategorizeDialog = false
                                    isSelectionMode = false
                                    selectedIds.clear()
                                }
                                .padding(12.dp)
                        ) {
                            Text(cat.name, fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRecategorizeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Move Account Dialog
    if (showMoveAccountDialog) {
        AlertDialog(
            onDismissRequest = { showMoveAccountDialog = false },
            title = { Text("Move ${selectedIds.size} transactions to account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.filter { !it.isArchived }.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.bulkMoveTransactionsToAccount(selectedIds.toList(), acc.id)
                                    showMoveAccountDialog = false
                                    isSelectionMode = false
                                    selectedIds.clear()
                                }
                                .padding(12.dp)
                        ) {
                            Text("${acc.displayName} (••${acc.last4})", fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Delete Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedIds.size} transactions?") },
            text = { Text("These transactions will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkDeleteTransactions(selectedIds.toList())
                        showDeleteConfirmDialog = false
                        isSelectionMode = false
                        selectedIds.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}
