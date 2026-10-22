package com.moneymatters.app.core.invest

import android.content.Context
import java.time.LocalDate
import org.json.JSONObject

private const val RULES_ASSET = "tax/capital_gains.json"

/**
 * Holding-period rules, read from versioned data (`assets/tax/capital_gains.json`) rather than
 * written into code: each entry applies to sales on or after its `effectiveFrom` date.
 */
class CapitalGainsRules(private val entries: List<Entry>) {
    data class Entry(val effectiveFrom: LocalDate, val listedEquityMonths: Int, val otherMonths: Int)

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

    companion object {
        fun parse(json: String): CapitalGainsRules {
            val array = JSONObject(json).getJSONArray("capitalGains")
            return CapitalGainsRules(
                (0 until array.length()).map {
                    val e = array.getJSONObject(it)
                    Entry(LocalDate.parse(e.getString("effectiveFrom")), e.getInt("listedEquityLongTermMonths"), e.getInt("otherLongTermMonths"))
                },
            )
        }

        fun load(context: Context): CapitalGainsRules =
            parse(context.assets.open(RULES_ASSET).bufferedReader().use { it.readText() })
    }
}
