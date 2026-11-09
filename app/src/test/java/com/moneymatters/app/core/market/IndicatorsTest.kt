package com.moneymatters.app.core.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {
    @Test fun smaAveragesTheLastN() {
        assertEquals(4.0, sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), 3)!! - 1.0, 1e-9)
        assertNull(sma(listOf(1.0, 2.0), 3))
    }

    @Test fun rsiIs100WithOnlyGainsAnd0WithOnlyLosses() {
        assertEquals(100.0, rsi((1..20).map { it.toDouble() })!!, 1e-9)
        assertEquals(0.0, rsi((20 downTo 1).map { it.toDouble() })!!, 1e-9)
        assertNull(rsi((1..10).map { it.toDouble() }))
    }

    @Test fun rsiMatchesAKnownWilderSeries() {
        // Classic worked example (Wilder / StockCharts): RSI after the first 14 changes is 70.46.
        val closes = listOf(44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28)
        assertEquals(70.46, rsi(closes)!!, 0.05)
    }

    @Test fun metricsReadReturnsAndRanges() {
        val closes = (1..300).map { 100.0 + it } // steady climb
        val m = metrics("UP", closes)
        assertEquals(400.0, m.last, 1e-9)
        assertTrue(m.ret1y!! > 50)
        assertTrue(m.fromHighPct > -2.0) // closing near its own (1% above close) high
        assertNotNull(m.sma200)
        assertTrue(m.last > m.sma50!!)
        assertEquals(100.0, m.rsi14!!, 1e-9)
    }

    @Test fun shortHistoryLeavesLongMeasuresEmptyInsteadOfGuessing() {
        val m = metrics("NEW", (1..30).map { 50.0 + it })
        assertNull(m.ret1y)
        assertNull(m.sma50)
        assertNull(m.sma200)
        assertNotNull(m.rsi14)
    }

    @Test fun volumeRatioCompaaresTodayWithThePriorMonth() {
        val r = rows("V", List(40) { 100.0 }, volume = 1000).toMutableList()
        r[r.lastIndex] = r.last().copy(volume = 5000)
        val m = computeMetrics("V", "EQ", r.map { it.toBar() })!!
        assertEquals(5.0, m.volumeRatio!!, 1e-9)
    }

    @Test fun universeSkipsStocksThatStoppedTrading() {
        val live = rows("LIVE", List(30) { 100.0 })
        val dead = rows("DEAD", List(10) { 100.0 }, end = java.time.LocalDate.of(2026, 9, 1))
        assertEquals(listOf("LIVE"), universeMetrics(live + dead).map { it.symbol })
    }
}
