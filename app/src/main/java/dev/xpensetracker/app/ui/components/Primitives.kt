package dev.xpensetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary

/**
 * The standard raised panel. A hairline border rather than a shadow, because on a near-black
 * surface an elevation shadow is invisible and the edge is what separates the card.
 */
@Composable
fun SurfacePanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, shape)
            .border(1.dp, Divider, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(Spacing.lg),
        content = content,
    )
}

/** A labelled figure. Used in pairs so spends and income read as one comparison. */
@Composable
fun StatCard(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .background(SurfaceCard, shape)
            .border(1.dp, Divider, shape)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1)
    }
}

/**
 * The one way this app says "there is nothing here". Every list and chart uses it, so an empty
 * state is never mistakable for a failed render.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxl, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(SurfaceCard, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(26.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = BackgroundBase,
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
        }
    }
}

/** Pill tabs. Replaces the default TabRow, whose underline indicator reads as unstyled here. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        MaterialTheme.shapes.medium,
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) BackgroundBase else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
