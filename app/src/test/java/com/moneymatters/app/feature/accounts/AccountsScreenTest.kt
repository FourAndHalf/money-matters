package com.moneymatters.app.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2600dp-xxhdpi")
class AccountsScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `shows net worth and every account group`() {
        compose.setContent { MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = {}) } }
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithText("Bank & cash").assertIsDisplayed()
        compose.onNodeWithText("Credit cards").assertIsDisplayed()
        compose.onNodeWithText("Loans").assertIsDisplayed()
        compose.onNodeWithText("Earmarks").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/accounts_light.png")
    }

    @Test fun `dark theme renders`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { AccountsScreen(SampleAccounts.state, onOpenAccount = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/accounts_dark.png")
    }

    @Test fun `tapping an account row opens its ledger`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = { opened = it }) } }
        compose.onNodeWithText("HDFC Bank").performClick()
        assertEquals("hdfc-bank", opened)
    }

    @Test fun `net worth is assets minus liabilities`() {
        val s = SampleAccounts.state
        assertEquals(s.assetsTotal - s.liabilitiesTotal, s.netWorth)
    }
}
