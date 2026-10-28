package com.campusfold.tracker.ui.sheets

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.ai.AiDiagnostics
import com.campusfold.tracker.ai.AiModelCatalog
import com.campusfold.tracker.ai.AiModelInfo
import com.campusfold.tracker.ai.AiModelStatus
import com.campusfold.tracker.ai.ChatMessage
import com.campusfold.tracker.ai.LocalAiManager
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.screens.DiagnosticsScreen
import com.campusfold.tracker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachSheet(
    viewModel: TransactionViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showDiagnosticsScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LocalAiManager.ensureEngineBound()
    }

    val modelStatus by LocalAiManager.modelStatus.collectAsStateWithLifecycle()
    val chatMessages by LocalAiManager.chatMessages.collectAsStateWithLifecycle()
    val isGenerating by LocalAiManager.isGenerating.collectAsStateWithLifecycle()
    val isFallbackEnabled by LocalAiManager.isAiFallbackEnabled.collectAsStateWithLifecycle()

    val trueSpend by viewModel.currentMonthTotalTrueSpend.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categorySpending by viewModel.categorySpending.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val personsWithBalances by viewModel.personsWithBalances.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val memInfo = remember { AiModelCatalog.getMemoryInfo(context) }
    val recommendedModel = remember { AiModelCatalog.recommendModel(context) }
    val canRunQwen15 = remember { AiModelCatalog.canDeviceRun(memInfo, AiModelCatalog.QWEN_1_5B) }
    val canRunGemma = remember { AiModelCatalog.canDeviceRun(memInfo, AiModelCatalog.GEMMA3_1B) }

    // File picker launcher for loading .litertlm model files
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val displayName = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) it.getString(nameIndex) else "local_model.litertlm"
                } else "local_model.litertlm"
            } ?: "local_model.litertlm"

            coroutineScope.launch {
                LocalAiManager.loadModelFromUri(context, uri, displayName)
            }
        }
    }

    if (showDiagnosticsScreen) {
        DiagnosticsScreen(onDismiss = { showDiagnosticsScreen = false })
        return
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = BorderCharcoal)
        },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Coach",
                            tint = NeonPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CAMPUSFOLD ON-DEVICE AI",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )
                        Text(
                            text = "AI Financial Coach",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showDiagnosticsScreen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostics",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Model Status & Selector Banner
            FoldCard(
                containerColor = CardElevated,
                borderColor = when (modelStatus) {
                    is AiModelStatus.Ready -> NeonEmerald.copy(alpha = 0.6f)
                    is AiModelStatus.Loading -> NeonAmber.copy(alpha = 0.6f)
                    is AiModelStatus.Stopped -> NeonCrimson.copy(alpha = 0.6f)
                    is AiModelStatus.Error -> NeonCrimson.copy(alpha = 0.6f)
                    else -> BorderCharcoal
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Section: Full-width title and subtitle
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (val status = modelStatus) {
                            is AiModelStatus.Ready -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonEmerald))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Model Ready: ${status.modelName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonEmerald
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Offline LiteRT-LM neural inference active (isolated :ai process)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            is AiModelStatus.Stopped -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI engine stopped",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCrimson
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "The AI process exited (${status.reason}). The app kept running safely.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            LocalAiManager.retry(context)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            is AiModelStatus.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = NeonAmber
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        fontSize = 12.sp,
                                        color = NeonAmber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (status.progressPercent > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { status.progressPercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = NeonAmber,
                                        trackColor = BorderCharcoal
                                    )
                                }
                            }
                            is AiModelStatus.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Model Error: ${status.message}",
                                        fontSize = 12.sp,
                                        color = NeonCrimson,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "No local model weights loaded",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val recText = recommendedModel?.displayName ?: "None (Insufficient RAM)"
                                Text(
                                    text = "App uses live DB rule analysis. Recommended for your device: $recText",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // Middle Section: Model Download & File Picker Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (modelStatus !is AiModelStatus.Ready) {
                            // Option 1: Qwen 2.5 (1.5B) - Recommended Default (if RAM sufficient)
                            if (canRunQwen15) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            LocalAiManager.downloadModel(context, AiModelCatalog.QWEN_1_5B)
                                        }
                                    },
                                    enabled = modelStatus !is AiModelStatus.Loading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonEmerald,
                                        contentColor = OledBlack
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Download Qwen 2.5 (1.5B - Recommended)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Option 2: Gemma 3 (1B IT) - Gated on HF Browser Import
                            if (canRunGemma) {
                                Button(
                                    onClick = {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(AiModelCatalog.GEMMA3_1B.hfWebUrl))
                                        context.startActivity(browserIntent)
                                        modelPickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                                    },
                                    enabled = modelStatus !is AiModelStatus.Loading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = OledBlack
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Import Gemma 3 (1B - Gated on HF)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                modelPickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                            },
                            enabled = modelStatus !is AiModelStatus.Loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (modelStatus is AiModelStatus.Ready) BorderCharcoal else NeonPurple,
                                contentColor = if (modelStatus is AiModelStatus.Ready) TextPrimary else OledBlack
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (modelStatus is AiModelStatus.Ready) Icons.Default.SwapHoriz else Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (modelStatus is AiModelStatus.Ready) "Change Model (Select .litertlm File)" else "Select Local .litertlm File",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // Bottom Section: Unstructured SMS AI Fallback Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Unstructured SMS/Email Fallback",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Switch(
                            checked = isFallbackEnabled,
                            onCheckedChange = { LocalAiManager.setAiFallbackEnabled(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonPurple,
                                checkedTrackColor = NeonPurple.copy(alpha = 0.3f),
                                uncheckedTrackColor = BorderCharcoal
                            ),
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Prompt Chips
            val quickPrompts = listOf(
                "Where can I cut spending this week?",
                "Who owes me the most money?",
                "Can I afford dinner out tonight?",
                "Analyze chai & beverage leaks"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickPrompts) { promptText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(InputBg)
                            .border(1.dp, BorderCharcoal, RoundedCornerShape(14.dp))
                            .clickable {
                                executePrompt(
                                    promptText,
                                    trueSpend,
                                    categories.map { cat ->
                                        val spent = categorySpending.find { it.categoryId == cat.id }?.totalSpent ?: 0.0
                                        cat.name to spent
                                    },
                                    allTransactions.groupBy { it.friendlyVendorName }
                                        .map { (v, txs) -> v to txs.sumOf { it.myActualShare } }
                                        .sortedByDescending { it.second }
                                        .take(5),
                                    personsWithBalances.filter { it.totalOwedToMe > 0 }
                                        .map { it.name to it.totalOwedToMe },
                                    viewModel.totalCreditCardAvailable,
                                    coroutineScope
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = promptText, fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chat Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatMessages) { msg ->
                    val isAi = msg.sender == "AI"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isAi) 2.dp else 14.dp,
                                        bottomEnd = if (isAi) 14.dp else 2.dp
                                    )
                                )
                                .background(if (isAi) CardElevated else NeonPurple.copy(alpha = 0.2f))
                                .border(
                                    1.dp,
                                    if (isAi) BorderCharcoal else NeonPurple.copy(alpha = 0.4f),
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isAi) 2.dp else 14.dp,
                                        bottomEnd = if (isAi) 14.dp else 2.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = msg.content,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = NeonPurple
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "CampusFold AI is analyzing...", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Prompt Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Ask your campus AI coach...", fontSize = 13.sp, color = TextMuted) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputPrompt.isNotBlank() && !isGenerating) {
                                val text = inputPrompt
                                inputPrompt = ""
                                executePrompt(
                                    text,
                                    trueSpend,
                                    categories.map { cat ->
                                        val spent = categorySpending.find { it.categoryId == cat.id }?.totalSpent ?: 0.0
                                        cat.name to spent
                                    },
                                    allTransactions.groupBy { it.friendlyVendorName }
                                        .map { (v, txs) -> v to txs.sumOf { it.myActualShare } }
                                        .sortedByDescending { it.second }
                                        .take(5),
                                    personsWithBalances.filter { it.totalOwedToMe > 0 }
                                        .map { it.name to it.totalOwedToMe },
                                    viewModel.totalCreditCardAvailable,
                                    coroutineScope
                                )
                            }
                        }
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputPrompt.isNotBlank() && !isGenerating) {
                            val text = inputPrompt
                            inputPrompt = ""
                            executePrompt(
                                text,
                                trueSpend,
                                categories.map { cat ->
                                    val spent = categorySpending.find { it.categoryId == cat.id }?.totalSpent ?: 0.0
                                    cat.name to spent
                                },
                                allTransactions.groupBy { it.friendlyVendorName }
                                    .map { (v, txs) -> v to txs.sumOf { it.myActualShare } }
                                    .sortedByDescending { it.second }
                                    .take(5),
                                personsWithBalances.filter { it.totalOwedToMe > 0 }
                                    .map { it.name to it.totalOwedToMe },
                                viewModel.totalCreditCardAvailable,
                                coroutineScope
                            )
                        }
                    },
                    enabled = inputPrompt.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (inputPrompt.isNotBlank() && !isGenerating) NeonPurple else BorderCharcoal)
                        .size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputPrompt.isNotBlank() && !isGenerating) OledBlack else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun executePrompt(
    query: String,
    trueSpend: Double,
    categoryBreakdown: List<Pair<String, Double>>,
    topVendors: List<Pair<String, Double>>,
    unsettledFriends: List<Pair<String, Double>>,
    creditLimit: Double,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        LocalAiManager.askAiCoach(
            userQuery = query,
            currentMonthSpend = trueSpend,
            categoryBreakdown = categoryBreakdown,
            topVendors = topVendors,
            creditLimit = creditLimit,
            unsettledFriends = unsettledFriends
        )
    }
}
