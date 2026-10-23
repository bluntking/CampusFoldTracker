package com.campusfold.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.ui.AppNavScreen
import com.campusfold.tracker.ui.theme.*

/**
 * Floating Pill-shaped Bottom Navigation Bar.
 * Houses Home, Analytics, AI Coach, plus floating "+" (Add transaction) and Sync (Scan now) buttons.
 */
@Composable
fun FloatingPillBottomBar(
    currentScreen: AppNavScreen,
    onNavigate: (AppNavScreen) -> Unit,
    onAddTransaction: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(32.dp)
                ),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Home Tab
                PillNavItem(
                    label = "Home",
                    icon = Icons.Default.Home,
                    isSelected = currentScreen == AppNavScreen.HOME,
                    onClick = { onNavigate(AppNavScreen.HOME) }
                )

                // 2. Analytics Tab
                PillNavItem(
                    label = "Analytics",
                    icon = Icons.Default.PieChart,
                    isSelected = currentScreen == AppNavScreen.ANALYTICS,
                    onClick = { onNavigate(AppNavScreen.ANALYTICS) }
                )

                // 3. Floating Add (+) Action
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onAddTransaction() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 4. Sync Now Action
                IconButton(
                    onClick = onSyncNow,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Now",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 5. AI Coach Tab
                PillNavItem(
                    label = "AI Coach",
                    icon = Icons.Default.AutoAwesome,
                    isSelected = currentScreen == AppNavScreen.AI_COACH,
                    onClick = { onNavigate(AppNavScreen.AI_COACH) }
                )
            }
        }
    }
}

@Composable
private fun PillNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "pillNavBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillNavContent"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = bgColor,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            AnimatedVisibility(visible = isSelected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}
