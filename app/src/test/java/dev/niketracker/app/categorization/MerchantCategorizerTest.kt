package dev.expensetracker.app.categorization

import dev.expensetracker.app.data.AppDatabase
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.RoomTestDatabase
import dev.expensetracker.app.data.entity.MerchantRuleEntity
import dev.expensetracker.app.data.entity.RuleSource
import dev.expensetracker.app.data.entity.TransactionState
import dev.expensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.expensetracker.app.parsing.ConfidenceTier
import dev.expensetracker.app.parsing.MessageType
import dev.expensetracker.app.parsing.ParsedTransaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verifies blueprints/merchant-categorization.md decision rules. */
@RunWith(RobolectricTestRunner::class)
class MerchantCategorizerTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository
    private lateinit var categorizer: MerchantCategorizer

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
        repository = ExpenseRepository(db)
        categorizer = MerchantCategorizer(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun parsed(merchant: String?, confidence: ConfidenceTier) = ParsedTransaction(
        messageType = MessageType.DEBIT,
        amountMinor = 10000L,
        merchantRaw = merchant,
        accountTail = "1234",
        refNo = "REF12345",
        balanceMinor = null,
        confidence = confidence,
        matchedRuleId = "test-rule",
    )

    @Test
    fun `a seed-rule merchant with HIGH confidence auto-confirms`() = runBlocking {
        db.merchantRuleDao().upsert(MerchantRuleEntity("amazon", "SHOPPING", RuleSource.SEED))

        val result = categorizer.categorize(parsed("Amazon", ConfidenceTier.HIGH))

        assertEquals("SHOPPING", result.category)
        assertEquals(TransactionState.CONFIRMED, result.state)
    }

    @Test
    fun `an unknown merchant always needs review regardless of confidence`() = runBlocking {
        val result = categorizer.categorize(parsed("Some Random Shop", ConfidenceTier.HIGH))

        assertEquals(UNKNOWN_CATEGORY_ID, result.category)
        assertEquals(TransactionState.NEEDS_REVIEW, result.state)
    }

    @Test
    fun `a USER rule wins over a SEED rule for the same merchant`() = runBlocking {
        db.merchantRuleDao().upsert(MerchantRuleEntity("swiggy", "FOOD_AND_DRINKS", RuleSource.SEED))
        repository.learnMerchantRule("swiggy", "OTHER")

        val result = categorizer.categorize(parsed("Swiggy", ConfidenceTier.HIGH))

        assertEquals("OTHER", result.category)
    }

    @Test
    fun `LOW confidence never auto-confirms even with a known rule`() = runBlocking {
        db.merchantRuleDao().upsert(MerchantRuleEntity("amazon", "SHOPPING", RuleSource.SEED))

        val result = categorizer.categorize(parsed("Amazon", ConfidenceTier.LOW))

        assertEquals(TransactionState.NEEDS_REVIEW, result.state)
    }

    @Test
    fun `editing a transaction category upserts a USER merchant rule`() = runBlocking {
        repository.learnMerchantRule("new merchant", "TRAVEL")

        val rule = repository.findMerchantRule("new merchant")
        assertEquals("TRAVEL", rule?.category)
        assertEquals(RuleSource.USER, rule?.source)
    }
}
