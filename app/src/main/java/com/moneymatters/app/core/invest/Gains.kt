package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import java.time.LocalDate

enum class GainTerm { Short, Long }

/**
 * How long a holding must be kept before a gain counts as long-term. The number of months is tax
 * data (versioned per tax year, and different for listed equity and other assets), so callers pass it in.
 */
data class HoldingPeriodRule(val longTermAfterMonths: Int)

/** Long-term only once held for *more than* the rule's months; exactly on the anniversary is still short-term. */
fun gainTerm(acquiredOn: LocalDate, soldOn: LocalDate, rule: HoldingPeriodRule): GainTerm =
    if (soldOn.isAfter(acquiredOn.plusMonths(rule.longTermAfterMonths.toLong()))) GainTerm.Long else GainTerm.Short

data class GainBucket(val term: GainTerm, val gain: Paise)

/** A consumed lot with the date it was bought, which sets the term of its share of the gain. */
data class DatedConsumption(val consumption: Consumption, val acquiredOn: LocalDate)

/**
 * Splits a sale's gain (proceeds − cost) into short- and long-term buckets. Proceeds are shared
 * across the consumed lots by units, the last lot taking the rounding remainder, so the buckets always
 * add up to exactly proceeds − total cost. Buckets with a zero gain are omitted.
 */
fun splitGain(proceeds: Paise, consumed: List<DatedConsumption>, soldOn: LocalDate, rule: HoldingPeriodRule): List<GainBucket> {
    require(consumed.isNotEmpty()) { "A sale consumes at least one lot" }
    val totalUnits = consumed.fold(Units.Zero) { acc, c -> acc + c.consumption.units }
    var allocated = Paise.Zero
    val gains = HashMap<GainTerm, Long>()
    consumed.forEachIndexed { index, dated ->
        val share = if (index == consumed.lastIndex) proceeds - allocated else proRata(proceeds, dated.consumption.units, totalUnits)
        allocated += share
        val term = gainTerm(dated.acquiredOn, soldOn, rule)
        gains[term] = (gains[term] ?: 0) + (share - dated.consumption.cost).value
    }
    return GainTerm.entries.mapNotNull { term -> gains[term]?.takeIf { it != 0L }?.let { GainBucket(term, Paise(it)) } }
}
