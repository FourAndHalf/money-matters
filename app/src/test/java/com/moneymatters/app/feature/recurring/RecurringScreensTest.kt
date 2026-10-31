package com.moneymatters.app.feature.recurring

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.recurring.Frequency
import com.moneymatters.app.core.recurring.RecurringRuleEntity
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1400dp-xxhdpi")
class RecurringScreensTest {
    @get:Rule val compose = createComposeRule()

    private val bank = AccountEntity("bank", "HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
    private val rent = AccountEntity("rent", "Expenses:Rent", AccountType.Expense)
    private val salary = AccountEntity("salary", "Income:Salary", AccountType.Income)
    private val accounts = listOf(bank, rent, salary)
    private val start = LocalDate.of(2026, 10, 5)

    private val rentRule = RecurringRuleEntity("r1", "Rent", "bank", "rent", 25_000_00, Frequency.Monthly, "2026-10-05", null, autoPost = true, lastGeneratedOn = "2026-10-05")

    @Test fun `the schedule shows each rule with its amount, next date and accounts`() {
        val rows = buildRows(listOf(rentRule), accounts)
        compose.setContent { MoneyMattersTheme { RecurringListContent(rows, null, {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("₹25,000.00").assertIsDisplayed()
        compose.onNodeWithText("Monthly · next 5 Nov 2026 · HDFC Bank → Expenses:Rent").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/recurring_list.png")
    }

    @Test fun `an empty schedule explains what to add`() {
        compose.setContent { MoneyMattersTheme { RecurringListContent(emptyList(), null, {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Add salary, rent, EMIs and subscriptions once, and they are booked for you on their dates.").assertIsDisplayed()
    }

    @Test fun `paused, draft and broken rules are labelled`() {
        val rows = buildRows(
            listOf(
                rentRule.copy(id = "a", name = "Paused rule", isActive = false),
                rentRule.copy(id = "b", name = "Draft rule", autoPost = false),
                rentRule.copy(id = "c", name = "Broken rule", toAccountId = "gone"),
            ),
            accounts,
        )
        compose.setContent { MoneyMattersTheme { RecurringListContent(rows, null, {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Paused").assertIsDisplayed()
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Account missing").assertIsDisplayed()
    }

    @Test fun `a rule that ended says so`() {
        val rows = buildRows(listOf(rentRule.copy(endDate = "2026-10-31")), accounts)
        assertEquals(null, rows.single().next)
        compose.setContent { MoneyMattersTheme { RecurringListContent(rows, null, {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Monthly · ended · HDFC Bank → Expenses:Rent").assertIsDisplayed()
    }

    @Test fun `the notice from the last run is shown`() {
        compose.setContent { MoneyMattersTheme { RecurringListContent(emptyList(), "3 posted", {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Just booked: 3 posted").assertIsDisplayed()
    }

    @Test fun `tapping a rule opens it`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { RecurringListContent(buildRows(listOf(rentRule), accounts), null, {}, { opened = it }, { _, _ -> }, {}) } }
        compose.onNodeWithText("Rent").performClick()
        assertEquals("r1", opened)
    }

    @Test fun `a new rule can't be saved until it is complete`() {
        val empty = RecurringForm(start)
        assertFalse(empty.canSave)
        val complete = empty.copy(name = "Rent", amountText = "25000", fromId = "bank", toId = "rent")
        assertTrue(complete.canSave)
        assertFalse(complete.copy(toId = "bank").canSave) // the same account on both sides
        assertFalse(complete.copy(amountText = "0").canSave)
        assertFalse(complete.copy(hasEnd = true, end = start.minusDays(1)).canSave)
        assertEquals(25_000.rupees, complete.amount)
    }

    @Test fun `the form offers the right accounts on each side and saves a balanced rule`() {
        assertEquals(listOf("HDFC Bank", "Income:Salary"), fromChoices(accounts).map { it.name })
        assertEquals(listOf("Expenses:Rent", "HDFC Bank"), toChoices(accounts).map { it.name })

        val rule = RecurringForm(start, name = " Rent ", amountText = "25000.50", fromId = "bank", toId = "rent").toRule()
        assertEquals("Rent", rule.name)
        assertEquals(2_500_050L, rule.amountPaise)
        assertEquals("2026-10-05", rule.startDate)
        assertEquals(null, rule.endDate)
    }

    @Test fun `editing keeps the rule's tags and shows its amount back as typed`() {
        val tagged = rentRule.copy(decision = com.moneymatters.app.core.ledger.DecisionKind.Need, paymentMethod = com.moneymatters.app.core.ledger.PaymentMethod.Upi, amountPaise = 2_500_050)
        val form = RecurringForm.from(tagged)
        assertEquals("25000.50", form.amountText)
        val back = form.toRule()
        assertEquals(tagged.decision, back.decision)
        assertEquals(tagged.paymentMethod, back.paymentMethod)
        assertEquals(tagged.amountPaise, back.amountPaise)
    }

    @Test fun `the form shows its fields, and editing adds delete and pause`() {
        compose.setContent { MoneyMattersTheme { RecurringFormContent(RecurringForm(start), accounts, null, {}, {}, {}, {}) } }
        compose.onNodeWithText("Add rule").assertIsDisplayed()
        compose.onNodeWithText("Name (becomes the payee)").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Delete rule").fetchSemanticsNodes().size)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/recurring_form.png")
    }

    @Test fun `editing shows delete and the active switch`() {
        compose.setContent { MoneyMattersTheme { RecurringFormContent(RecurringForm.from(rentRule), accounts, null, {}, {}, {}, {}) } }
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Delete rule").assertIsDisplayed()
    }

    @Test fun `saving is blocked while the form is incomplete`() {
        var saved = false
        compose.setContent { MoneyMattersTheme { RecurringFormContent(RecurringForm(start), accounts, null, {}, { saved = true }, {}, {}) } }
        compose.onNodeWithText("Add rule").performClick()
        assertFalse(saved)
    }

    @Test fun `saving a complete form calls save`() {
        var saved = false
        val form = RecurringForm(start, name = "Rent", amountText = "25000", fromId = "bank", toId = "rent")
        compose.setContent { MoneyMattersTheme { RecurringFormContent(form, accounts, null, {}, { saved = true }, {}, {}) } }
        compose.onNodeWithText("Add rule").performClick()
        assertTrue(saved)
    }
}
