package dev.expensetracker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.expensetracker.app.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeed(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    /** Everything, archived included, so historical rows can still resolve a label. */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** Pickers only ever offer these. */
    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeSelectable(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :name COLLATE NOCASE AND id != :excludingId)")
    suspend fun nameExists(name: String, excludingId: String): Boolean

    /** Archiving is allowed for built-ins too; only deletion is restricted. */
    @Query("UPDATE categories SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM categories WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: String): Int

    /**
     * Reference counts backing the delete guard. There is deliberately no SQL foreign key on the
     * category columns (blueprints/local-persistence.md): a cascade would erase transactions.
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE category = :id")
    suspend fun transactionReferenceCount(id: String): Int

    @Query("SELECT COUNT(*) FROM budgets WHERE category = :id")
    suspend fun budgetReferenceCount(id: String): Int

    @Query("SELECT COUNT(*) FROM merchant_rules WHERE category = :id")
    suspend fun merchantRuleReferenceCount(id: String): Int
}
