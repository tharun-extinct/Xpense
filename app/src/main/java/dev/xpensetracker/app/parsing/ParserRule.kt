package dev.xpensetracker.app.parsing

import kotlinx.serialization.Serializable

/**
 * A single declarative parsing rule loaded from a JSON asset. `pattern` is a regex with
 * named groups among: amount, merchant, accountTail, refNo, balance. See
 * blueprints/transaction-parsing.md for the rule-asset contract.
 */
@Serializable
data class ParserRule(
    val id: String,
    val messageType: String,
    val pattern: String,
)

@Serializable
data class ParserRuleFile(
    val rules: List<ParserRule>,
)
