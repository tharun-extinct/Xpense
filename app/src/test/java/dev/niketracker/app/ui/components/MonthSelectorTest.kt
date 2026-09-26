package dev.expensetracker.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.expensetracker.app.ui.theme.ExpenseTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * The month filter's bounds come from the data, so the arrows must be inert at the edges rather
 * than stepping onto a month that is guaranteed to be empty
 * (blueprints/design-system-and-navigation.md).
 */
@RunWith(RobolectricTestRunner::class)
class MonthSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val currentMonth: YearMonth = YearMonth.now()

    private fun setUp(selected: YearMonth, earliest: YearMonth?, onSelect: (YearMonth) -> Unit) {
        composeRule.setContent {
            ExpenseTrackerTheme {
                MonthSelector(selected = selected, earliestMonth = earliest, onSelect = onSelect)
            }
        }
    }

    @Test
    fun `the selected month is shown in words`() {
        setUp(currentMonth, earliest = currentMonth.minusMonths(3)) {}

        composeRule.onNodeWithText(currentMonth.displayLabel()).assertIsDisplayed()
    }

    @Test
    fun `the next arrow cannot move past the current month`() {
        var selected: YearMonth? = null
        setUp(currentMonth, earliest = currentMonth.minusMonths(3)) { selected = it }

        composeRule.onNodeWithContentDescription("Next month").performClick()

        assertNull(selected)
    }

    @Test
    fun `the previous arrow cannot move before the earliest recorded month`() {
        var selected: YearMonth? = null
        val earliest = currentMonth.minusMonths(2)
        setUp(earliest, earliest = earliest) { selected = it }

        composeRule.onNodeWithContentDescription("Previous month").performClick()

        assertNull(selected)
    }

    @Test
    fun `stepping back within the data range reports the previous month`() {
        var selected: YearMonth? = null
        setUp(currentMonth, earliest = currentMonth.minusMonths(3)) { selected = it }

        composeRule.onNodeWithContentDescription("Previous month").performClick()

        assertEquals(currentMonth.minusMonths(1), selected)
    }

    @Test
    fun `an empty database pins the selector to the current month`() {
        var selected: YearMonth? = null
        setUp(currentMonth, earliest = null) { selected = it }

        composeRule.onNodeWithContentDescription("Previous month").performClick()
        composeRule.onNodeWithContentDescription("Next month").performClick()

        assertNull(selected)
    }

    @Test
    fun `expanding the picker offers a month that can be chosen directly`() {
        var selected: YearMonth? = null
        val target = currentMonth.minusMonths(2)
        setUp(currentMonth, earliest = currentMonth.minusMonths(5)) { selected = it }

        composeRule.onNodeWithContentDescription("Choose a month").performClick()
        composeRule.onNodeWithText(target.shortLabelForTest()).performClick()

        assertEquals(target, selected)
    }
}

/** Mirrors the private label used by the grid cells. */
private fun YearMonth.shortLabelForTest(): String =
    month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + (year % 100)
