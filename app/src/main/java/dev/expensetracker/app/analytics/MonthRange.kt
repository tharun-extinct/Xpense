package dev.expensetracker.app.analytics

import java.time.YearMonth
import java.time.ZoneId

/**
 * The single place month-boundary conversion happens, per architecture.md #data-representation.
 * Every screen that shows "this month" goes through this so Home and Spends can never disagree.
 */
data class MonthRange(val yearMonth: YearMonth, val startMillis: Long, val endMillis: Long) {
    val label: String get() = yearMonth.month.name.lowercase()
        .replaceFirstChar { it.uppercase() } + " " + yearMonth.year

    companion object {
        fun forMonth(yearMonth: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): MonthRange {
            val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
            return MonthRange(yearMonth, start, end)
        }

        fun current(zoneId: ZoneId = ZoneId.systemDefault()): MonthRange = forMonth(YearMonth.now(zoneId), zoneId)
    }
}

fun MonthRange.previousMonth(zoneId: ZoneId = ZoneId.systemDefault()): MonthRange =
    MonthRange.forMonth(yearMonth.minusMonths(1), zoneId)
