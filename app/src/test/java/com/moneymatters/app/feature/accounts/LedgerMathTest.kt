package com.moneymatters.app.feature.accounts

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerMathTest {

    @Test fun `each balance is the previous one plus that entry`() {
        val balances = runningBalances(1_000.rupees, listOf(500.rupees, (-200).rupees, 300.rupees))
        assertEquals(listOf(1_500.rupees, 1_300.rupees, 1_600.rupees), balances)
    }

    @Test fun `the last balance is the opening balance plus everything`() {
        val amounts = listOf(72_000.rupees, (-18_000).rupees, (-10_324).rupees, (-5_000).rupees, (-2_140).rupees, (-1_800).rupees, (-486).rupees)
        val balances = runningBalances(1_50_000.rupees, amounts)
        assertEquals(1_50_000.rupees + amounts.fold(Paise.Zero) { acc, a -> acc + a }, balances.last())
    }

    @Test fun `an empty list of entries returns an empty list of balances`() {
        assertEquals(emptyList<Paise>(), runningBalances(5_000.rupees, emptyList()))
    }

    @Test fun `every sample ledger's last running balance matches its stated current balance`() {
        // Liability ledgers (cards, loans) keep a positive "amount owed" running balance;
        // AccountLedgerState.currentBalance is that same unsigned figure. Display negates it.
        SampleLedgers.byAccountId.values.forEach { ledger ->
            val oldestFirst = ledger.sections.reversed().flatMap { it.entries.reversed() }
            assertEquals("ledger ${ledger.accountId}", ledger.currentBalance, oldestFirst.last().runningBalance)
        }
    }

    @Test fun `every sample ledger entry's stored running balance is self-consistent`() {
        SampleLedgers.byAccountId.values.forEach { ledger ->
            ledger.sections.forEach { section ->
                val oldestFirst = section.entries.reversed()
                oldestFirst.zipWithNext().forEach { (prev, next) ->
                    assertEquals("entry ${next.id}", prev.runningBalance + next.amount, next.runningBalance)
                }
            }
        }
    }

    @Test fun `a credit card statement subtotal is purchases only, not net of payments`() {
        val card = SampleLedgers.byAccountId.getValue("icici-credit")
        card.sections.forEach { section ->
            val expected = section.entries.filter { it.amount.value > 0 }.fold(Paise.Zero) { acc, e -> acc + e.amount }
            assertEquals("section ${section.title}", expected, section.subtotal)
        }
        // The previous cycle included a payment, so its net total (0) must not equal its charges.
        val previousCycle = card.sections.single { it.title == "Statement: 10 Aug – 9 Sep" }
        assertEquals(18_200.rupees, previousCycle.subtotal)
    }
}
