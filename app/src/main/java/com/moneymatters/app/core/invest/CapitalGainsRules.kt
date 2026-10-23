package com.moneymatters.app.core.invest

import android.content.Context
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import org.json.JSONObject

private const val RULES_ASSET = "tax/capital_gains.json"

/** Flat rates (basis points) and the yearly long-term exemption for listed-equity gains. */
data class EquityTax(val shortTermBps: Int, val longTermBps: Int, val longTermExemption: Paise)

/**
 * Holding-period rules, read from versioned data (`assets/tax/capital_gains.json`) rather than
 * written into code: each entry applies to sales on or after its `effectiveFrom` date.
 */
class CapitalGainsRules(private val entries: List<Entry>) {
    /** [debtSlabFrom]: debt fund units bought on or after this date are taxed at slab rate, with no long-term treatment. */
    data class Entry(val effectiveFrom: LocalDate, val listedEquityMonths: Int, val otherMonths: Int, val debtSlabFrom: LocalDate? = null,
        val equityShortTermBps: Int = 0,
        val equityLongTermBps: Int = 0,
        val equityLongTermExemption: Paise = Paise.Zero,
    )

    init {
        require(entries.isNotEmpty()) { "At least one capital gains rule is required" }
    }

    private fun on(date: LocalDate) =
        entries.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }
            ?: entries.minBy { it.effectiveFrom }

    /** The rule for stocks and equity-oriented funds sold on [saleDate]. */
    fun listedEquity(saleDate: LocalDate) = HoldingPeriodRule(on(saleDate).listedEquityMonths)

    /** The rule for every other asset sold on [saleDate]. */
    fun other(saleDate: LocalDate) = HoldingPeriodRule(on(saleDate).otherMonths)

    /** Flat tax on listed-equity gains as in force on [date]; zero rates mean the data file has none for it. */
    fun equityTax(date: LocalDate): EquityTax = on(date).let { EquityTax(it.equityShortTermBps, it.equityLongTermBps, it.equityLongTermExemption) }

    /** The purchase date from which debt fund gains are slab-taxed, as in force for a sale on [saleDate]; null if none. */
    fun debtSlabFrom(saleDate: LocalDate): LocalDate? = on(saleDate).debtSlabFrom

    companion object {
        fun parse(json: String): CapitalGainsRules {
            val array = JSONObject(json).getJSONArray("capitalGains")
            return CapitalGainsRules(
                (0 until array.length()).map {
                    val e = array.getJSONObject(it)
                    Entry(
                        LocalDate.parse(e.getString("effectiveFrom")), e.getInt("listedEquityLongTermMonths"), e.getInt("otherLongTermMonths"),
                        e.optString("debtFundSlabTaxedFrom").takeIf { s -> s.isNotEmpty() }?.let(LocalDate::parse),
                        e.optInt("equityShortTermBps", 0), e.optInt("equityLongTermBps", 0), Paise(e.optLong("equityLongTermExemptionPaise", 0)),
                    )
                },
            )
        }

        fun load(context: Context): CapitalGainsRules =
            parse(context.assets.open(RULES_ASSET).bufferedReader().use { it.readText() })
    }
}
