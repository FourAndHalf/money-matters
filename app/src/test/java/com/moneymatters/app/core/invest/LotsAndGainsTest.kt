package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LotsAndGainsTest {

    private val a = OpenLot("a", LocalDate.of(2025, 1, 10), Units.whole(10), 1_000.rupees)
    private val b = OpenLot("b", LocalDate.of(2025, 6, 1), Units.whole(10), 1_200.rupees)
    private val rule = HoldingPeriodRule(longTermAfterMonths = 12)

    @Test fun `a sale uses the oldest lot first, whatever order the lots are given in`() {
        val used = allocateFifo(listOf(b, a), Units.whole(15))
        assertEquals(
            listOf(Consumption("a", Units.whole(10), 1_000.rupees), Consumption("b", Units.whole(5), 600.rupees)),
            used,
        )
    }

    @Test fun `selling more than is held is refused`() {
        assertThrows(InsufficientUnitsException::class.java) { allocateFifo(listOf(a), Units.whole(11)) }
    }

    @Test fun `partial sales of one lot always add back up to its cost`() {
        // 3 units costing ₹1.00: thirds don't divide evenly into paise.
        var lot = OpenLot("x", LocalDate.of(2025, 1, 1), Units.whole(3), Paise(100))
        var totalCost = 0L
        repeat(3) {
            val c = allocateFifo(listOf(lot), Units.whole(1)).single()
            totalCost += c.cost.value
            lot = lot.copy(remainingUnits = lot.remainingUnits - c.units, remainingCost = lot.remainingCost - c.cost)
        }
        assertEquals(100L, totalCost)
        assertEquals(Units.Zero, lot.remainingUnits)
        assertEquals(Paise.Zero, lot.remainingCost)
    }

    @Test fun `a gain is long-term only after more than the rule's months`() {
        val bought = LocalDate.of(2025, 1, 10)
        assertEquals(GainTerm.Short, gainTerm(bought, LocalDate.of(2026, 1, 10), rule))
        assertEquals(GainTerm.Long, gainTerm(bought, LocalDate.of(2026, 1, 11), rule))
        assertEquals(GainTerm.Short, gainTerm(bought, LocalDate.of(2025, 12, 1), rule))
    }

    @Test fun `a sale spanning old and new lots splits its gain by term and sums to proceeds minus cost`() {
        val used = allocateFifo(listOf(a, b), Units.whole(15))
        val dated = used.map { DatedConsumption(it, if (it.lotId == "a") a.acquiredOn else b.acquiredOn) }
        // Proceeds ₹2,400 for 15 units: ₹160 a unit. Lot a: 10 units → ₹1,600 vs cost ₹1,000 (long). Lot b: 5 units → ₹800 vs ₹600 (short).
        val buckets = splitGain(2_400.rupees, dated, LocalDate.of(2026, 3, 1), rule)
        assertEquals(listOf(GainBucket(GainTerm.Short, 200.rupees), GainBucket(GainTerm.Long, 600.rupees)), buckets)
        assertEquals(2_400.rupees - (1_600.rupees), Paise(buckets.sumOf { it.gain.value }))
    }

    @Test fun `a loss is a negative bucket and a zero gain is omitted`() {
        val one = listOf(DatedConsumption(Consumption("a", Units.whole(10), 1_000.rupees), a.acquiredOn))
        assertEquals(listOf(GainBucket(GainTerm.Long, (-300).rupees)), splitGain(700.rupees, one, LocalDate.of(2026, 3, 1), rule))
        assertEquals(emptyList<GainBucket>(), splitGain(1_000.rupees, one, LocalDate.of(2026, 3, 1), rule))
    }

    @Test fun `uneven proceeds shares still add up exactly`() {
        val three = listOf(
            DatedConsumption(Consumption("p", Units.whole(1), Paise(0)), LocalDate.of(2025, 1, 1)),
            DatedConsumption(Consumption("q", Units.whole(1), Paise(0)), LocalDate.of(2025, 1, 1)),
            DatedConsumption(Consumption("r", Units.whole(1), Paise(0)), LocalDate.of(2025, 1, 1)),
        )
        assertEquals(Paise(100), splitGain(Paise(100), three, LocalDate.of(2026, 6, 1), rule).single().gain)
    }
}
