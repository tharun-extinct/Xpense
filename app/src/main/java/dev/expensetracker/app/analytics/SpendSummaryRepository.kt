package dev.expensetracker.app.analytics

import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.entity.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SpendSummary(val spendsMinor: Long, val incomeMinor: Long)

/** Month-scoped spend/income totals; CONFIRMED transactions only (architecture.md #state-lifecycle). */
class SpendSummaryRepository(private val repository: ExpenseRepository) {

    fun observe(range: MonthRange): Flow<SpendSummary> {
        val spends = repository.observeTotalForRange(TransactionDirection.DEBIT, range.startMillis, range.endMillis)
        val income = repository.observeTotalForRange(TransactionDirection.CREDIT, range.startMillis, range.endMillis)
        return combine(spends, income) { s, i -> SpendSummary(s, i) }
    }
}
