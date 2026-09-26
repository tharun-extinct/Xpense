package dev.expensetracker.app.analytics

import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthRangeTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `a transaction at the very start of the month is included`() {
        val range = MonthRange.forMonth(YearMonth.of(2026, 9), zone)
        val startOfMonth = range.startMillis
        assertTrue(startOfMonth in range.startMillis..range.endMillis)
    }

    @Test
    fun `a transaction at the last millisecond of the month is included`() {
        val range = MonthRange.forMonth(YearMonth.of(2026, 9), zone)
        assertTrue(range.endMillis in range.startMillis..range.endMillis)
    }

    @Test
    fun `the first millisecond of the next month is excluded`() {
        val septemberRange = MonthRange.forMonth(YearMonth.of(2026, 9), zone)
        val octoberRange = MonthRange.forMonth(YearMonth.of(2026, 10), zone)
        assertTrue(octoberRange.startMillis > septemberRange.endMillis)
    }

    @Test
    fun `previousMonth returns the immediately preceding calendar month`() {
        val october = MonthRange.forMonth(YearMonth.of(2026, 10), zone)
        val september = october.previousMonth(zone)
        assertTrue(september.yearMonth == YearMonth.of(2026, 9))
    }
}
