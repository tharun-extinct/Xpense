package dev.xpensetracker.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.data.entity.CategoryEntity
import dev.xpensetracker.app.ui.components.CategoryAvatar
import dev.xpensetracker.app.ui.components.ScreenTopBar
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.CategoryIcons
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.SurfaceElevated
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary
import dev.xpensetracker.app.ui.theme.categorySwatches
import dev.xpensetracker.app.ui.theme.toCategoryColor

/**
 * Name, colour, and icon for one category.
 *
 * A live preview sits above the fields because colour and icon are choices best judged by seeing
 * the result, not by reading a swatch grid. Built-ins are editable too: a user who calls it
 * "Eating out" rather than "Food & Drinks" should not have to create a duplicate to say so.
 */
@Composable
fun CategoryEditorScreen(
    existing: CategoryEntity?,
    onCancel: () -> Unit,
    onSubmit: (name: String, colorArgb: Long, iconKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var colorArgb by remember(existing?.id) {
        mutableStateOf(existing?.colorArgb ?: categorySwatches.first())
    }
    var iconKey by remember(existing?.id) {
        mutableStateOf(existing?.iconKey ?: CategoryIcons.selectableKeys.first())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase),
    ) {
        ScreenTopBar(
            title = if (existing == null) "New category" else "Edit category",
            onBack = onCancel,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CategoryAvatar(iconKey = iconKey, color = colorArgb.toCategoryColor(), size = 72.dp)
                Text(
                    text = name.ifBlank { "Your category" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isBlank()) TextTertiary else TextPrimary,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = Divider,
                    focusedLabelColor = AccentPrimary,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = AccentPrimary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(text = "Colour", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                SwatchGrid(
                    selected = colorArgb,
                    onSelect = { colorArgb = it },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(text = "Icon", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                IconGrid(
                    selectedKey = iconKey,
                    color = colorArgb.toCategoryColor(),
                    onSelect = { iconKey = it },
                )
            }

            Spacer(Modifier.height(Spacing.sm))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(Spacing.gutter),
        ) {
            Button(
                onClick = { onSubmit(name, colorArgb, iconKey) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.minTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = BackgroundBase,
                    disabledContainerColor = Divider,
                    disabledContentColor = TextTertiary,
                ),
            ) {
                Text(
                    text = if (existing == null) "Create category" else "Save category",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SwatchGrid(selected: Long, onSelect: (Long) -> Unit) {
    // Chunked rows rather than a lazy grid, so this composes inside a scrolling column without
    // competing with it for height.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        categorySwatches.chunked(SWATCH_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { swatch ->
                    val color = swatch.toCategoryColor()
                    val isSelected = swatch == selected
                    Box(
                        modifier = Modifier
                            .size(Spacing.minTouchTarget)
                            .background(color, CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) TextPrimary else color,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(swatch) }
                            .semantics { contentDescription = if (isSelected) "Colour selected" else "Colour option" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = BackgroundBase,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconGrid(
    selectedKey: String,
    color: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CategoryIcons.selectableKeys.chunked(ICON_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { key ->
                    val isSelected = key == selectedKey
                    Box(
                        modifier = Modifier
                            .size(Spacing.minTouchTarget)
                            .background(
                                if (isSelected) color.copy(alpha = 0.2f) else SurfaceElevated,
                                MaterialTheme.shapes.small,
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) color else Divider,
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onSelect(key) }
                            .semantics { contentDescription = key.replace('_', ' ') },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CategoryIcons.resolve(key),
                            contentDescription = null,
                            tint = if (isSelected) color else TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_NAME_LENGTH = 24
private const val SWATCH_COLUMNS = 6
private const val ICON_COLUMNS = 6
