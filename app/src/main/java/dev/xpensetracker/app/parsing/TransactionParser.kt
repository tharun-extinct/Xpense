package dev.xpensetracker.app.parsing

import kotlinx.serialization.json.Json

/**
 * Rule-based SMS-to-transaction parser. No Android, Room, or Compose imports on purpose:
 * this class must be testable as a plain JVM unit test (architecture.md #verification-boundaries).
 *
 * Design: directional rules (JSON assets) only need to capture `amount` and `merchant` named
 * groups, keeping each rule template small and robust. `accountTail`, `refNo`, and `balance`
 * are extracted separately via generic regexes applied to the whole message, since their
 * surrounding text is far more uniform across issuers than the amount/merchant phrasing is.
 */
class TransactionParser(ruleFilesJson: List<String>) {

    private data class CompiledRule(val id: String, val messageType: MessageType, val regex: Regex)

    private val compiledRules: List<CompiledRule> = ruleFilesJson.flatMap { json ->
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(ParserRuleFile.serializer(), json)
        parsed.rules.map { rule ->
            CompiledRule(
                id = rule.id,
                messageType = MessageType.valueOf(rule.messageType),
                regex = Regex(rule.pattern, RegexOption.IGNORE_CASE),
            )
        }
    }

    // Ignore rules are checked first so a promo/OTP message never falls through to a
    // coincidental directional match.
    private val ignoreRules = compiledRules.filter { it.messageType == MessageType.IGNORE }
    private val directionalRules = compiledRules.filter { it.messageType != MessageType.IGNORE }

    fun parse(normalizedBody: String): ParsedTransaction {
        val ignoreMatch = ignoreRules.firstOrNull { it.regex.containsMatchIn(normalizedBody) }
        if (ignoreMatch != null) {
            return ParsedTransaction(
                messageType = MessageType.IGNORE,
                amountMinor = null,
                merchantRaw = null,
                accountTail = null,
                refNo = null,
                balanceMinor = null,
                confidence = ConfidenceTier.NONE,
                matchedRuleId = ignoreMatch.id,
            )
        }

        for (rule in directionalRules) {
            val match = rule.regex.find(normalizedBody) ?: continue
            return buildResult(rule.id, rule.messageType, match, normalizedBody)
        }

        return ParsedTransaction(
            messageType = MessageType.UNRECOGNIZED,
            amountMinor = null,
            merchantRaw = null,
            accountTail = null,
            refNo = null,
            balanceMinor = null,
            confidence = ConfidenceTier.NONE,
            matchedRuleId = null,
        )
    }

    private fun buildResult(ruleId: String, messageType: MessageType, match: MatchResult, body: String): ParsedTransaction {
        val amountMinor = groupOrNull(match, "amount")?.let { toMinorUnits(it) }
        val merchant = groupOrNull(match, "merchant")?.trim()
        val accountTail = GenericExtractors.accountTail(body)
        val refNo = GenericExtractors.refNo(body)
        val balanceMinor = GenericExtractors.balance(body)?.let { toMinorUnits(it) }

        val confidence = when {
            amountMinor == null -> ConfidenceTier.NONE
            !merchant.isNullOrBlank() && (!accountTail.isNullOrBlank() || !refNo.isNullOrBlank()) -> ConfidenceTier.HIGH
            else -> ConfidenceTier.LOW
        }

        return ParsedTransaction(
            messageType = messageType,
            amountMinor = amountMinor,
            merchantRaw = merchant,
            accountTail = accountTail,
            refNo = refNo,
            balanceMinor = balanceMinor,
            confidence = confidence,
            matchedRuleId = ruleId,
        )
    }

    private fun groupOrNull(match: MatchResult, name: String): String? =
        runCatching { match.groups[name]?.value }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Converts a decimal amount string (e.g. "1,234.50") to Long minor units (paise). Never returns Double. */
    private fun toMinorUnits(amountText: String): Long? {
        val cleaned = amountText.replace(",", "").trim()
        val parts = cleaned.split(".")
        return try {
            val whole = parts[0].toLong()
            val fraction = when {
                parts.size < 2 -> 0L
                parts[1].length == 1 -> parts[1].toLong() * 10L
                else -> parts[1].take(2).toLong()
            }
            whole * 100L + fraction
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/** Generic, issuer-agnostic extractors reused across all directional rules. */
private object GenericExtractors {

    private val accountTailRegex = Regex(
        "(?:a/c|acc(?:ount)?)\\s*(?:no\\.?)?\\s*[x*]*([0-9]{3,6})",
        RegexOption.IGNORE_CASE,
    )
    private val refNoRegex = Regex(
        "ref(?:erence)?\\.?\\s*(?:no)?\\.?\\s*[:#]?\\s*([0-9A-Za-z]{6,20})",
        RegexOption.IGNORE_CASE,
    )
    private val balanceRegex = Regex(
        "(?:available balance|avl bal|avail bal|bal)\\.?\\s*(?:is|:)?\\s*(?:rs\\.?|inr|\u20B9)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )

    fun accountTail(body: String): String? = accountTailRegex.find(body)?.groupValues?.get(1)
    fun refNo(body: String): String? = refNoRegex.find(body)?.groupValues?.get(1)
    fun balance(body: String): String? = balanceRegex.find(body)?.groupValues?.get(1)
}
