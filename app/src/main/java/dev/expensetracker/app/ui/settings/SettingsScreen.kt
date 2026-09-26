package dev.expensetracker.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.ui.theme.BackgroundBase
import dev.expensetracker.app.ui.theme.Divider
import dev.expensetracker.app.ui.theme.AccentPrimary
import dev.expensetracker.app.ui.theme.AccentTheme
import dev.expensetracker.app.ui.theme.Spacing
import dev.expensetracker.app.ui.theme.SurfaceCard
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.theme.TextTertiary

/**
 * Privacy statement, backfill trigger, and the entry point to category management.
 */
@Composable
fun SettingsScreen(
    repository: ExpenseRepository,
    modifier: Modifier = Modifier,
    onRunBackfill: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    selectedAccent: AccentTheme = AccentTheme.DEFAULT,
    onSelectAccent: (AccentTheme) -> Unit = {},
) {
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    var scanRequested by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

        SettingsPanel(
            icon = Icons.Filled.Lock,
            accent = AccentPrimary,
            title = "Private by design",
            body = "This app works fully offline. It never requests internet access and never " +
                "sends your messages or transactions anywhere. Only this device can see them.\n\n" +
                "Location is optional. It is read only when you tap to tag a transaction, and " +
                "stays on this device — until you tap that tag, which opens it in your map app.",
        )

        SettingsPanel(
            icon = Icons.Filled.Palette,
            accent = AccentPrimary,
            title = "Accent colour",
            body = "Pick the colour used for buttons, the selected tab, and the spend ring. " +
                "Income green and the delete red never change — they mean something.",
        ) {
            AccentPicker(selected = selectedAccent, onSelect = onSelectAccent)
        }

        SettingsPanel(
            icon = Icons.Filled.Category,
            accent = AccentPrimary,
            title = "Categories",
            body = "${categories.count { !it.isArchived }} categories available. " +
                "Create your own, or rename and recolour the built-in ones.",
        ) {
            NavRow(label = "Manage categories", onClick = onOpenCategories)
        }

        SettingsPanel(
            icon = Icons.Filled.Sync,
            accent = AccentPrimary,
            title = "Scan existing messages",
            body = "Run a one-time scan of your SMS inbox to import past transactions. " +
                "This only runs when you tap the button.",
        ) {
            Button(
                onClick = {
                    onRunBackfill()
                    scanRequested = true
                },
                modifier = Modifier.height(Spacing.minTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = BackgroundBase,
                ),
            ) {
                Text("Scan inbox now", style = MaterialTheme.typography.labelLarge)
            }
            if (scanRequested) {
                Text(
                    text = "Scan started. Imported transactions appear on Home as they are found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentPrimary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        Text(
            text = "Because this app reads SMS for expense tracking, it cannot be distributed via " +
                "the Google Play Store. Install it directly from a signed release build.",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
        )

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun SettingsPanel(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        content?.invoke()
    }
}

/**
 * The four accents as swatches.
 *
 * The selected one is marked with a tick and named underneath, never by ring or fill alone: a
 * colour picker whose only "selected" signal is a colour is unusable to anyone who cannot
 * distinguish the colours being offered.
 */
@Composable
private fun AccentPicker(selected: AccentTheme, onSelect: (AccentTheme) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AccentTheme.entries.forEach { theme ->
            val isSelected = theme == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(theme) },
                    )
                    .padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(theme.primary, CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) TextPrimary else Divider,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = BackgroundBase,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(Spacing.minTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = AccentPrimary)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = AccentPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}
