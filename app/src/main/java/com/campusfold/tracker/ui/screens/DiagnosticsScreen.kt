package com.campusfold.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.ai.AiDiagnostics
import com.campusfold.tracker.ai.AiModelCatalog
import com.campusfold.tracker.ai.DiagnosticEvent
import com.campusfold.tracker.ai.LocalAiManager
import com.campusfold.tracker.ai.AiModelStatus
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val events by AiDiagnostics.eventsFlow.collectAsStateWithLifecycle()
    val crashTrace by AiDiagnostics.lastCrashTrace.collectAsStateWithLifecycle()
    val modelStatus by LocalAiManager.modelStatus.collectAsStateWithLifecycle()
    val reversedEvents = remember(events) { events.reversed() }

    val memInfo = remember { AiModelCatalog.getMemoryInfo(context) }
    val totalRamMb = memInfo.totalMem / (1024 * 1024)
    val availRamMb = memInfo.availMem / (1024 * 1024)

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DIAGNOSTICS & LOGS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )
                        Text(
                            text = "AI Engine Health",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { AiDiagnostics.shareLog(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Log", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MatteCharcoal)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Uncaught Crash Alert Card (if any previous crash was captured)
            if (!crashTrace.isNullOrBlank()) {
                item {
                    FoldCard(
                        containerColor = NeonCrimson.copy(alpha = 0.12f),
                        borderColor = NeonCrimson.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Crash Captured From Previous Launch",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = NeonCrimson
                                    )
                                }
                                TextButton(
                                    onClick = { AiDiagnostics.clearCrashTrace() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Dismiss", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .background(OledBlack.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = crashTrace ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 2. System Hardware & Engine State Card
            item {
                FoldCard(
                    containerColor = CardElevated,
                    borderColor = BorderCharcoal
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SYSTEM HARDWARE & MEMORY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Available RAM", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "$availRamMb MB / $totalRamMb MB",
                                fontWeight = FontWeight.Bold,
                                color = if (memInfo.lowMemory) NeonCrimson else NeonEmerald,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("AI Process State", color = TextSecondary, fontSize = 12.sp)
                            val (statusText, statusColor) = when (modelStatus) {
                                is AiModelStatus.Ready -> Pair("READY (Active)", NeonEmerald)
                                is AiModelStatus.Loading -> Pair("LOADING...", NeonAmber)
                                is AiModelStatus.Stopped -> Pair("STOPPED", NeonCrimson)
                                is AiModelStatus.Error -> Pair("ERROR", NeonCrimson)
                                else -> Pair("NOT LOADED", TextMuted)
                            }
                            Text(
                                text = statusText,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recommended Model", color = TextSecondary, fontSize = 12.sp)
                            val rec = AiModelCatalog.recommendModel(context)
                            Text(
                                text = rec?.displayName ?: "None (Insufficient RAM)",
                                fontWeight = FontWeight.Bold,
                                color = if (rec != null) NeonCyan else NeonAmber,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 3. Action Buttons (Share Log / Clear Log)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { AiDiagnostics.shareLog(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Log", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { AiDiagnostics.clearEvents() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Events", fontSize = 12.sp)
                    }
                }
            }

            // 4. Header for Events List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAST 50 DIAGNOSTIC EVENTS (${events.size})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }

            if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No events recorded yet", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(reversedEvents, key = { "${it.timestamp}_${it.type}_${it.message.hashCode()}" }) { event ->
                    DiagnosticEventItem(event)
                }
            }
        }
    }
}

@Composable
fun DiagnosticEventItem(event: DiagnosticEvent) {
    val (badgeBg, badgeTextColor) = when (event.type) {
        "LOAD_OK", "DOWNLOAD_OK" -> Pair(NeonEmerald.copy(alpha = 0.15f), NeonEmerald)
        "LOAD_FAIL", "DOWNLOAD_FAIL", "ENGINE_DIED", "UNCAUGHT_EXCEPTION", "CRASH_GUARD" -> Pair(NeonCrimson.copy(alpha = 0.15f), NeonCrimson)
        "LOAD_START", "DOWNLOAD_START" -> Pair(NeonCyan.copy(alpha = 0.15f), NeonCyan)
        "RAM_CHECK", "CLEANUP" -> Pair(NeonAmber.copy(alpha = 0.15f), NeonAmber)
        else -> Pair(BorderCharcoal, TextSecondary)
    }

    FoldCard(
        containerColor = CardElevated,
        borderColor = BorderCharcoal
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = event.type,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }

                Text(
                    text = event.formattedTime(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = event.message,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            if (!event.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.details,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
        }
    }
}
