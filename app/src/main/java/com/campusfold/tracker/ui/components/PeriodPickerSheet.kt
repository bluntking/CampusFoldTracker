package com.campusfold.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DateViewMode {
    MONTH,
    WEEK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPickerBottomSheet(
    currentMode: DateViewMode,
    monthOffset: Int,
    weekOffset: Int,
    onSelectPeriod: (DateViewMode, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OledBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
    ) {
        PeriodPickerContent(
            currentMode = currentMode,
            monthOffset = monthOffset,
            weekOffset = weekOffset,
            onSelectPeriod = { mode, mOff, wOff ->
                onSelectPeriod(mode, mOff, wOff)
                onDismiss()
            }
        )
    }
}

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
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Select Time Period",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "QUICK PERIODS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
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

        Text(
            text = "PAST MONTHS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

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
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NeonEmerald else TextPrimary
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
