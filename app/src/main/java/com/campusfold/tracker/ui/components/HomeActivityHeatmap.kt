package com.campusfold.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HeatmapDayInfo(
    val timestamp: Long,
    val dayLabel: String,
    val spendAmount: Double,
    val count: Int,
    val level: Int
)

/**
 * Hardware-Accelerated Activity Heatmap Tile for Home (Part 3 #7):
 * - Last 14 Weeks Activity
 * - N active days, total spent
 * - Month dividers
 * - Tap a day to see its spend and transaction count
 */
@Composable
fun HomeActivityHeatmap(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier
) {
    var selectedDay by remember { mutableStateOf<HeatmapDayInfo?>(null) }

    // Pre-aggregate expenses by calendar day
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

    // Generate 14 weeks (98 days) up to today
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

    // Month transitions to split and center month labels across columns
    val monthTransitions = remember(weeks) {
        val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
        val list = mutableListOf<Triple<Int, String, Int>>()
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Activity Heatmap",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
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

                    // High-performance Hardware-Accelerated Heatmap Canvas
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
                                            selectedDay = if (selectedDay?.timestamp == clickedDay.timestamp) null else clickedDay
                                        }
                                    }
                                }
                        ) {
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

                        // Month Labels across exact columns
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

                Spacer(modifier = Modifier.height(12.dp))

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
                                text = selectedDay!!.dayLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (selectedDay!!.spendAmount > 0) "₹${selectedDay!!.spendAmount.toInt()} (${selectedDay!!.count} txns)" else "₹0 (No spend)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedDay!!.spendAmount > 0) Color(0xFF8CB4F8) else TextMuted
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
}
