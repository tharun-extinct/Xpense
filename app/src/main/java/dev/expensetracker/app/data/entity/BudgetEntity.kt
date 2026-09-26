package dev.expensetracker.app.data.entity

import androidx.room.Entity

/** Scoped to a calendar month ("YYYY-MM", device-local) and category. */
@Entity(tableName = "budgets", primaryKeys = ["month", "category"])
data class BudgetEntity(
    val month: String,
    val category: String,
    val limitMinor: Long,
)
