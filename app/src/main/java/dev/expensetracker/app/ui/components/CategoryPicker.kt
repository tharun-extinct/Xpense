package dev.expensetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.expensetracker.app.data.entity.CategoryEntity
import dev.expensetracker.app.ui.theme.Divider
import dev.expensetracker.app.ui.theme.Spacing
import dev.expensetracker.app.ui.theme.SurfaceElevated
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.theme.toCategoryColor

/**
 * Category chooser used by transaction detail and the review queue.
 *
 * Laid out as wrapped rows rather than a lazy grid so it can sit inside a scrolling column without
 * fighting it for height, and so the full set is visible at once — scanning beats scrolling when
 * the list is short and the task is a single choice.
 */
@Composable
fun CategoryPickerGrid(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    onCreateNew: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val cells: List<CategoryEntity?> = if (onCreateNew != null) categories + listOf(null) else categories
        cells.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                row.forEach { category ->
                    if (category == null) {
                        CreateNewCell(modifier = Modifier.weight(1f), onClick = onCreateNew!!)
                    } else {
                        CategoryCell(
                            category = category,
                            selected = category.id == selectedId,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(category.id) },
                        )
                    }
                }
                // Keeps the final row's cells the same width as every other row's.
                repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = category.colorArgb.toCategoryColor()
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .background(if (selected) color.copy(alpha = 0.16f) else SurfaceElevated, shape)
            .border(1.dp, if (selected) color else Divider, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "${category.name}, selected" else category.name }
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CategoryAvatar(iconKey = category.iconKey, color = color, size = 36.dp)
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(color, CircleShape)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreateNewCell(modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .background(SurfaceElevated, shape)
            .border(1.dp, Divider, shape)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "New",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
