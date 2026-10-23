package com.campusfold.tracker.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.MergeLogEntity
import com.campusfold.tracker.data.merge.AccountMergeManager
import com.campusfold.tracker.data.merge.DuplicatePair
import com.campusfold.tracker.ui.theme.*

/**
 * Card displayed on Home and Accounts when an automatic merge occurs.
 * "Merged Axis Bank ••2003 and Axis Bank XX2003 automatically" with an Undo button.
 */
@Composable
fun AutoMergedCard(
    mergeLog: MergeLogEntity,
    accounts: List<AccountEntity>,
    onUndo: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val survivingAccount = remember(mergeLog.survivingAccountId, accounts) {
        accounts.find { it.id == mergeLog.survivingAccountId }
    }
    val mergedAccount = remember(mergeLog.mergedAccountSnapshot) {
        try {
            AccountMergeManager.deserializeAccountSnapshot(mergeLog.mergedAccountSnapshot)
        } catch (_: Exception) {
            null
        }
    }

    val survivorName = survivingAccount?.displayName ?: "Primary Account"
    val mergedName = mergedAccount?.displayName ?: "Duplicate Account"

    FoldCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color(0xFF0F1E28),
        borderColor = NeonCyan.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallMerge,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ACCOUNTS AUTO-MERGED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonCyan
                    )
                }

                IconButton(
                    onClick = { onDismiss(mergeLog.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Merged '$mergedName' into '$survivorName' automatically.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "All transactions and identifiers were unified. Undo anytime to restore.",
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onUndo(mergeLog.id) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = OledBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Suggested Merge Card displayed when a partial or AI-assisted duplicate is detected.
 * Shows both accounts side by side with Merge, Not the same, and Dismiss actions.
 */
@Composable
fun SuggestedDuplicateCard(
    pair: DuplicatePair,
    onMerge: (source: AccountEntity, target: AccountEntity) -> Unit,
    onNotSame: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    FoldCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color(0xFF231C10),
        borderColor = NeonAmber.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (pair.isAiOnly) Icons.Default.AutoAwesome else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (pair.isAiOnly) NeonPurple else NeonAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (pair.isAiOnly) "AI SUGGESTED DUPLICATE" else "POSSIBLE DUPLICATE ACCOUNTS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (pair.isAiOnly) NeonPurple else NeonAmber
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = pair.reason,
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Side-by-side Accounts Comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Account A Card
                AccountComparisonBox(
                    account = pair.accountA,
                    modifier = Modifier.weight(1f)
                )

                // Account B Card
                AccountComparisonBox(
                    account = pair.accountB,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: Merge, Not the same, Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onNotSame(pair.accountA.id, pair.accountB.id) },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonCrimson,
                        containerColor = InputBg
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Not the same", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Dismiss", fontSize = 10.sp, color = TextMuted)
                    }

                    Button(
                        onClick = { onMerge(pair.accountB, pair.accountA) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = OledBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallMerge,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AccountComparisonBox(
    account: AccountEntity,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(InputBg)
            .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = account.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = account.bankName,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.last4.isNotBlank()) {
                    Text(
                        text = "••${account.last4}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            StatusPill(
                text = account.type.name,
                bgColor = if (account.type == AccountType.CREDIT_CARD) PillBgCard else PillBgIncome,
                textColor = if (account.type == AccountType.CREDIT_CARD) PillTextCard else PillTextIncome,
                fontSize = 8.sp
            )
        }
    }
}

/**
 * Manual Merge Dialog: Allows picking source & target accounts,
 * previewing transaction moves, selecting surviving attributes, and confirming merge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualMergeAccountsDialog(
    accounts: List<AccountEntity>,
    preSelectedSourceId: Long? = null,
    onCountTransactions: (Long, (Int) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirmMerge: (survivingAccountId: Long, mergedAccountId: Long, name: String?, type: AccountType?, last4: String?) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }

    var selectedSourceId by remember {
        mutableStateOf(preSelectedSourceId ?: activeAccounts.firstOrNull()?.id)
    }
    var selectedTargetId by remember {
        mutableStateOf(activeAccounts.find { it.id != selectedSourceId }?.id)
    }

    var txCountToMove by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedSourceId) {
        val sId = selectedSourceId
        if (sId != null) {
            onCountTransactions(sId) { count ->
                txCountToMove = count
            }
        } else {
            txCountToMove = 0
        }
    }

    val sourceAccount = remember(selectedSourceId, activeAccounts) {
        activeAccounts.find { it.id == selectedSourceId }
    }
    val targetAccount = remember(selectedTargetId, activeAccounts) {
        activeAccounts.find { it.id == selectedTargetId }
    }

    // Surviving attributes custom pickers
    var keepNameOption by remember { mutableStateOf(1) } // 0: Source, 1: Target
    var keepTypeOption by remember { mutableStateOf(1) } // 0: Source, 1: Target
    var keepLast4Option by remember { mutableStateOf(1) } // 0: Source, 1: Target

    Dialog(onDismissRequest = onDismiss) {
        FoldCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            containerColor = CardElevated,
            borderColor = BorderCharcoal
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MERGE ACCOUNTS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Combine two accounts into one. All transactions, split links, and aliases will be transferred to the surviving account.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Source Account (The one that will be merged into target)
                Text(
                    text = "1. Account to merge & remove:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))

                var sourceMenuExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { sourceMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(sourceAccount?.displayName ?: "Select Source Account", fontSize = 11.sp, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false },
                        modifier = Modifier.background(CardElevated)
                    ) {
                        activeAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.displayName} (${acc.bankName})", fontSize = 11.sp, color = TextPrimary) },
                                onClick = {
                                    selectedSourceId = acc.id
                                    if (selectedTargetId == acc.id) {
                                        selectedTargetId = activeAccounts.find { it.id != acc.id }?.id
                                    }
                                    sourceMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Target Account (The survivor that will keep the transactions)
                Text(
                    text = "2. Surviving account to keep:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))

                var targetMenuExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { targetMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(targetAccount?.displayName ?: "Select Surviving Account", fontSize = 11.sp, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = targetMenuExpanded,
                        onDismissRequest = { targetMenuExpanded = false },
                        modifier = Modifier.background(CardElevated)
                    ) {
                        activeAccounts.filter { it.id != selectedSourceId }.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.displayName} (${acc.bankName})", fontSize = 11.sp, color = TextPrimary) },
                                onClick = {
                                    selectedTargetId = acc.id
                                    targetMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview Info Card
                if (sourceAccount != null && targetAccount != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(InputBg)
                            .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PREVIEW",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Move ${txCountToMove ?: 0} transaction(s) into '${targetAccount.displayName}'.",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "• Save '${sourceAccount.bankName} ••${sourceAccount.last4}' as an alias so future alerts auto-route here.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val canMerge = sourceAccount != null && targetAccount != null && sourceAccount.id != targetAccount.id

                    Button(
                        onClick = {
                            if (canMerge) {
                                val finalName = if (keepNameOption == 0) sourceAccount!!.displayName else targetAccount!!.displayName
                                val finalType = if (keepTypeOption == 0) sourceAccount!!.type else targetAccount!!.type
                                val finalLast4 = if (keepLast4Option == 0) sourceAccount!!.last4 else targetAccount!!.last4

                                onConfirmMerge(
                                    targetAccount!!.id,
                                    sourceAccount!!.id,
                                    finalName,
                                    finalType,
                                    finalLast4
                                )
                            }
                        },
                        enabled = canMerge,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = OledBlack
                        )
                    ) {
                        Text("Confirm Merge", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
