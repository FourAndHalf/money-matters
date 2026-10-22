package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.CashFlow
import com.moneymatters.app.core.invest.CorporateActionKind
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.GainTerm
import com.moneymatters.app.core.invest.HoldingValuation
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.PriceEntity
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.formatUnits
import com.moneymatters.app.core.invest.gainTerm
import com.moneymatters.app.core.invest.holdingFlows
import com.moneymatters.app.core.invest.isEquityOriented
import com.moneymatters.app.core.invest.valuations
import com.moneymatters.app.core.invest.xirr
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.Tone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

/** How trustworthy the price behind a holding's value is (plan.html → "Price status badges"). */
@Immutable
data class PriceTag(val text: String, val tone: Tone)

@Immutable
data class HoldingRow(
    val id: String,
    val name: String,
    val symbol: String,
    val unitsLabel: String,
    val cost: Paise,
    val avgCostLabel: String,
    /** Null until the holding has a price; the portfolio then counts it at [cost]. */
    val marketValue: Paise?,
    val gain: Paise?,
    val gainPercent: Double?,
    val priceLabel: String?,
    val priceTag: PriceTag,
    val hasUnits: Boolean,
    val instrument: Instrument = Instrument.Stock,
    /** For a fund, e.g. "Equity" or "Debt"; null for a stock. */
    val classLabel: String? = null,
)

@Immutable
data class LotRow(
    val dateLabel: String,
    val unitsLabel: String,
    val cost: Paise,
    val termLabel: String,
    val isLongTerm: Boolean,
    /** A lock or exit-load window still running on this lot, if any. */
    val note: String? = null,
)

@Immutable
data class HoldingTxn(val id: String, val dateLabel: String, val isBuy: Boolean, val unitsLabel: String, val amount: Paise)

@Immutable
data class PricePoint(val dateLabel: String, val priceLabel: String, val source: String, val tag: PriceTag)

/** A dividend, split or bonus on a holding; [amount] is the gross dividend, null for a split or bonus. */
@Immutable
data class ActionRow(val dateLabel: String, val label: String, val amount: Paise?)

@Immutable
data class HoldingDetail(
    val row: HoldingRow,
    val lots: List<LotRow>,
    val transactions: List<HoldingTxn>,
    val prices: List<PricePoint>,
    val xirr: Double?,
    val actions: List<ActionRow> = emptyList(),
    val dividendTotal: Paise = Paise.Zero,
)

/** A fund purchase paid for but not yet allotted units. */
@Immutable
data class PendingRow(val id: String, val holdingName: String, val dateLabel: String, val amount: Paise)

/** Everything the Invest tab (P13) and holding detail (P14) render, built from the same snapshots as the rest of the app. */
@Immutable
data class InvestState(
    val holdings: List<HoldingRow>,
    val cost: Paise,
    /** Priced holdings at market value, unpriced at cost, so it matches the net worth on Home. */
    val value: Paise,
    val gain: Paise,
    val gainPercent: Double?,
    val xirr: Double?,
    val unpricedCount: Int,
    val details: Map<String, HoldingDetail>,
    val pending: List<PendingRow> = emptyList(),
)

fun formatPercent(fraction: Double): String = String.format(Locale.ROOT, "%+.2f%%", fraction * 100)

/** A per-unit price in 1/10,000 rupee as `₹81.23` or, when it carries more precision, `₹81.2345`. */
fun formatUnitPrice(tenThousandths: Long): String {
    val base = Paise(tenThousandths / 100).formatInr(showPaise = true)
    val extra = tenThousandths % 100
    return if (extra == 0L) base else base + extra.toString().padStart(2, '0')
}

internal fun PriceStatus.tag(): PriceTag = when (this) {
    PriceStatus.Verified -> PriceTag("✓ Verified", Tone.Positive)
    PriceStatus.Manual -> PriceTag("✍ Manual", Tone.Info)
    PriceStatus.Stale, PriceStatus.Mismatch -> PriceTag("⚠ ${name}", Tone.Warning)
    PriceStatus.Failed, PriceStatus.Suspicious -> PriceTag("✕ ${name}", Tone.Negative)
}

private val NoPrice = PriceTag("No price", Tone.Neutral)

private fun perUnit(cost: Paise, units: Long): String =
    if (units <= 0) "—" else Paise((cost.value * 1_000_000 + units / 2) / units).formatInr(showPaise = true)

/** Builds [InvestState]. Pure: same snapshots in, same state out. */
fun buildInvest(ledger: LedgerSnapshot, invest: InvestSnapshot, today: LocalDate, rules: CapitalGainsRules): InvestState {
    val accountsById = ledger.accounts.associateBy { it.id }
    val valued = valuations(invest)
    val rowsById = valued.associate { v -> v.holding.id to rowFor(v, accountsById.getValue(v.holding.accountId).name) }
    // Fully sold holdings stay reachable by id but drop off the list.
    val holdings = valued.filter { it.units.micro > 0 }.map { rowsById.getValue(it.holding.id) }.sortedByDescending { it.marketValue?.value ?: it.cost.value }

    val flows = mutableListOf<CashFlow>()
    val details = valued.associate { v ->
        val own = holdingFlows(ledger, v.holding.accountId)
        val dividends = dividendFlows(invest, v.holding.id)
        flows += own.map { it.second } + dividends
        v.holding.id to detailFor(v, rowsById.getValue(v.holding.id), own, dividends, ledger, invest, today, rules)
    }

    val held = valued.filter { it.units.micro > 0 }
    val cost = Paise(held.sumOf { it.cost.value })
    val value = Paise(held.sumOf { it.marketValue?.value ?: it.cost.value })
    val pricedCost = held.filter { it.marketValue != null }.sumOf { it.cost.value }
    val gain = Paise(held.sumOf { it.gain?.value ?: 0 })
    val allPriced = held.isNotEmpty() && held.all { it.marketValue != null }
    return InvestState(
        holdings = holdings,
        cost = cost,
        value = value,
        gain = gain,
        gainPercent = if (pricedCost > 0) gain.value.toDouble() / pricedCost else null,
        xirr = if (allPriced) xirr(flows + CashFlow(today, value)) else null,
        unpricedCount = held.count { it.marketValue == null },
        details = details,
        pending = invest.pending.filter { it.isOpen }.sortedBy { it.orderDate }.map { p ->
            PendingRow(p.id, rowsById[p.holdingId]?.name.orEmpty(), LocalDate.parse(p.orderDate).format(DayMonthYear), Paise(p.amountPaise))
        },
    )
}

private fun rowFor(v: HoldingValuation, accountName: String): HoldingRow {
    val symbol = v.holding.symbol ?: accountName
    val gain = v.gain
    return HoldingRow(
        id = v.holding.id,
        name = accountName,
        symbol = symbol,
        unitsLabel = formatUnits(v.units),
        cost = v.cost,
        avgCostLabel = perUnit(v.cost, v.units.micro),
        marketValue = v.marketValue,
        gain = gain,
        gainPercent = if (gain != null && v.cost.value > 0) gain.value.toDouble() / v.cost.value else null,
        priceLabel = v.price?.let { formatUnitPrice(it.price) },
        priceTag = v.price?.status?.tag() ?: NoPrice,
        hasUnits = v.units.micro > 0,
        instrument = v.holding.instrument,
        classLabel = v.holding.fundClass?.label(),
    )
}

private fun detailFor(
    v: HoldingValuation,
    row: HoldingRow,
    flows: List<Pair<String, CashFlow>>,
    dividends: List<CashFlow>,
    ledger: LedgerSnapshot,
    invest: InvestSnapshot,
    today: LocalDate,
    rules: CapitalGainsRules,
): HoldingDetail {
    val used = invest.consumptions.groupBy { it.lotId }
    val rule = if (v.holding.isEquityOriented) rules.listedEquity(today) else rules.other(today)
    val slabFrom = if (v.holding.fundClass == FundClass.Debt) rules.debtSlabFrom(today) else null
    val lots = invest.lots.filter { it.holdingId == v.holding.id }.sortedBy { it.acquiredOn }.mapNotNull { lot ->
        val consumed = used[lot.id].orEmpty()
        val units = lot.units - consumed.sumOf { it.units }
        if (units <= 0) return@mapNotNull null
        val acquired = LocalDate.parse(lot.acquiredOn)
        val slab = slabFrom != null && !acquired.isBefore(slabFrom)
        val long = !slab && gainTerm(acquired, today, rule) == GainTerm.Long
        val lock = lot.lockUntil?.let(LocalDate::parse)?.takeIf { it.isAfter(today) }
        val exitLoad = v.holding.exitLoadDays?.let { acquired.plusDays(it.toLong()) }?.takeIf { it.isAfter(today) }
        LotRow(
            dateLabel = acquired.format(DayMonthYear),
            unitsLabel = formatUnits(Units(units)),
            cost = Paise(lot.costPaise - consumed.sumOf { it.costPaise }),
            termLabel = if (slab) "Slab rate" else if (long) "Long-term" else "Short-term",
            isLongTerm = long,
            note = when {
                lock != null -> "Locked until ${lock.format(DayMonthYear)}"
                exitLoad != null -> "Exit load until ${exitLoad.format(DayMonthYear)}"
                else -> null
            },
        )
    }
    val postingsByTxn = ledger.postings.groupBy { it.transactionId }
    val txns = flows.sortedWith(compareByDescending<Pair<String, CashFlow>> { it.second.date }.thenByDescending { it.first }).map { (id, flow) ->
        val own = postingsByTxn[id].orEmpty().filter { it.accountId == v.holding.accountId }
        HoldingTxn(
            id = id,
            dateLabel = flow.date.format(DayMonthYear),
            isBuy = flow.amount.value < 0,
            unitsLabel = formatUnits(Units(own.sumOf { it.units ?: 0 })),
            amount = Paise(kotlin.math.abs(flow.amount.value)),
        )
    }
    val prices = invest.prices.filter { it.holdingId == v.holding.id }.sortedWith(compareByDescending<PriceEntity> { it.date }.thenByDescending { it.id }).map {
        PricePoint(LocalDate.parse(it.date).format(DayMonthYear), formatUnitPrice(it.price), it.source, it.status.tag())
    }
    // A holding still held needs a price for today's value; one fully sold is measured on its flows alone.
    val allFlows = flows.map { it.second } + dividends
    val xirr = when {
        v.units.micro == 0L -> xirr(allFlows)
        v.marketValue != null -> xirr(allFlows + CashFlow(today, v.marketValue))
        else -> null
    }
    val actions = invest.corporateActions.filter { it.holdingId == v.holding.id }.sortedByDescending { it.date }.map {
        val date = LocalDate.parse(it.date).format(DayMonthYear)
        when (it.kind) {
            CorporateActionKind.Dividend -> ActionRow(date, "Dividend", Paise(it.amountPaise ?: 0))
            CorporateActionKind.Split -> ActionRow(date, "Split ×${formatUnits(Units(it.ratioMicro ?: 0))}", null)
            CorporateActionKind.Bonus -> ActionRow(date, "Bonus ${formatUnits(Units(it.ratioMicro ?: 0))} per unit", null)
        }
    }
    return HoldingDetail(row, lots, txns, prices, xirr, actions, Paise(dividends.sumOf { it.amount.value }))
}

/** A dividend is money the holding paid out, so it counts as an inflow on its date (gross: net received plus TDS to be claimed). */
private fun dividendFlows(invest: InvestSnapshot, holdingId: String): List<CashFlow> =
    invest.corporateActions
        .filter { it.holdingId == holdingId && it.kind == CorporateActionKind.Dividend }
        .map { CashFlow(LocalDate.parse(it.date), Paise(it.amountPaise ?: 0)) }

internal fun FundClass.label(): String = when (this) {
    FundClass.Equity -> "Equity"
    FundClass.EquityHybrid -> "Equity hybrid"
    FundClass.Hybrid -> "Hybrid"
    FundClass.Debt -> "Debt"
    FundClass.Elss -> "ELSS"
}
