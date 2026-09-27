package dev.xpensetracker.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RuleSource { SEED, USER }

/**
 * merchantKey -> category mapping. USER rules always win over SEED rules on lookup
 * (enforced in MerchantCategorizer, not here). See blueprints/merchant-categorization.md.
 */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey
    val merchantKey: String,
    val category: String,
    val source: RuleSource,
)
