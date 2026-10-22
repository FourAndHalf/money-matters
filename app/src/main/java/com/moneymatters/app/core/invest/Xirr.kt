package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/** One dated cash flow as seen from the investor: money put in is negative, money (or value) received is positive. */
data class CashFlow(val date: LocalDate, val amount: Paise)

private const val MAX_ITERATIONS = 200
private const val TOLERANCE = 1e-9

/**
 * Annualised return (XIRR) of [flows], on an actual/365 basis. Display-only: it is a float and is
 * never stored or posted. Null when no return exists, i.e. the flows don't include both an outflow
 * and an inflow. Solved by bisection, which can't diverge the way Newton's method can on odd flows.
 */
fun xirr(flows: List<CashFlow>): Double? {
    if (flows.none { it.amount.value < 0 } || flows.none { it.amount.value > 0 }) return null
    val first = flows.minOf { it.date }
    val points = flows.map { ChronoUnit.DAYS.between(first, it.date) / 365.0 to it.amount.value / 100.0 }
    fun npv(rate: Double) = points.sumOf { (years, amount) -> amount / (1 + rate).pow(years) }

    var low = -0.999999
    var high = 10.0
    if (npv(low).let { it.isNaN() || it.isInfinite() }) return null
    if (npv(low) * npv(high) > 0) return null
    repeat(MAX_ITERATIONS) {
        val mid = (low + high) / 2
        val value = npv(mid)
        if (abs(value) < TOLERANCE || (high - low) < TOLERANCE) return mid
        if (npv(low) * value <= 0) high = mid else low = mid
    }
    return (low + high) / 2
}
