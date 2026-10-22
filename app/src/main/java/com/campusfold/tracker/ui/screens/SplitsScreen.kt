package com.campusfold.tracker.ui.screens

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
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.model.PersonWithBalance
import com.campusfold.tracker.data.model.UnsettledBillItem
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SplitsScreen(viewModel: TransactionViewModel) {
    val personsWithBalances by viewModel.personsWithBalances.collectAsState()
    val allUnsettledBills by viewModel.allUnsettledBills.collectAsState()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsState()
    val unallocatedCredits by viewModel.unallocatedHiddenSenderCredits.collectAsState()

    val totalOwedToMe = personsWithBalances.sumOf { it.totalOwedToMe }

    // Dialog states
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friendToEdit by remember { mutableStateOf<PersonEntity?>(null) }

    // Manual Settle Dialog state
    var friendToSettle by remember { mutableStateOf<PersonWithBalance?>(null) }
    var overpaymentPrompt by remember { mutableStateOf<Pair<Double, String>?>(null) }

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

            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAMPUS SPLITS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "Friend Balances",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = { showAddFriendDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Friend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1-Tap Pending Settlement Confirmation Banners
            if (pendingSuggestions.isNotEmpty()) {
                pendingSuggestions.forEach { suggestion ->
                    val friend = personsWithBalances.find { it.id == suggestion.suggestedPersonId }
                    val friendName = friend?.name ?: "Friend"
                    val pendingBalance = friend?.totalOwedToMe ?: 0.0

                    FoldCard(
                        containerColor = Color(0xFF0F261C),
                        borderColor = NeonEmerald.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SMART CREDIT SETTLEMENT MATCH",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = NeonEmerald
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Received ₹${suggestion.amount} from '${suggestion.detectedSenderName}'. Apply to $friendName's ₹${pendingBalance.toInt()} balance?",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.confirmAndSaveAlias(suggestion.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("1-Tap Confirm & Save Alias", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.dismissSettlementSuggestion(suggestion.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(BorderCharcoal)
                                    )
                                ) {
                                    Text("Dismiss", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Case C: 1-Tap Unallocated Hidden Sender Gmail Credits Banner
            if (unallocatedCredits.isNotEmpty()) {
                unallocatedCredits.forEach { credit ->
                    val unsettledFriends = personsWithBalances.filter { it.totalOwedToMe > 0 }
                    FoldCard(
                        containerColor = Color(0xFF161E28),
                        borderColor = NeonCyan.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MailOutline,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AXIS GMAIL CREDIT (SENDER HIDDEN)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${credit.amount.toInt()} credited via Axis Gmail. Apply to a friend?",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            if (unsettledFriends.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select friend who owes you:", fontSize = 10.sp, color = TextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    unsettledFriends.forEach { f ->
                                        AssistChip(
                                            onClick = {
                                                viewModel.applyHiddenCreditToFriend(credit, f.id, f.name)
                                            },
                                            label = { Text("${f.name} (₹${f.totalOwedToMe.toInt()})", fontSize = 11.sp, color = NeonEmerald) },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = InputBg),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Summary Card (Fold Money Style)
            FoldCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "TOTAL NET OWED TO ME",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MonospaceCurrencyText(
                        amount = totalOwedToMe,
                        fontSize = 28.sp,
                        color = NeonAmber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = BorderCharcoal, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Strict FIFO settlement active",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                        Text(
                            text = "${allUnsettledBills.size} open bill splits",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Friends List with Item-by-item unsettled bills
            if (personsWithBalances.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No friends registered yet.", color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { showAddFriendDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
                        ) {
                            Text("+ Add First Friend")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(personsWithBalances, key = { it.id }) { personWithBal ->
                        val friendBills = allUnsettledBills.filter { it.personId == personWithBal.id }
                        FriendBalanceCard(
                            personWithBalance = personWithBal,
                            unsettledBills = friendBills,
                            onSettleCash = {
                                friendToSettle = personWithBal
                            },
                            onEdit = {
                                friendToEdit = PersonEntity(
                                    id = personWithBal.id,
                                    name = personWithBal.name,
                                    exactSmsAliases = personWithBal.exactSmsAliases
                                )
                                showAddFriendDialog = true
                            },
                            onDelete = {
                                viewModel.deletePerson(
                                    PersonEntity(
                                        id = personWithBal.id,
                                        name = personWithBal.name,
                                        exactSmsAliases = personWithBal.exactSmsAliases
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Manual Settle Cash / Custom Amount Dialog
    friendToSettle?.let { friend ->
        ManualSettleDialog(
            friend = friend,
            onDismiss = { friendToSettle = null },
            onConfirmSettle = { amount ->
                viewModel.settleAmountForPerson(
                    personId = friend.id,
                    amount = amount,
                    onExcess = { excess, friendName ->
                        overpaymentPrompt = Pair(excess, friendName)
                    }
                )
                friendToSettle = null
            }
        )
    }

    // Overpayment Handling Prompt (Log excess as income)
    overpaymentPrompt?.let { (excessAmount, friendName) ->
        AlertDialog(
            onDismissRequest = { overpaymentPrompt = null },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Excess Overpayment Detected",
                    fontWeight = FontWeight.Bold,
                    color = NeonAmber
                )
            },
            text = {
                Text(
                    text = "$friendName overpaid by ₹${excessAmount.toInt()} beyond their total unsettled balance. Would you like to log this excess as general income / advance credit?",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logOverpaymentAsIncome(excessAmount, friendName)
                        overpaymentPrompt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
                ) {
                    Text("Log as Income")
                }
            },
            dismissButton = {
                TextButton(onClick = { overpaymentPrompt = null }) {
                    Text("Ignore", color = TextSecondary)
                }
            }
        )
    }

    // Add / Edit Friend Dialog
    if (showAddFriendDialog) {
        AddEditPersonDialog(
            initialPerson = friendToEdit,
            onDismiss = {
                showAddFriendDialog = false
                friendToEdit = null
            },
            onSave = { name, aliases ->
                if (friendToEdit == null) {
                    viewModel.addPerson(name, aliases)
                } else {
                    viewModel.editPerson(friendToEdit!!.id, name, aliases)
                }
                showAddFriendDialog = false
                friendToEdit = null
            }
        )
    }
}

@Composable
fun FriendBalanceCard(
    personWithBalance: PersonWithBalance,
    unsettledBills: List<UnsettledBillItem>,
    onSettleCash: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    FoldCard {
        Column(modifier = Modifier.padding(14.dp)) {
            // Friend Info Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (personWithBalance.totalOwedToMe > 0) NeonAmber.copy(alpha = 0.15f)
                            else NeonEmerald.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (personWithBalance.totalOwedToMe > 0) NeonAmber else NeonEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = personWithBalance.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (personWithBalance.exactSmsAliases.isNotBlank()) {
                        Text(
                            text = "Aliases: ${personWithBalance.exactSmsAliases}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Balance Display
                Column(horizontalAlignment = Alignment.End) {
                    MonospaceCurrencyText(
                        amount = personWithBalance.totalOwedToMe,
                        fontSize = 16.sp,
                        color = if (personWithBalance.totalOwedToMe > 0) NeonAmber else NeonEmerald
                    )
                    Text(
                        text = if (personWithBalance.totalOwedToMe > 0) "Owed" else "Settled",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (personWithBalance.totalOwedToMe > 0) NeonAmber else NeonEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderCharcoal, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Unsettled Bills Expander
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${unsettledBills.size} Unsettled Bills",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Actions: Settle Cash / Edit / Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (personWithBalance.totalOwedToMe > 0) {
                        Button(
                            onClick = onSettleCash,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Settle Cash", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonCrimson, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Expanded Item-by-item unsettled bills
            if (expanded && unsettledBills.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                    unsettledBills.forEach { bill ->
                        val dateFormatted = dateFormat.format(Date(bill.createdAt))
                        val isPartial = bill.amountPaidSoFar > 0.0
                        val remaining = bill.amountOwed - bill.amountPaidSoFar

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputBg)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bill.vendorName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$dateFormatted • Bill Total: ₹${bill.transactionTotal.toInt()}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                MonospaceCurrencyText(
                                    amount = remaining,
                                    fontSize = 12.sp,
                                    color = NeonAmber
                                )
                                if (isPartial) {
                                    StatusPill(
                                        text = "Paid ₹${bill.amountPaidSoFar.toInt()} of ₹${bill.amountOwed.toInt()}",
                                        bgColor = Color(0xFF1E1E2C),
                                        textColor = NeonCyan,
                                        fontSize = 8.sp
                                    )
                                } else {
                                    StatusPill(
                                        text = "Unsettled",
                                        bgColor = PillBgExpense,
                                        textColor = PillTextExpense,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualSettleDialog(
    friend: PersonWithBalance,
    onDismiss: () -> Unit,
    onConfirmSettle: (amount: Double) -> Unit
) {
    var amountText by remember { mutableStateOf(friend.totalOwedToMe.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "SETTLE BALANCE: ${friend.name.uppercase()}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Total dynamically owed: ₹${friend.totalOwedToMe}. Amounts are settled in strict FIFO order (oldest split items first).",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Settlement Amount (₹)", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    ),
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
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onConfirmSettle(amount)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
            ) {
                Text("Confirm FIFO Settlement", fontWeight = FontWeight.Bold)
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
fun AddEditPersonDialog(
    initialPerson: PersonEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, aliases: String) -> Unit
) {
    var name by remember { mutableStateOf(initialPerson?.name ?: "") }
    var aliases by remember { mutableStateOf(initialPerson?.exactSmsAliases ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = if (initialPerson == null) "Add Friend for Bill Splits" else "Edit Friend Profile",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend's Full Name (e.g. 'Rahul Sharma')", color = TextSecondary) },
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
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("Exact SMS / UPI Aliases (comma-separated)", color = TextSecondary) },
                    placeholder = { Text("e.g. 'rahul@oksbi, Rahul Sharma, RAHUL'") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )
                Text(
                    text = "Incoming Axis XX2003 SMS exactly matching these aliases will auto-settle balances instantly.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), aliases.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
            ) {
                Text("Save Friend", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
