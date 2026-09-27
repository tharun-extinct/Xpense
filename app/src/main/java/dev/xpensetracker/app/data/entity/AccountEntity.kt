package dev.xpensetracker.app.data.entity

import androidx.room.Entity

/** Identity is the (issuer, accountTail) tuple; see architecture.md #identity-and-ownership. */
@Entity(tableName = "accounts", primaryKeys = ["issuer", "accountTail"])
data class AccountEntity(
    val issuer: String,
    val accountTail: String,
    val lastSeenBalanceMinor: Long?,
    val lastSeenAtUtcMillis: Long,
)
