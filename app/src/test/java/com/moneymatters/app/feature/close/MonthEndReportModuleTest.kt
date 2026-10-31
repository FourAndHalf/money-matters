package com.moneymatters.app.feature.close

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.close.BudgetResult
import com.moneymatters.app.core.close.CategoryChange
import com.moneymatters.app.core.close.EntryCounts
import com.moneymatters.app.core.close.MonthEndReport
import com.moneymatters.app.core.close.Period
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h3200dp-xxhdpi")
class MonthEndReportModuleTest {
    @get:Rule val compose = createComposeRule()

    private val report = MonthEndReport(
        period = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), income = 60_500.rupees, expenses = 29_000.rupees,
        savingsRateBps = 5_206, netInvested = 10_000.rupees, investmentRateBps = 1_652, previousExpenses = 26_000.rupees, averageExpenses = Paise(2_633_333),
        movers = listOf(CategoryChange("Expenses:Food", 9_000.rupees, 6_000.rupees, 6_333.rupees)),
        needs = 20_000.rupees, wants = 9_000.rupees, investing = Paise.Zero, untagged = Paise.Zero, planned = 20_000.rupees, unplanned = 9_000.rupees,
        budgets = listOf(BudgetResult("Food", 8_000.rupees, 9_000.rupees)), entries = EntryCounts(5, 0, 0, 1, 0),
        netWorthStart = 171_000.rupees, netWorthEnd = 202_500.rupees,
    )

    @Test fun `the report shows income, the two rates and net worth`() {
        compose.setContent { MoneyMattersTheme { MonthEndReportModule(report) } }
        compose.onNodeWithText("Month-end report").assertIsDisplayed()
        compose.onNodeWithText("52.0%").assertIsDisplayed()
        compose.onNodeWithText("16.5%").assertIsDisplayed()
        compose.onNodeWithText("₹1,71,000.00").assertIsDisplayed()
        compose.onNodeWithText("₹2,02,500.00").assertIsDisplayed()
        compose.onNodeWithText("+₹31,500.00").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/month_end_report.png")
    }

    @Test fun `the report says what moved against last month and what went over budget`() {
        compose.setContent { MoneyMattersTheme { MonthEndReportModule(report) } }
        compose.onNodeWithText("₹9,000 (+₹3,000 on last month)").assertIsDisplayed()
        compose.onNodeWithText("₹9,000 of ₹8,000 · over").assertIsDisplayed()
        compose.onNodeWithText("₹26,333.33").assertIsDisplayed()
    }

    @Test fun `with no income the rates say so, and with no history the average says so`() {
        compose.setContent { MoneyMattersTheme { MonthEndReportModule(report.copy(savingsRateBps = null, investmentRateBps = null, averageExpenses = Paise.Zero)) } }
        compose.onNodeWithText("Not enough history").assertIsDisplayed()
    }
}
