package dev.expensetracker.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.expensetracker.app.ui.format.toRupeeDisplay
import dev.expensetracker.app.ui.theme.DangerRed
import dev.expensetracker.app.ui.theme.Divider
import dev.expensetracker.app.ui.theme.AccentPrimary
import dev.expensetracker.app.ui.theme.Spacing
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.theme.TextTertiary

/**
 * The month's total spend, with an optional budget-progress arc over a neutral track.
 *
 * The arc turns red past the limit rather than clamping silently, because a budget that is blown
 * is exactly the case the ring exists to communicate.
 */
@Composable
fun SpendRing(
    spendsMinor: Long,
    budgetProgressFraction: Float?,
    monthLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 224.dp,
    caption: String? = null,
) {
    val overBudget = (budgetProgressFraction ?: 0f) > 1f
    val animatedProgress by animateFloatAsState(
        targetValue = budgetProgressFraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(durationMillis = 700),
        label = "spendRingProgress",
    )

    // Read here rather than inside the Canvas: a draw lambda is not composition, so it cannot
    // reach the accent the user selected.
    val progressColor = if (overBudget) DangerRed else AccentPrimary

    val description = if (budgetProgressFraction == null) {
        "Spends for $monthLabel: ${spendsMinor.toRupeeDisplay()}"
    } else {
        "Spends for $monthLabel: ${spendsMinor.toRupeeDisplay()}, " +
            "${"%.0f".format(budgetProgressFraction * 100)} percent of budget"
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            drawArc(
                color = Divider,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
                size = arcSize,
                topLeft = topLeft,
            )
            if (budgetProgressFraction != null && animatedProgress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeft,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.xxl),
        ) {
            Text(
                text = "Spends",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
            )
            Text(
                text = spendsMinor.toRupeeDisplay(),
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = caption ?: monthLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (overBudget) DangerRed else TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}
