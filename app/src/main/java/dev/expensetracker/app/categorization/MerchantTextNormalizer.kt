package dev.expensetracker.app.categorization

/**
 * Cleans raw merchant text extracted by the parser into a stable lookup key, used both for
 * merchant_rules storage and matching. See blueprints/merchant-categorization.md.
 */
object MerchantTextNormalizer {

    private val trailingRefSuffix = Regex("(?i)\\b(ref|txn|order)[a-z0-9]*$")
    private val nonAlphaNum = Regex("[^a-z0-9 ]")
    private val whitespace = Regex("\\s+")

    fun normalize(rawMerchant: String): String {
        var cleaned = rawMerchant.lowercase().trim()
        cleaned = trailingRefSuffix.replace(cleaned, "")
        cleaned = nonAlphaNum.replace(cleaned, " ")
        cleaned = whitespace.replace(cleaned, " ").trim()
        return cleaned
    }
}
