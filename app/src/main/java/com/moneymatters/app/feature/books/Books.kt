package com.moneymatters.app.feature.books

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.depositValuation
import com.moneymatters.app.core.invest.valuations
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.AuditLogEntity
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.feature.accounts.AccountGroup
import com.moneymatters.app.feature.accounts.AccountLedgerState
import com.moneymatters.app.feature.accounts.AccountSummary
import com.moneymatters.app.feature.accounts.AccountsState
import com.moneymatters.app.feature.accounts.EntryStatus
import com.moneymatters.app.feature.accounts.LedgerEntry
import com.moneymatters.app.feature.accounts.LedgerSection
import com.moneymatters.app.feature.dashboard.HomeState
import com.moneymatters.app.feature.transactions.AuditEntry
import com.moneymatters.app.feature.transactions.DayGroup
import com.moneymatters.app.feature.transactions.Posting
import com.moneymatters.app.feature.transactions.TransactionDetail
import com.moneymatters.app.feature.transactions.TransactionSummary
import com.moneymatters.app.feature.transactions.TransactionsState
import com.moneymatters.app.feature.transactions.TxnStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Every read-only screen's state, built from one [LedgerSnapshot] so the pages can never disagree. */
@Immutable
data class BooksState(
    val home: HomeState,
    val accounts: AccountsState,
    val ledgers: Map<String, AccountLedgerState>,
    val transactions: TransactionsState,
    val details: Map<String, TransactionDetail>,
)

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")
private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")
private val Time = DateTimeFormatter.ofPattern("HH:mm")
private const val ALL_TIME = "All time"
const val NOT_IN_NET_WORTH = "Not in net worth"

/** The open period's first day: the latest [monthStartDay] on or before [today] (payday-to-payday months). */
fun periodStart(today: LocalDate, monthStartDay: Int): LocalDate {
    val thisMonth = today.withDayOfMonth(monthStartDay)
    return if (thisMonth.isAfter(today)) thisMonth.minusMonths(1) else thisMonth
}

/** 1 → `1st`, 22 → `22nd`, 11 → `11th`. */
internal fun ordinal(day: Int): String = day.toString() + when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

fun AccountKind.label() = when (this) {
    AccountKind.Bank -> "Bank account"
    AccountKind.Cash -> "Cash"
    AccountKind.CreditCard -> "Credit card"
    AccountKind.Loan -> "Loan"
    AccountKind.FixedDeposit -> "Fixed deposit"
    AccountKind.Investment -> "Investments"
}

fun PaymentMethod.label() = when (this) {
    PaymentMethod.Upi -> "UPI"
    PaymentMethod.Card -> "Card"
    PaymentMethod.NetBanking -> "Net banking"
    PaymentMethod.Cash -> "Cash"
}

/** `Expenses:Dining` → `Dining`; `Equity:Opening` → `Opening balance`. Real accounts keep their name. */
fun AccountEntity.displayName(): String = when {
    type == AccountType.Equity && name == "Equity:Opening" -> "Opening balance"
    else -> name.substringAfter(':')
}

private val AccountEntity.isReal get() = type == AccountType.Asset || type == AccountType.Liability

/** What the account's holder thinks of as its balance: money held for assets, money owed for liabilities. */
private fun AccountEntity.held(p: PostingEntity): Long {
    val net = (p.debitPaise ?: 0) - (p.creditPaise ?: 0)
    return if (type == AccountType.Liability) -net else net
}

/** Builds [BooksState] from [snapshot]. Pure: same input, same output, no I/O. */
fun buildBooks(
    snapshot: LedgerSnapshot,
    today: LocalDate,
    monthStartDay: Int = 1,
    zone: ZoneId = ZoneId.systemDefault(),
    invest: InvestSnapshot = InvestSnapshot.Empty,
): BooksState {
    val accountsById = snapshot.accounts.associateBy { it.id }
    val postingsByTxn = snapshot.postings.groupBy { it.transactionId }
    val auditByTxn = snapshot.audit.groupBy { it.transactionId }
    val booked = snapshot.transactions.filter { it.status != TransactionStatus.Draft }.map { it.id }.toSet()

    fun dateLabel(date: LocalDate) = date.format(if (date.year == today.year) DayMonth else DayMonthYear)

    // ---- per-transaction summaries ----
    val summaries = snapshot.transactions.associate { t ->
        t.id to summarize(t, postingsByTxn[t.id].orEmpty(), accountsById)
    }

    // ---- balances (booked postings only) ----
    val balances = HashMap<String, Long>()
    for (p in snapshot.postings) {
        if (p.transactionId !in booked) continue
        val account = accountsById[p.accountId] ?: continue
        balances[account.id] = (balances[account.id] ?: 0) + account.held(p)
    }
    val entryCounts = snapshot.postings.groupingBy { it.accountId }.eachCount()
    val realAccounts = snapshot.accounts.filter { it.isReal && !it.isArchived }

    // Holdings with a price count at market value, everything else at its ledger balance (cost).
    val marketByAccount = valuations(invest).mapNotNull { v -> v.marketValue?.let { v.holding.accountId to it } }.toMap()

    // Open deposits count interest earned to today even before it is booked, as holdings count market value.
    val unpostedByAccount = invest.fixedDeposits.filter { it.closedOn == null }.associate { fd ->
        fd.accountId to depositValuation(fd, Paise(balances[fd.accountId] ?: 0), today).unposted
    }

    // ---- Accounts (P08) ----
    val cardsById = snapshot.creditCards.associateBy { it.accountId }
    /** `Limit ₹1,00,000 · 8% used · statement on the 5th · ` for a card with details, else nothing. */
    fun cardNote(a: AccountEntity, balance: Long): String {
        val card = cardsById[a.id] ?: return ""
        // Balances are signed by what the account holds, so a liability's owed amount is positive.
        val owed = balance.coerceAtLeast(0)
        val used = (owed * 100 / card.limitPaise)
        return "Limit ${Paise(card.limitPaise).formatInr()} · $used% used · statement on the ${ordinal(card.statementDay)} · "
    }
    fun summaryRow(a: AccountEntity): AccountSummary {
        val count = entryCounts[a.id] ?: 0
        val kind = a.kind ?: if (a.type == AccountType.Liability) AccountKind.Loan else AccountKind.Bank
        return AccountSummary(
            id = a.id,
            kind = kind,
            name = a.name,
            subtitle = "${kind.label()} · " + cardNote(a, balances[a.id] ?: 0) + (if (count == 0) "no entries yet" else "$count ${if (count == 1) "entry" else "entries"}") +
                if (a.id in marketByAccount) " · at market" else if ((unpostedByAccount[a.id]?.value ?: 0) > 0) " · with accrued interest" else "",
            balance = marketByAccount[a.id] ?: (Paise(balances[a.id] ?: 0) + (unpostedByAccount[a.id] ?: Paise.Zero)),
            isLiability = a.type == AccountType.Liability,
            entryCount = count,
            cardLimit = cardsById[a.id]?.let { Paise(it.limitPaise) },
            statementDay = cardsById[a.id]?.statementDay,
            isPinned = a.isPinned,
        )
    }
    val groupOrder = listOf(
        "Bank & cash" to setOf(AccountKind.Bank, AccountKind.Cash),
        "Credit cards" to setOf(AccountKind.CreditCard),
        "Fixed deposits" to setOf(AccountKind.FixedDeposit),
        "Investments" to setOf(AccountKind.Investment),
        "Loans" to setOf(AccountKind.Loan),
    )
    // Accounts flagged out of net worth (EPF) are still listed, in their own group, but never summed.
    val rows = realAccounts.filter { it.inNetWorth }.map(::summaryRow)
    val excludedRows = realAccounts.filter { !it.inNetWorth }.map(::summaryRow)
    val groups = groupOrder.mapNotNull { (title, kinds) ->
        rows.filter { it.kind in kinds }.sortedBy { it.name.lowercase() }.takeIf { it.isNotEmpty() }?.let { AccountGroup(title, it) }
    } + listOfNotNull(
        excludedRows.sortedBy { it.name.lowercase() }.takeIf { it.isNotEmpty() }?.let { AccountGroup(NOT_IN_NET_WORTH, it) },
    )
    val assets = Paise(rows.filter { !it.isLiability }.sumOf { it.balance.value })
    val liabilities = Paise(rows.filter { it.isLiability }.sumOf { it.balance.value })
    val accountsState = AccountsState(
        netWorth = assets - liabilities,
        assetsTotal = assets,
        liabilitiesTotal = liabilities,
        asOfLabel = "As of ${dateLabel(today)}",
        groups = groups,
        earmarks = emptyList(),
    )

    // ---- Account ledgers (P09) ----
    val transactionsById = snapshot.transactions.associateBy { it.id }
    val postingsByAccount = snapshot.postings.groupBy { it.accountId }
    val ledgers = realAccounts.associate { account ->
        val ownPostings = postingsByAccount[account.id].orEmpty()
            .mapNotNull { p -> transactionsById[p.transactionId]?.let { it to p } }
            .sortedWith(compareBy({ it.first.date }, { firstAuditMillis(auditByTxn[it.first.id]) }))
        var running = 0L
        val oldestFirst = ownPostings.map { (t, p) ->
            val amount = account.held(p)
            if (t.status != TransactionStatus.Draft) running += amount
            val summary = summaries.getValue(t.id)
            LedgerEntry(
                id = t.id,
                dateLabel = dateLabel(LocalDate.parse(t.date)),
                chip = chipFor(t.payee),
                payee = t.payee,
                meta = listOfNotNull(summary.category, t.paymentMethod?.label()).joinToString(" · "),
                amount = Paise(amount),
                runningBalance = Paise(running),
                status = t.status.toEntryStatus(),
                isTransfer = summary.isTransfer,
            )
        }
        val entries = oldestFirst.reversed()
        account.id to AccountLedgerState(
            accountId = account.id,
            accountName = account.name,
            accountTypeLabel = (account.kind ?: AccountKind.Bank).label(),
            currentBalance = Paise(balances[account.id] ?: 0),
            isLiability = account.type == AccountType.Liability,
            periodLabel = ALL_TIME,
            availablePeriods = listOf(ALL_TIME),
            unreconciledCount = entries.count { it.status != EntryStatus.Reconciled },
            sections = listOf(LedgerSection(entries = entries)),
        )
    }

    // ---- Transactions (P06) and detail (P07) ----
    val newestFirst = snapshot.transactions.sortedWith(
        compareByDescending<TransactionEntity> { it.date }.thenByDescending { firstAuditMillis(auditByTxn[it.id]) },
    )
    val dayGroups = newestFirst.groupBy { it.date }.map { (date, txns) ->
        DayGroup(dateLabel(LocalDate.parse(date)), txns.map { summaries.getValue(it.id) }, LocalDate.parse(date))
    }
    val transactionsState = TransactionsState(dayGroups, accounts = realAccounts.map { it.name }.sorted())

    val details = snapshot.transactions.associate { t ->
        val summary = summaries.getValue(t.id)
        val audit = auditByTxn[t.id].orEmpty()
        t.id to TransactionDetail(
            id = t.id,
            payee = t.payee,
            amount = summary.amount,
            dateLabel = dateLabel(LocalDate.parse(t.date)),
            timeLabel = audit.firstOrNull()?.let { Instant.ofEpochMilli(it.whenMillis).atZone(zone).format(Time) } ?: "",
            accountName = summary.accountName,
            category = summary.category,
            status = summary.status,
            postings = summary.postings,
            decision = t.decision,
            plan = t.plan,
            reason = t.memo,
            audit = audit.map { it.toAuditEntry(zone, ::dateLabel) },
            isTransfer = summary.isTransfer,
        )
    }

    // ---- Home (simple P03) ----
    val start = periodStart(today, monthStartDay)
    var income = 0L
    var spent = 0L
    for (p in snapshot.postings) {
        val t = transactionsById[p.transactionId] ?: continue
        if (t.status == TransactionStatus.Draft || LocalDate.parse(t.date).isBefore(start) || LocalDate.parse(t.date).isAfter(today)) continue
        when (accountsById[p.accountId]?.type) {
            AccountType.Income -> income += (p.creditPaise ?: 0) - (p.debitPaise ?: 0)
            AccountType.Expense -> spent += (p.debitPaise ?: 0) - (p.creditPaise ?: 0)
            else -> Unit
        }
    }
    val home = HomeState(
        dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        netWorth = accountsState.netWorth,
        assetsTotal = assets,
        liabilitiesTotal = liabilities,
        periodLabel = "Since ${dateLabel(start)}",
        income = Paise(income),
        spent = Paise(spent),
        accounts = rows.filter { it.isPinned }.sortedBy { it.name.lowercase() },
        recent = dayGroups.flatMap { it.items }.take(5),
        draftCount = snapshot.transactions.count { it.status == TransactionStatus.Draft },
    )

    return BooksState(home, accountsState, ledgers, transactionsState, details)
}

/**
 * How a transaction reads in a list. [TransactionSummary.amount] is its effect on net worth
 * (negative = money out). A transfer between two of your own accounts leaves net worth unchanged,
 * so it carries the amount that moved, positive, and is flagged [TransactionSummary.isTransfer].
 */
private fun summarize(t: TransactionEntity, postings: List<PostingEntity>, accounts: Map<String, AccountEntity>): TransactionSummary {
    val legs = postings.mapNotNull { p -> accounts[p.accountId]?.let { it to p } }
    val real = legs.filter { it.first.isReal }
    val other = legs.filter { !it.first.isReal }
    var isTransfer = false
    val (amount, accountName, category) = if (other.isNotEmpty()) {
        val net = real.sumOf { (_, p) -> (p.debitPaise ?: 0) - (p.creditPaise ?: 0) }
        Triple(net, real.joinToString(", ") { it.first.name }, other.joinToString(", ") { it.first.displayName() })
    } else if (real.map { it.first.id }.distinct().size == 1) {
        // Both legs on one account: a split or bonus, which restates units and moves no money.
        Triple(0L, real.first().first.name, "Corporate action")
    } else {
        val from = real.filter { it.second.creditPaise != null }
        val to = real.filter { it.second.debitPaise != null }
        isTransfer = true
        Triple(
            from.sumOf { it.second.creditPaise ?: 0 },
            from.joinToString(", ") { it.first.name },
            "Transfer to " + to.joinToString(", ") { it.first.name },
        )
    }
    return TransactionSummary(
        id = t.id,
        chip = chipFor(t.payee),
        payee = t.payee,
        category = category,
        accountName = accountName,
        amount = Paise(amount),
        status = TxnStatus.valueOf(t.status.name),
        postings = legs.map { (a, p) -> Posting(a.name, p.debitPaise?.let(::Paise), p.creditPaise?.let(::Paise)) },
        decision = t.decision,
        plan = t.plan,
        uncategorized = other.any { it.first.name == SUSPENSE_ACCOUNT },
        isTransfer = isTransfer,
    )
}

/** Where uncategorized money waits (P06's suspense inbox). */
const val SUSPENSE_ACCOUNT = "Suspense"

private fun chipFor(payee: String) = payee.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "·"

private fun firstAuditMillis(audit: List<AuditLogEntity>?) = audit?.firstOrNull()?.whenMillis ?: 0L

private fun TransactionStatus.toEntryStatus(): EntryStatus? = when (this) {
    TransactionStatus.Draft -> EntryStatus.Draft
    TransactionStatus.Posted -> null
    TransactionStatus.Reconciled, TransactionStatus.Locked -> EntryStatus.Reconciled
    TransactionStatus.PriorPeriodAdjustment -> EntryStatus.PriorPeriod
}

private fun AuditLogEntity.toAuditEntry(zone: ZoneId, dateLabel: (LocalDate) -> String): AuditEntry {
    val at = Instant.ofEpochMilli(whenMillis).atZone(zone)
    val verb = when (action) {
        "Created" -> "Created"
        "Edited" -> "Edited"
        "StatusChanged" -> "Status changed"
        "Reversal" -> "Reversal entry"
        "Reversed" -> "Reversed"
        "PriorPeriodAdjustment" -> "Prior-period adjustment"
        else -> action
    }
    return AuditEntry("${dateLabel(at.toLocalDate())}, ${at.format(Time)}", "$verb · $summary", before, after)
}
