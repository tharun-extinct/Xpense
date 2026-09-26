package dev.expensetracker.app.data

import dev.expensetracker.app.data.entity.BuiltInCategories
import dev.expensetracker.app.data.entity.MerchantRuleEntity
import dev.expensetracker.app.data.entity.RuleSource
import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import dev.expensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the user-extensible category set from architecture.md #data-representation, including the
 * deletion policy that replaces the missing SQL foreign key.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
        repository = ExpenseRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() = repository.seedCategoriesIfMissing()

    private fun transaction(category: String) = TransactionEntity(
        contentHash = "hash-$category",
        amountMinor = 10_000L,
        direction = TransactionDirection.DEBIT,
        merchantRaw = "Amazon",
        merchantKey = "amazon",
        category = category,
        accountIssuer = "HDFC",
        accountTail = "1234",
        refNo = "REF1",
        balanceMinor = null,
        occurredAtUtcMillis = 1_726_000_000_000L,
        state = TransactionState.CONFIRMED,
        createdAtUtcMillis = 1_726_000_000_000L,
    )

    @Test
    fun `seeding twice leaves exactly one row per built-in`() = runBlocking {
        seed()
        seed()

        assertEquals(BuiltInCategories.all.size, db.categoryDao().count())
    }

    @Test
    fun `seeding does not overwrite a user-renamed built-in`() = runBlocking {
        seed()
        val food = db.categoryDao().findById("FOOD_AND_DRINKS")!!
        repository.updateCategory(food, newName = "Eating out", colorArgb = food.colorArgb, iconKey = food.iconKey)

        seed()

        assertEquals("Eating out", db.categoryDao().findById("FOOD_AND_DRINKS")?.name)
    }

    @Test
    fun `a custom category gets a generated id and is selectable`() = runBlocking {
        seed()
        val result = repository.createCustomCategory("Pet care", colorArgb = 0xFF22C55E, iconKey = "other")
        assertEquals(CategoryResult.Success, result)

        val created = db.categoryDao().observeAll().first().first { it.name == "Pet care" }
        assertTrue(created.id.startsWith("custom_"))
        assertFalse(created.isBuiltIn)
        assertTrue(db.categoryDao().observeSelectable().first().any { it.id == created.id })
    }

    @Test
    fun `a blank or duplicate name is rejected`() = runBlocking {
        seed()
        assertEquals(CategoryResult.InvalidName, repository.createCustomCategory("   ", 0xFF22C55E, "other"))
        // Built-ins are seeded with display names, so this collides with the seeded "Shopping".
        assertEquals(CategoryResult.DuplicateName, repository.createCustomCategory("shopping", 0xFF22C55E, "other"))
    }

    @Test
    fun `archiving hides a category from pickers but keeps it resolvable`() = runBlocking {
        seed()
        assertEquals(CategoryResult.Success, repository.setCategoryArchived("FUEL", archived = true))

        assertFalse(db.categoryDao().observeSelectable().first().any { it.id == "FUEL" })
        assertNotNull(db.categoryDao().findById("FUEL"))
    }

    @Test
    fun `the uncategorized bucket can never be archived`() = runBlocking {
        seed()
        assertEquals(CategoryResult.NotAllowed, repository.setCategoryArchived(UNKNOWN_CATEGORY_ID, archived = true))
        assertFalse(db.categoryDao().findById(UNKNOWN_CATEGORY_ID)!!.isArchived)
    }

    @Test
    fun `a built-in category can never be deleted`() = runBlocking {
        seed()
        assertEquals(CategoryResult.NotAllowed, repository.deleteCategory("SHOPPING"))
        assertNotNull(db.categoryDao().findById("SHOPPING"))
    }

    @Test
    fun `a referenced custom category is refused deletion so history is not orphaned`() = runBlocking {
        seed()
        repository.createCustomCategory("Pet care", 0xFF22C55E, "other")
        val custom = db.categoryDao().observeAll().first().first { it.name == "Pet care" }
        db.transactionDao().insert(transaction(custom.id))

        val result = repository.deleteCategory(custom.id)
        assertTrue(result is CategoryResult.InUse)
        assertEquals(1, (result as CategoryResult.InUse).referenceCount)
        assertNotNull(db.categoryDao().findById(custom.id))
    }

    @Test
    fun `a budget or merchant rule also blocks deletion`() = runBlocking {
        seed()
        repository.createCustomCategory("Pet care", 0xFF22C55E, "other")
        val custom = db.categoryDao().observeAll().first().first { it.name == "Pet care" }
        db.merchantRuleDao().upsert(MerchantRuleEntity("vet", custom.id, RuleSource.USER))

        assertTrue(repository.deleteCategory(custom.id) is CategoryResult.InUse)
    }

    @Test
    fun `an unreferenced custom category is deleted`() = runBlocking {
        seed()
        repository.createCustomCategory("Pet care", 0xFF22C55E, "other")
        val custom = db.categoryDao().observeAll().first().first { it.name == "Pet care" }

        assertEquals(CategoryResult.Success, repository.deleteCategory(custom.id))
        assertNull(db.categoryDao().findById(custom.id))
    }

    @Test
    fun `recategorizing promotes a needs-review transaction and can teach the merchant`() = runBlocking {
        seed()
        val id = db.transactionDao().insert(
            transaction(UNKNOWN_CATEGORY_ID).copy(state = TransactionState.NEEDS_REVIEW),
        )

        repository.recategorizeTransaction(id, "SHOPPING", alsoTeachMerchant = true)

        val updated = db.transactionDao().getById(id)!!
        assertEquals("SHOPPING", updated.category)
        assertEquals(TransactionState.CONFIRMED, updated.state)
        assertTrue(updated.isUserEdited)
        assertEquals("SHOPPING", repository.findMerchantRule("amazon")?.category)
        assertEquals(RuleSource.USER, repository.findMerchantRule("amazon")?.source)
    }

    @Test
    fun `recategorizing without teaching leaves the merchant rules untouched`() = runBlocking {
        seed()
        val id = db.transactionDao().insert(
            transaction(UNKNOWN_CATEGORY_ID).copy(state = TransactionState.NEEDS_REVIEW),
        )

        repository.recategorizeTransaction(id, "SHOPPING", alsoTeachMerchant = false)

        assertNull(repository.findMerchantRule("amazon"))
    }
}
