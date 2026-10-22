package com.campusfold.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.sheets.AiCoachSheet
import com.campusfold.tracker.ui.sheets.SyncFromDateDialog
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class DateViewMode { MONTH, WEEK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TransactionViewModel,
    onNavigateToTransactions: () -> Unit,
    onOpenTransaction: (TransactionEntity) -> Unit,
    onManageAccounts: () -> Unit,
    onOpenAddTransaction: () -> Unit
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current

    // Period selection state
    var dateViewMode by remember { mutableStateOf(DateViewMode.MONTH) }
    var monthOffset by remember { mutableIntStateOf(0) } // 0 = this month, -1 = last month...
    var weekOffset by remember { mutableIntStateOf(0) }  // 0 = this week, -1 = last week...
    var showPeriodPickerSheet by remember { mutableStateOf(false) }
    var showAiCoachSheet by remember { mutableStateOf(false) }

    // Selected Heatmap day tooltip
    var selectedHeatmapDate by remember { mutableStateOf<HeatmapDayInfo?>(null) }

    // Compute period bounds
    val (periodStart, periodEnd, periodLabel) = remember(dateViewMode, monthOffset, weekOffset) {
        if (dateViewMode == DateViewMode.MONTH) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, monthOffset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            Triple(start, end, fmt.format(Date(start)))
        } else {
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                add(Calendar.WEEK_OF_YEAR, weekOffset)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_WEEK, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
            Triple(start, end, "${fmt.format(Date(start))} - ${fmt.format(Date(end))}")
        }
    }

    // Filtered spends for selected period
    val periodTransactions = remember(allTransactions, periodStart, periodEnd) {
        allTransactions.filter { it.timestamp in periodStart..periodEnd }
    }
    val periodExpenses = remember(periodTransactions) {
        periodTransactions.filter { it.type == TransactionType.EXPENSE }
    }
    val periodIncome = remember(periodTransactions) {
        periodTransactions.filter { it.type == TransactionType.INCOME }
    }
    val totalPeriodSpend = remember(periodExpenses) {
        periodExpenses.sumOf { it.myActualShare }
    }
    val totalGrossExpense = remember(periodExpenses) {
        periodExpenses.sumOf { it.amount }
    }
    val totalPeriodIncome = remember(periodIncome) {
        periodIncome.sumOf { it.amount }
    }
    val periodReimbursementsOwed = remember(totalGrossExpense, totalPeriodSpend) {
        (totalGrossExpense - totalPeriodSpend).coerceAtLeast(0.0)
    }

    // Recent 5 transactions
    val recentTransactions = remember(allTransactions) {
        allTransactions.take(5)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Dashboard Top Header Bar
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
                        color = TextMuted
                    )
                    Text(
                        text = "Dashboard",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // 1. PennyWise AI Coach Pill Button
                Button(
                    onClick = { showAiCoachSheet = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple.copy(alpha = 0.18f),
                        contentColor = NeonPurple
                    ),
                    shape = RoundedCornerShape(17.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Coach", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Modern Period Spend Card ("My Spends")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E222B), Color(0xFF141619))
                            )
                        )
                        .padding(18.dp)
                ) {
                    // Top Row: Month / Week pill switcher + Filter button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Month / Week Segmented Switcher
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(InputBg)
                                .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (dateViewMode == DateViewMode.MONTH) MatteCharcoal else Color.Transparent)
                                    .clickable {
                                        dateViewMode = DateViewMode.MONTH
                                        monthOffset = 0
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Month",
                                    fontSize = 11.sp,
                                    fontWeight = if (dateViewMode == DateViewMode.MONTH) FontWeight.Bold else FontWeight.Medium,
                                    color = if (dateViewMode == DateViewMode.MONTH) NeonEmerald else TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (dateViewMode == DateViewMode.WEEK) MatteCharcoal else Color.Transparent)
                                    .clickable {
                                        dateViewMode = DateViewMode.WEEK
                                        weekOffset = 0
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Week",
                                    fontSize = 11.sp,
                                    fontWeight = if (dateViewMode == DateViewMode.WEEK) FontWeight.Bold else FontWeight.Medium,
                                    color = if (dateViewMode == DateViewMode.WEEK) NeonCyan else TextSecondary
                                )
                            }
                        }

                        // Filter / Calendar Button
                        Button(
                            onClick = { showPeriodPickerSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InputBg,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(13.dp), tint = NeonCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Filter", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Period Pager (<  October 2026  >)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                            modifier = Modifier.clickable { showPeriodPickerSheet = true }
                        ) {
                            Text(
                                text = periodLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                if (dateViewMode == DateViewMode.MONTH) {
                                    if (monthOffset < 0) monthOffset++
                                } else {
                                    if (weekOffset < 0) weekOffset++
                                }
                            },
                            enabled = (if (dateViewMode == DateViewMode.MONTH) monthOffset < 0 else weekOffset < 0),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next",
                                tint = if (if (dateViewMode == DateViewMode.MONTH) monthOffset < 0 else weekOffset < 0) TextSecondary else BorderCharcoal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Spend Title & Big Monospace Currency
                    Text(
                        text = if (dateViewMode == DateViewMode.MONTH) "MY NET SPEND THIS MONTH" else "MY NET SPEND THIS WEEK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MonospaceCurrencyText(
                        amount = totalPeriodSpend,
                        fontSize = 32.sp,
                        color = NeonEmerald
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = BorderCharcoal, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Breakdown row: Gross Spend | Splits Owed | Income
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Gross Spend", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            Text("₹${totalGrossExpense.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Split Owed", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            Text("₹${periodReimbursementsOwed.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Income", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            Text("₹${totalPeriodIncome.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Bank Accounts Carousel (Like Screenshot 225051 & 225103)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bank Accounts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Manage >",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8AB4F8),
                    modifier = Modifier
                        .clickable { onManageAccounts() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (accounts.isEmpty()) {
                FoldCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManageAccounts() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("No bank accounts linked", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Tap to configure accounts & balances", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        DashboardAccountCard(
                            account = acc,
                            onClick = onManageAccounts
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Activity (GitHub Contribution Heatmap - Screenshot 225131)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            DashboardActivityHeatmap(
                transactions = allTransactions,
                selectedDay = selectedHeatmapDate,
                onSelectDay = { selectedHeatmapDate = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Recent Transactions (Last 5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "See All >",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald,
                    modifier = Modifier
                        .clickable { onNavigateToTransactions() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (recentTransactions.isEmpty()) {
                FoldCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No transactions logged yet.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                    recentTransactions.forEach { tx ->
                        val isCredit = tx.type == TransactionType.INCOME
                        val isCcBill = tx.type == TransactionType.CC_BILL_TRANSFER

                        FoldCard(
                            onClick = { onOpenTransaction(tx) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Direction Icon
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.friendlyVendorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!tx.description.isNullOrBlank()) {
                                        Text(
                                            text = "“${tx.description}”",
                                            fontSize = 10.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = NeonCyan.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = dateFormat.format(Date(tx.timestamp)),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                }

                                MonospaceCurrencyText(
                                    amount = tx.amount,
                                    prefix = if (isCredit) "+₹" else if (isCcBill) "⇌ ₹" else "-₹",
                                    fontSize = 14.sp,
                                    color = when {
                                        isCcBill -> PillTextCcBill
                                        isCredit -> PillTextIncome
                                        else -> TextPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Floating Action Button to Log Transaction
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

    // Period Picker Bottom Sheet
    if (showPeriodPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPeriodPickerSheet = false },
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
        ) {
            PeriodPickerContent(
                currentMode = dateViewMode,
                monthOffset = monthOffset,
                weekOffset = weekOffset,
                onSelectPeriod = { mode, mOff, wOff ->
                    dateViewMode = mode
                    monthOffset = mOff
                    weekOffset = wOff
                    showPeriodPickerSheet = false
                }
            )
        }
    }

    // AI Financial Coach Bottom Sheet
    if (showAiCoachSheet) {
        AiCoachSheet(
            viewModel = viewModel,
            onDismiss = { showAiCoachSheet = false }
        )
    }
}

/**
 * Account Card matching Screenshot 225051 & 225103
 */
@Composable
fun DashboardAccountCard(
    account: AccountEntity,
    onClick: () -> Unit
) {
    var isBalanceVisible by remember { mutableStateOf(false) }
    val isCreditCard = account.accountType == "CREDIT_CARD"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A1E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
        modifier = Modifier
            .width(260.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Bank Icon, Bank Name & Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Bank Avatar
                BankAvatarLogo(
                    bankName = account.bankName.ifBlank { account.name },
                    colorHex = account.colorHex,
                    isCreditCard = isCreditCard
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = account.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Pill Badge (Savings vs Credit Card)
                    StatusPill(
                        text = if (isCreditCard) "Credit Card" else "Savings",
                        bgColor = if (isCreditCard) Color(0xFF281E38) else Color(0xFF1E2838),
                        textColor = if (isCreditCard) Color(0xFFC084FC) else Color(0xFF8AB4F8),
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Row: Balance Label & Masked / Revealed Amount with Eye Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCreditCard) "Avl Limit" else "Balance",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isBalanceVisible) {
                        Text(
                            text = "₹${account.creditLimitOrBalance.toInt()}",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    } else {
                        Text(
                            text = "••••••",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { isBalanceVisible = !isBalanceVisible },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = if (isBalanceVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Balance",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stylized Bank Avatar Logo representing top banks or falling back to initials
 */
@Composable
fun BankAvatarLogo(
    bankName: String,
    colorHex: String,
    isCreditCard: Boolean
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
        else -> {
            val fallbackColor = try {
                Color(android.graphics.Color.parseColor(colorHex))
            } catch (e: Exception) {
                if (isCreditCard) NeonPurple else NeonCyan
            }
            Triple(fallbackColor.copy(alpha = 0.2f), fallbackColor, bankName.take(2).uppercase())
        }
    }

    Box(
        modifier = Modifier
            .size(42.dp)
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

data class HeatmapDayInfo(
    val timestamp: Long,
    val dayLabel: String,
    val spendAmount: Double,
    val count: Int,
    val level: Int
)

/**
 * GitHub Contribution-style Activity Heatmap (matching Screenshot 225131)
 */
@Composable
fun DashboardActivityHeatmap(
    transactions: List<TransactionEntity>,
    selectedDay: HeatmapDayInfo?,
    onSelectDay: (HeatmapDayInfo?) -> Unit
) {
    // 1. O(1) Pre-aggregate expenses by calendar day start
    val (dailySpendMap, dailyCountMap) = remember(transactions) {
        val spendMap = mutableMapOf<Long, Double>()
        val countMap = mutableMapOf<Long, Int>()
        val cal = Calendar.getInstance()
        transactions.forEach { tx ->
            if (tx.type == TransactionType.EXPENSE) {
                cal.timeInMillis = tx.timestamp
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayKey = cal.timeInMillis
                spendMap[dayKey] = (spendMap[dayKey] ?: 0.0) + tx.myActualShare
                countMap[dayKey] = (countMap[dayKey] ?: 0) + 1
            }
        }
        Pair(spendMap, countMap)
    }

    // 2. Generate 14 weeks (98 days) up to today
    val weeks = remember(dailySpendMap, dailyCountMap) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endCal = cal.clone() as Calendar

        val weekList = mutableListOf<List<HeatmapDayInfo>>()
        val dayFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

        val startCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, -13)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val runner = startCal.clone() as Calendar
        var currentWeek = mutableListOf<HeatmapDayInfo>()

        while (runner.timeInMillis <= endCal.timeInMillis || currentWeek.isNotEmpty()) {
            val dStart = runner.timeInMillis
            val daySpend = dailySpendMap[dStart] ?: 0.0
            val dayCount = dailyCountMap[dStart] ?: 0

            val level = when {
                daySpend <= 0.0 -> 0
                daySpend < 250.0 -> 1
                daySpend < 750.0 -> 2
                daySpend < 2000.0 -> 3
                else -> 4
            }

            currentWeek.add(
                HeatmapDayInfo(
                    timestamp = dStart,
                    dayLabel = dayFmt.format(Date(dStart)),
                    spendAmount = daySpend,
                    count = dayCount,
                    level = level
                )
            )

            if (currentWeek.size == 7) {
                weekList.add(currentWeek)
                currentWeek = mutableListOf()
            }

            runner.add(Calendar.DAY_OF_MONTH, 1)
            runner.set(Calendar.HOUR_OF_DAY, 0)
            runner.set(Calendar.MINUTE, 0)
            runner.set(Calendar.SECOND, 0)
            if (weekList.size >= 14 && currentWeek.isEmpty()) break
        }
        weekList
    }

    // Month transitions to split and center month labels across their respective columns
    val monthTransitions = remember(weeks) {
        val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
        val list = mutableListOf<Triple<Int, String, Int>>() // colIdx, monthName, columnCount
        var currentMonth = -1
        var currentStartCol = 0
        var currentMonthName = ""

        weeks.forEachIndexed { colIdx, weekDays ->
            val firstDay = weekDays.firstOrNull() ?: return@forEachIndexed
            val cal = Calendar.getInstance().apply { timeInMillis = firstDay.timestamp }
            val month = cal.get(Calendar.MONTH)
            if (month != currentMonth) {
                if (currentMonth != -1) {
                    list.add(Triple(currentStartCol, currentMonthName, colIdx - currentStartCol))
                }
                currentMonth = month
                currentStartCol = colIdx
                currentMonthName = monthFmt.format(Date(firstDay.timestamp))
            }
        }
        if (currentMonth != -1) {
            list.add(Triple(currentStartCol, currentMonthName, weeks.size - currentStartCol))
        }
        list
    }

    val cellColors = remember {
        listOf(
            Color(0xFF212328),
            Color(0xFF2C3A4E),
            Color(0xFF435C80),
            Color(0xFF6287BF),
            Color(0xFF8CB4F8)
        )
    }

    val density = LocalDensity.current
    val cellSizeDp = 15.dp
    val spacingDp = 4.dp
    val cellSizePx = with(density) { cellSizeDp.toPx() }
    val spacingPx = with(density) { spacingDp.toPx() }
    val cornerRadiusPx = with(density) { 4.dp.toPx() }
    val canvasWidthDp = (14 * 15 + 13 * 4).dp
    val canvasHeightDp = (7 * 15 + 6 * 4).dp
    val canvasHeightPx = with(density) { canvasHeightDp.toPx() }

    val monthBoundaryColIndices = remember(monthTransitions) {
        monthTransitions.map { it.first }.filter { it > 0 }.toSet()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A1E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                // Day Labels on Left (M, W, F, S)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                ) {
                    val labels = listOf("M", "", "W", "", "F", "", "S")
                    labels.forEach { l ->
                        Box(
                            modifier = Modifier.size(15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = l,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (l == "S") Color(0xFF8CB4F8) else TextMuted
                            )
                        }
                    }
                }

                // High-performance Hardware-Accelerated Heatmap Canvas (0 composable overhead)
                Column {
                    Canvas(
                        modifier = Modifier
                            .size(width = canvasWidthDp, height = canvasHeightDp)
                            .pointerInput(weeks, selectedDay) {
                                detectTapGestures { offset ->
                                    val col = (offset.x / (cellSizePx + spacingPx)).toInt().coerceIn(0, weeks.lastIndex)
                                    val row = (offset.y / (cellSizePx + spacingPx)).toInt().coerceIn(0, 6)
                                    val clickedDay = weeks.getOrNull(col)?.getOrNull(row)
                                    if (clickedDay != null) {
                                        if (selectedDay?.timestamp == clickedDay.timestamp) {
                                            onSelectDay(null)
                                        } else {
                                            onSelectDay(clickedDay)
                                        }
                                    }
                                }
                            }
                    ) {
                        // Draw month separation lines so user can clearly see which month is which
                        monthBoundaryColIndices.forEach { boundaryCol ->
                            val dividerX = boundaryCol * (cellSizePx + spacingPx) - (spacingPx / 2f)
                            drawLine(
                                color = Color(0xFF383D48),
                                start = Offset(dividerX, 0f),
                                end = Offset(dividerX, canvasHeightPx),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        weeks.forEachIndexed { colIdx, weekDays ->
                            val x = colIdx * (cellSizePx + spacingPx)
                            weekDays.forEachIndexed { rowIdx, dayInfo ->
                                val y = rowIdx * (cellSizePx + spacingPx)
                                val isSelected = selectedDay?.timestamp == dayInfo.timestamp
                                val color = cellColors.getOrElse(dayInfo.level) { cellColors[0] }

                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(x, y),
                                    size = Size(cellSizePx, cellSizePx),
                                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                )

                                if (isSelected) {
                                    drawRoundRect(
                                        color = NeonEmerald,
                                        topLeft = Offset(x - 1f, y - 1f),
                                        size = Size(cellSizePx + 2f, cellSizePx + 2f),
                                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Month Labels split across their exact columns (never squished)
                    Box(
                        modifier = Modifier
                            .width(canvasWidthDp)
                            .height(20.dp)
                    ) {
                        monthTransitions.forEach { (startCol, monthName, colSpan) ->
                            val startXDp = (startCol * (15 + 4)).dp
                            val spanWidthDp = (colSpan * 15 + (colSpan - 1).coerceAtLeast(0) * 4).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = startXDp)
                                    .width(spanWidthDp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = monthName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8CB4F8)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tooltip Banner for clicked day or overall 14-week summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputBg)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (selectedDay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDay.dayLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (selectedDay.spendAmount > 0) "₹${selectedDay.spendAmount.toInt()} (${selectedDay.count} txns)" else "₹0 (No spend)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedDay.spendAmount > 0) Color(0xFF8CB4F8) else TextMuted
                        )
                    }
                } else {
                    val activeDays = weeks.flatten().count { it.level > 0 }
                    val totalSpend14w = weeks.flatten().sumOf { it.spendAmount }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last 14 Weeks Activity",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                        Text(
                            text = "$activeDays active days • ₹${totalSpend14w.toInt()} spent",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8CB4F8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Period Selection Modal Sheet
 */
@Composable
fun PeriodPickerContent(
    currentMode: DateViewMode,
    monthOffset: Int,
    weekOffset: Int,
    onSelectPeriod: (DateViewMode, Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = "Select Time Period",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text("QUICK PERIODS", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentMode == DateViewMode.MONTH && monthOffset == 0,
                onClick = { onSelectPeriod(DateViewMode.MONTH, 0, 0) },
                label = { Text("This Month", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentMode == DateViewMode.MONTH && monthOffset == -1,
                onClick = { onSelectPeriod(DateViewMode.MONTH, -1, 0) },
                label = { Text("Last Month", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentMode == DateViewMode.WEEK && weekOffset == 0,
                onClick = { onSelectPeriod(DateViewMode.WEEK, 0, 0) },
                label = { Text("This Week", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentMode == DateViewMode.WEEK && weekOffset == -1,
                onClick = { onSelectPeriod(DateViewMode.WEEK, 0, -1) },
                label = { Text("Last Week", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text("PAST MONTHS", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Last 6 months list
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 downTo -5) {
                val cal = Calendar.getInstance().apply { add(Calendar.MONTH, i) }
                val label = monthFmt.format(cal.time)
                val isSelected = currentMode == DateViewMode.MONTH && monthOffset == i

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MatteCharcoal else InputBg)
                        .border(1.dp, if (isSelected) NeonEmerald else BorderCharcoal, RoundedCornerShape(10.dp))
                        .clickable { onSelectPeriod(DateViewMode.MONTH, i, 0) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NeonEmerald else TextPrimary
                    )
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
