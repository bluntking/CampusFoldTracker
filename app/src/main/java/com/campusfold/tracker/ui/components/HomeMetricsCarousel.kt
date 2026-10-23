package com.campusfold.tracker.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

/**
 * Horizontally swipeable carousel with page dots:
 * 1. Spending gauge (half-circle: spent vs limit with "On Track" status, using existing budgets & burn-rate logic, hidden if no budget set).
 * 2. True spend this month.
 * 3. Friends owe you.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeMetricsCarousel(
    currentMonthTrueSpend: Double,
    monthlyBudgetLimit: Double,
    totalOwedToMe: Double,
    owedFriendsCount: Int,
    onNavigateToSplits: () -> Unit,
    onSetBudgetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    // Determine slides to show: if budget is set (> 0), show 3 slides; otherwise show 2 slides (True spend & Friends owe)
    val hasBudget = monthlyBudgetLimit > 0.0
    val pageCount = if (hasBudget) 3 else 2
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            pageSpacing = 12.dp
        ) { page ->
            // Map page index depending on whether spending gauge is included
            val actualSlideType = if (hasBudget) {
                when (page) {
                    0 -> CarouselSlide.SPENDING_GAUGE
                    1 -> CarouselSlide.TRUE_SPEND
                    else -> CarouselSlide.FRIENDS_OWE
                }
            } else {
                when (page) {
                    0 -> CarouselSlide.TRUE_SPEND
                    else -> CarouselSlide.FRIENDS_OWE
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                when (actualSlideType) {
                    CarouselSlide.SPENDING_GAUGE -> {
                        SpendingGauge(
                            spent = currentMonthTrueSpend,
                            budgetLimit = monthlyBudgetLimit,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    CarouselSlide.TRUE_SPEND -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = NeonEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "TRUE SPEND THIS MONTH",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    color = NeonEmerald.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Personal Only",
                                        color = NeonEmerald,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = currencyFormatter.format(currentMonthTrueSpend),
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Strictly excludes friend split shares & credit card bill transfers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    CarouselSlide.FRIENDS_OWE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onNavigateToSplits() }
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "FRIENDS OWE YOU",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "View Splits",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NeonCyan
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = currencyFormatter.format(totalOwedToMe),
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = if (totalOwedToMe > 0) NeonCyan else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (totalOwedToMe > 0) "Pending from $owedFriendsCount friends via bill splits." else "All friend splits are settled up!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Page Dots Indicator
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    label = "pageDotWidth"
                )
                val dotColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

private enum class CarouselSlide {
    SPENDING_GAUGE,
    TRUE_SPEND,
    FRIENDS_OWE
}
