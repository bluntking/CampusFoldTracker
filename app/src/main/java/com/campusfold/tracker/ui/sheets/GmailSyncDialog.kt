package com.campusfold.tracker.ui.sheets

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.campusfold.tracker.data.model.GmailSyncStats
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GmailSyncDialog(
    viewModel: TransactionViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val savedEmail by viewModel.gmailSyncEmail.collectAsState()
    val savedPassword by viewModel.gmailSyncAppPassword.collectAsState()
    val isSyncing by viewModel.isGmailSyncing.collectAsState()
    val syncProgress by viewModel.gmailSyncProgress.collectAsState()

    var emailInput by remember(savedEmail) { mutableStateOf(savedEmail ?: "") }
    var passwordInput by remember(savedPassword) { mutableStateOf(savedPassword ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberCredentials by remember { mutableStateOf(true) }
    var showHelpInfo by remember { mutableStateOf(false) }
    var syncResultStats by remember { mutableStateOf<GmailSyncStats?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }

    // Start Date Selection (default: 20 Oct 2026 or 30 days ago)
    val defaultCalendar = remember {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.YEAR) == 2026 && cal.get(Calendar.MONTH) >= Calendar.OCTOBER) {
            cal.set(2026, Calendar.OCTOBER, 20, 0, 0, 0)
        } else {
            cal.add(Calendar.DAY_OF_MONTH, -30)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }
        cal.set(Calendar.MILLISECOND, 0)
        cal
    }

    var selectedCalendar by remember { mutableStateOf<Calendar?>(defaultCalendar) }
    var selectedPreset by remember { mutableStateOf<String>("20_OCT_2026") }

    val shortDateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    fun openDatePicker() {
        val baseCal = selectedCalendar ?: Calendar.getInstance()
        val year = baseCal.get(Calendar.YEAR)
        val month = baseCal.get(Calendar.MONTH)
        val day = baseCal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, y, m, d ->
            val newCal = Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedCalendar = newCal
            selectedPreset = "CUSTOM"
        }, year, month, day).show()
    }

    Dialog(onDismissRequest = { if (!isSyncing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonPurple.copy(alpha = 0.15f))
                            .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Axis Bank Gmail Sync",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Direct IMAP fetch for Debits & Credits",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    IconButton(
                        onClick = { showHelpInfo = !showHelpInfo },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help info",
                            tint = if (showHelpInfo) NeonCyan else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Help Callout
                AnimatedVisibility(visible = showHelpInfo) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OledBlack.copy(alpha = 0.6f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "How to create an App Password:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Enable 2-Step Verification on your Google Account.\n" +
                                    "2. Go to myaccount.google.com/apppasswords.\n" +
                                    "3. Name the app 'CampusFold' and copy the 16-character code.\n" +
                                    "4. Paste it below. Your credentials remain strictly on this device.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://myaccount.google.com/apppasswords")
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Google App Passwords", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input: Gmail Address
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Gmail Address", fontSize = 13.sp) },
                    placeholder = { Text("e.g. sahil.gupta@gmail.com", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    enabled = !isSyncing,
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted,
                        focusedContainerColor = CardElevated,
                        unfocusedContainerColor = CardElevated
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input: App Password
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("16-character Google App Password", fontSize = 13.sp) },
                    placeholder = { Text("abcd efgh ijkl mnop", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    enabled = !isSyncing,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonPurple,
                        unfocusedLabelColor = TextMuted,
                        focusedContainerColor = CardElevated,
                        unfocusedContainerColor = CardElevated
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Remember Credentials & Clear row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !isSyncing) {
                            rememberCredentials = !rememberCredentials
                        }
                    ) {
                        Checkbox(
                            checked = rememberCredentials,
                            onCheckedChange = { rememberCredentials = it },
                            enabled = !isSyncing,
                            colors = CheckboxDefaults.colors(
                                checkedColor = NeonEmerald,
                                uncheckedColor = TextMuted,
                                checkmarkColor = OledBlack
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save credentials securely",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    if (!savedEmail.isNullOrBlank() || !savedPassword.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.clearGmailCredentials()
                                emailInput = ""
                                passwordInput = ""
                            },
                            enabled = !isSyncing,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Clear Saved", fontSize = 11.sp, color = NeonCrimson)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Date Filter Section
                Text(
                    text = "FETCH EMAILS FROM:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date preset chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Chip 1: 20 Oct 2026
                    FilterChip(
                        selected = selectedPreset == "20_OCT_2026",
                        onClick = {
                            selectedPreset = "20_OCT_2026"
                            val cal = Calendar.getInstance().apply {
                                set(2026, Calendar.OCTOBER, 20, 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            selectedCalendar = cal
                        },
                        label = { Text("From 20 Oct 2026", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CardElevated,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedPreset == "FROM_20_OCT",
                            borderColor = BorderCharcoal,
                            selectedBorderColor = NeonCyan.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.height(30.dp)
                    )

                    // Chip 2: Last 30 Days
                    FilterChip(
                        selected = selectedPreset == "LAST_30_DAYS",
                        onClick = {
                            selectedPreset = "LAST_30_DAYS"
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_MONTH, -30)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            selectedCalendar = cal
                        },
                        label = { Text("Last 30 Days", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CardElevated,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedPreset == "LAST_30_DAYS",
                            borderColor = BorderCharcoal,
                            selectedBorderColor = NeonCyan.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.height(30.dp)
                    )

                    // Chip 3: All Time
                    FilterChip(
                        selected = selectedPreset == "ALL_TIME",
                        onClick = {
                            selectedPreset = "ALL_TIME"
                            selectedCalendar = null
                        },
                        label = { Text("All Time", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CardElevated,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedPreset == "ALL_TIME",
                            borderColor = BorderCharcoal,
                            selectedBorderColor = NeonCyan.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.height(30.dp)
                    )

                    // Chip 4: Custom Date
                    FilterChip(
                        selected = selectedPreset == "CUSTOM",
                        onClick = { openDatePicker() },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedPreset == "CUSTOM" && selectedCalendar != null) {
                                        shortDateFmt.format(selectedCalendar!!.time)
                                    } else "Custom...",
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = CardElevated,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedPreset == "CUSTOM",
                            borderColor = BorderCharcoal,
                            selectedBorderColor = NeonCyan.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }

                // Selected date readout
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardElevated)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedCalendar != null) {
                            "Syncing emails from ${shortDateFmt.format(selectedCalendar!!.time)} to today"
                        } else {
                            "Syncing all historical Axis Bank emails in Gmail"
                        },
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }

                // Progress / Results Banner
                if (isSyncing || syncProgress != null || syncResultStats != null || syncError != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val bannerBg = when {
                        syncError != null -> NeonCrimson.copy(alpha = 0.12f)
                        syncResultStats != null -> NeonEmerald.copy(alpha = 0.12f)
                        else -> NeonCyan.copy(alpha = 0.12f)
                    }
                    val bannerBorder = when {
                        syncError != null -> NeonCrimson.copy(alpha = 0.4f)
                        syncResultStats != null -> NeonEmerald.copy(alpha = 0.4f)
                        else -> NeonCyan.copy(alpha = 0.4f)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bannerBg)
                            .border(1.dp, bannerBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = NeonCyan
                                )
                            } else if (syncError != null) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isSyncing -> "Syncing Axis Transactions..."
                                    syncError != null -> "Gmail Sync Error"
                                    else -> "Sync Complete!"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSyncing -> NeonCyan
                                    syncError != null -> NeonCrimson
                                    else -> NeonEmerald
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                syncError != null -> syncError!!
                                syncResultStats != null -> {
                                    val s = syncResultStats!!
                                    val total = s.importedDebits + s.importedCredits
                                    "Processed ${s.totalFound} emails: Imported $total transactions (${s.importedDebits} Debits, ${s.importedCredits} Credits). ${s.autoSettledCredits} auto-settled, ${s.pendingSuggestionsCreated} pending suggestions, ${s.skippedDuplicates} duplicates skipped."
                                }
                                else -> syncProgress ?: "Connecting to Gmail IMAP..."
                            },
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(if (syncResultStats != null) "Close" else "Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            syncError = null
                            syncResultStats = null
                            viewModel.syncAxisFromGmail(
                                emailAddress = emailInput.trim(),
                                appPassword = passwordInput.trim(),
                                startDateMillis = selectedCalendar?.timeInMillis,
                                saveCredentials = rememberCredentials
                            ) { stats, err ->
                                syncResultStats = stats
                                syncError = err
                            }
                        },
                        enabled = !isSyncing && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = OledBlack,
                            disabledContainerColor = MatteCharcoal,
                            disabledContentColor = TextMuted
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OledBlack
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Axis Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
