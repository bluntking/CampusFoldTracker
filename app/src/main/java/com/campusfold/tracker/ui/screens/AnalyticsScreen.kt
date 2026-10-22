package com.campusfold.tracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.model.BurnRateInsight
import com.campusfold.tracker.data.model.CategorySpending
import com.campusfold.tracker.data.model.HighFrequencyLeak
import com.campusfold.tracker.data.model.VendorSpendingItem
import com.campusfold.tracker.data.model.WeekOverWeekSpike
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

enum class ChartBreakdownMode {
    CATEGORY,
    CAMPUS_VENDOR
}

data class ChartSlice(
    val id: String,
    val name: String,
    val amount: Double,
    val percentage: Float,
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: TransactionViewModel,
    onNavigateToTransactions: ((filterType: String, filterValue: String) -> Unit)? = null
) {
    val monthCategorySpending by viewModel.monthCategorySpending.collectAsState()
    val monthVendorSpending by viewModel.monthVendorSpending.collectAsState()
    val currentMonthTotalTrueSpend by viewModel.currentMonthTotalTrueSpend.collectAsState()
    val overallMonthlyBudget by viewModel.overallMonthlyBudget.collectAsState()

    val burnRateWarnings by viewModel.burnRateWarnings.collectAsState()
    val highFrequencyLeaks by viewModel.highFrequencyLeaks.collectAsState()
    val weekOverWeekSpike by viewModel.weekOverWeekSpike.collectAsState()

    var breakdownMode by remember { mutableStateOf(ChartBreakdownMode.CATEGORY) }
    var selectedSliceId by remember { mutableStateOf<String?>(null) }

    // Dialogs for editing budgets
    var showOverallBudgetDialog by remember { mutableStateOf(false) }
    var categoryToEditBudget by remember { mutableStateOf<CategorySpending?>(null) }

    val currentMonthName = remember {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    }

    // Palette for vendors & dynamic colors
    val foldColors = listOf(
        Color(0xFF10B981), // Emerald
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF3B82F6), // Blue
        Color(0xFFF43F5E), // Crimson
        Color(0xFF84CC16), // Lime
        Color(0xFF14B8A6), // Teal
        Color(0xFFFB923C)  // Orange
    )

    // Compute slices for Donut Chart
    val chartSlices = remember(breakdownMode, monthCategorySpending, monthVendorSpending, currentMonthTotalTrueSpend) {
        val total = currentMonthTotalTrueSpend
        if (total <= 0.0) emptyList()
        else {
            var currentAngle = -90f
            when (breakdownMode) {
                ChartBreakdownMode.CATEGORY -> {
                    monthCategorySpending.filter { it.totalSpent > 0.0 }.mapIndexed { index, cat ->
                        val pct = ((cat.totalSpent / total) * 100.0).toFloat()
                        val sweep = (cat.totalSpent / total * 360.0).toFloat()
                        val sliceColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (e: Exception) {
                            foldColors[index % foldColors.size]
                        }
                        val slice = ChartSlice(
                            id = cat.categoryId.toString(),
                            name = cat.categoryName,
                            amount = cat.totalSpent,
                            percentage = pct,
                            color = sliceColor,
                            startAngle = currentAngle,
                            sweepAngle = sweep
                        )
                        currentAngle += sweep
                        slice
                    }
                }
                ChartBreakdownMode.CAMPUS_VENDOR -> {
                    monthVendorSpending.filter { it.totalSpent > 0.0 }.mapIndexed { index, v ->
                        val pct = ((v.totalSpent / total) * 100.0).toFloat()
                        val sweep = (v.totalSpent / total * 360.0).toFloat()
                        val slice = ChartSlice(
                            id = v.vendorName,
                            name = v.vendorName,
                            amount = v.totalSpent,
                            percentage = pct,
                            color = foldColors[index % foldColors.size],
                            startAngle = currentAngle,
                            sweepAngle = sweep
                        )
                        currentAngle += sweep
                        slice
                    }
                }
            }
        }
    }

    val selectedSlice = chartSlices.find { it.id == selectedSliceId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CAMPUSFOLD ANALYTICS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = "True Spend Matrix",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                    }

                    StatusPill(
                        text = currentMonthName.uppercase(),
                        bgColor = InputBg,
                        textColor = NeonCyan,
                        fontSize = 10.sp
                    )
                }
            }

            // Interactive Donut Chart Card
            item {
                FoldCard(
                    containerColor = MatteCharcoal,
                    borderColor = BorderCharcoal
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Toggle Breakdown Mode (Category vs Campus Vendor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(InputBg)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (breakdownMode == ChartBreakdownMode.CATEGORY) MatteCharcoal else Color.Transparent)
                                    .clickable {
                                        breakdownMode = ChartBreakdownMode.CATEGORY
                                        selectedSliceId = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "By Category",
                                    fontSize = 11.sp,
                                    fontWeight = if (breakdownMode == ChartBreakdownMode.CATEGORY) FontWeight.Bold else FontWeight.Normal,
                                    color = if (breakdownMode == ChartBreakdownMode.CATEGORY) NeonEmerald else TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (breakdownMode == ChartBreakdownMode.CAMPUS_VENDOR) MatteCharcoal else Color.Transparent)
                                    .clickable {
                                        breakdownMode = ChartBreakdownMode.CAMPUS_VENDOR
                                        selectedSliceId = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "By Campus Vendor",
                                    fontSize = 11.sp,
                                    fontWeight = if (breakdownMode == ChartBreakdownMode.CAMPUS_VENDOR) FontWeight.Bold else FontWeight.Normal,
                                    color = if (breakdownMode == ChartBreakdownMode.CAMPUS_VENDOR) NeonCyan else TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Interactive Canvas Donut Chart
                        if (chartSlices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .height(220.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PieChart,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Personal Expenses in $currentMonthName",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "True personal share is calculated from EXPENSE rows.",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(chartSlices) {
                                            detectTapGestures { offset ->
                                                val center = Offset(size.width / 2f, size.height / 2f)
                                                val dx = offset.x - center.x
                                                val dy = offset.y - center.y
                                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                if (angle < 0) angle += 360f

                                                // Adjust so -90 deg (top) is angle 0 to match drawArc
                                                var normalizedAngle = angle + 90f
                                                if (normalizedAngle >= 360f) normalizedAngle -= 360f

                                                val tappedSlice = chartSlices.find { slice ->
                                                    var start = slice.startAngle + 90f
                                                    if (start < 0) start += 360f
                                                    val end = start + slice.sweepAngle
                                                    if (end > 360f) {
                                                        normalizedAngle >= start || normalizedAngle <= (end - 360f)
                                                    } else {
                                                        normalizedAngle in start..end
                                                    }
                                                }

                                                selectedSliceId = if (tappedSlice?.id == selectedSliceId) null else tappedSlice?.id
                                            }
                                        }
                                ) {
                                    val strokeWidthBase = 32.dp.toPx()
                                    val arcSize = Size(size.width - strokeWidthBase - 8.dp.toPx(), size.height - strokeWidthBase - 8.dp.toPx())
                                    val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)

                                    chartSlices.forEach { slice ->
                                        val isSelected = slice.id == selectedSliceId
                                        val stroke = if (isSelected) strokeWidthBase + 10.dp.toPx() else strokeWidthBase

                                        drawArc(
                                            color = if (selectedSliceId == null || isSelected) slice.color else slice.color.copy(alpha = 0.35f),
                                            startAngle = slice.startAngle,
                                            sweepAngle = (slice.sweepAngle - 1.5f).coerceAtLeast(0.5f),
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                                        )
                                    }
                                }

                                // Donut Center Label (Selected Slice or Month Total True Spend)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    if (selectedSlice != null) {
                                        Text(
                                            text = selectedSlice.name.uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = selectedSlice.color,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        MonospaceCurrencyText(
                                            amount = selectedSlice.amount,
                                            fontSize = 18.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${selectedSlice.percentage.toInt()}% of personal spend",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted
                                        )
                                    } else {
                                        Text(
                                            text = "TRUE SPEND",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        MonospaceCurrencyText(
                                            amount = currentMonthTotalTrueSpend,
                                            fontSize = 20.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Tap slice for details",
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = BorderCharcoal, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Interactive Legend List
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chartSlices.forEach { slice ->
                                    val isSelected = slice.id == selectedSliceId
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) slice.color.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) slice.color else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    selectedSliceId = if (isSelected) null else slice.id
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(slice.color)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = slice.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = TextPrimary,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${slice.percentage.toInt()}%",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = TextMuted
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                MonospaceCurrencyText(
                                                    amount = slice.amount,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) slice.color else TextPrimary
                                                )
                                            }
                                        }

                                        // View Every Transaction button when selected
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = {
                                                    if (breakdownMode == ChartBreakdownMode.CATEGORY) {
                                                        onNavigateToTransactions?.invoke("CATEGORY", "${slice.id}:${slice.name}")
                                                    } else {
                                                        onNavigateToTransactions?.invoke("VENDOR", slice.name)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = slice.color,
                                                    contentColor = OledBlack
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ReceiptLong,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "View Every '${slice.name}' Transaction →",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 'Where to Control' Actionable Insights Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WHERE TO CONTROL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "Real-time Audits",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            }

            // Card a) Burn Rate Warning
            item {
                BurnRateWarningCard(burnRateWarnings = burnRateWarnings)
            }

            // Card b) High-Frequency Leaks
            item {
                HighFrequencyLeaksCard(leaks = highFrequencyLeaks)
            }

            // Card c) Week-over-Week Spike
            item {
                WeekOverWeekSpikeCard(spike = weekOverWeekSpike)
            }

            // Monthly Budgets Overview & Adjuster Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MONTHLY BUDGET PACING",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                    }

                    TextButton(onClick = { showOverallBudgetDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeonCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Overall", fontSize = 11.sp, color = NeonCyan)
                    }
                }
            }

            // Overall Monthly Personal Budget Card
            item {
                OverallBudgetProgressCard(
                    totalSpend = currentMonthTotalTrueSpend,
                    overallBudget = overallMonthlyBudget,
                    onEditClick = { showOverallBudgetDialog = true }
                )
            }

            // Category Budgets Progress List
            items(monthCategorySpending, key = { it.categoryId }) { cat ->
                CategoryBudgetProgressRow(
                    categorySpending = cat,
                    onEditBudget = { categoryToEditBudget = cat }
                )
            }
        }
    }

    // Dialog: Edit Overall Monthly Budget
    if (showOverallBudgetDialog) {
        var budgetInput by remember { mutableStateOf(overallMonthlyBudget.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showOverallBudgetDialog = false },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Set Overall Monthly Budget",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Applies to true personal expenses across all campus activities. High-priority alerts fire at 50%, 70%, 90%, and 100%.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("Monthly Target ₹", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = budgetInput.toDoubleOrNull() ?: overallMonthlyBudget
                        viewModel.setOverallMonthlyBudget(amount)
                        showOverallBudgetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
                ) {
                    Text("Save Budget", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverallBudgetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Edit Category Monthly Budget
    categoryToEditBudget?.let { cat ->
        var budgetInput by remember { mutableStateOf(cat.monthlyBudget.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { categoryToEditBudget = null },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Set Budget for ${cat.categoryName}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Current spend: ₹${cat.totalSpent.toInt()}. Alerts will trigger when crossing 50%, 70%, 90%, and 100%.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("Category Monthly Target ₹", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = budgetInput.toDoubleOrNull() ?: cat.monthlyBudget
                        viewModel.updateCategoryBudget(cat.categoryId, amount)
                        categoryToEditBudget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
                ) {
                    Text("Update", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEditBudget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Insight Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BurnRateWarningCard(burnRateWarnings: List<BurnRateInsight>) {
    FoldCard(
        containerColor = if (burnRateWarnings.isNotEmpty()) CardElevated else MatteCharcoal,
        borderColor = if (burnRateWarnings.isNotEmpty()) NeonCrimson.copy(alpha = 0.6f) else BorderCharcoal
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (burnRateWarnings.isNotEmpty()) NeonCrimson else NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Burn Rate Warning",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }

                StatusPill(
                    text = if (burnRateWarnings.isNotEmpty()) "${burnRateWarnings.size} CRITICAL" else "HEALTHY",
                    bgColor = if (burnRateWarnings.isNotEmpty()) PillBgExpense else PillBgIncome,
                    textColor = if (burnRateWarnings.isNotEmpty()) PillTextExpense else PillTextIncome,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (burnRateWarnings.isNotEmpty()) {
                val topWarning = burnRateWarnings.first()
                Text(
                    text = "${topWarning.categoryName} is pacing ${topWarning.pacingAheadPercent.toInt()}% ahead of monthly schedule!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonCrimson
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${topWarning.spentPercent.toInt()}% of budget spent (₹${topWarning.spent.toInt()}/₹${topWarning.budget.toInt()}) while only ${topWarning.daysElapsedPercent.toInt()}% of the month has elapsed.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                if (burnRateWarnings.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "+ ${burnRateWarnings.size - 1} other category pacing ahead: ${burnRateWarnings.drop(1).joinToString { it.categoryName }}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            } else {
                Text(
                    text = "All category spends are pacing within or below the month's elapsed days. No overburn detected.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun HighFrequencyLeaksCard(leaks: List<HighFrequencyLeak>) {
    FoldCard(
        containerColor = if (leaks.isNotEmpty()) CardElevated else MatteCharcoal,
        borderColor = if (leaks.isNotEmpty()) NeonAmber.copy(alpha = 0.6f) else BorderCharcoal
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = if (leaks.isNotEmpty()) NeonAmber else NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "High-Frequency Micro-Leaks",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }

                StatusPill(
                    text = if (leaks.isNotEmpty()) ">5 VISITS/WEEK" else "NO LEAKS",
                    bgColor = if (leaks.isNotEmpty()) PillBgCard else PillBgIncome,
                    textColor = if (leaks.isNotEmpty()) PillTextCard else PillTextIncome,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (leaks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    leaks.forEach { leak ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonAmber)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${leak.vendorName} (${leak.transactionCount} visits this week)",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                            MonospaceCurrencyText(
                                amount = leak.totalSpent,
                                fontSize = 12.sp,
                                color = NeonAmber
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No campus vendors have exceeded 5 visits in the last 7 days. Micro-spends remain well under control.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun WeekOverWeekSpikeCard(spike: WeekOverWeekSpike?) {
    FoldCard(
        containerColor = if (spike != null) CardElevated else MatteCharcoal,
        borderColor = if (spike != null) NeonCyan.copy(alpha = 0.6f) else BorderCharcoal
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = if (spike != null) NeonCyan else NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Week-over-Week Spike",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }

                StatusPill(
                    text = if (spike != null) "+${spike.percentIncrease.toInt()}% WOW" else "BALANCED",
                    bgColor = if (spike != null) InputBg else PillBgIncome,
                    textColor = if (spike != null) NeonCyan else PillTextIncome,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (spike != null) {
                Text(
                    text = "${spike.categoryName} spiked by +₹${spike.increaseAmount.toInt()} (+${spike.percentIncrease.toInt()}%) compared to previous 7 days.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current 7 days: ₹${spike.currentWeekSpend.toInt()}  •  Previous 7 days: ₹${spike.previousWeekSpend.toInt()}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            } else {
                Text(
                    text = "Weekly spending is steady or lower across all categories compared to the preceding 7 days.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Monthly Budget Progress Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OverallBudgetProgressCard(
    totalSpend: Double,
    overallBudget: Double,
    onEditClick: () -> Unit
) {
    val progress = if (overallBudget > 0.0) (totalSpend / overallBudget).toFloat().coerceIn(0f, 1f) else 0f
    val pct = if (overallBudget > 0.0) ((totalSpend / overallBudget) * 100.0).toInt() else 0

    val barColor = when {
        pct >= 100 -> NeonCrimson
        pct >= 90 -> NeonCrimson
        pct >= 70 -> NeonAmber
        pct >= 50 -> NeonCyan
        else -> NeonEmerald
    }

    FoldCard(
        containerColor = MatteCharcoal,
        borderColor = barColor.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL MONTHLY PERSONAL BUDGET",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        MonospaceCurrencyText(
                            amount = totalSpend,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = " / ₹${overallBudget.toInt()}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                StatusPill(
                    text = "$pct%",
                    bgColor = barColor.copy(alpha = 0.2f),
                    textColor = barColor,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = InputBg
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val remaining = (overallBudget - totalSpend).coerceAtLeast(0.0)
                Text(
                    text = "Remaining: ₹${remaining.toInt()}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Text(
                    text = "Alerts: 50%, 70%, 90%, 100%",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun CategoryBudgetProgressRow(
    categorySpending: CategorySpending,
    onEditBudget: () -> Unit
) {
    val budget = categorySpending.monthlyBudget
    val spent = categorySpending.totalSpent
    val pct = if (budget > 0.0) ((spent / budget) * 100.0).toInt() else 0
    val progress = if (budget > 0.0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f

    val barColor = when {
        pct >= 100 -> NeonCrimson
        pct >= 90 -> NeonCrimson
        pct >= 70 -> NeonAmber
        pct >= 50 -> NeonCyan
        else -> NeonEmerald
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MatteCharcoal)
            .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
            .clickable { onEditBudget() }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                try {
                                    Color(android.graphics.Color.parseColor(categorySpending.colorHex))
                                } catch (e: Exception) {
                                    NeonEmerald
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = categorySpending.categoryName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonospaceCurrencyText(
                        amount = spent,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = " / ₹${budget.toInt()}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(
                        text = "$pct%",
                        bgColor = barColor.copy(alpha = 0.2f),
                        textColor = barColor,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = barColor,
                trackColor = InputBg
            )
        }
    }
}
