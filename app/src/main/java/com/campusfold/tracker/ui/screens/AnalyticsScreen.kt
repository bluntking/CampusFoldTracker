package com.campusfold.tracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
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
import kotlin.math.roundToInt

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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val monthCategorySpending by viewModel.monthCategorySpending.collectAsStateWithLifecycle()
    val monthVendorSpending by viewModel.monthVendorSpending.collectAsStateWithLifecycle()
    val currentMonthTotalTrueSpend by viewModel.currentMonthTotalTrueSpend.collectAsStateWithLifecycle()
    val overallMonthlyBudget by viewModel.overallMonthlyBudget.collectAsStateWithLifecycle()

    val burnRateWarnings by viewModel.burnRateWarnings.collectAsStateWithLifecycle()
    val highFrequencyLeaks by viewModel.highFrequencyLeaks.collectAsStateWithLifecycle()
    val weekOverWeekSpike by viewModel.weekOverWeekSpike.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    // Period selection state (Month vs Week, offsets)
    var dateViewMode by remember { mutableStateOf(DateViewMode.MONTH) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var showPeriodPickerSheet by remember { mutableStateOf(false) }

    var breakdownMode by remember { mutableStateOf(ChartBreakdownMode.CATEGORY) }
    var selectedSliceId by remember { mutableStateOf<String?>(null) }

    // Dialogs for editing budgets
    var showOverallBudgetDialog by remember { mutableStateOf(false) }
    var categoryToEditBudget by remember { mutableStateOf<CategorySpending?>(null) }

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

    // Filtered expenses for selected period
    val periodExpenses = remember(allTransactions, periodStart, periodEnd) {
        allTransactions.filter { it.timestamp in periodStart..periodEnd && it.type == TransactionType.EXPENSE }
    }

    val selectedPeriodTotalTrueSpend = remember(periodExpenses) {
        periodExpenses.sumOf { it.myActualShare }
    }

    // Dynamic Category spending for the selected period
    val selectedPeriodCategorySpending = remember(periodExpenses, categories) {
        val catMap = periodExpenses.filter { it.categoryId != null && it.categoryId != 0L }
            .groupBy { it.categoryId!! }

        categories.map { cat ->
            val spent = catMap[cat.id]?.sumOf { it.myActualShare } ?: 0.0
            CategorySpending(
                categoryId = cat.id,
                categoryName = cat.name,
                colorHex = cat.colorHex,
                monthlyBudget = cat.monthlyBudget,
                totalSpent = spent
            )
        }.sortedByDescending { it.totalSpent }
    }

    // Dynamic Vendor spending for the selected period
    val selectedPeriodVendorSpending = remember(periodExpenses) {
        periodExpenses
            .groupBy { it.friendlyVendorName.ifBlank { it.rawMerchant }.ifBlank { "Unknown" } }
            .map { (vendor, txs) ->
                VendorSpendingItem(
                    vendorName = vendor,
                    totalSpent = txs.sumOf { it.myActualShare },
                    transactionCount = txs.size
                )
            }.sortedByDescending { it.totalSpent }
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
    val chartSlices = remember(breakdownMode, selectedPeriodCategorySpending, selectedPeriodVendorSpending, selectedPeriodTotalTrueSpend) {
        val total = selectedPeriodTotalTrueSpend
        if (total <= 0.0) emptyList()
        else {
            when (breakdownMode) {
                ChartBreakdownMode.CATEGORY -> {
                    val rawSlices = selectedPeriodCategorySpending
                        .filter { it.totalSpent > 0.0 }
                        .mapIndexed { index, cat ->
                            val sliceColor = try {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (e: Exception) {
                                foldColors[index % foldColors.size]
                            }
                            Triple(cat.categoryId.toString(), cat.categoryName, cat.totalSpent) to sliceColor
                        }.toMutableList()

                    val categorySum = rawSlices.sumOf { it.first.third }
                    val uncategorized = (total - categorySum).coerceAtLeast(0.0)
                    if (uncategorized > 0.5) {
                        rawSlices.add(
                            Triple("uncategorized", "Uncategorized", uncategorized) to Color(0xFF64748B)
                        )
                    }

                    val effectiveTotal = rawSlices.sumOf { it.first.third }
                    if (effectiveTotal <= 0.0) emptyList()
                    else {
                        var currentAngle = -90f
                        rawSlices.map { (info, color) ->
                            val (id, name, amount) = info
                            val pct = ((amount / effectiveTotal) * 100.0).toFloat()
                            val sweep = ((amount / effectiveTotal) * 360.0).toFloat()
                            val slice = ChartSlice(
                                id = id,
                                name = name,
                                amount = amount,
                                percentage = pct,
                                color = color,
                                startAngle = currentAngle,
                                sweepAngle = sweep
                            )
                            currentAngle += sweep
                            slice
                        }
                    }
                }
                ChartBreakdownMode.CAMPUS_VENDOR -> {
                    val rawSlices = selectedPeriodVendorSpending
                        .filter { it.totalSpent > 0.0 }
                        .mapIndexed { index, v ->
                            Triple(v.vendorName, v.vendorName, v.totalSpent) to foldColors[index % foldColors.size]
                        }.toMutableList()

                    val vendorSum = rawSlices.sumOf { it.first.third }
                    val other = (total - vendorSum).coerceAtLeast(0.0)
                    if (other > 0.5) {
                        rawSlices.add(
                            Triple("other_vendors", "Other / Direct", other) to Color(0xFF64748B)
                        )
                    }

                    val effectiveTotal = rawSlices.sumOf { it.first.third }
                    if (effectiveTotal <= 0.0) emptyList()
                    else {
                        var currentAngle = -90f
                        rawSlices.map { (info, color) ->
                            val (id, name, amount) = info
                            val pct = ((amount / effectiveTotal) * 100.0).toFloat()
                            val sweep = ((amount / effectiveTotal) * 360.0).toFloat()
                            val slice = ChartSlice(
                                id = id,
                                name = name,
                                amount = amount,
                                percentage = pct,
                                color = color,
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
            // Screen Header & Period Controls
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Period Pager Bar (<  October 2026  > + Filter)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MatteCharcoal)
                            .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { showPeriodPickerSheet = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Period", tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
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
                                        text = "No Personal Expenses in $periodLabel",
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
                                                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                                val outerRadius = size.width / 2f
                                                val strokeWidthBase = 32.dp.toPx()
                                                val innerRadius = outerRadius - strokeWidthBase - 16.dp.toPx()

                                                // Tapping inside the donut hole deselects current slice
                                                if (dist < innerRadius) {
                                                    selectedSliceId = null
                                                    return@detectTapGestures
                                                }
                                                // Tapping outside the outer ring is ignored
                                                if (dist > outerRadius) {
                                                    return@detectTapGestures
                                                }

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
                                    val singleSlice = chartSlices.size == 1

                                    chartSlices.forEach { slice ->
                                        val isSelected = slice.id == selectedSliceId
                                        val stroke = if (isSelected) strokeWidthBase + 10.dp.toPx() else strokeWidthBase
                                        val sweep = if (singleSlice) 360f else (slice.sweepAngle - 2f).coerceAtLeast(0.5f)

                                        drawArc(
                                            color = if (selectedSliceId == null || isSelected) slice.color else slice.color.copy(alpha = 0.35f),
                                            startAngle = slice.startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
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
                                            amount = selectedPeriodTotalTrueSpend,
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

            // Expense Trajectory: Week-over-Week & Month-over-Month Comparative Area Chart
            item {
                ExpenseComparisonAreaChartCard(transactions = allTransactions)
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
                    totalSpend = selectedPeriodTotalTrueSpend,
                    overallBudget = overallMonthlyBudget,
                    onEditClick = { showOverallBudgetDialog = true }
                )
            }

            // Category Budgets Progress List
            items(selectedPeriodCategorySpending, key = { it.categoryId }) { cat ->
                CategoryBudgetProgressRow(
                    categorySpending = cat,
                    onEditBudget = { categoryToEditBudget = cat }
                )
            }
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

// ─────────────────────────────────────────────────────────────────────────────
// Week-over-Week & Month-over-Month Comparative Expense Area Chart
// ─────────────────────────────────────────────────────────────────────────────

enum class ComparisonPeriod {
    WEEK_OVER_WEEK,
    MONTH_OVER_MONTH
}

enum class MetricViewMode {
    CUMULATIVE,
    DAILY
}

data class ComparisonPoint(
    val index: Int,
    val label: String,
    val fullLabel: String,
    val currentSpend: Double,
    val previousSpend: Double,
    val currentCumulative: Double,
    val previousCumulative: Double,
    val isFuture: Boolean
)

@Composable
fun ExpenseComparisonAreaChartCard(transactions: List<TransactionEntity>) {
    var period by remember { mutableStateOf(ComparisonPeriod.WEEK_OVER_WEEK) }
    var viewMode by remember { mutableStateOf(MetricViewMode.CUMULATIVE) }
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    val comparisonPoints = remember(transactions, period) {
        if (period == ComparisonPeriod.WEEK_OVER_WEEK) {
            computeWeekOverWeekData(transactions)
        } else {
            computeMonthOverMonthData(transactions)
        }
    }

    // Reset scrubbed index on period change
    LaunchedEffect(period) {
        scrubbedIndex = null
    }

    val activePoints = remember(comparisonPoints) {
        comparisonPoints.filter { !it.isFuture }
    }

    val currentTotal = remember(activePoints) {
        activePoints.lastOrNull()?.currentCumulative ?: 0.0
    }

    val previousTotal = remember(comparisonPoints) {
        comparisonPoints.lastOrNull()?.previousCumulative ?: 0.0
    }

    FoldCard(
        containerColor = MatteCharcoal,
        borderColor = BorderCharcoal
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title & Period Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoGraph,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "EXPENSE TRAJECTORY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (period == ComparisonPeriod.WEEK_OVER_WEEK) "Week-over-Week Area" else "Month-over-Month Area",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }
                }

                // Period Switcher (Week / Month)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputBg)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ComparisonTabPill(
                        text = "Week",
                        isSelected = period == ComparisonPeriod.WEEK_OVER_WEEK,
                        onClick = {
                            period = ComparisonPeriod.WEEK_OVER_WEEK
                            scrubbedIndex = null
                        }
                    )
                    ComparisonTabPill(
                        text = "Month",
                        isSelected = period == ComparisonPeriod.MONTH_OVER_MONTH,
                        onClick = {
                            period = ComparisonPeriod.MONTH_OVER_MONTH
                            scrubbedIndex = null
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-control Row: Mode Switcher (Cumulative vs Daily) + Reset hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputBg)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ComparisonTabPill(
                        text = "Cumulative",
                        isSelected = viewMode == MetricViewMode.CUMULATIVE,
                        onClick = { viewMode = MetricViewMode.CUMULATIVE }
                    )
                    ComparisonTabPill(
                        text = "Daily",
                        isSelected = viewMode == MetricViewMode.DAILY,
                        onClick = { viewMode = MetricViewMode.DAILY }
                    )
                }

                if (scrubbedIndex != null) {
                    Text(
                        text = "✕ Reset Inspection",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NeonAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { scrubbedIndex = null }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else {
                    Text(
                        text = "Drag/Tap to Inspect",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Summary Banner / Inspection Callout
            val inspectedPoint = scrubbedIndex?.let { comparisonPoints.getOrNull(it) }
            if (inspectedPoint != null) {
                // Inspection Display
                val currVal = if (viewMode == MetricViewMode.CUMULATIVE) inspectedPoint.currentCumulative else inspectedPoint.currentSpend
                val prevVal = if (viewMode == MetricViewMode.CUMULATIVE) inspectedPoint.previousCumulative else inspectedPoint.previousSpend
                val diffVal = currVal - prevVal

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputBg)
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = inspectedPoint.fullLabel.uppercase(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Curr: ",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            if (inspectedPoint.isFuture) {
                                Text(
                                    text = "-- (Future)",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                            } else {
                                MonospaceCurrencyText(
                                    amount = currVal,
                                    fontSize = 13.sp,
                                    color = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Prev: ",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            MonospaceCurrencyText(
                                amount = prevVal,
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    if (!inspectedPoint.isFuture) {
                        val diffSign = if (diffVal > 0) "+" else ""
                        val diffColor = if (diffVal > 0) NeonAmber else NeonEmerald
                        StatusPill(
                            text = "$diffSign₹${diffVal.toInt()}",
                            bgColor = diffColor.copy(alpha = 0.18f),
                            textColor = diffColor,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                // Default Total Overview
                val diffTotal = currentTotal - previousTotal
                val pctChange = if (previousTotal > 0) {
                    ((currentTotal - previousTotal) / previousTotal * 100).toInt()
                } else 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputBg)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current Total
                    Column {
                        Text(
                            text = if (period == ComparisonPeriod.WEEK_OVER_WEEK) "THIS WEEK" else "THIS MONTH",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        MonospaceCurrencyText(
                            amount = currentTotal,
                            fontSize = 16.sp,
                            color = NeonCyan
                        )
                    }

                    // Previous Total
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (period == ComparisonPeriod.WEEK_OVER_WEEK) "PREV WEEK" else "PREV MONTH",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        MonospaceCurrencyText(
                            amount = previousTotal,
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    // Pct Badge
                    val badgeColor = if (diffTotal > 0) NeonAmber else if (diffTotal < 0) NeonEmerald else TextMuted
                    val badgeSign = if (diffTotal > 0) "+" else ""
                    StatusPill(
                        text = "$badgeSign$pctChange%",
                        bgColor = badgeColor.copy(alpha = 0.18f),
                        textColor = badgeColor,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Area Chart Canvas
            val nPoints = comparisonPoints.size
            val maxVal = remember(comparisonPoints, viewMode) {
                val topCurr = comparisonPoints.filter { !it.isFuture }.maxOfOrNull {
                    if (viewMode == MetricViewMode.CUMULATIVE) it.currentCumulative else it.currentSpend
                } ?: 0.0
                val topPrev = comparisonPoints.maxOfOrNull {
                    if (viewMode == MetricViewMode.CUMULATIVE) it.previousCumulative else it.previousSpend
                } ?: 0.0
                maxOf(topCurr, topPrev).coerceAtLeast(100.0)
            }
            val effectiveMax = maxVal * 1.15

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(comparisonPoints, viewMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val paddingLeft = 16.dp.toPx()
                                val paddingRight = 16.dp.toPx()
                                val chartWidth = size.width - paddingLeft - paddingRight
                                val xStep = if (nPoints > 1) chartWidth / (nPoints - 1) else 0f
                                val x = offset.x.coerceIn(paddingLeft, size.width - paddingRight)
                                val idx = if (nPoints > 1) ((x - paddingLeft) / xStep).roundToInt().coerceIn(0, nPoints - 1) else 0
                                scrubbedIndex = idx
                            },
                            onDrag = { change, _ ->
                                val paddingLeft = 16.dp.toPx()
                                val paddingRight = 16.dp.toPx()
                                val chartWidth = size.width - paddingLeft - paddingRight
                                val xStep = if (nPoints > 1) chartWidth / (nPoints - 1) else 0f
                                val x = change.position.x.coerceIn(paddingLeft, size.width - paddingRight)
                                val idx = if (nPoints > 1) ((x - paddingLeft) / xStep).roundToInt().coerceIn(0, nPoints - 1) else 0
                                scrubbedIndex = idx
                            },
                            onDragEnd = {},
                            onDragCancel = { scrubbedIndex = null }
                        )
                    }
                    .pointerInput(comparisonPoints, viewMode) {
                        detectTapGestures { offset ->
                            val paddingLeft = 16.dp.toPx()
                            val paddingRight = 16.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRight
                            val xStep = if (nPoints > 1) chartWidth / (nPoints - 1) else 0f
                            val x = offset.x.coerceIn(paddingLeft, size.width - paddingRight)
                            val idx = if (nPoints > 1) ((x - paddingLeft) / xStep).roundToInt().coerceIn(0, nPoints - 1) else 0
                            scrubbedIndex = if (scrubbedIndex == idx) null else idx
                        }
                    }
            ) {
                val paddingLeft = 16.dp.toPx()
                val paddingRight = 16.dp.toPx()
                val paddingTop = 16.dp.toPx()
                val paddingBottom = 12.dp.toPx()

                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom
                val baselineY = size.height - paddingBottom
                val xStep = if (nPoints > 1) chartWidth / (nPoints - 1) else 0f

                // Draw Horizontal Subtle Dashed Gridlines
                val gridColor = Color(0xFF2B2F38)
                val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                    val gy = baselineY - fraction * chartHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(paddingLeft, gy),
                        end = Offset(size.width - paddingRight, gy),
                        strokeWidth = 1f,
                        pathEffect = gridDash
                    )
                }

                // Baseline line
                drawLine(
                    color = Color(0xFF383D48),
                    start = Offset(paddingLeft, baselineY),
                    end = Offset(size.width - paddingRight, baselineY),
                    strokeWidth = 1.5f
                )

                // 1. Previous Period Coordinates
                val prevPoints = comparisonPoints.mapIndexed { i, pt ->
                    val v = if (viewMode == MetricViewMode.CUMULATIVE) pt.previousCumulative else pt.previousSpend
                    val y = baselineY - (v / effectiveMax * chartHeight).toFloat().coerceIn(0f, chartHeight)
                    Offset(paddingLeft + i * xStep, y)
                }

                // 2. Current Period Coordinates (Non-future days only)
                val currPoints = activePoints.map { pt ->
                    val v = if (viewMode == MetricViewMode.CUMULATIVE) pt.currentCumulative else pt.currentSpend
                    val y = baselineY - (v / effectiveMax * chartHeight).toFloat().coerceIn(0f, chartHeight)
                    Offset(paddingLeft + pt.index * xStep, y)
                }

                // ── Draw Previous Period Area (Background - Grey) ──
                if (prevPoints.isNotEmpty()) {
                    val prevFillBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6B7280).copy(alpha = 0.28f),
                            Color(0xFF374151).copy(alpha = 0.04f)
                        ),
                        startY = paddingTop,
                        endY = baselineY
                    )
                    val prevFillPath = buildSmoothPath(prevPoints, baselineY, closeForFill = true)
                    drawPath(prevFillPath, brush = prevFillBrush)

                    val prevLinePath = buildSmoothPath(prevPoints, baselineY, closeForFill = false)
                    drawPath(
                        prevLinePath,
                        color = Color(0xFF9CA3AF).copy(alpha = 0.85f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // ── Draw Current Period Area (Foreground - Neon Cyan) ──
                if (currPoints.isNotEmpty()) {
                    val currFillBrush = Brush.verticalGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.40f),
                            NeonCyan.copy(alpha = 0.04f)
                        ),
                        startY = paddingTop,
                        endY = baselineY
                    )
                    val currFillPath = buildSmoothPath(currPoints, baselineY, closeForFill = true)
                    drawPath(currFillPath, brush = currFillBrush)

                    val currLinePath = buildSmoothPath(currPoints, baselineY, closeForFill = false)
                    drawPath(
                        currLinePath,
                        color = NeonCyan,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Glowing Head Dot on the latest active point
                    val lastPt = currPoints.last()
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = lastPt
                    )
                    drawCircle(
                        color = NeonCyan,
                        radius = 4.dp.toPx(),
                        center = lastPt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.8.dp.toPx(),
                        center = lastPt
                    )
                }

                // ── Draw Scrubbing / Inspection Indicator ──
                if (scrubbedIndex != null && scrubbedIndex in 0 until nPoints) {
                    val sIdx = scrubbedIndex!!
                    val scrubX = paddingLeft + sIdx * xStep

                    // Vertical guideline
                    drawLine(
                        color = Color.White.copy(alpha = 0.65f),
                        start = Offset(scrubX, paddingTop),
                        end = Offset(scrubX, baselineY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    // Dot on previous period
                    if (sIdx in prevPoints.indices) {
                        drawCircle(
                            color = Color(0xFF9CA3AF),
                            radius = 4.dp.toPx(),
                            center = prevPoints[sIdx]
                        )
                    }

                    // Dot on current period if active
                    val currPt = currPoints.getOrNull(activePoints.indexOfFirst { it.index == sIdx })
                    if (currPt != null) {
                        drawCircle(
                            color = NeonCyan,
                            radius = 5.dp.toPx(),
                            center = currPt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = currPt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // X-Axis Labels Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (period == ComparisonPeriod.WEEK_OVER_WEEK) {
                    comparisonPoints.forEach { pt ->
                        val isScrubbed = scrubbedIndex == pt.index
                        Text(
                            text = pt.label,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isScrubbed) FontWeight.Bold else FontWeight.Normal,
                            color = if (isScrubbed) NeonCyan else TextMuted
                        )
                    }
                } else {
                    // Month ticks: 1, 5, 10, 15, 20, 25, comparisonPoints.size
                    val monthTicks = listOf(1, 5, 10, 15, 20, 25, comparisonPoints.size).distinct()
                    monthTicks.forEach { dayNum ->
                        val isScrubbed = scrubbedIndex == (dayNum - 1)
                        Text(
                            text = "$dayNum",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isScrubbed) FontWeight.Bold else FontWeight.Normal,
                            color = if (isScrubbed) NeonCyan else TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current Period
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NeonCyan, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (period == ComparisonPeriod.WEEK_OVER_WEEK) "Current Week" else "Current Month",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.width(20.dp))

                // Previous Period
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF9CA3AF), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (period == ComparisonPeriod.WEEK_OVER_WEEK) "Previous Week (Benchmark)" else "Previous Month (Benchmark)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ComparisonTabPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) NeonCyan else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) OledBlack else TextSecondary
        )
    }
}

private fun buildSmoothPath(points: List<Offset>, baselineY: Float, closeForFill: Boolean): Path {
    val path = Path()
    if (points.isEmpty()) return path

    if (closeForFill) {
        path.moveTo(points.first().x, baselineY)
        path.lineTo(points.first().x, points.first().y)
    } else {
        path.moveTo(points.first().x, points.first().y)
    }

    if (points.size == 1) {
        if (closeForFill) {
            path.lineTo(points.first().x + 2f, points.first().y)
            path.lineTo(points.first().x + 2f, baselineY)
            path.close()
        }
        return path
    }

    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val cp1X = p0.x + (p1.x - p0.x) / 2f
        val cp1Y = p0.y
        val cp2X = p0.x + (p1.x - p0.x) / 2f
        val cp2Y = p1.y
        path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p1.x, p1.y)
    }

    if (closeForFill) {
        path.lineTo(points.last().x, baselineY)
        path.close()
    }
    return path
}

internal fun computeWeekOverWeekData(transactions: List<TransactionEntity>): List<ComparisonPoint> {
    val expenseTxns = transactions.filter { it.type == TransactionType.EXPENSE }
    val now = Calendar.getInstance()

    // Current week Monday
    val currCal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (currCal.timeInMillis > now.timeInMillis) {
        currCal.add(Calendar.WEEK_OF_YEAR, -1)
    }

    val prevCal = (currCal.clone() as Calendar).apply {
        add(Calendar.WEEK_OF_YEAR, -1)
    }

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val fullDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val result = ArrayList<ComparisonPoint>(7)
    var runningCurrent = 0.0
    var runningPrev = 0.0

    val currDayCal = currCal.clone() as Calendar
    val prevDayCal = prevCal.clone() as Calendar

    for (i in 0..6) {
        val currStart = currDayCal.timeInMillis
        currDayCal.set(Calendar.HOUR_OF_DAY, 23)
        currDayCal.set(Calendar.MINUTE, 59)
        currDayCal.set(Calendar.SECOND, 59)
        currDayCal.set(Calendar.MILLISECOND, 999)
        val currEnd = currDayCal.timeInMillis

        val prevStart = prevDayCal.timeInMillis
        prevDayCal.set(Calendar.HOUR_OF_DAY, 23)
        prevDayCal.set(Calendar.MINUTE, 59)
        prevDayCal.set(Calendar.SECOND, 59)
        prevDayCal.set(Calendar.MILLISECOND, 999)
        val prevEnd = prevDayCal.timeInMillis

        val isFuture = currStart > now.timeInMillis

        val currSpend = if (!isFuture) {
            expenseTxns.filter { it.timestamp in currStart..currEnd }.sumOf { it.myActualShare }
        } else 0.0

        val prevSpend = expenseTxns.filter { it.timestamp in prevStart..prevEnd }.sumOf { it.myActualShare }

        if (!isFuture) {
            runningCurrent += currSpend
        }
        runningPrev += prevSpend

        result.add(
            ComparisonPoint(
                index = i,
                label = dayNames[i],
                fullLabel = fullDayNames[i],
                currentSpend = currSpend,
                previousSpend = prevSpend,
                currentCumulative = runningCurrent,
                previousCumulative = runningPrev,
                isFuture = isFuture
            )
        )

        currDayCal.add(Calendar.DAY_OF_YEAR, 1)
        currDayCal.set(Calendar.HOUR_OF_DAY, 0)
        currDayCal.set(Calendar.MINUTE, 0)
        currDayCal.set(Calendar.SECOND, 0)
        currDayCal.set(Calendar.MILLISECOND, 0)

        prevDayCal.add(Calendar.DAY_OF_YEAR, 1)
        prevDayCal.set(Calendar.HOUR_OF_DAY, 0)
        prevDayCal.set(Calendar.MINUTE, 0)
        prevDayCal.set(Calendar.SECOND, 0)
        prevDayCal.set(Calendar.MILLISECOND, 0)
    }
    return result
}

internal fun computeMonthOverMonthData(transactions: List<TransactionEntity>): List<ComparisonPoint> {
    val expenseTxns = transactions.filter { it.type == TransactionType.EXPENSE }
    val now = Calendar.getInstance()
    val todayDay = now.get(Calendar.DAY_OF_MONTH)

    // Current month 1st
    val currCal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val daysInCurrMonth = currCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Previous month 1st
    val prevCal = (currCal.clone() as Calendar).apply {
        add(Calendar.MONTH, -1)
    }
    val daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val maxDays = maxOf(daysInCurrMonth, daysInPrevMonth)
    val result = ArrayList<ComparisonPoint>(maxDays)
    var runningCurrent = 0.0
    var runningPrev = 0.0

    for (day in 1..maxDays) {
        val isFuture = day > todayDay || day > daysInCurrMonth

        var currSpend = 0.0
        if (!isFuture && day <= daysInCurrMonth) {
            val dCal = currCal.clone() as Calendar
            dCal.set(Calendar.DAY_OF_MONTH, day)
            val dStart = dCal.timeInMillis
            dCal.set(Calendar.HOUR_OF_DAY, 23)
            dCal.set(Calendar.MINUTE, 59)
            dCal.set(Calendar.SECOND, 59)
            dCal.set(Calendar.MILLISECOND, 999)
            val dEnd = dCal.timeInMillis

            currSpend = expenseTxns.filter { it.timestamp in dStart..dEnd }.sumOf { it.myActualShare }
            runningCurrent += currSpend
        }

        var prevSpend = 0.0
        if (day <= daysInPrevMonth) {
            val dCal = prevCal.clone() as Calendar
            dCal.set(Calendar.DAY_OF_MONTH, day)
            val dStart = dCal.timeInMillis
            dCal.set(Calendar.HOUR_OF_DAY, 23)
            dCal.set(Calendar.MINUTE, 59)
            dCal.set(Calendar.SECOND, 59)
            dCal.set(Calendar.MILLISECOND, 999)
            val dEnd = dCal.timeInMillis

            prevSpend = expenseTxns.filter { it.timestamp in dStart..dEnd }.sumOf { it.myActualShare }
            runningPrev += prevSpend
        }

        result.add(
            ComparisonPoint(
                index = day - 1,
                label = "$day",
                fullLabel = "Day $day",
                currentSpend = currSpend,
                previousSpend = prevSpend,
                currentCumulative = runningCurrent,
                previousCumulative = runningPrev,
                isFuture = isFuture
            )
        )
    }
    return result
}
