package dev.xpensetracker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.xpensetracker.app.data.entity.TransactionDirection
import dev.xpensetracker.app.data.entity.TransactionEntity
import dev.xpensetracker.app.data.entity.TransactionState
import kotlinx.coroutines.flow.Flow

/** [category] is a `categories` row id; the display name and color are joined in by analytics. */
data class CategorySpend(val category: String, val totalMinor: Long, val count: Int)

data class MerchantSpend(val merchantKey: String, val merchantRaw: String, val totalMinor: Long, val count: Int)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE occurredAtUtcMillis BETWEEN :startMillis AND :endMillis ORDER BY occurredAtUtcMillis DESC")
    fun observeForRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAtUtcMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE state = 'NEEDS_REVIEW' ORDER BY occurredAtUtcMillis DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE state = 'NEEDS_REVIEW'")
    fun observeNeedsReviewCount(): Flow<Int>

    // Totals count everything the user has not explicitly ignored. Restricting these to
    // CONFIRMED hid every transaction whose merchant has no category rule yet, which is most
    // of them on a fresh install, so the spend ring read zero even with transactions imported.
    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE direction = :direction AND state != 'IGNORED' AND occurredAtUtcMillis BETWEEN :startMillis AND :endMillis",
    )
    fun observeTotalForRange(direction: TransactionDirection, startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        "SELECT category, COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS count FROM transactions " +
            "WHERE direction = 'DEBIT' AND state != 'IGNORED' AND occurredAtUtcMillis BETWEEN :startMillis AND :endMillis " +
            "GROUP BY category ORDER BY totalMinor DESC",
    )
    fun observeCategoryBreakdown(startMillis: Long, endMillis: Long): Flow<List<CategorySpend>>

    @Query(
        "SELECT merchantKey, merchantRaw, COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS count FROM transactions " +
            "WHERE direction = 'DEBIT' AND state != 'IGNORED' AND occurredAtUtcMillis BETWEEN :startMillis AND :endMillis " +
            "GROUP BY merchantKey ORDER BY totalMinor DESC",
    )
    fun observeMerchantBreakdown(startMillis: Long, endMillis: Long): Flow<List<MerchantSpend>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    /**
     * Lower bound for the month picker, so it cannot page backwards into months that are
     * guaranteed empty. Null when nothing has been imported yet.
     */
    @Query("SELECT MIN(occurredAtUtcMillis) FROM transactions WHERE state != 'IGNORED'")
    fun observeEarliestTransactionMillis(): Flow<Long?>

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE direction = 'DEBIT' AND state != 'IGNORED' AND accountIssuer = :issuer " +
            "AND accountTail = :accountTail AND occurredAtUtcMillis BETWEEN :startMillis AND :endMillis",
    )
    fun observeAccountSpendForRange(
        issuer: String,
        accountTail: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
