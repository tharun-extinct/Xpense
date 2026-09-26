package dev.expensetracker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.expensetracker.app.data.entity.MerchantRuleEntity

@Dao
interface MerchantRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedRules(rules: List<MerchantRuleEntity>)

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun findByKey(merchantKey: String): MerchantRuleEntity?

    @Query("SELECT COUNT(*) FROM merchant_rules")
    suspend fun count(): Int
}
