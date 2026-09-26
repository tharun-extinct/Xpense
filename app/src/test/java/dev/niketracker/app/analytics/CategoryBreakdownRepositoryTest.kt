package dev.expensetracker.app.analytics

import dev.expensetracker.app.data.AppDatabase
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.RoomTestDatabase
import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import dev.expensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.expensetracker.app.ui.components.ROLLUP_SLICE_ID
import dev.expensetracker.app.ui.components.rolledUpForRing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * The donut renders exactly what this emits, so the slice metadata and percentages are tested here
 * rather than through the canvas (blueprints/spend-analytics-and-budgets.md).
 */
@RunWith(RobolectricTestRunner::class)
class CategoryBreakdownRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository
    private lateinit var breakdown: CategoryBreakdownRepository

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
        repository = ExpenseRepository(db)
        breakdown = CategoryBreakdownRepository(repository)
        runBlocking { repository.seedCategoriesIfMissing() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // Widened past a single calendar month so the fixtures below do not depend on the clock.
    private val range = MonthRange(YearMonth.of(2026, 9), 0L, Long.MAX_VALUE)

    private suspend fun insert(hash: String, category: String, amountMinor: Long) {
        db.transactionDao().insert(
            TransactionEntity(
                contentHash = hash,
                amountMinor = amountMinor,
                direction = TransactionDirection.DEBIT,
                merchantRaw = "Merchant $hash",
                merchantKey = "merchant-$hash",
                category = category,
                accountIssuer = "HDFC",
                accountTail = "1234",
                refNo = null,
                balanceMinor = null,
                occurredAtUtcMillis = 1_726_000_000_000L,
                state = TransactionState.CONFIRMED,
                createdAtUtcMillis = 1_726_000_000_000L,
            ),
        )
    }

    @Test
    fun `an empty month emits no slices rather than a zero-value slice`() = runBlocking {
        assertTrue(breakdown.observe(range).first().isEmpty())
    }

    @Test
    fun `slices carry the category name and color from the table`() = runBlocking {
        insert("h1", "SHOPPING", 10_000L)

        val slice = breakdown.observe(range).first().single()
        val row = db.categoryDao().findById("SHOPPING")!!
        assertEquals(row.name, slice.name)
        assertEquals(row.colorArgb, slice.colorArgb)
        assertEquals(row.iconKey, slice.iconKey)
        assertEquals(1, slice.transactionCount)
    }

    @Test
    fun `percentages sum to one hundred`() = runBlocking {
        insert("h1", "SHOPPING", 10_000L)
        insert("h2", "FUEL", 20_000L)
        insert("h3", "GROCERIES", 70_000L)

        val slices = breakdown.observe(range).first()
        assertEquals(3, slices.size)
        assertEquals(100.0, slices.sumOf { it.percentOfTotal }, 0.0001)
        assertEquals(70.0, slices.first { it.categoryId == "GROCERIES" }.percentOfTotal, 0.0001)
    }

    @Test
    fun `uncategorized spend appears as its own slice instead of disappearing`() = runBlocking {
        insert("h1", UNKNOWN_CATEGORY_ID, 40_000L)
        insert("h2", "FUEL", 60_000L)

        val slices = breakdown.observe(range).first()
        val unknown = slices.first { it.categoryId == UNKNOWN_CATEGORY_ID }
        assertEquals(40_000L, unknown.totalMinor)
        assertEquals(40.0, unknown.percentOfTotal, 0.0001)
    }

    @Test
    fun `an id with no category row keeps its amount in the total`() = runBlocking {
        insert("h1", "GHOST_CATEGORY", 10_000L)

        val slice = breakdown.observe(range).first().single()
        assertEquals("GHOST_CATEGORY", slice.name)
        assertEquals(10_000L, slice.totalMinor)
    }

    @Test
    fun `the ring rollup preserves the total while capping the slice count`() {
        val slices = (1..12).map {
            CategorySlice(
                categoryId = "c$it",
                name = "Category $it",
                colorArgb = 0xFF22C55E,
                iconKey = "other",
                totalMinor = it * 1_000L,
                transactionCount = 1,
                percentOfTotal = 100.0 / 12,
            )
        }

        val rolled = slices.rolledUpForRing(maxSlices = 8)

        assertEquals(8, rolled.size)
        assertEquals(slices.sumOf { it.totalMinor }, rolled.sumOf { it.totalMinor })
        assertEquals(100.0, rolled.sumOf { it.percentOfTotal }, 0.0001)
        assertEquals(ROLLUP_SLICE_ID, rolled.last().categoryId)
        assertEquals("5 more", rolled.last().name)
    }

    @Test
    fun `the rollup is a no-op when the list already fits`() {
        val slices = (1..8).map {
            CategorySlice("c$it", "Category $it", 0xFF22C55E, "other", 1_000L, 1, 12.5)
        }

        assertEquals(slices, slices.rolledUpForRing(maxSlices = 8))
    }
}
