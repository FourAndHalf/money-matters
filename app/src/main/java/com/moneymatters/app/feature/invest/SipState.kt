package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.InstalmentState
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.nextSipDue
import com.moneymatters.app.core.invest.sipInstalments
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.feature.dashboard.projectedValue
import java.time.LocalDate
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
)

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
        )
    }
    val rows = details.values.map { it.row }.sortedWith(compareBy<SipRow> { it.ended }.thenBy { it.nextLabel == null }.thenBy { it.fundName })
    return rows to details
}
