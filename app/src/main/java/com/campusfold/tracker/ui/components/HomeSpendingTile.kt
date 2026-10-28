package com.campusfold.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import com.campusfold.tracker.data.model.NetBalanceOverview
import com.campusfold.tracker.ui.theme.*
import com.campusfold.tracker.util.CurrencyUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Total Spends Tile (Part 3 #4):
 * - Month / Week toggle.
 * - Period picker (PeriodPickerBottomSheet).
 * - True Spend headline.
 * - Half-circle budget gauge inside this tile (when monthly budget is set and current month is selected).
 * - Breakdown row: Gross Spend | Split Owed (friends owe me) | You Owe (I owe friends) | Total Income.
 */
@Composable
fun HomeSpendingTile(
    transactions: List<TransactionEntity>,
    netOverview: NetBalanceOverview,
    monthlyBudget: Double,
    modifier: Modifier = Modifier
) {
    var dateViewMode by remember { mutableStateOf(DateViewMode.MONTH) }
    var monthOffset by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var showPeriodPicker by remember { mutableStateOf(false) }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    // Determine Period Time Bounds
    val (periodStart, periodEnd, periodLabel) = remember(dateViewMode, monthOffset, weekOffset) {
        val cal = Calendar.getInstance()
        if (dateViewMode == DateViewMode.MONTH) {
            cal.add(Calendar.MONTH, monthOffset)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, maxDay)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val label = if (monthOffset == 0) "This Month (${fmt.format(start)})" else fmt.format(start)
            Triple(start, end, label)
        } else {
            cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.add(Calendar.DAY_OF_WEEK, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            val label = if (weekOffset == 0) "This Week" else "${fmt.format(start)} - ${fmt.format(end)}"
            Triple(start, end, label)
        }
    }

    // Filter transactions in this period
    val periodTransactions = remember(transactions, periodStart, periodEnd) {
        transactions.filter { it.timestamp in periodStart..periodEnd }
    }

    val periodExpenses = remember(periodTransactions) {
        periodTransactions.filter { it.type == TransactionType.EXPENSE }
    }
    val trueSpendPaise = remember(periodExpenses) {
        periodExpenses.sumOf { it.myActualSharePaise }
    }
    val grossSpendPaise = remember(periodExpenses) {
        periodExpenses.filter { it.paidByPersonId == null }.sumOf { it.amountPaise }
    }
    val totalPeriodIncomePaise = remember(periodTransactions) {
        periodTransactions.filter { it.type == TransactionType.INCOME && it.linkedExpenseId == null }.sumOf { it.amountPaise }
    }

    // Reimbursement owed (unsettled friend debt owed to me)
    val periodSplitOwedPaise = remember(netOverview.totalFriendsOweMePaise) {
        netOverview.totalFriendsOweMePaise
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
            // Mode Toggle Row: Month | Week
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Pill for Month / Week
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(InputBg)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (dateViewMode == DateViewMode.MONTH) NeonEmerald else Color.Transparent)
                            .clickable {
                                dateViewMode = DateViewMode.MONTH
                                monthOffset = 0
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Month",
                            fontSize = 12.sp,
                            fontWeight = if (dateViewMode == DateViewMode.MONTH) FontWeight.Bold else FontWeight.Normal,
                            color = if (dateViewMode == DateViewMode.MONTH) OledBlack else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (dateViewMode == DateViewMode.WEEK) NeonEmerald else Color.Transparent)
                            .clickable {
                                dateViewMode = DateViewMode.WEEK
                                weekOffset = 0
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Week",
                            fontSize = 12.sp,
                            fontWeight = if (dateViewMode == DateViewMode.WEEK) FontWeight.Bold else FontWeight.Normal,
                            color = if (dateViewMode == DateViewMode.WEEK) OledBlack else TextSecondary
                        )
                    }
                }

                // Period Navigation: < Label v >
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (dateViewMode == DateViewMode.MONTH) monthOffset-- else weekOffset--
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPeriodPicker = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (dateViewMode == DateViewMode.MONTH && monthOffset == 0) "This Month" else if (dateViewMode == DateViewMode.WEEK && weekOffset == 0) "This Week" else periodLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }

                    val canGoNext = if (dateViewMode == DateViewMode.MONTH) monthOffset < 0 else weekOffset < 0
                    IconButton(
                        onClick = {
                            if (canGoNext) {
                                if (dateViewMode == DateViewMode.MONTH) monthOffset++ else weekOffset++
                            }
                        },
                        enabled = canGoNext,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next",
                            tint = if (canGoNext) TextSecondary else TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Headline Spend Label & Big Amount
            Text(
                text = if (dateViewMode == DateViewMode.MONTH) "TRUE SPEND THIS MONTH" else "TRUE SPEND THIS WEEK",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currencyFormatter.format(trueSpendPaise / 100.0),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = NeonEmerald,
                letterSpacing = (-0.5).sp
            )

            // Budget Gauge (only when budget is set and viewing current month)
            if (monthlyBudget > 0.0 && dateViewMode == DateViewMode.MONTH && monthOffset == 0) {
                Spacer(modifier = Modifier.height(10.dp))
                SpendingGauge(
                    spent = trueSpendPaise / 100.0,
                    budgetLimit = monthlyBudget,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = BorderCharcoal.copy(alpha = 0.6f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 4-Column Breakdown Row: Gross Spend | Split Owed | You Owe | Total Income
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gross Spend
                Column {
                    Text("Gross Spend", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        currencyFormatter.format(grossSpendPaise / 100.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Split Owed (Friends owe me)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Split Owed", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        currencyFormatter.format(periodSplitOwedPaise / 100.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                }

                // You Owe (I owe friends)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You Owe", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        currencyFormatter.format(netOverview.totalOwedToFriends),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netOverview.totalOwedToFriendsPaise > 0) NeonAmber else TextSecondary
                    )
                }

                // Total Income
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Income", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        currencyFormatter.format(totalPeriodIncomePaise / 100.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }
        }
    }

    if (showPeriodPicker) {
        PeriodPickerBottomSheet(
            currentMode = dateViewMode,
            monthOffset = monthOffset,
            weekOffset = weekOffset,
            onSelectPeriod = { mode, mOff, wOff ->
                dateViewMode = mode
                monthOffset = mOff
                weekOffset = wOff
            },
            onDismiss = { showPeriodPicker = false }
        )
    }
}
