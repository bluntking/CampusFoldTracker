package com.campusfold.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.sheets.TransactionDetailBottomSheet
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAddTransaction: () -> Unit
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val totalMyExpense by viewModel.totalMyExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val vendorMappings by viewModel.vendorMappings.collectAsState()
    val personsWithBalances by viewModel.personsWithBalances.collectAsState()
    val allPersons by viewModel.allPersons.collectAsState()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsState()
    val pnbCreditLimit by viewModel.pnbCreditLimit.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val totalOwedToMe = personsWithBalances.sumOf { it.totalOwedToMe }

    // Bottom sheet state
    var selectedTransactionForSheet by remember { mutableStateOf<TransactionEntity?>(null) }
    var showSyncMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Sleek Fold Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAMPUSFOLD",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Financial Matrix",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }

                // Sync Menu
                Box {
                    IconButton(
                        onClick = {
                            if (hasSmsPermission) {
                                showSyncMenu = true
                            } else {
                                onRequestPermissions()
                            }
                        },
                        enabled = !isScanning,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MatteCharcoal)
                            .border(1.dp, BorderCharcoal, CircleShape)
                            .size(38.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = NeonEmerald
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync SMS",
                                tint = NeonEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSyncMenu,
                        onDismissRequest = { showSyncMenu = false },
                        modifier = Modifier.background(MatteCharcoal)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sync Last 30 Days", color = TextPrimary) },
                            onClick = {
                                showSyncMenu = false
                                viewModel.scanInbox(daysBack = 30)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sync Last 90 Days", color = TextPrimary) },
                            onClick = {
                                showSyncMenu = false
                                viewModel.scanInbox(daysBack = 90)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Permission Missing Top Sleek Card
            if (!hasSmsPermission) {
                FoldCard(
                    containerColor = Color(0xFF1F1214),
                    borderColor = NeonCrimson.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SMS ACCESS REQUIRED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NeonCrimson
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant READ_SMS & RECEIVE_SMS to parse PNB 8598 and Axis 2003 debit alerts offline.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCrimson,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Grant Permissions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.scanInbox(daysBack = 90) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(BorderCharcoal)
                                )
                            ) {
                                Text("Sync SMS Inbox", fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 1-Tap Pending Settlement Suggestions Banner
            if (pendingSuggestions.isNotEmpty()) {
                val suggestion = pendingSuggestions.first()
                val friendName = personsWithBalances.find { it.id == suggestion.suggestedPersonId }?.name ?: "Friend"
                FoldCard(
                    containerColor = Color(0xFF0F261C),
                    borderColor = NeonEmerald.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FRIEND SETTLEMENT DETECTED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = NeonEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Did $friendName settle ₹${suggestion.amount} via '${suggestion.detectedSenderName}'?",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.confirmSettlementSuggestion(suggestion.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("1-Tap Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissSettlementSuggestion(suggestion.id) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Dismiss", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Top Summary Card (Fold Money Style)
            FoldCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MY TRUE MONTHLY SPEND",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = TextMuted
                        )

                        // Live badge for PNB 8598 Available Limit
                        if (pnbCreditLimit > 0) {
                            StatusPill(
                                text = "PNB 8598: ₹${pnbCreditLimit.toInt()}",
                                bgColor = PillBgCard,
                                textColor = PillTextCard,
                                icon = Icons.Default.CreditCard,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Prominent Monospace Currency Display
                    MonospaceCurrencyText(
                        amount = totalMyExpense,
                        fontSize = 32.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = BorderCharcoal, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary Metrics (Owed to Me & Received)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "OWED TO ME",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            MonospaceCurrencyText(
                                amount = totalOwedToMe,
                                fontSize = 14.sp,
                                color = NeonAmber
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "RECEIVED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            MonospaceCurrencyText(
                                amount = totalIncome,
                                fontSize = 14.sp,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { viewModel.setFilter("ALL") },
                    label = { Text("All", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MatteCharcoal,
                        selectedLabelColor = TextPrimary,
                        containerColor = InputBg,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "ALL",
                        borderColor = BorderCharcoal,
                        selectedBorderColor = NeonEmerald
                    )
                )
                FilterChip(
                    selected = selectedFilter == "EXPENSE",
                    onClick = { viewModel.setFilter("EXPENSE") },
                    label = { Text("Expenses", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MatteCharcoal,
                        selectedLabelColor = NeonCrimson,
                        containerColor = InputBg,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "EXPENSE",
                        borderColor = BorderCharcoal,
                        selectedBorderColor = NeonCrimson
                    )
                )
                FilterChip(
                    selected = selectedFilter == "INCOME",
                    onClick = { viewModel.setFilter("INCOME") },
                    label = { Text("Income", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MatteCharcoal,
                        selectedLabelColor = NeonEmerald,
                        containerColor = InputBg,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "INCOME",
                        borderColor = BorderCharcoal,
                        selectedBorderColor = NeonEmerald
                    )
                )
                FilterChip(
                    selected = selectedFilter == "CC_BILL",
                    onClick = { viewModel.setFilter("CC_BILL") },
                    label = { Text("CC Bills", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MatteCharcoal,
                        selectedLabelColor = NeonCyan,
                        containerColor = InputBg,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "CC_BILL",
                        borderColor = BorderCharcoal,
                        selectedBorderColor = NeonCyan
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transaction Feed
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
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Sync SMS above or '+' below to log an expense.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(transactions, key = { it.id }) { txn ->
                        val category = categories.find { it.id == txn.categoryId }
                        val isMapped = vendorMappings.any {
                            it.rawSmsKeyword.equals(txn.rawMerchant, ignoreCase = true)
                        }

                        FoldTransactionFeedCard(
                            transaction = txn,
                            categoryName = category?.name ?: "General",
                            isUnmapped = !isMapped && txn.rawMerchant.isNotBlank(),
                            onClick = { selectedTransactionForSheet = txn }
                        )
                    }
                }
            }
        }

        // Floating Action Button to add any transaction
        FloatingActionButton(
            onClick = onOpenAddTransaction,
            containerColor = NeonEmerald,
            contentColor = OledBlack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Transaction")
        }
    }

    // Edit & Actions Bottom Sheet
    selectedTransactionForSheet?.let { tx ->
        val splitsFlow = remember(tx.id) { viewModel.getTransactionWithSplits(tx.id) }
        val txWithSplits by splitsFlow.collectAsState(initial = null)

        TransactionDetailBottomSheet(
            transaction = tx,
            categories = categories,
            vendorMappings = vendorMappings,
            allPersons = allPersons,
            initialSplits = txWithSplits?.splits ?: emptyList(),
            onDismiss = { selectedTransactionForSheet = null },
            onSaveTransaction = { updatedTx, splits ->
                viewModel.updateTransactionWithSplits(updatedTx, splits)
            },
            onToggleCcBill = {
                viewModel.toggleCcBillTransfer(tx.id)
            },
            onSaveVendorMapping = { keyword, friendly, catId ->
                viewModel.saveVendorMappingForFuture(keyword, friendly, catId)
            },
            onAddPersonInline = { name, aliases ->
                viewModel.addPerson(name, aliases)
            },
            onDeletePersonInline = { person ->
                viewModel.deletePerson(person)
            },
            onDelete = {
                viewModel.deleteTransaction(tx)
                selectedTransactionForSheet = null
            }
        )
    }
}

@Composable
fun FoldTransactionFeedCard(
    transaction: TransactionEntity,
    categoryName: String,
    isUnmapped: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.timestamp))
    val isCredit = transaction.type == TransactionType.INCOME
    val isCcBill = transaction.type == TransactionType.CC_BILL_TRANSFER

    FoldCard(
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCcBill -> PillBgCcBill
                                isCredit -> PillBgIncome
                                else -> PillBgExpense
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
                            isCcBill -> PillTextCcBill
                            isCredit -> PillTextIncome
                            else -> PillTextExpense
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Friendly vendor name & unmapped indicator
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.friendlyVendorName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        if (isUnmapped) {
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusPill(
                                text = "Unmapped",
                                bgColor = PillBgUnmapped,
                                textColor = PillTextUnmapped,
                                fontSize = 8.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Category pill & Payment badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusPill(
                            text = categoryName,
                            bgColor = InputBg,
                            textColor = TextSecondary,
                            fontSize = 9.sp
                        )

                        // Payment badge ('PNB 8598' or 'Axis 2003')
                        val badgeText = when {
                            transaction.paymentMode.contains("8598") -> "PNB 8598"
                            transaction.paymentMode.contains("2003") -> "Axis 2003"
                            else -> transaction.paymentMode
                        }
                        StatusPill(
                            text = badgeText,
                            bgColor = PillBgCard,
                            textColor = PillTextCard,
                            fontSize = 9.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }

                // Currency Amount Display
                Column(horizontalAlignment = Alignment.End) {
                    val sign = when {
                        isCcBill -> "⇌ "
                        isCredit -> "+"
                        else -> "-"
                    }
                    MonospaceCurrencyText(
                        amount = transaction.amount,
                        prefix = sign + "₹",
                        fontSize = 15.sp,
                        color = when {
                            isCcBill -> PillTextCcBill
                            isCredit -> PillTextIncome
                            else -> TextPrimary
                        }
                    )

                    // Total paid vs. My Share (if split)
                    if (transaction.myActualShare != transaction.amount && !isCcBill) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "My Share: ",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            MonospaceCurrencyText(
                                amount = transaction.myActualShare,
                                fontSize = 11.sp,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}
