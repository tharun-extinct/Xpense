package dev.expensetracker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.expensetracker.app.data.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE month = :month")
    fun observeForMonth(month: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE month = :month AND category = :category LIMIT 1")
    suspend fun findForMonthAndCategory(month: String, category: String): BudgetEntity?

    @Query("DELETE FROM budgets WHERE month = :month AND category = :category")
    suspend fun delete(month: String, category: String)
}
