package com.campusfold.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.ui.theme.*

/**
 * Recent Transactions Tile for Home (Part 3 #5):
 * - Header "Recent Transactions" with a "See All >" button that opens the Transactions screen.
 * - The latest 5 transactions in the existing transaction card style.
 * - Tapping a row opens the existing detail sheet.
 * - No inline expand, no search on Home.
 */
@Composable
fun HomeRecentTransactionsTile(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    onSeeAll: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryMap = remember(categories) {
        categories.associateBy { it.id }
    }

    val recent5 = remember(transactions) {
        transactions.take(5)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Transactions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeeAll() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "See All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "See All",
                    tint = NeonEmerald,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (recent5.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MatteCharcoal),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCharcoal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent transactions",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            recent5.forEach { tx ->
                val cat = categoryMap[tx.categoryId]
                FoldTransactionFeedCard(
                    transaction = tx,
                    categoryName = cat?.name ?: "Uncategorized",
                    isUnmapped = tx.categoryId == null,
                    isSelectionMode = false,
                    isSelected = false,
                    onClick = { onTransactionClick(tx) }
                )
            }
        }
    }
}
