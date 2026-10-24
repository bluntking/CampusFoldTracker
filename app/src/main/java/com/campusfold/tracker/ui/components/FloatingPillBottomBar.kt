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
 * Exactly 5 slots in order: Home, Analytics, + (center), Splits, Settings.
 * Selected item expands to show its text label.
 * Pushed screens highlight Home.
 */
@Composable
fun FloatingPillBottomBar(
    currentScreen: AppNavScreen,
    onNavigate: (AppNavScreen) -> Unit,
    onAddTransaction: () -> Unit,
    isPushedScreenActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isHomeActive = currentScreen == AppNavScreen.HOME || isPushedScreenActive

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
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
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Wing: Home & Analytics (strictly 50% left space)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillNavItem(
                        label = "Home",
                        icon = Icons.Default.Home,
                        isSelected = isHomeActive,
                        onClick = { onNavigate(AppNavScreen.HOME) }
                    )

                    PillNavItem(
                        label = "Analytics",
                        icon = Icons.Default.PieChart,
                        isSelected = currentScreen == AppNavScreen.ANALYTICS,
                        onClick = { onNavigate(AppNavScreen.ANALYTICS) }
                    )
                }

                // Center Floating Add (+) Action: mathematically centered at 50% width on every screen
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

                // Right Wing: Splits & Settings (strictly 50% right space)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillNavItem(
                        label = "Splits",
                        icon = Icons.Default.CallSplit,
                        isSelected = currentScreen == AppNavScreen.SPLITS,
                        onClick = { onNavigate(AppNavScreen.SPLITS) }
                    )

                    PillNavItem(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        isSelected = currentScreen == AppNavScreen.SETTINGS,
                        onClick = { onNavigate(AppNavScreen.SETTINGS) }
                    )
                }
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
