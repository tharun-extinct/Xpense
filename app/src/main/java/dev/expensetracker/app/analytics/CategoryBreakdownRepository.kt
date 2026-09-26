package dev.expensetracker.app.analytics

import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * One category's share of a month's spend, already carrying everything needed to draw it.
 *
 * Display attributes are resolved here rather than in the UI because a category's name and color
 * are row data (architecture.md #data-representation) — no screen may derive a label from an id.
 */
data class CategorySlice(
    val categoryId: String,
    val name: String,
    val colorArgb: Long,
    val iconKey: String,
    val totalMinor: Long,
    val transactionCount: Int,
    val percentOfTotal: Double,
)

/**
 * Per-category DEBIT sums and percentages for the donut and the category list.
 *
 * Percentages are Double for display only; they are never fed back into stored data
 * (architecture.md #data-representation).
 */
class CategoryBreakdownRepository(private val repository: ExpenseRepository) {

    fun observe(range: MonthRange): Flow<List<CategorySlice>> =
        combine(
            repository.observeCategoryBreakdown(range.startMillis, range.endMillis),
            repository.observeCategories(),
        ) { spends, categories ->
            val byId = categories.associateBy(CategoryEntity::id)
            // coerceAtLeast(1) only guards division; an empty month yields an empty list anyway.
            val total = spends.sumOf { it.totalMinor }.coerceAtLeast(1)
            spends.map { spend ->
                val category = byId[spend.category]
                CategorySlice(
                    categoryId = spend.category,
                    // An id with no row is only reachable through manual database edits. Showing the
                    // raw id keeps the amount in the total instead of silently losing money from it.
                    name = category?.name ?: spend.category,
                    colorArgb = category?.colorArgb ?: FALLBACK_COLOR_ARGB,
                    iconKey = category?.iconKey ?: "unknown",
                    totalMinor = spend.totalMinor,
                    transactionCount = spend.count,
                    percentOfTotal = spend.totalMinor * 100.0 / total,
                )
            }
        }

    private companion object {
        const val FALLBACK_COLOR_ARGB = 0xFF6B7280
    }
}
