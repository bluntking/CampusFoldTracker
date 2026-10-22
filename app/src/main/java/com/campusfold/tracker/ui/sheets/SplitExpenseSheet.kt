package com.campusfold.tracker.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*
import kotlin.math.round

enum class SplitMode {
    EQUAL,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitExpenseSheet(
    transaction: TransactionEntity,
    allPersons: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSaveSplits: (splits: List<SplitItemEntity>) -> Unit,
    onAddPersonInline: (name: String, aliases: String) -> Unit,
    onDeletePersonInline: (PersonEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var splitMode by remember { mutableStateOf(SplitMode.EQUAL) }
    var includeMyself by remember { mutableStateOf(true) }

    // Map of selected person IDs to their custom amount owed
    val selectedPersonIds = remember { mutableStateListOf<Long>() }
    val customAmounts = remember { mutableStateMapOf<Long, String>() }

    // Inline friend creation dialog state
    var showAddFriendDialog by remember { mutableStateOf(false) }

    val totalAmount = transaction.amount

    // Calculate splits
    val calculatedSplits: List<SplitItemEntity> = remember(
        splitMode,
        includeMyself,
        selectedPersonIds.toList(),
        customAmounts.toMap(),
        totalAmount
    ) {
        if (selectedPersonIds.isEmpty()) {
            emptyList()
        } else {
            when (splitMode) {
                SplitMode.EQUAL -> {
                    val count = selectedPersonIds.size + (if (includeMyself) 1 else 0)
                    val sharePerPerson = if (count > 0) round((totalAmount / count) * 100.0) / 100.0 else 0.0
                    selectedPersonIds.map { pId ->
                        SplitItemEntity(
                            transactionId = transaction.id,
                            personId = pId,
                            amountOwed = sharePerPerson,
                            amountPaidSoFar = 0.0,
                            isSettled = false,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                }
                SplitMode.CUSTOM -> {
                    selectedPersonIds.map { pId ->
                        val owed = customAmounts[pId]?.toDoubleOrNull() ?: 0.0
                        SplitItemEntity(
                            transactionId = transaction.id,
                            personId = pId,
                            amountOwed = owed,
                            amountPaidSoFar = 0.0,
                            isSettled = false,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    val friendsTotalOwed = calculatedSplits.sumOf { it.amountOwed }
    val myActualShare = (totalAmount - friendsTotalOwed).coerceAtLeast(0.0)

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SPLIT BILL WITH FRIENDS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = transaction.friendlyVendorName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
                MonospaceCurrencyText(
                    amount = totalAmount,
                    fontSize = 20.sp,
                    color = NeonCrimson
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Split Mode Switcher (Equal vs Custom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputBg)
                    .border(1.dp, BorderCharcoal, RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (splitMode == SplitMode.EQUAL) CardElevated else Color.Transparent)
                        .clickable { splitMode = SplitMode.EQUAL }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Split Equally",
                        fontSize = 12.sp,
                        fontWeight = if (splitMode == SplitMode.EQUAL) FontWeight.Bold else FontWeight.Normal,
                        color = if (splitMode == SplitMode.EQUAL) NeonEmerald else TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (splitMode == SplitMode.CUSTOM) CardElevated else Color.Transparent)
                        .clickable { splitMode = SplitMode.CUSTOM }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Custom Exact Amounts",
                        fontSize = 12.sp,
                        fontWeight = if (splitMode == SplitMode.CUSTOM) FontWeight.Bold else FontWeight.Normal,
                        color = if (splitMode == SplitMode.CUSTOM) NeonAmber else TextSecondary
                    )
                }
            }

            if (splitMode == SplitMode.EQUAL) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeMyself = !includeMyself },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeMyself,
                        onCheckedChange = { includeMyself = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonEmerald,
                            checkmarkColor = OledBlack,
                            uncheckedColor = BorderCharcoal
                        )
                    )
                    Text(
                        text = "Include myself in equal split",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Select Friends Header + Add Friend Inline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT FRIENDS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )

                TextButton(
                    onClick = { showAddFriendDialog = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Friend", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Friends Selection List
            if (allPersons.isEmpty()) {
                FoldCard(containerColor = InputBg) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No friends added yet.", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { showAddFriendDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ Add First Friend", fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allPersons.forEach { person ->
                        val isSelected = selectedPersonIds.contains(person.id)
                        FoldCard(
                            containerColor = if (isSelected) CardElevated else InputBg,
                            borderColor = if (isSelected) NeonEmerald else BorderCharcoal,
                            onClick = {
                                if (isSelected) {
                                    selectedPersonIds.remove(person.id)
                                    customAmounts.remove(person.id)
                                } else {
                                    selectedPersonIds.add(person.id)
                                    if (customAmounts[person.id].isNullOrBlank()) {
                                        customAmounts[person.id] = (totalAmount / 2).toString()
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedPersonIds.add(person.id)
                                            if (customAmounts[person.id].isNullOrBlank()) {
                                                customAmounts[person.id] = (totalAmount / 2).toString()
                                            }
                                        } else {
                                            selectedPersonIds.remove(person.id)
                                            customAmounts.remove(person.id)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonEmerald,
                                        checkmarkColor = OledBlack,
                                        uncheckedColor = BorderCharcoal
                                    )
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = person.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    if (person.exactSmsAliases.isNotBlank()) {
                                        Text(
                                            text = "Aliases: ${person.exactSmsAliases}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted,
                                            maxLines = 1
                                        )
                                    }
                                }

                                if (isSelected && splitMode == SplitMode.CUSTOM) {
                                    Row(
                                        modifier = Modifier
                                            .width(105.dp)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MatteCharcoal)
                                            .border(1.dp, NeonAmber, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "₹",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonAmber
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = customAmounts[person.id] ?: "",
                                            onValueChange = { customAmounts[person.id] = it },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = TextPrimary
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else if (isSelected && splitMode == SplitMode.EQUAL) {
                                    val owedForThisPerson = calculatedSplits.find { it.personId == person.id }?.amountOwed ?: 0.0
                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        MonospaceCurrencyText(
                                            amount = owedForThisPerson,
                                            fontSize = 13.sp,
                                            color = NeonAmber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Split Breakdown Summary Card
            FoldCard(
                containerColor = MatteCharcoal,
                borderColor = BorderCharcoal
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Friends' Total Owed:",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                        MonospaceCurrencyText(
                            amount = friendsTotalOwed,
                            fontSize = 14.sp,
                            color = NeonAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "My True Share (Expense):",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        MonospaceCurrencyText(
                            amount = myActualShare,
                            fontSize = 16.sp,
                            color = NeonEmerald
                        )
                    }

                    if (friendsTotalOwed > totalAmount) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Warning: Split total exceeds bill amount by ₹${(friendsTotalOwed - totalAmount).toInt()}",
                            fontSize = 11.sp,
                            color = NeonCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Split Action
            Button(
                onClick = {
                    onSaveSplits(calculatedSplits)
                    onDismiss()
                },
                enabled = selectedPersonIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmerald,
                    contentColor = OledBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply Bill Split (${calculatedSplits.size} Friends)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

    // Inline Add Friend Dialog
    if (showAddFriendDialog) {
        var friendName by remember { mutableStateOf("") }
        var friendAliases by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Add Friend for Bill Splitting",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = friendName,
                        onValueChange = { friendName = it },
                        label = { Text("Friend's Name (e.g. 'Rahul')", color = TextSecondary) },
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
                        value = friendAliases,
                        onValueChange = { friendAliases = it },
                        label = { Text("Exact SMS Aliases (e.g. 'rahul@oksbi, Rahul Sharma')", color = TextSecondary) },
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
                        if (friendName.isNotBlank()) {
                            onAddPersonInline(friendName.trim(), friendAliases.trim())
                            showAddFriendDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack)
                ) {
                    Text("Add Friend")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
