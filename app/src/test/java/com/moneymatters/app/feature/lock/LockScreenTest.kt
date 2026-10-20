package com.moneymatters.app.feature.lock

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h851dp-xxhdpi")
class LockScreenTest {

    @get:Rule val compose = createComposeRule()

    private val store = PasscodeStore(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("lock_screen_test", Context.MODE_PRIVATE))
    private lateinit var lock: AppLock
    private var biometricRequests = 0

    @Before fun setUp() {
        store.save("4829", biometricEnabled = true)
        lock = AppLock(store, hashing = Dispatchers.Unconfined)
    }

    private fun show(dark: Boolean = false, biometric: Boolean = true) = compose.setContent {
        MoneyMattersTheme(darkTheme = dark) {
            LockScreen(lock, onRequestBiometric = if (biometric) { { biometricRequests++ } } else null)
        }
    }

    private fun tap(code: String) = code.forEach { compose.onNodeWithText(it.toString()).performClick() }

    @Test fun `the keypad unlocks with the right passcode`() {
        show()
        compose.onNodeWithText("Enter passcode").assertIsDisplayed()
        tap("48")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lock_light.png")
        tap("29")
        compose.waitForIdle()
        assertFalse(lock.isLocked)
    }

    @Test fun `a wrong passcode says how many tries are left`() {
        show()
        tap("1111")
        compose.onNodeWithText("Wrong passcode · 4 tries left").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lock_wrong.png")
    }

    @Test fun `after five misses the keypad waits`() {
        show()
        repeat(5) { tap("0000") }
        compose.onNodeWithText("Too many tries. Try again in 30s").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lock_lockout.png")
    }

    @Test fun `delete removes the last digit`() {
        show()
        tap("48")
        compose.onNodeWithContentDescription("Delete").performClick()
        assertEquals("4", lock.entered)
    }

    @Test fun `biometrics are offered on lock and from the fingerprint key`() {
        show()
        compose.waitForIdle()
        assertEquals(1, biometricRequests)
        compose.onNodeWithContentDescription("Use biometrics").performClick()
        assertEquals(2, biometricRequests)
    }

    @Test fun `no fingerprint key without biometrics`() {
        show(biometric = false)
        assertEquals(0, compose.onAllNodesWithContentDescription("Use biometrics").fetchSemanticsNodes().size)
    }

    @Test fun `dark theme renders`() {
        show(dark = true)
        tap("4")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lock_dark.png")
    }
}
