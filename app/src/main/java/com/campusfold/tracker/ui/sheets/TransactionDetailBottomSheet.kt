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
import androidx.compose.ui.text.style.TextOverflow
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
    allTransactions: List<TransactionEntity> = emptyList(),
    initialSplits: List<SplitItemEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveTransaction: (updatedTx: TransactionEntity, splits: List<SplitItemEntity>) -> Unit,
    onToggleCcBill: () -> Unit,
    onLinkIncomeToExpense: ((incomeId: Long, expenseId: Long, offsetAmount: Double) -> Unit)? = null,
    onSaveVendorMapping: (rawKeyword: String, friendlyName: String, categoryId: Long?) -> Unit,
    onAddPersonInline: (name: String, aliases: String) -> Unit = { _, _ -> },
    onDeletePersonInline: (PersonEntity) -> Unit = {},
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Link Income <-> Expense state
    var showLinkIncomeExpenseDialog by remember { mutableStateOf(false) }

    // Form states
    var amountText by remember { mutableStateOf(transaction.amount.toString()) }
    var friendlyVendor by remember { mutableStateOf(transaction.friendlyVendorName) }
    var descriptionText by remember { mutableStateOf(transaction.description ?: "") }
    var selectedCategoryId by remember { mutableStateOf(transaction.categoryId) }
    var selectedType by remember { mutableStateOf(transaction.type) }
    var paymentMode by remember { mutableStateOf(transaction.paymentMode) }
    var showPaidFriendSection by remember { mutableStateOf(false) }

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

            // Map Income to Expense / Offset with Income Card
            if (onLinkIncomeToExpense != null && allTransactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                if (selectedType == TransactionType.INCOME) {
                    FoldCard(
                        containerColor = InputBg,
                        borderColor = NeonEmerald.copy(alpha = 0.5f),
                        onClick = { showLinkIncomeExpenseDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = NeonEmerald
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Map to an Expense (Reimbursement)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Friend paid back? Link to the expense you covered to reduce your share.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (selectedType == TransactionType.EXPENSE) {
                    FoldCard(
                        containerColor = InputBg,
                        borderColor = NeonCyan.copy(alpha = 0.4f),
                        onClick = { showLinkIncomeExpenseDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Offset with Income / Friend Payment",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Link an incoming transfer directly to reduce your true share of this bill.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Paid Friend for My Share Card
            if (selectedType == TransactionType.EXPENSE) {
                Spacer(modifier = Modifier.height(10.dp))
                FoldCard(
                    containerColor = if (showPaidFriendSection) Color(0xFF14241D) else InputBg,
                    borderColor = if (showPaidFriendSection) NeonEmerald else NeonAmber.copy(alpha = 0.5f),
                    onClick = { showPaidFriendSection = !showPaidFriendSection }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handshake,
                                contentDescription = null,
                                tint = if (showPaidFriendSection) NeonEmerald else NeonAmber
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Paid Friend for My Share",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Friend paid for you? Assign to friend & select true expense category.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = if (showPaidFriendSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (showPaidFriendSection) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = BorderCharcoal)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("WHICH FRIEND DID YOU PAY?", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(6.dp))

                            if (allPersons.isEmpty()) {
                                Text(
                                    text = "No friends added yet. Add friends in Splits tab to pick them.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(allPersons) { friend ->
                                        val isCurrentFriend = friendlyVendor.contains(friend.name, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrentFriend) NeonAmber.copy(alpha = 0.2f) else InputBg)
                                                .border(1.dp, if (isCurrentFriend) NeonAmber else BorderCharcoal, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    friendlyVendor = "Paid to ${friend.name} (My Share)"
                                                    if (descriptionText.isBlank()) {
                                                        descriptionText = "My share paid to ${friend.name}"
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = friend.name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isCurrentFriend) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrentFriend) NeonAmber else TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("WHAT WAS YOUR SHARE FOR?", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(categories) { cat ->
                                    val isSelected = selectedCategoryId == cat.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) NeonEmerald.copy(alpha = 0.2f) else InputBg)
                                            .border(1.dp, if (isSelected) NeonEmerald else BorderCharcoal, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedCategoryId = cat.id
                                                if (descriptionText.isBlank() || descriptionText.startsWith("My share")) {
                                                    val friendPart = friendlyVendor.removePrefix("Paid to ").removeSuffix(" (My Share)")
                                                    descriptionText = if (friendPart.isNotBlank() && friendPart != friendlyVendor) {
                                                        "My share to $friendPart for ${cat.name}"
                                                    } else {
                                                        "My share for ${cat.name}"
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
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
                        }
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

            Spacer(modifier = Modifier.height(10.dp))

            // Note / Description field
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Note / Why did you spend this? (Optional)", color = TextSecondary) },
                placeholder = { Text("e.g. Dinner with Rahul at BBQ Nation, hostel groceries", color = TextMuted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = BorderCharcoal,
                    focusedContainerColor = InputBg,
                    unfocusedContainerColor = InputBg
                ),
                minLines = 2,
                maxLines = 4,
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
                            var showPersonDropdown by remember { mutableStateOf(false) }
                            val currentPerson = allPersons.find { it.id == newSplitPersonId } ?: allPersons.first()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Friend selector dropdown (44.dp height)
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MatteCharcoal)
                                        .border(1.dp, BorderCharcoal, RoundedCornerShape(8.dp))
                                        .clickable { showPersonDropdown = true }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentPerson.name,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showPersonDropdown,
                                        onDismissRequest = { showPersonDropdown = false },
                                        modifier = Modifier.background(MatteCharcoal)
                                    ) {
                                        allPersons.forEach { person ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = person.name,
                                                        fontSize = 12.sp,
                                                        color = if (person.id == currentPerson.id) NeonAmber else TextPrimary
                                                    )
                                                },
                                                onClick = {
                                                    newSplitPersonId = person.id
                                                    showPersonDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Owed Amount Input (44.dp height, perfectly matching)
                                OutlinedTextField(
                                    value = newSplitAmountText,
                                    onValueChange = { newSplitAmountText = it },
                                    placeholder = { Text("Owed ₹", fontSize = 11.sp, color = TextMuted) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MatteCharcoal,
                                        unfocusedContainerColor = MatteCharcoal,
                                        focusedBorderColor = NeonAmber,
                                        unfocusedBorderColor = BorderCharcoal
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )

                                // Add Button (44.dp square, perfectly matching)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeonAmber)
                                        .clickable {
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
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Split",
                                        tint = OledBlack,
                                        modifier = Modifier.size(20.dp)
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
                            paymentMode = paymentMode,
                            description = descriptionText.trim().ifBlank { null }
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

    if (showLinkIncomeExpenseDialog && onLinkIncomeToExpense != null) {
        LinkIncomeExpenseDialog(
            currentTransaction = transaction,
            allTransactions = allTransactions,
            onDismiss = { showLinkIncomeExpenseDialog = false },
            onConfirmLink = { incomeId, expenseId, offsetAmount ->
                onLinkIncomeToExpense(incomeId, expenseId, offsetAmount)
                showLinkIncomeExpenseDialog = false
                onDismiss()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Map Income <-> Expense Manual Linking Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LinkIncomeExpenseDialog(
    currentTransaction: TransactionEntity,
    allTransactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onConfirmLink: (incomeId: Long, expenseId: Long, offsetAmount: Double) -> Unit
) {
    val isCurrentIncome = currentTransaction.type == TransactionType.INCOME
    val candidateType = if (isCurrentIncome) TransactionType.EXPENSE else TransactionType.INCOME

    var searchQuery by remember { mutableStateOf("") }
    val candidates = remember(allTransactions, candidateType, searchQuery) {
        allTransactions.filter { tx ->
            tx.id != currentTransaction.id &&
            tx.type == candidateType &&
            (if (isCurrentIncome) tx.myActualShare > 0.01 else true) &&
            (searchQuery.isBlank() ||
                tx.friendlyVendorName.contains(searchQuery, ignoreCase = true) ||
                tx.rawMerchant.contains(searchQuery, ignoreCase = true) ||
                tx.amount.toString().contains(searchQuery))
        }.sortedByDescending { it.timestamp }
    }

    var selectedCandidate by remember { mutableStateOf<TransactionEntity?>(candidates.firstOrNull()) }
    var offsetAmountText by remember(selectedCandidate) {
        val initialOffset = if (selectedCandidate != null) {
            if (isCurrentIncome) {
                minOf(currentTransaction.amount, selectedCandidate!!.myActualShare)
            } else {
                minOf(selectedCandidate!!.amount, currentTransaction.myActualShare)
            }
        } else currentTransaction.amount
        mutableStateOf(String.format(Locale.US, "%.2f", initialOffset))
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = if (isCurrentIncome) NeonEmerald else NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCurrentIncome) "Map to an Expense" else "Offset with Income",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isCurrentIncome) {
                        "Select the expense that this incoming payment (₹${currentTransaction.amount.toInt()}) is reimbursing. Your share of the expense will be reduced."
                    } else {
                        "Select the incoming payment that reimburses this expense (Share: ₹${currentTransaction.myActualShare.toInt()})."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search payee, vendor, or amount...", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                Text(
                    text = if (isCurrentIncome) "RECENT EXPENSES (${candidates.size})" else "RECENT INCOMES (${candidates.size})",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )

                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCurrentIncome) "No eligible expenses found." else "No eligible incoming payments found.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidates.take(25).forEach { candidate ->
                            val isSelected = selectedCandidate?.id == candidate.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) InputBg else MatteCharcoal)
                                    .border(
                                        1.dp,
                                        if (isSelected) (if (isCurrentIncome) NeonEmerald else NeonCyan) else BorderCharcoal,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedCandidate = candidate }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.friendlyVendorName.ifBlank { candidate.rawMerchant },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(candidate.timestamp))} • ${candidate.paymentMode}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        MonospaceCurrencyText(
                                            amount = candidate.amount,
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        )
                                        if (candidate.type == TransactionType.EXPENSE) {
                                            Text(
                                                text = "Share: ₹${candidate.myActualShare.toInt()}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = NeonAmber
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // If candidate is selected: Calculation Preview
                if (selectedCandidate != null) {
                    val candidate = selectedCandidate!!
                    val expenseTx = if (isCurrentIncome) candidate else currentTransaction
                    val incomeTx = if (isCurrentIncome) currentTransaction else candidate

                    val parsedOffset = offsetAmountText.toDoubleOrNull() ?: 0.0
                    val newShare = (expenseTx.myActualShare - parsedOffset).coerceAtLeast(0.0)

                    FoldCard(
                        containerColor = InputBg,
                        borderColor = BorderCharcoal
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Reimbursement Offset ₹:", fontSize = 11.sp, color = TextSecondary)
                                OutlinedTextField(
                                    value = offsetAmountText,
                                    onValueChange = { offsetAmountText = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MatteCharcoal,
                                        unfocusedContainerColor = MatteCharcoal,
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = BorderCharcoal
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                            Divider(color = BorderCharcoal)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Original Share:", fontSize = 11.sp, color = TextMuted)
                                Text("₹${expenseTx.myActualShare.toInt()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("New Share After Offset:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                                Text("₹${newShare.toInt()}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCandidate != null) {
                        val offset = offsetAmountText.toDoubleOrNull() ?: 0.0
                        if (offset > 0.0) {
                            val incomeId = if (isCurrentIncome) currentTransaction.id else selectedCandidate!!.id
                            val expenseId = if (isCurrentIncome) selectedCandidate!!.id else currentTransaction.id
                            onConfirmLink(incomeId, expenseId, offset)
                        }
                    }
                },
                enabled = selectedCandidate != null && (offsetAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentIncome) NeonEmerald else NeonCyan,
                    contentColor = OledBlack
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Link", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
