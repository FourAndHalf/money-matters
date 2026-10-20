package com.moneymatters.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.profile.TaxRegime
import com.moneymatters.app.core.profile.UserProfile
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.feature.books.SampleBooks
import com.moneymatters.app.feature.quickadd.QuickAddPrefs
import com.moneymatters.app.feature.quickadd.QuickAddViewModel
import com.moneymatters.app.feature.settings.AiSettingsStore
import com.moneymatters.app.feature.settings.SettingsPrefs
import com.moneymatters.app.feature.settings.SettingsViewModel
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the dashboard → accounts → ledger → back path through the real app shell. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h852dp-xxhdpi")
class MoneyMattersAppNavigationTest {

    @get:Rule val compose = createComposeRule()

    private fun quickAddModel(): QuickAddViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        return QuickAddViewModel(LedgerRepository(db))
    }

    private fun quickAddPrefs() = QuickAddPrefs(ApplicationProvider.getApplicationContext())

    private fun settingsModel(): SettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = ProfileStore(context.getSharedPreferences("nav_test_profile", Context.MODE_PRIVATE))
        profiles.completeSetup(UserProfile(LocalDate.of(1995, 1, 1), 1, TaxRegime.New, null, false, emptySet(), false, false))
        val passcodes = PasscodeStore(context.getSharedPreferences("nav_test_passcode", Context.MODE_PRIVATE))
        val db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val ai = AiSettingsStore(context.getSharedPreferences("nav_test_ai", Context.MODE_PRIVATE))
        return SettingsViewModel(LedgerRepository(db), profiles, passcodes, SettingsPrefs(context), ai)
    }

    @Test fun `View all on Home switches to the Accounts tab`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel()) }
        }
        compose.onAllNodesWithText("View all")[0].performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test fun `tapping an account opens its ledger and back returns to the tab bar`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel()) }
        }
        compose.onAllNodesWithText("View all")[0].performClick()
        compose.onNodeWithText("HDFC Bank").performClick()

        compose.onNodeWithText("Current balance").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test fun `tapping Quick add opens the entry screen and back returns to the tab bar`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel()) }
        }
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onAllNodesWithText("Quick add")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick add").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick add").assertIsDisplayed()
    }
}
