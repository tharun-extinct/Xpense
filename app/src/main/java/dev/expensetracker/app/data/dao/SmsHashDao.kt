package dev.expensetracker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.expensetracker.app.data.entity.SmsHashEntity

@Dao
interface SmsHashDao {

    /** Returns -1 (per OnConflictStrategy.IGNORE) if this hash was already processed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SmsHashEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM sms_hashes WHERE contentHash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Query("DELETE FROM sms_hashes WHERE contentHash NOT IN (SELECT contentHash FROM transactions)")
    suspend fun deleteOrphans()
}
