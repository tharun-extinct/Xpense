package dev.xpensetracker.app.categorization

import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.TransactionState
import dev.xpensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.xpensetracker.app.parsing.ConfidenceTier
import dev.xpensetracker.app.parsing.ParsedTransaction

data class CategorizationResult(
    /** A `categories` row id. */
    val category: String,
    val state: TransactionState,
    val merchantKey: String,
)

/**
 * Decides category + CONFIRMED/NEEDS_REVIEW per architecture.md #state-lifecycle: a
 * transaction is only CONFIRMED when parser confidence is HIGH *and* a known merchant
 * rule (seed or user) resolves a category. USER rules always win over SEED rules.
 */
class MerchantCategorizer(private val repository: ExpenseRepository) {

    suspend fun categorize(parsed: ParsedTransaction): CategorizationResult {
        val merchantKey = parsed.merchantRaw?.let { MerchantTextNormalizer.normalize(it) } ?: ""

        if (merchantKey.isBlank()) {
            return CategorizationResult(UNKNOWN_CATEGORY_ID, TransactionState.NEEDS_REVIEW, merchantKey)
        }

        val rule = repository.findMerchantRule(merchantKey)
        val category = rule?.category ?: UNKNOWN_CATEGORY_ID

        val state = if (parsed.confidence == ConfidenceTier.HIGH && rule != null) {
            TransactionState.CONFIRMED
        } else {
            TransactionState.NEEDS_REVIEW
        }

        return CategorizationResult(category, state, merchantKey)
    }
}
