package com.campusfold.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.BalanceSource
import com.campusfold.tracker.data.model.NetBalanceOverview
import com.campusfold.tracker.ui.theme.*
import com.campusfold.tracker.util.CurrencyUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun isAccountBalanceKnown(account: AccountEntity): Boolean {
    return account.balanceSource == BalanceSource.MANUAL || account.anchorSet || account.balanceAsOf > 0L
}

/**
 * Net Balance Card adhering strictly to Part 3 & Part 6:
 * - Headline: "Net available balance" = savings balances - credit card owed.
 * - Eye icon to hide/reveal amounts (persisted).
 * - Breakdown: "Savings" and "Credit card owed".
 * - Separate small line: "Owed to friends" (shown only when > 0, NOT subtracted from the net).
 * - "Tracked since <date>" note at bottom.
 * - "Set opening balances" button when any account has no opening balance set.
 * - Never shows available credit as the headline.
 */
@Composable
fun BalanceCard(
    netOverview: NetBalanceOverview,
    isBalanceHidden: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onManageAccounts: () -> Unit,
    onSetOpeningBalances: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val trackedSinceFormatter = remember {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Label & Eye Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NeonEmerald.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "NET AVAILABLE BALANCE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                }

                IconButton(
                    onClick = onToggleBalanceVisibility,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isBalanceHidden) "Show balances" else "Hide balances",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Headline Net Amount
            val netRupees = netOverview.netAvailableBalance
            val formattedNet = if (isBalanceHidden) {
                "₹ ••••••••"
            } else {
                val sign = if (netOverview.netAvailableBalancePaise < 0) "-" else ""
                val absFormatted = currencyFormatter.format(kotlin.math.abs(netRupees))
                "$sign$absFormatted"
            }

            Text(
                text = formattedNet,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (netOverview.netAvailableBalancePaise < 0) NeonRose else TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = BorderCharcoal.copy(alpha = 0.6f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Breakdown: Savings vs Credit Card Owed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Savings Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Savings",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isBalanceHidden) "₹ •••••" else currencyFormatter.format(netOverview.totalSavingsBalance),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                // Credit Card Owed Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Credit card owed",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isBalanceHidden) "₹ •••••" else currencyFormatter.format(netOverview.totalCardOwed),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (netOverview.totalCardOwedPaise > 0) NeonAmber else TextSecondary
                    )
                }
            }

            // Owed to Friends Row (Only if > 0, NOT subtracted from net)
            if (netOverview.totalOwedToFriendsPaise > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonAmber.copy(alpha = 0.08f))
                        .border(1.dp, NeonAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Owed to friends: ",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = if (isBalanceHidden) "₹ •••" else currencyFormatter.format(netOverview.totalOwedToFriends),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonAmber
                        )
                    }
                    Text(
                        text = "Not deducted from net",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Footer Row: Tracked Since & Optional "Set opening balances" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val trackedDate = if (netOverview.earliestTrackedTimestamp > 0L) {
                    trackedSinceFormatter.format(Date(netOverview.earliestTrackedTimestamp))
                } else {
                    "recently"
                }

                Text(
                    text = "Tracked since $trackedDate",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                if (netOverview.unanchoredAccountsCount > 0) {
                    OutlinedButton(
                        onClick = onSetOpeningBalances,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Set opening balances",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonEmerald
                        )
                    }
                }
            }
        }
    }
}
