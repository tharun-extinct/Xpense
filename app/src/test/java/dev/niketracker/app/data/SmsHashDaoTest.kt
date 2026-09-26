package dev.expensetracker.app.data

import dev.expensetracker.app.data.entity.SmsHashEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsHashDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `inserting the same hash twice ignores the second insert`() = runBlocking {
        val dao = db.smsHashDao()
        val first = dao.insert(SmsHashEntity("hash-1", 1L))
        val second = dao.insert(SmsHashEntity("hash-1", 2L))

        assertTrue(first != -1L)
        assertEquals(-1L, second)
        assertTrue(dao.exists("hash-1"))
    }
}
