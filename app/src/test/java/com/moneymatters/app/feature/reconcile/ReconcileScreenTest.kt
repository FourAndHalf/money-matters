package com.moneymatters.app.feature.reconcile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.reconcile.ReconcileLine
import com.moneymatters.app.core.reconcile.ReconcileView
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
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
@Config(sdk = [35], qualifiers = "w392dp-h1400dp-xxhdpi")
class ReconcileScreenTest {
    @get:Rule val compose = createComposeRule()

    private val view = ReconcileView(
        "bank", "HDFC Bank", isLiability = false, cleared = 40_000.rupees,
        lines = listOf(
            ReconcileLine("a", LocalDate.of(2026, 10, 1), "Salary", 10_000.rupees),
            ReconcileLine("b", LocalDate.of(2026, 10, 5), "Lunch", (-500).rupees),
        ),
    )

    private fun show(state: ReconcileScreenState, note: String = "", onFinish: () -> Unit = {}, onPost: () -> Unit = {}, onToggle: (String) -> Unit = {}) {
        compose.setContent { MoneyMattersTheme { ReconcileContent(state, note, null, {}, onToggle, {}, {}, onPost, onFinish, {}) } }
    }

    @Test fun `typed balances accept a minus for an overdrawn account`() {
        assertEquals(48_250.rupees, parseSignedRupees("48,250"))
        assertEquals(Paise(-12_345), parseSignedRupees("-123.45"))
        assertEquals(Paise(-12_345), parseSignedRupees("−123.45"))
        assertNull(parseSignedRupees("abc"))
        assertNull(parseSignedRupees(""))
    }

    @Test fun `the state is balanced only when the statement, cleared and ticked entries agree`() {
        val s = ReconcileScreenState(view, "49500", setOf("a", "b"))
        assertTrue(s.matches)
        assertTrue(s.canFinish)
        assertFalse(ReconcileScreenState(view, "49500", setOf("a")).matches)
        assertFalse(ReconcileScreenState(view, "", setOf("a", "b")).canFinish) // no statement yet
        assertFalse(ReconcileScreenState(view.copy(lines = emptyList()), "40000", emptySet()).canFinish) // balanced but nothing to reconcile
        assertEquals(9_500.rupees, s.tickedTotal)
    }

    @Test fun `the screen lists the entries and what is already reconciled`() {
        show(ReconcileScreenState(view, "", emptySet()))
        compose.onNodeWithText("Salary").assertIsDisplayed()
        compose.onNodeWithText("Lunch").assertIsDisplayed()
        compose.onNodeWithText("Already reconciled: ₹40,000.00").assertIsDisplayed()
        compose.onNodeWithText("0 of 2 ticked").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Difference").fetchSemanticsNodes().size) // nothing until a statement balance is typed
    }

    @Test fun `a matching statement shows a tick and enables finishing`() {
        var finished = false
        show(ReconcileScreenState(view, "49500", setOf("a", "b")), onFinish = { finished = true })
        compose.onNodeWithText("✓ Matches").assertIsDisplayed()
        compose.onNodeWithText("Reconcile 2 entries").performClick()
        assertTrue(finished)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/reconcile_match.png")
    }

    @Test fun `a difference is shown and finishing is blocked`() {
        var finished = false
        show(ReconcileScreenState(view, "49700", setOf("a", "b")), onFinish = { finished = true })
        compose.onNodeWithText("₹200.00 out").assertIsDisplayed()
        compose.onNodeWithText("Book to Unaccounted").assertIsDisplayed()
        compose.onNodeWithText("Reconcile 2 entries").performClick()
        assertFalse(finished)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/reconcile_difference.png")
    }

    @Test fun `booking the difference needs a note`() {
        var posted = false
        show(ReconcileScreenState(view, "49700", setOf("a", "b")), note = "", onPost = { posted = true })
        compose.onNodeWithText("Book to Unaccounted").performClick()
        assertFalse(posted)
    }

    @Test fun `booking the difference with a note posts it`() {
        var posted = false
        show(ReconcileScreenState(view, "49700", setOf("a", "b")), note = "Bank charge", onPost = { posted = true })
        compose.onNodeWithText("Book to Unaccounted").performClick()
        assertTrue(posted)
    }

    @Test fun `tapping an entry ticks it`() {
        var toggled: String? = null
        show(ReconcileScreenState(view, "", emptySet()), onToggle = { toggled = it })
        compose.onNodeWithText("Lunch").performClick()
        assertEquals("b", toggled)
    }

    @Test fun `a card asks for the amount owed`() {
        show(ReconcileScreenState(view.copy(isLiability = true), "", emptySet()))
        compose.onNodeWithText("Amount owed on the statement (₹)").assertIsDisplayed()
    }

    @Test fun `with nothing waiting the screen says so`() {
        show(ReconcileScreenState(view.copy(lines = emptyList()), "", emptySet()))
        compose.onNodeWithText("Nothing is waiting to be reconciled.").assertIsDisplayed()
    }
}
