package com.moneymatters.app.core.invest

import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.marketValue
import java.time.LocalDate

/** The three buckets the allocation target is set against. Hybrid funds that are not equity-oriented sit in [Other]. */
enum class AssetClass { Equity, Debt, Other }

val HoldingEntity.assetClass: AssetClass
    get() = when {
        isEquityOriented -> AssetClass.Equity
        fundClass == FundClass.Debt -> AssetClass.Debt
        else -> AssetClass.Other
    }

data class AllocationSlice(val assetClass: AssetClass, val value: Paise, val fraction: Double)

/** Where the portfolio sits against the equity target; [alert] is set once the gap reaches [DRIFT_ALERT_POINTS]. */
data class Drift(val targetPercent: Int, val actualPercent: Double, val alert: Boolean) {
    val gapPoints: Double get() = actualPercent - targetPercent
}

/** A single stock that makes up more than [CONCENTRATION_LIMIT_PERCENT] of the equity held. */
data class Concentration(val holdingId: String, val fraction: Double)

const val DRIFT_ALERT_POINTS = 5.0
const val CONCENTRATION_LIMIT_PERCENT = 10

/**
 * Splits the portfolio by [AssetClass] at market value (cost where a holding has no price yet), with
 * open deposits counted as debt. Classes with nothing in them are left out.
 */
fun allocation(valued: List<HoldingValuation>, deposits: List<DepositValuation>): List<AllocationSlice> {
    val totals = HashMap<AssetClass, Long>()
    for (v in valued) {
        if (v.units.micro <= 0) continue
        totals.merge(v.holding.assetClass, (v.marketValue ?: v.cost).value, Long::plus)
    }
    for (d in deposits) if (!d.isClosed) totals.merge(AssetClass.Debt, d.value.value, Long::plus)
    val total = totals.values.sum()
    if (total <= 0) return emptyList()
    return AssetClass.entries.mapNotNull { c -> totals[c]?.takeIf { it > 0 }?.let { AllocationSlice(c, Paise(it), it.toDouble() / total) } }
}

fun drift(slices: List<AllocationSlice>, targetEquityPercent: Int?): Drift? {
    if (targetEquityPercent == null || slices.isEmpty()) return null
    val actual = (slices.firstOrNull { it.assetClass == AssetClass.Equity }?.fraction ?: 0.0) * 100
    return Drift(targetEquityPercent, actual, kotlin.math.abs(actual - targetEquityPercent) >= DRIFT_ALERT_POINTS)
}

/** Stocks above the single-stock limit, measured against all equity held (stocks and equity-oriented funds), largest first. */
fun concentrations(valued: List<HoldingValuation>): List<Concentration> {
    fun worth(v: HoldingValuation) = (v.marketValue ?: v.cost).value
    val equity = valued.filter { it.units.micro > 0 && it.holding.assetClass == AssetClass.Equity }
    val total = equity.sumOf(::worth)
    if (total <= 0) return emptyList()
    return equity.filter { it.holding.instrument == Instrument.Stock }
        .map { Concentration(it.holding.id, worth(it).toDouble() / total) }
        .filter { it.fraction * 100 > CONCENTRATION_LIMIT_PERCENT }
        .sortedByDescending { it.fraction }
}

/** The gain, split by term, if every sellable unit were sold at the latest price. Null when there is no price or nothing to sell. */
data class TaxPreview(val sellableUnits: Units, val lockedUnits: Units, val proceeds: Paise, val cost: Paise, val buckets: List<GainBucket>) {
    val gain: Paise get() = proceeds - cost
}

/**
 * Sells the holding on paper today: oldest unlocked lots first (as a real sale would), at the latest price,
 * with the holding-period rules in force today. Nothing is written.
 */
fun taxIfSoldToday(v: HoldingValuation, snapshot: InvestSnapshot, today: LocalDate, rules: CapitalGainsRules): TaxPreview? {
    val price = v.price ?: return null
    val used = snapshot.consumptions.groupBy { it.lotId }
    val open = snapshot.lots.filter { it.holdingId == v.holding.id && !LocalDate.parse(it.acquiredOn).isAfter(today) }.mapNotNull { lot ->
        val consumed = used[lot.id].orEmpty()
        val units = lot.units - consumed.sumOf { it.units }
        if (units <= 0) return@mapNotNull null
        OpenLot(lot.id, LocalDate.parse(lot.acquiredOn), Units(units), Paise(lot.costPaise - consumed.sumOf { it.costPaise }), lot.lockUntil?.let(LocalDate::parse))
    }
    val (sellable, locked) = open.partition { it.lockUntil == null || !it.lockUntil.isAfter(today) }
    val units = Units(sellable.sumOf { it.remainingUnits.micro })
    if (units.micro <= 0) return null
    val consumed = allocateFifo(sellable, units)
    val proceeds = marketValue(units, UnitPrice(price.price))
    val rule = if (v.holding.isEquityOriented) rules.listedEquity(today) else rules.other(today)
    val slabFrom = if (v.holding.fundClass == FundClass.Debt) rules.debtSlabFrom(today) else null
    val acquired = sellable.associate { it.id to it.acquiredOn }
    return TaxPreview(
        sellableUnits = units,
        lockedUnits = Units(locked.sumOf { it.remainingUnits.micro }),
        proceeds = proceeds,
        cost = Paise(consumed.sumOf { it.cost.value }),
        buckets = splitGain(proceeds, consumed.map { DatedConsumption(it, acquired.getValue(it.lotId)) }, today, rule, slabFrom),
    )
}

/** One realised sale: what came in, what the units cost, and the gain by term. Feeds the Tax page (P20). */
data class RealisedSale(
    val transactionId: String,
    val holdingId: String,
    val date: LocalDate,
    val units: Units,
    val proceeds: Paise,
    val cost: Paise,
    val buckets: List<GainBucket>,
) {
    val gain: Paise get() = proceeds - cost

    /** The Indian financial year the sale falls in: April to March, named by the year it starts. */
    val financialYear: Int get() = if (date.monthValue >= 4) date.year else date.year - 1
}

/**
 * Every sale still standing (a reversed one has lost its lot consumptions), newest first. Terms are worked out
 * as at the sale date with the rules in force then, exactly as the sale itself booked them.
 */
fun capitalGainsRegister(ledger: LedgerSnapshot, snapshot: InvestSnapshot, rules: CapitalGainsRules): List<RealisedSale> {
    val lotsById = snapshot.lots.associateBy { it.id }
    val holdingsById = snapshot.holdings.associateBy { it.id }
    val proceedsBySale = HashMap<String, Long>()
    for (h in snapshot.holdings) for ((txn, flow) in holdingFlows(ledger, h.accountId)) proceedsBySale[txn] = flow.amount.value
    val dates = ledger.transactions.associate { it.id to LocalDate.parse(it.date) }
    // A split retires lots through the same table a sale uses, but it is not a sale.
    val restatements = snapshot.corporateActions.mapNotNull { it.transactionId }.toSet()
    return snapshot.consumptions.groupBy { it.saleTransactionId }.mapNotNull { (txn, rows) ->
        if (txn in restatements) return@mapNotNull null
        val date = dates[txn] ?: return@mapNotNull null
        val holding = holdingsById[lotsById[rows.first().lotId]?.holdingId] ?: return@mapNotNull null
        val proceeds = Paise(proceedsBySale[txn] ?: return@mapNotNull null)
        val consumed = rows.map { DatedConsumption(Consumption(it.lotId, Units(it.units), Paise(it.costPaise)), LocalDate.parse(lotsById.getValue(it.lotId).acquiredOn)) }
        val rule = if (holding.isEquityOriented) rules.listedEquity(date) else rules.other(date)
        val slabFrom = if (holding.fundClass == FundClass.Debt) rules.debtSlabFrom(date) else null
        RealisedSale(txn, holding.id, date, Units(rows.sumOf { it.units }), proceeds, Paise(rows.sumOf { it.costPaise }), splitGain(proceeds, consumed, date, rule, slabFrom))
    }.sortedWith(compareByDescending<RealisedSale> { it.date }.thenByDescending { it.transactionId })
}
