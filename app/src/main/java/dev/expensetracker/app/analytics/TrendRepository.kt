package dev.expensetracker.app.analytics

import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.entity.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class MonthTrend(val currentMinor: Long, val previousMinor: Long) {
    /** Positive means spending increased vs. last month. Null when there's no prior-month baseline. */
    val percentChange: Double?
        get() = if (previousMinor <= 0) null else ((currentMinor - previousMinor) * 100.0 / previousMinor)
}

/** Current-vs-prior-month spend comparison only; v1 scope per blueprints/spend-analytics-and-budgets.md. */
class TrendRepository(private val repository: ExpenseRepository) {

    fun observeSpendTrend(current: MonthRange, previous: MonthRange): Flow<MonthTrend> {
        val currentTotal = repository.observeTotalForRange(TransactionDirection.DEBIT, current.startMillis, current.endMillis)
        val previousTotal = repository.observeTotalForRange(TransactionDirection.DEBIT, previous.startMillis, previous.endMillis)
        return combine(currentTotal, previousTotal) { c, p -> MonthTrend(c, p) }
    }
}
