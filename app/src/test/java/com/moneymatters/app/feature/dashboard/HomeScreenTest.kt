package com.moneymatters.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.feature.books.SampleBooks
import com.moneymatters.app.feature.books.buildBooks
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1400dp-xxhdpi")
class HomeScreenTest {

    @get:Rule val compose = createComposeRule()

    private var opened: String? = null

    private fun show(state: HomeState, dark: Boolean = false) = compose.setContent {
        MoneyMattersTheme(darkTheme = dark) {
            // The app shell paints the canvas; do the same so the dark render is true to life.
            Box(Modifier.background(MoneyTheme.colors.canvas)) {
                HomeScreen(state, onOpenAccounts = { opened = "accounts" }, onOpenAccount = { opened = it }, onOpenTransactions = { opened = "transactions" }, onOpenTransaction = { opened = it })
            }
        }
    }

    @Test fun `shows net worth, the month so far, accounts and recent entries`() {
        show(SampleBooks.state.home)
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithText("Month so far").assertIsDisplayed()
        compose.onNodeWithText("+₹72,000").assertIsDisplayed()
        compose.onNodeWithText("1 draft waiting").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_light.png")
    }

    @Test fun `rows open their pages`() {
        show(SampleBooks.state.home)
        compose.onNodeWithText("HDFC Bank").performClick()
        assertEquals("hdfc-bank", opened)
        compose.onNodeWithText("1 draft waiting").performClick()
        assertEquals("transactions", opened)
        compose.onAllNodesWithText("View all")[0].performClick()
        assertEquals("accounts", opened)
    }

    @Test fun `an empty ledger says so instead of showing zeros everywhere`() {
        show(buildBooks(LedgerSnapshot(emptyList(), emptyList(), emptyList(), emptyList()), LocalDate.of(2026, 10, 20)).home)
        compose.onNodeWithText("No accounts yet.").assertIsDisplayed()
        compose.onNodeWithText("No transactions yet. Tap + to add one.").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_empty.png")
    }

    @Test fun `dark theme renders`() {
        show(SampleBooks.state.home, dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_dark.png")
    }
}
