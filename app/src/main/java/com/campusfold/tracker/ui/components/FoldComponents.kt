package com.campusfold.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.ui.theme.BorderCharcoal
import com.campusfold.tracker.ui.theme.MatteCharcoal
import com.campusfold.tracker.ui.theme.TextPrimary
import java.text.NumberFormat
import java.util.Locale

/**
 * Fold Money Signature Matte Charcoal Card with 1dp border.
 */
@Composable
fun FoldCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    containerColor: Color = MatteCharcoal,
    borderColor: Color = BorderCharcoal,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content
    )
}

/**
 * High-contrast status pill for Fold Money dark aesthetic.
 */
@Composable
fun StatusPill(
    text: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fontSize: TextUnit = 10.sp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

/**
 * Bold Monospace Currency Text (Fold Money Typography).
 */
@Composable
fun MonospaceCurrencyText(
    amount: Double,
    modifier: Modifier = Modifier,
    prefix: String = "₹",
    fontSize: TextUnit = 16.sp,
    color: Color = TextPrimary,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val formatted = formatter.format(amount)

    Row(modifier = modifier, verticalAlignment = Alignment.Baseline) {
        Text(
            text = prefix,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = (fontSize.value * 0.85).sp,
            color = color.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = formatted,
            fontFamily = FontFamily.Monospace,
            fontWeight = fontWeight,
            fontSize = fontSize,
            color = color
        )
    }
}
