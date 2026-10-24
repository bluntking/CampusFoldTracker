package com.campusfold.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.BalanceSource
import com.campusfold.tracker.data.entity.MergeLogEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.*
import com.campusfold.tracker.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: TransactionViewModel,
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val derivedBalances by viewModel.derivedAccountBalances.collectAsStateWithLifecycle()
    val netOverview by viewModel.netBalanceOverview.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchivedAccounts.collectAsStateWithLifecycle()
    val latestAutoMerge by viewModel.latestAutoMergeLog.collectAsStateWithLifecycle()
    val dismissedAutoMergeId by viewModel.dismissedAutoMergeLogId.collectAsStateWithLifecycle()
    val suggestedDuplicates by viewModel.suggestedDuplicates.collectAsStateWithLifecycle()
    val allActiveMergeLogs by viewModel.allActiveMergeLogs.collectAsStateWithLifecycle()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDeleteWithReassign by remember { mutableStateOf<AccountEntity?>(null) }
    var accountForManualBalance by remember { mutableStateOf<AccountEntity?>(null) }
    var showMergeAccountsDialog by remember { mutableStateOf(false) }
    var mergePreselectedSourceId by remember { mutableStateOf<Long?>(null) }

    val displayedAccounts = remember(accounts, showArchived) {
        if (showArchived) accounts else accounts.filter { !it.isArchived }
    }
    val creditCards = displayedAccounts.filter { it.type == AccountType.CREDIT_CARD }
    val bankAccounts = displayedAccounts.filter { it.type != AccountType.CREDIT_CARD }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val trackedSinceFormatter = remember {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimensions.Padding.content)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Top Bar Header with Back Arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACCOUNTS & CARDS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Manage Accounts",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (accounts.size >= 2) {
                        OutlinedButton(
                            onClick = {
                                mergePreselectedSourceId = null
                                showMergeAccountsDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.CallMerge,
                                contentDescription = "Merge",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Merge",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = {
                            accountToEdit = null
                            showAddAccountDialog = true
                        },
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Account",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Status Line: Tracked since <date>
            val trackedDate = if (netOverview.earliestTrackedTimestamp > 0L) {
                trackedSinceFormatter.format(Date(netOverview.earliestTrackedTimestamp))
            } else "recently"
            Text(
                text = "Tracked since $trackedDate",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Archive Filter Toggle Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${bankAccounts.size} Bank Accounts • ${creditCards.size} Credit Cards",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.toggleShowArchivedAccounts() }
                ) {
                    Icon(
                        imageVector = if (showArchived) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showArchived) "Showing Archived" else "Archived Hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-Merged Notification Card (if present)
            if (latestAutoMerge != null && latestAutoMerge?.undoneAt == null && latestAutoMerge?.id != dismissedAutoMergeId) {
                AutoMergedCard(
                    mergeLog = latestAutoMerge!!,
                    accounts = accounts,
                    onUndo = { viewModel.undoMerge(it) },
                    onDismiss = { viewModel.dismissAutoMergeNotification(it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Suggested Duplicates Cards
            suggestedDuplicates.forEach { pair ->
                SuggestedDuplicateCard(
                    pair = pair,
                    onMerge = { source, _ ->
                        mergePreselectedSourceId = source.id
                        showMergeAccountsDialog = true
                    },
                    onNotSame = { a, b -> viewModel.markPairNotSame(a, b) },
                    onDismiss = { viewModel.dismissSuggestedDuplicate(pair) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Accounts List grouped by Bank Accounts and Credit Cards
            if (displayedAccounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No payment accounts configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showAddAccountDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Add First Account")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // 1. Bank Accounts Group
                    if (bankAccounts.isNotEmpty()) {
                        item {
                            Text(
                                text = "BANK ACCOUNTS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        items(bankAccounts, key = { it.id }) { account ->
                            val activeMergeLog = allActiveMergeLogs.find { it.survivingAccountId == account.id }
                            val derived = derivedBalances.find { it.account.id == account.id }
                            AccountReferenceCard(
                                account = account,
                                derived = derived,
                                activeMergeLog = activeMergeLog,
                                currencyFormatter = currencyFormatter,
                                onUpdateBalance = { accountForManualBalance = account },
                                onUseBankBalance = {
                                    viewModel.updateAccountAnchor(
                                        account.id,
                                        account.balancePaise,
                                        account.balancePaise,
                                        account.balanceAsOf
                                    )
                                },
                                onEdit = {
                                    accountToEdit = account
                                    showAddAccountDialog = true
                                },
                                onClearManualBalance = { viewModel.clearManualBalanceOverride(account.id) },
                                onToggleArchive = { viewModel.setAccountArchived(account.id, !account.isArchived) },
                                onMergeWith = {
                                    mergePreselectedSourceId = account.id
                                    showMergeAccountsDialog = true
                                },
                                onSplitUndoMerge = { logId -> viewModel.undoMerge(logId) },
                                onDelete = { accountToDeleteWithReassign = account }
                            )
                        }
                    }

                    // 2. Credit Cards Group
                    if (creditCards.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "CREDIT CARDS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        items(creditCards, key = { it.id }) { card ->
                            val activeMergeLog = allActiveMergeLogs.find { it.survivingAccountId == card.id }
                            val derived = derivedBalances.find { it.account.id == card.id }
                            AccountReferenceCard(
                                account = card,
                                derived = derived,
                                activeMergeLog = activeMergeLog,
                                currencyFormatter = currencyFormatter,
                                onUpdateBalance = { accountForManualBalance = card },
                                onUseBankBalance = {
                                    viewModel.updateAccountAnchor(
                                        card.id,
                                        card.balancePaise,
                                        card.balancePaise,
                                        card.balanceAsOf
                                    )
                                },
                                onEdit = {
                                    accountToEdit = card
                                    showAddAccountDialog = true
                                },
                                onClearManualBalance = { viewModel.clearManualBalanceOverride(card.id) },
                                onToggleArchive = { viewModel.setAccountArchived(card.id, !card.isArchived) },
                                onMergeWith = {
                                    mergePreselectedSourceId = card.id
                                    showMergeAccountsDialog = true
                                },
                                onSplitUndoMerge = { logId -> viewModel.undoMerge(logId) },
                                onDelete = { accountToDeleteWithReassign = card }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    // Manual Merge Accounts Dialog
    if (showMergeAccountsDialog) {
        ManualMergeAccountsDialog(
            accounts = accounts,
            preSelectedSourceId = mergePreselectedSourceId,
            onCountTransactions = { accId, callback ->
                viewModel.countTransactionsForAccount(accId, callback)
            },
            onDismiss = {
                showMergeAccountsDialog = false
                mergePreselectedSourceId = null
            },
            onConfirmMerge = { survivingId, mergedId, name, type, last4 ->
                viewModel.mergeAccounts(
                    survivingAccountId = survivingId,
                    mergedAccountId = mergedId,
                    overrideDisplayName = name,
                    overrideType = type,
                    overrideLast4 = last4
                )
                showMergeAccountsDialog = false
                mergePreselectedSourceId = null
            }
        )
    }

    // Add / Edit Account Dialog
    if (showAddAccountDialog) {
        AddEditAccountDialog(
            account = accountToEdit,
            onDismiss = {
                showAddAccountDialog = false
                accountToEdit = null
            },
            onSave = { name, bankName, type, last4, limit, colorHex ->
                if (accountToEdit == null) {
                    viewModel.addAccount(name, bankName, type, last4, limit, colorHex)
                } else {
                    viewModel.editAccount(accountToEdit!!.id, name, bankName, type, last4, limit, colorHex)
                }
                showAddAccountDialog = false
                accountToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog with Reassign
    accountToDeleteWithReassign?.let { acc ->
        DeleteAccountReassignDialog(
            account = acc,
            accounts = accounts,
            onDismiss = { accountToDeleteWithReassign = null },
            onConfirm = { targetAccountId ->
                viewModel.deleteAccountWithReassign(acc.id, targetAccountId)
                accountToDeleteWithReassign = null
            }
        )
    }

    // Set Manual Balance / Anchor Dialog
    accountForManualBalance?.let { acc ->
        SetManualBalanceDialog(
            account = acc,
            onDismiss = { accountForManualBalance = null },
            onSaveAnchor = { balancePaise, owedPaise, asOf ->
                viewModel.updateAccountAnchor(acc.id, balancePaise, owedPaise, asOf)
                accountForManualBalance = null
            }
        )
    }
}

/**
 * Restyled Account Card matching Reference Design:
 * - Bank Icon on left
 * - "Bank Name ••1234"
 * - Derived Balance on right
 * - Discrepancy indicator with "Use bank balance"
 * - Outlined "Update Balance" button
 * - 3-dot overflow menu
 */
@Composable
fun AccountReferenceCard(
    account: AccountEntity,
    derived: com.campusfold.tracker.data.model.DerivedAccountBalance? = null,
    activeMergeLog: MergeLogEntity? = null,
    currencyFormatter: NumberFormat,
    onUpdateBalance: () -> Unit,
    onUseBankBalance: () -> Unit = {},
    onEdit: () -> Unit,
    onClearManualBalance: () -> Unit,
    onToggleArchive: () -> Unit,
    onMergeWith: () -> Unit,
    onSplitUndoMerge: (Long) -> Unit,
    onDelete: () -> Unit
) {
    val isCreditCard = account.type == AccountType.CREDIT_CARD

    var menuExpanded by remember { mutableStateOf(false) }

    val asOfFormatter = remember {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (account.isArchived) 0.25f else 0.45f
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.card)
        ) {
            // Row 1: Bank Icon, Account Name & Subtitle on left, 3-dot overflow menu on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Bank Icon & Title
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (account.displayName.isNotBlank()) account.displayName else account.bankName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (account.isArchived) {
                                Surface(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Archived",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        val accountTypeLabel = when (account.type) {
                            AccountType.SAVINGS -> "Savings Account"
                            AccountType.CURRENT -> "Current Account"
                            AccountType.CREDIT_CARD -> "Credit Card"
                            AccountType.WALLET -> "Digital Wallet"
                            AccountType.OTHER -> "Other Account"
                            AccountType.UNASSIGNED -> "Account"
                        }
                        val subtitleText = if (account.displayName.isNotBlank() && !account.displayName.contains(account.bankName, ignoreCase = true)) {
                            if (account.last4.isNotBlank()) "${account.bankName} ••${account.last4} • $accountTypeLabel"
                            else "${account.bankName} • $accountTypeLabel"
                        } else {
                            accountTypeLabel
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3-dot overflow menu on top right
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Details") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )

                        if (account.anchorSet && account.balanceSource == BalanceSource.MANUAL) {
                            DropdownMenuItem(
                                text = { Text("Clear Manual Balance") },
                                onClick = {
                                    menuExpanded = false
                                    onClearManualBalance()
                                },
                                leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(if (account.isArchived) "Unarchive Account" else "Archive Account") },
                            onClick = {
                                menuExpanded = false
                                onToggleArchive()
                            },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                        )

                        DropdownMenuItem(
                            text = { Text("Merge into Another...") },
                            onClick = {
                                menuExpanded = false
                                onMergeWith()
                            },
                            leadingIcon = { Icon(Icons.Default.CallMerge, contentDescription = null) }
                        )

                        if (activeMergeLog != null) {
                            DropdownMenuItem(
                                text = { Text("Undo Last Merge", color = NeonCyan) },
                                onClick = {
                                    menuExpanded = false
                                    onSplitUndoMerge(activeMergeLog.id)
                                },
                                leadingIcon = { Icon(Icons.Default.Undo, contentDescription = null, tint = NeonCyan) }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Delete Account...", color = NeonCrimson) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = NeonCrimson) }
                        )
                    }
                }
            }

            // Row 2: Dedicated Balance and Update Balance Action
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (isCreditCard) {
                        Text(
                            text = "Owed: ${currencyFormatter.format(derived?.derivedOwed ?: 0.0)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if ((derived?.derivedOwedPaise ?: 0L) > 0) NeonAmber else MaterialTheme.colorScheme.onSurface
                        )
                        if (account.creditLimitPaise > 0L) {
                            Text(
                                text = "Avl: ${currencyFormatter.format(derived?.availableCredit ?: 0.0)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald
                            )
                        }
                    } else {
                        val bal = derived?.derivedBalance ?: (account.balancePaise / 100.0)
                        Text(
                            text = currencyFormatter.format(bal),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (account.anchorSet) "Derived Balance" else "No anchor set",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onUpdateBalance,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Update Balance",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Discrepancy Alert Strip (Part 6)
            if (derived?.hasDiscrepancy == true && derived.discrepancyBankBalance != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonAmber.copy(alpha = 0.12f))
                        .border(1.dp, NeonAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bank reported: ${currencyFormatter.format(derived.discrepancyBankBalance)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeonAmber
                    )
                    TextButton(
                        onClick = onUseBankBalance,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Use bank balance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                    }
                }
            }

            // Middle Status Bar: Balance source / As of timestamp / Merge tag
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Only show "entered manually" if anchor was actually set and source is MANUAL
                    if (account.anchorSet && account.balanceSource == BalanceSource.MANUAL) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "entered manually",
                                color = NeonCyan,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (account.balanceSource == BalanceSource.AUTO && account.balanceAsOf > 0L) {
                        Text(
                            text = "as of ${asOfFormatter.format(Date(account.balanceAsOf))}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (activeMergeLog != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Merged",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Existing Dialogs from Steps 6 & 7 (Preserved) ──────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditAccountDialog(
    account: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (displayName: String, bankName: String, type: AccountType, last4: String, limit: Double, colorHex: String) -> Unit
) {
    var displayName by remember { mutableStateOf(account?.displayName ?: "") }
    var bankName by remember { mutableStateOf(account?.bankName ?: "") }
    var last4 by remember { mutableStateOf(account?.last4 ?: "") }
    var selectedType by remember { mutableStateOf(account?.type ?: AccountType.SAVINGS) }
    var limitInput by remember { mutableStateOf(if (account != null && account.creditLimitPaise > 0) (account.creditLimitPaise / 100.0).toInt().toString() else "") }
    var selectedColor by remember { mutableStateOf(account?.color ?: "#10B981") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Add Account" else "Edit Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Account Name / Alias") },
                    placeholder = { Text("e.g. Salary Account or Campus Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank / Institution Name") },
                    placeholder = { Text("e.g. HDFC Bank, SBI, ICICI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = last4,
                        onValueChange = { if (it.length <= 4) last4 = it },
                        label = { Text("Last 4 Digits") },
                        placeholder = { Text("1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Type", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = selectedType == AccountType.SAVINGS,
                                onClick = { selectedType = AccountType.SAVINGS },
                                label = { Text("Savings", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = selectedType == AccountType.CREDIT_CARD,
                                onClick = { selectedType = AccountType.CREDIT_CARD },
                                label = { Text("Credit", fontSize = 10.sp) }
                            )
                        }
                    }
                }

                if (selectedType == AccountType.CREDIT_CARD) {
                    OutlinedTextField(
                        value = limitInput,
                        onValueChange = { limitInput = it },
                        label = { Text("Credit Limit (₹)") },
                        placeholder = { Text("50000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitInput.toDoubleOrNull() ?: 0.0
                    onSave(
                        displayName.trim().ifEmpty { bankName.trim() },
                        bankName.trim(),
                        selectedType,
                        last4.trim(),
                        limit,
                        selectedColor
                    )
                },
                enabled = bankName.isNotBlank()
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

@Composable
fun SetManualBalanceDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSaveAnchor: (balancePaise: Long, owedPaise: Long, asOf: Long) -> Unit
) {
    val isCreditCard = account.type == AccountType.CREDIT_CARD
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var amountInput by remember {
        val initialAmt = if (isCreditCard) {
            if (account.openingOwedPaise > 0L) account.openingOwedPaise / 100.0
            else if (account.balancePaise > 0L) account.balancePaise / 100.0 else 0.0
        } else {
            if (account.openingBalancePaise > 0L) account.openingBalancePaise / 100.0
            else if (account.balancePaise > 0L) account.balancePaise / 100.0 else 0.0
        }
        mutableStateOf(if (initialAmt > 0.0) initialAmt.toString() else "")
    }

    var selectedAsOf by remember {
        mutableStateOf(if (account.openingAsOf > 0L) account.openingAsOf else System.currentTimeMillis())
    }

    val calendar = remember(selectedAsOf) {
        Calendar.getInstance().apply { timeInMillis = selectedAsOf }
    }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedAsOf = newCal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCreditCard) "Set Card Opening / Owed Balance" else "Set Account Opening Balance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Anchor balance for ${account.displayName.ifEmpty { account.bankName }} (••${account.last4}). All transactions on or after this date will be tracked against this balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(if (isCreditCard) "Current Amount Owed (₹)" else "Opening Balance (₹)") },
                    placeholder = { Text(if (isCreditCard) "e.g. 15000" else "e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Picker trigger
                OutlinedCard(
                    onClick = { datePickerDialog.show() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "As of date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                dateFormatter.format(Date(selectedAsOf)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Pick date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amtDouble = amountInput.toDoubleOrNull() ?: 0.0
                    val amtPaise = (amtDouble * 100).toLong().coerceAtLeast(0L)
                    if (isCreditCard) {
                        onSaveAnchor(0L, amtPaise, selectedAsOf)
                    } else {
                        onSaveAnchor(amtPaise, 0L, selectedAsOf)
                    }
                }
            ) {
                Text("Save Anchor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteAccountReassignDialog(
    account: AccountEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (targetAccountId: Long?) -> Unit
) {
    val otherAccounts = remember(accounts, account) {
        accounts.filter { it.id != account.id && !it.isArchived }
    }
    var selectedTargetId by remember { mutableStateOf<Long?>(otherAccounts.firstOrNull()?.id) }
    var reassignChoice by remember { mutableStateOf(if (otherAccounts.isNotEmpty()) "REASSIGN" else "NONE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to delete ${account.displayName.ifEmpty { account.bankName }} (••${account.last4})?",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (otherAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Where should its existing transactions go?",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = reassignChoice == "REASSIGN",
                            onClick = { reassignChoice = "REASSIGN" }
                        )
                        Text("Reassign to another account", style = MaterialTheme.typography.bodySmall)
                    }

                    if (reassignChoice == "REASSIGN") {
                        Column(modifier = Modifier.padding(start = 24.dp)) {
                            otherAccounts.forEach { other ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTargetId = other.id }
                                ) {
                                    RadioButton(
                                        selected = selectedTargetId == other.id,
                                        onClick = { selectedTargetId = other.id }
                                    )
                                    Text(
                                        text = "${other.displayName.ifEmpty { other.bankName }} ••${other.last4}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = reassignChoice == "NONE",
                            onClick = { reassignChoice = "NONE" }
                        )
                        Text("Leave unassigned", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = if (reassignChoice == "REASSIGN") selectedTargetId else null
                    onConfirm(target)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
