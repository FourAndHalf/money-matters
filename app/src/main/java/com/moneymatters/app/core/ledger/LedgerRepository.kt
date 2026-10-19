package com.moneymatters.app.core.ledger

import androidx.room.withTransaction
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** One leg of a transaction as given by a caller, before it has an id. */
data class PostingLine(val accountId: String, val debit: Paise? = null, val credit: Paise? = null)

/**
 * The real double-entry ledger: every write here enforces plan.html's non-negotiable rules
 * (balanced postings, a fixed lifecycle, an append-only audit log) instead of trusting a caller.
 * UI layers call this instead of touching [LedgerDao] directly.
 */
class LedgerRepository(
    private val database: MoneyMattersDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.ledgerDao()

    fun accounts(): Flow<List<AccountEntity>> = dao.accounts()
    fun transactions(): Flow<List<TransactionEntity>> = dao.transactions()
    fun auditLogFor(transactionId: String): Flow<List<AuditLogEntity>> = dao.auditLogForTransaction(transactionId)

    suspend fun createAccount(name: String, type: AccountType, parentId: String? = null): String {
        val id = newId()
        dao.insertAccount(AccountEntity(id, name, type, parentId))
        return id
    }

    suspend fun accountIdByName(name: String): String? = dao.findAccountByName(name)?.id

    /** Looks an account up by name first; creates it only if missing. Used for shared accounts like `Equity:Opening`. */
    suspend fun createAccountIfAbsent(name: String, type: AccountType, parentId: String? = null): String =
        accountIdByName(name) ?: createAccount(name, type, parentId)

    /** Runs several repository writes as one database transaction: all of them land, or none do. */
    suspend fun <R> inTransaction(block: suspend () -> R): R = database.withTransaction(block)

    /** Net of every posting against this account: debit − credit. Sign meaning depends on [AccountType]. */
    suspend fun accountBalance(accountId: String): Paise =
        dao.getPostingsForAccount(accountId).fold(Paise.Zero) { acc, p ->
            acc + Paise(p.debitPaise ?: 0L) - Paise(p.creditPaise ?: 0L)
        }

    /** Creates a new transaction as [TransactionStatus.Draft] or [TransactionStatus.Posted]. */
    suspend fun postTransaction(
        date: LocalDate,
        payee: String,
        postings: List<PostingLine>,
        memo: String? = null,
        status: TransactionStatus = TransactionStatus.Posted,
    ): String {
        require(status == TransactionStatus.Draft || status == TransactionStatus.Posted) {
            "A new transaction must start Draft or Posted, not $status"
        }
        validateBalanced(postings)
        val id = newId()
        database.withTransaction {
            dao.insertTransaction(TransactionEntity(id, date.toString(), payee, memo, status))
            dao.insertPostings(postings.toEntities(id))
            audit(id, "Created", "$payee (${status.name}), ${postings.size} postings")
        }
        return id
    }

    /** Replaces a Draft or Posted transaction's fields and postings. Anything further along is immutable. */
    suspend fun edit(
        transactionId: String,
        date: LocalDate,
        payee: String,
        postings: List<PostingLine>,
        memo: String? = null,
    ) {
        val existing = requireTransaction(transactionId)
        if (existing.status != TransactionStatus.Draft && existing.status != TransactionStatus.Posted) {
            throw IllegalLifecycleTransitionException("Cannot edit a ${existing.status} transaction; use reverse or a prior-period adjustment")
        }
        validateBalanced(postings)
        database.withTransaction {
            dao.updateTransaction(existing.copy(date = date.toString(), payee = payee, memo = memo))
            dao.deletePostingsForTransaction(transactionId)
            dao.insertPostings(postings.toEntities(transactionId))
            audit(transactionId, "Edited", payee, before = existing.payee, after = payee)
        }
    }

    /** Posted → Reconciled, once a statement or bank balance confirms the entry. */
    suspend fun reconcile(transactionId: String) = transition(transactionId, from = TransactionStatus.Posted, to = TransactionStatus.Reconciled)

    /** Reconciled → Locked, done in bulk by month-end close (plan.html → P17), never by hand per entry. */
    suspend fun lock(transactionId: String) = transition(transactionId, from = TransactionStatus.Reconciled, to = TransactionStatus.Locked)

    /** Only a Reconciled entry can be reversed; this posts a new, inverted entry rather than deleting history. */
    suspend fun reverse(transactionId: String, reason: String): String {
        val original = requireTransaction(transactionId)
        if (original.status != TransactionStatus.Reconciled) {
            throw IllegalLifecycleTransitionException("Cannot reverse a ${original.status} transaction; only Reconciled entries can be")
        }
        val originalPostings = dao.getPostingsForTransaction(transactionId)
        val inverted = originalPostings.map {
            PostingLine(it.accountId, debit = it.creditPaise?.let(::Paise), credit = it.debitPaise?.let(::Paise))
        }
        val reversalId = newId()
        database.withTransaction {
            dao.insertTransaction(
                TransactionEntity(reversalId, original.date, original.payee, "Reversal of $transactionId: $reason", TransactionStatus.Posted),
            )
            dao.insertPostings(inverted.toEntities(reversalId))
            audit(reversalId, "Reversal", "Reverses $transactionId: $reason")
            audit(transactionId, "Reversed", "Reversed by $reversalId: $reason")
        }
        return reversalId
    }

    /** Only a Locked (closed-month) entry can get a prior-period adjustment; it posts a new, separate entry. */
    suspend fun postPriorPeriodAdjustment(
        originalTransactionId: String,
        date: LocalDate,
        payee: String,
        postings: List<PostingLine>,
        reason: String,
        memo: String? = null,
    ): String {
        val original = requireTransaction(originalTransactionId)
        if (original.status != TransactionStatus.Locked && original.status != TransactionStatus.PriorPeriodAdjustment) {
            throw IllegalLifecycleTransitionException(
                "Cannot post a prior-period adjustment against a ${original.status} transaction; only a closed month needs one",
            )
        }
        validateBalanced(postings)
        val id = newId()
        database.withTransaction {
            dao.insertTransaction(TransactionEntity(id, date.toString(), payee, memo, TransactionStatus.PriorPeriodAdjustment))
            dao.insertPostings(postings.toEntities(id))
            audit(id, "PriorPeriodAdjustment", "Adjusts $originalTransactionId: $reason")
        }
        return id
    }

    private suspend fun transition(transactionId: String, from: TransactionStatus, to: TransactionStatus) {
        val existing = requireTransaction(transactionId)
        if (existing.status != from) {
            throw IllegalLifecycleTransitionException("Cannot move a ${existing.status} transaction to $to; it must be $from first")
        }
        database.withTransaction {
            dao.updateTransaction(existing.copy(status = to))
            audit(transactionId, "StatusChanged", "$from → $to", before = from.name, after = to.name)
        }
    }

    private suspend fun requireTransaction(id: String) =
        dao.getTransaction(id) ?: throw NoSuchElementException("No transaction with id $id")

    private fun validateBalanced(postings: List<PostingLine>) {
        require(postings.size >= 2) { "A transaction needs at least two postings, got ${postings.size}" }
        for (p in postings) {
            val hasDebit = p.debit != null && p.debit.value > 0
            val hasCredit = p.credit != null && p.credit.value > 0
            require(hasDebit != hasCredit) { "Posting on ${p.accountId} must have exactly one of a positive debit or credit" }
        }
        val debits = postings.fold(Paise.Zero) { acc, p -> acc + (p.debit ?: Paise.Zero) }
        val credits = postings.fold(Paise.Zero) { acc, p -> acc + (p.credit ?: Paise.Zero) }
        if (debits != credits) {
            throw UnbalancedTransactionException("Debits ${debits.value} ≠ credits ${credits.value}")
        }
    }

    private fun List<PostingLine>.toEntities(transactionId: String) = map {
        PostingEntity(transactionId = transactionId, accountId = it.accountId, debitPaise = it.debit?.value, creditPaise = it.credit?.value)
    }

    private suspend fun audit(transactionId: String, action: String, summary: String, before: String? = null, after: String? = null) {
        dao.insertAuditLog(AuditLogEntity(whenMillis = now(), transactionId = transactionId, action = action, summary = summary, before = before, after = after))
    }
}
