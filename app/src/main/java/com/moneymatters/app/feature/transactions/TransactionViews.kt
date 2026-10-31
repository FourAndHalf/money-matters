package com.moneymatters.app.feature.transactions

import com.moneymatters.app.core.money.Paise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Money out and in over some days, with transfers between your own accounts and drafts left out (they change no one's totals). */
data class Flow(val out: Paise, val into: Paise, val count: Int) {
    val net: Paise get() = into - out

    operator fun plus(other: Flow) = Flow(out + other.out, into + other.into, count + other.count)

    companion object { val None = Flow(Paise.Zero, Paise.Zero, 0) }
}

/** One calendar week or month in the weekly and monthly views. */
data class PeriodRow(val start: LocalDate, val end: LocalDate, val flow: Flow)

private fun TransactionSummary.counts() = !isTransfer && status != TxnStatus.Draft

/** What happened on one day. [Flow.count] counts every entry, so a day of only transfers still shows as active. */
fun DayGroup.flow(): Flow {
    var out = 0L
    var into = 0L
    items.filter { it.counts() }.forEach { if (it.amount.value < 0) out += -it.amount.value else into += it.amount.value }
    return Flow(Paise(out), Paise(into), items.size)
}

/** Net money movement by day, for the calendar. Days are those the groups carry a date for. */
fun dayNets(groups: List<DayGroup>): Map<LocalDate, Paise> =
    groups.mapNotNull { g -> g.date?.let { it to g.flow().net } }.toMap()

private fun periods(groups: List<DayGroup>, key: (LocalDate) -> LocalDate, end: (LocalDate) -> LocalDate): List<PeriodRow> =
    groups.mapNotNull { g -> g.date?.let { it to g.flow() } }
        .groupBy { key(it.first) }
        .map { (start, days) -> PeriodRow(start, end(start), days.map { it.second }.fold(Flow.None, Flow::plus)) }
        .sortedByDescending { it.start }

/** Weeks, Monday to Sunday, newest first. */
fun weeklyRows(groups: List<DayGroup>): List<PeriodRow> =
    periods(groups, { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }, { it.plusDays(6) })

/** Calendar months, newest first. */
fun monthlyRows(groups: List<DayGroup>): List<PeriodRow> =
    periods(groups, { it.withDayOfMonth(1) }, { YearMonth.from(it).atEndOfMonth() })

/** Totals over every row shown, for the summary line above the weekly and monthly lists. */
fun totalFlow(rows: List<PeriodRow>): Flow = rows.fold(Flow.None) { acc, r -> acc + r.flow }
