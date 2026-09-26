package dev.expensetracker.app.data

import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two things a user can attach to a transaction while reviewing it, per architecture.md
 * #data-representation: a note and a location tag. Both are user-authored and neither may ever
 * influence how an amount is counted, so these tests pin the storage rules rather than any
 * behaviour downstream — there deliberately is none.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionAnnotationsTest {

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

    private suspend fun insertOne(): Long = repository.insertTransaction(
        TransactionEntity(
            contentHash = "hash-annotated",
            amountMinor = 50_000L,
            direction = TransactionDirection.DEBIT,
            merchantRaw = "Kirana Store",
            merchantKey = "kirana store",
            category = "GROCERIES",
            accountIssuer = "HDFC",
            accountTail = "1234",
            refNo = null,
            balanceMinor = null,
            occurredAtUtcMillis = 1_726_000_000_000L,
            state = TransactionState.CONFIRMED,
            createdAtUtcMillis = 1_726_000_000_000L,
        ),
    )

    @Test
    fun `a note is stored trimmed and marks the row as user edited`() = runBlocking {
        val id = insertOne()

        repository.setTransactionNote(id, "  Groceries for the week  ")

        val saved = repository.getTransaction(id)!!
        assertEquals("Groceries for the week", saved.note)
        assertTrue(saved.isUserEdited)
    }

    // Blank and absent have to be the same stored state, or screens would have to tell apart a
    // note that was cleared from one that was never written, for no benefit to anyone.
    @Test
    fun `a blank note is stored as no note at all`() = runBlocking {
        val id = insertOne()
        repository.setTransactionNote(id, "Temporary")

        repository.setTransactionNote(id, "   ")

        assertNull(repository.getTransaction(id)!!.note)
    }

    // Saving the screen writes every field whether or not it changed, so a no-op write must not
    // claim the row was edited by hand.
    @Test
    fun `writing the note a row already has leaves it untouched`() = runBlocking {
        val id = insertOne()

        repository.setTransactionNote(id, null)
        repository.setTransactionLocation(id, null, null)

        assertFalse(repository.getTransaction(id)!!.isUserEdited)
    }

    @Test
    fun `a location tag stores both coordinates`() = runBlocking {
        val id = insertOne()

        repository.setTransactionLocation(id, 12.97160, 77.59460)

        val saved = repository.getTransaction(id)!!
        assertEquals(12.97160, saved.locationLat!!, 0.000001)
        assertEquals(77.59460, saved.locationLng!!, 0.000001)
        assertTrue(saved.isUserEdited)
    }

    @Test
    fun `clearing a location removes both coordinates`() = runBlocking {
        val id = insertOne()
        repository.setTransactionLocation(id, 12.97160, 77.59460)

        repository.setTransactionLocation(id, null, null)

        val saved = repository.getTransaction(id)!!
        assertNull(saved.locationLat)
        assertNull(saved.locationLng)
    }

    // Half a coordinate would render as a point on the equator the user has never been to.
    @Test
    fun `a single coordinate is refused rather than stored alone`() = runBlocking {
        val id = insertOne()

        repository.setTransactionLocation(id, 12.97160, null)

        val saved = repository.getTransaction(id)!!
        assertNull(saved.locationLat)
        assertNull(saved.locationLng)
    }

    @Test
    fun `annotating a transaction changes neither its amount nor its direction`() = runBlocking {
        val id = insertOne()

        repository.setTransactionNote(id, "Paid for a friend")
        repository.setTransactionLocation(id, 12.97160, 77.59460)

        val saved = repository.getTransaction(id)!!
        assertEquals(50_000L, saved.amountMinor)
        assertEquals(TransactionDirection.DEBIT, saved.direction)
        assertEquals("GROCERIES", saved.category)
    }
}
