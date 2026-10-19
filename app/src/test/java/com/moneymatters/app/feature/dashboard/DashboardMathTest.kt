package com.moneymatters.app.feature.dashboard

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardMathTest {

    @Test fun `daily allowance rounds down to the rupee`() {
        assertEquals(1_416.rupees, dailyAllowance(18_420.rupees, 13))
        assertEquals(1_000.rupees, dailyAllowance(10_000.rupees, 10))
    }

    @Test fun `daily allowance is zero when leeway is gone or payday is here`() {
        assertEquals(Paise.Zero, dailyAllowance((-500).rupees, 10))
        assertEquals(Paise.Zero, dailyAllowance(Paise.Zero, 10))
        assertEquals(Paise.Zero, dailyAllowance(5_000.rupees, 0))
    }

    @Test fun `budget pace compares share used with share of period elapsed`() {
        val elapsed = 18 / 31f // ~58%
        assertEquals(BudgetPace.OnPace, budgetPace(2_150.rupees, 4_000.rupees, elapsed)) // 54%
        assertEquals(BudgetPace.Watch, budgetPace(6_240.rupees, 9_000.rupees, elapsed)) // 69%
        assertEquals(BudgetPace.OverPace, budgetPace(4_880.rupees, 5_000.rupees, elapsed)) // 98%
    }

    @Test fun `any overspend is over pace even at period end`() {
        assertEquals(BudgetPace.OverPace, budgetPace(5_001.rupees, 5_000.rupees, 1f))
        assertEquals(BudgetPace.OnPace, budgetPace(5_000.rupees, 5_000.rupees, 1f))
    }

    @Test fun `zero cap budget is over as soon as anything is spent`() {
        assertEquals(BudgetPace.OnPace, budgetPace(Paise.Zero, Paise.Zero, 0.5f))
        assertEquals(BudgetPace.OverPace, budgetPace(1.rupees, Paise.Zero, 0.5f))
    }

    @Test fun `required SIP for one crore in 37 months at 10 percent`() {
        val required = requiredMonthlyContribution(1_00_00_000.rupees, 60_900.rupees, 37, 0.10)
        assertEquals(2_31_519.rupees, required)
    }

    @Test fun `following the required SIP reaches the target`() {
        val target = 1_00_00_000.rupees
        val sip = requiredMonthlyContribution(target, 60_900.rupees, 37, 0.10)
        assertTrue(projectedValue(60_900.rupees, sip, 37, 0.10) >= target)
    }

    @Test fun `projection at current SIP`() {
        val projected = projectedValue(60_900.rupees, 5_000.rupees, 37, 0.10)
        assertEquals(Paise(2_95_905_14), projected)
    }

    @Test fun `no contribution needed when growth alone reaches the target`() {
        assertEquals(Paise.Zero, requiredMonthlyContribution(1_000.rupees, 2_000.rupees, 12, 0.10))
    }

    @Test fun `zero return projection is plain sum`() {
        assertEquals(1_60_900.rupees, projectedValue(60_900.rupees, 10_000.rupees, 10, 0.0))
        assertEquals(10_000.rupees, requiredMonthlyContribution(1_60_900.rupees, 60_900.rupees, 10, 0.0))
    }
}
