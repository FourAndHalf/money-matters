package com.moneymatters.app.core.budget

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymatters.app.core.close.periodContaining
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How often a budget resets (plan.html → P12). Annual follows the April–March tax year. */
enum class BudgetPeriod { Weekly, Monthly, Annual }

/**
 * A standing budget for one expense category. It applies to every period of its kind, so there is
 * nothing to copy forward at the start of a new one. Spending in the category's sub-categories counts.
 */
@Entity(tableName = "budgets", indices = [Index("accountId", unique = true)])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val amountPaise: Long,
    val period: BudgetPeriod,
)

/** An inclusive run of days. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/** The budget period that holds [date]. Monthly follows the investor's month start day (payday); weekly runs Monday to Sunday. */
fun BudgetPeriod.rangeContaining(date: LocalDate, monthStartDay: Int): DateRange = when (this) {
    BudgetPeriod.Weekly -> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DateRange(start, start.plusDays(6))
    }
    BudgetPeriod.Monthly -> periodContaining(date, monthStartDay).let { DateRange(it.start, it.end) }
    BudgetPeriod.Annual -> {
        val start = LocalDate.of(if (date.monthValue >= 4) date.year else date.year - 1, 4, 1)
        DateRange(start, start.plusYears(1).minusDays(1))
    }
}

/** [accountId] and every account beneath it in the category tree. */
fun categoryTree(accountId: String, accounts: List<AccountEntity>): Set<String> {
    val children = accounts.groupBy { it.parentId }
    val out = mutableSetOf<String>()
    fun walk(id: String) {
        if (out.add(id)) children[id].orEmpty().forEach { walk(it.id) }
    }
    walk(accountId)
    return out
}

/**
 * Net spending in a category (and its sub-categories) over [range]: debits less credits, so a refund
 * reduces it. Drafts are never counted, as everywhere else in the app.
 */
fun spentIn(
    accountId: String,
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    postings: List<PostingEntity>,
    range: DateRange,
): Paise {
    val tree = categoryTree(accountId, accounts)
    val counted = transactions.filter { it.status != TransactionStatus.Draft && LocalDate.parse(it.date) in range }.map { it.id }.toSet()
    val net = postings.filter { it.transactionId in counted && it.accountId in tree }
        .sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) }
    return Paise(net)
}

/** Days of a period needed before a straight-line projection means anything; on day one a single coffee "projects" to a huge month. */
const val MIN_DAYS_TO_PROJECT = 3

/**
 * The day the budget runs out if spending carries on at the pace so far, or null when it is already
 * over, nothing has been spent, it is too early to tell, or the budget lasts to the end of the period.
 */
fun runsOutOn(spent: Paise, cap: Paise, range: DateRange, today: LocalDate): LocalDate? {
    val elapsed = ChronoUnit.DAYS.between(range.start, today).toInt() + 1
    if (elapsed < MIN_DAYS_TO_PROJECT || spent.value <= 0 || cap.value <= 0 || spent.value >= cap.value) return null
    val daysToCap = (cap.value * elapsed + spent.value - 1) / spent.value // rounded up
    val date = range.start.plusDays(daysToCap - 1)
    return date.takeIf { !it.isAfter(range.end) }
}
