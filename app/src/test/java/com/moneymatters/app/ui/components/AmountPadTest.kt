package com.moneymatters.app.ui.components

import com.moneymatters.app.core.money.rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountPadTest {

    @Test fun `a single amount evaluates to itself`() {
        assertEquals(150.rupees, evaluateAmountExpression("150"))
    }

    @Test fun `a chain of plus and minus evaluates left to right`() {
        assertEquals(165.rupees, evaluateAmountExpression("120+60−15"))
    }

    @Test fun `blank, dangling-operator or unparseable input is rejected`() {
        assertNull(evaluateAmountExpression(""))
        assertNull(evaluateAmountExpression("120+"))
        assertNull(evaluateAmountExpression("+"))
    }
}
