package dev.xpensetracker.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary
import java.time.YearMonth

/**
 * The month filter shared by Home and Spends.
 *
 * Bounds come from data rather than an arbitrary window: the user cannot advance past the current
 * month or page back before [earliestMonth], so stepping never lands on a month that is guaranteed
 * to be empty. When only one month of data exists, the arrows are simply inert.
 */
@Composable
fun MonthSelector(
    selected: YearMonth,
    earliestMonth: YearMonth?,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMonth = remember { YearMonth.now() }
    val lowerBound = earliestMonth?.takeIf { it.isBefore(currentMonth) } ?: currentMonth
    val canGoBack = selected.isAfter(lowerBound)
    val canGoForward = selected.isBefore(currentMonth)

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, MaterialTheme.shapes.large)
                .border(1.dp, Divider, MaterialTheme.shapes.large)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepButton(
                icon = Icons.Filled.ChevronLeft,
                enabled = canGoBack,
                description = "Previous month",
                onClick = { onSelect(selected.minusMonths(1)) },
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { expanded = !expanded }
                    .padding(vertical = Spacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.displayLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide month picker" else "Choose a month",
                    tint = TextSecondary,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(18.dp),
                )
            }

            StepButton(
                icon = Icons.Filled.ChevronRight,
                enabled = canGoForward,
                description = "Next month",
                onClick = { onSelect(selected.plusMonths(1)) },
            )
        }

        AnimatedVisibility(visible = expanded) {
            MonthGrid(
                selected = selected,
                lowerBound = lowerBound,
                currentMonth = currentMonth,
                onSelect = {
                    onSelect(it)
                    expanded = false
                },
            )
        }
    }
}

/**
 * Jumping several months back without repeated tapping. Only months within the data range are
 * offered, so every option resolves to something.
 */
@Composable
private fun MonthGrid(
    selected: YearMonth,
    lowerBound: YearMonth,
    currentMonth: YearMonth,
    onSelect: (YearMonth) -> Unit,
) {
    val months = remember(lowerBound, currentMonth) {
        generateSequence(currentMonth) { it.minusMonths(1) }
            .takeWhile { !it.isBefore(lowerBound) }
            .take(MAX_MONTH_OPTIONS)
            .toList()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .heightForRows(months.size),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(months) { month ->
            val isSelected = month == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) AccentPrimary else SurfaceCard,
                        MaterialTheme.shapes.small,
                    )
                    .clickable { onSelect(month) }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = month.shortLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) BackgroundBase else TextSecondary,
                )
            }
        }
    }
}

/**
 * A grid inside a scrolling column has no intrinsic height, so it is measured from the row count
 * instead of being allowed to claim infinite height.
 */
private fun Modifier.heightForRows(itemCount: Int): Modifier {
    val rows = (itemCount + 2) / 3
    return this.height((rows * 52).dp)
}

@Composable
private fun StepButton(
    icon: ImageVector,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Spacing.minTouchTarget)
            .background(BackgroundBase, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            // The description stays on the disabled button too, so a screen reader can explain
            // why the control is there rather than it vanishing from the tree.
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextPrimary else TextTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val MAX_MONTH_OPTIONS = 24

fun YearMonth.displayLabel(): String =
    month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + year

private fun YearMonth.shortLabel(): String =
    month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + (year % 100)
