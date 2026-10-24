package com.campusfold.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.model.DerivedAccountBalance
import com.campusfold.tracker.ui.theme.*
import com.campusfold.tracker.util.CurrencyUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stylized Bank Avatar Logo representing top banks or falling back to initials.
 */
@Composable
fun BankAvatarLogo(
    bankName: String,
    colorHex: String,
    isCreditCard: Boolean,
    modifier: Modifier = Modifier
) {
    val cleanName = bankName.uppercase()
    val (bgColor, textColor, label) = when {
        cleanName.contains("PNB") || cleanName.contains("PUNJAB") ->
            Triple(Color(0xFFA6192E), Color(0xFFFFD200), "pnb")
        cleanName.contains("FEDERAL") || cleanName.contains("JUPITER") ->
            Triple(Color(0xFF003366), Color(0xFFFFB81C), "FB")
        cleanName.contains("AXIS") ->
            Triple(Color(0xFF861F41), Color.White, "AXIS")
        cleanName.contains("HDFC") ->
            Triple(Color(0xFF004C8F), Color.White, "HDFC")
        cleanName.contains("SBI") || cleanName.contains("STATE BANK") ->
            Triple(Color(0xFF280071), Color(0xFF29B6F6), "SBI")
        cleanName.contains("ICICI") ->
            Triple(Color(0xFF992B15), Color.White, "ICICI")
        cleanName.contains("KOTAK") ->
            Triple(Color(0xFFED1C24), Color.White, "KOTAK")
        else -> {
            val fallbackColor = try {
                Color(android.graphics.Color.parseColor(colorHex))
            } catch (_: Exception) {
                if (isCreditCard) NeonPurple else NeonEmerald
            }
            Triple(fallbackColor.copy(alpha = 0.2f), fallbackColor, bankName.take(2).uppercase().ifBlank { "AC" })
        }
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 2) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Dashboard Account Card showing balance/owed, limit, and "as of" timestamp.
 */
@Composable
fun DashboardAccountCard(
    derived: DerivedAccountBalance,
    isAmountHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = derived.account
    val isCreditCard = account.type == AccountType.CREDIT_CARD

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val asOfFormatter = remember {
        SimpleDateFormat("d MMM", Locale.getDefault())
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Avatar, Name & Type Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BankAvatarLogo(
                    bankName = account.bankName.ifBlank { account.displayName },
                    colorHex = account.color,
                    isCreditCard = isCreditCard
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = account.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.last4.isNotBlank()) {
                            Text(
                                text = "••${account.last4}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val asOfText = if (account.openingAsOf > 0L) {
                        "as of ${asOfFormatter.format(Date(account.openingAsOf))}"
                    } else "tracked"

                    Text(
                        text = if (isCreditCard) "Credit Card • $asOfText" else "Bank Account • $asOfText",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Discrepancy indicator pill
                if (derived.hasDiscrepancy) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonAmber.copy(alpha = 0.15f))
                            .border(1.dp, NeonAmber.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Diff",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderCharcoal.copy(alpha = 0.4f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Figures
            if (isCreditCard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Owed", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (isAmountHidden) "₹ ••••" else currencyFormatter.format(derived.derivedOwed),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (derived.derivedOwedPaise > 0) NeonAmber else TextPrimary
                        )
                    }

                    if (account.creditLimitPaise > 0L) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Available Credit", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (isAmountHidden) "₹ ••••" else currencyFormatter.format(derived.availableCredit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Balance", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = if (isAmountHidden) "₹ ••••••" else currencyFormatter.format(derived.derivedBalance),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * My Accounts Tile for Home (Part 3 #6):
 * - Header "My Accounts" with "Manage >" that opens the Accounts screen.
 * - Shows up to 4 account cards in the DashboardAccountCard / BankAvatarLogo style.
 * - "Show all" row at bottom.
 */
@Composable
fun HomeAccountsTile(
    derivedBalances: List<DerivedAccountBalance>,
    isAmountHidden: Boolean,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccounts = remember(derivedBalances) {
        derivedBalances.filter { !it.account.isArchived }
    }
    val display4 = remember(activeAccounts) {
        activeAccounts.take(4)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Accounts",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onManageAccounts() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Manage",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Manage Accounts",
                    tint = NeonEmerald,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (display4.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No accounts added yet",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            display4.forEach { derived ->
                DashboardAccountCard(
                    derived = derived,
                    isAmountHidden = isAmountHidden,
                    onClick = onManageAccounts
                )
            }

            if (activeAccounts.size > 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onManageAccounts() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show all ${activeAccounts.size} accounts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonEmerald
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
