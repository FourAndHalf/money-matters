package com.moneymatters.app.ui.components

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountExpressionTest {
    @Test fun `a single amount evaluates to itself`() {
        assertEquals(150.rupees, evaluateAmountExpression("150"))
        assertEquals(Paise(15_050), evaluateAmountExpression("150.5"))
    }

    @Test fun `a chain of additions and subtractions evaluates left to right`() {
        assertEquals(165.rupees, evaluateAmountExpression("120+60−15"))
    }

    @Test fun `multiply and divide bind tighter than add and subtract`() {
        assertEquals(130.rupees, evaluateAmountExpression("50+40×2")) // 50 + 80
        assertEquals(20.rupees, evaluateAmountExpression("100÷5")) // plain divide
        assertEquals(190.rupees, evaluateAmountExpression("200−100÷10")) // 200 − 10
        assertEquals(200.rupees, evaluateAmountExpression("2.5×80"))
    }

    @Test fun `the keyboard's own symbols and stray spaces and commas are understood`() {
        assertEquals(130.rupees, evaluateAmountExpression("50 + 40 * 2"))
        assertEquals(130.rupees, evaluateAmountExpression("50+40x2"))
        assertEquals(40.rupees, evaluateAmountExpression("100-120/2"))
        assertEquals(1_200.rupees, evaluateAmountExpression("1,000+200"))
    }

    @Test fun `results round to the nearest paisa`() {
        assertEquals(Paise(3_333), evaluateAmountExpression("100÷3")) // 33.333…
        assertEquals(Paise(6_667), evaluateAmountExpression("200÷3")) // 66.666…
    }

    @Test fun `anything that doesn't parse is null`() {
        assertNull(evaluateAmountExpression(""))
        assertNull(evaluateAmountExpression("120+"))
        assertNull(evaluateAmountExpression("+"))
        assertNull(evaluateAmountExpression("+5"))
        assertNull(evaluateAmountExpression("5××2"))
        assertNull(evaluateAmountExpression("12ab"))
        assertNull(evaluateAmountExpression("100÷0"))
    }
}
