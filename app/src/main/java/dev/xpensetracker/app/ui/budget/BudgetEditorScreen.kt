package dev.xpensetracker.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.analytics.MonthRange
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.BudgetEntity
import dev.xpensetracker.app.ui.components.CategoryAvatar
import dev.xpensetracker.app.ui.components.ScreenTopBar
import dev.xpensetracker.app.ui.components.displayLabel
import dev.xpensetracker.app.ui.format.toRupeeDisplay
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
import java.time.YearMonth

/**
 * Sets one category's limit for one month, matching the `(month, category)` scope of `budgets` in
 * architecture.md #state-lifecycle — a budget is per month, never a rolling global setting.
 *
 * The field takes whole rupees and converts on save, because money is stored as Long paise
 * everywhere (architecture.md #data-representation).
 */
@Composable
fun BudgetEditorScreen(
    repository: ExpenseRepository,
    month: YearMonth,
    categoryId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthKey = remember(month) { month.toString() }
    val monthRange = remember(month) { MonthRange.forMonth(month) }
    val scope = rememberCoroutineScope()

    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val budgets by repository.observeBudgetsForMonth(monthKey)
        .collectAsState(initial = emptyList<BudgetEntity>())
    val breakdown by repository.observeCategoryBreakdown(monthRange.startMillis, monthRange.endMillis)
        .collectAsState(initial = emptyList())

    val category = categories.firstOrNull { it.id == categoryId }
    val existing = budgets.firstOrNull { it.category == categoryId }
    val spentMinor = breakdown.firstOrNull { it.category == categoryId }?.totalMinor ?: 0L

    var rupees by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    // The stored limit arrives asynchronously; seed the field once so typing is never overwritten.
    LaunchedEffect(existing?.limitMinor) {
        if (!seeded && existing != null) {
            rupees = (existing.limitMinor / 100).toString()
            seeded = true
        }
    }

    val parsedRupees = rupees.toLongOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase),
    ) {
        ScreenTopBar(
            title = "Monthly budget",
            subtitle = month.displayLabel(),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CategoryAvatar(
                    iconKey = category?.iconKey ?: "other",
                    color = (category?.colorArgb ?: 0xFF6B7280).toCategoryColor(),
                    size = 60.dp,
                )
                Text(
                    text = category?.name ?: categoryId,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "Already spent ${spentMinor.toRupeeDisplay()} this month",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }

            OutlinedTextField(
                value = rupees,
                onValueChange = { input -> rupees = input.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text("Limit in rupees") },
                prefix = { Text("\u20B9") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

            if (parsedRupees != null && parsedRupees > 0) {
                val remaining = parsedRupees * 100 - spentMinor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, MaterialTheme.shapes.large)
                        .border(1.dp, Divider, MaterialTheme.shapes.large)
                        .padding(Spacing.lg),
                ) {
                    Text(
                        text = if (remaining >= 0) {
                            "${remaining.toRupeeDisplay()} would be left for the rest of the month."
                        } else {
                            "You are already ${(-remaining).toRupeeDisplay()} past this limit."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (remaining >= 0) TextSecondary else DangerRed,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        repository.setBudget(
                            BudgetEntity(
                                month = monthKey,
                                category = categoryId,
                                limitMinor = (parsedRupees ?: 0L) * 100,
                            ),
                        )
                        onBack()
                    }
                },
                enabled = parsedRupees != null && parsedRupees > 0,
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
                Text(text = "Save budget", style = MaterialTheme.typography.labelLarge)
            }

            if (existing != null) {
                Text(
                    text = "Remove this budget",
                    style = MaterialTheme.typography.labelMedium,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                repository.clearBudget(monthKey, categoryId)
                                onBack()
                            }
                        }
                        .padding(vertical = Spacing.md),
                )
            }
        }
    }
}

private const val MAX_DIGITS = 8
