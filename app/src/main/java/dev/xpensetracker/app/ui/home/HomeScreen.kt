package dev.xpensetracker.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.xpensetracker.app.analytics.MonthRange
import dev.xpensetracker.app.analytics.SpendSummary
import dev.xpensetracker.app.analytics.SpendSummaryRepository
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.BudgetEntity
import dev.xpensetracker.app.ui.components.AccountCard
import dev.xpensetracker.app.ui.components.CategoryLookup
import dev.xpensetracker.app.ui.components.EmptyState
import dev.xpensetracker.app.ui.components.MonthSelector
import dev.xpensetracker.app.ui.components.NeedsReviewBanner
import dev.xpensetracker.app.ui.components.SectionHeader
import dev.xpensetracker.app.ui.components.SpendRing
import dev.xpensetracker.app.ui.components.StatCard
import dev.xpensetracker.app.ui.components.TransactionRow
import dev.xpensetracker.app.ui.components.displayLabel
import dev.xpensetracker.app.ui.format.toRupeeDisplay
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.DangerRed
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.PositiveGreen
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextTertiary
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun HomeScreen(
    repository: ExpenseRepository,
    selectedMonth: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenSpends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthRange = remember(selectedMonth) { MonthRange.forMonth(selectedMonth) }
    val summaryRepo = remember(repository) { SpendSummaryRepository(repository) }

    val summary by summaryRepo.observe(monthRange).collectAsState(initial = SpendSummary(0, 0))
    val transactions by repository
        .observeTransactionsForRange(monthRange.startMillis, monthRange.endMillis)
        .collectAsState(initial = emptyList())
    val needsReviewCount by repository.observeNeedsReviewCount().collectAsState(initial = 0)
    val accounts by repository.observeAccounts().collectAsState(initial = emptyList())
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val budgets by repository.observeBudgetsForMonth(monthRange.yearMonth.toString())
        .collectAsState(initial = emptyList<BudgetEntity>())
    val earliestMillis by repository.observeEarliestTransactionMillis().collectAsState(initial = null)

    val lookup = remember(categories) { CategoryLookup.from(categories) }
    val earliestMonth = remember(earliestMillis) { earliestMillis?.toYearMonth() }

    // The ring's arc compares this month's spend against the sum of this month's budgets. With no
    // budget set there is nothing to measure against, so the arc is omitted rather than shown at
    // zero, which would read as "you have spent nothing".
    val budgetTotalMinor = remember(budgets) { budgets.sumOf { it.limitMinor } }
    val budgetFraction = if (budgetTotalMinor > 0) {
        summary.spendsMinor.toFloat() / budgetTotalMinor.toFloat()
    } else {
        null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { Spacer(Modifier.height(Spacing.sm)) }

        item {
            Column {
                Text(
                    text = "Your money",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "Built from your SMS, stored only on this device",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
        }

        item {
            MonthSelector(
                selected = selectedMonth,
                earliestMonth = earliestMonth,
                onSelect = onSelectMonth,
            )
        }

        if (needsReviewCount > 0) {
            item { NeedsReviewBanner(count = needsReviewCount, onClick = onOpenReview) }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SpendRing(
                    spendsMinor = summary.spendsMinor,
                    budgetProgressFraction = budgetFraction,
                    monthLabel = selectedMonth.displayLabel(),
                    caption = budgetFraction?.let { fraction ->
                        val percent = (fraction * 100).toInt()
                        "$percent% of ${budgetTotalMinor.toRupeeDisplay()} budget"
                    },
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatCard(
                    label = "Spent",
                    value = summary.spendsMinor.toRupeeDisplay(),
                    accent = DangerRed,
                    icon = Icons.Filled.ArrowUpward,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Received",
                    value = summary.incomeMinor.toRupeeDisplay(),
                    accent = PositiveGreen,
                    icon = Icons.Filled.ArrowDownward,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionHeader(
                title = "Transactions",
                subtitle = selectedMonth.displayLabel(),
                trailing = {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentPrimary,
                        modifier = Modifier
                            .clickable(onClick = onOpenSpends)
                            .padding(Spacing.sm),
                    )
                },
            )
        }

        if (transactions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.ReceiptLong,
                    title = "Nothing in ${selectedMonth.displayLabel()}",
                    message = "Transactions appear here automatically as bank and UPI messages " +
                        "arrive. You can also scan your existing inbox from Settings.",
                )
            }
        } else {
            items(transactions.take(HOME_TRANSACTION_LIMIT), key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    categories = lookup,
                    onClick = { onOpenTransaction(transaction.id) },
                )
            }
            if (transactions.size > HOME_TRANSACTION_LIMIT) {
                item {
                    Text(
                        text = "View all ${transactions.size} transactions",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSpends)
                            .padding(vertical = Spacing.md),
                    )
                }
            }
        }

        if (accounts.isNotEmpty()) {
            item { SectionHeader(title = "Accounts") }
            items(accounts, key = { "${it.issuer}-${it.accountTail}" }) { account ->
                AccountCard(account = account)
            }
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

private const val HOME_TRANSACTION_LIMIT = 8

internal fun Long.toYearMonth(): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
