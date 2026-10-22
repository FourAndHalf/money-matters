package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XirrAndFixedDepositTest {

    private val jan1 = LocalDate.of(2026, 1, 1)

    @Test fun `xirr of one year at ten percent is ten percent`() {
        val flows = listOf(CashFlow(LocalDate.of(2021, 1, 1), (-1_000).rupees), CashFlow(LocalDate.of(2022, 1, 1), 1_100.rupees))
        assertEquals(0.10, xirr(flows)!!, 1e-6)
    }

    @Test fun `xirr handles a monthly SIP and a final value`() {
        val flows = (0 until 12).map { CashFlow(jan1.plusMonths(it.toLong()), (-10_000).rupees) } +
            CashFlow(jan1.plusMonths(12), 130_000.rupees)
        val r = xirr(flows)
        assertNotNull(r)
        assertTrue("positive return expected, got $r", r!! > 0.1 && r < 0.5)
    }

    @Test fun `xirr is null when there is no outflow or no inflow`() {
        assertNull(xirr(listOf(CashFlow(jan1, 1_000.rupees))))
        assertNull(xirr(listOf(CashFlow(jan1, (-1_000).rupees), CashFlow(jan1.plusYears(1), (-500).rupees))))
    }

    @Test fun `quarterly compounding for a year`() {
        // ₹1,00,000 at 7.00%: 1.0175^4 = 1.07185903, so interest ₹7,185.90.
        assertEquals(718_590.paise, accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, jan1, jan1.plusYears(1), jan1.plusYears(1)))
    }

    @Test fun `simple interest for a year, and monthly compounds a little more than quarterly`() {
        assertEquals(700_000.paise, accruedInterest(1_00_000.rupees, 700, Compounding.Simple, jan1, jan1.plusYears(1), jan1.plusYears(1)))
        val monthly = accruedInterest(1_00_000.rupees, 700, Compounding.Monthly, jan1, jan1.plusYears(1), jan1.plusYears(1))
        assertTrue(monthly.value > 718_590)
    }

    @Test fun `a part-period earns simple interest by days over 365`() {
        // 45 days: 1,00,000 × 7% × 45/365 = ₹863.01 → 86,301 paise
        assertEquals(86_301.paise, accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, jan1, jan1.plusDays(45), jan1.plusYears(1)))
    }

    @Test fun `interest stops at maturity and is zero before the start`() {
        val maturity = jan1.plusYears(1)
        assertEquals(
            accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, jan1, maturity, maturity),
            accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, jan1, maturity.plusYears(3), maturity),
        )
        assertEquals(Paise.Zero, accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, jan1, jan1.minusDays(5), maturity))
    }

    @Test fun `maturity value is principal plus interest`() {
        assertEquals(10_718_590.paise, maturityValue(1_00_000.rupees, 700, Compounding.Quarterly, jan1, jan1.plusYears(1)))
    }

    @Test fun `breaking early recomputes interest at the reduced rate`() {
        val maturity = jan1.plusYears(1)
        // Six months at 7% - 1% = 6%, quarterly: 1.015² = 1.030225 → ₹3,022.50
        val broken = breakValue(1_00_000.rupees, 700, Compounding.Quarterly, jan1, maturity, jan1.plusMonths(6), penaltyBps = 100)
        assertEquals(10_302_250.paise, broken.proceeds)
        assertEquals(1_00_000.rupees + accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, jan1, jan1.plusMonths(6), maturity) - broken.proceeds, broken.penalty)
        assertTrue(broken.penalty.value > 0)
    }

    @Test fun `no penalty within thirty days of maturity or when the FD has none`() {
        val maturity = jan1.plusYears(1)
        val late = breakValue(1_00_000.rupees, 700, Compounding.Quarterly, jan1, maturity, maturity.minusDays(30), penaltyBps = 100)
        assertEquals(Paise.Zero, late.penalty)
        val none = breakValue(1_00_000.rupees, 700, Compounding.Quarterly, jan1, maturity, jan1.plusMonths(6), penaltyBps = 0)
        assertEquals(Paise.Zero, none.penalty)
    }

    private val Int.paise get() = Paise(toLong())
}
