package com.moneymatters.app.feature.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun txn(id: String, amount: Paise, status: TxnStatus = TxnStatus.Posted, transfer: Boolean = false, payee: String = "Payee $id") =
    TransactionSummary(id, "P", payee, "Other", "HDFC Bank", amount, status, emptyList(), isTransfer = transfer)

private fun day(date: String, vararg items: TransactionSummary) = DayGroup(date, items.toList(), LocalDate.parse(date))

class TransactionViewsTest {
    @Test fun `a day's flow splits money out from money in and ignores transfers and drafts`() {
        val g = day("2026-10-12", txn("a", (-500).rupees), txn("b", 2_000.rupees), txn("c", 700.rupees, transfer = true), txn("d", (-999).rupees, status = TxnStatus.Draft))
        val f = g.flow()
        assertEquals(500.rupees, f.out)
        assertEquals(2_000.rupees, f.into)
        assertEquals(1_500.rupees, f.net)
        assertEquals(4, f.count) // every entry still counts as activity
    }

    @Test fun `weeks run Monday to Sunday and newest come first`() {
        val rows = weeklyRows(
            listOf(
                day("2026-10-28", txn("a", (-100).rupees)), // Wed, week of 26 Oct
                day("2026-10-26", txn("b", (-200).rupees)), // Mon, same week
                day("2026-10-25", txn("c", (-50).rupees)), // Sun, week of 19 Oct
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 19)), rows.map { it.start })
        assertEquals(LocalDate.of(2026, 11, 1), rows.first().end)
        assertEquals(300.rupees, rows.first().flow.out)
        assertEquals(50.rupees, rows.last().flow.out)
    }

    @Test fun `months group by calendar month`() {
        val rows = monthlyRows(listOf(day("2026-10-31", txn("a", (-100).rupees)), day("2026-11-01", txn("b", 300.rupees)), day("2026-11-15", txn("c", (-50).rupees))))
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)), rows.map { it.start })
        assertEquals(LocalDate.of(2026, 11, 30), rows.first().end)
        assertEquals(250.rupees, rows.first().flow.net)
        assertEquals(2, rows.first().flow.count)
    }

    @Test fun `the totals add up across the rows shown`() {
        val rows = monthlyRows(listOf(day("2026-10-05", txn("a", (-100).rupees)), day("2026-11-05", txn("b", 900.rupees))))
        val t = totalFlow(rows)
        assertEquals(100.rupees, t.out)
        assertEquals(900.rupees, t.into)
        assertEquals(800.rupees, t.net)
    }

    @Test fun `days without a date are left out of the date views`() {
        val undated = DayGroup("Today", listOf(txn("a", (-100).rupees)))
        assertTrue(weeklyRows(listOf(undated)).isEmpty())
        assertTrue(dayNets(listOf(undated)).isEmpty())
    }

    @Test fun `day nets are keyed by date`() {
        val nets = dayNets(listOf(day("2026-10-12", txn("a", (-500).rupees), txn("b", 200.rupees))))
        assertEquals(mapOf(LocalDate.of(2026, 10, 12) to (-300).rupees), nets)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2400dp-xxhdpi")
class TransactionViewsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 29)
    private val state = TransactionsState(
        groups = listOf(
            day("2026-10-28", txn("a", (-486).rupees, payee = "Swiggy")),
            day("2026-10-12", txn("b", 72_000.rupees, payee = "Salary"), txn("c", (-1_200).rupees, payee = "Dinner")),
            day("2026-09-30", txn("d", (-300).rupees, payee = "Chai")),
        ),
        accounts = listOf("HDFC Bank"),
    )

    private fun show() = compose.setContent { MoneyMattersTheme { TransactionsScreen(state, onOpenTransaction = {}, today = today) } }

    @Test fun `the list is still the default view`() {
        show()
        compose.onNodeWithText("Swiggy").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Tap a day to see its entries.").fetchSemanticsNodes().size)
    }

    @Test fun `the calendar shows the month with each day's net, and a tapped day lists its entries`() {
        show()
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.onNodeWithText("Tap a day to see its entries.").assertIsDisplayed()
        compose.onNodeWithContentDescription("12, net ₹70,800", substring = true).performClick()
        compose.onNodeWithText("Salary").assertIsDisplayed()
        compose.onNodeWithText("Dinner").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transactions_calendar.png")
    }

    @Test fun `a day with nothing says so, and months can be paged back to older entries`() {
        show()
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithContentDescription("5").performClick()
        compose.onNodeWithText("Nothing on 5 October 2026.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("30, net −₹300", substring = true).performClick()
        compose.onNodeWithText("Chai").assertIsDisplayed()
    }

    @Test fun `the calendar can't page past the current month, or before the first entry`() {
        show()
        compose.onNodeWithText("Calendar").performClick()
        assertEquals(0, compose.onAllNodesWithContentDescriptionCount("Next month"))
        compose.onNodeWithContentDescription("Previous month").performClick()
        assertEquals(0, compose.onAllNodesWithContentDescriptionCount("Previous month")) // September holds the oldest entry
    }

    @Test fun `weekly totals list each week with money out and in`() {
        show()
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("26 Oct – 1 Nov").assertIsDisplayed()
        compose.onNodeWithText("Out ₹486").assertIsDisplayed()
        compose.onNodeWithText("12 Oct – 18 Oct").assertIsDisplayed()
        compose.onNodeWithText("In ₹72,000").assertIsDisplayed()
    }

    @Test fun `monthly totals list each month with a summary on top`() {
        show()
        compose.onNodeWithText("Monthly").performClick()
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithText("Out ₹1,986 · In ₹72,000 · Net +₹70,014").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transactions_monthly.png")
    }

    @Test fun `filters apply to every view`() {
        show()
        compose.onNodeWithText("Monthly").performClick()
        compose.onNodeWithTag("status-filter-Draft").performClick()
        assertEquals(0, compose.onAllNodesWithText("October 2026").fetchSemanticsNodes().size)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionCount(text: String) =
        onAllNodesWithContentDescription(text).fetchSemanticsNodes().size
}
