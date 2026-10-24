package com.campusfold.tracker.ui.screens

import android.app.DatePickerDialog
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
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitDirection
import com.campusfold.tracker.data.model.PersonWithBalance
import com.campusfold.tracker.data.model.RepaymentResult
import com.campusfold.tracker.data.model.UnsettledBillItem
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SplitsScreen(
    viewModel: TransactionViewModel,
    onAddFriendPaidExpense: () -> Unit = {}
) {
    val personsWithBalances by viewModel.personsWithBalances.collectAsStateWithLifecycle()
    val allUnsettledBills by viewModel.allUnsettledBills.collectAsStateWithLifecycle()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsStateWithLifecycle()
    val unallocatedCredits by viewModel.unallocatedHiddenSenderCredits.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    val totalOwedToMe = personsWithBalances.sumOf { it.totalOwedToMePaise } / 100.0
    val totalOwedByMe = personsWithBalances.sumOf { it.totalOwedByMePaise } / 100.0

    // Dialog states
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friendToEdit by remember { mutableStateOf<PersonEntity?>(null) }
    var friendToReceiveFrom by remember { mutableStateOf<PersonWithBalance?>(null) }
    var friendToRepay by remember { mutableStateOf<PersonWithBalance?>(null) }
    var overpaymentPrompt by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var repaymentFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(repaymentFeedbackMessage) {
        repaymentFeedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            repaymentFeedbackMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = OledBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        text = "Friend Balances & Debts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onAddFriendPaidExpense,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald.copy(alpha = 0.2f),
                            contentColor = NeonEmerald
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Friend Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showAddFriendDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Friend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1-Tap Pending Settlement Confirmation Banners (both OWED_TO_ME and OWED_BY_ME)
            if (pendingSuggestions.isNotEmpty()) {
                pendingSuggestions.forEach { suggestion ->
                    val friend = personsWithBalances.find { it.id == suggestion.suggestedPersonId }
                    val friendName = friend?.name ?: "Friend"
                    val isRepayment = suggestion.direction == SplitDirection.OWED_BY_ME

                    FoldCard(
                        containerColor = if (isRepayment) Color(0xFF261A0F) else Color(0xFF0F261C),
                        borderColor = (if (isRepayment) NeonAmber else NeonEmerald).copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = if (isRepayment) NeonAmber else NeonEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRepayment) "OUTGOING REPAYMENT SUGGESTION" else "SMART CREDIT SETTLEMENT MATCH",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isRepayment) NeonAmber else NeonEmerald
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val messageText = if (isRepayment) {
                                "Paid ₹${suggestion.amount} to '${suggestion.detectedSenderName}'. Mark debt to $friendName as repaid?"
                            } else {
                                val pendingBalance = friend?.totalOwedToMe ?: 0.0
                                "Received ₹${suggestion.amount} from '${suggestion.detectedSenderName}'. Apply to $friendName's ₹${pendingBalance.toInt()} balance?"
                            }
                            Text(
                                text = messageText,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.confirmAndSaveAlias(suggestion.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRepayment) NeonAmber else NeonEmerald,
                                        contentColor = OledBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.dismissSettlementSuggestion(suggestion.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(BorderCharcoal)
                                    )
                                ) {
                                    Text("Decline", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Unallocated Hidden Sender Gmail Credits Banner
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
                                    text = "BANK GMAIL CREDIT (SENDER HIDDEN)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${credit.amount.toInt()} credited via Bank Gmail. Apply to a friend?",
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

            // Summary Card (Bidirectional Debt Summary)
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DEBT & BALANCE SUMMARY",
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
                            Text(
                                text = "Owed to you",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            MonospaceCurrencyText(
                                amount = totalOwedToMe,
                                fontSize = 20.sp,
                                color = if (totalOwedToMe > 0) NeonAmber else TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(BorderCharcoal)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "You owe",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            MonospaceCurrencyText(
                                amount = totalOwedByMe,
                                fontSize = 20.sp,
                                color = if (totalOwedByMe > 0) NeonCrimson else TextMuted
                            )
                        }
                    }

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
                            text = "${allUnsettledBills.size} open split items",
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
                            onTheyPaidMe = { friendToReceiveFrom = personWithBal },
                            onIPaidThem = { friendToRepay = personWithBal },
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

    // "They Paid Me" Settlement Dialog
    friendToReceiveFrom?.let { friend ->
        TheyPaidMeDialog(
            friend = friend,
            onDismiss = { friendToReceiveFrom = null },
            onConfirmSettle = { amount ->
                viewModel.settleAmountForPerson(
                    personId = friend.id,
                    amount = amount,
                    onExcess = { excess, friendName ->
                        overpaymentPrompt = Pair(excess, friendName)
                    }
                )
                friendToReceiveFrom = null
            }
        )
    }

    // "I Paid Them" Repayment Dialog
    friendToRepay?.let { friend ->
        RepayFriendDebtDialog(
            friend = friend,
            accounts = accounts,
            onDismiss = { friendToRepay = null },
            onConfirmRepay = { accountId, amount, date ->
                viewModel.repayFriendDebt(
                    personId = friend.id,
                    accountId = accountId,
                    amountToPayPaise = (amount * 100).toLong(),
                    timestamp = date
                ) { result ->
                    repaymentFeedbackMessage = result.message
                }
                friendToRepay = null
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
                if (friendToEdit != null) {
                    viewModel.updatePerson(
                        PersonEntity(
                            id = friendToEdit!!.id,
                            name = name,
                            exactSmsAliases = aliases
                        )
                    )
                } else {
                    viewModel.createPerson(name, aliases)
                }
                showAddFriendDialog = false
                friendToEdit = null
            }
        )
    }
}

/**
 * Friend Card with Net Balance & Expandable Bills in both directions
 */
@Composable
fun FriendBalanceCard(
    personWithBalance: PersonWithBalance,
    unsettledBills: List<UnsettledBillItem>,
    onTheyPaidMe: () -> Unit,
    onIPaidThem: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val netPaise = personWithBalance.netBalancePaise
    val theyOweMe = personWithBalance.totalOwedToMePaise > 0L
    val iOweThem = personWithBalance.totalOwedByMePaise > 0L

    FoldCard {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar, Name, Aliases and Net Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BorderCharcoal),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = personWithBalance.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = NeonCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = personWithBalance.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        val subtitle = if (theyOweMe && iOweThem) {
                            "They owe ₹${(personWithBalance.totalOwedToMePaise / 100.0).toInt()} • You owe ₹${(personWithBalance.totalOwedByMePaise / 100.0).toInt()}"
                        } else if (personWithBalance.exactSmsAliases.isNotBlank()) {
                            "Alias: ${personWithBalance.exactSmsAliases}"
                        } else {
                            "No SMS alias configured"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Net Balance Display
                Column(horizontalAlignment = Alignment.End) {
                    val (netText, netColor) = when {
                        netPaise > 0L -> Pair("They owe you", NeonEmerald)
                        netPaise < 0L -> Pair("You owe them", NeonCrimson)
                        else -> Pair("Settled", TextSecondary)
                    }
                    MonospaceCurrencyText(
                        amount = Math.abs(netPaise) / 100.0,
                        fontSize = 16.sp,
                        color = netColor
                    )
                    Text(
                        text = netText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = netColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderCharcoal, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action row & expander
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Text(
                        text = "${unsettledBills.size} Bills",
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

                // Actions: They Paid Me / I Paid Them / Edit / Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (theyOweMe) {
                        Button(
                            onClick = onTheyPaidMe,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("They paid me", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (iOweThem) {
                        Button(
                            onClick = onIPaidThem,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCrimson.copy(alpha = 0.2f),
                                contentColor = NeonCrimson
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f))
                        ) {
                            Text("I paid them", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
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
                    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
                    unsettledBills.forEach { bill ->
                        val dateFormatted = dateFormat.format(Date(bill.createdAt))
                        val isPartial = bill.amountPaidSoFar > 0.0
                        val remaining = bill.amountOwed - bill.amountPaidSoFar
                        val isOwedByMe = bill.direction == SplitDirection.OWED_BY_ME

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
                                    text = if (isOwedByMe) "${bill.vendorName} (Friend paid)" else bill.vendorName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$dateFormatted • Total: ₹${bill.transactionTotal.toInt()}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                MonospaceCurrencyText(
                                    amount = remaining,
                                    fontSize = 12.sp,
                                    color = if (isOwedByMe) NeonCrimson else NeonAmber
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
                                        text = if (isOwedByMe) "You owe" else "Owed to you",
                                        bgColor = if (isOwedByMe) PillBgExpense else PillBgTransfer,
                                        textColor = if (isOwedByMe) PillTextExpense else PillTextTransfer,
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

/**
 * "They Paid Me" dialog (incoming settlement from friend)
 */
@Composable
fun TheyPaidMeDialog(
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
                text = "THEY PAID ME: ${friend.name.uppercase()}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${friend.name} owes you ₹${friend.totalOwedToMe}. Enter amount received; it will be applied FIFO to the oldest debts.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount Received (₹)", color = TextSecondary) },
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
                Text("Confirm Received", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * "I Paid Them" Repayment Dialog (repaying debt to friend from an account)
 */
@Composable
fun RepayFriendDebtDialog(
    friend: PersonWithBalance,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirmRepay: (accountId: Long, amount: Double, date: Long) -> Unit
) {
    var amountText by remember { mutableStateOf((friend.totalOwedByMePaise / 100.0).toString()) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val calendar = remember(selectedDate) {
        Calendar.getInstance().apply { timeInMillis = selectedDate }
    }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDate = newCal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = "REPAY FRIEND: ${friend.name.uppercase()}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You owe ${friend.name} ₹${(friend.totalOwedByMePaise / 100.0)}. Select the account paid from to record the payment and settle debts FIFO.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount Repaid (₹)", color = TextSecondary) },
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
                        focusedBorderColor = NeonCrimson,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Paid From Account Chips
                Text("Paid From Account:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedAccountId == 0L,
                        onClick = { selectedAccountId = 0L },
                        label = { Text("Cash / External", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan
                        )
                    )
                    accounts.take(3).forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text("${acc.bankName} ••${acc.last4}", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            )
                        )
                    }
                }

                // Date Picker
                OutlinedCard(
                    onClick = { datePickerDialog.show() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Payment Date", fontSize = 10.sp, color = TextMuted)
                            Text(dateFormatter.format(Date(selectedDate)), fontSize = 12.sp, color = TextPrimary)
                        }
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onConfirmRepay(selectedAccountId, amount, selectedDate)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary)
            ) {
                Text("Confirm Repayment", fontWeight = FontWeight.Bold)
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
                    text = "Incoming SMS or Outgoing payments matching these aliases will auto-suggest settlements.",
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
