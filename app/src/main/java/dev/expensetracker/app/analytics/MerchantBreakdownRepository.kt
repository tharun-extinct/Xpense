package dev.expensetracker.app.analytics

import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.dao.MerchantSpend
import kotlinx.coroutines.flow.Flow

/** Powers the "Merchants" tab: month-scoped DEBIT spend grouped by merchant, descending. */
class MerchantBreakdownRepository(private val repository: ExpenseRepository) {
    fun observe(range: MonthRange): Flow<List<MerchantSpend>> =
        repository.observeMerchantBreakdown(range.startMillis, range.endMillis)
}
