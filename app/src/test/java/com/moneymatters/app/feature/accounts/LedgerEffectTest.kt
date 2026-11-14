package com.moneymatters.app.feature.accounts

import com.moneymatters.app.core.money.Paise
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerEffectTest {
    private fun entry(amount: Long) = LedgerEntry(
        id = "t", dateLabel = "5 Nov", chip = "S", payee = "Shop", meta = "", amount = Paise(amount),
        runningBalance = Paise(0), status = null, isTransfer = false,
    )

    @Test fun `a credit card charge is money out`() {
        // Owed goes up by 500, so the entry is +500 in owed terms and -500 in effect on you.
        assertEquals(Paise(-500_00), entry(500_00).effectOnYou(isLiability = true))
    }

    @Test fun `a payment towards a card is money in`() {
        assertEquals(Paise(300_00), entry(-300_00).effectOnYou(isLiability = true))
    }

    @Test fun `a bank account keeps its sign`() {
        assertEquals(Paise(-200_00), entry(-200_00).effectOnYou(isLiability = false))
        assertEquals(Paise(900_00), entry(900_00).effectOnYou(isLiability = false))
    }
}
