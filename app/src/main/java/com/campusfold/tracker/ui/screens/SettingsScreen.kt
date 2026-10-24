package com.campusfold.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.sheets.SyncFromDateDialog
import com.campusfold.tracker.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubScreen {
    SYNC_DATA,
    BACKUP,
    RULES,
    REVIEW_QUEUE,
    CATEGORIES_VENDORS,
    DIAGNOSTICS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TransactionViewModel,
    onNavigateToAccounts: () -> Unit,
    onOpenAiCoach: () -> Unit
) {
    var subScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

    when (subScreen) {
        SettingsSubScreen.SYNC_DATA -> {
            SyncAndDataSubScreen(
                viewModel = viewModel,
                onBack = { subScreen = null }
            )
        }
        SettingsSubScreen.BACKUP -> {
            BackupSubScreen(
                viewModel = viewModel,
                onBack = { subScreen = null }
            )
        }
        SettingsSubScreen.RULES -> {
            RulesScreen(
                viewModel = viewModel,
                onNavigateBack = { subScreen = null }
            )
        }
        SettingsSubScreen.REVIEW_QUEUE -> {
            VendorsCategoriesScreen(
                viewModel = viewModel,
                initialTab = 1,
                onBack = { subScreen = null }
            )
        }
        SettingsSubScreen.CATEGORIES_VENDORS -> {
            VendorsCategoriesScreen(
                viewModel = viewModel,
                initialTab = 3,
                onBack = { subScreen = null }
            )
        }
        SettingsSubScreen.DIAGNOSTICS -> {
            DiagnosticsScreen(
                onDismiss = { subScreen = null }
            )
        }
        null -> {
            MainSettingsContent(
                viewModel = viewModel,
                onNavigateToAccounts = onNavigateToAccounts,
                onOpenAiCoach = onOpenAiCoach,
                onOpenSubScreen = { subScreen = it }
            )
        }
    }
}

@Composable
private fun MainSettingsContent(
    viewModel: TransactionViewModel,
    onNavigateToAccounts: () -> Unit,
    onOpenAiCoach: () -> Unit,
    onOpenSubScreen: (SettingsSubScreen) -> Unit
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val allRules by viewModel.allRules.collectAsStateWithLifecycle()
    val pendingReviewItems by viewModel.pendingReviewItems.collectAsStateWithLifecycle()
    val autoUseBankBalances by viewModel.autoUseBankBalances.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val amoledMode by viewModel.amoledMode.collectAsStateWithLifecycle()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showClearTxConfirmDialog by remember { mutableStateOf(false) }
    var showResetAllConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // Top Header
        item {
            Column {
                Text(
                    text = "CAMPUS FOLD",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = "Settings & Preferences",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            }
        }

        // 1. Sync & Data Card
        item {
            FoldCard(
                modifier = Modifier.clickable { onOpenSubScreen(SettingsSubScreen.SYNC_DATA) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sync & Data",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Gmail IMAP, SMS, bank senders, live sync",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSyncing) {
                            StatusPill(
                                text = "Syncing",
                                bgColor = NeonCyan.copy(alpha = 0.2f),
                                textColor = NeonCyan,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }
        }

        // 2. Backup & Restore Card
        item {
            FoldCard(
                modifier = Modifier.clickable { onOpenSubScreen(SettingsSubScreen.BACKUP) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Backup & Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Export or restore JSON snapshot",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }
        }

        // 3. Organize & Automation Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ORGANIZATION & AUTOMATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Categories & Vendors
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSubScreen(SettingsSubScreen.CATEGORIES_VENDORS) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Categories & Vendors", fontSize = 14.sp, color = TextPrimary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Review Queue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSubScreen(SettingsSubScreen.REVIEW_QUEUE) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Review Queue", fontSize = 14.sp, color = TextPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pendingReviewItems.isNotEmpty()) {
                                StatusPill(
                                    text = "${pendingReviewItems.size} pending",
                                    bgColor = NeonPurple.copy(alpha = 0.25f),
                                    textColor = NeonPurple,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Rules Management
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSubScreen(SettingsSubScreen.RULES) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Rule, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Automation Rules", fontSize = 14.sp, color = TextPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = "${allRules.size} rules",
                                bgColor = BorderCharcoal,
                                textColor = TextSecondary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }
            }
        }

        // 4. Accounts Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACCOUNTS & BALANCES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAccounts() }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Manage Bank Accounts & Cards", fontSize = 14.sp, color = TextPrimary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-use bank-reported balances", fontSize = 14.sp, color = TextPrimary)
                            Text(
                                "Automatically anchor derived balance to latest balance stated in SMS / Email",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = autoUseBankBalances,
                            onCheckedChange = { viewModel.setAutoUseBankBalances(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = NeonCyan
                            )
                        )
                    }
                }
            }
        }

        // 5. AI Assistant & Coach Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI ASSISTANT & HEALTH",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAiCoach() }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("AI Financial Coach", fontSize = 14.sp, color = TextPrimary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSubScreen(SettingsSubScreen.DIAGNOSTICS) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("AI Diagnostics & Logs", fontSize = 14.sp, color = TextPrimary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }
        }

        // 6. Appearance & Profile Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APPEARANCE & PROFILE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Display Name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEditNameDialog = true }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Display Name", fontSize = 14.sp, color = TextPrimary)
                            Text(
                                if (userName.isNotBlank()) userName else "Not set (optional)",
                                fontSize = 12.sp,
                                color = if (userName.isNotBlank()) NeonCyan else TextMuted
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Theme Selector: Dark, Light, AMOLED
                    Text("Theme Mode", fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == "dark" && !amoledMode,
                            onClick = {
                                viewModel.setThemeMode("dark")
                                viewModel.setAmoledMode(false)
                            },
                            label = { Text("Dark", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            )
                        )
                        FilterChip(
                            selected = amoledMode,
                            onClick = {
                                viewModel.setThemeMode("dark")
                                viewModel.setAmoledMode(true)
                            },
                            label = { Text("AMOLED", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            )
                        )
                        FilterChip(
                            selected = themeMode == "light",
                            onClick = {
                                viewModel.setThemeMode("light")
                                viewModel.setAmoledMode(false)
                            },
                            label = { Text("Light", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            )
                        )
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Dynamic Color
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dynamic Colors", fontSize = 14.sp, color = TextPrimary)
                            Text("Derive palette from system wallpaper", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = NeonCyan
                            )
                        )
                    }
                }
            }
        }

        // 7. Danger Zone Card
        item {
            FoldCard(
                containerColor = Color(0xFF1E1114),
                borderColor = NeonCrimson.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DANGER ZONE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCrimson
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Data reset operations cannot be undone without a backup file.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearTxConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonBorder.let {
                                ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson)
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Txns", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showResetAllConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Edit Display Name Dialog
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = MatteCharcoal,
            title = { Text("Display Name", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Your Name (Optional)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUserName(tempName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Clear Transactions Only Confirm Dialog (requires typing "DELETE")
    if (showClearTxConfirmDialog) {
        var inputConfirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showClearTxConfirmDialog = false },
            containerColor = MatteCharcoal,
            title = { Text("Clear Transactions Only?", fontWeight = FontWeight.Bold, color = NeonCrimson) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will permanently delete all transactions, split items, and pending review items. Accounts will revert to their opening balances. Categories, accounts, rules, and settings will NOT be deleted.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Type DELETE to confirm:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = NeonAmber
                    )
                    OutlinedTextField(
                        value = inputConfirm,
                        onValueChange = { inputConfirm = it },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearTransactionsOnly()
                        showClearTxConfirmDialog = false
                    },
                    enabled = inputConfirm.trim() == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary)
                ) {
                    Text("Delete Transactions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTxConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Reset Everything Confirm Dialog (requires typing "DELETE")
    if (showResetAllConfirmDialog) {
        var inputConfirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showResetAllConfirmDialog = false },
            containerColor = MatteCharcoal,
            title = { Text("Reset Entire App to Defaults?", fontWeight = FontWeight.Bold, color = NeonCrimson) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will permanently wipe ALL transactions, accounts, rules, categories, persons, and reset all preferences back to factory defaults. The app will restart into onboarding.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Type DELETE to confirm total wipe:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = NeonAmber
                    )
                    OutlinedTextField(
                        value = inputConfirm,
                        onValueChange = { inputConfirm = it },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetEverything()
                        showResetAllConfirmDialog = false
                    },
                    enabled = inputConfirm.trim() == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary)
                ) {
                    Text("Total Wipe & Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Sync & Data Pushed Sub-Screen
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncAndDataSubScreen(
    viewModel: TransactionViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val savedEmail by viewModel.gmailSyncEmail.collectAsStateWithLifecycle()
    val savedPassword by viewModel.gmailSyncAppPassword.collectAsStateWithLifecycle()
    val syncGmailOnOpen by viewModel.syncGmailOnOpen.collectAsStateWithLifecycle()
    val scanAllSenders by viewModel.scanAllSenders.collectAsStateWithLifecycle()
    val customBankSenders by viewModel.customBankSenders.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isGmailSyncing by viewModel.isGmailSyncing.collectAsStateWithLifecycle()
    val isScanningSms by viewModel.isScanning.collectAsStateWithLifecycle()
    val gmailLastSync by viewModel.gmailLastSyncTimestamp.collectAsStateWithLifecycle()
    val smsLastSync by viewModel.lastSmsSyncTimestamp.collectAsStateWithLifecycle()
    val syncStats by viewModel.gmailSyncStats.collectAsStateWithLifecycle()
    val keystoreDecryptionFailed by viewModel.keystoreDecryptionFailed.collectAsStateWithLifecycle()

    var emailInput by remember(savedEmail) { mutableStateOf(savedEmail ?: "") }
    var passwordInput by remember(savedPassword) { mutableStateOf(savedPassword ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    var testConnectionStatus by remember { mutableStateOf<String?>(null) }
    var testConnectionSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    var newSenderInput by remember { mutableStateOf("") }
    var showGmailDateDialog by remember { mutableStateOf(false) }
    var showSmsDateDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // Subscreen Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SETTINGS / SYNC",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(
                        text = "Sync & Data Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        if (keystoreDecryptionFailed) {
            item {
                Surface(
                    color = NeonCrimson.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gmail App Password could not be decrypted (Keystore key changed after app update/reinstall). Please re-enter your 16-character App Password below.",
                            fontSize = 12.sp,
                            color = NeonCrimson,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Section 1: Gmail Account Setup
        item {
            FoldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GMAIL IMAP ACCOUNT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Gmail Address", color = TextSecondary) },
                        placeholder = { Text("e.g. you@gmail.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("16-character Google App Password", color = TextSecondary) },
                        placeholder = { Text("xxxx xxxx xxxx xxxx") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )

                    // Test Connection Feedback
                    testConnectionStatus?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (testConnectionSuccess == true) NeonEmerald else NeonCrimson
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                    isTestingConnection = true
                                    testConnectionStatus = "Connecting..."
                                    scope.launch {
                                        val res = viewModel.testGmailConnection(emailInput.trim(), passwordInput.trim())
                                        isTestingConnection = false
                                        res.onSuccess {
                                            testConnectionSuccess = true
                                            testConnectionStatus = "✓ Connected to Gmail IMAP successfully!"
                                        }.onFailure { err ->
                                            testConnectionSuccess = false
                                            testConnectionStatus = "✗ Connection failed: ${err.message}"
                                        }
                                    }
                                }
                            },
                            enabled = !isTestingConnection && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isTestingConnection) "Testing..." else "Test Connection", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.saveGmailCredentials(emailInput.trim(), passwordInput.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 2: Sync Toggles
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYNC BEHAVIOR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync on app open", fontSize = 14.sp, color = TextPrimary)
                            Text("Silently fetch new bank messages when app opens", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = syncGmailOnOpen,
                            onCheckedChange = { viewModel.setSyncGmailOnOpen(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OledBlack, checkedTrackColor = NeonCyan)
                        )
                    }

                    Divider(color = BorderCharcoal, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scan all senders (Relaxed filter)", fontSize = 14.sp, color = TextPrimary)
                            Text("Scan messages even if sender does not match official bank domains", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = scanAllSenders,
                            onCheckedChange = { viewModel.setScanAllSenders(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OledBlack, checkedTrackColor = NeonAmber)
                        )
                    }
                }
            }
        }

        // Section 3: Bank Email Senders List
        item {
            FoldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BANK EMAIL SENDERS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "Standard RBI domains (.bank.in, .fin.in) and official addresses are matched automatically. Add custom bank sender emails below:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    // Chips of senders
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customBankSenders.forEach { sender ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(sender, fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.removeCustomBankSender(sender) }
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = InputBg,
                                    labelColor = NeonCyan,
                                    trailingIconColor = TextSecondary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal)
                            )
                        }
                    }

                    // Add new sender row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSenderInput,
                            onValueChange = { newSenderInput = it },
                            placeholder = { Text("e.g. alerts@mybank.com", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderCharcoal
                            )
                        )
                        Button(
                            onClick = {
                                if (newSenderInput.isNotBlank()) {
                                    viewModel.addCustomBankSender(newSenderInput.trim())
                                    newSenderInput = ""
                                }
                            },
                            enabled = newSenderInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
                        ) {
                            Text("+ Add")
                        }
                    }
                }
            }
        }

        // Section 4: Gmail Sync Triggers
        item {
            FoldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GMAIL SYNC EXECUTION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    val gmailStatusText = if (gmailLastSync > 0L) {
                        "Last synced: ${dateFormatter.format(Date(gmailLastSync))}"
                    } else "Never synced"
                    Text(gmailStatusText, fontSize = 12.sp, color = TextSecondary)

                    // Last Sync Panel
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LAST SYNC METRICS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                if (syncStats != null) {
                                    Text(
                                        text = if (syncStats?.lastError == null) "COMPLETED" else "FAILED",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (syncStats?.lastError == null) NeonEmerald else NeonCrimson
                                    )
                                }
                            }

                            if (syncStats != null) {
                                val s = syncStats!!
                                val timeText = if (s.timestamp > 0L) dateFormatter.format(Date(s.timestamp)) else "N/A"
                                Text("Time: $timeText", fontSize = 11.sp, color = TextPrimary)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Fetched", fontSize = 10.sp, color = TextSecondary)
                                        Text("${s.emailsFetched}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Filtered", fontSize = 10.sp, color = TextSecondary)
                                        Text("${s.filteredOut}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Parsed", fontSize = 10.sp, color = TextSecondary)
                                        Text("${s.parsed}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Review", fontSize = 10.sp, color = TextSecondary)
                                        Text("${s.sentToReview}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Saved", fontSize = 10.sp, color = TextSecondary)
                                        Text("${s.saved}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                                    }
                                }

                                if (!s.lastError.isNullOrBlank()) {
                                    Text(
                                        text = "Last error: ${s.lastError}",
                                        fontSize = 11.sp,
                                        color = NeonCrimson,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "Last error: None",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            } else {
                                Text(
                                    text = "No sync has been recorded yet.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showGmailDateDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync from date", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val email = savedEmail
                                val pass = savedPassword
                                if (!email.isNullOrBlank() && !pass.isNullOrBlank()) {
                                    viewModel.syncAxisFromGmail(email, pass, null)
                                }
                            },
                            enabled = !isGmailSyncing && !savedEmail.isNullOrBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isGmailSyncing) "Syncing..." else "Sync Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 5: SMS Sync Triggers
        item {
            FoldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SMS SYNC EXECUTION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    val smsStatusText = if (smsLastSync > 0L) {
                        "Last scanned: ${dateFormatter.format(Date(smsLastSync))}"
                    } else "Never scanned"
                    Text(smsStatusText, fontSize = 12.sp, color = TextSecondary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSmsDateDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync SMS from date", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.rescanSms() },
                            enabled = !isScanningSms,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isScanningSms) "Scanning..." else "Sync Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showGmailDateDialog) {
        SyncFromDateDialog(
            isScanning = isGmailSyncing,
            onDismiss = { showGmailDateDialog = false },
            onConfirmSync = { startDateMillis ->
                val email = savedEmail
                val pass = savedPassword
                if (!email.isNullOrBlank() && !pass.isNullOrBlank()) {
                    viewModel.syncAxisFromGmail(email, pass, startDateMillis)
                }
                showGmailDateDialog = false
            }
        )
    }

    if (showSmsDateDialog) {
        SyncFromDateDialog(
            isScanning = isScanningSms,
            onDismiss = { showSmsDateDialog = false },
            onConfirmSync = { startDateMillis ->
                viewModel.scanInboxFromDate(startDateMillis)
                showSmsDateDialog = false
            }
        )
    }
}

/**
 * Backup & Restore Sub-Screen
 */
@Composable
fun BackupSubScreen(
    viewModel: TransactionViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(context, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            showImportConfirmDialog = uri
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SETTINGS / BACKUP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                    Text(
                        text = "Backup & Restore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        item {
            FoldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "LOCAL JSON BACKUP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "Export all transactions, account anchors, categories, vendors, split items, and rules to a readable JSON file. CampusFold runs 100% offline and your data never leaves your device.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            exportLauncher.launch("CampusFold_Backup_$timeStamp.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export JSON Backup", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore from JSON Backup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showImportConfirmDialog != null) {
        val uri = showImportConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = null },
            containerColor = MatteCharcoal,
            title = { Text("Confirm Backup Restore", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    text = "This will restore transactions, split items, accounts, categories, and rules from the selected backup file. Existing records will be merged safely.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackup(context, uri)
                        showImportConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
                ) {
                    Text("Proceed Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
