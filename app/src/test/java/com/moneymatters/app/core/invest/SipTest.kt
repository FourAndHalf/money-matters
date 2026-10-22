package com.moneymatters.app.core.invest

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SipTest {

    private fun plan(day: Int = 5, start: String = "2026-01-10", end: String? = null) =
        SipPlanEntity("p1", "h1", 10_000_00, day, "bank", start, end)

    private fun order(due: String, cancelled: Boolean = false, planId: String = "p1") =
        PendingAllotmentEntity("o-$due", "h1", due, 10_000_00, "t-$due", cancelTransactionId = if (cancelled) "c" else null, sipPlanId = planId)

    @Test fun `due dates fall on the chosen day each month, the first on or after the start`() {
        val dates = sipDueDates(LocalDate.of(2026, 1, 10), 5, LocalDate.of(2026, 4, 30))
        assertEquals(listOf("2026-02-05", "2026-03-05", "2026-04-05"), dates.map { it.toString() }) // 5 Jan is before the 10th start
        assertEquals(listOf("2026-01-10"), sipDueDates(LocalDate.of(2026, 1, 10), 10, LocalDate.of(2026, 1, 31)).map { it.toString() })
    }

    @Test fun `a 31st becomes the last day of shorter months, leap years included`() {
        val dates = sipDueDates(LocalDate.of(2027, 12, 1), 31, LocalDate.of(2028, 4, 30))
        assertEquals(listOf("2027-12-31", "2028-01-31", "2028-02-29", "2028-03-31", "2028-04-30"), dates.map { it.toString() })
    }

    @Test fun `the through date is included and a bad day is refused`() {
        assertEquals(1, sipDueDates(LocalDate.of(2026, 1, 1), 5, LocalDate.of(2026, 1, 5)).size)
        assertEquals(0, sipDueDates(LocalDate.of(2026, 1, 1), 5, LocalDate.of(2026, 1, 4)).size)
        assertThrows(IllegalArgumentException::class.java) { sipDueDates(LocalDate.of(2026, 1, 1), 0, LocalDate.of(2026, 3, 1)) }
        assertThrows(IllegalArgumentException::class.java) { sipDueDates(LocalDate.of(2026, 1, 1), 32, LocalDate.of(2026, 3, 1)) }
    }

    @Test fun `instalments are paid, skipped, due or missed, with a three day grace`() {
        val states = sipInstalments(
            plan(start = "2026-01-01"),
            orders = listOf(order("2026-01-05")),
            skips = listOf(SipSkipEntity("p1", "2026-02-05")),
            today = LocalDate.of(2026, 10, 7),
        ).associate { it.due.toString() to it.state }
        assertEquals(InstalmentState.Paid, states.getValue("2026-01-05"))
        assertEquals(InstalmentState.Skipped, states.getValue("2026-02-05"))
        assertEquals(InstalmentState.Missed, states.getValue("2026-03-05"))
        assertEquals(InstalmentState.Missed, states.getValue("2026-09-05"))
        assertEquals(InstalmentState.Due, states.getValue("2026-10-05")) // 2 days overdue: inside the grace period
    }

    @Test fun `the grace period ends after three days`() {
        val p = plan(start = "2026-10-01")
        assertEquals(InstalmentState.Due, sipInstalments(p, emptyList(), emptyList(), LocalDate.of(2026, 10, 8)).single().state)
        assertEquals(InstalmentState.Missed, sipInstalments(p, emptyList(), emptyList(), LocalDate.of(2026, 10, 9)).single().state)
    }

    @Test fun `a cancelled order leaves its instalment due again, and orders of other plans do not count`() {
        val p = plan(start = "2026-10-01")
        val today = LocalDate.of(2026, 10, 6)
        assertEquals(InstalmentState.Due, sipInstalments(p, listOf(order("2026-10-05", cancelled = true)), emptyList(), today).single().state)
        assertEquals(InstalmentState.Due, sipInstalments(p, listOf(order("2026-10-05", planId = "other")), emptyList(), today).single().state)
        assertEquals(InstalmentState.Paid, sipInstalments(p, listOf(order("2026-10-05")), emptyList(), today).single().state)
    }

    @Test fun `a paid instalment wins over a skip recorded earlier`() {
        val p = plan(start = "2026-10-01")
        val state = sipInstalments(p, listOf(order("2026-10-05")), listOf(SipSkipEntity("p1", "2026-10-05")), LocalDate.of(2026, 10, 6)).single().state
        assertEquals(InstalmentState.Paid, state)
    }

    @Test fun `a plan stops proposing instalments after its end date`() {
        val p = plan(start = "2026-01-01", end = "2026-03-31")
        val due = sipInstalments(p, emptyList(), emptyList(), LocalDate.of(2026, 10, 1)).map { it.due.toString() }
        assertEquals(listOf("2026-01-05", "2026-02-05", "2026-03-05"), due)
        assertNull(nextSipDue(p, LocalDate.of(2026, 3, 10)))
    }

    @Test fun `the next due date is the first one after today`() {
        val p = plan(start = "2026-01-01")
        assertEquals(LocalDate.of(2026, 10, 5), nextSipDue(p, LocalDate.of(2026, 9, 20)))
        assertEquals(LocalDate.of(2026, 11, 5), nextSipDue(p, LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 1, 5), nextSipDue(p, LocalDate.of(2025, 12, 1)))
    }
}
