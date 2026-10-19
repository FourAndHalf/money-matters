package com.moneymatters.app.core.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileStoreTest {

    private val store = ProfileStore(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("profile_test", Context.MODE_PRIVATE))

    private val profile = UserProfile(
        dateOfBirth = LocalDate.of(1998, 8, 15),
        monthStartDay = 1,
        taxRegime = TaxRegime.Old,
        monthlyTakeHome = 92_000.rupees,
        hasFreelanceIncome = true,
        essentialCategories = setOf("Groceries", "Bills"),
        emergencyFundGoal = true,
        croreByThirtyGoal = false,
    )

    @Test fun `setup isn't complete until a profile is saved`() {
        assertFalse(store.isSetupComplete())
        assertNull(store.profile())
    }

    @Test fun `round-trips every field and marks setup complete`() {
        store.completeSetup(profile)
        assertTrue(store.isSetupComplete())
        assertEquals(profile, store.profile())
    }

    @Test fun `a missing take-home stays missing`() {
        store.completeSetup(profile)
        store.completeSetup(profile.copy(monthlyTakeHome = null))
        assertNull(store.profile()!!.monthlyTakeHome)
    }
}
