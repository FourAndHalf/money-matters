package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EquityTaxTest {

    private val rules = CapitalGainsRules(
        listOf(
            CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, null, 1500, 1000, 1_00_000.rupees),
            CapitalGainsRules.Entry(LocalDate.of(2024, 7, 23), 12, 24, null, 2000, 1250, 1_25_000.rupees),
        ),
    )

    private fun sale(year: Int, term: GainTerm, gain: Long, equity: Boolean = true, id: String = "t$year$term$gain") = RealisedSale(
        transactionId = id, holdingId = "h", date = LocalDate.of(year, 6, 1), units = Units.whole(1),
        proceeds = (1_000 + gain).rupees, cost = 1_000.rupees, buckets = listOf(GainBucket(term, gain.rupees)), equity = equity,
    )

    private fun years(vararg sales: RealisedSale) = equityYears(sales.toList(), rules).associateBy { it.year }

    @Test fun `long-term gain is taxed above the exemption and short-term at its flat rate`() {
        val y = years(sale(2025, GainTerm.Long, 3_00_000), sale(2025, GainTerm.Short, 1_00_000)).getValue(2025)
        assertEquals(1_25_000.rupees, y.exemptionUsed)
        assertEquals((1_75_000 * 125 / 1000 + 20_000).rupees, y.estimatedTax) // 21,875 + 20,000
    }

    @Test fun `the rates in force on the year's last day apply`() {
        val old = years(sale(2019, GainTerm.Long, 2_00_000), sale(2019, GainTerm.Short, 1_00_000)).getValue(2019)
        assertEquals(1_00_000.rupees, old.exemptionUsed)
        assertEquals((10_000 + 15_000).rupees, old.estimatedTax) // 10% of 1L above 1L, 15% of 1L
    }

    @Test fun `a short-term loss in the same year offsets a long-term gain`() {
        val y = years(sale(2025, GainTerm.Short, -50_000), sale(2025, GainTerm.Long, 2_00_000)).getValue(2025)
        assertEquals(0.rupees, y.shortNet)
        assertEquals(1_50_000.rupees, y.longNet)
        assertEquals((25_000 * 125 / 1000).rupees, y.estimatedTax)
    }

    @Test fun `a long-term loss brought forward offsets later long-term gain and then is used up`() {
        val all = years(sale(2024, GainTerm.Long, -1_00_000), sale(2025, GainTerm.Long, 3_00_000))
        assertEquals(1_00_000.rupees, all.getValue(2024).longLossForward)
        val y = all.getValue(2025)
        assertEquals(2_00_000.rupees, y.longNet)
        assertEquals(0.rupees, y.longLossForward)
        assertEquals((75_000 * 125 / 1000).rupees, y.estimatedTax)
    }

    @Test fun `a long-term loss never offsets a short-term gain and stays carried`() {
        val all = years(sale(2024, GainTerm.Long, -1_00_000), sale(2025, GainTerm.Short, 50_000))
        val y = all.getValue(2025)
        assertEquals(50_000.rupees, y.shortNet)
        assertEquals(10_000.rupees, y.estimatedTax)
        assertEquals(1_00_000.rupees, y.longLossForward)
    }

    @Test fun `a short-term loss brought forward can offset either kind of gain`() {
        val y = years(sale(2024, GainTerm.Short, -80_000), sale(2025, GainTerm.Short, 30_000), sale(2025, GainTerm.Long, 2_00_000)).getValue(2025)
        assertEquals(0.rupees, y.shortNet)
        assertEquals(1_50_000.rupees, y.longNet) // 50,000 of the loss is left for the long-term gain
        assertEquals(0.rupees, y.shortLossForward)
    }

    @Test fun `a loss expires after eight years`() {
        val all = years(sale(2017, GainTerm.Long, -1_00_000), sale(2026, GainTerm.Long, 1_00_000))
        assertEquals(1_00_000.rupees, all.getValue(2026).longNet)
    }

    @Test fun `other assets are left out and no equity sales means no years`() {
        assertEquals(emptyList<EquityYear>(), equityYears(listOf(sale(2025, GainTerm.Long, 1_00_000, equity = false)), rules))
        val y = years(sale(2025, GainTerm.Long, 50_000), sale(2025, GainTerm.Long, 9_00_000, equity = false)).getValue(2025)
        assertEquals(50_000.rupees, y.longNet)
        assertEquals(0.rupees, y.estimatedTax)
    }

    @Test fun `the data file carries the rates`() {
        val parsed = CapitalGainsRules.parse(java.io.File("src/main/assets/tax/capital_gains.json").readText())
        val now = parsed.equityTax(LocalDate.of(2026, 3, 31))
        assertEquals(2000, now.shortTermBps)
        assertEquals(1250, now.longTermBps)
        assertEquals(Paise(12_500_000), now.longTermExemption)
        assertEquals(1000, parsed.equityTax(LocalDate.of(2024, 3, 31)).longTermBps)
    }
}
