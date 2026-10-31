package com.moneymatters.app.core.loan

import com.moneymatters.app.core.money.Paise
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Longest schedule worked out, so an EMI that barely covers interest can't produce an endless list. */
const val MAX_INSTALMENTS = 600

/**
 * One month's interest on [outstanding] at [rateBps] a year (1 bp = 0.01%), at one twelfth of the annual rate,
 * rounded half up to the paisa the way banks post it. Integer maths throughout.
 */
fun monthlyInterest(outstanding: Paise, rateBps: Int): Paise {
    if (outstanding.value <= 0 || rateBps <= 0) return Paise.Zero
    val numerator = outstanding.value * rateBps
    val denominator = 120_000L // 10,000 bps per unit, 12 months
    return Paise((numerator * 2 + denominator) / (2 * denominator))
}

/** How one EMI divides. [paid] is what leaves the bank: [principal] plus [interest]. */
data class EmiSplit(val principal: Paise, val interest: Paise) {
    val paid: Paise get() = principal + interest
}

/**
 * Splits the next EMI of [emi] given what is still [outstanding]. The last instalment is whatever clears the
 * balance, so it can be smaller than the EMI. An EMI that doesn't cover the month's interest repays nothing.
 */
fun splitEmi(outstanding: Paise, emi: Paise, rateBps: Int): EmiSplit {
    val interest = monthlyInterest(outstanding, rateBps)
    if (outstanding.value <= 0) return EmiSplit(Paise.Zero, Paise.Zero)
    if (outstanding.value + interest.value <= emi.value) return EmiSplit(outstanding, interest)
    return EmiSplit(Paise(maxOf(0L, emi.value - interest.value)), interest)
}

/** True when the EMI repays some principal in the first month, which a loan needs in order to ever end. */
fun emiCoversInterest(outstanding: Paise, emi: Paise, rateBps: Int): Boolean =
    emi.value > monthlyInterest(outstanding, rateBps).value

data class Instalment(val number: Int, val due: LocalDate, val principal: Paise, val interest: Paise, val balanceAfter: Paise) {
    val paid: Paise get() = principal + interest
}

/** The monthly due date of instalment [n] (counting from 0 at [first]); short months clamp, and later dates don't drift. */
fun dueDate(first: LocalDate, n: Int): LocalDate = first.plusMonths(n.toLong())

/**
 * The instalments still to pay, in order, from [outstanding] with the first falling on [firstDue]. Ends when the
 * balance reaches zero, or after [MAX_INSTALMENTS] if the EMI is too small to ever clear it.
 */
fun schedule(outstanding: Paise, emi: Paise, rateBps: Int, firstDue: LocalDate, limit: Int = MAX_INSTALMENTS): List<Instalment> {
    val out = mutableListOf<Instalment>()
    var balance = outstanding
    var n = 0
    while (balance.value > 0 && n < limit) {
        val split = splitEmi(balance, emi, rateBps)
        if (split.principal.value == 0L) break // the EMI never touches the balance
        balance -= split.principal
        out += Instalment(n + 1, dueDate(firstDue, n), split.principal, split.interest, balance)
        n++
    }
    return out
}

/** The EMI that clears [principal] in [months] at [rateBps] a year, rounded up to the rupee so it never falls short. */
fun suggestEmi(principal: Paise, rateBps: Int, months: Int): Paise {
    require(months > 0) { "A loan needs at least one month" }
    if (principal.value <= 0) return Paise.Zero
    if (rateBps <= 0) return roundUpToRupee(BigDecimal(principal.value).divide(BigDecimal(months), MathContext.DECIMAL128))
    val r = BigDecimal(rateBps).divide(BigDecimal(120_000), MathContext.DECIMAL128)
    val growth = BigDecimal.ONE.add(r).pow(months, MathContext.DECIMAL128)
    val emi = BigDecimal(principal.value).multiply(r).multiply(growth).divide(growth.subtract(BigDecimal.ONE), MathContext.DECIMAL128)
    return roundUpToRupee(emi)
}

private fun roundUpToRupee(paise: BigDecimal): Paise {
    val rupees = paise.divide(BigDecimal(100), 0, RoundingMode.CEILING)
    return Paise(rupees.toLong() * 100)
}

/** What a loan costs from here: interest still to pay, and the date of the last instalment. */
data class Payoff(val instalments: Int, val interestRemaining: Paise, val lastDue: LocalDate?)

fun payoff(schedule: List<Instalment>): Payoff =
    Payoff(schedule.size, Paise(schedule.sumOf { it.interest.value }), schedule.lastOrNull()?.due)
