package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How an FD's interest compounds; [Simple] pays interest on the principal only, once, at maturity. */
enum class Compounding(val monthsPerPeriod: Int) { Monthly(1), Quarterly(3), Simple(0) }

// Every division here passes MC: Kotlin's BigDecimal `/` keeps the dividend's scale and silently truncates.
private val MC = MathContext(30)
private val BASIS_POINTS = BigDecimal(10_000)
private val DAYS_IN_YEAR = BigDecimal(365)

/**
 * Interest earned from [start] to [asOf], capped at [maturity]. Whole compounding periods compound
 * at rate/periods-per-year; the part-period after the last one earns simple interest on the
 * compounded balance, by actual days / 365. Rounded half-up to paise once, at the end.
 */
fun accruedInterest(
    principal: Paise,
    rateBps: Int,
    compounding: Compounding,
    start: LocalDate,
    asOf: LocalDate,
    maturity: LocalDate,
): Paise {
    val end = if (asOf.isAfter(maturity)) maturity else asOf
    if (!end.isAfter(start)) return Paise.Zero
    val p = BigDecimal(principal.value)
    val rate = BigDecimal(rateBps).divide(BASIS_POINTS, MC)
    val interest = if (compounding == Compounding.Simple) {
        p * rate * BigDecimal(ChronoUnit.DAYS.between(start, end)).divide(DAYS_IN_YEAR, MC)
    } else {
        val months = compounding.monthsPerPeriod
        val periods = (ChronoUnit.MONTHS.between(start, end) / months).toInt()
        val periodRate = rate * BigDecimal(months).divide(BigDecimal(12), MC)
        val balance = p * (BigDecimal.ONE + periodRate).pow(periods, MC)
        val stubDays = ChronoUnit.DAYS.between(start.plusMonths((periods * months).toLong()), end)
        balance * (BigDecimal.ONE + rate * BigDecimal(stubDays).divide(DAYS_IN_YEAR, MC)) - p
    }
    return Paise(interest.setScale(0, RoundingMode.HALF_UP).toLong())
}

fun maturityValue(principal: Paise, rateBps: Int, compounding: Compounding, start: LocalDate, maturity: LocalDate) =
    principal + accruedInterest(principal, rateBps, compounding, start, maturity, maturity)

/** What breaking an FD early pays out: the proceeds, and how much the penalty cost versus holding to the same day. */
data class BreakValue(val proceeds: Paise, val penalty: Paise)

/**
 * Premature-break value on [breakOn]: interest is recomputed at the contract rate less [penaltyBps],
 * for the days actually held. Within [penaltyFreeDays] of maturity there is no penalty.
 */
fun breakValue(
    principal: Paise,
    rateBps: Int,
    compounding: Compounding,
    start: LocalDate,
    maturity: LocalDate,
    breakOn: LocalDate,
    penaltyBps: Int,
    penaltyFreeDays: Int = 30,
): BreakValue {
    val full = principal + accruedInterest(principal, rateBps, compounding, start, breakOn, maturity)
    val penaltyApplies = penaltyBps > 0 && breakOn.isBefore(maturity.minusDays(penaltyFreeDays.toLong()))
    if (!penaltyApplies) return BreakValue(full, Paise.Zero)
    val reduced = principal + accruedInterest(principal, (rateBps - penaltyBps).coerceAtLeast(0), compounding, start, breakOn, maturity)
    return BreakValue(reduced, full - reduced)
}
