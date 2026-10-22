package com.moneymatters.app.core.invest

import java.time.LocalDate
import java.time.YearMonth

/** Where one SIP instalment stands. [Missed] is due and more than the grace period overdue, or deliberately skipped. */
enum class InstalmentState { Paid, Skipped, Due, Missed }

data class Instalment(val due: LocalDate, val state: InstalmentState)

/** Days after its due date an unrecorded instalment is still just [InstalmentState.Due] rather than [InstalmentState.Missed]. */
const val SIP_GRACE_DAYS = 3

/**
 * The instalment dates of a plan from [start] up to and including [through]: one a month on
 * [dayOfMonth] (a 31st becomes the month's last day), the first being the first such date on or after [start].
 */
fun sipDueDates(start: LocalDate, dayOfMonth: Int, through: LocalDate): List<LocalDate> {
    require(dayOfMonth in 1..31) { "Day of month must be 1 to 31" }
    val dates = mutableListOf<LocalDate>()
    var month = YearMonth.from(start)
    while (true) {
        val date = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
        if (date.isAfter(through)) break
        if (!date.isBefore(start)) dates += date
        month = month.plusMonths(1)
    }
    return dates
}

/**
 * Every instalment of [plan] that has fallen due by [today] (and by its end date), each with its state.
 * An instalment is [InstalmentState.Paid] when an order for that date exists and was not cancelled.
 */
fun sipInstalments(
    plan: SipPlanEntity,
    orders: List<PendingAllotmentEntity>,
    skips: List<SipSkipEntity>,
    today: LocalDate,
): List<Instalment> {
    val through = plan.endDate?.let(LocalDate::parse)?.let { if (it.isBefore(today)) it else today } ?: today
    val paid = orders.filter { it.sipPlanId == plan.id && it.cancelTransactionId == null }.map { it.orderDate }.toSet()
    val skipped = skips.filter { it.planId == plan.id }.map { it.dueDate }.toSet()
    return sipDueDates(LocalDate.parse(plan.startDate), plan.dayOfMonth, through).map { due ->
        val state = when {
            due.toString() in paid -> InstalmentState.Paid
            due.toString() in skipped -> InstalmentState.Skipped
            due.plusDays(SIP_GRACE_DAYS.toLong()).isBefore(today) -> InstalmentState.Missed
            else -> InstalmentState.Due
        }
        Instalment(due, state)
    }
}

/** The next instalment after [today], or null once the plan has ended. */
fun nextSipDue(plan: SipPlanEntity, today: LocalDate): LocalDate? {
    val end = plan.endDate?.let(LocalDate::parse)
    val horizon = today.plusMonths(2)
    val next = sipDueDates(LocalDate.parse(plan.startDate), plan.dayOfMonth, horizon).firstOrNull { it.isAfter(today) }
    return next?.takeIf { end == null || !it.isAfter(end) }
}
