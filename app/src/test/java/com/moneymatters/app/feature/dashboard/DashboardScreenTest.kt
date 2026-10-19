package com.moneymatters.app.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.feature.accounts.SampleAccounts
import com.moneymatters.app.feature.accounts.SampleLedgers
import com.moneymatters.app.ui.MoneyMattersApp
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the dashboard on the JVM and checks the card stack's behaviour.
 * `./gradlew recordRoborazziDebug` writes the images to app/build/outputs/roborazzi for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h852dp-xxhdpi")
class DashboardScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun app(dark: Boolean = false) = compose.setContent {
        MoneyMattersTheme(darkTheme = dark) { MoneyMattersApp(SampleDashboard.state, SampleAccounts.state, SampleLedgers.byAccountId) }
    }

    @Test fun `first screen shows leeway and allowance`() {
        app()
        compose.onNodeWithText("Safe to spend").assertIsDisplayed()
        compose.onNodeWithText("₹1,416 / day").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/dashboard_light.png")
    }

    @Test fun `dark theme renders`() {
        app(dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/dashboard_dark.png")
    }

    @Test fun `swiping the stack left brings the next account forward`() {
        app()
        compose.onNodeWithText("Swipe the stack · 1 / 5").assertExists()
        compose.onNodeWithContentDescription("Account cards", substring = true).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Swipe the stack · 2 / 5").assertExists()
        compose.onNodeWithText("ICICI Rewards · credit").assertExists()
    }

    @Test fun `swiping right from the first account wraps to the last`() {
        app()
        compose.onNodeWithContentDescription("Account cards", substring = true).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Swipe the stack · 5 / 5").assertExists()
        compose.onNodeWithText("Stocks & mutual funds").assertExists()
    }
}

/** Full-length render of every dashboard module, for visual review against the mockup. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h4800dp-xxhdpi")
class DashboardFullPageTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `full page light`() {
        compose.setContent { MoneyMattersTheme(darkTheme = false) { MoneyMattersApp(SampleDashboard.state, SampleAccounts.state, SampleLedgers.byAccountId) } }
        compose.onNodeWithText("Needs attention").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/dashboard_full_light.png")
    }

    @Test fun `full page dark`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { MoneyMattersApp(SampleDashboard.state, SampleAccounts.state, SampleLedgers.byAccountId) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/dashboard_full_dark.png")
    }
}
