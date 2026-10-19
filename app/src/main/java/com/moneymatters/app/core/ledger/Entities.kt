package com.moneymatters.app.core.ledger

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The five double-entry account types; debit/credit meaning is fixed by this, never by UI choice. */
enum class AccountType { Asset, Liability, Equity, Income, Expense }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: AccountType,
    val parentId: String? = null,
    val isArchived: Boolean = false,
)

/** Mirrors `TxnAction`'s lifecycle in feature/transactions, one level down (DB, not UI). */
enum class TransactionStatus { Draft, Posted, Reconciled, Locked, PriorPeriodAdjustment }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    /** ISO-8601 (`yyyy-MM-dd`); stored as text so it sorts and queries without a converter. */
    val date: String,
    val payee: String,
    val memo: String? = null,
    val status: TransactionStatus,
)

/**
 * One leg of a transaction. Exactly one of [debitPaise]/[creditPaise] is set and positive;
 * [LedgerRepository] enforces that every transaction's postings sum to zero before it writes them.
 */
@Entity(
    tableName = "postings",
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AccountEntity::class, ["id"], ["accountId"]),
    ],
    indices = [Index("transactionId"), Index("accountId")],
)
data class PostingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val accountId: String,
    val debitPaise: Long? = null,
    val creditPaise: Long? = null,
)

/** Every ledger mutation, append-only (plan.html → "audit log" is non-negotiable, never edited or deleted). */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val whenMillis: Long,
    val transactionId: String?,
    val action: String,
    val summary: String,
    val before: String? = null,
    val after: String? = null,
)
