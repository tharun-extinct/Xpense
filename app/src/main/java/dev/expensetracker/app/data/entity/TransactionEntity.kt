package dev.expensetracker.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** See architecture.md #data-representation and #state-lifecycle. */
enum class TransactionDirection { DEBIT, CREDIT }

enum class TransactionState { CONFIRMED, NEEDS_REVIEW, IGNORED }

/**
 * A single parsed transaction. Amounts are always integer minor units (paise).
 * `contentHash` links back to the [SmsHashEntity] that produced this row, one-to-one.
 *
 * [category] is a [CategoryEntity] id, not an enum: the category set is user-extensible
 * (architecture.md #data-representation). It is indexed because the Spends breakdown groups on it.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["category"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentHash: String,
    val amountMinor: Long,
    val direction: TransactionDirection,
    val merchantRaw: String,
    val merchantKey: String,
    val category: String,
    val accountIssuer: String?,
    val accountTail: String?,
    val refNo: String?,
    val balanceMinor: Long?,
    val occurredAtUtcMillis: Long,
    val state: TransactionState,
    val isUserEdited: Boolean = false,
    val createdAtUtcMillis: Long,
    /** Free text the user attached while reviewing. Never parsed, never matched on. */
    val note: String? = null,
    /**
     * Where the user was standing when they tagged this transaction, captured only on an explicit
     * tap. Nullable together: either both coordinates are present or the row has no location at
     * all, which is the normal case. Stored as degrees rather than minor units because this is a
     * coordinate, not money.
     */
    val locationLat: Double? = null,
    val locationLng: Double? = null,
)
