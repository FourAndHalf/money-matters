package com.moneymatters.app.core.close

import com.moneymatters.app.core.invest.HoldingEntity
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.STALE_DAYS
import com.moneymatters.app.core.invest.depositValuation
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.ClosedPeriodEntity
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.SUSPENSE_ACCOUNT
import com.moneymatters.app.core.loan.LoanEntity
import com.moneymatters.app.core.loan.LoanPaymentEntity
import com.moneymatters.app.core.loan.livePayments
import com.moneymatters.app.core.loan.nextDue
import com.moneymatters.app.core.loan.owedOn
import com.moneymatters.app.core.recurring.RecurringRuleEntity
import com.moneymatters.app.core.recurring.dueDates
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One month of the investor's books, from the day their month starts to the day before the next one starts. */
data class Period(val start: LocalDate, val end: LocalDate) {
    /** The name the investor types to confirm a close, e.g. `October 2026`. */
    val label: String get() = "${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${start.year}"

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

fun periodStartOn(date: LocalDate, monthStartDay: Int): LocalDate {
    val thisMonth = date.withDayOfMonth(monthStartDay)
    return if (thisMonth.isAfter(date)) thisMonth.minusMonths(1) else thisMonth
}

fun periodContaining(date: LocalDate, monthStartDay: Int): Period {
    val start = periodStartOn(date, monthStartDay)
    return Period(start, start.plusMonths(1).minusDays(1))
}

/** The most recent period that has fully ended, which is the one to close next. */
fun latestEndedPeriod(today: LocalDate, monthStartDay: Int): Period =
    periodContaining(periodStartOn(today, monthStartDay).minusDays(1), monthStartDay)

/**
 * The month to close next: the one holding the oldest entry that is still open, so a backlog is closed oldest first
 * (months must close in order). With nothing older open, it is the latest month that has ended.
 */
fun nextPeriodToClose(ledger: LedgerSnapshot, today: LocalDate, monthStartDay: Int): Period {
    val latest = latestEndedPeriod(today, monthStartDay)
    val oldestOpen = ledger.transactions
        .filter { it.status in listOf(TransactionStatus.Draft, TransactionStatus.Posted, TransactionStatus.Reconciled) }
        .minOfOrNull { it.date }
        ?.let(LocalDate::parse) ?: return latest
    val period = periodContaining(oldestOpen, monthStartDay)
    return if (period.start.isBefore(latest.start)) period else latest
}

/** What to do about a failing checklist item; the screen turns each into a button or a link. */
enum class CheckFix { None, Prices, Interest, Drafts, Confirm, Reconcile, Recurring, Units }

data class CheckItem(val title: String, val ok: Boolean, val detail: String, val fix: CheckFix = CheckFix.None)

data class Checklist(val items: List<CheckItem>) {
    val canClose: Boolean get() = items.all { it.ok }
}

/**
 * What the newer checklist items need beyond the ledger and holdings. Every field defaults to "nothing there", so a book
 * with no recurring rules, loans or pending orders passes those items, and [unitsConfirmed] is true unless the screen says otherwise.
 */
data class ChecklistExtras(
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val loanPayments: List<LoanPaymentEntity> = emptyList(),
    val unitsConfirmed: Boolean = true,
)

/** How long an order may wait In Transit at the end of a month before it counts as stale. */
const val STALE_IN_TRANSIT_DAYS = 7L

/** A real-world account the investor confirms against their statement before the month closes. */
data class BalanceLine(val accountId: String, val name: String, val balance: Paise)

private fun bookedIn(ledger: LedgerSnapshot, period: Period) =
    ledger.transactions.filter { it.status != TransactionStatus.Draft && it.status != TransactionStatus.PriorPeriodAdjustment && LocalDate.parse(it.date) in period }

/** Asset and liability accounts that moved during the period, with their booked balance at its end. */
fun balanceLines(ledger: LedgerSnapshot, period: Period): List<BalanceLine> {
    val datesById = ledger.transactions.filter { it.status != TransactionStatus.Draft }.associate { it.id to LocalDate.parse(it.date) }
    val activeIds = bookedIn(ledger, period).map { it.id }.toSet()
    val touched = ledger.postings.filter { it.transactionId in activeIds }.map { it.accountId }.toSet()
    return ledger.accounts.filter { it.id in touched && (it.type == AccountType.Asset || it.type == AccountType.Liability) }.sortedBy { it.name }.map { account ->
        val balance = ledger.postings.filter { it.accountId == account.id && (datesById[it.transactionId]?.let { d -> !d.isAfter(period.end) } == true) }
            .sumOf { (it.debitPaise ?: 0) - (it.creditPaise ?: 0) }
        BalanceLine(account.id, account.name, Paise(balance))
    }
}

private fun unitsAt(holding: HoldingEntity, invest: InvestSnapshot, saleDates: Map<String, LocalDate>, end: LocalDate): Long {
    val lots = invest.lots.filter { it.holdingId == holding.id && !LocalDate.parse(it.acquiredOn).isAfter(end) }
    val lotIds = lots.map { it.id }.toSet()
    val sold = invest.consumptions.filter { it.lotId in lotIds && saleDates[it.saleTransactionId]?.isAfter(end) == false }.sumOf { it.units }
    return lots.sumOf { it.units } - sold
}

/** Open deposits whose interest to the end of the period isn't in the ledger yet, with the amount owed. */
fun owingDeposits(ledger: LedgerSnapshot, invest: InvestSnapshot, period: Period): List<Pair<String, Paise>> {
    val datesById = ledger.transactions.filter { it.status != TransactionStatus.Draft }.associate { it.id to LocalDate.parse(it.date) }
    return invest.fixedDeposits.filter { fd ->
        !LocalDate.parse(fd.startDate).isAfter(period.end) && (fd.closedOn == null || LocalDate.parse(fd.closedOn).isAfter(period.end))
    }.mapNotNull { fd ->
        val balance = ledger.postings.filter { it.accountId == fd.accountId && datesById[it.transactionId]?.isAfter(period.end) == false }.sumOf { (it.debitPaise ?: 0) - (it.creditPaise ?: 0) }
        depositValuation(fd.copy(closedOn = null), Paise(balance), period.end).let { v -> (v.accruedInterest - v.postedInterest).takeIf { it.value > 0 }?.let { fd.accountId to it } }
    }
}

/** Holdings that were held at the end of the period, each with the newest price on or before it. */
private fun heldWithPrice(ledger: LedgerSnapshot, invest: InvestSnapshot, end: LocalDate): List<Triple<HoldingEntity, Long, com.moneymatters.app.core.invest.PriceEntity?>> {
    val saleDates = ledger.transactions.associate { it.id to LocalDate.parse(it.date) }
    return invest.holdings.mapNotNull { h ->
        val units = unitsAt(h, invest, saleDates, end)
        if (units <= 0) return@mapNotNull null
        val price = invest.prices.filter { it.holdingId == h.id && !LocalDate.parse(it.date).isAfter(end) }.maxWithOrNull(compareBy({ it.date }, { it.id }))
        Triple(h, units, price)
    }
}

private fun isUsable(price: com.moneymatters.app.core.invest.PriceEntity?, end: LocalDate) =
    price != null && (price.status == PriceStatus.Verified || price.status == PriceStatus.Manual) && !LocalDate.parse(price.date).isBefore(end.minusDays(STALE_DAYS))

/**
 * The close checklist for [period], live from the same snapshots as the rest of the app (plan.html → P17).
 * [confirmed] holds the accounts whose balance the investor has confirmed in this session.
 */
fun buildChecklist(ledger: LedgerSnapshot, invest: InvestSnapshot, period: Period, today: LocalDate, confirmed: Set<String>, extras: ChecklistExtras = ChecklistExtras()): Checklist {
    val items = mutableListOf<CheckItem>()
    items += CheckItem("Month has ended", today.isAfter(period.end), if (today.isAfter(period.end)) "${period.label} is over" else "Closes after ${period.end}")

    val open = ledger.transactions.filter { LocalDate.parse(it.date).isBefore(period.start) && it.status in listOf(TransactionStatus.Draft, TransactionStatus.Posted, TransactionStatus.Reconciled) }
    items += CheckItem("Earlier months are closed", open.isEmpty(), if (open.isEmpty()) "Nothing open before ${period.start}" else "${open.size} entries before ${period.start} are still open")

    val drafts = ledger.transactions.count { it.status == TransactionStatus.Draft && LocalDate.parse(it.date) in period }
    items += CheckItem("No drafts", drafts == 0, if (drafts == 0) "Every entry is booked" else "$drafts drafts to post or delete", if (drafts == 0) CheckFix.None else CheckFix.Drafts)

    val suspense = ledger.accounts.filter { it.name == SUSPENSE_ACCOUNT }.map { it.id }.toSet()
    val inSuspense = bookedIn(ledger, period).count { t -> ledger.postings.any { it.transactionId == t.id && it.accountId in suspense } }
    items += CheckItem(
        "Nothing in suspense", inSuspense == 0,
        if (inSuspense == 0) "Every entry has a category" else "$inSuspense ${if (inSuspense == 1) "entry is" else "entries are"} uncategorized: give them a category in Transactions",
    )

    val recurringDue = extras.recurringRules.filter { it.isActive }.sumOf { it.dueDates(minOf(period.end, today)).size }
    items += CheckItem(
        "Recurring entries booked", recurringDue == 0,
        if (recurringDue == 0) "Everything due this month is in the books" else "$recurringDue recurring ${if (recurringDue == 1) "entry is" else "entries are"} due and not booked yet",
        if (recurringDue == 0) CheckFix.None else CheckFix.Recurring,
    )

    val liveByLoan = extras.loanPayments.let { all -> livePayments(all, ledger.transactions.map { it.id }.toSet()).groupBy { it.loanAccountId } }
    val unpaidLoans = extras.loans.filter { owedOn(ledger, it.accountId).value > 0 && nextDue(it, liveByLoan[it.accountId].orEmpty()) <= period.end }
    items += CheckItem(
        "Loan EMIs paid", unpaidLoans.isEmpty(),
        if (unpaidLoans.isEmpty()) "No EMI is waiting" else "${unpaidLoans.size} ${if (unpaidLoans.size == 1) "loan has an EMI" else "loans have EMIs"} due: pay in More > Loans",
    )

    val staleOrders = invest.pending.filter { it.isOpen && !LocalDate.parse(it.orderDate).isAfter(period.end.minusDays(STALE_IN_TRANSIT_DAYS)) }
    items += CheckItem(
        "In Transit is clear", staleOrders.isEmpty(),
        if (staleOrders.isEmpty()) "No order is waiting too long for its units" else "${staleOrders.size} ${if (staleOrders.size == 1) "order has" else "orders have"} waited over $STALE_IN_TRANSIT_DAYS days: allot or cancel on the Invest tab",
    )

    val held = heldWithPrice(ledger, invest, period.end)
    if (held.isNotEmpty()) items += CheckItem(
        "Holding units checked", extras.unitsConfirmed,
        if (extras.unitsConfirmed) "Units checked against your broker or CAS statement" else "Check ${held.size} ${if (held.size == 1) "holding's" else "holdings'"} units against your broker or CAS statement",
        if (extras.unitsConfirmed) CheckFix.None else CheckFix.Units,
    )
    val unpriced = held.filter { !isUsable(it.third, period.end) }
    items += CheckItem(
        "Holdings priced", unpriced.isEmpty(),
        when {
            held.isEmpty() -> "No holdings"
            unpriced.isEmpty() -> "${held.size} ${if (held.size == 1) "holding has" else "holdings have"} a verified or confirmed price from the last ${STALE_DAYS} days of the month"
            else -> "${unpriced.size} of ${held.size} need a verified or confirmed price"
        },
        if (unpriced.isEmpty()) CheckFix.None else CheckFix.Prices,
    )

    val owing = owingDeposits(ledger, invest, period)
    items += CheckItem(
        "FD interest posted", owing.isEmpty(),
        if (owing.isEmpty()) "Interest is booked to the end of the month" else "${owing.size} ${if (owing.size == 1) "deposit owes" else "deposits owe"} ${Paise(owing.sumOf { it.second.value }).formatInr(showPaise = true)} of interest",
        if (owing.isEmpty()) CheckFix.None else CheckFix.Interest,
    )

    val lines = balanceLines(ledger, period)
    val unconfirmed = lines.count { it.accountId !in confirmed }
    items += CheckItem("Balances confirmed", unconfirmed == 0, "${lines.size - unconfirmed} of ${lines.size} account balances confirmed", if (unconfirmed == 0) CheckFix.None else CheckFix.Confirm)

    val toReconcile = bookedIn(ledger, period).count { it.status == TransactionStatus.Posted }
    items += CheckItem("Entries reconciled", toReconcile == 0, if (toReconcile == 0) "Every entry is reconciled" else "$toReconcile entries to reconcile", if (toReconcile == 0) CheckFix.None else CheckFix.Reconcile)
    return Checklist(items)
}

/** The prices the close rests on, one line per holding held at the end of the period. Part of what the hash covers. */
fun priceSnapshot(ledger: LedgerSnapshot, invest: InvestSnapshot, period: Period): String =
    heldWithPrice(ledger, invest, period.end).sortedBy { it.first.id }.joinToString("\n") { (h, units, price) ->
        "${h.id}|$units|${price?.date}|${price?.price}|${price?.status}"
    }

const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

/** Everything in the period's booked entries in a fixed order, with Reconciled and Locked treated alike so the text is the same before and after locking. */
internal fun canonicalText(ledger: LedgerSnapshot, period: Period): String {
    val postingsByTxn = ledger.postings.groupBy { it.transactionId }
    return ledger.transactions
        .filter { (it.status == TransactionStatus.Reconciled || it.status == TransactionStatus.Locked) && LocalDate.parse(it.date) in period }
        .sortedWith(compareBy({ it.date }, { it.id }))
        .joinToString("\n") { t ->
            val legs = postingsByTxn[t.id].orEmpty().sortedBy { it.id }.joinToString(",") { "${it.accountId}:${it.debitPaise ?: 0}:${it.creditPaise ?: 0}:${it.units ?: 0}:${it.unitPrice ?: 0}" }
            "${t.id}|${t.date}|${t.payee}|${t.memo.orEmpty()}|${t.decision}|${t.plan}|${t.paymentMethod}|$legs"
        }
}

fun chainHash(prevHash: String, period: Period, canonical: String, snapshot: String): String {
    val input = "$prevHash\n${period.start}..${period.end}\n$canonical\n--\n$snapshot"
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class ChainCheck(val period: ClosedPeriodEntity, val ok: Boolean, val problem: String? = null)

/** Re-derives every closed period's hash and its link to the one before; a changed entry or a gap in the chain shows as a failure. */
fun verifyChain(closed: List<ClosedPeriodEntity>, ledger: LedgerSnapshot): List<ChainCheck> {
    var prev = GENESIS_HASH
    return closed.sortedBy { it.start }.map { c ->
        val period = Period(LocalDate.parse(c.start), LocalDate.parse(c.end))
        val problem = when {
            c.prevHash != prev -> "The chain is broken before this month"
            chainHash(c.prevHash, period, canonicalText(ledger, period), c.snapshot) != c.hash -> "Entries in this month have changed since it was closed"
            else -> null
        }
        prev = c.hash
        ChainCheck(c, problem == null, problem)
    }
}
