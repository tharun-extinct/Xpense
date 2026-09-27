package dev.xpensetracker.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.xpensetracker.app.analytics.MonthRange
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.AccountEntity
import dev.xpensetracker.app.ui.components.AccountCard
import dev.xpensetracker.app.ui.components.EmptyState
import dev.xpensetracker.app.ui.components.displayLabel
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextTertiary
import java.time.YearMonth

@Composable
fun AccountsScreen(
    repository: ExpenseRepository,
    selectedMonth: YearMonth,
    modifier: Modifier = Modifier,
) {
    val accounts by repository.observeAccounts().collectAsState(initial = emptyList())
    val monthRange = remember(selectedMonth) { MonthRange.forMonth(selectedMonth) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(horizontal = Spacing.gutter),
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Text(text = "Accounts", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(
            text = "Detected from your bank messages",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )

        if (accounts.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AccountBalanceWallet,
                title = "No accounts yet",
                message = "An account appears here as soon as a bank SMS mentioning it is parsed.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(accounts, key = { "${it.issuer}-${it.accountTail}" }) { account ->
                AccountCardWithSpend(repository, account, monthRange, selectedMonth)
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

/**
 * Per-account month spend is its own Flow per row rather than one aggregate query, because the
 * account list is short and this keeps each card independently reactive.
 */
@Composable
private fun AccountCardWithSpend(
    repository: ExpenseRepository,
    account: AccountEntity,
    monthRange: MonthRange,
    selectedMonth: YearMonth,
) {
    val spend by repository
        .observeAccountSpendForRange(
            issuer = account.issuer,
            accountTail = account.accountTail,
            startMillis = monthRange.startMillis,
            endMillis = monthRange.endMillis,
        )
        .collectAsState(initial = 0L)

    AccountCard(
        account = account,
        monthSpendMinor = spend,
        monthLabel = selectedMonth.displayLabel(),
    )
}
