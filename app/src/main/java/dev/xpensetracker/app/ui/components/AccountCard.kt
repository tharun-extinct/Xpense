package dev.xpensetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.data.entity.AccountEntity
import dev.xpensetracker.app.ui.format.toRupeeDisplay
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary

/**
 * One account. Balance is shown as "last seen" rather than "current" because it is whatever the
 * most recent SMS happened to state — there is no bank API to reconcile against.
 */
@Composable
fun AccountCard(
    account: AccountEntity,
    modifier: Modifier = Modifier,
    monthSpendMinor: Long? = null,
    monthLabel: String? = null,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, shape)
            .border(1.dp, Divider, shape)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(AccentPrimary.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = account.issuer,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "\u2022\u2022\u2022\u2022 ${account.accountTail}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Last seen balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Text(
                    text = account.lastSeenBalanceMinor?.toRupeeDisplay() ?: "Not reported",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
            if (monthSpendMinor != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = monthLabel?.let { "Spent in ${it.substringBefore(' ')}" } ?: "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    Text(
                        text = monthSpendMinor.toRupeeDisplay(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
