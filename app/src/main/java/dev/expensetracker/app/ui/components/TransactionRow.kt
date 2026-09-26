package dev.expensetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import dev.expensetracker.app.ui.format.toRupeeDisplay
import dev.expensetracker.app.ui.theme.PositiveGreen
import dev.expensetracker.app.ui.theme.Spacing
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("d MMM \u2022 h:mm a", Locale.getDefault())

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    categories: CategoryLookup,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val isCredit = transaction.direction == TransactionDirection.CREDIT
    val categoryName = categories.name(transaction.category)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Spacing.minTouchTarget + Spacing.md)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryAvatar(
                iconKey = categories.iconKey(transaction.category),
                color = categories.color(transaction.category),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = transaction.merchantRaw,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The category is stated in words, never by color alone, so the row is
                    // readable without color perception.
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "  \u00B7  ${dateFormatter.format(Date(transaction.occurredAtUtcMillis))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = Spacing.sm),
        ) {
            Text(
                text = (if (isCredit) "+" else "\u2212") + transaction.amountMinor.toRupeeDisplay(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCredit) PositiveGreen else TextPrimary,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            if (transaction.state == TransactionState.NEEDS_REVIEW) {
                Text(
                    text = "Needs review",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.shapes.extraSmall,
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
    }
}
