package com.moneymatters.app.feature.close

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.close.CheckFix
import com.moneymatters.app.core.close.CheckItem
import com.moneymatters.app.core.close.CloseRepository
import com.moneymatters.app.core.close.Period
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1000dp-xxhdpi")
class CloseScreenTest {

    @get:Rule val compose = createComposeRule()

    private val october = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))

    @Test fun `the checklist shows what is left and the close button waits for the typed name`() {
        val state = CloseState(
            period = october,
            items = listOf(CheckItem("No drafts", true, "Every entry is booked"), CheckItem("Entries reconciled", false, "3 entries to reconcile", CheckFix.Reconcile)),
            balances = emptyList(), canClose = false,
        )
        var reconciled = false
        compose.setContent {
            MoneyMattersTheme {
                CloseScreen(state, "", null, onTypedName = {}, onBack = {}, onConfirm = {}, onConfirmAll = {}, onReconcile = { reconciled = true }, onPostInterest = {}, onOpenPrices = {}, onClose = {})
            }
        }
        compose.onNodeWithText("3 entries to reconcile").assertIsDisplayed()
        compose.onNodeWithText("Finish the checklist first").assertIsDisplayed()
        compose.onNodeWithText("Reconcile").performClick()
        assertTrue(reconciled)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/close_checklist_light.png")
    }

    @Test fun `with everything done only the typed month name enables the close`() {
        val state = CloseState(october, listOf(CheckItem("No drafts", true, "Every entry is booked")), canClose = true)
        compose.setContent {
            MoneyMattersTheme {
                CloseScreen(state, "October 2026", null, onTypedName = {}, onBack = {}, onConfirm = {}, onConfirmAll = {}, onReconcile = {}, onPostInterest = {}, onOpenPrices = {}, onClose = {})
            }
        }
        // The heading and the button both carry the name; the hint text only shows while the button is off.
        compose.onAllNodesWithText("Close October 2026").assertCountEquals(2)
    }

    @Test fun `closed months are listed with their hash and a changed one is flagged`() {
        val state = CloseState(
            null,
            history = listOf(ClosedRow("October 2026", "1 Oct 2026 – 31 Oct 2026", "ab12cd34ef56", true, null), ClosedRow("September 2026", "1 Sep 2026 – 30 Sep 2026", "00aa11bb22cc", false, "Entries in this month have changed since it was closed")),
            nextLabel = "October 2026 is closed",
        )
        compose.setContent {
            MoneyMattersTheme {
                CloseScreen(state, "", null, onTypedName = {}, onBack = {}, onConfirm = {}, onConfirmAll = {}, onReconcile = {}, onPostInterest = {}, onOpenPrices = {}, onClose = {})
            }
        }
        compose.onNodeWithText("✓ Intact").assertIsDisplayed()
        compose.onNodeWithText("✕ Changed").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/close_history_light.png")
    }

    @Test fun `a month becomes closable once balances are confirmed and entries reconciled, and is gone once closed`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val ledger = LedgerRepository(db)
        val invest = InvestRepository(db, ledger, rules)
        val close = CloseRepository(db, ledger)
        val today = LocalDate.of(2026, 11, 3)
        runBlocking {
            val bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            val food = ledger.createAccount("Food", AccountType.Expense)
            ledger.postTransaction(LocalDate.of(2026, 10, 5), "Lunch", listOf(PostingLine(food, debit = 200.rupees), PostingLine(bank, credit = 200.rupees)))
        }
        fun built(confirmed: Set<String>) = runBlocking { buildClose(ledger.snapshot().first(), invest.snapshot().first(), today, 1, confirmed) }
        val bankId = built(emptySet()).balances.single().line.accountId
        assertFalse(built(setOf(bankId)).canClose)
        runBlocking { close.reconcileAll(october) }
        assertFalse(built(emptySet()).canClose)
        assertTrue(built(setOf(bankId)).canClose)
        assertEquals("October 2026", built(setOf(bankId)).period!!.label)
        runBlocking { close.close(october, "October 2026", today) }
        val after = built(emptySet())
        assertNull(after.period)
        assertEquals("October 2026", after.history.single().label)
        assertTrue(after.history.single().ok)
    }
}
