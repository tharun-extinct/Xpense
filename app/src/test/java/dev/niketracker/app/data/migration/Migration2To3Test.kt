package dev.expensetracker.app.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import dev.expensetracker.app.data.AppDatabase
import dev.expensetracker.app.data.entity.BuiltInCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the v2 -> v3 upgrade that adds the note and location columns.
 *
 * The point of the test is not that `ALTER TABLE` works, but that a database carrying real
 * transactions crosses the version boundary with every row and value intact, and that Room accepts
 * the resulting schema. A migration that loses a row here loses it for good: there is no backup
 * (architecture.md #error-and-recovery).
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-2-3-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV2Database(populate: (SupportSQLiteDatabase) -> Unit = {}) {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                V2_SCHEMA.forEach(db::execSQL)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build(),
        )
        val db = helper.writableDatabase
        populate(db)
        db.close()
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `a populated v2 database keeps every row and gains empty note and location columns`() = runBlocking {
        createV2Database { db ->
            db.execSQL(
                "INSERT INTO `transactions` (`contentHash`, `amountMinor`, `direction`, `merchantRaw`, " +
                    "`merchantKey`, `category`, `accountIssuer`, `accountTail`, `refNo`, `balanceMinor`, " +
                    "`occurredAtUtcMillis`, `state`, `isUserEdited`, `createdAtUtcMillis`) " +
                    "VALUES ('hash-1', 50000, 'DEBIT', 'Amazon', 'amazon', 'SHOPPING', 'HDFC', '1234', " +
                    "'REF1', NULL, 1726000000000, 'CONFIRMED', 0, 1726000000000)",
            )
            BuiltInCategories.all.take(2).forEach { category ->
                db.execSQL(
                    "INSERT INTO `categories` (`id`, `name`, `colorArgb`, `iconKey`, `isBuiltIn`, " +
                        "`sortOrder`, `isArchived`) VALUES (?, ?, ?, ?, 1, ?, 0)",
                    arrayOf<Any>(
                        category.id,
                        category.name,
                        category.colorArgb,
                        category.iconKey,
                        category.sortOrder,
                    ),
                )
            }
        }

        val db = openMigrated()
        try {
            // Reading is what actually triggers the migration.
            assertEquals(1, db.transactionDao().count())

            val transaction = db.transactionDao().observeRecent(10).first().single()
            assertEquals("SHOPPING", transaction.category)
            assertEquals(50000L, transaction.amountMinor)
            assertNull(transaction.note)
            assertNull(transaction.locationLat)
            assertNull(transaction.locationLng)
        } finally {
            db.close()
        }
    }

    @Test
    fun `an empty v2 database migrates and is immediately usable`() = runBlocking {
        createV2Database()

        val db = openMigrated()
        try {
            assertEquals(0, db.transactionDao().count())
        } finally {
            db.close()
        }
    }

    private companion object {
        /** What Room generated at v2: v1's tables plus `categories` and the category index. */
        val V2_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`contentHash` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `direction` TEXT NOT NULL, " +
                "`merchantRaw` TEXT NOT NULL, `merchantKey` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`accountIssuer` TEXT, `accountTail` TEXT, `refNo` TEXT, `balanceMinor` INTEGER, " +
                "`occurredAtUtcMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, " +
                "`isUserEdited` INTEGER NOT NULL, `createdAtUtcMillis` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_contentHash` ON `transactions` (`contentHash`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_category` ON `transactions` (`category`)",
            "CREATE TABLE IF NOT EXISTS `sms_hashes` (" +
                "`contentHash` TEXT NOT NULL, `processedAtUtcMillis` INTEGER NOT NULL, PRIMARY KEY(`contentHash`))",
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`issuer` TEXT NOT NULL, `accountTail` TEXT NOT NULL, `lastSeenBalanceMinor` INTEGER, " +
                "`lastSeenAtUtcMillis` INTEGER NOT NULL, PRIMARY KEY(`issuer`, `accountTail`))",
            "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                "`merchantKey` TEXT NOT NULL, `category` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "PRIMARY KEY(`merchantKey`))",
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`month` TEXT NOT NULL, `category` TEXT NOT NULL, `limitMinor` INTEGER NOT NULL, " +
                "PRIMARY KEY(`month`, `category`))",
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, " +
                "`iconKey` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`isArchived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
