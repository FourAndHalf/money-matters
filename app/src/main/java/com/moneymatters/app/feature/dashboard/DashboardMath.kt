package com.moneymatters.app.feature.dashboard

import com.moneymatters.app.core.money.Paise
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Daily allowance = leeway ÷ days to payday, rounded down to the rupee so it never overstates
 * what is safe. Zero when leeway is used up or payday has arrived.
 */
fun dailyAllowance(leeway: Paise, daysLeft: Int): Paise {
    if (daysLeft <= 0 || leeway.value <= 0) return Paise.Zero
    val perDay = leeway.value / daysLeft
    return Paise(perDay - perDay % 100)
}

enum class BudgetPace { OnPace, Watch, OverPace }

/**
 * Compares the share of a budget used with the share of the period elapsed.
 * More than 8 points ahead of pace is worth watching; more than 25 points, or any overspend, is over.
 */
fun budgetPace(spent: Paise, cap: Paise, elapsedFraction: Float): BudgetPace {
    if (cap.value <= 0) return if (spent.value > 0) BudgetPace.OverPace else BudgetPace.OnPace
    val used = spent.value.toDouble() / cap.value
    return when {
        used > 1.0 || used > elapsedFraction + 0.25 -> BudgetPace.OverPace
        used > elapsedFraction + 0.08 -> BudgetPace.Watch
        else -> BudgetPace.OnPace
    }
}

private fun monthlyRate(annualReturn: Double) = (1 + annualReturn).pow(1.0 / 12) - 1

/** Growth of [start] plus a monthly contribution after [months], at [annualReturn] compounded monthly. */
fun projectedValue(start: Paise, monthly: Paise, months: Int, annualReturn: Double): Paise =
    Paise(floor(projectedRupees(start, monthly, months, annualReturn) * 100).toLong())

/** Same projection in rupees as a Double, for plotting only; never store or post this value. */
fun projectedRupees(start: Paise, monthly: Paise, months: Int, annualReturn: Double): Double {
    val r = monthlyRate(annualReturn)
    val s = start.value / 100.0
    val m = monthly.value / 100.0
    if (r == 0.0) return s + m * months
    val growth = (1 + r).pow(months)
    return s * growth + m * (growth - 1) / r
}

/**
 * Monthly contribution needed to reach [target] from [start] in [months]. Rounded up to the rupee
 * so following it reaches the goal. Zero if the start already gets there on growth alone.
 */
fun requiredMonthlyContribution(target: Paise, start: Paise, months: Int, annualReturn: Double): Paise {
    if (months <= 0) return Paise((target - start).value.coerceAtLeast(0))
    val r = monthlyRate(annualReturn)
    val gap = target.value / 100.0 - start.value / 100.0 * (1 + r).pow(months)
    if (gap <= 0) return Paise.Zero
    val factor = if (r == 0.0) months.toDouble() else ((1 + r).pow(months) - 1) / r
    return Paise(ceil(gap / factor).toLong() * 100)
}
