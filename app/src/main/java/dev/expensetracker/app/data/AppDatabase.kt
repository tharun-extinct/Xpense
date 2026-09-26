package dev.expensetracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.expensetracker.app.data.dao.AccountDao
import dev.expensetracker.app.data.dao.BudgetDao
import dev.expensetracker.app.data.dao.CategoryDao
import dev.expensetracker.app.data.dao.MerchantRuleDao
import dev.expensetracker.app.data.dao.SmsHashDao
import dev.expensetracker.app.data.dao.TransactionDao
import dev.expensetracker.app.data.entity.AccountEntity
import dev.expensetracker.app.data.entity.BudgetEntity
import dev.expensetracker.app.data.entity.CategoryEntity
import dev.expensetracker.app.data.entity.MerchantRuleEntity
import dev.expensetracker.app.data.entity.SmsHashEntity
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.migration.MIGRATION_1_2
import dev.expensetracker.app.data.migration.MIGRATION_2_3

/**
 * Single source of truth for on-device state. No fallbackToDestructiveMigration once this
 * ships v1; see architecture.md #error-and-recovery and blueprints/local-persistence.md.
 */
@Database(
    entities = [
        TransactionEntity::class,
        SmsHashEntity::class,
        AccountEntity::class,
        MerchantRuleEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun smsHashDao(): SmsHashDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expense-tracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
