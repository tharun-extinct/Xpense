package dev.xpensetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.data.entity.CategoryEntity
import dev.xpensetracker.app.ui.theme.CategoryIcons
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.toCategoryColor

/**
 * Resolves a stored category id to its display attributes.
 *
 * Exists so no composable ever derives a label from an id (architecture.md #data-representation).
 * An id with no row degrades to the id text rather than rendering blank, which keeps a manually
 * corrupted database legible instead of invisible.
 */
@Immutable
class CategoryLookup(private val byId: Map<String, CategoryEntity>) {

    fun find(id: String): CategoryEntity? = byId[id]

    fun name(id: String): String = byId[id]?.name ?: id

    fun color(id: String): Color = byId[id]?.colorArgb?.toCategoryColor() ?: TextSecondary

    fun iconKey(id: String): String = byId[id]?.iconKey ?: "other"

    companion object {
        val Empty = CategoryLookup(emptyMap())

        fun from(categories: List<CategoryEntity>): CategoryLookup =
            CategoryLookup(categories.associateBy(CategoryEntity::id))
    }
}

/** A category's icon on its own tinted disc. The one way a category is depicted app-wide. */
@Composable
fun CategoryAvatar(
    iconKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryIcons.resolve(iconKey),
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
