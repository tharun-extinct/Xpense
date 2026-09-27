package dev.xpensetracker.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.CategoryEntity
import dev.xpensetracker.app.data.entity.TransactionDirection
import dev.xpensetracker.app.data.entity.TransactionEntity
import dev.xpensetracker.app.ui.components.CategoryAvatar
import dev.xpensetracker.app.ui.components.EmptyState
import dev.xpensetracker.app.ui.components.ScreenTopBar
import dev.xpensetracker.app.ui.format.toRupeeDisplay
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.PositiveGreen
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.SurfaceElevated
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary
import dev.xpensetracker.app.ui.theme.toCategoryColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("d MMM", Locale.getDefault())

/**
 * The needs-review queue: exactly the `NEEDS_REVIEW` transactions, per architecture.md
 * #state-lifecycle — a query, not a separate store.
 *
 * Optimised for clearing a backlog: likely categories are one tap away inline, and a row leaves
 * the list the moment it is resolved, so progress is visible.
 */
@Composable
fun ReviewScreen(
    repository: ExpenseRepository,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending by repository.observeNeedsReview().collectAsState(initial = emptyList())
    val categories by repository.observeSelectableCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Kept to four so the chips fit one row; the full grid is one tap away on the detail screen.
    val quickPicks = remember(categories) { categories.filter { it.isBuiltIn }.take(4) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase),
    ) {
        ScreenTopBar(
            title = "Needs review",
            subtitle = if (pending.isEmpty()) null else "${pending.size} waiting",
            onBack = onBack,
        )

        if (pending.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.TaskAlt,
                title = "All caught up",
                message = "Every transaction has a category. New ones show up here when the " +
                    "parser is not confident enough to decide on its own.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    text = "Tap a category to file these, or open one for the full list. " +
                        "Filing also teaches the merchant, so it will not ask again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            items(pending, key = { it.id }) { transaction ->
                ReviewCard(
                    transaction = transaction,
                    quickPicks = quickPicks,
                    onQuickPick = { categoryId ->
                        scope.launch {
                            repository.recategorizeTransaction(
                                transactionId = transaction.id,
                                categoryId = categoryId,
                                alsoTeachMerchant = true,
                            )
                        }
                    },
                    onOpen = { onOpenTransaction(transaction.id) },
                )
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
private fun ReviewCard(
    transaction: TransactionEntity,
    quickPicks: List<CategoryEntity>,
    onQuickPick: (String) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchantRaw,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateFormatter.format(Date(transaction.occurredAtUtcMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
            // Signed and coloured like every other amount in the app: an unsigned figure in this
            // queue read as an xpense even when the parser had it as money in.
            val isCredit = transaction.direction == TransactionDirection.CREDIT
            Text(
                text = (if (isCredit) "+" else "\u2212") + transaction.amountMinor.toRupeeDisplay(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCredit) PositiveGreen else TextPrimary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            quickPicks.forEach { category ->
                QuickPickChip(
                    category = category,
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickPick(category.id) },
                )
            }
        }

        Text(
            text = "More categories",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(vertical = Spacing.xs),
        )
    }
}

@Composable
private fun QuickPickChip(
    category: CategoryEntity,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = category.colorArgb.toCategoryColor()
    Column(
        modifier = modifier
            .background(SurfaceElevated, MaterialTheme.shapes.medium)
            .border(1.dp, Divider, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        CategoryAvatar(iconKey = category.iconKey, color = color, size = 30.dp)
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
