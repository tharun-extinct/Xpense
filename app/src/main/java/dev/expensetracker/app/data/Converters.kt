package dev.expensetracker.app.data

import androidx.room.TypeConverter
import dev.expensetracker.app.data.entity.RuleSource
import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionState

class Converters {
    @TypeConverter
    fun toDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toState(value: String): TransactionState = TransactionState.valueOf(value)

    @TypeConverter
    fun fromState(value: TransactionState): String = value.name

    // No category converter: a category is a `categories` row id stored as TEXT, so Room persists
    // it directly. See architecture.md #data-representation.

    @TypeConverter
    fun toRuleSource(value: String): RuleSource = RuleSource.valueOf(value)

    @TypeConverter
    fun fromRuleSource(value: RuleSource): String = value.name
}
