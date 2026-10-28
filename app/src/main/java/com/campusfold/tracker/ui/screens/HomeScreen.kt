package com.campusfold.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.*
import com.campusfold.tracker.ui.sheets.TransactionDetailBottomSheet
import com.campusfold.tracker.ui.theme.*

/**
 * Rebuilt HomeScreen (Part 3):
 * Exactly 7 sections in order:
 * 1. Header: "CAMPUS FOLD", greeting with optional display name, AI Coach button. Thin non-interactive "Syncing" indicator during sync.
 * 2. Needs-attention strip: slim strip when non-empty (review items, duplicates, auto-merge undo, suggestions).
 * 3. Net balance card (Part 6): Net available balance headline, savings vs card owed breakdown, small owed-to-friends line, tracked since.
 * 4. Total spends tile: Month/Week toggle, period picker, Gross Spend, Split Owed, You Owe, Total Income, True Spend headline & budget gauge.
 * 5. Recent Transactions tile: latest 5 transactions, "See All >" button to Transactions screen, tap to detail sheet.
 * 6. My Accounts tile: up to 4 account cards in DashboardAccountCard/BankAvatarLogo style, "Manage >" and "Show all".
 * 7. Activity Heatmap tile: 14 weeks activity, active days & total spent, month dividers, tap day for tooltip.
 */
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    onOpenAiCoach: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToAccountTransactions: (Long) -> Unit = {},
    onNavigateToSplits: () -> Unit,
    onNavigateToSettingsReview: () -> Unit,
    initialSelectedTransaction: TransactionEntity? = null,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val netOverview by viewModel.netBalanceOverview.collectAsStateWithLifecycle()
    val derivedBalances by viewModel.derivedAccountBalances.collectAsStateWithLifecycle()
    val isBalanceHidden by viewModel.hideBalances.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.overallMonthlyBudget.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    // Needs-attention state
    val pendingReviewItems by viewModel.pendingReviewItems.collectAsStateWithLifecycle()
    val suggestedDuplicates by viewModel.suggestedDuplicates.collectAsStateWithLifecycle()
    val latestAutoMerge by viewModel.latestAutoMergeLog.collectAsStateWithLifecycle()
    val dismissedAutoMergeId by viewModel.dismissedAutoMergeLogId.collectAsStateWithLifecycle()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsStateWithLifecycle()

    var selectedTransactionForSheet by remember { mutableStateOf<TransactionEntity?>(initialSelectedTransaction) }

    val hasAttentionItems = remember(pendingReviewItems, suggestedDuplicates, latestAutoMerge, dismissedAutoMergeId, pendingSuggestions) {
        pendingReviewItems.isNotEmpty() ||
                suggestedDuplicates.isNotEmpty() ||
                (latestAutoMerge != null && latestAutoMerge?.id != dismissedAutoMergeId) ||
                pendingSuggestions.isNotEmpty()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ─── 1. Header ─────────────────────────────────────────────────────────────
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAMPUS FOLD",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val greeting = if (userName.isNotBlank()) "Hello, $userName" else "Hello"
                    Text(
                        text = greeting,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Thin non-interactive "Syncing..." pill when sync runs in background
                    if (isSyncing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(InputBg)
                                .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = NeonEmerald,
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Syncing",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // AI Coach Button
                    IconButton(
                        onClick = onOpenAiCoach,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MatteCharcoal)
                            .border(1.dp, BorderCharcoal, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Coach",
                            tint = NeonPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ─── 2. Needs-Attention Strip (shown only when non-empty) ─────────────────
        if (hasAttentionItems) {
            item(key = "needs_attention") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Review Queue count
                        if (pendingReviewItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSettingsReview() }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonAmber))
                                    Text(
                                        text = "${pendingReviewItems.size} review items need attention",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Duplicate accounts
                        if (suggestedDuplicates.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAccounts() }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonPurple))
                                    Text(
                                        text = "Possible duplicate accounts detected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Auto-merge with Undo
                        latestAutoMerge?.let { log ->
                            if (log.id != dismissedAutoMergeId) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonCyan))
                                        Text(
                                            text = "Accounts merged automatically",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.undoMerge(log.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Undo", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Pending settlement suggestions
                        if (pendingSuggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSplits() }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonEmerald))
                                    Text(
                                        text = "${pendingSuggestions.size} pending settlement suggestions",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ─── 3. Net Balance Card (Part 6) ──────────────────────────────────────────
        item(key = "net_balance_card") {
            BalanceCard(
                netOverview = netOverview,
                isBalanceHidden = isBalanceHidden,
                onToggleBalanceVisibility = { viewModel.setHideBalances(!isBalanceHidden) },
                onManageAccounts = onNavigateToAccounts,
                onSetOpeningBalances = onNavigateToAccounts
            )
        }

        // ─── 4. Total Spends Tile ──────────────────────────────────────────────────
        item(key = "spending_tile") {
            HomeSpendingTile(
                transactions = allTransactions,
                netOverview = netOverview,
                monthlyBudget = monthlyBudget
            )
        }

        // ─── 5. Recent Transactions Tile ───────────────────────────────────────────
        item(key = "recent_transactions_tile") {
            HomeRecentTransactionsTile(
                transactions = allTransactions,
                categories = categories,
                onSeeAll = onNavigateToTransactions,
                onTransactionClick = { tx -> selectedTransactionForSheet = tx }
            )
        }

        // ─── 6. My Accounts Tile ───────────────────────────────────────────────────
        item(key = "my_accounts_tile") {
            HomeAccountsTile(
                derivedBalances = derivedBalances,
                isAmountHidden = isBalanceHidden,
                onManageAccounts = onNavigateToAccounts,
                onAccountClick = onNavigateToAccountTransactions
            )
        }

        // ─── 7. Activity Heatmap Tile ──────────────────────────────────────────────
        item(key = "activity_heatmap_tile") {
            HomeActivityHeatmap(
                transactions = allTransactions
            )
        }
    }

    // Detail Sheet for clicked transaction
    selectedTransactionForSheet?.let { tx ->
        TransactionDetailBottomSheet(
            transaction = tx,
            viewModel = viewModel,
            onDismiss = { selectedTransactionForSheet = null }
        )
    }
}
