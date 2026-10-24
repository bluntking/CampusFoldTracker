package com.campusfold.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.ReviewItemEntity
import com.campusfold.tracker.data.entity.ReviewOrigin
import com.campusfold.tracker.data.entity.RuleEntity
import com.campusfold.tracker.data.entity.RuleSource
import com.campusfold.tracker.rules.ActionType
import com.campusfold.tracker.rules.ConditionOperator
import com.campusfold.tracker.rules.LogicalOperator
import com.campusfold.tracker.rules.RuleAction
import com.campusfold.tracker.rules.RuleCondition
import com.campusfold.tracker.rules.RuleEngine
import com.campusfold.tracker.rules.TransactionField
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: TransactionViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val rules by viewModel.allRules.collectAsStateWithLifecycle()
    val reviewItems by viewModel.pendingReviewItems.collectAsStateWithLifecycle()
    val autoAcceptAi by viewModel.autoAcceptAiResults.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Rules, 1: Review Queue
    var showAddRuleDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTOMATION ENGINE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "Rules & Review Queue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }

                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MatteCharcoal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2-Segment Pill Switcher (Rules vs Review Queue)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MatteCharcoal)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Rules Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 0) CardElevated else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Rules",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) NeonEmerald else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(
                            text = rules.size.toString(),
                            bgColor = if (selectedTab == 0) NeonEmerald.copy(alpha = 0.2f) else BorderCharcoal,
                            textColor = if (selectedTab == 0) NeonEmerald else TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                // 2. Review Queue Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 1) CardElevated else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Review Queue",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) NeonPurple else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(
                            text = reviewItems.size.toString(),
                            bgColor = if (reviewItems.isNotEmpty()) NeonPurple.copy(alpha = 0.25f) else BorderCharcoal,
                            textColor = if (reviewItems.isNotEmpty()) NeonPurple else TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            when (selectedTab) {
                0 -> {
                    RulesTabContent(
                        rules = rules,
                        onToggleRule = { ruleId, enabled -> viewModel.toggleRuleEnabled(ruleId, enabled) },
                        onDeleteRule = { rule -> viewModel.deleteRule(rule) },
                        onApplyToPast = { ruleId -> viewModel.applyRuleToPastTransactions(ruleId) }
                    )
                }
                1 -> {
                    ReviewQueueTabContent(
                        reviewItems = reviewItems,
                        autoAcceptAi = autoAcceptAi,
                        onToggleAutoAccept = { enabled -> viewModel.setAutoAcceptAiResults(enabled) },
                        onAccept = { itemId -> viewModel.acceptReviewItem(itemId) },
                        onDiscard = { itemId -> viewModel.discardReviewItem(itemId) },
                        onAcceptAllHighConfidence = { viewModel.acceptAllHighConfidenceReviewItems() },
                        onDiscardAll = { viewModel.discardAllPendingReviewItems() }
                    )
                }
            }
        }

        // Floating Action Button to Add Rule (visible on Rules tab)
        if (selectedTab == 0) {
            FloatingActionButton(
                onClick = { showAddRuleDialog = true },
                containerColor = NeonEmerald,
                contentColor = OledBlack,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Rule",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showAddRuleDialog) {
            AddRuleDialog(
                categories = categories,
                onDismiss = { showAddRuleDialog = false },
                onAddRule = { newRule ->
                    viewModel.addRule(newRule)
                    showAddRuleDialog = false
                }
            )
        }
    }
}

@Composable
fun RulesTabContent(
    rules: List<RuleEntity>,
    onToggleRule: (Long, Boolean) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit,
    onApplyToPast: (Long) -> Unit
) {
    var filterSource by remember { mutableStateOf<RuleSource?>(null) }

    val filteredRules = remember(rules, filterSource) {
        if (filterSource == null) rules else rules.filter { it.source == filterSource }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Source Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterSource == null,
                onClick = { filterSource = null },
                label = { Text("All (${rules.size})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CardElevated,
                    selectedLabelColor = TextPrimary,
                    containerColor = InputBg,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filterSource == null,
                    borderColor = BorderCharcoal,
                    selectedBorderColor = NeonEmerald
                )
            )

            FilterChip(
                selected = filterSource == RuleSource.USER,
                onClick = { filterSource = RuleSource.USER },
                label = { Text("User Rules (${rules.count { it.source == RuleSource.USER }})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CardElevated,
                    selectedLabelColor = NeonEmerald,
                    containerColor = InputBg,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filterSource == RuleSource.USER,
                    borderColor = BorderCharcoal,
                    selectedBorderColor = NeonEmerald
                )
            )

            FilterChip(
                selected = filterSource == RuleSource.LEARNED,
                onClick = { filterSource = RuleSource.LEARNED },
                label = { Text("Learned (${rules.count { it.source == RuleSource.LEARNED }})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CardElevated,
                    selectedLabelColor = NeonCyan,
                    containerColor = InputBg,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filterSource == RuleSource.LEARNED,
                    borderColor = BorderCharcoal,
                    selectedBorderColor = NeonCyan
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Rules Defined",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap + to create a rule, or edit any transaction\nto generate a learned auto-categorization rule.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filteredRules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { onToggleRule(rule.id, it) },
                        onDelete = { onDeleteRule(rule) },
                        onApplyToPast = { onApplyToPast(rule.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun RuleCard(
    rule: RuleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onApplyToPast: () -> Unit
) {
    val conditions = remember(rule.conditionsJson) { RuleEngine.parseConditions(rule.conditionsJson) }
    val actions = remember(rule.actionsJson) { RuleEngine.parseActions(rule.actionsJson) }

    FoldCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = CardElevated,
        borderColor = if (rule.enabled) BorderCharcoal else BorderCharcoal.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Source Pill, Priority, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        text = rule.source.name,
                        bgColor = if (rule.source == RuleSource.USER) NeonEmerald.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.15f),
                        textColor = if (rule.source == RuleSource.USER) NeonEmerald else NeonCyan,
                        fontSize = 9.sp
                    )

                    Text(
                        text = "Priority: ${rule.priority}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OledBlack,
                        checkedTrackColor = NeonEmerald,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = InputBg
                    ),
                    modifier = Modifier.size(36.dp, 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conditions Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(InputBg)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "CONDITIONS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (conditions.isEmpty()) {
                    Text(
                        text = "Matches all incoming messages",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                } else {
                    conditions.forEachIndexed { index, cond ->
                        if (index > 0) {
                            Text(
                                text = cond.logicalOperator.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonAmber,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = cond.field.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = cond.operator.name.lowercase().replace('_', ' '),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NeonCyan
                            )
                            Text(
                                text = "\"${cond.value}\"",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(InputBg)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "ACTIONS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (actions.isEmpty()) {
                    Text(
                        text = "No action defined",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                } else {
                    actions.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val actionColor = if (action.actionType == ActionType.BLOCK) NeonCrimson else NeonEmerald
                            Text(
                                text = action.actionType.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = actionColor
                            )
                            if (action.actionType != ActionType.BLOCK) {
                                Text(
                                    text = action.field.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "\"${action.value}\"",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions: Apply to Past + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onApplyToPast,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonCyan,
                        containerColor = InputBg
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply to Past", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Rule",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewQueueTabContent(
    reviewItems: List<ReviewItemEntity>,
    autoAcceptAi: Boolean,
    onToggleAutoAccept: (Boolean) -> Unit,
    onAccept: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onAcceptAllHighConfidence: (() -> Unit)? = null,
    onDiscardAll: (() -> Unit)? = null,
    onEditItem: ((ReviewItemEntity) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Auto-Accept AI Predictions Banner
        FoldCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = CardElevated,
            borderColor = if (autoAcceptAi) NeonPurple.copy(alpha = 0.5f) else BorderCharcoal
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Auto-accept AI Predictions",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (autoAcceptAi) "AI predictions skip review queue automatically" else "Predictions queue here for 1-tap review",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = autoAcceptAi,
                    onCheckedChange = onToggleAutoAccept,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OledBlack,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = InputBg
                    ),
                    modifier = Modifier.size(36.dp, 20.dp)
                )
            }
        }

        if (reviewItems.isNotEmpty() && (onAcceptAllHighConfidence != null || onDiscardAll != null)) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAcceptAllHighConfidence != null) {
                    Button(
                        onClick = onAcceptAllHighConfidence,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald.copy(alpha = 0.2f),
                            contentColor = NeonEmerald
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Accept High-Conf", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                if (onDiscardAll != null) {
                    OutlinedButton(
                        onClick = onDiscardAll,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCrimson
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discard All", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (reviewItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Review Queue Clear",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All transactions have been categorized with high confidence.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(reviewItems, key = { it.id }) { item ->
                    ReviewItemCard(
                        item = item,
                        onAccept = { onAccept(item.id) },
                        onDiscard = { onDiscard(item.id) },
                        onEdit = onEditItem?.let { { it(item) } }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewItemCard(
    item: ReviewItemEntity,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(item.createdAt) { dateFormat.format(Date(item.createdAt)) }

    val originColor = when (item.origin) {
        ReviewOrigin.AI -> NeonPurple
        ReviewOrigin.RULES -> NeonCyan
        ReviewOrigin.MANUAL -> NeonAmber
    }

    val payloadJson = remember(item.payloadJson) {
        try { JSONObject(item.payloadJson) } catch (_: Exception) { null }
    }

    FoldCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = CardElevated,
        borderColor = BorderCharcoal
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Origin, Confidence, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(
                        text = if (item.origin == ReviewOrigin.AI) "Suggested by AI" else item.origin.name,
                        bgColor = originColor.copy(alpha = 0.15f),
                        textColor = originColor,
                        fontSize = 9.sp
                    )
                    StatusPill(
                        text = "${(item.confidence * 100).toInt()}% Confidence",
                        bgColor = InputBg,
                        textColor = TextSecondary,
                        fontSize = 9.sp
                    )
                }

                Text(
                    text = formattedDate,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason
            Text(
                text = item.reason,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            // Payload Summary Block
            if (payloadJson != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputBg)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val vendor = payloadJson.optString("vendor", payloadJson.optString("merchant", ""))
                    val category = payloadJson.optString("predictedCategory", payloadJson.optString("category", ""))
                    val bank = payloadJson.optString("bankName", "")
                    val last4 = payloadJson.optString("last4", "")

                    if (vendor.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Vendor:", fontSize = 11.sp, color = TextMuted)
                            Text(vendor, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                    if (category.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Suggested Category:", fontSize = 11.sp, color = TextMuted)
                            Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                        }
                    }
                    if (bank.isNotBlank() || last4.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Account:", fontSize = 11.sp, color = TextMuted)
                            Text("$bank ••$last4", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onEdit != null) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCyan,
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                OutlinedButton(
                    onClick = onDiscard,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextMuted,
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Discard", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = OledBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAddRule: (RuleEntity) -> Unit
) {
    var field by remember { mutableStateOf(TransactionField.MERCHANT) }
    var operator by remember { mutableStateOf(ConditionOperator.CONTAINS) }
    var conditionValue by remember { mutableStateOf("") }

    var actionType by remember { mutableStateOf(ActionType.SET) }
    var actionField by remember { mutableStateOf(TransactionField.CATEGORY) }
    var actionValue by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }
    var priorityStr by remember { mutableStateOf("0") }

    var fieldMenuExpanded by remember { mutableStateOf(false) }
    var operatorMenuExpanded by remember { mutableStateOf(false) }
    var actionTypeMenuExpanded by remember { mutableStateOf(false) }
    var actionFieldMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        FoldCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            containerColor = CardElevated,
            borderColor = BorderCharcoal
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CREATE CATEGORIZATION RULE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonEmerald
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Condition Section
                Text("Condition (IF)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Field Picker
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { fieldMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(field.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(
                            expanded = fieldMenuExpanded,
                            onDismissRequest = { fieldMenuExpanded = false },
                            modifier = Modifier.background(CardElevated)
                        ) {
                            listOf(
                                TransactionField.MERCHANT,
                                TransactionField.AMOUNT,
                                TransactionField.SMS_TEXT,
                                TransactionField.BANK_NAME,
                                TransactionField.ACCOUNT,
                                TransactionField.TYPE,
                                TransactionField.NARRATION
                            ).forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.name, fontSize = 11.sp, color = TextPrimary) },
                                    onClick = {
                                        field = f
                                        fieldMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Operator Picker
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { operatorMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(operator.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(
                            expanded = operatorMenuExpanded,
                            onDismissRequest = { operatorMenuExpanded = false },
                            modifier = Modifier.background(CardElevated)
                        ) {
                            listOf(
                                ConditionOperator.CONTAINS,
                                ConditionOperator.EQUALS,
                                ConditionOperator.STARTS_WITH,
                                ConditionOperator.ENDS_WITH,
                                ConditionOperator.GREATER_THAN,
                                ConditionOperator.LESS_THAN
                            ).forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op.name, fontSize = 11.sp, color = TextPrimary) },
                                    onClick = {
                                        operator = op
                                        operatorMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = conditionValue,
                    onValueChange = { conditionValue = it },
                    label = { Text("Value to match (e.g. Swiggy)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Section
                Text("Action (THEN)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action Type Picker
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { actionTypeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(actionType.name, fontSize = 10.sp)
                        }
                        DropdownMenu(
                            expanded = actionTypeMenuExpanded,
                            onDismissRequest = { actionTypeMenuExpanded = false },
                            modifier = Modifier.background(CardElevated)
                        ) {
                            listOf(ActionType.SET, ActionType.APPEND, ActionType.CLEAR, ActionType.BLOCK).forEach { at ->
                                DropdownMenuItem(
                                    text = { Text(at.name, fontSize = 11.sp, color = TextPrimary) },
                                    onClick = {
                                        actionType = at
                                        actionTypeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Action Target Field Picker
                    if (actionType != ActionType.BLOCK) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { actionFieldMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(actionField.name, fontSize = 10.sp)
                            }
                            DropdownMenu(
                                expanded = actionFieldMenuExpanded,
                                onDismissRequest = { actionFieldMenuExpanded = false },
                                modifier = Modifier.background(CardElevated)
                            ) {
                                listOf(TransactionField.CATEGORY, TransactionField.MERCHANT, TransactionField.ACCOUNT).forEach { af ->
                                    DropdownMenuItem(
                                        text = { Text(af.name, fontSize = 11.sp, color = TextPrimary) },
                                        onClick = {
                                            actionField = af
                                            actionFieldMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (actionType != ActionType.BLOCK && actionType != ActionType.CLEAR) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (actionField == TransactionField.CATEGORY && categories.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { categoryMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = InputBg, contentColor = TextPrimary)
                            ) {
                                Text(actionValue.ifBlank { "Select Category" }, fontSize = 12.sp, color = NeonEmerald)
                            }
                            DropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false },
                                modifier = Modifier.background(CardElevated)
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, fontSize = 11.sp, color = TextPrimary) },
                                        onClick = {
                                            actionValue = cat.name
                                            categoryMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = actionValue,
                            onValueChange = { actionValue = it },
                            label = { Text("Target value", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = InputBg,
                                unfocusedContainerColor = InputBg,
                                focusedBorderColor = NeonEmerald,
                                unfocusedBorderColor = BorderCharcoal,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priorityStr,
                    onValueChange = { priorityStr = it },
                    label = { Text("Priority (Higher runs first)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (conditionValue.isNotBlank()) {
                                val condition = RuleCondition(
                                    field = field,
                                    operator = operator,
                                    value = conditionValue.trim(),
                                    logicalOperator = LogicalOperator.AND
                                )
                                val action = RuleAction(
                                    field = actionField,
                                    actionType = actionType,
                                    value = actionValue.trim()
                                )
                                val newRule = RuleEntity(
                                    conditionsJson = RuleEngine.serializeConditions(listOf(condition)),
                                    actionsJson = RuleEngine.serializeActions(listOf(action)),
                                    priority = priorityStr.toIntOrNull() ?: 0,
                                    enabled = true,
                                    source = RuleSource.USER
                                )
                                onAddRule(newRule)
                            }
                        },
                        enabled = conditionValue.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = OledBlack
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Rule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
