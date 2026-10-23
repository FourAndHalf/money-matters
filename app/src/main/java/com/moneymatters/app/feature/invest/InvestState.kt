package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.CashFlow
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.CorporateActionKind
import com.moneymatters.app.core.invest.DepositValuation
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.GainTerm
import com.moneymatters.app.core.invest.HoldingValuation
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.PriceEntity
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.formatUnits
import com.moneymatters.app.core.invest.gainTerm
import com.moneymatters.app.core.invest.depositValuation
import com.moneymatters.app.core.invest.effectiveStatus
import com.moneymatters.app.core.invest.allocation
import com.moneymatters.app.core.invest.capitalGainsRegister
import com.moneymatters.app.core.invest.concentrations
import com.moneymatters.app.core.invest.drift
import com.moneymatters.app.core.invest.holdingFlows
import com.moneymatters.app.core.invest.taxIfSoldToday
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
import java.time.ZoneId
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
    /** What selling everything sellable today would realise; null without a price or units. */
    val taxPreview: TaxPreviewRow? = null,
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
    val deposits: List<DepositRow> = emptyList(),
    val depositDetails: Map<String, DepositDetail> = emptyMap(),
    val sips: List<SipRow> = emptyList(),
    val sipDetails: Map<String, SipDetail> = emptyMap(),
    val prices: PricesState = PricesState(),
    /** Whether there is a fund to start a SIP in. */
    val hasFunds: Boolean = false,
    val analytics: AnalyticsState = AnalyticsState(),
    /** Realised gains by financial year, newest first (the capital-gains register). */
    val gainsByYear: List<YearGains> = emptyList(),
)

/** A fixed deposit in the portfolio list; [value] is principal plus interest earned so far. */
@Immutable
data class DepositRow(
    val accountId: String,
    val name: String,
    val subtitle: String,
    val principal: Paise,
    val value: Paise,
    val badge: PriceTag,
)

@Immutable
data class DepositDetail(
    val row: DepositRow,
    val rateLabel: String,
    val compoundingLabel: String,
    val startLabel: String,
    val maturityLabel: String,
    val accruedInterest: Paise,
    val postedInterest: Paise,
    val unpostedInterest: Paise,
    val maturityValue: Paise,
    val breakProceeds: Paise,
    val breakPenalty: Paise,
    val autoRenew: Boolean,
    val isClosed: Boolean,
    val matured: Boolean,
    val closedLabel: String?,
    val payout: Paise?,
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
fun buildInvest(
    ledger: LedgerSnapshot,
    invest: InvestSnapshot,
    today: LocalDate,
    rules: CapitalGainsRules,
    goal: SipGoal? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    targetEquityPercent: Int? = null,
): InvestState {
    val accountsById = ledger.accounts.associateBy { it.id }
    val valued = valuations(invest)
    val rowsById = valued.associate { v -> v.holding.id to rowFor(v, accountsById.getValue(v.holding.accountId).name, today) }
    // Fully sold holdings stay reachable by id but drop off the list.
    val holdings = valued.filter { it.units.micro > 0 }.map { rowsById.getValue(it.holding.id) }.sortedByDescending { it.marketValue?.value ?: it.cost.value }

    val flows = mutableListOf<CashFlow>()
    val details = valued.associate { v ->
        val own = holdingFlows(ledger, v.holding.accountId)
        val dividends = dividendFlows(invest, v.holding.id)
        flows += own.map { it.second } + dividends
        v.holding.id to detailFor(v, rowsById.getValue(v.holding.id), own, dividends, ledger, invest, today, rules)
            .copy(taxPreview = taxIfSoldToday(v, invest, today, rules)?.toRow())
    }

    val held = valued.filter { it.units.micro > 0 }
    val cost = Paise(held.sumOf { it.cost.value })
    val value = Paise(held.sumOf { it.marketValue?.value ?: it.cost.value })
    val pricedCost = held.filter { it.marketValue != null }.sumOf { it.cost.value }
    val gain = Paise(held.sumOf { it.gain?.value ?: 0 })
    val deposits = invest.fixedDeposits.map { fd -> depositValuation(fd, bookedBalance(ledger, fd.accountId), today) }
    val openDeposits = deposits.filter { !it.isClosed }
    for (d in deposits) {
        flows += CashFlow(LocalDate.parse(d.deposit.startDate), -d.principal)
        d.deposit.closedOn?.let { flows += CashFlow(LocalDate.parse(it), Paise(d.deposit.payoutPaise ?: 0)) }
    }
    val totalCost = cost + Paise(openDeposits.sumOf { it.principal.value })
    val totalValue = value + Paise(openDeposits.sumOf { it.value.value })
    val totalGain = gain + Paise(openDeposits.sumOf { it.accruedInterest.value })
    val totalPricedCost = pricedCost + openDeposits.sumOf { it.principal.value }
    val allPriced = (held.isNotEmpty() || deposits.isNotEmpty()) && held.all { it.marketValue != null }
    val (sipRows, sipDetails) = buildSips(ledger, invest, today, goal, rowsById.mapValues { it.value.name })
    val slices = allocation(valued, deposits)
    val depositRows = deposits.associate { v -> v.deposit.accountId to depositRow(v, accountsById.getValue(v.deposit.accountId).name, today) }
    return InvestState(
        holdings = holdings,
        cost = totalCost,
        value = totalValue,
        gain = totalGain,
        gainPercent = if (totalPricedCost > 0) totalGain.value.toDouble() / totalPricedCost else null,
        xirr = if (allPriced) xirr(flows + CashFlow(today, totalValue)) else null,
        deposits = openDeposits.sortedBy { it.deposit.maturityDate }.map { depositRows.getValue(it.deposit.accountId) },
        depositDetails = deposits.associate { v -> v.deposit.accountId to depositDetail(v, depositRows.getValue(v.deposit.accountId), today) },
        sips = sipRows,
        sipDetails = sipDetails,
        prices = buildPrices(valued, rowsById, invest, today, zone),
        analytics = slices.let { buildAnalytics(it, drift(it, targetEquityPercent), concentrations(valued), rowsById.mapValues { r -> r.value.name }) },
        gainsByYear = buildYears(capitalGainsRegister(ledger, invest, rules), rowsById.mapValues { it.value.name }),
        hasFunds = valued.any { it.holding.instrument == Instrument.MutualFund },
        unpricedCount = held.count { it.marketValue == null },
        details = details,
        pending = invest.pending.filter { it.isOpen }.sortedBy { it.orderDate }.map { p ->
            PendingRow(p.id, rowsById[p.holdingId]?.name.orEmpty(), LocalDate.parse(p.orderDate).format(DayMonthYear), Paise(p.amountPaise))
        },
    )
}

private fun rowFor(v: HoldingValuation, accountName: String, today: LocalDate): HoldingRow {
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
        priceTag = v.price?.effectiveStatus(today)?.tag() ?: NoPrice,
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
        PricePoint(LocalDate.parse(it.date).format(DayMonthYear), formatUnitPrice(it.price), it.source, it.effectiveStatus(today).tag())
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

/** An account's booked balance (drafts excluded), debit minus credit: what an asset holds. */
private fun bookedBalance(ledger: LedgerSnapshot, accountId: String): Paise {
    val booked = ledger.transactions.filter { it.status != TransactionStatus.Draft }.map { it.id }.toSet()
    return Paise(ledger.postings.filter { it.accountId == accountId && it.transactionId in booked }.sumOf { (it.debitPaise ?: 0) - (it.creditPaise ?: 0) })
}

private fun Compounding.label() = when (this) {
    Compounding.Monthly -> "Monthly"
    Compounding.Quarterly -> "Quarterly"
    Compounding.Simple -> "Simple, paid at maturity"
}

private fun depositRow(v: DepositValuation, name: String, today: LocalDate): DepositRow {
    val d = v.deposit
    val maturity = LocalDate.parse(d.maturityDate)
    val badge = when {
        v.isClosed -> PriceTag("Closed", Tone.Neutral)
        v.daysToMaturity < 0 -> PriceTag("Matured", Tone.Warning)
        v.daysToMaturity <= 30 -> PriceTag("Matures in ${v.daysToMaturity} ${if (v.daysToMaturity == 1L) "day" else "days"}", Tone.Warning)
        else -> PriceTag("Matures ${maturity.format(DayMonthYear)}", Tone.Neutral)
    }
    return DepositRow(
        accountId = d.accountId,
        name = name,
        subtitle = "${formatBps(d.rateBps)} · ${d.compounding.label().substringBefore(",").lowercase()}",
        principal = v.principal,
        value = if (v.isClosed) Paise(d.payoutPaise ?: 0) else v.value,
        badge = badge,
    )
}

private fun depositDetail(v: DepositValuation, row: DepositRow, today: LocalDate): DepositDetail {
    val d = v.deposit
    return DepositDetail(
        row = row,
        rateLabel = formatBps(d.rateBps),
        compoundingLabel = d.compounding.label(),
        startLabel = LocalDate.parse(d.startDate).format(DayMonthYear),
        maturityLabel = LocalDate.parse(d.maturityDate).format(DayMonthYear),
        accruedInterest = v.accruedInterest,
        postedInterest = v.postedInterest,
        unpostedInterest = v.unposted,
        maturityValue = v.maturityValue,
        breakProceeds = v.breakProceeds,
        breakPenalty = v.breakPenalty,
        autoRenew = d.autoRenew,
        isClosed = v.isClosed,
        matured = v.daysToMaturity <= 0,
        closedLabel = d.closedOn?.let { LocalDate.parse(it).format(DayMonthYear) },
        payout = d.payoutPaise?.let(::Paise),
    )
}

/** 725 basis points as `7.25%`. */
internal fun formatBps(bps: Int): String = String.format(Locale.ROOT, "%d.%02d%%", bps / 100, bps % 100)
