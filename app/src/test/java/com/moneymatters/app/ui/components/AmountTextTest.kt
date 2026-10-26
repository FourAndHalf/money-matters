package com.moneymatters.app.ui.components

import com.moneymatters.app.core.money.rupees
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {
    @Test fun `amounts show how much moved with no sign, whichever way the number points`() {
        assertEquals("₹486", amountText((-486).rupees))
        assertEquals("₹72,000", amountText(72_000.rupees))
    }
}
