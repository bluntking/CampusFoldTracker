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
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.VendorMappingEntity
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorsCategoriesScreen(viewModel: TransactionViewModel) {
    val categories by viewModel.categories.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()
    val vendorMappings by viewModel.vendorMappings.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Vendors, 1: Categories

    // Dialog states
    var showAddVendorDialog by remember { mutableStateOf(false) }
    var vendorToEdit by remember { mutableStateOf<VendorMappingEntity?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var showBackupSheet by remember { mutableStateOf(false) }

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

            // Screen Header with Title on Left and ⚙ Backup Button on Right
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

                // ⚙ Backup Button in Header
                Button(
                    onClick = { showBackupSheet = true },
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
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Backup & Restore",
                        tint = NeonPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Backup",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean 2-Segment Full-Width Pill Switcher (Campus Vendors vs Categories)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MatteCharcoal)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Campus Vendors Pill
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
                            text = "Campus Vendors",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) NeonEmerald else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(
                            text = vendorMappings.size.toString(),
                            bgColor = if (selectedTab == 0) NeonEmerald.copy(alpha = 0.2f) else BorderCharcoal,
                            textColor = if (selectedTab == 0) NeonEmerald else TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                // 2. Categories Pill
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
                            text = "Categories",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) NeonCyan else TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(
                            text = categories.size.toString(),
                            bgColor = if (selectedTab == 1) NeonCyan.copy(alpha = 0.2f) else BorderCharcoal,
                            textColor = if (selectedTab == 1) NeonCyan else TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Vendors List
                    VendorsListContent(
                        mappings = vendorMappings,
                        categories = categories,
                        onEdit = {
                            vendorToEdit = it
                            showAddVendorDialog = true
                        },
                        onDelete = { viewModel.deleteVendorMapping(it) }
                    )
                }
                1 -> {
                    // Categories List
                    CategoriesListContent(
                        categories = categories,
                        spending = categorySpending,
                        onEdit = {
                            categoryToEdit = it
                            showAddCategoryDialog = true
                        },
                        onDelete = { viewModel.deleteCategory(it) }
                    )
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                if (selectedTab == 0) {
                    vendorToEdit = null
                    showAddVendorDialog = true
                } else {
                    categoryToEdit = null
                    showAddCategoryDialog = true
                }
            },
            containerColor = if (selectedTab == 0) NeonEmerald else NeonCyan,
            contentColor = OledBlack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add New")
        }
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

    // Backup & Settings Bottom Sheet
    if (showBackupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBackupSheet = false },
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
        ) {
            SettingsBackupContent(viewModel = viewModel)
        }
    }
}

@Composable
fun VendorsListContent(
    mappings: List<VendorMappingEntity>,
    categories: List<CategoryEntity>,
    onEdit: (VendorMappingEntity) -> Unit,
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

                        Row {
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

                            Row {
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
fun SettingsBackupContent(viewModel: TransactionViewModel) {
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Data Backup & Restore Card
        item {
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                text = "1-Tap Local Backup & Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Portable JSON format (Storage Access Framework)",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Safeguard your financial records offline. Exports all transactions, split shares, custom categories, campus vendor mappings, and account details into an open JSON file.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
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

        // 2. App In-Place Updates & Data Safety Engine Card
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
                                text = "Data Safety & In-Place Updates",
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
