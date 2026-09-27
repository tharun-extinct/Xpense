package dev.xpensetracker.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.Spacing

/**
 * The differentiator this app has that the reference UI doesn't: a visible affordance for
 * transactions the parser/categorizer couldn't confidently auto-confirm. It is the entry point to
 * the review queue, so it must always be tappable while visible.
 */
@Composable
fun NeedsReviewBanner(count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (count <= 0) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AccentPrimary, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(BackgroundBase.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PriorityHigh,
                    contentDescription = null,
                    tint = BackgroundBase,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = "$count transaction${if (count == 1) "" else "s"} need review",
                    style = MaterialTheme.typography.titleSmall,
                    color = BackgroundBase,
                )
                Text(
                    text = "Give them a category to sharpen your breakdown",
                    style = MaterialTheme.typography.labelSmall,
                    color = BackgroundBase.copy(alpha = 0.7f),
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Open review queue",
            tint = BackgroundBase,
            modifier = Modifier.size(20.dp),
        )
    }
}
