package dev.xpensetracker.app.ingestion

import dev.xpensetracker.app.categorization.MerchantCategorizer
import dev.xpensetracker.app.data.AppDatabase
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.RoomTestDatabase
import dev.xpensetracker.app.data.entity.MerchantRuleEntity
import dev.xpensetracker.app.data.entity.RuleSource
import dev.xpensetracker.app.data.entity.TransactionState
import dev.xpensetracker.app.parsing.TransactionParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end test of the "SMS-to-transaction pipeline" cross-cutting flow
 * (architecture.md), using the real parser rule assets and an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
class SmsPipelineTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository
    private lateinit var pipeline: SmsPipeline

    private val ruleAssetPaths = listOf(
        "parser-rules/upi-debit.json",
        "parser-rules/upi-credit.json",
        "parser-rules/card-and-bill.json",
        "parser-rules/fallback.json",
        "parser-rules/ignore.json",
    )

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
        repository = ExpenseRepository(db)
        val parser = TransactionParser(
            ruleAssetPaths.map {
                javaClass.classLoader!!.getResourceAsStream(it)!!.bufferedReader().use { r -> r.readText() }
            },
        )
        pipeline = SmsPipeline(repository, parser, MerchantCategorizer(repository))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a debit sms from an allowlisted sender is recorded as a transaction`() = runBlocking {
        db.merchantRuleDao().upsert(MerchantRuleEntity("amazon", "SHOPPING", RuleSource.SEED))

        pipeline.process(
            sender = "VM-HDFCBK",
            rawBody = "Rs.500.00 debited from A/c XX1234 to VPA amazon on 12-09-25. Ref No 123456789012",
            receivedAtUtcMillis = 1_726_000_000_000L,
            issuerHint = "HDFCBK",
        )

        assertEquals(1, db.transactionDao().count())
        val transaction = db.transactionDao().observeRecent(1).first().first()
        assertEquals(50000L, transaction.amountMinor)
        assertEquals(TransactionState.CONFIRMED, transaction.state)
    }

    @Test
    fun `processing the same sms twice never creates a duplicate transaction`() = runBlocking {
        val sender = "VM-HDFCBK"
        val body = "Rs.500.00 debited from A/c XX1234 to VPA amazon on 12-09-25. Ref No 123456789012"
        val timestamp = 1_726_000_000_000L

        pipeline.process(sender, body, timestamp, "HDFCBK")
        pipeline.process(sender, body, timestamp, "HDFCBK")

        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun `a non-allowlisted sender is never recorded`() = runBlocking {
        pipeline.process(
            sender = "+919876543210",
            rawBody = "Rs.500.00 debited from A/c XX1234 to VPA amazon on 12-09-25.",
            receivedAtUtcMillis = 1_726_000_000_000L,
            issuerHint = "UNKNOWN",
        )

        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun `an OTP message is never recorded`() = runBlocking {
        val body = "Your OTP for login is 482913. Do not share this with anyone."

        pipeline.process("VM-HDFCBK", body, 1_726_000_000_000L, "HDFCBK")

        assertEquals(0, db.transactionDao().count())
        // Only transactions claim a hash. Claiming here would make the dedupe table a
        // permanent blocklist, so a rescan with better rules could never revisit the message.
        assertFalse(
            db.smsHashDao().exists(
                SmsNormalizer.contentHash("VM-HDFCBK", SmsNormalizer.normalizeBody(body), 1_726_000_000_000L),
            ),
        )
    }

    @Test
    fun `a message the rules cannot parse stays eligible for a later rescan`() = runBlocking {
        val body = "Your account statement for August is ready to download."

        pipeline.process("VM-HDFCBK", body, 1_726_000_000_000L, "HDFCBK")

        assertEquals(0, db.transactionDao().count())
        assertFalse(
            db.smsHashDao().exists(
                SmsNormalizer.contentHash("VM-HDFCBK", SmsNormalizer.normalizeBody(body), 1_726_000_000_000L),
            ),
        )
    }

    @Test
    fun `a rescan drops dedupe rows that never produced a transaction`() = runBlocking {
        repository.tryClaimHash("orphaned-hash", 1_726_000_000_000L)

        repository.clearOrphanHashes()

        assertFalse(db.smsHashDao().exists("orphaned-hash"))
    }
}
