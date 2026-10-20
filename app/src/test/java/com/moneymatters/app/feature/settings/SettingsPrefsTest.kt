package com.moneymatters.app.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPrefsTest {

    private lateinit var prefs: SettingsPrefs

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = SettingsPrefs(context)
    }

    @Test fun `every preference defaults sensibly and round-trips`() {
        assertFalse(prefs.accountantMode)
        assertEquals(NumberFormat.Full, prefs.numberFormat)
        assertEquals(50, prefs.impactThresholdPercent)
        assertTrue(prefs.decisionTagPrompts)
        assertFalse(prefs.notificationsEnabled)

        prefs.accountantMode = true
        prefs.numberFormat = NumberFormat.Compact
        prefs.impactThresholdPercent = 70
        prefs.decisionTagPrompts = false
        prefs.notificationsEnabled = true

        assertTrue(prefs.accountantMode)
        assertEquals(NumberFormat.Compact, prefs.numberFormat)
        assertEquals(70, prefs.impactThresholdPercent)
        assertFalse(prefs.decisionTagPrompts)
        assertTrue(prefs.notificationsEnabled)
    }

    @Test fun `the impact threshold is clamped to 1 through 100`() {
        prefs.impactThresholdPercent = 500
        assertEquals(100, prefs.impactThresholdPercent)
        prefs.impactThresholdPercent = -5
        assertEquals(1, prefs.impactThresholdPercent)
    }
}
