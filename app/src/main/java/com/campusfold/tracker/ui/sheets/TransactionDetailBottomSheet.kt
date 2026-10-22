package com.campusfold.tracker.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailBottomSheet(
    transaction: TransactionEntity,
    categories: List<CategoryEntity>,
    vendorMappings: List<VendorMappingEntity>,
    allPersons: List<PersonEntity>,
    initialSplits: List<SplitItemEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveTransaction: (updatedTx: TransactionEntity, splits: List<SplitItemEntity>) -> Unit,
    onToggleCcBill: () -> Unit,
    onSaveVendorMapping: (rawKeyword: String, friendlyName: String, categoryId: Long?) -> Unit,
    onAddPersonInline: (name: String, aliases: String) -> Unit = { _, _ -> },
    onDeletePersonInline: (PersonEntity) -> Unit = {},
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Form states
    var amountText by remember { mutableStateOf(transaction.amount.toString()) }
    var friendlyVendor by remember { mutableStateOf(transaction.friendlyVendorName) }
    var selectedCategoryId by remember { mutableStateOf(transaction.categoryId) }
    var selectedType by remember { mutableStateOf(transaction.type) }
    var paymentMode by remember { mutableStateOf(transaction.paymentMode) }

    // Split state
    var showSplitSection by remember { mutableStateOf(false) }
    var showFullSplitSheet by remember { mutableStateOf(false) }
    val splitList = remember { mutableStateListOf<SplitItemEntity>() }
    var newSplitPersonId by remember { mutableStateOf(allPersons.firstOrNull()?.id ?: 0L) }
    var newSplitAmountText by remember { mutableStateOf("") }

    LaunchedEffect(initialSplits) {
        if (initialSplits.isNotEmpty() && splitList.isEmpty()) {
            splitList.addAll(initialSplits)
            showSplitSection = true
        }
    }

    val currentAmount = amountText.toDoubleOrNull() ?: transaction.amount
    val totalSplitOwed = splitList.sumOf { it.amountOwed }
    val mySharePreview = if (selectedType == TransactionType.EXPENSE) {
        (currentAmount - totalSplitOwed).coerceAtLeast(0.0)
    } else {
        currentAmount
    }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.timestamp))

    // Check if rawMerchant is already in vendor mappings
    val isAlreadyMapped = vendorMappings.any {
        it.rawSmsKeyword.equals(transaction.rawMerchant, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MatteCharcoal,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = BorderCharcoal)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Amount & Status Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TRANSACTION ACTIONS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MonospaceCurrencyText(
                        amount = currentAmount,
                        fontSize = 28.sp,
                        color = when (selectedType) {
                            TransactionType.CC_BILL_TRANSFER -> NeonCyan
                            TransactionType.INCOME -> NeonEmerald
                            else -> NeonCrimson
                        }
                    )
                }

                // Type status pill
                StatusPill(
                    text = when (selectedType) {
                        TransactionType.CC_BILL_TRANSFER -> "CC BILL"
                        TransactionType.INCOME -> "INCOME"
                        TransactionType.SPLIT_SETTLEMENT -> "SETTLEMENT"
                        TransactionType.EXPENSE -> "EXPENSE"
                    },
                    bgColor = when (selectedType) {
                        TransactionType.CC_BILL_TRANSFER -> PillBgCcBill
                        TransactionType.INCOME -> PillBgIncome
                        else -> PillBgExpense
                    },
                    textColor = when (selectedType) {
                        TransactionType.CC_BILL_TRANSFER -> PillTextCcBill
                        TransactionType.INCOME -> PillTextIncome
                        else -> PillTextExpense
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1-Tap Toggle CC_BILL_TRANSFER Card
            FoldCard(
                containerColor = if (selectedType == TransactionType.CC_BILL_TRANSFER) PillBgCcBill else InputBg,
                borderColor = if (selectedType == TransactionType.CC_BILL_TRANSFER) PillTextCcBill else BorderCharcoal,
                onClick = {
                    selectedType = if (selectedType == TransactionType.CC_BILL_TRANSFER) {
                        TransactionType.EXPENSE
                    } else {
                        TransactionType.CC_BILL_TRANSFER
                    }
                    onToggleCcBill()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = if (selectedType == TransactionType.CC_BILL_TRANSFER) PillTextCcBill else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedType == TransactionType.CC_BILL_TRANSFER) "Credit Card Bill Transfer (Active)" else "Toggle as CC Bill Payment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedType == TransactionType.CC_BILL_TRANSFER) PillTextCcBill else TextPrimary
                        )
                        Text(
                            text = "Excludes row from monthly spend, charts, and budget limits",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Edit Fields Section
            Text(
                text = "DETAILS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Amount field
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (₹)", color = TextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = BorderCharcoal,
                    focusedContainerColor = InputBg,
                    unfocusedContainerColor = InputBg
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Vendor Name field
            OutlinedTextField(
                value = friendlyVendor,
                onValueChange = { friendlyVendor = it },
                label = { Text("Campus Friendly Vendor Name", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = BorderCharcoal,
                    focusedContainerColor = InputBg,
                    unfocusedContainerColor = InputBg
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Save as Vendor Mapping for future SMS
            if (!isAlreadyMapped && transaction.rawMerchant.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                FoldCard(
                    containerColor = PillBgUnmapped,
                    borderColor = PillTextUnmapped.copy(alpha = 0.5f),
                    onClick = {
                        onSaveVendorMapping(transaction.rawMerchant, friendlyVendor, selectedCategoryId)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = PillTextUnmapped,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Always map '${transaction.rawMerchant}' → '$friendlyVendor' for future SMS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PillTextUnmapped
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Picker
            Text(
                text = "CATEGORY",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) NeonEmerald.copy(alpha = 0.2f) else InputBg)
                            .border(
                                1.dp,
                                if (isSelected) NeonEmerald else BorderCharcoal,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonEmerald else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Split Expense Section
            FoldCard(
                containerColor = InputBg,
                borderColor = BorderCharcoal
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSplitSection = !showSplitSection },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Split Expense with Friends",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = if (showSplitSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }

                    if (showSplitSection) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick action to launch Full Equal / Custom Split Tool
                        Button(
                            onClick = { showFullSplitSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonAmber,
                                contentColor = OledBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Equal & Custom Split Calculator",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Current split list
                        if (splitList.isNotEmpty()) {
                            splitList.forEachIndexed { index, split ->
                                val personName = allPersons.find { it.id == split.personId }?.name ?: "Friend"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = personName,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MonospaceCurrencyText(
                                            amount = split.amountOwed,
                                            fontSize = 13.sp,
                                            color = NeonAmber
                                        )
                                        IconButton(
                                            onClick = { splitList.removeAt(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = NeonCrimson,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(color = BorderCharcoal, modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Add new split
                        if (allPersons.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Friend dropdown / chip preview
                                val currentPerson = allPersons.find { it.id == newSplitPersonId } ?: allPersons.first()
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MatteCharcoal)
                                        .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = currentPerson.name,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                }

                                OutlinedTextField(
                                    value = newSplitAmountText,
                                    onValueChange = { newSplitAmountText = it },
                                    label = { Text("Owed ₹", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MatteCharcoal,
                                        unfocusedContainerColor = MatteCharcoal,
                                        focusedBorderColor = NeonAmber,
                                        unfocusedBorderColor = BorderCharcoal
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        val owed = newSplitAmountText.toDoubleOrNull() ?: 0.0
                                        if (owed > 0) {
                                            splitList.add(
                                                SplitItemEntity(
                                                    transactionId = transaction.id,
                                                    personId = currentPerson.id,
                                                    amountOwed = owed
                                                )
                                            )
                                            newSplitAmountText = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeonAmber)
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Split",
                                        tint = OledBlack
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Add friends in Persons section to split expenses.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "My True Share:",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                            MonospaceCurrencyText(
                                amount = mySharePreview,
                                fontSize = 14.sp,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Raw SMS preview info
            if (!transaction.rawSms.isNullOrBlank()) {
                Text(
                    text = "ORIGINAL BANK SMS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.rawSms,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputBg)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save & Delete Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Delete
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonCrimson
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NeonCrimson.copy(alpha = 0.5f))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }

                // Save
                Button(
                    onClick = {
                        val finalAmount = amountText.toDoubleOrNull() ?: transaction.amount
                        val updated = transaction.copy(
                            amount = finalAmount,
                            friendlyVendorName = friendlyVendor.trim(),
                            categoryId = selectedCategoryId,
                            type = selectedType,
                            paymentMode = paymentMode
                        )
                        onSaveTransaction(updated, splitList.toList())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = OledBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showFullSplitSheet) {
        SplitExpenseSheet(
            transaction = transaction.copy(amount = currentAmount),
            allPersons = allPersons,
            onDismiss = { showFullSplitSheet = false },
            onSaveSplits = { newSplits ->
                splitList.clear()
                splitList.addAll(newSplits)
                showFullSplitSheet = false
            },
            onAddPersonInline = onAddPersonInline,
            onDeletePersonInline = onDeletePersonInline
        )
    }
}
