package dev.expensetracker.app.categorization

import dev.expensetracker.app.data.entity.BuiltInCategories
import dev.expensetracker.app.data.entity.MerchantRuleEntity
import dev.expensetracker.app.data.entity.RuleSource
import dev.expensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SeedRuleJson(val merchantKey: String, val category: String)

@Serializable
private data class SeedRuleFile(val rules: List<SeedRuleJson>)

/** Parses `merchant-rules/seed-rules.json` asset text into Room entities. Pure Kotlin. */
object SeedRuleLoader {
    fun parse(json: String): List<MerchantRuleEntity> {
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(SeedRuleFile.serializer(), json)
        return file.rules.map {
            MerchantRuleEntity(
                merchantKey = it.merchantKey,
                // A typo in the asset must not take ingestion down with an IllegalArgumentException
                // the way enum valueOf did; an unresolvable id degrades to uncategorized instead.
                category = if (BuiltInCategories.isBuiltIn(it.category)) it.category else UNKNOWN_CATEGORY_ID,
                source = RuleSource.SEED,
            )
        }
    }
}
