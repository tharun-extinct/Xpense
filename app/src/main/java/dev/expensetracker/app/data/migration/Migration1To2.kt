package dev.expensetracker.app.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.expensetracker.app.data.entity.BuiltInCategories

/**
 * Turns the fixed category enum into the `categories` table (architecture.md #data-representation).
 *
 * Purely additive by design: `transactions.category`, `budgets.category`, and
 * `merchant_rules.category` were already TEXT columns holding `SpendCategory` constant names, and
 * the seeded ids are those same strings, so not one existing row is rewritten. That matters because
 * there is no backend to restore from if a migration loses data (architecture.md #error-and-recovery).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `colorArgb` INTEGER NOT NULL,
                `iconKey` TEXT NOT NULL,
                `isBuiltIn` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        BuiltInCategories.all.forEach { category ->
            db.execSQL(
                "INSERT OR IGNORE INTO `categories` " +
                    "(`id`, `name`, `colorArgb`, `iconKey`, `isBuiltIn`, `sortOrder`, `isArchived`) " +
                    "VALUES (?, ?, ?, ?, 1, ?, 0)",
                // Explicitly Any: the bind arguments mix String, Long and Int, which Kotlin would
                // otherwise infer as an intersection type.
                arrayOf<Any>(
                    category.id,
                    category.name,
                    category.colorArgb,
                    category.iconKey,
                    category.sortOrder,
                ),
            )
        }

        // The category-breakdown aggregate groups on this column every time the Spends screen
        // recomposes, and it was previously unindexed.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_category` ON `transactions` (`category`)")
    }
}
