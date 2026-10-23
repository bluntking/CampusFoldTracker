package com.campusfold.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.sheets.GmailSyncDialog
import com.campusfold.tracker.ui.sheets.SyncFromDateDialog
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorsCategoriesScreen(viewModel: TransactionViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categorySpending by viewModel.categorySpending.collectAsStateWithLifecycle()
    val vendorMappings by viewModel.vendorMappings.collectAsStateWithLifecycle()
    val rules by viewModel.allRules.collectAsStateWithLifecycle()
    val reviewItems by viewModel.pendingReviewItems.collectAsStateWithLifecycle()
    val unparsedMessages by viewModel.unparsedMessages.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val autoAcceptAi by viewModel.autoAcceptAiResults.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Rules, 1: Review, 2: Unparsed, 3: Vendors, 4: Categories

    // Dialog states
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddVendorDialog by remember { mutableStateOf(false) }
    var vendorToEdit by remember { mutableStateOf<VendorMappingEntity?>(null) }
    var vendorToMerge by remember { mutableStateOf<VendorMappingEntity?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToMerge by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDeleteWithReassign by remember { mutableStateOf<CategoryEntity?>(null) }

    var unparsedToCreateTx by remember { mutableStateOf<UnparsedMessageEntity?>(null) }
    var showDataSyncSheet by remember { mutableStateOf(false) }

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

            // Screen Header with Title on Left and ⚙ Sync & Data Button on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MANAGE REPOSITORIES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "Campus Rules & Budgets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }

                // ⚙ Sync & Data Button in Header
                Button(
                    onClick = { showDataSyncSheet = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InputBg,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = "Sync & Data Management",
                        tint = NeonCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sync & Data",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean 5-Segment Full-Width Pill Switcher (Rules vs Review vs Unparsed vs Vendors vs Categories)
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
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Rules",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) NeonEmerald else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        StatusPill(
                            text = rules.size.toString(),
                            bgColor = if (selectedTab == 0) NeonEmerald.copy(alpha = 0.2f) else BorderCharcoal,
                            textColor = if (selectedTab == 0) NeonEmerald else TextSecondary,
                            fontSize = 8.sp
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
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Review",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) NeonPurple else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        StatusPill(
                            text = reviewItems.size.toString(),
                            bgColor = if (reviewItems.isNotEmpty()) NeonPurple.copy(alpha = 0.25f) else BorderCharcoal,
                            textColor = if (reviewItems.isNotEmpty()) NeonPurple else TextSecondary,
                            fontSize = 8.sp
                        )
                    }
                }

                // 3. Unparsed Messages Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 2) CardElevated else Color.Transparent)
                        .clickable { selectedTab = 2 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Unparsed",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 2) NeonCrimson else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        StatusPill(
                            text = unparsedMessages.size.toString(),
                            bgColor = if (unparsedMessages.isNotEmpty()) NeonCrimson.copy(alpha = 0.25f) else BorderCharcoal,
                            textColor = if (unparsedMessages.isNotEmpty()) NeonCrimson else TextSecondary,
                            fontSize = 8.sp
                        )
                    }
                }

                // 4. Campus Vendors Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 3) CardElevated else Color.Transparent)
                        .clickable { selectedTab = 3 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Vendors",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 3) NeonCyan else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        StatusPill(
                            text = vendorMappings.size.toString(),
                            bgColor = if (selectedTab == 3) NeonCyan.copy(alpha = 0.2f) else BorderCharcoal,
                            textColor = if (selectedTab == 3) NeonCyan else TextSecondary,
                            fontSize = 8.sp
                        )
                    }
                }

                // 5. Categories Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 4) CardElevated else Color.Transparent)
                        .clickable { selectedTab = 4 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Categories",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 4) NeonAmber else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        StatusPill(
                            text = categories.size.toString(),
                            bgColor = if (selectedTab == 4) NeonAmber.copy(alpha = 0.2f) else BorderCharcoal,
                            textColor = if (selectedTab == 4) NeonAmber else TextSecondary,
                            fontSize = 8.sp
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
                2 -> {
                    UnparsedMessagesTabContent(
                        messages = unparsedMessages,
                        onCreateTransaction = { msg -> unparsedToCreateTx = msg },
                        onDeleteMessage = { id -> viewModel.deleteUnparsedMessage(id) }
                    )
                }
                3 -> {
                    // Vendors List
                    VendorsListContent(
                        mappings = vendorMappings,
                        categories = categories,
                        onEdit = {
                            vendorToEdit = it
                            showAddVendorDialog = true
                        },
                        onMerge = {
                            vendorToMerge = it
                        },
                        onDelete = { viewModel.deleteVendorMapping(it) }
                    )
                }
                4 -> {
                    // Categories List
                    CategoriesListContent(
                        categories = categories,
                        spending = categorySpending,
                        onEdit = {
                            categoryToEdit = it
                            showAddCategoryDialog = true
                        },
                        onMerge = {
                            categoryToMerge = it
                        },
                        onDelete = {
                            categoryToDeleteWithReassign = it
                        }
                    )
                }
            }
        }

        // Floating Action Button
        if (selectedTab == 0 || selectedTab == 3 || selectedTab == 4) {
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddRuleDialog = true
                        3 -> {
                            vendorToEdit = null
                            showAddVendorDialog = true
                        }
                        4 -> {
                            categoryToEdit = null
                            showAddCategoryDialog = true
                        }
                    }
                },
                containerColor = when (selectedTab) {
                    0 -> NeonEmerald
                    3 -> NeonCyan
                    4 -> NeonAmber
                    else -> NeonEmerald
                },
                contentColor = OledBlack,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New")
            }
        }
    }

    // Add Rule Dialog
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

    // Add / Edit Vendor Dialog
    if (showAddVendorDialog) {
        AddEditVendorDialog(
            initialMapping = vendorToEdit,
            categories = categories,
            onDismiss = { showAddVendorDialog = false },
            onSave = { keyword, friendly, categoryId ->
                if (vendorToEdit == null) {
                    viewModel.addVendorMapping(keyword, friendly, categoryId)
                } else {
                    viewModel.editVendorMapping(vendorToEdit!!.id, keyword, friendly, categoryId)
                }
                showAddVendorDialog = false
            }
        )
    }

    // Merge Vendors Dialog
    if (vendorToMerge != null) {
        MergeVendorsDialog(
            sourceVendor = vendorToMerge!!,
            allVendors = vendorMappings,
            onDismiss = { vendorToMerge = null },
            onConfirmMerge = { targetVendorId ->
                val targetVendor = vendorMappings.find { it.id == targetVendorId }
                val targetName = targetVendor?.friendlyName ?: ""
                val targetCat = targetVendor?.categoryId
                viewModel.mergeVendors(
                    survivingVendorId = targetVendorId,
                    mergedVendorId = vendorToMerge!!.id,
                    survivingName = targetName,
                    categoryId = targetCat
                )
                vendorToMerge = null
            }
        )
    }

    // Add / Edit Category Dialog
    if (showAddCategoryDialog) {
        AddEditCategoryDialog(
            initialCategory = categoryToEdit,
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name, colorHex, budget ->
                if (categoryToEdit == null) {
                    viewModel.addCategory(name, colorHex, budget)
                } else {
                    viewModel.editCategory(categoryToEdit!!.id, name, colorHex, budget)
                }
                showAddCategoryDialog = false
            }
        )
    }

    // Merge Categories Dialog
    if (categoryToMerge != null) {
        MergeCategoriesDialog(
            sourceCategory = categoryToMerge!!,
            allCategories = categories,
            onDismiss = { categoryToMerge = null },
            onConfirmMerge = { targetCategoryId ->
                viewModel.mergeCategories(categoryToMerge!!.id, targetCategoryId)
                categoryToMerge = null
            }
        )
    }

    // Delete Category with Reassign Dialog
    if (categoryToDeleteWithReassign != null) {
        DeleteCategoryReassignDialog(
            categoryToDelete = categoryToDeleteWithReassign!!,
            survivingCategories = categories.filter { it.id != categoryToDeleteWithReassign!!.id },
            onDismiss = { categoryToDeleteWithReassign = null },
            onConfirmDelete = { reassignTargetId ->
                viewModel.deleteCategoryWithReassign(categoryToDeleteWithReassign!!.id, reassignTargetId)
                categoryToDeleteWithReassign = null
            }
        )
    }

    // Create Transaction from Unparsed Message Dialog
    if (unparsedToCreateTx != null) {
        CreateTransactionFromUnparsedDialog(
            message = unparsedToCreateTx!!,
            accounts = accounts,
            categories = categories,
            onDismiss = { unparsedToCreateTx = null },
            onSaveTransaction = { tx ->
                viewModel.addTransaction(tx)
                viewModel.deleteUnparsedMessage(unparsedToCreateTx!!.id)
                unparsedToCreateTx = null
            }
        )
    }

    // Data & Sync Bottom Sheet
    if (showDataSyncSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDataSyncSheet = false },
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
        ) {
            DataSyncSheetContent(viewModel = viewModel)
        }
    }
}

@Composable
fun VendorsListContent(
    mappings: List<VendorMappingEntity>,
    categories: List<CategoryEntity>,
    onEdit: (VendorMappingEntity) -> Unit,
    onMerge: (VendorMappingEntity) -> Unit,
    onDelete: (VendorMappingEntity) -> Unit
) {
    if (mappings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No vendor mappings yet. Tap '+' to create one.", color = TextSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(mappings, key = { it.id }) { mapping ->
                val category = categories.find { it.id == mapping.categoryId }
                FoldCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mapping.friendlyName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SMS Keyword: ",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "'${mapping.rawSmsKeyword}'",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeonAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (category != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusPill(
                                    text = category.name,
                                    bgColor = InputBg,
                                    textColor = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onMerge(mapping) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.CallMerge, contentDescription = "Merge", tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onEdit(mapping) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDelete(mapping) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoriesListContent(
    categories: List<CategoryEntity>,
    spending: List<com.campusfold.tracker.data.model.CategorySpending>,
    onEdit: (CategoryEntity) -> Unit,
    onMerge: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit
) {
    if (categories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No categories yet. Tap '+' to create one.", color = TextSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                val spent = spending.find { it.categoryId == cat.id }?.totalSpent ?: 0.0
                val progress = if (cat.monthlyBudget > 0) (spent / cat.monthlyBudget).toFloat().coerceIn(0f, 1f) else 0f
                val color = try {
                    Color(android.graphics.Color.parseColor(cat.colorHex))
                } catch (e: Exception) {
                    NeonEmerald
                }

                FoldCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onMerge(cat) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.CallMerge, contentDescription = "Merge", tint = NeonAmber, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onEdit(cat) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(cat) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Monthly Spent",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                MonospaceCurrencyText(
                                    amount = spent,
                                    fontSize = 14.sp,
                                    color = if (spent > cat.monthlyBudget && cat.monthlyBudget > 0) NeonCrimson else TextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Budget",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                MonospaceCurrencyText(
                                    amount = cat.monthlyBudget,
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (spent > cat.monthlyBudget && cat.monthlyBudget > 0) NeonCrimson else color,
                            trackColor = BorderCharcoal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditVendorDialog(
    initialMapping: VendorMappingEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (keyword: String, friendly: String, categoryId: Long?) -> Unit
) {
    var rawKeyword by remember { mutableStateOf(initialMapping?.rawSmsKeyword ?: "") }
    var friendlyName by remember { mutableStateOf(initialMapping?.friendlyName ?: "") }
    var selectedCategoryId by remember { mutableStateOf(initialMapping?.categoryId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = if (initialMapping == null) "Add Campus Vendor" else "Edit Campus Vendor",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawKeyword,
                    onValueChange = { rawKeyword = it },
                    label = { Text("Raw SMS Keyword (e.g. 'Sekar store')", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                OutlinedTextField(
                    value = friendlyName,
                    onValueChange = { friendlyName = it },
                    label = { Text("Friendly Name (e.g. 'Akka shop')", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                Text("Default Category:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawKeyword.isNotBlank() && friendlyName.isNotBlank()) {
                        onSave(rawKeyword.trim(), friendlyName.trim(), selectedCategoryId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun AddEditCategoryDialog(
    initialCategory: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String, budget: Double) -> Unit
) {
    var name by remember { mutableStateOf(initialCategory?.name ?: "") }
    var colorHex by remember { mutableStateOf(initialCategory?.colorHex ?: "#10B981") }
    var budgetText by remember { mutableStateOf(initialCategory?.monthlyBudget?.toString() ?: "3000.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = if (initialCategory == null) "Add Category" else "Edit Category",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name (e.g. 'Food')", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it },
                    label = { Text("Color Hex (e.g. '#10B981')", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Monthly Budget (₹)", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    val budget = budgetText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        onSave(name.trim(), colorHex.trim(), budget)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun DataSyncSheetContent(viewModel: TransactionViewModel) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var showGmailSyncDialog by remember { mutableStateOf(false) }
    var showSyncDateDialog by remember { mutableStateOf(false) }

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

    // Confirm Data Restore Dialog
    if (showImportConfirmDialog != null) {
        val uri = showImportConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = null },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Confirm Data Restore",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will restore transactions, categories, vendor mappings, persons, and accounts from the selected JSON backup file. Matching records will be updated safely.",
                    color = TextSecondary,
                    fontSize = 13.sp
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
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirm Start Afresh (Clear All) Dialog
    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Start Afresh (Clear All)?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NeonCrimson
                )
            },
            text = {
                Text(
                    text = "This will erase all transactions, unsettled splits, and suggestions so you start with ₹0.00 spend.\n\nYour categories, vendor mappings, and accounts will be preserved.",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTransactionsAndReset()
                        showClearAllConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear All & Start Afresh", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Direct Axis Bank Gmail IMAP Sync Dialog
    if (showGmailSyncDialog) {
        GmailSyncDialog(
            viewModel = viewModel,
            onDismiss = { showGmailSyncDialog = false }
        )
    }

    // Sync from Custom Date Dialog
    if (showSyncDateDialog) {
        SyncFromDateDialog(
            isScanning = isScanning,
            onDismiss = { showSyncDateDialog = false },
            onConfirmSync = { startDateMillis ->
                viewModel.scanInboxFromDate(startDateMillis)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "DATA & SYNC HUB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = "Sync & Data Management",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Fetch bank transactions, export offline backups, and manage app storage.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // 1. Bank & SMS Sync Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonPurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Live Bank & SMS Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Fetch historical & real-time transactions",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option A: Sync Axis Bank from Gmail
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputBg)
                            .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                            .clickable { showGmailSyncDialog = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonPurple.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sync Axis Bank from Gmail",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Direct IMAP fetch for Debits & Credits",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Button(
                            onClick = { showGmailSyncDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Sync Axis", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option B: Sync SMS from Date
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputBg)
                            .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                            .clickable { showSyncDateDialog = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sync SMS from Date",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Import bank SMS from any chosen date",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Button(
                            onClick = { showSyncDateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Sync SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Offline JSON Backup & Restore Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Portable JSON Backup & Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Storage Access Framework (SAF)",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Exports all transactions, split shares, custom categories, campus vendor mappings, and accounts into an offline JSON file.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Export Button
                        Button(
                            onClick = {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                exportLauncher.launch("CampusFold_Backup_$timestamp.json")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = OledBlack
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        // Import Button
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NeonCyan
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Danger Zone: Rebuild from Scratch & Start Afresh
        item {
            var showRebuildConfirmDialog by remember { mutableStateOf(false) }

            if (showRebuildConfirmDialog) {
                var confirmInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showRebuildConfirmDialog = false },
                    containerColor = MatteCharcoal,
                    title = {
                        Text(
                            text = "Rebuild from Scratch?",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NeonCrimson
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "This will wipe all transactions and scan history, reset account balances, and re-read all SMS and Gmail messages from the beginning, reapplying your rules.\n\nType 'REBUILD' to confirm:",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            OutlinedTextField(
                                value = confirmInput,
                                onValueChange = { confirmInput = it },
                                placeholder = { Text("REBUILD", color = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = InputBg,
                                    unfocusedContainerColor = InputBg,
                                    focusedBorderColor = NeonCrimson,
                                    unfocusedBorderColor = BorderCharcoal
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (confirmInput.trim().equals("REBUILD", ignoreCase = true)) {
                                    viewModel.rebuildFromScratch()
                                    showRebuildConfirmDialog = false
                                }
                            },
                            enabled = confirmInput.trim().equals("REBUILD", ignoreCase = true),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Rebuild Now", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRebuildConfirmDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                )
            }

            FoldCard(
                containerColor = Color(0xFF261014),
                borderColor = NeonCrimson.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCrimson.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Danger Zone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = NeonCrimson
                            )
                            Text(
                                text = "Destructive data operations",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Reset transactions to ₹0.00 spend, or rebuild the entire database from SMS and Gmail inbox history.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showClearAllConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCrimson.copy(alpha = 0.3f),
                                contentColor = NeonCrimson
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Afresh", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showRebuildConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCrimson,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rebuild All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4. Local AI Fallback Controls Card
        item {
            val aiFallbackParsing by viewModel.aiFallbackParsingEnabled.collectAsStateWithLifecycle()
            val aiFallbackAccounts by viewModel.aiFallbackAccountsEnabled.collectAsStateWithLifecycle()
            val aiFallbackVendors by viewModel.aiFallbackVendorsEnabled.collectAsStateWithLifecycle()
            val aiFallbackCategories by viewModel.aiFallbackCategoriesEnabled.collectAsStateWithLifecycle()
            val autoAcceptAi by viewModel.autoAcceptAiResults.collectAsStateWithLifecycle()

            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonPurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Local AI Engine & Fallbacks",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Fine-tune on-device AI tasks & auto-acceptance",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AiTaskSwitchRow(
                        title = "Unstructured Parser Fallback",
                        subtitle = "Use local AI when standard bank regex doesn't match",
                        checked = aiFallbackParsing,
                        onCheckedChange = { viewModel.setAiTaskEnabled("PARSE_UNSTRUCTURED", it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AiTaskSwitchRow(
                        title = "Account Resolution Fallback",
                        subtitle = "Identify accounts from unknown SMS headers via AI",
                        checked = aiFallbackAccounts,
                        onCheckedChange = { viewModel.setAiTaskEnabled("ACCOUNT_IDENTIFICATION", it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AiTaskSwitchRow(
                        title = "Vendor Resolution Fallback",
                        subtitle = "Clean raw merchant descriptions via AI",
                        checked = aiFallbackVendors,
                        onCheckedChange = { viewModel.setAiTaskEnabled("VENDOR_IS_SAME", it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AiTaskSwitchRow(
                        title = "Category Classification Fallback",
                        subtitle = "Auto-assign category when rules don't match",
                        checked = aiFallbackCategories,
                        onCheckedChange = { viewModel.setAiTaskEnabled("CATEGORIZE_VENDOR", it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AiTaskSwitchRow(
                        title = "Auto-Accept AI Results",
                        subtitle = "Directly save predictions without queuing in Review tab",
                        checked = autoAcceptAi,
                        onCheckedChange = { viewModel.setAutoAcceptAiResults(it) }
                    )
                }
            }
        }

        // 5. Scan History Card
        item {
            val scanHistory by viewModel.scanHistory.collectAsStateWithLifecycle()

            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Scan History",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Recent inbox sync and ingestion logs",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (scanHistory.isEmpty()) {
                        Text(
                            text = "No scan history recorded yet.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val historyFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            scanHistory.take(6).forEach { scan ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(InputBg)
                                        .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StatusPill(
                                                text = scan.source.uppercase(),
                                                bgColor = if (scan.source.contains("GMAIL", true)) NeonPurple.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f),
                                                textColor = if (scan.source.contains("GMAIL", true)) NeonPurple else NeonCyan,
                                                fontSize = 9.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = historyFmt.format(Date(scan.startedAt)),
                                                fontSize = 11.sp,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Read: ${scan.read}  •  Added: ${scan.added}  •  Dupes: ${scan.duplicatesSkipped}  •  Review: ${scan.sentToReview}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Data Safety Engine Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Data Safety & Architecture",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Zero data loss during APK updates",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SafetyFeatureRow(
                        title = "Permanent Signing Keystore",
                        subtitle = "Persistent RSA-2048 keystore (campusfold.jks) prevents INSTALL_FAILED_UPDATE_INCOMPATIBLE error.",
                        isActive = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SafetyFeatureRow(
                        title = "Auto-Incrementing Version Code",
                        subtitle = "Every build auto-increments versionCode based on GitHub run number for smooth in-place installs.",
                        isActive = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SafetyFeatureRow(
                        title = "Safe Room Schema Migrations",
                        subtitle = "fallbackToDestructiveMigrationOnDowngrade + MIGRATION_1_2 prevents database wipes during schema updates.",
                        isActive = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyFeatureRow(
    title: String,
    subtitle: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InputBg)
            .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (isActive) NeonEmerald else TextMuted,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun AiTaskSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InputBg)
            .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary,
                lineHeight = 13.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonPurple,
                checkedTrackColor = NeonPurple.copy(alpha = 0.4f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = MatteCharcoal
            )
        )
    }
}

@Composable
fun UnparsedMessagesTabContent(
    messages: List<UnparsedMessageEntity>,
    onCreateTransaction: (UnparsedMessageEntity) -> Unit,
    onDeleteMessage: (Long) -> Unit
) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No Unparsed Messages",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All scanned messages matched bank regex or AI fallback cleanly.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                FoldCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    text = msg.sender.ifBlank { "UNKNOWN" },
                                    bgColor = NeonCrimson.copy(alpha = 0.15f),
                                    textColor = NeonCrimson,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPill(
                                    text = msg.source.uppercase(),
                                    bgColor = BorderCharcoal,
                                    textColor = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(msg.receivedAt)),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = msg.body,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(InputBg)
                                .padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onCreateTransaction(msg) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonEmerald,
                                    contentColor = OledBlack
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Transaction", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { onDeleteMessage(msg.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTransactionFromUnparsedDialog(
    message: UnparsedMessageEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSaveTransaction: (TransactionEntity) -> Unit
) {
    // Attempt extracting a numeric amount from message body
    val initialAmount = remember(message.body) {
        val regex = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(message.body)
        match?.groupValues?.get(1)?.replace(",", "") ?: ""
    }

    var amountText by remember { mutableStateOf(initialAmount) }
    var vendorName by remember { mutableStateOf(message.sender) }
    var descriptionText by remember { mutableStateOf(message.body) }
    var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }

    var accountMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "Create Transaction",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("From sender: ", fontSize = 11.sp, color = TextMuted)
                    StatusPill(text = message.sender.ifBlank { "UNKNOWN" }, bgColor = InputBg, textColor = NeonAmber, fontSize = 10.sp)
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Vendor Name
                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Vendor / Merchant Name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Account Dropdown
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it }
                ) {
                    val currentAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "Unassigned (Cash/Manual)"
                    OutlinedTextField(
                        value = currentAccountName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassigned (Cash/Manual)") },
                            onClick = {
                                selectedAccountId = null
                                accountMenuExpanded = false
                            }
                        )
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedAccountId = acc.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    val currentCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "None (Unassigned)"
                    OutlinedTextField(
                        value = currentCategoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unassigned)") },
                            onClick = {
                                selectedCategoryId = null
                                categoryMenuExpanded = false
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Transaction Type Chips
                Text("Transaction Type:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        TransactionType.EXPENSE to "Expense",
                        TransactionType.INCOME to "Income",
                        TransactionType.OWN_TRANSFER to "Transfer"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }

                // Description (Pre-filled body)
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Description / SMS Body", color = TextSecondary) },
                    maxLines = 3,
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
                    val amountDouble = amountText.toDoubleOrNull() ?: 0.0
                    val amountPaise = (amountDouble * 100).toLong()
                    val selectedAcc = accounts.find { it.id == selectedAccountId }
                    val tx = TransactionEntity(
                        amountPaise = amountPaise,
                        myActualSharePaise = amountPaise,
                        type = selectedType,
                        paymentMode = selectedAcc?.name ?: "Cash/Manual",
                        rawMerchant = message.sender,
                        friendlyVendorName = vendorName.ifBlank { message.sender },
                        categoryId = selectedCategoryId,
                        accountId = selectedAccountId,
                        timestamp = message.receivedAt,
                        rawSms = message.body,
                        description = descriptionText.ifBlank { null },
                        source = TransactionSource.MANUAL,
                        confidence = 1.0f,
                        editedFields = "[\"amount\",\"friendlyVendorName\",\"description\",\"categoryId\",\"accountId\",\"type\"]"
                    )
                    onSaveTransaction(tx)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
            ) {
                Text("Save Transaction", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeVendorsDialog(
    sourceVendor: VendorMappingEntity,
    allVendors: List<VendorMappingEntity>,
    onDismiss: () -> Unit,
    onConfirmMerge: (targetVendorId: Long) -> Unit
) {
    val candidates = remember(allVendors, sourceVendor) {
        allVendors.filter { it.id != sourceVendor.id }
    }
    var selectedTargetId by remember { mutableStateOf(candidates.firstOrNull()?.id) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "Merge Vendor",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Merge '${sourceVendor.friendlyName}' into another vendor:",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = "All transactions and raw SMS keywords ('${sourceVendor.rawSmsKeyword}') mapped to '${sourceVendor.friendlyName}' will be reassigned to the selected surviving vendor. '${sourceVendor.friendlyName}' will then be removed.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                if (candidates.isEmpty()) {
                    Text(
                        text = "No other vendors exist to merge into. Create another vendor first.",
                        fontSize = 12.sp,
                        color = NeonAmber
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it }
                    ) {
                        val currentTarget = candidates.find { it.id == selectedTargetId }
                        OutlinedTextField(
                            value = currentTarget?.friendlyName ?: "Select target vendor",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Surviving Target Vendor", color = TextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = InputBg,
                                unfocusedContainerColor = InputBg,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderCharcoal
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            candidates.forEach { vendor ->
                                DropdownMenuItem(
                                    text = { Text("${vendor.friendlyName} (SMS: '${vendor.rawSmsKeyword}')") },
                                    onClick = {
                                        selectedTargetId = vendor.id
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetId = selectedTargetId
                    if (targetId != null) {
                        onConfirmMerge(targetId)
                    }
                },
                enabled = selectedTargetId != null,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
            ) {
                Text("Merge Vendors", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteCategoryReassignDialog(
    categoryToDelete: CategoryEntity,
    survivingCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirmDelete: (reassignTargetId: Long?) -> Unit
) {
    var selectedTargetId by remember { mutableStateOf<Long?>(survivingCategories.firstOrNull()?.id) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "Delete Category: ${categoryToDelete.name}",
                fontWeight = FontWeight.Bold,
                color = NeonCrimson
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Where should transactions in '${categoryToDelete.name}' go?",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = "All logged transactions and vendor defaults mapped to this category will be moved to the chosen category below.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it }
                ) {
                    val currentTargetName = survivingCategories.find { it.id == selectedTargetId }?.name ?: "Leave Unassigned (None)"
                    OutlinedTextField(
                        value = currentTargetName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reassign Transactions To", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonCrimson,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Leave Unassigned (None)") },
                            onClick = {
                                selectedTargetId = null
                                menuExpanded = false
                            }
                        )
                        survivingCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedTargetId = cat.id
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(selectedTargetId) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary)
            ) {
                Text("Reassign & Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeCategoriesDialog(
    sourceCategory: CategoryEntity,
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirmMerge: (targetCategoryId: Long) -> Unit
) {
    val candidates = remember(allCategories, sourceCategory) {
        allCategories.filter { it.id != sourceCategory.id }
    }
    var selectedTargetId by remember { mutableStateOf(candidates.firstOrNull()?.id) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "Merge Category",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Merge '${sourceCategory.name}' into another category:",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = "All transactions, rules, and vendor mappings currently assigned to '${sourceCategory.name}' will be moved to the selected category. '${sourceCategory.name}' will be permanently deleted.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                if (candidates.isEmpty()) {
                    Text(
                        text = "No other categories exist to merge into. Create another category first.",
                        fontSize = 12.sp,
                        color = NeonAmber
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it }
                    ) {
                        val currentTarget = candidates.find { it.id == selectedTargetId }
                        OutlinedTextField(
                            value = currentTarget?.name ?: "Select target category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Surviving Target Category", color = TextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = InputBg,
                                unfocusedContainerColor = InputBg,
                                focusedBorderColor = NeonAmber,
                                unfocusedBorderColor = BorderCharcoal
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            candidates.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedTargetId = cat.id
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetId = selectedTargetId
                    if (targetId != null) {
                        onConfirmMerge(targetId)
                    }
                },
                enabled = selectedTargetId != null,
                colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = OledBlack)
            ) {
                Text("Merge Categories", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
