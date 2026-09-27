package dev.xpensetracker.app.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the two things a user can attach to a transaction while reviewing it: a free-text note and
 * an optional location tag.
 *
 * Three nullable columns and nothing else. `ALTER TABLE ... ADD COLUMN` cannot fail partway and
 * rewrites no existing row, which is the only acceptable shape for a migration in an app whose
 * database is the user's sole copy of their financial history (architecture.md #error-and-recovery).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `note` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `locationLat` REAL")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `locationLng` REAL")
    }
}
