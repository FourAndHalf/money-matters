package com.moneymatters.app.feature.settings

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.profile.TaxRegime
import com.moneymatters.app.core.profile.UserProfile
import com.moneymatters.app.core.security.LockTimeout
import com.moneymatters.app.core.security.PasscodeStore
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {

    private lateinit var db: MoneyMattersDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var profiles: ProfileStore
    private lateinit var passcodes: PasscodeStore
    private lateinit var model: SettingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = LedgerRepository(db)
        profiles = ProfileStore(context.getSharedPreferences("settings_vm_profile", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        profiles.completeSetup(UserProfile(LocalDate.of(1995, 6, 1), 1, TaxRegime.New, null, false, setOf("Groceries"), false, false))
        passcodes = PasscodeStore(context.getSharedPreferences("settings_vm_passcode", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        passcodes.save("1234", biometricEnabled = false, timeout = LockTimeout.OneMinute)
        val prefs = SettingsPrefs(context)
        val ai = AiSettingsStore(context.getSharedPreferences("settings_vm_ai", Context.MODE_PRIVATE))
        // Unconfined hashing + Main.immediate on Robolectric's main thread: each check finishes inline (see AppLockTest).
        model = SettingsViewModel(repo, profiles, passcodes, prefs, ai, hashing = Dispatchers.Unconfined)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun `saveProfile persists the edited month start day`() {
        model.profileForm = model.profileForm.copy(monthStartDay = 5)
        model.saveProfile()
        idle()
        assertEquals(5, profiles.profile()!!.monthStartDay)
        assertFalse(model.isSavingProfile)
        assertFalse(model.profileSaveFailed)
    }

    @Test fun `toggling biometric writes straight through to the passcode store`() {
        assertFalse(model.biometricEnabled)
        model.updateBiometricEnabled(true)
        assertTrue(model.biometricEnabled)
        assertTrue(passcodes.isBiometricEnabled())
    }

    @Test fun `changing the lock timeout writes straight through`() {
        model.updateLockTimeout(LockTimeout.FiveMinutes)
        assertEquals(LockTimeout.FiveMinutes, model.lockTimeout)
        assertEquals(LockTimeout.FiveMinutes, passcodes.lockTimeout())
    }

    @Test fun `changing the passcode needs the right current one and a matching confirmation`() {
        model.currentPasscode = "wrong"
        model.newPasscode = "5678"
        model.confirmPasscode = "5678"
        model.changePasscode()
        idle()
        assertTrue(model.passcodeChangeFailed)
        assertTrue(passcodes.verify("1234"))

        model.currentPasscode = "1234"
        model.newPasscode = "5678"
        model.confirmPasscode = "0000"
        model.changePasscode()
        idle()
        assertTrue(model.passcodeChangeFailed)

        model.currentPasscode = "1234"
        model.newPasscode = "5678"
        model.confirmPasscode = "5678"
        model.changePasscode()
        idle()
        assertFalse(model.passcodeChangeFailed)
        assertTrue(model.passcodeChanged)
        assertTrue(passcodes.verify("5678"))
        assertEquals("", model.currentPasscode)
    }

    @Test fun `archiving and unarchiving an account round-trips`() = runBlocking {
        val id = repo.createAccount("HDFC Bank", AccountType.Asset)
        model.archiveAccount(id)
        idle()
        assertTrue(db.ledgerDao().getAccount(id)!!.isArchived)
        model.unarchiveAccount(id)
        idle()
        assertFalse(db.ledgerDao().getAccount(id)!!.isArchived)
    }

    @Test fun `toggling essential adds and removes the category from the profile`() {
        assertTrue("Groceries" in model.profileForm.essentialCategories)
        model.toggleEssential("Groceries")
        assertFalse("Groceries" in model.profileForm.essentialCategories)
        assertFalse("Groceries" in profiles.profile()!!.essentialCategories)
        model.toggleEssential("Dining")
        assertTrue("Dining" in model.profileForm.essentialCategories)
    }

    @Test fun `exportCsv lists every posting with its account and amount`() = runBlocking {
        val bank = repo.createAccount("HDFC Bank", AccountType.Asset)
        val dining = repo.createAccount("Expenses:Dining", AccountType.Expense)
        repo.postTransaction(
            LocalDate.of(2026, 10, 19),
            "Swiggy",
            listOf(
                PostingLine(dining, debit = 486.rupees),
                PostingLine(bank, credit = 486.rupees),
            ),
        )
        val csv = model.exportCsv()
        assertTrue(csv.contains("Swiggy"))
        assertTrue(csv.contains("486.00"))
        assertEquals(3, csv.trim().lines().size) // header + 2 postings
    }
}
