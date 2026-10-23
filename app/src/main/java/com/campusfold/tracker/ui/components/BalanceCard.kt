package com.campusfold.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.BalanceSource
import com.campusfold.tracker.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reference-styled Balance Card.
 * Adheres strictly to Balance Display Rules:
 * - Total balance sums ONLY savings / checking / wallet accounts.
 * - Credit cards are displayed separately under "Available Credit" and NEVER added into the savings total.
 * - Shows latest balance parsed from messages with "as of" timestamp OR manual value labeled "entered manually".
 * - Shows "Balance not detected yet" when no balance is known.
 * - NEVER computes balance by summing transactions unless starting balance is known.
 */
@Composable
fun BalanceCard(
    accounts: List<AccountEntity>,
    isBalanceHidden: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onManageAccounts: () -> Unit,
    onUpdateAccountBalance: (AccountEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevronRotation"
    )

    // Separate bank accounts (savings/current/wallet) from credit cards
    val bankAccounts = remember(accounts) {
        accounts.filter { !it.isArchived && it.type != AccountType.CREDIT_CARD }
    }
    val creditCards = remember(accounts) {
        accounts.filter { !it.isArchived && it.type == AccountType.CREDIT_CARD }
    }

    // Total of savings accounts with detected or manual balance
    val knownBankAccounts = remember(bankAccounts) {
        bankAccounts.filter { isAccountBalanceKnown(it) }
    }
    val totalSavingsBalancePaise = remember(knownBankAccounts) {
        knownBankAccounts.sumOf { it.balancePaise }
    }
    val totalSavingsBalanceRupees = totalSavingsBalancePaise / 100.0

    // Total available credit limit across credit cards
    val totalAvailableCreditRupees = remember(creditCards) {
        creditCards.sumOf { it.creditLimitPaise } / 100.0
    }

    // Latest balance timestamp across known accounts
    val latestBalanceAsOf = remember(accounts) {
        accounts.filter { isAccountBalanceKnown(it) && it.balanceAsOf > 0L }
            .maxOfOrNull { it.balanceAsOf }
    }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val asOfFormatter = remember {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .animateContentSize(animationSpec = tween(300)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.card)
        ) {
            // Header Row: Label, Eye icon, Expand chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "TOTAL SAVINGS BALANCE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = onToggleBalanceVisibility,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isBalanceHidden) "Show Balance" else "Hide Balance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                ) {
                    Text(
                        text = "${bankAccounts.size} accounts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand accounts",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Hero Balance Amount
            if (knownBankAccounts.isEmpty()) {
                Text(
                    text = "Balance not detected yet",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (isBalanceHidden) "••••••" else currencyFormatter.format(totalSavingsBalanceRupees),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Timestamp ("as of ...")
            if (latestBalanceAsOf != null && latestBalanceAsOf > 0L) {
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "As of ${asOfFormatter.format(Date(latestBalanceAsOf))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Available Credit Banner (strictly separate from savings balance)
            if (creditCards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = NeonCyan
                            )
                            Text(
                                text = "Available Credit",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = if (isBalanceHidden) "••••••" else currencyFormatter.format(totalAvailableCreditRupees),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonCyan
                        )
                    }
                }
            }

            // Expandable Per-Account Breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    if (bankAccounts.isEmpty() && creditCards.isEmpty()) {
                        Text(
                            text = "No accounts configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Bank Accounts
                        if (bankAccounts.isNotEmpty()) {
                            Text(
                                text = "Bank Accounts",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            bankAccounts.forEach { acc ->
                                AccountBalanceRow(
                                    account = acc,
                                    isBalanceHidden = isBalanceHidden,
                                    currencyFormatter = currencyFormatter,
                                    asOfFormatter = asOfFormatter,
                                    onUpdateClick = { onUpdateAccountBalance(acc) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        // Credit Cards
                        if (creditCards.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Credit Cards",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            creditCards.forEach { card ->
                                AccountBalanceRow(
                                    account = card,
                                    isBalanceHidden = isBalanceHidden,
                                    currencyFormatter = currencyFormatter,
                                    asOfFormatter = asOfFormatter,
                                    onUpdateClick = { onUpdateAccountBalance(card) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Manage Accounts Link
                    OutlinedButton(
                        onClick = onManageAccounts,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manage All Accounts")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceRow(
    account: AccountEntity,
    isBalanceHidden: Boolean,
    currencyFormatter: NumberFormat,
    asOfFormatter: SimpleDateFormat,
    onUpdateClick: () -> Unit
) {
    val isCreditCard = account.type == AccountType.CREDIT_CARD
    val isKnown = isAccountBalanceKnown(account)
    val amountRupees = if (isCreditCard) account.creditLimitPaise / 100.0 else account.balancePaise / 100.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (account.last4.isNotBlank()) "${account.bankName} ••${account.last4}" else account.bankName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (account.balanceSource == BalanceSource.MANUAL && isKnown) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "entered manually",
                                color = NeonCyan,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (account.balanceSource == BalanceSource.AUTO && account.balanceAsOf > 0L) {
                    Text(
                        text = "as of ${asOfFormatter.format(Date(account.balanceAsOf))}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (!isKnown) {
                Text(
                    text = "Balance not detected yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (isBalanceHidden) "••••••" else currencyFormatter.format(amountRupees),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isCreditCard) NeonCyan else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Helper to determine if an account balance is considered "known".
 * An account balance is known if:
 * 1. It was set manually (balanceSource == MANUAL && (balancePaise != 0L || editedFields contains balancePaise))
 * 2. It was parsed automatically (balanceSource == AUTO && balanceAsOf > 0L)
 */
fun isAccountBalanceKnown(account: AccountEntity): Boolean {
    return when (account.balanceSource) {
        BalanceSource.MANUAL -> account.balancePaise != 0L || account.editedFields.contains("balancePaise")
        BalanceSource.AUTO -> account.balanceAsOf > 0L && account.balancePaise >= 0L
    }
}
