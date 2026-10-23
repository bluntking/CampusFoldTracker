package com.campusfold.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusfold.tracker.ui.theme.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

/**
 * Half-circle Spending Gauge showing month-to-date spent vs budget limit.
 * Uses existing budget and burn-rate pacing logic.
 */
@Composable
fun SpendingGauge(
    spent: Double,
    budgetLimit: Double,
    modifier: Modifier = Modifier
) {
    if (budgetLimit <= 0.0) return

    val progress = min(1.0f, (spent / budgetLimit).toFloat()).coerceAtLeast(0.0f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "gaugeProgress"
    )

    // Pacing evaluation using days elapsed in current month
    val calendar = Calendar.getInstance()
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysElapsedPercent = (dayOfMonth.toDouble() / totalDays.toDouble()) * 100.0
    val spentPercent = (spent / budgetLimit) * 100.0
    val pacingAhead = spentPercent - daysElapsedPercent

    val (statusText, statusBgColor, statusTextColor, gaugeColor) = when {
        spent > budgetLimit -> {
            Tuple4("Over Budget", md_theme_dark_errorContainer, md_theme_dark_error, NeonCrimson)
        }
        pacingAhead > 8.0 -> {
            Tuple4("Pacing Fast", PillBgUnmapped, NeonAmber, NeonAmber)
        }
        else -> {
            Tuple4("On Track", PillBgIncome, NeonEmerald, NeonEmerald)
        }
    }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

        Canvas(
            modifier = Modifier
                .size(width = 240.dp, height = 120.dp)
                .align(Alignment.TopCenter)
        ) {
            val strokeWidth = 14.dp.toPx()
            val canvasWidth = size.width
            val canvasHeight = size.height * 2

            // Background half-circle arc
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
            )

            // Foreground progress arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = gaugeColor,
                    startAngle = 180f,
                    sweepAngle = 180f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
                )
            }
        }

        // Center labels
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currencyFormatter.format(spent),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "of ${currencyFormatter.format(budgetLimit)} limit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = statusBgColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusTextColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
