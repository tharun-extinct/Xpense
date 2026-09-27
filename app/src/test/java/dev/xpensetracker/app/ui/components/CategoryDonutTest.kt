package dev.xpensetracker.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.xpensetracker.app.analytics.CategorySlice
import dev.xpensetracker.app.ui.theme.ExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The donut previously rendered as an unexplained circle. These cover the three states that made it
 * look broken: no data at all, a single category, and a normal multi-category month.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryDonutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun slice(name: String, totalMinor: Long, percent: Double, count: Int = 1) = CategorySlice(
        categoryId = name.uppercase(),
        name = name,
        colorArgb = 0xFF22C55E,
        iconKey = "other",
        totalMinor = totalMinor,
        transactionCount = count,
        percentOfTotal = percent,
    )

    private fun render(slices: List<CategorySlice>, monthLabel: String = "September 2026") {
        composeRule.setContent {
            ExpenseTrackerTheme {
                CategoryDonut(slices = slices, monthLabel = monthLabel)
            }
        }
    }

    @Test
    fun `an empty month explains itself instead of drawing a bare circle`() {
        render(emptyList())

        composeRule.onNodeWithText("No spends in").assertIsDisplayed()
        composeRule.onNodeWithText("September 2026").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Category breakdown for September 2026: no spends recorded.")
            .assertIsDisplayed()
    }

    @Test
    fun `a single-category month still reports its total in the centre`() {
        render(listOf(slice("Shopping", 150_00L, 100.0)))

        composeRule.onNodeWithText("Total spent").assertIsDisplayed()
        composeRule.onNodeWithText("1 txn in 1 category").assertIsDisplayed()
    }

    @Test
    fun `a multi-category month counts every transaction and category`() {
        render(
            listOf(
                slice("Shopping", 100_00L, 50.0, count = 2),
                slice("Fuel", 60_00L, 30.0, count = 1),
                slice("Groceries", 40_00L, 20.0, count = 3),
            ),
        )

        composeRule.onNodeWithText("6 txns in 3 categories").assertIsDisplayed()
    }

    @Test
    fun `the ring is described for screen readers by its largest slices`() {
        render(listOf(slice("Shopping", 100_00L, 60.0), slice("Fuel", 66_00L, 40.0)))

        composeRule
            .onNodeWithContentDescription("Shopping 60 percent", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `slices totalling zero fall back to the empty state rather than dividing by nothing`() {
        render(listOf(slice("Shopping", 0L, 0.0)))

        composeRule.onNodeWithText("No spends in").assertIsDisplayed()
    }
}
