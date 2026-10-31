package com.moneymatters.app.feature.close

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.close.BalanceLine
import com.moneymatters.app.core.budget.BudgetEntity
import com.moneymatters.app.core.close.CheckItem
import com.moneymatters.app.core.close.ChecklistExtras
import com.moneymatters.app.core.close.MonthEndReport
import com.moneymatters.app.core.close.monthEndReport
import com.moneymatters.app.core.close.Period
import com.moneymatters.app.core.close.balanceLines
import com.moneymatters.app.core.close.buildChecklist
import com.moneymatters.app.core.close.latestEndedPeriod
import com.moneymatters.app.core.close.nextPeriodToClose
import com.moneymatters.app.core.close.verifyChain
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.ledger.LedgerSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

@Immutable
data class BalanceRow(val line: BalanceLine, val confirmed: Boolean)

/** A closed month and whether its hash still checks out. */
@Immutable
data class ClosedRow(val label: String, val rangeLabel: String, val shortHash: String, val ok: Boolean, val problem: String?)

/** What the close page (P17) renders: the period up next, its live checklist, balances to confirm, and the history. */
@Immutable
data class CloseState(
    /** Null when nothing is waiting: the latest ended month is already closed. */
    val period: Period?,
    val items: List<CheckItem> = emptyList(),
    val balances: List<BalanceRow> = emptyList(),
    val canClose: Boolean = false,
    val history: List<ClosedRow> = emptyList(),
    val nextLabel: String = "",
    val report: MonthEndReport? = null,
)

fun buildClose(
    ledger: LedgerSnapshot,
    invest: InvestSnapshot,
    today: LocalDate,
    monthStartDay: Int,
    confirmed: Set<String>,
    extras: ChecklistExtras = ChecklistExtras(),
    budgets: List<BudgetEntity> = emptyList(),
): CloseState {
    val latest = latestEndedPeriod(today, monthStartDay)
    val next = nextPeriodToClose(ledger, today, monthStartDay)
    val alreadyClosed = ledger.closedPeriods.any { it.start == next.start.toString() }
    val history = verifyChain(ledger.closedPeriods, ledger).reversed().map { c ->
        val start = LocalDate.parse(c.period.start)
        val period = Period(start, LocalDate.parse(c.period.end))
        ClosedRow(period.label, "${start.format(DayMonthYear)} – ${period.end.format(DayMonthYear)}", c.period.hash.take(12), c.ok, c.problem)
    }
    if (alreadyClosed) return CloseState(null, history = history, nextLabel = "${latest.label} is closed; the next month can be closed after ${latest.end.plusMonths(1).format(DayMonthYear)}")
    val checklist = buildChecklist(ledger, invest, next, today, confirmed, extras)
    val balances = balanceLines(ledger, next).map { BalanceRow(it, it.accountId in confirmed) }
    return CloseState(next, checklist.items, balances, checklist.canClose, history, report = monthEndReport(ledger, next, monthStartDay, budgets))
}
