package com.moneymatters.app.core.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The five double-entry account types; debit/credit meaning is fixed by this, never by UI choice. */
enum class AccountType { Asset, Liability, Equity, Income, Expense }

/**
 * What kind of real-world account an Asset or Liability is, for grouping and display. The
 * ledger's debit/credit rules come from [AccountType] alone; this never changes them.
 */
enum class AccountKind { Bank, Cash, CreditCard, Loan, FixedDeposit, Investment }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: AccountType,
    val parentId: String? = null,
    val isArchived: Boolean = false,
    /** Null for Income, Expense and Equity accounts, which have no real-world kind. */
    val kind: AccountKind? = null,
    /** False for assets tracked but kept out of net worth, like EPF. Defaults to counted. */
    @ColumnInfo(defaultValue = "1") val inNetWorth: Boolean = true,
)

/** Mirrors `TxnAction`'s lifecycle in feature/transactions, one level down (DB, not UI). */
enum class TransactionStatus { Draft, Posted, Reconciled, Locked, PriorPeriodAdjustment }

/** Why the money moved: an accountability tag, not a judgement (design/DESIGN.md keeps these neutral). */
enum class DecisionKind { Need, Want, Investment }
enum class PlanKind { Planned, Unplanned }

/** How a payment was made (plan.html → P04). */
enum class PaymentMethod { Upi, Card, NetBanking, Cash }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    /** ISO-8601 (`yyyy-MM-dd`); stored as text so it sorts and queries without a converter. */
    val date: String,
    val payee: String,
    val memo: String? = null,
    val status: TransactionStatus,
    val decision: DecisionKind? = null,
    val plan: PlanKind? = null,
    val paymentMethod: PaymentMethod? = null,
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
    /** Set on the holding's leg of a buy or sell: shares/fund units in micro-units, and the price in 1/10,000 rupee. Detail only; money is the debit/credit. */
    val units: Long? = null,
    val unitPrice: Long? = null,
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

/**
 * A month-end close (plan.html → P17). Periods are stored as date ranges because the investor can change
 * their month start day later. [hash] chains each close to the one before it, so any later edit to a closed
 * period shows up when the chain is re-checked; [snapshot] is the price snapshot the hash covers.
 */
@Entity(tableName = "closed_periods")
data class ClosedPeriodEntity(
    @PrimaryKey val start: String,
    val end: String,
    val closedAtMillis: Long,
    val prevHash: String,
    val hash: String,
    val snapshot: String,
)

/** What a credit card has beyond its liability account: the limit it was issued with and the day its statement is cut. */
@Entity(tableName = "credit_cards", foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"])])
data class CreditCardEntity(
    @PrimaryKey val accountId: String,
    val limitPaise: Long,
    /** Day of the month, 1 to 28. */
    val statementDay: Int,
)
