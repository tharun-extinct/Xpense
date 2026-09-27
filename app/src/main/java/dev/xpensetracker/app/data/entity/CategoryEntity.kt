package dev.xpensetracker.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A spend category. Rows, not a Kotlin enum, so the user can create their own
 * (architecture.md #data-representation).
 *
 * [id] is what `transactions.category`, `budgets.category`, and `merchant_rules.category` store.
 * Built-in ids are the former `SpendCategory` constant names, which is why upgrading installs
 * need no data rewrite; user categories get a `custom_<uuid>` id.
 *
 * Display attributes live here rather than in the theme so a user-created category is visually
 * indistinguishable in capability from a built-in one.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** Packed ARGB. Stored as Long because SQLite INTEGER is signed and 0xFF... overflows Int. */
    val colorArgb: Long,
    /** Resolved to an ImageVector by ui/theme/CategoryIcons.kt; unknown keys fall back, never crash. */
    val iconKey: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
    /** Hidden from pickers but still resolvable, so historical rows keep their label. */
    val isArchived: Boolean = false,
)

/** The category assigned when no merchant rule matches. Never deletable. */
const val UNKNOWN_CATEGORY_ID: String = "UNKNOWN"
