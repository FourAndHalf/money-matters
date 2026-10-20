package com.moneymatters.app.feature.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.security.LockTimeout
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.feature.accounts.AccountKind
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingSaverTest {

    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var db: MoneyMattersDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var passcodes: PasscodeStore
    private lateinit var profiles: ProfileStore
    private lateinit var saver: OnboardingSaver

    private val form = OnboardingForm(
        profile = ProfileInfo("15081998"),
        passcode = "482910",
        passcodeConfirm = "482910",
        accounts = listOf(
            NewAccountDraft("HDFC Bank", AccountKind.Bank, "1,84,250.50"),
            NewAccountDraft("Wallet", AccountKind.Cash, "2,000"),
            NewAccountDraft("HDFC Regalia", AccountKind.CreditCard, "18,420"),
            NewAccountDraft("Car loan", AccountKind.Loan, "3,20,000"),
            NewAccountDraft("Empty FD", AccountKind.FixedDeposit, ""),
            NewAccountDraft("   ", AccountKind.Bank, "999"),
        ),
        income = IncomeProfile("92,000", hasFreelanceIncome = true),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        repo = LedgerRepository(db)
        passcodes = PasscodeStore(context.getSharedPreferences("saver_passcode_test", Context.MODE_PRIVATE))
        profiles = ProfileStore(context.getSharedPreferences("saver_profile_test", Context.MODE_PRIVATE))
        saver = OnboardingSaver(repo, passcodes, profiles, today = { today })
    }

    private fun id(name: String) = runBlocking { requireNotNull(repo.accountIdByName(name)) { "missing $name" } }
    private fun balance(name: String) = runBlocking { repo.accountBalance(id(name)) }

    @Test fun `opening balances are posted against Equity Opening with the right signs`() = runBlocking {
        saver.save(form)
        assertEquals(Paise(1_84_250_50), balance("HDFC Bank"))
        assertEquals(2_000.rupees, balance("Wallet"))
        // Liabilities sit on the credit side, so their debit − credit balance is negative.
        assertEquals((-18_420).rupees, balance("HDFC Regalia"))
        assertEquals((-3_20_000).rupees, balance("Car loan"))
        assertEquals(Paise.Zero, balance("Empty FD"))
        // Equity:Opening is net worth at setup: assets − liabilities, on the credit side.
        assertEquals(-(Paise(1_84_250_50) + 2_000.rupees - 18_420.rupees - 3_20_000.rupees), balance(OPENING_EQUITY_ACCOUNT))
    }

    @Test fun `every opening entry balances and the whole ledger nets to zero`() = runBlocking {
        saver.save(form)
        val transactions = repo.transactions().first()
        assertEquals(4, transactions.size) // the empty FD and blank-named draft post nothing
        for (t in transactions) {
            val postings = db.ledgerDao().getPostingsForTransaction(t.id)
            assertEquals(postings.sumOf { it.debitPaise ?: 0 }, postings.sumOf { it.creditPaise ?: 0 })
        }
        val net = repo.accounts().first().fold(Paise.Zero) { acc, a -> acc + repo.accountBalance(a.id) }
        assertEquals(Paise.Zero, net)
    }

    @Test fun `creates the account, category and income accounts with the right types`() = runBlocking {
        saver.save(form)
        val accounts = repo.accounts().first().associateBy { it.name }
        assertEquals(AccountType.Asset, accounts["HDFC Bank"]!!.type)
        assertEquals(AccountType.Liability, accounts["HDFC Regalia"]!!.type)
        assertEquals(AccountKind.CreditCard, accounts["HDFC Regalia"]!!.kind)
        assertEquals(AccountKind.Loan, accounts["Car loan"]!!.kind)
        assertNull(accounts[OPENING_EQUITY_ACCOUNT]!!.kind)
        assertEquals(AccountType.Equity, accounts[OPENING_EQUITY_ACCOUNT]!!.type)
        DefaultCategories.forEach { assertEquals(AccountType.Expense, accounts["Expenses:${it.name}"]!!.type) }
        assertEquals(AccountType.Income, accounts["Income:Salary"]!!.type)
        assertEquals(AccountType.Income, accounts["Income:Freelance"]!!.type)
        assertNull(accounts["   "])
    }

    @Test fun `saves the passcode and profile and marks setup complete`() = runBlocking {
        saver.save(form)
        assertTrue(passcodes.verify("482910"))
        assertTrue(profiles.isSetupComplete())
        assertEquals(92_000.rupees, profiles.profile()!!.monthlyTakeHome)
    }

    @Test fun `re-running after an interrupted save never doubles opening balances`() = runBlocking {
        postSetupToLedger(repo, form, today) // the ledger write landed, then the app died
        assertFalse(profiles.isSetupComplete())
        saver.save(form)
        assertEquals(Paise(1_84_250_50), balance("HDFC Bank"))
        assertEquals(4, repo.transactions().first().size)
        assertTrue(profiles.isSetupComplete())
    }

    @Test fun `an invalid form writes nothing at all`() {
        val bad = form.copy(profile = ProfileInfo("31022000"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { saver.save(bad) } }
        runBlocking {
            assertEquals(0, repo.accounts().first().size)
            assertFalse(passcodes.isSet())
            assertFalse(profiles.isSetupComplete())
        }
    }

    @Test fun `an account without a balance is still created`() = runBlocking {
        saver.save(form.copy(accounts = listOf(NewAccountDraft("Cash"))))
        assertNotNull(repo.accountIdByName("Cash"))
        assertEquals(0, repo.transactions().first().size)
    }

    @Test fun `saves the chosen lock timeout and passcode length`() = runBlocking {
        saver.save(form.copy(lockTimeout = LockTimeout.FiveMinutes))
        assertEquals(LockTimeout.FiveMinutes, passcodes.lockTimeout())
        assertEquals(6, passcodes.passcodeLength())
    }
}
