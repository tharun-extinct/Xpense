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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.data.CategoryResult
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.CategoryEntity
import dev.xpensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.xpensetracker.app.ui.components.CategoryAvatar
import dev.xpensetracker.app.ui.components.ScreenTopBar
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.DangerRed
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary
import dev.xpensetracker.app.ui.theme.toCategoryColor
import kotlinx.coroutines.launch

/**
 * Create, rename, recolour, archive, and (where safe) delete categories.
 *
 * Deletion is deliberately narrow: built-ins are never deletable and a custom category in use is
 * not either, because both would either orphan historical rows or force a history rewrite, which
 * architecture.md #identity-and-ownership forbids. Archiving is the always-available alternative.
 */
@Composable
fun CategoryManagerScreen(
    repository: ExpenseRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startInCreateMode: Boolean = false,
) {
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editorTarget by remember {
        mutableStateOf<EditorTarget?>(if (startInCreateMode) EditorTarget.New else null)
    }
    var message by remember { mutableStateOf<String?>(null) }

    val target = editorTarget
    if (target != null) {
        CategoryEditorScreen(
            existing = (target as? EditorTarget.Existing)?.category,
            onCancel = { editorTarget = null },
            onSubmit = { name, color, icon ->
                scope.launch {
                    val result = when (target) {
                        EditorTarget.New -> repository.createCustomCategory(name, color, icon)
                        is EditorTarget.Existing -> repository.updateCategory(target.category, name, color, icon)
                    }
                    message = result.toMessage()
                    if (result is CategoryResult.Success) editorTarget = null
                }
            },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase),
    ) {
        ScreenTopBar(
            title = "Categories",
            subtitle = "${categories.count { !it.isArchived }} in use",
            onBack = onBack,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(Spacing.minTouchTarget)
                        .background(AccentPrimary, CircleShape)
                        .clickable { editorTarget = EditorTarget.New }
                        .semantics { contentDescription = "Create a category" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = BackgroundBase,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { message = null }
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryManageRow(
                    category = category,
                    onEdit = { editorTarget = EditorTarget.Existing(category) },
                    onToggleArchive = {
                        scope.launch {
                            message = repository
                                .setCategoryArchived(category.id, !category.isArchived)
                                .toMessage()
                        }
                    },
                    onDelete = {
                        scope.launch { message = repository.deleteCategory(category.id).toMessage() }
                    },
                )
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val category: CategoryEntity) : EditorTarget
}

private fun CategoryResult.toMessage(): String = when (this) {
    CategoryResult.Success -> "Saved"
    CategoryResult.InvalidName -> "Give the category a name"
    CategoryResult.DuplicateName -> "A category with that name already exists"
    CategoryResult.NotAllowed -> "Built-in categories cannot be deleted, only hidden"
    is CategoryResult.InUse ->
        "Still used by $referenceCount item${if (referenceCount == 1) "" else "s"}. Hide it instead."
}

@Composable
private fun CategoryManageRow(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = category.colorArgb.toCategoryColor()
    // Uncategorized is where unmatched transactions land, so hiding it would make that spend
    // unreachable in the picker.
    val canArchive = category.id != UNKNOWN_CATEGORY_ID
    val canDelete = !category.isBuiltIn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(iconKey = category.iconKey, color = color, size = 38.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (category.isArchived) TextTertiary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    if (category.isBuiltIn) "Built-in" else "Custom",
                    if (category.isArchived) "Hidden" else null,
                ).joinToString(" \u00B7 "),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }

        IconAction(
            icon = Icons.Filled.Edit,
            description = "Edit ${category.name}",
            onClick = onEdit,
        )
        if (canArchive) {
            IconAction(
                icon = if (category.isArchived) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                description = if (category.isArchived) "Show ${category.name}" else "Hide ${category.name}",
                onClick = onToggleArchive,
            )
        }
        if (canDelete) {
            IconAction(
                icon = Icons.Filled.Delete,
                description = "Delete ${category.name}",
                tint = DangerRed,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = TextSecondary,
) {
    Box(
        modifier = Modifier
            .size(Spacing.minTouchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}
