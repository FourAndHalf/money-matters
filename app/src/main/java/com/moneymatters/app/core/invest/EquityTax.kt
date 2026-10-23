package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import java.time.LocalDate

/** How many later financial years a capital loss can be carried forward. */
const val LOSS_CARRY_FORWARD_YEARS = 8

/**
 * One financial year of listed-equity gains after setting losses off, with the exemption and an estimate of the tax.
 * Only equity-oriented sales are netted here; other assets are reported beside it and taxed at slab rate.
 */
data class EquityYear(
    val year: Int,
    val shortNet: Paise,
    val longNet: Paise,
    val exemptionUsed: Paise,
    val exemptionLimit: Paise,
    val estimatedTax: Paise,
    /** Losses still to carry into later years: short-term losses can offset either kind of gain, long-term losses only long-term gains. */
    val shortLossForward: Paise,
    val longLossForward: Paise,
)

private data class Loss(val year: Int, val amount: Long)

/**
 * Works through each year in order: this year's short and long results are netted, then losses brought
 * forward from the last [LOSS_CARRY_FORWARD_YEARS] years are used (short-term losses against short then long gains, long-term
 * losses against long gains), the exemption is taken off the long-term gain, and the flat rates apply. Rates and exemption
 * are those in force on the year's last day. A loss not used is carried on, and expires after the limit.
 */
fun equityYears(sales: List<RealisedSale>, rules: CapitalGainsRules): List<EquityYear> {
    val equity = sales.filter { it.equity }
    if (equity.isEmpty()) return emptyList()
    var shortLosses = listOf<Loss>()
    var longLosses = listOf<Loss>()
    return equity.map { it.financialYear }.distinct().sorted().map { year ->
        val inYear = equity.filter { it.financialYear == year }
        fun total(term: GainTerm) = inYear.sumOf { s -> s.buckets.filter { it.term == term }.sumOf { it.gain.value } }
        var short = total(GainTerm.Short)
        var long = total(GainTerm.Long)
        shortLosses = shortLosses.filter { year - it.year <= LOSS_CARRY_FORWARD_YEARS }
        longLosses = longLosses.filter { year - it.year <= LOSS_CARRY_FORWARD_YEARS }

        // A loss in the year itself offsets the other kind of gain first.
        if (short < 0 && long > 0) { val used = minOf(-short, long); short += used; long -= used }
        if (long < 0 && short > 0) { val used = minOf(-long, short); long += used; short -= used }

        // Then older losses, oldest first.
        fun useOn(losses: List<Loss>, gain: Long): Pair<List<Loss>, Long> {
            var left = gain
            val rest = losses.sortedBy { it.year }.map { l ->
                val take = if (left > 0) minOf(l.amount, left) else 0L
                left -= take
                Loss(l.year, l.amount - take)
            }
            return rest.filter { it.amount > 0 } to left
        }
        if (short > 0) { val (rest, left) = useOn(shortLosses, short); shortLosses = rest; short = left }
        if (long > 0) {
            val (restShort, afterShort) = useOn(shortLosses, long); shortLosses = restShort
            val (restLong, afterLong) = useOn(longLosses, afterShort); longLosses = restLong
            long = afterLong
        }
        if (short < 0) shortLosses = shortLosses + Loss(year, -short)
        if (long < 0) longLosses = longLosses + Loss(year, -long)

        val tax = rules.equityTax(LocalDate.of(year + 1, 3, 31))
        val exemption = Paise(if (long > 0) minOf(long, tax.longTermExemption.value) else 0)
        val estimate = maxOf(short, 0L) * tax.shortTermBps / 10_000 + (maxOf(long, 0L) - exemption.value) * tax.longTermBps / 10_000
        EquityYear(
            year, Paise(short), Paise(long), exemption, tax.longTermExemption, Paise(estimate),
            Paise(shortLosses.sumOf { it.amount }), Paise(longLosses.sumOf { it.amount }),
        )
    }
}
