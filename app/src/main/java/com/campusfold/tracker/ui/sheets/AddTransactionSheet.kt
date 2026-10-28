package com.campusfold.tracker.ui.sheets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.theme.*
import com.campusfold.tracker.util.CurrencyUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ManualTxType {
    EXPENSE,
    INCOME,
    INVESTMENT,
    CC_BILL,
    TRANSFER
}

enum class ExpensePayer {
    I_PAID,
    FRIEND_PAID
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    viewModel: TransactionViewModel,
    preselectFriendPaid: Boolean = false,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val persons by viewModel.allPersons.collectAsState()

    var selectedType by remember { mutableStateOf(ManualTxType.EXPENSE) }
    var expensePayer by remember { mutableStateOf(if (preselectFriendPaid) ExpensePayer.FRIEND_PAID else ExpensePayer.I_PAID) }

    // Form fields
    var amountText by remember { mutableStateOf("") }
    var totalBillText by remember { mutableStateOf("") }
    var vendorText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedToAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedPersonId by remember { mutableStateOf<Long?>(null) }

    val calendar = remember { Calendar.getInstance() }
    var selectedDateMillis by remember { mutableStateOf(calendar.timeInMillis) }

    val dateTimeFormatter = remember {
        SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault())
    }

    // Inline Add Friend Dialog state
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var newFriendName by remember { mutableStateOf("") }
    var newFriendAliases by remember { mutableStateOf("") }

    // Active bank accounts & credit cards
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    val bankAccounts = remember(activeAccounts) { activeAccounts.filter { it.type != AccountType.CREDIT_CARD } }
    val creditCards = remember(activeAccounts) { activeAccounts.filter { it.type == AccountType.CREDIT_CARD } }

    fun openDateTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val yr = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH)
        val dy = cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, y, m, d ->
            val hr = cal.get(Calendar.HOUR_OF_DAY)
            val mi = cal.get(Calendar.MINUTE)
            TimePickerDialog(context, { _, h, min ->
                val newCal = Calendar.getInstance().apply {
                    set(y, m, d, h, min, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedDateMillis = newCal.timeInMillis
            }, hr, mi, false).show()
        }, yr, mo, dy).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderCharcoal) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Sheet Title
            Text(
                text = "Add Transaction",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Transaction Type Selector (4 Types)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ManualTxType.EXPENSE to "Expense",
                    ManualTxType.INCOME to "Income",
                    ManualTxType.INVESTMENT to "Investment",
                    ManualTxType.CC_BILL to "Card Bill",
                    ManualTxType.TRANSFER to "Transfer"
                ).forEach { (type, label) ->
                    val isSelected = selectedType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NeonEmerald else MatteCharcoal)
                            .border(1.dp, if (isSelected) NeonEmerald else BorderCharcoal, RoundedCornerShape(10.dp))
                            .clickable {
                                selectedType = type
                                if (type == ManualTxType.CC_BILL) {
                                    if (selectedAccountId == null) selectedAccountId = bankAccounts.firstOrNull()?.id
                                    if (selectedToAccountId == null) selectedToAccountId = creditCards.firstOrNull()?.id
                                } else if (type == ManualTxType.TRANSFER) {
                                    if (selectedAccountId == null) selectedAccountId = activeAccounts.firstOrNull()?.id
                                    if (selectedToAccountId == null) selectedToAccountId = activeAccounts.getOrNull(1)?.id
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) OledBlack else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Who Paid Segmented Control (Expense Only)
            if (selectedType == ManualTxType.EXPENSE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(InputBg)
                        .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (expensePayer == ExpensePayer.I_PAID) NeonEmerald else Color.Transparent)
                            .clickable { expensePayer = ExpensePayer.I_PAID }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "I paid",
                            fontSize = 13.sp,
                            fontWeight = if (expensePayer == ExpensePayer.I_PAID) FontWeight.Bold else FontWeight.Normal,
                            color = if (expensePayer == ExpensePayer.I_PAID) OledBlack else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (expensePayer == ExpensePayer.FRIEND_PAID) NeonEmerald else Color.Transparent)
                            .clickable { expensePayer = ExpensePayer.FRIEND_PAID }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A friend paid",
                            fontSize = 13.sp,
                            fontWeight = if (expensePayer == ExpensePayer.FRIEND_PAID) FontWeight.Bold else FontWeight.Normal,
                            color = if (expensePayer == ExpensePayer.FRIEND_PAID) OledBlack else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Friend Selector if "A friend paid"
            if (selectedType == ManualTxType.EXPENSE && expensePayer == ExpensePayer.FRIEND_PAID) {
                Text(
                    text = "WHO PAID?",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    persons.forEach { person ->
                        val isSelected = selectedPersonId == person.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPersonId = person.id },
                            label = { Text(person.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald,
                                selectedLabelColor = OledBlack
                            )
                        )
                    }

                    // "+ Add friend" inline chip
                    AssistChip(
                        onClick = { showAddFriendDialog = true },
                        label = { Text("+ Add friend", fontSize = 12.sp, color = NeonEmerald) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Amount Input Field
            val amountLabel = when {
                selectedType == ManualTxType.EXPENSE && expensePayer == ExpensePayer.FRIEND_PAID -> "My Share (₹) *"
                selectedType == ManualTxType.INCOME -> "Amount Received (₹) *"
                selectedType == ManualTxType.CC_BILL -> "Bill Amount (₹) *"
                selectedType == ManualTxType.TRANSFER -> "Transfer Amount (₹) *"
                else -> "Amount (₹) *"
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(amountLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = BorderCharcoal,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Optional Total Bill (Friend paid only)
            if (selectedType == ManualTxType.EXPENSE && expensePayer == ExpensePayer.FRIEND_PAID) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = totalBillText,
                    onValueChange = { totalBillText = it },
                    label = { Text("Total Bill (₹) (Optional note)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vendor / Merchant Field (Expense or Income)
            if (selectedType == ManualTxType.EXPENSE || selectedType == ManualTxType.INCOME) {
                OutlinedTextField(
                    value = vendorText,
                    onValueChange = { vendorText = it },
                    label = { Text(if (selectedType == ManualTxType.INCOME) "Received From / Source" else "Vendor / Merchant") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Category Selector (Expense or Income)
            if (selectedType == ManualTxType.EXPENSE || selectedType == ManualTxType.INCOME) {
                Text(
                    text = "CATEGORY",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryId == cat.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = if (isSelected) null else cat.id },
                            label = { Text(cat.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald,
                                selectedLabelColor = OledBlack
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Account Selection based on Type
            when (selectedType) {
                ManualTxType.EXPENSE, ManualTxType.INVESTMENT -> {
                    if (expensePayer == ExpensePayer.I_PAID) {
                        Text(
                            text = "PAID FROM ACCOUNT",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "Cash / No account" chip
                            FilterChip(
                                selected = selectedAccountId == null,
                                onClick = { selectedAccountId = null },
                                label = { Text("Cash / No account", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonEmerald,
                                    selectedLabelColor = OledBlack
                                )
                            )

                            activeAccounts.forEach { acc ->
                                val isSelected = selectedAccountId == acc.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedAccountId = acc.id },
                                    label = { Text("${acc.displayName} (••${acc.last4})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonEmerald,
                                        selectedLabelColor = OledBlack
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        // Friend paid hint
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonAmber.copy(alpha = 0.08f))
                                .border(1.dp, NeonAmber.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "💡 Nothing will be debited from your accounts. This records your share as spend and adds a debt you owe to the friend in Splits.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
                ManualTxType.INCOME -> {
                    Text(
                        text = "RECEIVED IN ACCOUNT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedAccountId == null,
                            onClick = { selectedAccountId = null },
                            label = { Text("Cash / Manual", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald,
                                selectedLabelColor = OledBlack
                            )
                        )

                        bankAccounts.forEach { acc ->
                            val isSelected = selectedAccountId == acc.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text("${acc.displayName} (••${acc.last4})", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonEmerald,
                                    selectedLabelColor = OledBlack
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                ManualTxType.CC_BILL -> {
                    Text(
                        text = "PAID FROM (SAVINGS)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bankAccounts.forEach { acc ->
                            val isSelected = selectedAccountId == acc.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonEmerald,
                                    selectedLabelColor = OledBlack
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "CARD PAID (CREDIT CARD)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        creditCards.forEach { card ->
                            val isSelected = selectedToAccountId == card.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedToAccountId = card.id },
                                label = { Text(card.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = OledBlack
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                ManualTxType.TRANSFER -> {
                    Text(
                        text = "FROM ACCOUNT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeAccounts.forEach { acc ->
                            val isSelected = selectedAccountId == acc.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonEmerald,
                                    selectedLabelColor = OledBlack
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "TO ACCOUNT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeAccounts.filter { it.id != selectedAccountId }.forEach { acc ->
                            val isSelected = selectedToAccountId == acc.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedToAccountId = acc.id },
                                label = { Text(acc.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = OledBlack
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Date & Time Picker Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputBg)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(12.dp))
                    .clickable { openDateTimePicker() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = dateTimeFormatter.format(Date(selectedDateMillis)),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
                Text("Change", fontSize = 12.sp, color = NeonEmerald, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Note Field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note / Description (Optional)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = BorderCharcoal,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    val amountDouble = amountText.toDoubleOrNull() ?: 0.0
                    val amountPaise = CurrencyUtils.rupeesToPaise(amountDouble)
                    if (amountPaise <= 0L) return@Button

                    if (selectedType == ManualTxType.EXPENSE && expensePayer == ExpensePayer.FRIEND_PAID) {
                        val personId = selectedPersonId ?: persons.firstOrNull()?.id ?: return@Button
                        val totalBillPaise = totalBillText.toDoubleOrNull()?.let { CurrencyUtils.rupeesToPaise(it) }

                        viewModel.addFriendPaidExpense(
                            personId = personId,
                            mySharePaise = amountPaise,
                            totalBillPaise = totalBillPaise,
                            vendor = vendorText.trim(),
                            categoryId = selectedCategoryId,
                            timestamp = selectedDateMillis,
                            note = noteText.trim()
                        )
                    } else {
                        val txType = when (selectedType) {
                            ManualTxType.EXPENSE -> TransactionType.EXPENSE
                            ManualTxType.INCOME -> TransactionType.INCOME
                            ManualTxType.INVESTMENT -> TransactionType.INVESTMENT
                            ManualTxType.CC_BILL -> TransactionType.CC_BILL_TRANSFER
                            ManualTxType.TRANSFER -> TransactionType.OWN_TRANSFER
                        }

                        viewModel.addManualTransaction(
                            amountPaise = amountPaise,
                            type = txType,
                            accountId = selectedAccountId,
                            toAccountId = selectedToAccountId,
                            vendor = vendorText.trim(),
                            categoryId = selectedCategoryId,
                            timestamp = selectedDateMillis,
                            note = noteText.trim()
                        )
                    }
                    onDismiss()
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0 &&
                        (selectedType != ManualTxType.EXPENSE || expensePayer != ExpensePayer.FRIEND_PAID || selectedPersonId != null || persons.isNotEmpty()),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmerald,
                    contentColor = OledBlack
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Transaction", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Inline Add Friend Dialog
    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text("Add Friend", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newFriendName,
                        onValueChange = { newFriendName = it },
                        label = { Text("Friend's Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFriendAliases,
                        onValueChange = { newFriendAliases = it },
                        label = { Text("SMS/UPI Aliases (Optional, comma-separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFriendName.trim()
                        if (name.isNotBlank()) {
                            scope.launch {
                                val newId = viewModel.createPersonSync(name, newFriendAliases.trim())
                                selectedPersonId = newId
                                showAddFriendDialog = false
                                newFriendName = ""
                                newFriendAliases = ""
                            }
                        }
                    },
                    enabled = newFriendName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
