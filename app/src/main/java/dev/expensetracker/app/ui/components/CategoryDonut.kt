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
import androidx.compose.runtime.remember
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
import dev.expensetracker.app.analytics.CategorySlice
import dev.expensetracker.app.ui.format.toRupeeDisplay
import dev.expensetracker.app.ui.theme.Divider
import dev.expensetracker.app.ui.theme.Spacing
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.theme.TextTertiary
import dev.expensetracker.app.ui.theme.toCategoryColor

/**
 * Category-breakdown donut.
 *
 * Four things keep it from ever reading as an unexplained circle: a neutral track is drawn before
 * any data, an empty month renders explicit copy instead of a silent canvas, the centre always
 * carries the total, and every arc is separated by a gap with a minimum sweep so a single-category
 * month still reads as a ring rather than a filled disc.
 */
@Composable
fun CategoryDonut(
    slices: List<CategorySlice>,
    monthLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 208.dp,
    strokeWidth: Dp = 22.dp,
) {
    val totalMinor = remember(slices) { slices.sumOf { it.totalMinor } }
    val transactionCount = remember(slices) { slices.sumOf { it.transactionCount } }
    val isEmpty = slices.isEmpty() || totalMinor <= 0L

    val sweepProgress by animateFloatAsState(
        targetValue = if (isEmpty) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "donutSweep",
    )

    val description = remember(slices, totalMinor, monthLabel) {
        if (slices.isEmpty()) {
            "Category breakdown for $monthLabel: no spends recorded."
        } else {
            val top = slices.take(3).joinToString(", ") {
                "${it.name} ${"%.0f".format(it.percentOfTotal)} percent"
            }
            "Category breakdown for $monthLabel, total ${totalMinor.toRupeeDisplay()}. Largest: $top."
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // Always drawn, so the component occupies visible space even with no data at all.
            drawArc(
                color = Divider,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
                size = arcSize,
                topLeft = topLeft,
            )

            if (isEmpty) return@Canvas

            var startAngle = START_ANGLE
            slices.forEach { slice ->
                val raw = (slice.percentOfTotal / 100.0 * 360.0).toFloat()
                // A sub-degree slice would otherwise be invisible; the distortion is bounded
                // because only the long tail is affected and the list below shows exact amounts.
                val sweep = raw.coerceAtLeast(MIN_SWEEP_DEGREES)
                val gap = if (slices.size > 1) GAP_DEGREES else 0f
                val drawn = (sweep - gap).coerceAtLeast(MIN_SWEEP_DEGREES) * sweepProgress

                drawArc(
                    color = slice.colorArgb.toCategoryColor(),
                    startAngle = startAngle,
                    sweepAngle = drawn,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeft,
                )
                startAngle += sweep
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.xxl),
        ) {
            if (isEmpty) {
                Text(
                    text = "No spends in",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "Total spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Text(
                    text = totalMinor.toRupeeDisplay(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "$transactionCount txn${if (transactionCount == 1) "" else "s"} " +
                        "in ${slices.size} categor${if (slices.size == 1) "y" else "ies"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val START_ANGLE = -90f
private const val GAP_DEGREES = 3f
private const val MIN_SWEEP_DEGREES = 4f

/** Id of the synthetic slice; deliberately not a real category, so it can never be assigned. */
const val ROLLUP_SLICE_ID: String = "__rollup__"

/**
 * Collapses the long tail into one slice so the ring stays readable once a user has created many
 * categories.
 *
 * This is presentation only — the repository still emits every category, and the list rendered
 * beside the donut shows the full breakdown, so the two always sum to the same total.
 */
fun List<CategorySlice>.rolledUpForRing(maxSlices: Int = 8): List<CategorySlice> {
    if (size <= maxSlices) return this
    val sorted = sortedByDescending { it.totalMinor }
    val head = sorted.take(maxSlices - 1)
    val tail = sorted.drop(maxSlices - 1)
    return head + CategorySlice(
        categoryId = ROLLUP_SLICE_ID,
        name = "${tail.size} more",
        colorArgb = 0xFF6B7280,
        iconKey = "other",
        totalMinor = tail.sumOf { it.totalMinor },
        transactionCount = tail.sumOf { it.transactionCount },
        percentOfTotal = tail.sumOf { it.percentOfTotal },
    )
}
