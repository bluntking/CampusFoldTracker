package com.campusfold.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.ui.TransactionViewModel
import com.campusfold.tracker.ui.components.FoldCard
import com.campusfold.tracker.ui.components.MonospaceCurrencyText
import com.campusfold.tracker.ui.components.StatusPill
import com.campusfold.tracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: TransactionViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val pnbLimit by viewModel.pnbCreditLimit.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }

    val creditCards = accounts.filter { it.accountType == "CREDIT_CARD" }
    val bankAccounts = accounts.filter { it.accountType != "CREDIT_CARD" }

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
            Spacer(modifier = Modifier.height(12.dp))

            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAMPUSFOLD",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Accounts & Cards",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = {
                        accountToEdit = null
                        showAddAccountDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Account", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Card
            FoldCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACTIVE ACCOUNTS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${accounts.size} Total Sources",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(
                                text = "${creditCards.size} Cards",
                                bgColor = PillBgCard,
                                textColor = PillTextCard,
                                icon = Icons.Default.CreditCard,
                                fontSize = 10.sp
                            )
                            StatusPill(
                                text = "${bankAccounts.size} Bank A/c",
                                bgColor = PillBgIncome,
                                textColor = PillTextIncome,
                                icon = Icons.Default.AccountBalance,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (pnbLimit > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderCharcoal, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "PNB 8598 Avl Limit:",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            MonospaceCurrencyText(
                                amount = pnbLimit,
                                fontSize = 12.sp,
                                color = PillTextCard
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Accounts List
            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No payment accounts configured.", color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showAddAccountDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack)
                        ) {
                            Text("+ Add First Account")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        AccountItemCard(
                            account = account,
                            onEdit = {
                                accountToEdit = account
                                showAddAccountDialog = true
                            },
                            onDelete = {
                                accountToDelete = account
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Account Dialog
    if (showAddAccountDialog) {
        AddEditAccountDialog(
            account = accountToEdit,
            onDismiss = {
                showAddAccountDialog = false
                accountToEdit = null
            },
            onSave = { name, bankName, type, last4, limit, colorHex ->
                if (accountToEdit == null) {
                    viewModel.addAccount(name, bankName, type, last4, limit, colorHex)
                } else {
                    viewModel.editAccount(accountToEdit!!.id, name, bankName, type, last4, limit, colorHex)
                }
                showAddAccountDialog = false
                accountToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            containerColor = MatteCharcoal,
            title = {
                Text(
                    text = "Remove Account",
                    fontWeight = FontWeight.Bold,
                    color = NeonCrimson
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${acc.name}'? Existing transactions will retain their payment mode string.",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(acc)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = TextPrimary)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun AccountItemCard(
    account: AccountEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCreditCard = account.accountType == "CREDIT_CARD"
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex))
    } catch (e: Exception) {
        if (isCreditCard) NeonCrimson else NeonEmerald
    }

    FoldCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account Icon Box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(parsedColor.copy(alpha = 0.15f))
                    .border(1.dp, parsedColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = parsedColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = account.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = account.bankName,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    if (account.last4.isNotBlank()) {
                        Text(
                            text = "• A/c: ${account.last4}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }

                    StatusPill(
                        text = if (isCreditCard) "Credit Card" else "Savings",
                        bgColor = if (isCreditCard) PillBgCard else PillBgIncome,
                        textColor = if (isCreditCard) PillTextCard else PillTextIncome,
                        fontSize = 8.sp
                    )
                }

                if (account.creditLimitOrBalance > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isCreditCard) "Limit: " else "Balance: ",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                        MonospaceCurrencyText(
                            amount = account.creditLimitOrBalance,
                            fontSize = 11.sp,
                            color = parsedColor
                        )
                    }
                }
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Account",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Account",
                        tint = NeonCrimson,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditAccountDialog(
    account: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, bankName: String, type: String, last4: String, limit: Double, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var bankName by remember { mutableStateOf(account?.bankName ?: "") }
    var accountType by remember { mutableStateOf(account?.accountType ?: "SAVINGS") }
    var last4 by remember { mutableStateOf(account?.last4 ?: "") }
    var limitText by remember { mutableStateOf(account?.creditLimitOrBalance?.let { if (it > 0) it.toString() else "" } ?: "") }
    var selectedColor by remember { mutableStateOf(account?.colorHex ?: "#10B981") }

    val colors = listOf("#10B981", "#8B5CF6", "#F59E0B", "#06B6D4", "#3B82F6", "#EC4899", "#F43F5E", "#14B8A6")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteCharcoal,
        title = {
            Text(
                text = if (account == null) "ADD ACCOUNT" else "EDIT ACCOUNT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = accountType == "SAVINGS",
                        onClick = { accountType = "SAVINGS" },
                        label = { Text("Savings / Bank", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PillBgIncome,
                            selectedLabelColor = PillTextIncome
                        )
                    )
                    FilterChip(
                        selected = accountType == "CREDIT_CARD",
                        onClick = { accountType = "CREDIT_CARD" },
                        label = { Text("Credit Card", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PillBgCard,
                            selectedLabelColor = PillTextCard
                        )
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name (e.g. Axis Bank XX2003)", fontSize = 11.sp, color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name", fontSize = 11.sp, color = TextSecondary) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )

                    OutlinedTextField(
                        value = last4,
                        onValueChange = { last4 = it },
                        label = { Text("Last 4 digits", fontSize = 11.sp, color = TextSecondary) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = BorderCharcoal
                        )
                    )
                }

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text(if (accountType == "CREDIT_CARD") "Credit Limit (₹)" else "Starting Balance (₹ optional)", fontSize = 11.sp, color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBg,
                        unfocusedContainerColor = InputBg,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = BorderCharcoal
                    )
                )

                // Color picker
                Text("Account Color:", fontSize = 11.sp, color = TextMuted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { NeonEmerald }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == hex) 2.dp else 0.dp,
                                    color = if (selectedColor == hex) TextPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val limit = limitText.toDoubleOrNull() ?: 0.0
                        onSave(name.trim(), bankName.trim().ifEmpty { name.trim() }, accountType, last4.trim(), limit, selectedColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OledBlack),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Account", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
