package dev.expensetracker.app.data

import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The user's correction of a misread direction, per architecture.md #data-representation: direction
 * is what splits Spends from Income, so reclassifying a row has to actually move the money between
 * those totals rather than only relabel the detail screen.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionDirectionTest {

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

    private fun debit(hash: String, amount: Long = 50_000L) = TransactionEntity(
        contentHash = hash,
        amountMinor = amount,
        direction = TransactionDirection.DEBIT,
        merchantRaw = "Acme Payroll",
        merchantKey = "acme payroll",
        category = "SHOPPING",
        accountIssuer = "HDFC",
        accountTail = "1234",
        refNo = null,
        balanceMinor = null,
        occurredAtUtcMillis = 1_726_000_000_000L,
        state = TransactionState.CONFIRMED,
        createdAtUtcMillis = 1_726_000_000_000L,
    )

    private suspend fun totalFor(direction: TransactionDirection): Long =
        repository.observeTotalForRange(direction, 0L, Long.MAX_VALUE).first()

    @Test
    fun `reclassifying a debit as credit moves it from spends to income`() = runBlocking {
        val id = repository.insertTransaction(debit("hash-salary"))

        repository.setTransactionDirection(id, TransactionDirection.CREDIT)

        assertEquals(0L, totalFor(TransactionDirection.DEBIT))
        assertEquals(50_000L, totalFor(TransactionDirection.CREDIT))
    }

    @Test
    fun `reclassifying drops the row out of the category breakdown`() = runBlocking {
        val id = repository.insertTransaction(debit("hash-refund"))

        repository.setTransactionDirection(id, TransactionDirection.CREDIT)

        val breakdown = repository.observeCategoryBreakdown(0L, Long.MAX_VALUE).first()
        assertTrue(breakdown.none { it.category == "SHOPPING" })
    }

    @Test
    fun `reclassifying marks the row as user edited so a reparse cannot undo it`() = runBlocking {
        val id = repository.insertTransaction(debit("hash-edited"))

        repository.setTransactionDirection(id, TransactionDirection.CREDIT)

        assertTrue(repository.getTransaction(id)!!.isUserEdited)
    }

    // Confirming a transaction without touching the toggle still writes the same direction back,
    // so a no-op must not fabricate an edit the user never made.
    @Test
    fun `writing the direction it already has leaves the row untouched`() = runBlocking {
        val id = repository.insertTransaction(debit("hash-unchanged"))

        repository.setTransactionDirection(id, TransactionDirection.DEBIT)

        assertFalse(repository.getTransaction(id)!!.isUserEdited)
    }

    @Test
    fun `reclassifying one row leaves its siblings alone`() = runBlocking {
        val id = repository.insertTransaction(debit("hash-one"))
        repository.insertTransaction(debit("hash-two"))

        repository.setTransactionDirection(id, TransactionDirection.CREDIT)

        assertEquals(50_000L, totalFor(TransactionDirection.DEBIT))
        assertEquals(50_000L, totalFor(TransactionDirection.CREDIT))
    }
}
