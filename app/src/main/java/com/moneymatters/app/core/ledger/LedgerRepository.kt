package com.moneymatters.app.core.ledger

import androidx.room.withTransaction
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
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
    /** Accounts that are stock/fund holdings; their postings must carry units, so Quick Add keeps them out of its pickers. */
    val holdingAccountIds: Set<String> = emptySet(),
    val closedPeriods: List<ClosedPeriodEntity> = emptyList(),
    val creditCards: List<CreditCardEntity> = emptyList(),
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
        database.invalidationTracker.createFlow("accounts", "transactions", "postings", "audit_log", "holdings", "closed_periods", "credit_cards").map {
            database.withTransaction {
                LedgerSnapshot(
                    dao.getAllAccounts(), dao.getAllTransactions(), dao.getAllPostings(), dao.getAllAuditLog(),
                    database.investDao().getAllHoldings().map { it.accountId }.toSet(),
                    dao.getAllClosedPeriods(),
                    dao.getAllCreditCards(),
                )
            }
        }

    suspend fun createAccount(name: String, type: AccountType, parentId: String? = null, kind: AccountKind? = null, inNetWorth: Boolean = true): String {
        val id = newId()
        dao.insertAccount(AccountEntity(id, name, type, parentId, kind = kind, inNetWorth = inNetWorth))
        return id
    }

    /** Records a card's limit and statement day; [accountId] must be a credit card account. */
    suspend fun addCreditCardDetails(accountId: String, limit: Paise, statementDay: Int) {
        require(limit.value > 0) { "A card needs a limit" }
        require(statementDay in 1..28) { "The statement day must be from 1 to 28" }
        dao.insertCreditCard(CreditCardEntity(accountId, limit.value, statementDay))
    }

    /**
     * Changes a card's limit and statement day, or sets them for a card that was added without. Balances and entries are untouched.
     * Refused for an account that isn't a credit card.
     */
    suspend fun updateCreditCardDetails(accountId: String, limit: Paise, statementDay: Int) {
        require(limit.value > 0) { "A card needs a limit" }
        require(statementDay in 1..28) { "The statement day must be from 1 to 28" }
        val account = dao.getAccount(accountId) ?: throw NoSuchElementException("No account with id $accountId")
        require(account.kind == AccountKind.CreditCard) { "${account.name} isn't a credit card" }
        dao.upsertCreditCard(CreditCardEntity(accountId, limit.value, statementDay))
    }

    suspend fun accountIdByName(name: String): String? = dao.findAccountByName(name)?.id

    /** Hides an account from pickers and the Accounts page without touching its history (Settings → account editor). */
    suspend fun archiveAccount(id: String) {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        dao.updateAccount(account.copy(isArchived = true))
    }

    /** Counts an account in (or leaves it out of) net worth, e.g. EPF (Settings → account editor). */
    suspend fun setInNetWorth(id: String, inNetWorth: Boolean) {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        dao.updateAccount(account.copy(inNetWorth = inNetWorth))
    }

    /** Pins an account to Home's account section, or takes it off again. */
    suspend fun setPinned(id: String, pinned: Boolean) {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        dao.updateAccount(account.copy(isPinned = pinned))
    }

    suspend fun unarchiveAccount(id: String) {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        dao.updateAccount(account.copy(isArchived = false))
    }

    /** Gives an account a new name; names stay unique. */
    suspend fun renameAccount(id: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Give it a name" }
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        if (account.name == trimmed) return
        require(dao.findAccountByName(trimmed) == null) { "There is already an account called $trimmed" }
        dao.updateAccount(account.copy(name = trimmed))
    }

    /** Every transaction with a posting on [accountId], drafts included. */
    suspend fun transactionIdsFor(accountId: String): List<String> = dao.transactionIdsForAccount(accountId)

    suspend fun accountIdsIn(transactionId: String): Set<String> = dao.getPostingsForTransaction(transactionId).map { it.accountId }.toSet()

    suspend fun transaction(id: String): TransactionEntity? = dao.getTransaction(id)

    /** Every transaction id, for checking that records pointing at entries still point at something. */
    suspend fun allTransactionIds(): Set<String> = dao.getAllTransactions().map { it.id }.toSet()

    /** An account's name, for labelling the entries made on its behalf. */
    suspend fun accountName(id: String): String = requireNotNull(dao.getAccount(id)) { "No such account" }.name

    /**
     * Removes an account that has nothing posted to it any more, e.g. a deposit or holding whose entries were just deleted.
     * An account with postings is refused: its history has to stay.
     */
    suspend fun deleteEmptyAccount(id: String) {
        check(dao.transactionIdsForAccount(id).isEmpty()) { "That account still has entries" }
        dao.deleteAccount(id)
    }

    /**
     * Deletes an account added by mistake: one with no entries, or only the opening-balance entry that came with it (removed
     * along with it, and logged). An account with any other history is refused, so is an investment (delete it from Invest),
     * Equity, and one whose opening entry sits in a reconciled or closed month. Archive hides those instead.
     */
    suspend fun deleteAccount(id: String) = database.withTransaction {
        val account = dao.getAccount(id) ?: throw NoSuchElementException("No account with id $id")
        require(account.type != AccountType.Equity) { "${account.name} is a system account and can't be deleted" }
        require(account.kind != AccountKind.Investment && account.kind != AccountKind.FixedDeposit) {
            "${account.name} is an investment: delete it from the Invest page"
        }
        val entries = dao.transactionIdsForAccount(id)
        val opening = entries.filter { dao.getTransaction(it)?.payee == "Opening balance: ${account.name}" }
        require(entries.size == opening.size) { "${account.name} has entries, so it can't be deleted; archive it instead" }
        deleteEntries(opening, "Account ${account.name} deleted")
        dao.deleteCreditCard(id)
        dao.deleteAccount(id)
    }

    /**
     * Permanently removes Draft or Posted entries, with their postings, so they disappear from every list, journal and balance.
     * Reconciled and closed-month entries are immutable and are refused (reverse those instead), and one failure refuses them all.
     * Each deletion leaves a line in the audit log: the log itself is never trimmed.
     */
    suspend fun deleteEntries(ids: Collection<String>, reason: String) = database.withTransaction {
        val entries = ids.distinct().map { requireTransaction(it) }
        for (t in entries) {
            if (t.status != TransactionStatus.Draft && t.status != TransactionStatus.Posted) {
                throw IllegalLifecycleTransitionException(
                    "\"${t.payee}\" on ${t.date} is ${t.status.name.lowercase()}, so it can't be deleted; reverse it instead",
                )
            }
            requireOpenDate(LocalDate.parse(t.date))
        }
        for (t in entries) {
            val legs = dao.getPostingsForTransaction(t.id)
            val amount = Paise(legs.sumOf { it.debitPaise ?: 0 })
            dao.deletePostingsForTransaction(t.id)
            dao.deleteTransaction(t.id)
            audit(t.id, "Deleted", "Deleted ${t.payee} (${t.date}, ${amount.formatInr(showPaise = true)}): $reason", before = t.payee)
        }
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
        requireUnitsOnHoldingLegs(postings)
        requireOpenDate(date)
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
        requireUnitsOnHoldingLegs(postings)
        requireOpenDate(date)
        database.withTransaction {
            val oldPostings = dao.getPostingsForTransaction(transactionId)
            val updated = existing.copy(
                date = date.toString(), payee = payee, memo = memo,
                decision = tags.decision, plan = tags.plan, paymentMethod = tags.paymentMethod,
            )
            dao.updateTransaction(updated)
            dao.deletePostingsForTransaction(transactionId)
            dao.insertPostings(postings.toEntities(transactionId))
            // One line per thing that actually changed, each with its before and after, so repeated edits stay readable.
            fun money(p: Paise) = p.formatInr(showPaise = p.value % 100 != 0L)
            fun sides(legs: List<Triple<String, Long?, Long?>>) = legs.map { (account, debit, credit) -> account to (debit != null) }.sortedBy { it.first + it.second }
            suspend fun accountNames(legs: List<Pair<String, Boolean>>): String {
                val names = mutableListOf<String>()

                for ((id, isDebit) in legs) {
                    val name = dao.getAccount(id)?.name ?: id
                    names.add("${if (isDebit) "Dr " else "Cr "}$name")
                } 

                return names.joinToString(", ")
            }
            val oldLegs = sides(oldPostings.map { Triple(it.accountId, it.debitPaise, it.creditPaise) })
            val newLegs = sides(postings.map { Triple(it.accountId, it.debit?.value, it.credit?.value) })
            val oldAmount = Paise(oldPostings.sumOf { it.debitPaise ?: 0 })
            val newAmount = Paise(postings.sumOf { it.debit?.value ?: 0 })
            fun tagText(t: TransactionEntity) = listOfNotNull(t.decision?.name, t.plan?.name, t.paymentMethod?.name).joinToString(" · ").ifEmpty { "None" }
            if (existing.payee != payee) audit(transactionId, "Edited", "Payee", before = existing.payee, after = payee)
            if (existing.date != date.toString()) audit(transactionId, "Edited", "Date", before = existing.date, after = date.toString())
            if (oldAmount != newAmount) audit(transactionId, "Edited", "Amount", before = money(oldAmount), after = money(newAmount))
            if (oldLegs != newLegs) audit(transactionId, "Edited", "Accounts", before = accountNames(oldLegs), after = accountNames(newLegs))
            if (existing.memo != memo) audit(transactionId, "Edited", "Note", before = existing.memo ?: "None", after = memo ?: "None")
            if (tagText(existing) != tagText(updated)) audit(transactionId, "Edited", "Tags", before = tagText(existing), after = tagText(updated))
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
        if (invest.pendingCountForOrder(transactionId) > 0) {
            throw IllegalLifecycleTransitionException("This entry sent money to In Transit for a fund order; cancel the order from the Invest tab instead")
        }
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
            invest.deleteCorporateActionsForTransaction(transactionId)
            invest.reopenPendingForAllotment(transactionId)
            invest.reopenPendingCancelledBy(transactionId)
            invest.reopenDepositsClosedBy(transactionId)
            invest.deleteDepositsOpenedBy(transactionId)
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
        requireUnitsOnHoldingLegs(postings)
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
        return invest.lotsFromTransaction(transactionId).isNotEmpty() || invest.consumptionCountForSale(transactionId) > 0 ||
            invest.pendingCountForOrder(transactionId) > 0
    }

    private suspend fun requireOpenDate(date: LocalDate) {
        if (dao.closedPeriodsCovering(date.toString()) > 0) {
            throw ClosedPeriodException("That date is in a closed month; fix it with a prior-period adjustment instead")
        }
    }

    private suspend fun requireTransaction(id: String) =
        dao.getTransaction(id) ?: throw NoSuchElementException("No transaction with id $id")

    /** A stock or fund holding's postings must carry units, or its lots and its ledger balance would drift apart. */
    private suspend fun requireUnitsOnHoldingLegs(postings: List<PostingLine>) {
        for (p in postings) {
            require(p.units != null || database.investDao().holdingForAccount(p.accountId) == null) {
                "Posting on holding account ${p.accountId} needs units; use the Invest tab to buy or sell"
            }
        }
    }

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
