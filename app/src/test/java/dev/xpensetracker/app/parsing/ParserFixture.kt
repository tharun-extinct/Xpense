package dev.xpensetracker.app.parsing

import kotlinx.serialization.Serializable

@Serializable
data class ParserFixture(
    val body: String,
    val expectedType: String,
    val expectedAmountMinor: Long? = null,
    val expectedAccountTail: String? = null,
)

@Serializable
data class ParserFixtureFile(val fixtures: List<ParserFixture>)
