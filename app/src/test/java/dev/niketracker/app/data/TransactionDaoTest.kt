package dev.expensetracker.app.data

import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies architecture.md #identity-and-ownership (dedupe on content_hash) and
 * #state-lifecycle (a category edit updates exactly one row).
 */
@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleTransaction(hash: String, merchant: String = "Amazon", amount: Long = 50000L) = TransactionEntity(
        contentHash = hash,
        amountMinor = amount,
        direction = TransactionDirection.DEBIT,
        merchantRaw = merchant,
        merchantKey = merchant.lowercase(),
        category = "SHOPPING",
        accountIssuer = "HDFC",
        accountTail = "1234",
        refNo = "REF123456",
        balanceMinor = null,
        occurredAtUtcMillis = 1_726_000_000_000L,
        state = TransactionState.CONFIRMED,
        createdAtUtcMillis = 1_726_000_000_000L,
    )

    @Test
    fun `inserting the same content hash twice results in exactly one row`() = runBlocking {
        val dao = db.transactionDao()
        dao.insert(sampleTransaction("hash-1"))
        dao.insert(sampleTransaction("hash-1"))

        assertEquals(1, dao.count())
    }

    @Test
    fun `editing a transaction updates only that row`() = runBlocking {
        val dao = db.transactionDao()
        val idA = dao.insert(sampleTransaction("hash-a", merchant = "Amazon"))
        dao.insert(sampleTransaction("hash-b", merchant = "Amazon"))

        val a = dao.getById(idA)!!
        dao.update(a.copy(category = "OTHER", isUserEdited = true))

        val updated = dao.getById(idA)!!
        assertEquals("OTHER", updated.category)
        assertTrue(updated.isUserEdited)

        val recent = dao.observeRecent(10).first()
        val sibling = recent.first { it.contentHash == "hash-b" }
        assertEquals("SHOPPING", sibling.category)
        assertTrue(!sibling.isUserEdited)
    }

    @Test
    fun `needs review query returns only NEEDS_REVIEW transactions`() = runBlocking {
        val dao = db.transactionDao()
        dao.insert(sampleTransaction("hash-confirmed").copy(state = TransactionState.CONFIRMED))
        dao.insert(sampleTransaction("hash-review").copy(state = TransactionState.NEEDS_REVIEW))

        val needsReview = dao.observeNeedsReview().first()
        assertEquals(1, needsReview.size)
        assertEquals("hash-review", needsReview.first().contentHash)
    }

    /**
     * The breakdown counts everything the user has not explicitly ignored, including
     * `NEEDS_REVIEW`. Restricting it to `CONFIRMED` would hide every transaction whose merchant has
     * no rule yet, which is most of them on a fresh install, and the totals would silently
     * under-report.
     */
    @Test
    fun `category breakdown sums every DEBIT in range except ignored ones`() = runBlocking {
        val dao = db.transactionDao()
        dao.insert(sampleTransaction("h1", amount = 1000L))
        dao.insert(sampleTransaction("h2", amount = 2000L))
        dao.insert(sampleTransaction("h3", amount = 500L).copy(state = TransactionState.NEEDS_REVIEW))
        dao.insert(sampleTransaction("h4", amount = 9000L).copy(state = TransactionState.IGNORED))

        val breakdown = dao.observeCategoryBreakdown(0L, Long.MAX_VALUE).first()
        val shopping = breakdown.first { it.category == "SHOPPING" }
        assertEquals(3500L, shopping.totalMinor)
        assertEquals(3, shopping.count)
    }

    @Test
    fun `earliest transaction millis ignores IGNORED rows`() = runBlocking {
        val dao = db.transactionDao()
        dao.insert(sampleTransaction("h-old").copy(occurredAtUtcMillis = 1_000L, state = TransactionState.IGNORED))
        dao.insert(sampleTransaction("h-new").copy(occurredAtUtcMillis = 5_000L))

        assertEquals(5_000L, dao.observeEarliestTransactionMillis().first())
    }

    @Test
    fun `earliest transaction millis is null on an empty database`() = runBlocking {
        assertEquals(null, db.transactionDao().observeEarliestTransactionMillis().first())
    }
}
