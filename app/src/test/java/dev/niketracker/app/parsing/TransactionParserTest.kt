package dev.expensetracker.app.parsing

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fixture-driven test per blueprints/transaction-parsing.md: every rule template ships with
 * at least one fixture here. This is a plain JVM test with zero Android imports.
 */
class TransactionParserTest {

    private val ruleAssetPaths = listOf(
        "parser-rules/upi-debit.json",
        "parser-rules/upi-credit.json",
        "parser-rules/card-and-bill.json",
        "parser-rules/ignore.json",
    )

    private val fixtureResourcePaths = listOf(
        "sms-fixtures/debit-fixtures.json",
        "sms-fixtures/credit-fixtures.json",
        "sms-fixtures/ignore-fixtures.json",
    )

    private fun readResource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "Missing resource: $path" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val parser: TransactionParser by lazy {
        TransactionParser(ruleAssetPaths.map { readResource(it) })
    }

    private val fixtures: List<ParserFixture> by lazy {
        fixtureResourcePaths.flatMap { path ->
            Json { ignoreUnknownKeys = true }
                .decodeFromString(ParserFixtureFile.serializer(), readResource(path))
                .fixtures
        }
    }

    @Test
    fun `every fixture produces the exact expected fields`() {
        for (fixture in fixtures) {
            val result = parser.parse(fixture.body)
            assertEquals("messageType for: ${fixture.body}", fixture.expectedType, result.messageType.name)

            if (fixture.expectedAmountMinor != null) {
                assertEquals("amountMinor for: ${fixture.body}", fixture.expectedAmountMinor, result.amountMinor)
            }
            if (fixture.expectedAccountTail != null) {
                assertEquals("accountTail for: ${fixture.body}", fixture.expectedAccountTail, result.accountTail)
            }
        }
    }

    @Test
    fun `debit and credit fixtures reach HIGH confidence`() {
        val transactional = fixtures.filter { it.expectedType == "DEBIT" || it.expectedType == "CREDIT" }
        for (fixture in transactional) {
            val result = parser.parse(fixture.body)
            assertEquals("confidence for: ${fixture.body}", ConfidenceTier.HIGH, result.confidence)
        }
    }

    @Test
    fun `unrecognized message returns NONE confidence without throwing`() {
        val result = parser.parse("This is a completely unrelated message with no financial content.")
        assertEquals(MessageType.UNRECOGNIZED, result.messageType)
        assertEquals(ConfidenceTier.NONE, result.confidence)
        assertNull(result.amountMinor)
    }

    @Test
    fun `amounts are always Long minor units`() {
        val result = parser.parse("Rs.99.99 debited from A/c XX1111 to VPA test@bank on 01-01-25.")
        assertEquals(9999L, result.amountMinor)
    }
}
