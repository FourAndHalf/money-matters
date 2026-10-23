package com.moneymatters.app.feature.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.feature.books.buildBooks
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Random mixes of everything the Invest tab can do. An operation may be refused (selling more than held, a locked
 * lot); what must never happen is the screen state failing to build from whatever the ledger then holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InvestFuzzTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val rules = CapitalGainsRules(
        listOf(
            CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1)),
        ),
    )

    @Test fun `state builds after any sequence of investment operations`() = runBlocking {
        repeat(25) { seed ->
            val rnd = Random(seed)
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
            val ledger = LedgerRepository(db)
            val invest = InvestRepository(db, ledger, rules)
            val bank = ledger.createAccount("Bank", AccountType.Asset, kind = AccountKind.Bank)
            val symbols = listOf("INFY", "TCS", "RELIANCE", "NIFTYBEES")
            val holdings = mutableListOf<String>()
            fun date() = today.minusDays(rnd.nextLong(0, 900))
            fun attempt(block: suspend () -> Unit) { try { runBlocking { block() } } catch (_: Exception) { } }
            repeat(40) { step ->
                when (rnd.nextInt(16)) {
                    0, 1, 2 -> attempt {
                        val bond = rnd.nextInt(4) == 0
                        val fund = !bond && rnd.nextBoolean()
                        val h = invest.findOrCreateHolding(
                            if (bond) Instrument.Bond else if (fund) Instrument.MutualFund else Instrument.Stock, symbols[rnd.nextInt(symbols.size)].takeIf { !fund && !bond },
                            name = if (bond) "Bond ${rnd.nextInt(3)}" else if (fund) "Fund ${rnd.nextInt(3)}" else null,
                            fundClass = if (fund) FundClass.entries.getOrNull(rnd.nextInt(FundClass.entries.size + 1)) else null,
                            couponBps = if (bond) rnd.nextInt(100, 1_500) else null,
                        )
                        holdings += h
                        val units = Units(rnd.nextLong(1, 500) * 10_000)
                        val amount = Paise(rnd.nextLong(1, 5_000_000))
                        invest.buy(h, date(), units, UnitPrice(rnd.nextLong(1, 2_000_000)), amount, bank, "buy")
                    }
                    3, 4 -> attempt {
                        val h = holdings.random(rnd)
                        invest.sell(h, date(), Units(rnd.nextLong(1, 300) * 10_000), UnitPrice(rnd.nextLong(1, 2_000_000)), Paise(rnd.nextLong(1, 5_000_000)), bank, "sell")
                    }
                    5 -> attempt { invest.recordPrice(holdings.random(rnd), date(), UnitPrice(rnd.nextLong(1, 3_000_000)), "manual", PriceStatus.entries[rnd.nextInt(PriceStatus.entries.size)]) }
                    6 -> attempt { invest.applyCorporateAction(holdings.random(rnd), RestateKind.entries[rnd.nextInt(2)], Units(rnd.nextLong(1, 10) * 1_000_000), date()) }
                    7 -> attempt { invest.recordDividend(holdings.random(rnd), date(), Paise(rnd.nextLong(1, 100_000)), Paise(0), bank, "div") }
                    8 -> attempt {
                        val start = date()
                        val acct = invest.openDeposit("FD $step", Paise(rnd.nextLong(1, 5_000_000)), rnd.nextInt(1, 1_000), Compounding.entries[rnd.nextInt(3)], start, start.plusDays(rnd.nextLong(1, 1500)), bank)
                        if (rnd.nextBoolean()) invest.accrueInterest(acct, today)
                        if (rnd.nextInt(4) == 0) invest.closeDeposit(acct, today, bank)
                    }
                    9 -> attempt { invest.createSipPlan(holdings.random(rnd), Paise(rnd.nextLong(1, 100_000)), rnd.nextInt(1, 32), bank, date()) }
                    10 -> attempt { invest.orderFund(holdings.random(rnd), date(), Paise(rnd.nextLong(1, 100_000)), bank, "order") }
                    11 -> attempt { invest.deleteHolding(holdings.random(rnd)) }
                    12 -> attempt { invest.deleteTrade(ledger.snapshot().first().transactions.random(rnd).id) }
                    13 -> attempt {
                        val t = ledger.snapshot().first().transactions.random(rnd)
                        invest.editTrade(t.id, date(), Units(rnd.nextLong(1, 300) * 10_000), Paise(rnd.nextLong(1, 5_000_000)), bank)
                    }
                    14 -> attempt { invest.deleteDeposit(invest.snapshot().first().fixedDeposits.random(rnd).accountId) }
                    15 -> attempt { invest.deleteSip(invest.snapshot().first().sipPlans.random(rnd).id) }
                }
                val l = ledger.snapshot().first()
                val i = invest.snapshot().first()
                try {
                    buildInvest(l, i, today, rules, goal = SipGoal(today.plusYears(5), 0.12))
                    buildBooks(l, today, invest = i)
                    val accountIds = l.accounts.map { it.id }.toSet()
                    check(l.postings.all { it.accountId in accountIds }) { "a posting points at an account that no longer exists" }
                    check(l.postings.groupBy { it.transactionId }.values.all { legs -> legs.sumOf { it.debitPaise ?: 0 } == legs.sumOf { it.creditPaise ?: 0 } }) { "an entry is out of balance" }
                    check(i.lots.all { lot -> l.transactions.any { it.id == lot.sourceTransactionId } }) { "a lot points at an entry that no longer exists" }
                    check(i.consumptions.all { c -> l.transactions.any { it.id == c.saleTransactionId } }) { "a sale record points at an entry that no longer exists" }
                } catch (e: Throwable) {
                    throw AssertionError("seed $seed step $step: building state failed", e)
                }
            }
            db.close()
        }
    }
}
