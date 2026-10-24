package com.campusfold.tracker.ui.sheets

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncFromDateDialog(
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onConfirmSync: (startDateMillis: Long) -> Unit
) {
    val context = LocalContext.current

    // Default start date: 20 Oct 2026 or 7 days ago if current date is earlier
    val defaultCalendar = remember {
        val cal = Calendar.getInstance()
        // If current year is 2026 and month is >= Oct, default to 20 Oct 2026
        if (cal.get(Calendar.YEAR) == 2026 && cal.get(Calendar.MONTH) >= Calendar.OCTOBER) {
            cal.set(2026, Calendar.OCTOBER, 20, 0, 0, 0)
        } else {
            cal.add(Calendar.DAY_OF_MONTH, -7)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }
        cal.set(Calendar.MILLISECOND, 0)
        cal
    }

    var selectedCalendar by remember { mutableStateOf(defaultCalendar) }

    val dateDisplayFmt = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }
    val shortDateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    fun openDatePicker() {
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, y, m, d ->
            val newCal = Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedCalendar = newCal
        }, year, month, day).show()
    }

    Dialog(onDismissRequest = { if (!isScanning) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SYNC SMS FROM DATE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Track from Custom Date",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Select the exact date you want to start tracking from. Only SMS alerts received on or after this date will be imported.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Active Selected Date Box (Tappable to launch DatePickerDialog)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(InputBg)
                        .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { openDatePicker() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "START TRACKING FROM",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dateDisplayFmt.format(selectedCalendar.time),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                        }

                        Button(
                            onClick = { openDatePicker() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MatteCharcoal,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Change", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Presets
                Text(
                    text = "QUICK SHORTCUTS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Preset 1: 20 Oct 2026
                    SuggestionChip(
                        onClick = {
                            selectedCalendar = Calendar.getInstance().apply {
                                set(2026, Calendar.OCTOBER, 20, 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        },
                        label = { Text("20 Oct 2026", fontSize = 11.sp) }
                    )

                    // Preset 2: 1st of This Month
                    SuggestionChip(
                        onClick = {
                            selectedCalendar = Calendar.getInstance().apply {
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        },
                        label = { Text("1st of Month", fontSize = 11.sp) }
                    )

                    // Preset 3: Last 7 Days
                    SuggestionChip(
                        onClick = {
                            selectedCalendar = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_MONTH, -7)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        },
                        label = { Text("Last 7 Days", fontSize = 11.sp) }
                    )

                    // Preset 4: Last 30 Days
                    SuggestionChip(
                        onClick = {
                            selectedCalendar = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_MONTH, -30)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        },
                        label = { Text("Last 30 Days", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel & Start Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal)
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            onConfirmSync(selectedCalendar.timeInMillis)
                            onDismiss()
                        },
                        enabled = !isScanning,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = OledBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = OledBlack,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing...", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start Sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
