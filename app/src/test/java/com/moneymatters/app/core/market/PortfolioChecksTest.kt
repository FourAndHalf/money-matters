package com.moneymatters.app.core.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioChecksTest {
    private fun pos(id: String, value: Long, m: StockMetrics? = null) = HeldPosition(id, id, id, m != null, value, m)

    @Test fun nothingHeldMeansNoCheck() = assertNull(portfolioCheck(emptyList()))

    @Test fun evenSpreadScoresHighAndOneHoldingScoresZero() {
        assertEquals(0, portfolioCheck(listOf(pos("A", 100_00)))!!.spreadScore)
        assertEquals(100, portfolioCheck((1..10).map { pos("S$it", 10_00) })!!.spreadScore)
        assertEquals(100, portfolioCheck((1..20).map { pos("S$it", 10_00) })!!.spreadScore)
    }

    @Test fun overweightHoldingIsFlaggedWithItsShare() {
        val check = portfolioCheck(listOf(pos("BIG", 60_00), pos("SMALL", 40_00)))!!
        assertEquals(60.0, check.topHoldingPct, 1e-9)
        assertTrue(check.weights.first().flags.any { it.title == "Concentrated" })
        assertEquals(100.0, check.flaggedPct, 1e-9) // both are over 25%
    }

    @Test fun priceRedFlagsAddUpByWeight() {
        val bad = metrics("BAD", List(30) { 100.0 }, volume = 10, series = "BZ")
        val check = portfolioCheck(listOf(pos("OK", 80_00), pos("BAD", 20_00, bad)) + (1..6).map { pos("F$it", 0) })!!
        assertEquals(1, check.weights.count { w -> w.flags.any { it.title == "Not in the normal series" } })
        assertTrue(check.flaggedPct >= 20.0)
    }
}
