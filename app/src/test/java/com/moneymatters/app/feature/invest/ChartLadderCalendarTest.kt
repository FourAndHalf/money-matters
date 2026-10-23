package com.moneymatters.app.feature.invest

import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.FixedDepositEntity
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.PendingAllotmentEntity
import com.moneymatters.app.core.invest.SipPlanEntity
import com.moneymatters.app.core.invest.SipSkipEntity
import com.moneymatters.app.core.invest.depositValuation
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.ui.components.CalendarMark
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartLadderCalendarTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun fd(id: String, principal: Long, maturity: String) = depositValuation(
        FixedDepositEntity(id, principal, 700, Compounding.Simple, "2026-01-01", maturity), Paise(principal), today,
    )

    @Test fun `deposits maturing in the same month share a rung, sized against the biggest`() {
        val ladder = maturityLadder(listOf(fd("a", 1_00_00_000, "2027-03-05"), fd("b", 50_00_000, "2027-03-20"), fd("c", 50_00_000, "2027-08-01")))
        assertEquals(listOf("Mar 2027", "Aug 2027"), ladder.map { it.label })
        assertEquals(listOf(2, 1), ladder.map { it.count })
        assertEquals(1f, ladder[0].fraction, 0.0001f)
        assertEquals(ladder[1].amount.value.toFloat() / ladder[0].amount.value, ladder[1].fraction, 0.0001f)
    }

    @Test fun `a deposit already past its date leads the ladder as Matured`() {
        val ladder = maturityLadder(listOf(fd("a", 1_00_00_000, "2027-03-05"), fd("b", 1_00_00_000, "2026-10-01")))
        assertEquals(listOf("Matured", "Mar 2027"), ladder.map { it.label })
    }

    @Test fun `one deposit makes no ladder`() {
        assertEquals(emptyList<MaturityRung>(), maturityLadder(listOf(fd("a", 1_00_00_000, "2027-03-05"))))
    }

    private val plan = SipPlanEntity("p1", "h1", 10_000_00, 5, "bank", "2026-08-05")
    private fun order(date: String, planId: String = "p1", cancelled: Boolean = false) =
        PendingAllotmentEntity("o$date", "h1", date, 10_000_00, "t$date", sipPlanId = planId, cancelTransactionId = if (cancelled) "x" else null)

    private fun snapshot(plans: List<SipPlanEntity>, orders: List<PendingAllotmentEntity> = emptyList(), skips: List<SipSkipEntity> = emptyList()) =
        InvestSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), pending = orders, sipPlans = plans, sipSkips = skips)

    @Test fun `past instalments show their state and future ones show as upcoming for a year`() {
        val snap = snapshot(listOf(plan), listOf(order("2026-08-05")), listOf(SipSkipEntity("p1", "2026-09-05")))
        val cal = sipCalendar(snap.sipPlans, snap, today)!!
        assertEquals(CalendarMark.Done, cal.marks[LocalDate.of(2026, 8, 5)])
        assertEquals(CalendarMark.Skipped, cal.marks[LocalDate.of(2026, 9, 5)])
        assertEquals(CalendarMark.Missed, cal.marks[LocalDate.of(2026, 10, 5)])
        assertEquals(CalendarMark.Upcoming, cal.marks[LocalDate.of(2026, 11, 5)])
        assertEquals(CalendarMark.Upcoming, cal.marks[LocalDate.of(2027, 10, 5)])
        assertNull(cal.marks[LocalDate.of(2027, 11, 5)])
        assertEquals(YearMonth.of(2026, 8), cal.first)
        assertEquals(YearMonth.of(2027, 10), cal.last)
    }

    @Test fun `a SIP that has ended shows nothing after its end date`() {
        val ended = plan.copy(endDate = "2026-10-10")
        val snap = snapshot(listOf(ended))
        val cal = sipCalendar(snap.sipPlans, snap, today)!!
        assertEquals(YearMonth.of(2026, 10), cal.last)
        assertNull(cal.marks[LocalDate.of(2026, 11, 5)])
    }

    @Test fun `where two SIPs share a day the one needing attention shows`() {
        val other = plan.copy(id = "p2")
        val snap = snapshot(listOf(plan, other), listOf(order("2026-10-05", "p1")))
        val cal = sipCalendar(snap.sipPlans, snap, today)!!
        // p1 paid on the 5th of October, p2 missed it.
        assertEquals(CalendarMark.Missed, cal.marks[LocalDate.of(2026, 10, 5)])
    }

    @Test fun `no plans means no calendar`() {
        assertNull(sipCalendar(emptyList(), snapshot(emptyList()), today))
    }
}
