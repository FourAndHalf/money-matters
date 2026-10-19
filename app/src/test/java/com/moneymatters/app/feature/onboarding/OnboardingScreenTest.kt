package com.moneymatters.app.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
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
@Config(sdk = [35], qualifiers = "w392dp-h851dp-xxhdpi")
class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 20)
    private val wizard = OnboardingWizard()
    private var saved: OnboardingForm? = null
    private var finished = false

    private fun show(dark: Boolean = false, onSave: suspend (OnboardingForm) -> Unit = { saved = it }) {
        compose.setContent {
            MoneyMattersTheme(darkTheme = dark) {
                OnboardingScreen(onFinished = { finished = true }, wizard = wizard, onSave = onSave, today = { today })
            }
        }
    }

    private fun next() = compose.onNodeWithText("Next").performClick()
    private fun snap(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_$name.png")

    private fun fillToGoals() {
        next()
        compose.onNodeWithTag("dob-field").performTextInput("15081998")
        compose.onNodeWithText("15/08/1998").assertIsDisplayed()
        snap("profile")
        next()
        compose.onNodeWithTag("passcode-field").performTextInput("482910")
        compose.onNodeWithTag("passcode-confirm-field").performTextInput("482910")
        snap("passcode")
        next()
        compose.onNodeWithTag("account-name-field").performTextInput("HDFC Bank")
        compose.onNodeWithTag("opening-balance-field").performTextInput("1,84,250.50")
        snap("accounts")
        next()
        compose.onNodeWithTag("income-field").performTextInput("92000")
        next()
        snap("categories")
        next()
        snap("goals")
    }

    @Test fun `walks every step and submits the whole form once, at the end`() {
        show()
        snap("welcome")
        fillToGoals()
        assertNull("nothing is saved before the last step", saved)
        compose.onNodeWithText("Finish setup").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("You're all set").assertIsDisplayed()
        snap("done")

        val form = saved!!
        assertEquals("15081998", form.profile.dateOfBirthDigits)
        assertEquals("482910", form.passcode)
        assertEquals(listOf(NewAccountDraft("HDFC Bank", openingBalance = "1,84,250.50")), form.accounts)
        assertEquals("92000", form.income.monthlyTakeHome)
        assertEquals("", wizard.form.passcode) // cleared from memory once saved

        // No way back once committed: going back and forward again would post twice.
        assertEquals(0, compose.onAllNodesWithText("Back").fetchSemanticsNodes().size)
        assertFalse(finished)
        compose.onNodeWithText("Get started").performClick()
        assertTrue(finished)
    }

    @Test fun `next does nothing until the step is valid`() {
        show()
        next()
        next()
        assertEquals(OnboardingStep.Profile, wizard.step)
        compose.onNodeWithTag("dob-field").performTextInput("31022000")
        compose.onNodeWithText("Not a valid past date").assertIsDisplayed()
        next()
        assertEquals(OnboardingStep.Profile, wizard.step)
    }

    @Test fun `mismatched passcodes are flagged and block the step`() {
        show()
        next()
        compose.onNodeWithTag("dob-field").performTextInput("15081998")
        next()
        compose.onNodeWithTag("passcode-field").performTextInput("1234")
        compose.onNodeWithTag("passcode-confirm-field").performTextInput("1243")
        compose.onNodeWithText("Doesn't match").assertIsDisplayed()
        next()
        assertEquals(OnboardingStep.Passcode, wizard.step)
    }

    @Test fun `back keeps what was typed`() {
        show()
        next()
        compose.onNodeWithTag("dob-field").performTextInput("15081998")
        next()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("15/08/1998").assertIsDisplayed()
    }

    @Test fun `a bad opening balance shows an error`() {
        show()
        next()
        compose.onNodeWithTag("dob-field").performTextInput("15081998")
        next()
        compose.onNodeWithTag("passcode-field").performTextInput("1234")
        compose.onNodeWithTag("passcode-confirm-field").performTextInput("1234")
        next()
        compose.onNodeWithTag("account-name-field").performTextInput("HDFC Bank")
        compose.onNodeWithTag("opening-balance-field").performTextInput("12.345")
        compose.onNodeWithText("Enter an amount like 12,500.50").assertIsDisplayed()
        next()
        assertEquals(OnboardingStep.Accounts, wizard.step)
    }

    @Test fun `a failed save stays on Goals with an error and can be retried`() {
        var attempts = 0
        show(onSave = { if (++attempts == 1) error("disk full") })
        fillToGoals()
        compose.onNodeWithText("Finish setup").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Couldn't save your setup. It's safe to try again.").assertIsDisplayed()
        assertEquals(OnboardingStep.Goals, wizard.step)
        compose.onNodeWithText("Finish setup").performClick()
        compose.waitForIdle()
        assertEquals(OnboardingStep.Done, wizard.step)
    }

    @Test fun `dark theme renders`() {
        show(dark = true)
        snap("welcome_dark")
        next()
        compose.onNodeWithTag("dob-field").performTextInput("15081998")
        next()
        snap("passcode_dark")
    }
}
