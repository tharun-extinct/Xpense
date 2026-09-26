package dev.expensetracker.app.ui.spends

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
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.expensetracker.app.analytics.BudgetProgressCalculator
import dev.expensetracker.app.analytics.CategoryBreakdownRepository
import dev.expensetracker.app.analytics.CategorySlice
import dev.expensetracker.app.analytics.MerchantBreakdownRepository
import dev.expensetracker.app.analytics.MonthRange
import dev.expensetracker.app.analytics.MonthTrend
import dev.expensetracker.app.analytics.TrendRepository
import dev.expensetracker.app.analytics.previousMonth
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.entity.BudgetEntity
import dev.expensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.expensetracker.app.ui.components.CategoryAvatar
import dev.expensetracker.app.ui.components.CategoryDonut
import dev.expensetracker.app.ui.components.CategoryLookup
import dev.expensetracker.app.ui.components.EmptyState
import dev.expensetracker.app.ui.components.MonthSelector
import dev.expensetracker.app.ui.components.SegmentedTabs
import dev.expensetracker.app.ui.components.TransactionRow
import dev.expensetracker.app.ui.components.displayLabel
import dev.expensetracker.app.ui.components.rolledUpForRing
import dev.expensetracker.app.ui.format.toRupeeDisplay
import dev.expensetracker.app.ui.home.toYearMonth
import dev.expensetracker.app.ui.theme.BackgroundBase
import dev.expensetracker.app.ui.theme.DangerRed
import dev.expensetracker.app.ui.theme.Divider
import dev.expensetracker.app.ui.theme.AccentPrimary
import dev.expensetracker.app.ui.theme.PositiveGreen
import dev.expensetracker.app.ui.theme.Spacing
import dev.expensetracker.app.ui.theme.SurfaceCard
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.theme.TextTertiary
import dev.expensetracker.app.ui.theme.toCategoryColor
import java.time.YearMonth

private val tabs = listOf("Activity", "Categories", "Merchants")

@Composable
fun SpendsScreen(
    repository: ExpenseRepository,
    selectedMonth: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onEditBudget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthRange = remember(selectedMonth) { MonthRange.forMonth(selectedMonth) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val earliestMillis by repository.observeEarliestTransactionMillis().collectAsState(initial = null)
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val lookup = remember(categories) { CategoryLookup.from(categories) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "All spends",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )

        MonthSelector(
            selected = selectedMonth,
            earliestMonth = earliestMillis?.toYearMonth(),
            onSelect = onSelectMonth,
        )

        SegmentedTabs(
            options = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        when (selectedTab) {
            0 -> TransactionsTab(repository, monthRange, lookup, onOpenTransaction)
            1 -> CategoriesTab(repository, monthRange, selectedMonth, onOpenReview, onEditBudget)
            else -> MerchantsTab(repository, monthRange, selectedMonth)
        }
    }
}

@Composable
private fun TransactionsTab(
    repository: ExpenseRepository,
    monthRange: MonthRange,
    lookup: CategoryLookup,
    onOpenTransaction: (Long) -> Unit,
) {
    val transactions by repository.observeTransactionsForRange(monthRange.startMillis, monthRange.endMillis)
        .collectAsState(initial = emptyList())

    if (transactions.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.ReceiptLong,
            title = "No transactions in ${monthRange.yearMonth.displayLabel()}",
            message = "Pick another month, or scan your inbox from Settings to import older messages.",
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        items(transactions, key = { it.id }) { transaction ->
            TransactionRow(
                transaction = transaction,
                categories = lookup,
                onClick = { onOpenTransaction(transaction.id) },
            )
        }
        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun CategoriesTab(
    repository: ExpenseRepository,
    monthRange: MonthRange,
    selectedMonth: YearMonth,
    onOpenReview: () -> Unit,
    onEditBudget: (String) -> Unit,
) {
    val breakdownRepo = remember(repository) { CategoryBreakdownRepository(repository) }
    val trendRepo = remember(repository) { TrendRepository(repository) }
    val slices by breakdownRepo.observe(monthRange).collectAsState(initial = emptyList())
    val trend by trendRepo.observeSpendTrend(monthRange, monthRange.previousMonth())
        .collectAsState(initial = MonthTrend(0, 0))
    val budgets by repository.observeBudgetsForMonth(monthRange.yearMonth.toString())
        .collectAsState(initial = emptyList<BudgetEntity>())
    val budgetByCategory = remember(budgets) { budgets.associateBy { it.category } }

    // The ring shows a readable subset; the list below shows every category, and both sum to the
    // same total (blueprints/spend-analytics-and-budgets.md).
    val ringSlices = remember(slices) { slices.rolledUpForRing() }
    val uncategorized = remember(slices) { slices.firstOrNull { it.categoryId == UNKNOWN_CATEGORY_ID } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CategoryDonut(slices = ringSlices, monthLabel = selectedMonth.displayLabel())
            }
        }

        if (slices.isNotEmpty()) {
            item { TrendPill(trend) }
        }

        // An uncategorized pile is the most common reason this chart looks like one flat blob, so
        // it gets an explicit route to fixing it rather than sitting silently in the legend.
        if (uncategorized != null && uncategorized.percentOfTotal > 0) {
            item { UncategorizedCallout(uncategorized, onOpenReview) }
        }

        items(slices, key = { it.categoryId }) { slice ->
            CategoryBreakdownRow(
                slice = slice,
                budget = budgetByCategory[slice.categoryId],
                onEditBudget = { onEditBudget(slice.categoryId) },
            )
        }

        if (slices.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.DonutLarge,
                    title = "No spends to break down",
                    message = "Once ${selectedMonth.displayLabel()} has spending, you will see " +
                        "where every rupee went.",
                )
            }
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun TrendPill(trend: MonthTrend) {
    val change = trend.percentChange ?: return
    val up = change >= 0
    Row(
        modifier = Modifier
            .background(SurfaceCard, MaterialTheme.shapes.small)
            .border(1.dp, Divider, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (up) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
            contentDescription = null,
            tint = if (up) DangerRed else PositiveGreen,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (up) {
                "Spending is up ${"%.0f".format(change)}% vs last month"
            } else {
                "Spending is down ${"%.0f".format(-change)}% vs last month"
            },
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

@Composable
private fun UncategorizedCallout(slice: CategorySlice, onOpenReview: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentPrimary.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .border(1.dp, AccentPrimary.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpenReview)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AccentPrimary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PriorityHigh,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = Spacing.md)) {
            Text(
                text = "${slice.totalMinor.toRupeeDisplay()} uncategorized",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                text = "That's ${"%.0f".format(slice.percentOfTotal)}% of this month. " +
                    "Categorize it to sharpen the chart.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    slice: CategorySlice,
    budget: BudgetEntity?,
    onEditBudget: () -> Unit,
) {
    val color = slice.colorArgb.toCategoryColor()
    val progress = budget?.let {
        BudgetProgressCalculator.calculate(slice.categoryId, it.limitMinor, slice.totalMinor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CategoryAvatar(iconKey = slice.iconKey, color = color, size = 36.dp)
                Column(modifier = Modifier.padding(start = Spacing.md)) {
                    Text(
                        text = slice.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${"%.0f".format(slice.percentOfTotal)}% \u00B7 " +
                            "${slice.transactionCount} txn${if (slice.transactionCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
            Text(
                text = slice.totalMinor.toRupeeDisplay(),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
            )
        }

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.progressFraction.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (progress.isOverBudget) DangerRed else AccentPrimary,
                trackColor = Divider,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditBudget),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (progress.isOverBudget) {
                        "Over budget by ${(progress.actualMinor - progress.limitMinor).toRupeeDisplay()}"
                    } else {
                        "${(progress.limitMinor - progress.actualMinor).toRupeeDisplay()} left"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.isOverBudget) DangerRed else TextSecondary,
                )
                Text(
                    text = "Budget ${progress.limitMinor.toRupeeDisplay()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        } else {
            Text(
                text = "Set a budget",
                style = MaterialTheme.typography.labelMedium,
                color = AccentPrimary,
                modifier = Modifier
                    .clickable(onClick = onEditBudget)
                    .padding(vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun MerchantsTab(
    repository: ExpenseRepository,
    monthRange: MonthRange,
    selectedMonth: YearMonth,
) {
    val merchantRepo = remember(repository) { MerchantBreakdownRepository(repository) }
    val merchants by merchantRepo.observe(monthRange).collectAsState(initial = emptyList())
    val topSpend = merchants.maxOfOrNull { it.totalMinor } ?: 1L

    if (merchants.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Storefront,
            title = "No merchants yet",
            message = "${selectedMonth.displayLabel()} has no spending to rank.",
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(merchants, key = { it.merchantKey }) { merchant ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, MaterialTheme.shapes.medium)
                    .border(1.dp, Divider, MaterialTheme.shapes.medium)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = merchant.merchantRaw,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = merchant.totalMinor.toRupeeDisplay(),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                // Relative bar rather than a percentage, because the useful comparison here is
                // "which merchant dominates", not the exact share of total.
                LinearProgressIndicator(
                    progress = { (merchant.totalMinor.toFloat() / topSpend.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentPrimary,
                    trackColor = Divider,
                )
                Text(
                    text = "${merchant.count} transaction${if (merchant.count == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }
        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}
