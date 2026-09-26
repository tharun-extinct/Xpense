package dev.expensetracker.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The sole de-duplication record for processed SMS. See architecture.md
 * #identity-and-ownership. The raw SMS body is never stored, only its hash.
 */
@Entity(tableName = "sms_hashes")
data class SmsHashEntity(
    @PrimaryKey
    val contentHash: String,
    val processedAtUtcMillis: Long,
)
