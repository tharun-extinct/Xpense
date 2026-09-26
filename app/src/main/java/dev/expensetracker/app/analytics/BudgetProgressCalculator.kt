package dev.expensetracker.app.analytics

data class BudgetProgress(
    /** A `categories` row id. */
    val category: String,
    val limitMinor: Long,
    val actualMinor: Long,
) {
    /** Always recomputed from current data, never stored (blueprints/spend-analytics-and-budgets.md). */
    val progressFraction: Double
        get() = if (limitMinor <= 0) 0.0 else (actualMinor.toDouble() / limitMinor).coerceIn(0.0, 1.0)

    val isOverBudget: Boolean get() = actualMinor > limitMinor
}

/** Pure function: limit-vs-actual, no I/O. */
object BudgetProgressCalculator {
    fun calculate(category: String, limitMinor: Long, actualMinor: Long): BudgetProgress =
        BudgetProgress(category, limitMinor, actualMinor)
}
