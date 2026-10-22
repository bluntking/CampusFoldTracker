package com.campusfold.tracker.ui.sheets

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
import com.campusfold.tracker.ai.AiModelStatus
import com.campusfold.tracker.ai.ChatMessage
import com.campusfold.tracker.ai.LocalAiManager
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.StatusPill
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

    val modelStatus by LocalAiManager.modelStatus.collectAsState()
    val chatMessages by LocalAiManager.chatMessages.collectAsState()
    val isGenerating by LocalAiManager.isGenerating.collectAsState()
    val isFallbackEnabled by LocalAiManager.isAiFallbackEnabled.collectAsState()

    val trueSpend by viewModel.currentMonthTotalTrueSpend.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val personsWithBalances by viewModel.personsWithBalances.collectAsState()
    val pnbLimit by viewModel.pnbCreditLimit.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // File picker launcher for loading .bin / .task model files
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val displayName = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) it.getString(nameIndex) else "local_model.bin"
                } else "local_model.bin"
            } ?: "local_model.bin"

            coroutineScope.launch {
                LocalAiManager.loadModelFromUri(context, uri, displayName)
            }
        }
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
                            text = "PennyWise AI Financial Coach",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Model Status & Selector Banner
            FoldCard(
                containerColor = CardElevated,
                borderColor = when (modelStatus) {
                    is AiModelStatus.Ready -> NeonEmerald.copy(alpha = 0.6f)
                    is AiModelStatus.Loading -> NeonAmber.copy(alpha = 0.6f)
                    is AiModelStatus.Error -> NeonCrimson.copy(alpha = 0.6f)
                    else -> BorderCharcoal
                }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            when (val status = modelStatus) {
                                is AiModelStatus.Ready -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonEmerald))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Model Ready: ${status.modelName}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonEmerald
                                        )
                                    }
                                    Text(
                                        text = "Offline MediaPipe neural inference active",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                                is AiModelStatus.Loading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = NeonAmber
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = status.message,
                                            fontSize = 11.sp,
                                            color = NeonAmber
                                        )
                                    }
                                }
                                is AiModelStatus.Error -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Model Error: ${status.message.take(40)}...",
                                            fontSize = 11.sp,
                                            color = NeonCrimson
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = "No local model weights loaded",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeonAmber
                                    )
                                    Text(
                                        text = "App uses live DB rule analysis. Tap to load Gemma/Qwen .task file.",
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                modelPickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (modelStatus is AiModelStatus.Ready) BorderCharcoal else NeonPurple,
                                contentColor = if (modelStatus is AiModelStatus.Ready) TextPrimary else OledBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (modelStatus is AiModelStatus.Ready) "Change Model" else "Select LLM (.task/.bin)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Unstructured SMS AI Fallback Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Unstructured SMS/Email Fallback",
                            fontSize = 10.sp,
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

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Prompt Chips
            val quickPrompts = listOf(
                "Where can I cut spending this week?",
                "Who owes me the most money?",
                "Can I afford dinner at Bistro tonight?",
                "Analyze chai & beverage leaks"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    pnbLimit,
                                    personsWithBalances.filter { it.totalOwedToMe > 0 }
                                        .map { it.name to it.totalOwedToMe },
                                    coroutineScope
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = promptText,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    val isUser = msg.sender == "USER"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isUser) 14.dp else 2.dp,
                                        bottomEnd = if (isUser) 2.dp else 14.dp
                                    )
                                )
                                .background(if (isUser) Color(0xFF2A2A32) else CardElevated)
                                .border(
                                    1.dp,
                                    if (isUser) BorderCharcoal else NeonPurple.copy(alpha = 0.3f),
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isUser) 14.dp else 2.dp,
                                        bottomEnd = if (isUser) 2.dp else 14.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isUser) "You" else "CampusFold AI",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) TextMuted else NeonPurple
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = msg.content,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    lineHeight = 17.sp
                                )
                            }
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
                            Text(
                                text = "AI Coach is thinking...",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Ask about campus spending...", fontSize = 12.sp, color = TextMuted) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = BorderCharcoal
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputPrompt.isNotBlank() && !isGenerating) {
                            val promptToSend = inputPrompt.trim()
                            inputPrompt = ""
                            executePrompt(
                                promptToSend,
                                trueSpend,
                                categories.map { cat ->
                                    val spent = categorySpending.find { it.categoryId == cat.id }?.totalSpent ?: 0.0
                                    cat.name to spent
                                },
                                allTransactions.groupBy { it.friendlyVendorName }
                                    .map { (v, txs) -> v to txs.sumOf { it.myActualShare } }
                                    .sortedByDescending { it.second }
                                    .take(5),
                                pnbLimit,
                                personsWithBalances.filter { it.totalOwedToMe > 0 }
                                    .map { it.name to it.totalOwedToMe },
                                coroutineScope
                            )
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputPrompt.isNotBlank() && !isGenerating) {
                            val promptToSend = inputPrompt.trim()
                            inputPrompt = ""
                            executePrompt(
                                promptToSend,
                                trueSpend,
                                categories.map { cat ->
                                    val spent = categorySpending.find { it.categoryId == cat.id }?.totalSpent ?: 0.0
                                    cat.name to spent
                                },
                                allTransactions.groupBy { it.friendlyVendorName }
                                    .map { (v, txs) -> v to txs.sumOf { it.myActualShare } }
                                    .sortedByDescending { it.second }
                                    .take(5),
                                pnbLimit,
                                personsWithBalances.filter { it.totalOwedToMe > 0 }
                                    .map { it.name to it.totalOwedToMe },
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
    pnbLimit: Double,
    unsettledFriends: List<Pair<String, Double>>,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        LocalAiManager.askAiCoach(
            userQuery = query,
            currentMonthSpend = trueSpend,
            categoryBreakdown = categoryBreakdown,
            topVendors = topVendors,
            pnbCreditLimit = pnbLimit,
            unsettledFriends = unsettledFriends
        )
    }
}
