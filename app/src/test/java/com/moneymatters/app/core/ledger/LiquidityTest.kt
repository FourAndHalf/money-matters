package com.moneymatters.app.core.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiquidityTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var bank: String
    private lateinit var fd: String
    private lateinit var stocks: String
    private lateinit var epf: String
    private lateinit var card: String
    private lateinit var food: String
    private lateinit var opening: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            fd = ledger.createAccount("FD HDFC", AccountType.Asset, kind = AccountKind.FixedDeposit)
            stocks = ledger.createAccount("Infosys", AccountType.Asset, kind = AccountKind.Investment)
            epf = ledger.createAccount("EPF", AccountType.Asset)
            card = ledger.createAccount("HDFC Card", AccountType.Liability, kind = AccountKind.CreditCard)
            food = ledger.createAccount("Expenses:Food", AccountType.Expense)
            opening = ledger.createAccount("Equity:Opening", AccountType.Equity)
        }
        fund(bank, 100_000)
        fund(fd, 200_000)
        fund(stocks, 300_000)
        fund(epf, 400_000)
    }

    private fun fund(account: String, amount: Long, status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(LocalDate.of(2026, 10, 1), "Opening", listOf(PostingLine(account, debit = amount.rupees), PostingLine(opening, credit = amount.rupees)), status = status)
    }

    private fun account(id: String) = runBlocking { ledger.snapshot().first() }.accounts.single { it.id == id }
    private fun map() = moneyMap(runBlocking { ledger.snapshot().first() })

    @Test fun `each kind of asset has a sensible default tier`() {
        assertEquals(LiquidityTier.Cash, effectiveTier(account(bank)))
        assertEquals(LiquidityTier.Deposits, effectiveTier(account(fd)))
        assertEquals(LiquidityTier.Markets, effectiveTier(account(stocks)))
        assertEquals(LiquidityTier.Locked, effectiveTier(account(epf))) // an asset of no particular kind can't be reached soon
    }

    @Test fun `only assets have a tier`() {
        assertNull(effectiveTier(account(card)))
        assertNull(effectiveTier(account(food)))
        assertNull(effectiveTier(account(opening)))
    }

    @Test fun `a chosen tier overrides the default, and clearing it goes back`() {
        runBlocking { ledger.setLiquidityTier(fd, LiquidityTier.Cash) } // e.g. an FD held as a sweep account
        assertEquals(LiquidityTier.Cash, effectiveTier(account(fd)))
        runBlocking { ledger.setLiquidityTier(fd, null) }
        assertEquals(LiquidityTier.Deposits, effectiveTier(account(fd)))
    }

    @Test fun `a goal can be set and cleared on an asset`() {
        runBlocking { ledger.setGoal(fd, AccountGoal.EmergencyFund) }
        assertEquals(AccountGoal.EmergencyFund, account(fd).goal)
        runBlocking { ledger.setGoal(fd, null) }
        assertNull(account(fd).goal)
    }

    @Test fun `tiers and goals can't be set on liabilities, categories or equity`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.setLiquidityTier(card, LiquidityTier.Cash) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.setGoal(food, AccountGoal.WealthGoal) } }
        assertThrows(NoSuchElementException::class.java) { runBlocking { ledger.setGoal("nope", AccountGoal.WealthGoal) } }
    }

    @Test fun `the money map groups balances by tier and by goal`() {
        runBlocking {
            ledger.setGoal(bank, AccountGoal.EmergencyFund)
            ledger.setGoal(fd, AccountGoal.EmergencyFund)
            ledger.setGoal(stocks, AccountGoal.WealthGoal)
        }
        val m = map()

        assertEquals(100_000.rupees, m.byTier.getValue(LiquidityTier.Cash))
        assertEquals(200_000.rupees, m.byTier.getValue(LiquidityTier.Deposits))
        assertEquals(300_000.rupees, m.byTier.getValue(LiquidityTier.Markets))
        assertEquals(400_000.rupees, m.byTier.getValue(LiquidityTier.Locked))
        assertEquals(300_000.rupees, m.byGoal.getValue(AccountGoal.EmergencyFund))
        assertEquals(300_000.rupees, m.byGoal.getValue(AccountGoal.WealthGoal))
        assertEquals(400_000.rupees, m.unassigned) // EPF isn't counted towards a goal
    }

    @Test fun `the tiers add up to all assets, and tiers plus unassigned match goals plus unassigned`() {
        runBlocking { ledger.setGoal(bank, AccountGoal.EmergencyFund) }
        val m = map()
        val tiers = m.byTier.values.sumOf { it.value }
        assertEquals(1_000_000.rupees, Paise(tiers))
        assertEquals(Paise(tiers), Paise(m.byGoal.values.sumOf { it.value } + m.unassigned.value))
    }

    @Test fun `drafts and archived accounts are left out`() {
        fund(bank, 5_000, status = TransactionStatus.Draft)
        runBlocking { ledger.archiveAccount(epf) }
        val m = map()
        assertEquals(100_000.rupees, m.byTier.getValue(LiquidityTier.Cash))
        assertEquals(Paise.Zero, m.byTier.getValue(LiquidityTier.Locked))
    }

    @Test fun `a goal moves with the account when its tier changes`() {
        runBlocking { ledger.setGoal(fd, AccountGoal.EmergencyFund); ledger.setLiquidityTier(fd, LiquidityTier.Cash) }
        val m = map()
        assertEquals(300_000.rupees, m.byTier.getValue(LiquidityTier.Cash))
        assertEquals(200_000.rupees, m.byGoal.getValue(AccountGoal.EmergencyFund))
    }

    @Test fun `labels read as words`() {
        assertEquals("Emergency fund", AccountGoal.EmergencyFund.label())
        assertEquals("₹1 Crore by 30", AccountGoal.WealthGoal.label())
        assertEquals("Locked", LiquidityTier.Locked.label())
    }
}
