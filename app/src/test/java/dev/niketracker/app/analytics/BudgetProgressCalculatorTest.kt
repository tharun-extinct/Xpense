package dev.expensetracker.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetProgressCalculatorTest {

    @Test
    fun `progress fraction is actual over limit`() {
        val progress = BudgetProgressCalculator.calculate("SHOPPING", limitMinor = 10_000, actualMinor = 5_000)
        assertEquals(0.5, progress.progressFraction, 0.0001)
        assertFalse(progress.isOverBudget)
    }

    @Test
    fun `progress fraction is clamped to 1 when over budget`() {
        val progress = BudgetProgressCalculator.calculate("SHOPPING", limitMinor = 10_000, actualMinor = 15_000)
        assertEquals(1.0, progress.progressFraction, 0.0001)
        assertTrue(progress.isOverBudget)
    }

    @Test
    fun `zero limit never divides by zero`() {
        val progress = BudgetProgressCalculator.calculate("SHOPPING", limitMinor = 0, actualMinor = 500)
        assertEquals(0.0, progress.progressFraction, 0.0001)
    }
}
