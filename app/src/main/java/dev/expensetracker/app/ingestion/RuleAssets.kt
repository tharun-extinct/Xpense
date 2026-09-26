package dev.expensetracker.app.ingestion

import android.content.Context
import dev.expensetracker.app.categorization.SeedRuleLoader
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.entity.MerchantRuleEntity
import dev.expensetracker.app.parsing.TransactionParser

// Order matters: the parser takes the first directional rule that matches, so the
// merchant-bearing rules must all be tried before the amount-only fallbacks.
private val PARSER_RULE_ASSET_PATHS = listOf(
    "parser-rules/upi-debit.json",
    "parser-rules/upi-credit.json",
    "parser-rules/card-and-bill.json",
    "parser-rules/fallback.json",
    "parser-rules/ignore.json",
)

private const val SEED_MERCHANT_RULES_ASSET = "merchant-rules/seed-rules.json"

/** The only place that touches AssetManager; everything downstream is plain Kotlin/JVM code. */
object RuleAssets {

    fun buildParser(context: Context): TransactionParser {
        val jsonTexts = PARSER_RULE_ASSET_PATHS.map { path ->
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return TransactionParser(jsonTexts)
    }

    suspend fun seedMerchantRules(context: Context, repository: ExpenseRepository) {
        val json = context.assets.open(SEED_MERCHANT_RULES_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val rules: List<MerchantRuleEntity> = SeedRuleLoader.parse(json)
        repository.seedMerchantRulesIfEmpty(rules)
    }
}
