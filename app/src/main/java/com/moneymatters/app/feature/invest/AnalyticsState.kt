package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.AllocationSlice
import com.moneymatters.app.core.invest.AssetClass
import com.moneymatters.app.core.invest.Concentration
import com.moneymatters.app.core.invest.Drift
import com.moneymatters.app.core.invest.EquityYear
import com.moneymatters.app.core.invest.GainTerm
import com.moneymatters.app.core.invest.RealisedSale
import com.moneymatters.app.core.invest.TaxPreview
import com.moneymatters.app.core.invest.formatUnits
import com.moneymatters.app.core.money.Paise
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

@Immutable
data class AllocationRow(val label: String, val value: Paise, val fraction: Double)

@Immutable
data class ConcentrationRow(val holdingId: String, val name: String, val percentLabel: String)

/** Allocation against the equity target, and stocks that make up too much of the equity held. */
@Immutable
data class AnalyticsState(
    val allocation: List<AllocationRow> = emptyList(),
    val targetEquityPercent: Int? = null,
    /** Set when equity has drifted five points or more from the target; the card shows it as a warning. */
    val driftMessage: String? = null,
    val concentration: List<ConcentrationRow> = emptyList(),
)

/** Gain by term if a holding were sold today; each [lines] entry is a labelled gain (or loss). */
@Immutable
data class TaxPreviewRow(
    val sellableLabel: String,
    val lockedLabel: String?,
    val proceeds: Paise,
    val cost: Paise,
    val gain: Paise,
    val lines: List<Pair<String, Paise>>,
)

@Immutable
data class SaleRow(
    val id: String,
    val dateLabel: String,
    val name: String,
    val unitsLabel: String,
    val proceeds: Paise,
    val cost: Paise,
    val gain: Paise,
    val termLabel: String,
)

/** Listed-equity gains for a year once losses are set off; [lossForward] is what carries into later years. */
@Immutable
data class EquityRow(val shortNet: Paise, val longNet: Paise, val exemptionUsed: Paise, val exemptionLimit: Paise, val estimatedTax: Paise, val lossForward: Paise)

/** Room left in this year's tax-free long-term gain, and the long-term gain that could fill it by selling today. */
@Immutable
data class HarvestRow(val yearLabel: String, val room: Paise, val available: Paise) {
    val suggested: Paise get() = Paise(minOf(room.value, available.value))
}

/** One financial year of realised gains; [slab] is debt-fund gain taxed at the investor's slab rate. */
@Immutable
data class YearGains(
    val label: String,
    val short: Paise,
    val long: Paise,
    val slab: Paise,
    val sales: List<SaleRow>,
    /** Listed-equity result after loss set-off, with the exemption and an estimate of tax; null when no equity was sold. */
    val equity: EquityRow? = null,
) {
    val total: Paise get() = short + long + slab
}

internal fun AssetClass.label() = when (this) {
    AssetClass.Equity -> "Equity"
    AssetClass.Debt -> "Debt & FDs"
    AssetClass.Other -> "Other"
}

internal fun buildAnalytics(slices: List<AllocationSlice>, drift: Drift?, concentrations: List<Concentration>, names: Map<String, String>) = AnalyticsState(
    allocation = slices.map { AllocationRow(it.assetClass.label(), it.value, it.fraction) },
    targetEquityPercent = drift?.targetPercent,
    driftMessage = drift?.takeIf { it.alert }?.let {
        val side = if (it.gapPoints > 0) "above" else "below"
        String.format(Locale.ROOT, "Equity is %.1f points %s your %d%% target", kotlin.math.abs(it.gapPoints), side, it.targetPercent)
    },
    concentration = concentrations.map { ConcentrationRow(it.holdingId, names[it.holdingId].orEmpty(), String.format(Locale.ROOT, "%.1f%% of equity", it.fraction * 100)) },
)

internal fun financialYearOf(date: java.time.LocalDate) = if (date.monthValue >= 4) date.year else date.year - 1

internal fun yearLabel(year: Int) = "FY $year-${String.format(Locale.ROOT, "%02d", (year + 1) % 100)}"

internal fun TaxPreview.toRow(): TaxPreviewRow = TaxPreviewRow(
    sellableLabel = formatUnits(sellableUnits),
    lockedLabel = lockedUnits.micro.takeIf { it > 0 }?.let { formatUnits(lockedUnits) },
    proceeds = proceeds,
    cost = cost,
    gain = gain,
    lines = buckets.map { it.term.label() to it.gain },
)

private fun GainTerm.label() = when (this) {
    GainTerm.Short -> "Short-term"
    GainTerm.Long -> "Long-term"
    GainTerm.Slab -> "Slab rate"
}

/** Groups the register by financial year, newest year first. */
internal fun buildYears(sales: List<RealisedSale>, names: Map<String, String>, equity: List<EquityYear> = emptyList()): List<YearGains> =
    sales.groupBy { it.financialYear }.toSortedMap(compareByDescending { it }).map { (year, list) ->
        fun sum(term: GainTerm) = Paise(list.sumOf { s -> s.buckets.filter { it.term == term }.sumOf { it.gain.value } })
        YearGains(
            label = yearLabel(year),
            short = sum(GainTerm.Short),
            long = sum(GainTerm.Long),
            slab = sum(GainTerm.Slab),
            equity = equity.firstOrNull { it.year == year }?.let {
                EquityRow(it.shortNet, it.longNet, it.exemptionUsed, it.exemptionLimit, it.estimatedTax, it.shortLossForward + it.longLossForward)
            },
            sales = list.map { s ->
                SaleRow(
                    id = s.transactionId,
                    dateLabel = s.date.format(DayMonthYear),
                    name = names[s.holdingId].orEmpty(),
                    unitsLabel = formatUnits(s.units),
                    proceeds = s.proceeds,
                    cost = s.cost,
                    gain = s.gain,
                    termLabel = s.buckets.map { it.term.label() }.ifEmpty { listOf("—") }.joinToString(" + "),
                )
            },
        )
    }
