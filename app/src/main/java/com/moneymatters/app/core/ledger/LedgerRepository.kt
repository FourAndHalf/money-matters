package com.moneymatters.app.core.ledger

import androidx.room.withTransaction
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Every row of the ledger at one moment, for read-only screens. Includes archived accounts so old postings still resolve. */
data class LedgerSnapshot(
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val postings: List<PostingEntity>,
    val audit: List<AuditLogEntity>,
)

/** The optional P04 tags on a transaction; none affect balances. */
data class TransactionTags(val decision: DecisionKind? = null, val plan: PlanKind? = null, val paymentMethod: PaymentMethod? = null)

/**
 * One leg of a transaction as given by a caller, before it has an id. [units] and [unitPrice] are
 * set only on a holding's leg of an investment buy or sell; they never affect balancing.
 */
data class PostingLine(
    val accountId: String,
    val debit: Paise? = null,
    val credit: Paise? = null,
    val units: Units? = null,
    val unitPrice: UnitPrice? = null,
)

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

    /**
     * The whole ledger, re-read after every write. All four tables are read in one transaction, so
     * a snapshot never holds a transaction without its postings. Fine at personal scale; page it if it ever isn't.
     */
    fun snapshot(): Flow<LedgerSnapshot> =
        database.invalidationTracker.createFlow("accounts", "transactions", "postings", "audit_log").map {
            database.withTransaction {
                LedgerSnapshot(dao.getAllAccounts(), dao.getAllTransactions(), dao.getAllPostings(), dao.getAllAuditLog())
            }
        }

    suspend fun createAccount(name: String, type: AccountType, parentId: String? = null, kind: AccountKind? = null, inNetWorth: Boolean = true): String {
        val id = newId()
        dao.insertAccount(AccountEntity(id, name, type, parentId, kind = kind, inNetWorth = inNetWorth))
        return id
    }

    suspend fun accountIdByName(name: String): String? = dao.findAccountByName(name)?.id

    /** Hides an account from pickers and the Accounts page without touching its history (Settings → account editor). */
    suspend fun archiveAccount(id: String) {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        dao.updateAccount(account.copy(isArchived = true))
    }

    suspend fun unarchiveAccount(id: String) {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        dao.updateAccount(account.copy(isArchived = false))
    }

    /** Looks an account up by name first; creates it only if missing. Used for shared accounts like `Equity:Opening`. */
    suspend fun createAccountIfAbsent(name: String, type: AccountType, parentId: String? = null, kind: AccountKind? = null): String =
        accountIdByName(name) ?: createAccount(name, type, parentId, kind)

    /** Runs several repository writes as one database transaction: all of them land, or none do. */
    suspend fun <R> inTransaction(block: suspend () -> R): R = database.withTransaction(block)

    /** Flushes the write-ahead log into the main database file, so a raw file copy (Settings → Backup) is complete. */
    suspend fun checkpoint() = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
    }

    /** Net of every booked (non-draft) posting against this account: debit − credit. Sign meaning depends on [AccountType]. */
    suspend fun accountBalance(accountId: String): Paise =
        dao.getBookedPostingsForAccount(accountId).fold(Paise.Zero) { acc, p ->
            acc + Paise(p.debitPaise ?: 0L) - Paise(p.creditPaise ?: 0L)
        }

    /** Creates a new transaction as [TransactionStatus.Draft] or [TransactionStatus.Posted]. */
    suspend fun postTransaction(
        date: LocalDate,
        payee: String,
        postings: List<PostingLine>,
        memo: String? = null,
        status: TransactionStatus = TransactionStatus.Posted,
        tags: TransactionTags = TransactionTags(),
    ): String {
        require(status == TransactionStatus.Draft || status == TransactionStatus.Posted) {
            "A new transaction must start Draft or Posted, not $status"
        }
        validateBalanced(postings)
        val id = newId()
        database.withTransaction {
            dao.insertTransaction(TransactionEntity(id, date.toString(), payee, memo, status, tags.decision, tags.plan, tags.paymentMethod))
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
        tags: TransactionTags = TransactionTags(),
    ) {
        val existing = requireTransaction(transactionId)
        if (existing.status != TransactionStatus.Draft && existing.status != TransactionStatus.Posted) {
            throw IllegalLifecycleTransitionException("Cannot edit a ${existing.status} transaction; use reverse or a prior-period adjustment")
        }
        if (isInvestmentEntry(transactionId)) {
            throw IllegalLifecycleTransitionException("An investment entry's units and lots can't be edited in place; reverse it and enter it again")
        }
        validateBalanced(postings)
        database.withTransaction {
            dao.updateTransaction(
                existing.copy(
                    date = date.toString(), payee = payee, memo = memo,
                    decision = tags.decision, plan = tags.plan, paymentMethod = tags.paymentMethod,
                ),
            )
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
        val invest = database.investDao()
        val createdLots = invest.lotsFromTransaction(transactionId)
        if (createdLots.any { invest.consumptionCountForLot(it.id) > 0 }) {
            throw IllegalLifecycleTransitionException("Cannot reverse this purchase while later sales have used its lots; reverse those sales first")
        }
        val originalPostings = dao.getPostingsForTransaction(transactionId)
        val inverted = originalPostings.map {
            PostingLine(
                it.accountId,
                debit = it.creditPaise?.let(::Paise),
                credit = it.debitPaise?.let(::Paise),
                units = it.units?.let(::Units),
                unitPrice = it.unitPrice?.let(::UnitPrice),
            )
        }
        val reversalId = newId()
        database.withTransaction {
            dao.insertTransaction(
                TransactionEntity(reversalId, original.date, original.payee, "Reversal of $transactionId: $reason", TransactionStatus.Posted),
            )
            dao.insertPostings(inverted.toEntities(reversalId))
            // A reversed sale gives its lots their units back; a reversed purchase takes its (unused) lots away.
            invest.deleteConsumptionsForSale(transactionId)
            createdLots.forEach { invest.deleteLot(it.id) }
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

    private suspend fun isInvestmentEntry(transactionId: String): Boolean {
        val invest = database.investDao()
        return invest.lotsFromTransaction(transactionId).isNotEmpty() || invest.consumptionCountForSale(transactionId) > 0
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
        PostingEntity(
            transactionId = transactionId, accountId = it.accountId, debitPaise = it.debit?.value, creditPaise = it.credit?.value,
            units = it.units?.micro, unitPrice = it.unitPrice?.tenThousandths,
        )
    }

    private suspend fun audit(transactionId: String, action: String, summary: String, before: String? = null, after: String? = null) {
        dao.insertAuditLog(AuditLogEntity(whenMillis = now(), transactionId = transactionId, action = action, summary = summary, before = before, after = after))
    }
}
