package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.InstalmentState
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.SipPlanEntity
import com.moneymatters.app.core.invest.nextSipDue
import com.moneymatters.app.core.invest.sipDueDates
import com.moneymatters.app.core.invest.sipInstalments
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.feature.dashboard.projectedValue
import com.moneymatters.app.ui.components.CalendarMark
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

/** The wealth goal a SIP serves: the date it must be reached by, and the return assumed on the way there. */
data class SipGoal(val date: LocalDate, val annualReturn: Double)

/** A SIP in the portfolio list. [dueCount] instalments need confirming; [missedCount] were skipped or are overdue. */
@Immutable
data class SipRow(
    val planId: String,
    val fundName: String,
    val amount: Paise,
    val scheduleLabel: String,
    val nextLabel: String?,
    val dueCount: Int,
    val missedCount: Int,
    val ended: Boolean,
)

@Immutable
data class DueRow(val due: LocalDate, val dateLabel: String, val state: InstalmentState)

@Immutable
data class SipDetail(
    val row: SipRow,
    val fromAccountName: String,
    val dues: List<DueRow>,
    val paidCount: Int,
    /** What the missed instalments would have grown to by the goal date; null with no goal. */
    val missedImpact: Paise?,
    val goalLabel: String?,
    val calendar: SipCalendar? = null,
)

/** Instalment dates by state, spanning [first] to [last] month; what the calendar draws. */
@Immutable
data class SipCalendar(val first: YearMonth, val last: YearMonth, val today: LocalDate, val marks: Map<LocalDate, CalendarMark>)

/** How far ahead the calendar shows upcoming instalments of a SIP that has no end date. */
private const val CALENDAR_MONTHS_AHEAD = 12L

private fun CalendarMark.rank() = when (this) {
    CalendarMark.Missed -> 4
    CalendarMark.Due -> 3
    CalendarMark.Upcoming -> 2
    CalendarMark.Skipped -> 1
    CalendarMark.Done -> 0
}

private fun InstalmentState.mark() = when (this) {
    InstalmentState.Paid -> CalendarMark.Done
    InstalmentState.Skipped -> CalendarMark.Skipped
    InstalmentState.Due -> CalendarMark.Due
    InstalmentState.Missed -> CalendarMark.Missed
}

/**
 * Every instalment of [plans], past ones by their state and future ones (to the end date, or a year ahead) as upcoming.
 * Where plans share a day the one needing the most attention is shown. Null when there are none.
 */
fun sipCalendar(plans: List<SipPlanEntity>, invest: InvestSnapshot, today: LocalDate): SipCalendar? {
    val marks = HashMap<LocalDate, CalendarMark>()
    for (plan in plans) {
        val horizon = today.plusMonths(CALENDAR_MONTHS_AHEAD)
        val through = plan.endDate?.let(LocalDate::parse)?.let { if (it.isBefore(horizon)) it else horizon } ?: horizon
        val past = sipInstalments(plan, invest.pending, invest.sipSkips, today).associate { it.due to it.state.mark() }
        val upcoming = sipDueDates(LocalDate.parse(plan.startDate), plan.dayOfMonth, through).filter { it.isAfter(today) }.associateWith { CalendarMark.Upcoming }
        for ((date, mark) in past + upcoming) marks.merge(date, mark) { a, b -> if (a.rank() >= b.rank()) a else b }
    }
    if (marks.isEmpty()) return null
    return SipCalendar(YearMonth.from(marks.keys.min()), YearMonth.from(marks.keys.max()), today, marks)
}

internal fun ordinal(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$day$suffix"
}

/** What [missed] instalments of [amount] would each have grown to by [goal]'s date, added up. */
fun missedImpact(amount: Paise, missed: List<LocalDate>, goal: SipGoal): Paise =
    Paise(
        missed.sumOf { due ->
            val months = ChronoUnit.MONTHS.between(due, goal.date).toInt()
            if (months <= 0) 0L else projectedValue(amount, Paise.Zero, months, goal.annualReturn).value
        },
    )

internal fun buildSips(ledger: LedgerSnapshot, invest: InvestSnapshot, today: LocalDate, goal: SipGoal?, fundNames: Map<String, String>): Pair<List<SipRow>, Map<String, SipDetail>> {
    val accountNames = ledger.accounts.associate { it.id to it.name }
    val details = invest.sipPlans.associate { plan ->
        val instalments = sipInstalments(plan, invest.pending, invest.sipSkips, today)
        val outstanding = instalments.filter { it.state != InstalmentState.Paid }
        val missed = outstanding.filter { it.state == InstalmentState.Missed || it.state == InstalmentState.Skipped }
        val amount = Paise(plan.amountPaise)
        val ended = plan.endDate?.let { !LocalDate.parse(it).isAfter(today) } == true
        val next = nextSipDue(plan, today)
        val row = SipRow(
            planId = plan.id,
            fundName = fundNames[plan.holdingId].orEmpty(),
            amount = amount,
            scheduleLabel = "on the ${ordinal(plan.dayOfMonth)} of every month",
            nextLabel = next?.format(DayMonthYear),
            dueCount = outstanding.count { it.state == InstalmentState.Due || it.state == InstalmentState.Missed },
            missedCount = missed.size,
            ended = ended,
        )
        val impact = goal?.takeIf { it.date.isAfter(today) && missed.isNotEmpty() }?.let { missedImpact(amount, missed.map { m -> m.due }, it) }
        plan.id to SipDetail(
            row = row,
            fromAccountName = accountNames[plan.fromAccountId].orEmpty(),
            dues = outstanding.sortedByDescending { it.due }.map { DueRow(it.due, it.due.format(DayMonthYear), it.state) },
            paidCount = instalments.count { it.state == InstalmentState.Paid },
            missedImpact = impact,
            goalLabel = goal?.date?.format(DayMonthYear),
            calendar = sipCalendar(listOf(plan), invest, today),
        )
    }
    val rows = details.values.map { it.row }.sortedWith(compareBy<SipRow> { it.ended }.thenBy { it.nextLabel == null }.thenBy { it.fundName })
    return rows to details
}
